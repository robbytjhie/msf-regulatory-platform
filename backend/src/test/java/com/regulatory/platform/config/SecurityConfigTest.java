package com.regulatory.platform.config;

import com.regulatory.platform.security.JwtAuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(mock(JwtAuthFilter.class), mock(org.springframework.security.core.userdetails.UserDetailsService.class));
        ReflectionTestUtils.setField(securityConfig, "allowedOriginPatterns", "http://localhost:*,http://127.0.0.1:*");
        ReflectionTestUtils.setField(securityConfig, "relaxPrivateNetworkOrigins", true);
    }

    @Test
    void returnsBaseCorsWhenOriginHeaderMissing() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        CorsConfiguration config = source.getCorsConfiguration(request);
        assertThat(config).isNotNull();
        assertThat(config.getAllowedOriginPatterns()).contains("http://localhost:*");
    }

    @Test
    void allowsMatchingPatternOrigin() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Origin", "http://localhost:5173");
        CorsConfiguration config = source.getCorsConfiguration(request);
        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins()).containsExactly("http://localhost:5173");
    }

    @Test
    void allowsPrivateNetworkOriginWhenRelaxEnabled() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Origin", "http://10.10.10.10:3000");
        CorsConfiguration config = source.getCorsConfiguration(request);
        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins()).containsExactly("http://10.10.10.10:3000");
    }

    @Test
    void rejectsNonMatchingOriginWhenRelaxDisabled() {
        ReflectionTestUtils.setField(securityConfig, "relaxPrivateNetworkOrigins", false);
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Origin", "http://evil.example");
        CorsConfiguration config = source.getCorsConfiguration(request);
        assertThat(config).isNull();
    }
}
