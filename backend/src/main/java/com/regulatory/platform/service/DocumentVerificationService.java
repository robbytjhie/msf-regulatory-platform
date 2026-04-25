package com.regulatory.platform.service;

import com.regulatory.platform.entity.Document;
import com.regulatory.platform.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentVerificationService {

    private final DocumentRepository documentRepository;
    private final ExternalDocumentAiAnalysisService externalDocumentAiAnalysisService;
    @Value("${app.ai.verification-enabled:true}")
    private boolean aiVerificationEnabled;

    public boolean isAiVerificationEnabled() {
        return aiVerificationEnabled;
    }

    @Async
    @Transactional
    public void startSimulatedVerification(Long applicationId) {
        if (!aiVerificationEnabled) {
            markAllAsManualReviewRequired(applicationId);
            return;
        }
        // Allow submit transaction to complete before reading.
        sleep(1200);
        List<Document> docs = documentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        for (Document doc : docs) {
            doc.setAiVerificationStatus(Document.AiVerificationStatus.PROCESSING);
            doc.setAiVerificationNotes("AI verification in progress...");
        }
        documentRepository.saveAll(docs);

        for (Document doc : docs) {
            sleep(800 + ThreadLocalRandom.current().nextInt(1200));
            externalDocumentAiAnalysisService.analyze(doc).ifPresentOrElse(
                    result -> {
                        doc.setAiVerificationStatus(result.status());
                        doc.setAiVerificationNotes(result.notes());
                    },
                    () -> {
                        boolean flagged = ThreadLocalRandom.current().nextDouble() < 0.25;
                        doc.setAiVerificationStatus(flagged
                                ? Document.AiVerificationStatus.FLAGGED
                                : Document.AiVerificationStatus.PASSED);
                        doc.setAiVerificationNotes(flagged
                                ? "Potential inconsistency detected. Officer review recommended."
                                : "No issues detected by simulated AI verification.");
                    });
            documentRepository.save(doc);
            log.info("Document {} verification complete: {}", doc.getId(), doc.getAiVerificationStatus());
        }
    }

    @Async
    @Transactional
    public void startSimulatedVerificationForDocument(Long documentId) {
        if (!aiVerificationEnabled) {
            Document doc = documentRepository.findById(documentId).orElse(null);
            if (doc == null) return;
            doc.setAiVerificationStatus(Document.AiVerificationStatus.FAILED);
            doc.setAiVerificationNotes("AI verification is unavailable. Officer manual validation is required.");
            documentRepository.save(doc);
            return;
        }
        sleep(700);
        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) return;
        doc.setAiVerificationStatus(Document.AiVerificationStatus.PROCESSING);
        doc.setAiVerificationNotes("AI verification in progress...");
        documentRepository.save(doc);

        sleep(800 + ThreadLocalRandom.current().nextInt(900));
        externalDocumentAiAnalysisService.analyze(doc).ifPresent(result -> {
            doc.setAiVerificationStatus(result.status());
            doc.setAiVerificationNotes(result.notes());
            documentRepository.save(doc);
            log.info("Document {} re-validation complete: {}", doc.getId(), doc.getAiVerificationStatus());
        });
    }

    /** Package-private so tests can override with no-op sleeps. */
    void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void markAllAsManualReviewRequired(Long applicationId) {
        List<Document> docs = documentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        if (docs.isEmpty()) return;
        for (Document doc : docs) {
            doc.setAiVerificationStatus(Document.AiVerificationStatus.FAILED);
            doc.setAiVerificationNotes("AI verification is unavailable. Officer manual validation is required.");
        }
        documentRepository.saveAll(docs);
        log.warn("AI verification disabled; application {} routed to manual officer validation", applicationId);
    }
}
