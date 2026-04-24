package com.regulatory.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CommentRequest(
        @NotBlank String commentText,
        String targetSection,  // e.g. "business_registration", "site_plan" — null = general
        Long targetDocumentId
) {}
