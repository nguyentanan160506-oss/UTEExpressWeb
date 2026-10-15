package com.ute.uteexpress.service;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrackingRateLimiterTests {
    @Test void limitsEachPeerIndependentlyAndReturnsRetryAfter() {
        var clock = new TestClock(); var limiter = new TrackingRateLimiter(clock, 2, 10);
        assertTrue(limiter.check("192.0.2.1").allowed()); assertTrue(limiter.check("192.0.2.1").allowed());
        var blocked = limiter.check("192.0.2.1");
        assertFalse(blocked.allowed()); assertEquals(60, blocked.retryAfterSeconds());
        assertTrue(limiter.check("192.0.2.2").allowed());
    }

    @Test void quotaResetsExactlyWhenWindowExpires() {
        var clock = new TestClock(); var limiter = new TrackingRateLimiter(clock, 1, 10);
        assertTrue(limiter.check("192.0.2.1").allowed());
        clock.advance(Duration.ofSeconds(59));
        assertEquals(1, limiter.check("192.0.2.1").retryAfterSeconds());
        clock.advance(Duration.ofSeconds(1));
        assertTrue(limiter.check("192.0.2.1").allowed());
    }

    @Test void retryAfterRoundsUpEvenForASubMillisecondFraction() {
        var clock = new TestClock(); var limiter = new TrackingRateLimiter(clock, 1, 10);
        limiter.check("192.0.2.1");
        clock.advance(Duration.ofSeconds(58).plusNanos(999500000));
        assertEquals(2, limiter.check("192.0.2.1").retryAfterSeconds());
    }

    @Test void boundedClientPoolRejectsNewPeersWithoutResettingExistingQuotas() {
        var clock = new TestClock(); var limiter = new TrackingRateLimiter(clock, 1, 2);
        assertTrue(limiter.check("192.0.2.1").allowed()); assertTrue(limiter.check("192.0.2.2").allowed());
        assertFalse(limiter.check("192.0.2.3").allowed()); assertFalse(limiter.check("192.0.2.1").allowed());
        clock.advance(Duration.ofMinutes(1));
        assertTrue(limiter.check("192.0.2.3").allowed());
    }

    @Test void expiredOldestPeerIsPrunedEvenWhenOtherPeerIsStillActive() {
        var clock = new TestClock(); var limiter = new TrackingRateLimiter(clock, 1, 2);
        limiter.check("192.0.2.1"); clock.advance(Duration.ofSeconds(30)); limiter.check("192.0.2.2");
        clock.advance(Duration.ofSeconds(30));
        assertTrue(limiter.check("192.0.2.3").allowed()); assertFalse(limiter.check("192.0.2.2").allowed());
    }

    @Test void missingPeerAddressesShareOneConservativeBucket() {
        var limiter = new TrackingRateLimiter(new TestClock(), 1, 10);
        assertTrue(limiter.check(null).allowed()); assertFalse(limiter.check(" ").allowed());
    }

    @Test void invalidConfigurationCannotDisableProtection() {
        assertThrows(IllegalArgumentException.class, () -> new TrackingRateLimiter(new TestClock(), 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new TrackingRateLimiter(new TestClock(), 1, 0));
    }

    @Test void concurrentRequestsCannotExceedTheQuota() throws Exception {
        var limiter = new TrackingRateLimiter(new TestClock(), 5, 10);
        ExecutorService workers = Executors.newFixedThreadPool(8); CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                results.add(workers.submit(() -> {
                    if (!start.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("test start timeout"); }
                    return limiter.check("192.0.2.1").allowed();
                }));
            }
            start.countDown(); int allowed = 0;
            for (var result : results) { if (result.get(10, TimeUnit.SECONDS)) { allowed++; } }
            assertEquals(5, allowed);
        } finally { workers.shutdownNow(); }
    }

    private static class TestClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-10-16T00:00:00Z"));
        void advance(Duration duration) { time.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return time.get(); }
    }
}
