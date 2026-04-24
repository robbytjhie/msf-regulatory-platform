package com.regulatory.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OperatorClarificationRequest(
        @NotBlank String message,
        Long attachedDocumentId
) {}
