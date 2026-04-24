package com.regulatory.platform.entity;

import com.regulatory.platform.enums.ChecklistItemStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "checklist_items")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(nullable = false)
    private String itemCode;

    @Column(nullable = false)
    private String itemTitle;

    @Column(columnDefinition = "TEXT")
    private String itemDescription;

    @Column(nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ChecklistItemStatus status = ChecklistItemStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String officerComment;

    @Column(columnDefinition = "TEXT")
    private String operatorResponse;

    @Builder.Default
    private boolean draftSaved = false;

    @LastModifiedDate
    @Column(name = "last_modified_at")
    private LocalDateTime lastModifiedAt;

    // ClarificationThread.createdAt is the correct JPQL field name
    @OneToMany(mappedBy = "checklistItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<ClarificationThread> clarificationThreads = new ArrayList<>();
}
