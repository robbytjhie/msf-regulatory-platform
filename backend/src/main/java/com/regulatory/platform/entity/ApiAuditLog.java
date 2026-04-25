package com.regulatory.platform.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_audit_logs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 512)
    private String path;

    @Column(nullable = false)
    private int statusCode;

    @Column(nullable = false)
    private long durationMs;

    @Column(length = 128)
    private String clientIp;

    @Column(length = 255)
    private String origin;

    @Column(length = 255)
    private String userEmail;

    @Column(length = 512)
    private String userAgent;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
