package com.regulatory.platform.dto.request;

import com.regulatory.platform.enums.ApplicationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OfficerFeedbackRequest(
        @NotNull ApplicationStatus newStatus,
        String statusNotes,
        @Valid List<CommentRequest> comments
) {}
