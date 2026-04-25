package com.regulatory.platform.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "application_round_snapshots")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApplicationRoundSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(nullable = false)
    private int submissionRound;

    @Column(nullable = false)
    private String businessName;

    private String businessType;

    @Column(columnDefinition = "TEXT")
    private String businessAddress;

    private String contactPhone;

    @Column(columnDefinition = "TEXT")
    private String activityDescription;

    @Column(columnDefinition = "TEXT")
    private String documentSummary;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
