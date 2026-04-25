package com.regulatory.platform.security;

import com.regulatory.platform.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginRateLimitServiceTest {

    @Test
    void allowsWhenNoPriorAttempts() {
        LoginRateLimitService service = new LoginRateLimitService(3, 60);
        assertDoesNotThrow(() -> service.assertAllowed("a@x.com", "1.1.1.1"));
    }

    @Test
    void blocksWhenFailuresReachLimitWithinWindow() {
        LoginRateLimitService service = new LoginRateLimitService(2, 300);
        service.onFailedLogin("a@x.com", "1.1.1.1");
        service.onFailedLogin("a@x.com", "1.1.1.1");
        assertThrows(TooManyRequestsException.class, () -> service.assertAllowed("a@x.com", "1.1.1.1"));
    }

    @Test
    void successClearsFailureWindow() {
        LoginRateLimitService service = new LoginRateLimitService(1, 300);
        service.onFailedLogin("a@x.com", "1.1.1.1");
        service.onSuccessfulLogin("a@x.com", "1.1.1.1");
        assertDoesNotThrow(() -> service.assertAllowed("a@x.com", "1.1.1.1"));
    }

    @Test
    void windowExpiryResetsFailures() throws Exception {
        LoginRateLimitService service = new LoginRateLimitService(1, 1);
        service.onFailedLogin("a@x.com", "1.1.1.1");
        Thread.sleep(1200);
        assertDoesNotThrow(() -> service.assertAllowed("a@x.com", "1.1.1.1"));
    }

    @Test
    void normalizesEmailAndNullIp() {
        LoginRateLimitService service = new LoginRateLimitService(1, 300);
        service.onFailedLogin("  A@X.COM ", null);
        assertThrows(TooManyRequestsException.class, () -> service.assertAllowed("a@x.com", "unknown"));
    }
}
