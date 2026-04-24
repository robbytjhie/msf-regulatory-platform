package com.regulatory.platform.dto.request;

import com.regulatory.platform.enums.ChecklistItemStatus;
import jakarta.validation.constraints.NotNull;

public record ChecklistItemUpdateRequest(
        @NotNull Long itemId,
        @NotNull ChecklistItemStatus status,
        String officerComment
) {}
