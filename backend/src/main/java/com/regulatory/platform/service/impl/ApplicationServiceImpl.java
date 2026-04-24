package com.regulatory.platform.service.impl;

import com.regulatory.platform.dto.request.ApplicationSubmitRequest;
import com.regulatory.platform.dto.request.OfficerFeedbackRequest;
import com.regulatory.platform.dto.request.ResubmitRequest;
import com.regulatory.platform.dto.response.ApplicationDetailResponse;
import com.regulatory.platform.dto.response.ApplicationSummaryResponse;
import com.regulatory.platform.entity.*;
import com.regulatory.platform.enums.ApplicationStatus;
import com.regulatory.platform.enums.NotificationType;
import com.regulatory.platform.enums.UserRole;
import com.regulatory.platform.exception.ApplicationNotFoundException;
import com.regulatory.platform.exception.ForbiddenOperationException;
import com.regulatory.platform.exception.InvalidStatusTransitionException;
import com.regulatory.platform.repository.*;
import com.regulatory.platform.service.ApplicationService;
import com.regulatory.platform.service.NotificationService;
import com.regulatory.platform.service.StatusTransitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final OfficerCommentRepository officerCommentRepository;
    private final StatusTransitionService statusTransitionService;
    private final NotificationService notificationService;

    // ── UC1: Operator Submission ──────────────────────────────────

    @Override
    public ApplicationDetailResponse submit(ApplicationSubmitRequest request, User operator) {
        String refNum = generateReferenceNumber();

        Application application = Application.builder()
                .referenceNumber(refNum)
                .operator(operator)
                .status(ApplicationStatus.APPLICATION_RECEIVED)
                .businessName(request.businessName())
                .businessType(request.businessType())
                .businessAddress(request.businessAddress())
                .contactPhone(request.contactPhone())
                .activityDescription(request.activityDescription())
                .submissionRound(1)
                .build();

        application = applicationRepository.save(application);
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
    public ApplicationDetailResponse submitOfficerFeedback(Long applicationId,
                                                            OfficerFeedbackRequest request,
                                                            User officer) {
        Application application = findWithUsersOrThrow(applicationId);
        ApplicationStatus targetStatus = request.newStatus();

        statusTransitionService.validate(application.getStatus(), targetStatus, UserRole.OFFICER);

        if (request.comments() != null) {
            request.comments().forEach(commentReq -> {
                OfficerComment comment = OfficerComment.builder()
                        .application(application)
                        .officer(officer)
                        .commentText(commentReq.commentText())
                        .targetSection(commentReq.targetSection())
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
