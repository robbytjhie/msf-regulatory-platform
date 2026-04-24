package com.regulatory.platform.dto.response;

import com.regulatory.platform.entity.Application;
import com.regulatory.platform.enums.ApplicationStatus;

import java.time.LocalDateTime;

public record ApplicationSummaryResponse(
        Long id,
        String referenceNumber,
        String businessName,
        String statusLabel,
        ApplicationStatus internalStatus,
        int submissionRound,
        String operatorName,
        LocalDateTime lastModifiedAt
) {
    public static ApplicationSummaryResponse forOfficer(Application app) {
        return new ApplicationSummaryResponse(
                app.getId(),
                app.getReferenceNumber(),
                app.getBusinessName(),
                app.getStatus().getOfficerLabel(),
                app.getStatus(),
                app.getSubmissionRound(),
                app.getOperator().getFullName(),
                app.getLastModifiedAt()
        );
    }

    public static ApplicationSummaryResponse forOperator(Application app) {
        return new ApplicationSummaryResponse(
                app.getId(),
                app.getReferenceNumber(),
                app.getBusinessName(),
                app.getStatus().getOperatorLabel(),
                null,   // internal status hidden from operator
                app.getSubmissionRound(),
                null,
                app.getLastModifiedAt()
        );
    }
}
