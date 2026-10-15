package com.ute.uteexpress.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Bounded, per-instance fixed-window limiter; caller must use the transport peer address, not request headers. */
@Component
public class TrackingRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private final Clock clock;
    private final int maxRequests;
    private final int maxClients;
    private final Map<String, Window> clients = new LinkedHashMap<>();

    public TrackingRateLimiter(Clock clock,
            @Value("${uteexpress.tracking.rate-limit.requests-per-minute:20}") int maxRequests,
            @Value("${uteexpress.tracking.rate-limit.max-clients:10000}") int maxClients) {
        if (maxRequests < 1 || maxClients < 1) { throw new IllegalArgumentException("Tracking limits must be positive"); }
        this.clock = clock; this.maxRequests = maxRequests; this.maxClients = maxClients;
    }

    public synchronized Decision check(String peerAddress) {
        Instant now = clock.instant();
        // Insertion order is expiry order because every new window has the same duration.
        var iterator = clients.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt.isAfter(now)) { break; }
            iterator.remove();
        }
        String key = peerAddress == null || peerAddress.isBlank() ? "unknown" : peerAddress;
        Window window = clients.get(key);
        if (window == null) {
            // Do not evict live clients: evicting would let rotating IPs reset somebody else's quota.
            if (clients.size() >= maxClients) { return new Decision(false, WINDOW.toSeconds()); }
            window = new Window(now.plus(WINDOW)); clients.put(key, window);
        }
        if (window.requests >= maxRequests) {
            Duration remaining = Duration.between(now, window.expiresAt);
            long seconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
            return new Decision(false, Math.max(1, seconds));
        }
        window.requests++;
        return new Decision(true, 0);
    }

    public record Decision(boolean allowed, long retryAfterSeconds) { }
    private static final class Window {
        final Instant expiresAt;
        int requests;
        Window(Instant expiresAt) { this.expiresAt = expiresAt; }
    }
}
