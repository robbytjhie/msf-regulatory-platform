package com.regulatory.platform.service.impl;

import com.regulatory.platform.dto.request.ApplicationSubmitRequest;
import com.regulatory.platform.dto.request.DocumentReuploadRequest;
import com.regulatory.platform.dto.request.OfficerFeedbackRequest;
import com.regulatory.platform.dto.request.ResubmitRequest;
import com.regulatory.platform.dto.response.ApplicationDetailResponse;
import com.regulatory.platform.dto.response.ApplicationSummaryResponse;
import com.regulatory.platform.dto.response.DocumentResponse;
import com.regulatory.platform.entity.*;
import com.regulatory.platform.enums.ApplicationStatus;
import com.regulatory.platform.enums.ChecklistItemStatus;
import com.regulatory.platform.enums.LicensingTrack;
import com.regulatory.platform.enums.NotificationType;
import com.regulatory.platform.enums.UserRole;
import com.regulatory.platform.exception.ApplicationNotFoundException;
import com.regulatory.platform.exception.ForbiddenOperationException;
import com.regulatory.platform.exception.InvalidRequestException;
import com.regulatory.platform.exception.InvalidStatusTransitionException;
import com.regulatory.platform.repository.*;
import com.regulatory.platform.service.ApplicationService;
import com.regulatory.platform.service.DocumentVerificationService;
import com.regulatory.platform.service.NotificationService;
import com.regulatory.platform.service.StatusTransitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final OfficerCommentRepository officerCommentRepository;
    private final DocumentRepository documentRepository;
    private final StatusTransitionService statusTransitionService;
    private final NotificationService notificationService;
    private final DocumentVerificationService documentVerificationService;
    private final ChecklistItemRepository checklistItemRepository;

    // ── UC1: Operator Submission ──────────────────────────────────

    @Override
    public ApplicationDetailResponse submit(ApplicationSubmitRequest request, User operator) {
        validateRequiredDocuments(request);
        String refNum = generateReferenceNumber();

        Application application = Application.builder()
                .referenceNumber(refNum)
                .operator(operator)
                .status(ApplicationStatus.APPLICATION_RECEIVED)
                .businessName(request.businessName())
                .licensingTrack(request.licensingTrack())
                .businessType(request.businessType())
                .businessAddress(request.businessAddress())
                .contactPhone(request.contactPhone())
                .activityDescription(request.activityDescription())
                .submissionRound(1)
                .build();

        application = applicationRepository.save(application);

        if (request.documents() != null) {
            for (ApplicationSubmitRequest.DocumentUploadRequest docReq : request.documents()) {
                Document doc = Document.builder()
                        .application(application)
                        .originalFileName(docReq.originalFileName())
                        .storedFileName("simulated-" + UUID.randomUUID())
                        .contentType(docReq.contentType() != null ? docReq.contentType() : "application/octet-stream")
                        .fileSizeBytes(docReq.fileSizeBytes())
                        .documentCategory(docReq.documentCategory())
                        .submissionRound(application.getSubmissionRound())
                        .aiVerificationStatus(Document.AiVerificationStatus.PENDING)
                        .aiVerificationNotes("Verification queued")
                        .build();
                application.getDocuments().add(doc);
            }
            application = applicationRepository.save(application);
            if (documentVerificationService.isAiVerificationEnabled()) {
                documentVerificationService.startSimulatedVerification(application.getId());
            } else {
                ApplicationStatus previousStatus = application.getStatus();
                application.setStatus(ApplicationStatus.MANUAL_OFFICER_VALIDATION);
                application = applicationRepository.save(application);
                recordStatusHistory(application, previousStatus, ApplicationStatus.MANUAL_OFFICER_VALIDATION, operator,
                        "AI unavailable. Routed to manual officer validation.");
                notificationService.create(
                        application.getOperator(),
                        application,
                        NotificationType.STATUS_CHANGE,
                        "Application " + application.getReferenceNumber() + " routed to manual officer validation."
                );
            }
        }

        recordStatusHistory(application, null, ApplicationStatus.APPLICATION_RECEIVED, operator,
                "Initial submission");

        log.info("Application {} submitted by operator {}", refNum, operator.getEmail());
        initializeCollections(application);
        return ApplicationDetailResponse.forOperator(application);
    }

    @Override
    public ApplicationDetailResponse resubmit(Long applicationId, ResubmitRequest request, User operator) {
        Application application = findWithUsersOrThrow(applicationId);
        assertOperatorOwns(application, operator);

        if (application.getStatus() != ApplicationStatus.PENDING_PRE_SITE_RESUBMISSION
                && application.getStatus() != ApplicationStatus.PENDING_POST_SITE_RESUBMISSION) {
            throw new InvalidStatusTransitionException(
                    "Application is not awaiting resubmission. Current status: " + application.getStatus());
        }

        ApplicationStatus targetStatus = application.getStatus() == ApplicationStatus.PENDING_PRE_SITE_RESUBMISSION
                ? ApplicationStatus.PRE_SITE_RESUBMITTED
                : ApplicationStatus.POST_SITE_CLARIFICATION_RESUBMITTED;

        statusTransitionService.validate(application.getStatus(), targetStatus, UserRole.OPERATOR);

        if (request.businessName() != null)        application.setBusinessName(request.businessName());
        if (request.businessAddress() != null)     application.setBusinessAddress(request.businessAddress());
        if (request.contactPhone() != null)        application.setContactPhone(request.contactPhone());
        if (request.activityDescription() != null) application.setActivityDescription(request.activityDescription());

        ApplicationStatus previousStatus = application.getStatus();
        application.setStatus(targetStatus);
        application.setSubmissionRound(application.getSubmissionRound() + 1);

        officerCommentRepository
                .findByApplicationIdAndResolved(applicationId, false)
                .forEach(c -> c.setResolved(true));

        applicationRepository.save(application);
        recordStatusHistory(application, previousStatus, targetStatus, operator,
                "Round %d resubmission".formatted(application.getSubmissionRound()));

        log.info("Application {} resubmitted (round {}) by operator {}",
                application.getReferenceNumber(), application.getSubmissionRound(), operator.getEmail());

        if (application.getAssignedOfficer() != null) {
            notificationService.create(
                    application.getAssignedOfficer(),
                    application,
                    NotificationType.RESUBMISSION_SUBMITTED,
                    "Application " + application.getReferenceNumber() + " was resubmitted by operator."
            );
        }

        initializeCollections(application);
        return ApplicationDetailResponse.forOperator(application);
    }

    // ── UC2: Officer Review ───────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ApplicationDetailResponse getForOfficer(Long applicationId, User officer) {
        Application application = findWithUsersOrThrow(applicationId);
        initializeCollections(application);
        return ApplicationDetailResponse.forOfficer(application);
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationDetailResponse getForOperator(Long applicationId, User operator) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException("Application not found: " + applicationId));
        assertOperatorOwns(application, operator);
        initializeCollections(application);
        return ApplicationDetailResponse.forOperator(application);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationSummaryResponse> listForOfficer(User officer) {
        return applicationRepository.findAllWithUsers()
                .stream()
                .map(ApplicationSummaryResponse::forOfficer)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationSummaryResponse> listForOperator(User operator) {
        return applicationRepository.findByOperatorWithUsers(operator)
                .stream()
                .map(ApplicationSummaryResponse::forOperator)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getOperatorDocumentStatuses(Long applicationId, User operator) {
        Application application = findWithUsersOrThrow(applicationId);
        assertOperatorOwns(application, operator);
        Hibernate.initialize(application.getDocuments());
        return application.getDocuments().stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Override
    public DocumentResponse reuploadAndRevalidateDocument(Long applicationId,
                                                          Long documentId,
                                                          DocumentReuploadRequest request,
                                                          User operator) {
        Application application = findWithUsersOrThrow(applicationId);
        assertOperatorOwns(application, operator);
        Hibernate.initialize(application.getDocuments());
        Document doc = application.getDocuments().stream()
                .filter(d -> d.getId().equals(documentId))
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("Document not found for this application: " + documentId));

        doc.setOriginalFileName(request.originalFileName());
        doc.setStoredFileName("simulated-" + UUID.randomUUID());
        doc.setContentType(request.contentType() != null && !request.contentType().isBlank()
                ? request.contentType()
                : "application/octet-stream");
        doc.setFileSizeBytes(request.fileSizeBytes());
        doc.setSubmissionRound(application.getSubmissionRound());
        doc.setAiVerificationStatus(Document.AiVerificationStatus.PENDING);
        doc.setAiVerificationNotes("Re-upload received. Verification queued.");
        documentRepository.save(doc);
        documentVerificationService.startSimulatedVerificationForDocument(doc.getId());
        return DocumentResponse.from(doc);
    }

    @Override
    public ApplicationDetailResponse submitOfficerFeedback(Long applicationId,
                                                            OfficerFeedbackRequest request,
                                                            User officer) {
        Application application = findWithUsersOrThrow(applicationId);
        ApplicationStatus targetStatus = request.newStatus();

        statusTransitionService.validate(application.getStatus(), targetStatus, UserRole.OFFICER);

        if (request.comments() != null) {
            request.comments().forEach(commentReq -> {
                Document targetDocument = null;
                if (commentReq.targetDocumentId() != null) {
                    targetDocument = application.getDocuments().stream()
                            .filter(d -> d.getId().equals(commentReq.targetDocumentId()))
                            .findFirst()
                            .orElseThrow(() -> new InvalidRequestException(
                                    "Target document does not belong to this application: " + commentReq.targetDocumentId()));
                }
                OfficerComment comment = OfficerComment.builder()
                        .application(application)
                        .officer(officer)
                        .commentText(commentReq.commentText())
                        .targetSection(commentReq.targetSection())
                        .targetDocument(targetDocument)
                        .submissionRound(application.getSubmissionRound())
                        .resolved(false)
                        .build();
                officerCommentRepository.save(comment);
            });
        }

        ApplicationStatus previousStatus = application.getStatus();
        application.setStatus(targetStatus);
        if (application.getAssignedOfficer() == null) {
            application.setAssignedOfficer(officer);
        }

        applicationRepository.save(application);
        recordStatusHistory(application, previousStatus, targetStatus, officer, request.statusNotes());

        if (targetStatus == ApplicationStatus.SITE_VISIT_SCHEDULED) {
            seedDefaultSiteVisitChecklistIfEmpty(application);
        }

        log.info("Officer {} set application {} status to {}",
                officer.getEmail(), application.getReferenceNumber(), targetStatus);

        notificationService.create(
                application.getOperator(),
                application,
                NotificationType.STATUS_CHANGE,
                "Application " + application.getReferenceNumber() + " status updated to "
                        + (targetStatus.getOperatorLabel() != null ? targetStatus.getOperatorLabel() : "Under Review")
        );

        // Re-fetch so newly saved comments are visible
        Application refreshed = findWithUsersOrThrow(applicationId);
        initializeCollections(refreshed);
        return ApplicationDetailResponse.forOfficer(refreshed);
    }

    // ── Helpers ───────────────────────────────────────────────────

    /**
     * Loads application with operator + assignedOfficer users eagerly via JPQL.
     */
    private Application findWithUsersOrThrow(Long id) {
        return applicationRepository.findByIdWithUsers(id)
                .orElseThrow(() -> new ApplicationNotFoundException("Application not found: " + id));
    }

    /**
     * Force-initializes all lazy collections while still inside the transaction.
     * This prevents LazyInitializationException when Jackson serializes the response.
     */
    private void initializeCollections(Application app) {
        // Initialize collections
        Hibernate.initialize(app.getDocuments());
        Hibernate.initialize(app.getOfficerComments());
        Hibernate.initialize(app.getStatusHistory());
        Hibernate.initialize(app.getChecklistItems());

        // Initialize nested associations on each collection element
        app.getOfficerComments().forEach(c -> {
            Hibernate.initialize(c.getOfficer());
            if (c.getTargetDocument() != null) Hibernate.initialize(c.getTargetDocument());
        });
        app.getStatusHistory().forEach(h -> {
            if (h.getChangedBy() != null) Hibernate.initialize(h.getChangedBy());
        });
        app.getChecklistItems().forEach(item ->
                Hibernate.initialize(item.getClarificationThreads()));
    }

    private void assertOperatorOwns(Application application, User operator) {
        if (!application.getOperator().getId().equals(operator.getId())) {
            throw new ForbiddenOperationException("You do not have access to this application");
        }
    }

    private void validateRequiredDocuments(ApplicationSubmitRequest request) {
        if (request.documents() == null || request.documents().isEmpty()) {
            throw new InvalidRequestException("Please upload required document category: REGISTRATION_DOC");
        }
        Set<String> submitted = request.documents().stream()
                .map(ApplicationSubmitRequest.DocumentUploadRequest::documentCategory)
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> missing = new java.util.LinkedHashSet<>(requiredDocCategoriesForTrack(request.licensingTrack()));
        missing.removeAll(submitted);
        if (!missing.isEmpty()) {
            throw new InvalidRequestException("Missing required document categories: " + String.join(", ", missing));
        }
    }

    private Set<String> requiredDocCategoriesForTrack(LicensingTrack track) {
        return switch (track) {
            case ECDC -> Set.of("REGISTRATION_DOC", "FLOOR_PLAN");
            case SCFA -> Set.of("REGISTRATION_DOC", "ATTENDANCE_LOG");
            case HFAA -> Set.of("REGISTRATION_DOC", "STAFF_ROSTER");
            case CHILDMINDING -> Set.of("REGISTRATION_DOC", "HOME_SAFETY_PHOTOS");
        };
    }

    /**
     * When a site visit is scheduled, ensure officers have starter checklist rows (demo / MVP template).
     * Replace or extend with configurable templates in a full product.
     */
    private void seedDefaultSiteVisitChecklistIfEmpty(Application application) {
        if (!checklistItemRepository.findByApplicationIdOrderBySortOrderAsc(application.getId()).isEmpty()) {
            return;
        }
        List<ChecklistItem> baseItems = new ArrayList<>(List.of(
                ChecklistItem.builder().application(application).itemCode("SITE_01")
                        .itemTitle("Site access and safety briefing").sortOrder(1)
                        .itemDescription("Confirm access arrangements and safety briefing with the operator.")
                        .status(ChecklistItemStatus.PENDING).build()
        ));
        List<ChecklistItem> trackItems = switch (application.getLicensingTrack()) {
            case ECDC -> List.of(
                    ChecklistItem.builder().application(application).itemCode("ECDC_01")
                            .itemTitle("Spatial capacity verification").sortOrder(2)
                            .itemDescription("Verify indoor/outdoor play space adequacy and child-capacity fit.")
                            .status(ChecklistItemStatus.PENDING).build(),
                    ChecklistItem.builder().application(application).itemCode("ECDC_02")
                            .itemTitle("Safety and CCTV measures").sortOrder(3)
                            .itemDescription("Check age-appropriate furnishings, hazard controls, and CCTV installation.")
                            .status(ChecklistItemStatus.PENDING).build(),
                    ChecklistItem.builder().application(application).itemCode("ECDC_03")
                            .itemTitle("Clarification evidence closure").sortOrder(4)
                            .itemDescription("Track revised floor plans or proof of completed remedial safety works.")
                            .status(ChecklistItemStatus.PENDING).build()
            );
            case SCFA -> List.of(
                    ChecklistItem.builder().application(application).itemCode("SCFA_01")
                            .itemTitle("Attendance logs vs subsidy claims").sortOrder(2)
                            .itemDescription("On-site verify attendance records against submitted subsidy claims.")
                            .status(ChecklistItemStatus.PENDING).build(),
                    ChecklistItem.builder().application(application).itemCode("SCFA_02")
                            .itemTitle("Environment standards").sortOrder(3)
                            .itemDescription("Check lighting, ventilation, and site supervision conditions.")
                            .status(ChecklistItemStatus.PENDING).build(),
                    ChecklistItem.builder().application(application).itemCode("SCFA_03")
                            .itemTitle("Clarification evidence closure").sortOrder(4)
                            .itemDescription("Track updated subsidy withdrawal forms or amended attendance records.")
                            .status(ChecklistItemStatus.PENDING).build()
            );
            case HFAA -> List.of(
                    ChecklistItem.builder().application(application).itemCode("HFAA_01")
                            .itemTitle("Staff-to-resident ratio verification").sortOrder(2)
                            .itemDescription("Verify on-duty staff against approved roster and resident-care needs.")
                            .status(ChecklistItemStatus.PENDING).build(),
                    ChecklistItem.builder().application(application).itemCode("HFAA_02")
                            .itemTitle("Sanitation and premises safety").sortOrder(3)
                            .itemDescription("Inspect common areas, toilets, and kitchen hygiene for sanitary compliance.")
                            .status(ChecklistItemStatus.PENDING).build(),
                    ChecklistItem.builder().application(application).itemCode("HFAA_03")
                            .itemTitle("Clarification evidence closure").sortOrder(4)
                            .itemDescription("Track updated fire safety certificates or staff medical screening records.")
                            .status(ChecklistItemStatus.PENDING).build()
            );
            case CHILDMINDING -> List.of(
                    ChecklistItem.builder().application(application).itemCode("CM_01")
                            .itemTitle("Home safety and child-proofing").sortOrder(2)
                            .itemDescription("Check child-proofing controls, hygiene setup, and safety equipment.")
                            .status(ChecklistItemStatus.PENDING).build(),
                    ChecklistItem.builder().application(application).itemCode("CM_02")
                            .itemTitle("Home capacity suitability").sortOrder(3)
                            .itemDescription("Confirm the home can safely accommodate the proposed number of infants.")
                            .status(ChecklistItemStatus.PENDING).build(),
                    ChecklistItem.builder().application(application).itemCode("CM_03")
                            .itemTitle("Clarification evidence closure").sortOrder(4)
                            .itemDescription("Track new safety-gate photos or revised equipment inventory evidence.")
                            .status(ChecklistItemStatus.PENDING).build()
            );
        };
        baseItems.addAll(trackItems);
        checklistItemRepository.saveAll(baseItems);
        log.info("Seeded default site-visit checklist for application {}", application.getReferenceNumber());
    }

    private void recordStatusHistory(Application application,
                                     ApplicationStatus from,
                                     ApplicationStatus to,
                                     User changedBy,
                                     String notes) {
        StatusHistory history = StatusHistory.builder()
                .application(application)
                .fromStatus(from)
                .toStatus(to)
                .changedBy(changedBy)
                .notes(notes)
                .build();
        statusHistoryRepository.save(history);
    }

    private String generateReferenceNumber() {
        String candidate;
        do {
            candidate = "LIC-" + LocalDateTime.now().getYear() + "-"
                    + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        } while (applicationRepository.existsByReferenceNumber(candidate));
        return candidate;
    }
}
