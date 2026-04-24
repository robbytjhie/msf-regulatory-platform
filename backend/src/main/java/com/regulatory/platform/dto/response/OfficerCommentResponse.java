package com.regulatory.platform.dto.response;

import com.regulatory.platform.entity.OfficerComment;

import java.time.LocalDateTime;

public record OfficerCommentResponse(
        Long id,
        String commentText,
        String targetSection,
        Long targetDocumentId,
        int submissionRound,
        boolean resolved,
        String officerName,
        LocalDateTime createdAt
) {
    public static OfficerCommentResponse from(OfficerComment comment) {
        return new OfficerCommentResponse(
                comment.getId(),
                comment.getCommentText(),
                comment.getTargetSection(),
                comment.getTargetDocument() != null ? comment.getTargetDocument().getId() : null,
                comment.getSubmissionRound(),
                comment.isResolved(),
                comment.getOfficer().getFullName(),
                comment.getCreatedAt()
        );
    }
}
