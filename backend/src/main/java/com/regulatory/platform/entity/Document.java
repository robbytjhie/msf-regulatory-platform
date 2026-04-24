package com.regulatory.platform.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false)
    private String storedFileName;

    @Column(nullable = false)
    private String contentType;

    private Long fileSizeBytes;

    private String documentCategory;

    @Column(nullable = false)
    @Builder.Default
    private int submissionRound = 1;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AiVerificationStatus aiVerificationStatus = AiVerificationStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String aiVerificationNotes;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum AiVerificationStatus {
        PENDING, PROCESSING, PASSED, FLAGGED, FAILED
    }
}
