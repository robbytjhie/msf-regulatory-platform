package com.regulatory.platform.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerIpExtractionTest {

    @Test
    void prefersXForwardedForFirstIp() {
        AuthController controller = new AuthController(null, null, null, null, null);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");
        String ip = (String) ReflectionTestUtils.invokeMethod(controller, "extractClientIp", request);
        assertThat(ip).isEqualTo("203.0.113.5");
    }

    @Test
    void fallsBackToXRealIpThenRemoteAddr() {
        AuthController controller = new AuthController(null, null, null, null, null);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(" ");
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.7");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        String ip = (String) ReflectionTestUtils.invokeMethod(controller, "extractClientIp", request);
        assertThat(ip).isEqualTo("198.51.100.7");

        when(request.getHeader("X-Real-IP")).thenReturn("");
        String fallback = (String) ReflectionTestUtils.invokeMethod(controller, "extractClientIp", request);
        assertThat(fallback).isEqualTo("127.0.0.1");
    }
}
