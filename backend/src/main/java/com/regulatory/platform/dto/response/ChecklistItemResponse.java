package com.regulatory.platform.dto.response;

import com.regulatory.platform.entity.ChecklistItem;
import com.regulatory.platform.entity.ClarificationThread;
import com.regulatory.platform.enums.ChecklistItemStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ChecklistItemResponse(
        Long id,
        String itemCode,
        String itemTitle,
        String itemDescription,
        int sortOrder,
        ChecklistItemStatus status,
        String officerComment,       // null in operator view for non-flagged items
        String operatorResponse,
        boolean draftSaved,
        LocalDateTime lastModifiedAt,
        List<ClarificationThreadResponse> clarificationThreads
) {

    /**
     * Full view for officers — includes all fields.
     */
    public static ChecklistItemResponse full(ChecklistItem item) {
        return new ChecklistItemResponse(
                item.getId(),
                item.getItemCode(),
                item.getItemTitle(),
                item.getItemDescription(),
                item.getSortOrder(),
                item.getStatus(),
                item.getOfficerComment(),
                item.getOperatorResponse(),
                item.isDraftSaved(),
                item.getLastModifiedAt(),
                item.getClarificationThreads().stream()
                        .map(ClarificationThreadResponse::from)
                        .toList()
        );
    }

    /**
     * Restricted view for operators.
     * SPEC: "Operator sees ONLY the items flagged for clarification" and
     *       "Operator sees the Officer's comment per flagged item"
     * Officers' internal notes are shown only for flagged items.
     */
    public static ChecklistItemResponse flaggedOnly(ChecklistItem item) {
        return new ChecklistItemResponse(
                item.getId(),
                item.getItemCode(),
                item.getItemTitle(),
                item.getItemDescription(),
                item.getSortOrder(),
                item.getStatus(),
                item.getOfficerComment(),   // operator can see comment for flagged items
                item.getOperatorResponse(),
                false,                      // draftSaved is internal
                item.getLastModifiedAt(),
                item.getClarificationThreads().stream()
                        .map(ClarificationThreadResponse::from)
                        .toList()
        );
    }

    public record ClarificationThreadResponse(
            Long id,
            String authorName,
            String message,
            int clarificationRound,
            LocalDateTime createdAt
    ) {
        public static ClarificationThreadResponse from(ClarificationThread t) {
            return new ClarificationThreadResponse(
                    t.getId(),
                    t.getAuthor().getFullName(),
                    t.getMessage(),
                    t.getClarificationRound(),
                    t.getCreatedAt()
            );
        }
    }
}
