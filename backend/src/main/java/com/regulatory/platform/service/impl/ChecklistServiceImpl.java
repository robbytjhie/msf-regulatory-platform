package com.regulatory.platform.service.impl;

import com.regulatory.platform.dto.request.ChecklistSubmitRequest;
import com.regulatory.platform.dto.request.OperatorClarificationRequest;
import com.regulatory.platform.dto.response.ChecklistItemResponse;
import com.regulatory.platform.entity.*;
import com.regulatory.platform.enums.ApplicationStatus;
import com.regulatory.platform.enums.ChecklistItemStatus;
import com.regulatory.platform.enums.NotificationType;
import com.regulatory.platform.enums.UserRole;
import com.regulatory.platform.exception.ApplicationNotFoundException;
import com.regulatory.platform.exception.ForbiddenOperationException;
import com.regulatory.platform.exception.ResourceNotFoundException;
import com.regulatory.platform.repository.*;
import com.regulatory.platform.service.ChecklistService;
import com.regulatory.platform.service.NotificationService;
import com.regulatory.platform.service.StatusTransitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChecklistServiceImpl implements ChecklistService {

    private final ApplicationRepository applicationRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final ClarificationThreadRepository clarificationThreadRepository;
    private final StatusTransitionService statusTransitionService;
    private final StatusHistoryRepository statusHistoryRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public List<ChecklistItemResponse> getFullChecklist(Long applicationId, User officer) {
        Application app = findApplicationOrThrow(applicationId);
        assertSiteVisitScheduled(app);
        return checklistItemRepository.findByApplicationIdOrderBySortOrderAsc(applicationId)
                .stream()
                .peek(item -> Hibernate.initialize(item.getClarificationThreads()))
                .map(ChecklistItemResponse::full)
                .toList();
    }

    @Override
    public void saveDraft(Long applicationId, ChecklistSubmitRequest request, User officer) {
        Application app = findApplicationOrThrow(applicationId);
        assertSiteVisitScheduled(app);
        applyChecklistUpdates(applicationId, request, true);
        log.info("Draft checklist saved for application {} by officer {}", applicationId, officer.getEmail());
    }

    @Override
    public void submitChecklist(Long applicationId, ChecklistSubmitRequest request, User officer) {
        Application app = findApplicationOrThrow(applicationId);
        assertSiteVisitScheduled(app);

        applyChecklistUpdates(applicationId, request, false);

        boolean hasFlaggedItems = !checklistItemRepository
                .findByApplicationIdAndStatusOrderBySortOrderAsc(applicationId, ChecklistItemStatus.NEEDS_CLARIFICATION)
                .isEmpty();

        ApplicationStatus targetStatus = hasFlaggedItems
                ? ApplicationStatus.AWAITING_POST_SITE_CLARIFICATION
                : ApplicationStatus.PENDING_APPROVAL;

        statusTransitionService.validate(app.getStatus(), targetStatus, UserRole.OFFICER);

        ApplicationStatus previousStatus = app.getStatus();
        app.setStatus(targetStatus);
        applicationRepository.save(app);

        StatusHistory history = StatusHistory.builder()
                .application(app)
                .fromStatus(previousStatus)
                .toStatus(targetStatus)
                .changedBy(officer)
                .notes("Checklist submitted. Flagged items: " + (hasFlaggedItems ? "yes" : "none"))
                .build();
        statusHistoryRepository.save(history);

        log.info("Checklist submitted for application {} -> status {}", applicationId, targetStatus);

        notificationService.create(
                app.getOperator(),
                app,
                NotificationType.STATUS_CHANGE,
                hasFlaggedItems
                        ? "Site checklist submitted. Clarification is required for selected items."
                        : "Site checklist submitted. Your application is progressing to final review."
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChecklistItemResponse> getFlaggedItemsForOperator(Long applicationId, User operator) {
        Application app = findApplicationOrThrow(applicationId);
        assertOperatorOwns(app, operator);

        // SPEC: Operators see ONLY items flagged for clarification — never the full checklist
        return checklistItemRepository
                .findByApplicationIdAndStatusOrderBySortOrderAsc(applicationId, ChecklistItemStatus.NEEDS_CLARIFICATION)
                .stream()
                .peek(item -> Hibernate.initialize(item.getClarificationThreads()))
                .map(ChecklistItemResponse::flaggedOnly)
                .toList();
    }

    @Override
    public ChecklistItemResponse respondToItem(Long checklistItemId,
                                               OperatorClarificationRequest request,
                                               User operator) {
        ChecklistItem item = checklistItemRepository.findById(checklistItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Checklist item not found: " + checklistItemId));

        // Force-load application.operator to check ownership
        Hibernate.initialize(item.getApplication());
        Hibernate.initialize(item.getApplication().getOperator());
        assertOperatorOwns(item.getApplication(), operator);

        if (item.getStatus() != ChecklistItemStatus.NEEDS_CLARIFICATION) {
            throw new ForbiddenOperationException("This item is not awaiting operator clarification");
        }

        int nextRound = clarificationThreadRepository
                .findByChecklistItemIdOrderByCreatedAtAsc(checklistItemId)
                .stream()
                .mapToInt(ClarificationThread::getClarificationRound)
                .max()
                .orElse(0) + 1;

        ClarificationThread thread = ClarificationThread.builder()
                .checklistItem(item)
                .author(operator)
                .message(request.message())
                .clarificationRound(nextRound)
                .build();
        clarificationThreadRepository.save(thread);

        item.setOperatorResponse(request.message());
        checklistItemRepository.save(item);

        Hibernate.initialize(item.getClarificationThreads());
        log.info("Operator {} responded to checklist item {} (round {})",
                operator.getEmail(), checklistItemId, nextRound);

        if (item.getApplication().getAssignedOfficer() != null) {
            notificationService.create(
                    item.getApplication().getAssignedOfficer(),
                    item.getApplication(),
                    NotificationType.CHECKLIST_RESPONSE,
                    "Operator responded to flagged checklist item " + item.getItemCode()
            );
        }

        return ChecklistItemResponse.flaggedOnly(item);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private void applyChecklistUpdates(Long applicationId, ChecklistSubmitRequest request, boolean isDraft) {
        request.items().forEach(itemReq -> {
            ChecklistItem item = checklistItemRepository.findById(itemReq.itemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Checklist item not found: " + itemReq.itemId()));

            if (!item.getApplication().getId().equals(applicationId)) {
                throw new ForbiddenOperationException("Checklist item does not belong to this application");
            }

            item.setStatus(itemReq.status());
            item.setOfficerComment(itemReq.officerComment());
            item.setDraftSaved(isDraft);
            checklistItemRepository.save(item);
        });
    }

    private Application findApplicationOrThrow(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException("Application not found: " + id));
    }

    private void assertSiteVisitScheduled(Application app) {
        if (app.getStatus() != ApplicationStatus.SITE_VISIT_SCHEDULED
                && app.getStatus() != ApplicationStatus.SITE_VISIT_DONE) {
            throw new ForbiddenOperationException(
                    "Checklist is only accessible after site visit is scheduled");
        }
    }

    private void assertOperatorOwns(Application application, User operator) {
        if (!application.getOperator().getId().equals(operator.getId())) {
            throw new ForbiddenOperationException("You do not have access to this application");
        }
    }
}
