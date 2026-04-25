package com.regulatory.platform.service;

import com.regulatory.platform.entity.ApiAuditLog;
import com.regulatory.platform.repository.ApiAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiAuditLogService {

    private final ApiAuditLogRepository apiAuditLogRepository;

    public void record(String method,
                       String path,
                       int statusCode,
                       long durationMs,
                       String clientIp,
                       String origin,
                       String userEmail,
                       String userAgent) {
        try {
            apiAuditLogRepository.save(ApiAuditLog.builder()
                    .method(method)
                    .path(path)
                    .statusCode(statusCode)
                    .durationMs(durationMs)
                    .clientIp(truncate(clientIp, 128))
                    .origin(truncate(origin, 255))
                    .userEmail(truncate(userEmail, 255))
                    .userAgent(truncate(userAgent, 512))
                    .build());
        } catch (Exception ex) {
            // Never fail API response because of audit logging.
            log.warn("Failed to persist API audit event for {} {}: {}", method, path, ex.getMessage());
        }
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
