package com.regulatory.platform.dto.response;

import com.regulatory.platform.entity.Document;

import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        String originalFileName,
        String documentCategory,
        String contentType,
        Long fileSizeBytes,
        int submissionRound,
        Document.AiVerificationStatus aiVerificationStatus,
        String aiVerificationNotes,
        LocalDateTime uploadedAt
) {
    public static DocumentResponse from(Document doc) {
        return new DocumentResponse(
                doc.getId(),
                doc.getOriginalFileName(),
                doc.getDocumentCategory(),
                doc.getContentType(),
                doc.getFileSizeBytes(),
                doc.getSubmissionRound(),
                doc.getAiVerificationStatus(),
                doc.getAiVerificationNotes(),
                doc.getCreatedAt()
        );
    }
}
