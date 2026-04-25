package com.regulatory.platform.config;

import com.regulatory.platform.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    @Value("${app.security.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*,http://192.168.*:*,http://10.*:*}")
    private String allowedOriginPatterns;

    @Value("${app.security.cors.relax-private-network-origins:true}")
    private boolean relaxPrivateNetworkOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Operator endpoints
                        .requestMatchers("/api/operator/**").hasRole("OPERATOR")
                        // Officer endpoints
                        .requestMatchers("/api/officer/**").hasRole("OFFICER")
                        // Shared authenticated notifications
                        .requestMatchers("/api/notifications/**").hasAnyRole("OPERATOR", "OFFICER", "ADMIN")
                        // Admin
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .headers(h -> h
                        .frameOptions(f -> f.sameOrigin()) // Allow H2 console iframes
                        .contentTypeOptions(c -> {})
                        .xssProtection(x -> {})
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; frame-ancestors 'none'; object-src 'none'"))
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> patterns = Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        CorsConfiguration patternProbe = new CorsConfiguration();
        patternProbe.setAllowedOriginPatterns(patterns);
        patternProbe.setAllowCredentials(true);

        return request -> {
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            if (!StringUtils.hasText(origin)) {
                return baseCorsWithPatterns(patterns);
            }
            String resolvedOrigin = patternProbe.checkOrigin(origin);
            if (resolvedOrigin == null && relaxPrivateNetworkOrigins && isPrivateNetworkDevOrigin(origin)) {
                resolvedOrigin = origin;
            }
            if (resolvedOrigin == null) {
                return null;
            }
            CorsConfiguration config = baseCorsWithPatterns(List.of());
            config.setAllowedOrigins(List.of(resolvedOrigin));
            config.setAllowedOriginPatterns(List.of());
            return config;
        };
    }

    /**
     * Host prefixes where we echo Origin when Spring pattern matching fails (e.g. some
     * {@code http://127.0.0.1:&lt;random port&gt;} Minikube tunnel URLs).
     */
    private static boolean isPrivateNetworkDevOrigin(String origin) {
        return origin.startsWith("http://127.0.0.1:")
                || origin.startsWith("http://localhost:")
                || origin.startsWith("http://192.168.")
                || origin.startsWith("http://10.")
                || origin.startsWith("http://[::1]:");
    }

    private static CorsConfiguration baseCorsWithPatterns(List<String> patterns) {
        CorsConfiguration config = new CorsConfiguration();
        if (!patterns.isEmpty()) {
            config.setAllowedOriginPatterns(patterns);
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        return config;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
