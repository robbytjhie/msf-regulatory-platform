package com.regulatory.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ApplicationSubmitRequest(
        @NotBlank String businessName,
        @NotBlank String businessType,
        @NotBlank String businessAddress,
        String contactPhone,
        @NotBlank String activityDescription
) {}
