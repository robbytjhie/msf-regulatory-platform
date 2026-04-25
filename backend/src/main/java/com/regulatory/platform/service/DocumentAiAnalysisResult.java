package com.regulatory.platform.service;

import com.regulatory.platform.entity.Document;

/**
 * Outcome of an optional external AI analysis (metadata-only until file upload is implemented).
 */
public record DocumentAiAnalysisResult(Document.AiVerificationStatus status, String notes) {
}
