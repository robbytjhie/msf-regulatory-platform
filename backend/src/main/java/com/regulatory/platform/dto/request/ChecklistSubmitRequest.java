package com.regulatory.platform.dto.request;

import com.regulatory.platform.enums.ChecklistItemStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ChecklistSubmitRequest(
        @NotEmpty @Valid List<ChecklistItemUpdateRequest> items
) {}
