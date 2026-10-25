package com.ute.uteexpress.operations;

import com.ute.uteexpress.service.operations.CustomerSupportThrottle;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class CustomerSupportThrottleTests {
    @Test void quotaIsPerAuthenticatedDatabaseUserAndReturnsRetryAfter(){var limiter=new CustomerSupportThrottle(new OperationsJdbcConfig.MutableClock(),2,10);limiter.check(1L);limiter.check(1L);var e=assertThrows(ResponseStatusException.class,()->limiter.check(1L));assertEquals(HttpStatus.TOO_MANY_REQUESTS,e.getStatusCode());assertEquals("60",e.getHeaders().getFirst("Retry-After"));assertDoesNotThrow(()->limiter.check(2L));}
    @Test void quotaExpiresAtWindowBoundary(){var clock=new OperationsJdbcConfig.MutableClock();var limiter=new CustomerSupportThrottle(clock,1,10);limiter.check(1L);clock.set(OperationsJdbcConfig.MutableClock.NOW.plusSeconds(59));assertThrows(ResponseStatusException.class,()->limiter.check(1L));clock.set(OperationsJdbcConfig.MutableClock.NOW.plusSeconds(60));assertDoesNotThrow(()->limiter.check(1L));}
    @Test void fullRegistryFailsClosedInsteadOfEvictingLiveUserQuota(){var limiter=new CustomerSupportThrottle(new OperationsJdbcConfig.MutableClock(),1,1);limiter.check(1L);assertThrows(ResponseStatusException.class,()->limiter.check(2L));assertThrows(ResponseStatusException.class,()->limiter.check(1L));}
    @Test void invalidConfigurationIsRejected(){assertThrows(IllegalArgumentException.class,()->new CustomerSupportThrottle(new OperationsJdbcConfig.MutableClock(),0,10));assertThrows(IllegalArgumentException.class,()->new CustomerSupportThrottle(new OperationsJdbcConfig.MutableClock(),10,0));}
}
