package com.ute.uteexpress.service.operations;

import com.ute.uteexpress.service.TrackingRateLimiter;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Reuses the bounded fixed-window implementation; separate USER-based quota, per application instance. */
@Component
public class CustomerSupportThrottle {
    private final TrackingRateLimiter limiter;
    public CustomerSupportThrottle(Clock clock,
            @Value("${uteexpress.support.rate-limit.writes-per-minute:20}") int requests,
            @Value("${uteexpress.support.rate-limit.max-users:10000}") int users) {
        limiter=new TrackingRateLimiter(clock,requests,users);
    }
    public void check(Long userId) {
        var decision=limiter.check(userId.toString());
        if(!decision.allowed()) {
            throw new TooManyRequests(decision.retryAfterSeconds());
        }
    }
    private static final class TooManyRequests extends ResponseStatusException {
        private final org.springframework.http.HttpHeaders headers=new org.springframework.http.HttpHeaders();
        TooManyRequests(long seconds){super(HttpStatus.TOO_MANY_REQUESTS,"Bạn gửi yêu cầu quá nhanh. Vui lòng chờ rồi thử lại.");headers.set("Retry-After",Long.toString(seconds));}
        @Override public org.springframework.http.HttpHeaders getHeaders(){return headers;}
    }
}
