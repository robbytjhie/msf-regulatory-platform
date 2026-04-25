package com.regulatory.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DocumentReuploadRequest(
        @NotBlank String originalFileName,
        String contentType,
        Long fileSizeBytes
) {}

