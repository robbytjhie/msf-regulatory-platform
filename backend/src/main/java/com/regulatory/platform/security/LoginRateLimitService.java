package com.regulatory.platform.security;

import com.regulatory.platform.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginRateLimitService {

    private static final class AttemptWindow {
        private int failures;
        private long windowStartEpochSec;
    }

    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final long windowSeconds;

    public LoginRateLimitService(
            @Value("${app.security.login-rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${app.security.login-rate-limit.window-seconds:300}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
    }

    public void assertAllowed(String email, String clientIp) {
        String key = toKey(email, clientIp);
        AttemptWindow w = attempts.get(key);
        if (w == null) return;

        long now = Instant.now().getEpochSecond();
        if (now - w.windowStartEpochSec >= windowSeconds) {
            attempts.remove(key);
            return;
        }
        if (w.failures >= maxAttempts) {
            throw new TooManyRequestsException("Too many login attempts. Please try again later.");
        }
    }

    public void onFailedLogin(String email, String clientIp) {
        String key = toKey(email, clientIp);
        long now = Instant.now().getEpochSecond();

        attempts.compute(key, (k, existing) -> {
            if (existing == null || (now - existing.windowStartEpochSec) >= windowSeconds) {
                AttemptWindow fresh = new AttemptWindow();
                fresh.windowStartEpochSec = now;
                fresh.failures = 1;
                return fresh;
            }
            existing.failures++;
            return existing;
        });
    }

    public void onSuccessfulLogin(String email, String clientIp) {
        attempts.remove(toKey(email, clientIp));
    }

    private String toKey(String email, String clientIp) {
        return (email == null ? "" : email.trim().toLowerCase()) + "|" + (clientIp == null ? "unknown" : clientIp);
    }
}
