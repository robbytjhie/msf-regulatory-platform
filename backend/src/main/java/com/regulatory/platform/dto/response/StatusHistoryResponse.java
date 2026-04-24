package com.regulatory.platform.dto.response;

import com.regulatory.platform.entity.StatusHistory;

import java.time.LocalDateTime;

public record StatusHistoryResponse(
        String fromStatusLabel,
        String toStatusLabel,
        String changedByName,
        String notes,
        LocalDateTime changedAt
) {
    public static StatusHistoryResponse forOfficer(StatusHistory h) {
        return new StatusHistoryResponse(
                h.getFromStatus() != null ? h.getFromStatus().getOfficerLabel() : null,
                h.getToStatus().getOfficerLabel(),
                h.getChangedBy() != null ? h.getChangedBy().getFullName() : "System",
                h.getNotes(),
                h.getChangedAt()
        );
    }

    public static StatusHistoryResponse forOperator(StatusHistory h) {
        return new StatusHistoryResponse(
                h.getFromStatus() != null ? h.getFromStatus().getOperatorLabel() : null,
                h.getToStatus().getOperatorLabel(),
                null,   // operator doesn't see who made the change
                null,   // operator doesn't see internal notes
                h.getChangedAt()
        );
    }
}
