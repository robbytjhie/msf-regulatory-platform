package com.regulatory.platform.config;

import com.regulatory.platform.service.ApiAuditLogService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class RequestLoggingConfig {
    private final ApiAuditLogService apiAuditLogService;

    /**
     * Logs every inbound request: method, URI, origin, status, duration.
     * Helps diagnose CORS preflight failures (OPTIONS requests) and auth issues.
     */
    @Bean
    public OncePerRequestFilter httpRequestResponseLogger() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                long start = System.currentTimeMillis();
                String origin = request.getHeader("Origin");
                String auth   = request.getHeader("Authorization");

                try {
                    chain.doFilter(request, response);
                } finally {
                    long ms = System.currentTimeMillis() - start;
                    log.info("[HTTP] {} {} | Origin: {} | Auth: {} | Status: {} | {}ms",
                            request.getMethod(),
                            request.getRequestURI(),
                            origin != null ? origin : "none",
                            auth  != null ? "Bearer ***" : "none",
                            response.getStatus(),
                            ms);

                    // Warn explicitly on CORS-blocked preflights
                    if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                            && response.getStatus() == 403) {
                        log.warn("[CORS] Preflight BLOCKED for Origin: {} → URI: {}",
                                origin, request.getRequestURI());
                    }
                    // Warn on auth failures
                    if (response.getStatus() == 401 || response.getStatus() == 403) {
                        log.warn("[AUTH] {} {} returned {} for Origin: {}",
                                request.getMethod(), request.getRequestURI(),
                                response.getStatus(), origin);
                    }

                    if (request.getRequestURI().startsWith("/api/")) {
                        apiAuditLogService.record(
                                request.getMethod(),
                                request.getRequestURI(),
                                response.getStatus(),
                                ms,
                                extractClientIp(request),
                                origin,
                                resolveUserEmail(),
                                request.getHeader("User-Agent")
                        );
                    }
                }
            }
        };
    }

    private String resolveUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return auth.getName();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Spring's built-in verbose request logger — logs query params and headers.
     * Activate by setting:
     *   logging.level.org.springframework.web.filter.CommonsRequestLoggingFilter=DEBUG
     */
    @Bean
    public CommonsRequestLoggingFilter commonsRequestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludeHeaders(true);
        filter.setIncludeClientInfo(true);
        filter.setMaxPayloadLength(1000);
        filter.setAfterMessagePrefix("[REQUEST] ");
        return filter;
    }
}
