package com.regulatory.platform.security;

import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    @Test
    void generatesAndValidatesToken_withPlainTextSecret() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "dev-only-change-me-very-long-secret-key-2026");
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3600000L);

        User user = new User("officer@gov.sg", "pw", Collections.emptyList());
        String token = jwtService.generateToken(user);

        assertNotNull(token);
        assertEquals("officer@gov.sg", jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token, user));
    }

    @Test
    void generatesAndValidatesToken_withBase64Secret() {
        JwtService jwtService = new JwtService();
        String base64Secret = Encoders.BASE64.encode("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(jwtService, "secretKey", base64Secret);
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3600000L);

        User user = new User("operator@acme.sg", "pw", Collections.emptyList());
        String token = jwtService.generateToken(user);

        assertNotNull(token);
        assertEquals("operator@acme.sg", jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token, user));
    }
}
