package com.regulatory.platform.service;

import com.regulatory.platform.entity.Application;
import com.regulatory.platform.entity.Document;
import com.regulatory.platform.repository.DocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentVerificationService")
class DocumentVerificationServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ExternalDocumentAiAnalysisService externalDocumentAiAnalysisService;

    /** Runs verification without sleeping (keeps tests fast). */
    private static final class FastDocumentVerificationService extends DocumentVerificationService {
        FastDocumentVerificationService(DocumentRepository repo, ExternalDocumentAiAnalysisService external) {
            super(repo, external);
            ReflectionTestUtils.setField(this, "aiVerificationEnabled", true);
        }

        @Override
        void sleep(long ms) {
            // no-op for unit tests
        }
    }

    private DocumentVerificationService service() {
        return new FastDocumentVerificationService(documentRepository, externalDocumentAiAnalysisService);
    }

    private static Document sampleDoc() {
        Application application = mock(Application.class);
        return Document.builder()
                .id(77L)
                .application(application)
                .originalFileName("permit.pdf")
                .storedFileName("stored-1")
                .contentType("application/pdf")
                .fileSizeBytes(2048L)
                .documentCategory("Permit")
                .aiVerificationStatus(Document.AiVerificationStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("Uses external AI result when Groq/HF layer returns a value")
    void appliesExternalAiWhenPresent() {
        Document doc = sampleDoc();
        when(documentRepository.findByApplicationIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(doc));
        when(externalDocumentAiAnalysisService.analyze(doc))
                .thenReturn(Optional.of(new DocumentAiAnalysisResult(
                        Document.AiVerificationStatus.PASSED,
                        "External model approved metadata.")));

        service().startSimulatedVerification(10L);

        // Same Document instances are mutated after saveAll; do not assert PROCESSING here (matcher runs after completion).
        verify(documentRepository).saveAll(argThat((List<Document> list) -> list.size() == 1 && list.get(0).getId().equals(77L)));

        verify(externalDocumentAiAnalysisService).analyze(doc);
        verify(documentRepository).save(doc);
        assertThat(doc.getAiVerificationStatus()).isEqualTo(Document.AiVerificationStatus.PASSED);
        assertThat(doc.getAiVerificationNotes()).isEqualTo("External model approved metadata.");
    }

    @Test
    @DisplayName("Simulated pass when external AI is absent and random roll is high")
    void simulatedPassWhenExternalEmpty() {
        Document doc = sampleDoc();
        when(documentRepository.findByApplicationIdOrderByCreatedAtDesc(11L)).thenReturn(List.of(doc));
        when(externalDocumentAiAnalysisService.analyze(doc)).thenReturn(Optional.empty());

        try (MockedStatic<ThreadLocalRandom> mocked = mockStatic(ThreadLocalRandom.class)) {
            ThreadLocalRandom tlr = mock(ThreadLocalRandom.class);
            mocked.when(ThreadLocalRandom::current).thenReturn(tlr);
            when(tlr.nextInt(1200)).thenReturn(0);
            when(tlr.nextDouble()).thenReturn(0.99);

            service().startSimulatedVerification(11L);
        }

        verify(documentRepository).saveAll(argThat((List<Document> list) -> list.size() == 1 && list.get(0) == doc));
        assertThat(doc.getAiVerificationStatus()).isEqualTo(Document.AiVerificationStatus.PASSED);
        assertThat(doc.getAiVerificationNotes()).contains("simulated");
    }

    @Test
    @DisplayName("Simulated flag when external AI is absent and random roll is low")
    void simulatedFlagWhenExternalEmpty() {
        Document doc = sampleDoc();
        when(documentRepository.findByApplicationIdOrderByCreatedAtDesc(12L)).thenReturn(List.of(doc));
        when(externalDocumentAiAnalysisService.analyze(doc)).thenReturn(Optional.empty());

        try (MockedStatic<ThreadLocalRandom> mocked = mockStatic(ThreadLocalRandom.class)) {
            ThreadLocalRandom tlr = mock(ThreadLocalRandom.class);
            mocked.when(ThreadLocalRandom::current).thenReturn(tlr);
            when(tlr.nextInt(1200)).thenReturn(0);
            when(tlr.nextDouble()).thenReturn(0.01);

            service().startSimulatedVerification(12L);
        }

        verify(documentRepository).saveAll(argThat((List<Document> list) -> list.size() == 1 && list.get(0) == doc));
        assertThat(doc.getAiVerificationStatus()).isEqualTo(Document.AiVerificationStatus.FLAGGED);
        assertThat(doc.getAiVerificationNotes()).contains("inconsistency");
    }

    @Test
    @DisplayName("Processes every document returned for the application")
    void processesMultipleDocuments() {
        Document a = sampleDoc();
        Document b = sampleDoc();
        b.setId(78L);
        when(documentRepository.findByApplicationIdOrderByCreatedAtDesc(20L)).thenReturn(List.of(a, b));
        when(externalDocumentAiAnalysisService.analyze(any(Document.class)))
                .thenReturn(Optional.of(new DocumentAiAnalysisResult(
                        Document.AiVerificationStatus.FLAGGED, "Review")));

        service().startSimulatedVerification(20L);

        verify(externalDocumentAiAnalysisService, times(2)).analyze(any(Document.class));
        verify(documentRepository, atLeastOnce()).saveAll(any(List.class));
        assertThat(a.getAiVerificationStatus()).isEqualTo(Document.AiVerificationStatus.FLAGGED);
        assertThat(b.getAiVerificationStatus()).isEqualTo(Document.AiVerificationStatus.FLAGGED);
    }

    @Test
    @DisplayName("Manual-review fallback still performs a saveAll call when no documents")
    void noDocuments_noFurtherSaves() {
        when(documentRepository.findByApplicationIdOrderByCreatedAtDesc(99L)).thenReturn(List.of());

        service().startSimulatedVerification(99L);

        verify(documentRepository).saveAll(argThat((List<Document> list) -> list.isEmpty()));
        verifyNoInteractions(externalDocumentAiAnalysisService);
    }
}
