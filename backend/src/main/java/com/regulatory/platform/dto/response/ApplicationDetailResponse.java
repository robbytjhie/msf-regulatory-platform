package com.regulatory.platform.dto.response;

import com.regulatory.platform.entity.Application;
import com.regulatory.platform.entity.OfficerComment;
import com.regulatory.platform.entity.StatusHistory;
import com.regulatory.platform.enums.ApplicationStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full application detail view.
 * Two factory methods enforce role-specific field visibility:
 *   - forOfficer: sees internal status label, all comments, full history
 *   - forOperator: sees mapped operator label, only unresolved comments relevant to them
 *
 * SPEC: Operators cannot see PENDING_APPROVAL internal stage.
 */
public record ApplicationDetailResponse(
        Long id,
        String referenceNumber,
        String statusLabel,         // role-specific label from spec status mapping
        ApplicationStatus internalStatus, // null for operator responses
        String businessName,
        String businessType,
        String businessAddress,
        String contactPhone,
        String activityDescription,
        int submissionRound,
        String operatorName,
        String operatorEmail,
        String assignedOfficerName,
        LocalDateTime submittedAt,
        LocalDateTime lastModifiedAt,
        List<DocumentResponse> documents,
        List<OfficerCommentResponse> officerComments,
        List<StatusHistoryResponse> statusHistory
) {

    public static ApplicationDetailResponse forOfficer(Application app) {
        return new ApplicationDetailResponse(
                app.getId(),
                app.getReferenceNumber(),
                app.getStatus().getOfficerLabel(),
                app.getStatus(),                          // officer sees internal enum
                app.getBusinessName(),
                app.getBusinessType(),
                app.getBusinessAddress(),
                app.getContactPhone(),
                app.getActivityDescription(),
                app.getSubmissionRound(),
                app.getOperator().getFullName(),
                app.getOperator().getEmail(),
                app.getAssignedOfficer() != null ? app.getAssignedOfficer().getFullName() : null,
                app.getSubmittedAt(),
                app.getLastModifiedAt(),
                app.getDocuments().stream().map(DocumentResponse::from).toList(),
                app.getOfficerComments().stream().map(OfficerCommentResponse::from).toList(),
                app.getStatusHistory().stream().map(StatusHistoryResponse::forOfficer).toList()
        );
    }

    public static ApplicationDetailResponse forOperator(Application app) {
        return new ApplicationDetailResponse(
                app.getId(),
                app.getReferenceNumber(),
                app.getStatus().getOperatorLabel(),
                null,                                     // SPEC: internal status never exposed to operator
                app.getBusinessName(),
                app.getBusinessType(),
                app.getBusinessAddress(),
                app.getContactPhone(),
                app.getActivityDescription(),
                app.getSubmissionRound(),
                app.getOperator().getFullName(),
                app.getOperator().getEmail(),
                null,                                     // operator doesn't see officer assignment
                app.getSubmittedAt(),
                app.getLastModifiedAt(),
                app.getDocuments().stream().map(DocumentResponse::from).toList(),
                // Operator sees all comments (unresolved shown prominently on UI)
                app.getOfficerComments().stream().map(OfficerCommentResponse::from).toList(),
                app.getStatusHistory().stream().map(StatusHistoryResponse::forOperator).toList()
        );
    }
}
