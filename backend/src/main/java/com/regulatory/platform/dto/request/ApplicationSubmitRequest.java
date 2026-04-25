package com.regulatory.platform.dto.request;

import com.regulatory.platform.entity.Document;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ApplicationSubmitRequest(
        @NotBlank String businessName,
        @NotBlank String businessType,
        @NotBlank String businessAddress,
        String contactPhone,
        @NotBlank String activityDescription,
        List<DocumentUploadRequest> documents
) {
    public record DocumentUploadRequest(
            @NotBlank String originalFileName,
            String contentType,
            Long fileSizeBytes,
            String documentCategory,
            Document.AiVerificationStatus aiVerificationStatus,
            String aiVerificationNotes
    ) {
    }
}
