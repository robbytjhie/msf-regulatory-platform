package com.regulatory.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulatory.platform.entity.Application;
import com.regulatory.platform.entity.Document;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("ExternalDocumentAiAnalysisService")
class ExternalDocumentAiAnalysisServiceTest {

    private static Document sampleDoc() {
        Application application = mock(Application.class);
        return Document.builder()
                .id(42L)
                .application(application)
                .originalFileName("floor-plan.pdf")
                .storedFileName("s1")
                .contentType("application/pdf")
                .fileSizeBytes(333L)
                .documentCategory("Layout")
                .build();
    }

    @Test
    @DisplayName("Falls back to deterministic local heuristic when no API keys are configured")
    void noKeys_returnsHeuristicResult() {
        ExternalDocumentAiAnalysisService svc = new ExternalDocumentAiAnalysisService(new ObjectMapper());
        ReflectionTestUtils.setField(svc, "groqApiKey", "");
        ReflectionTestUtils.setField(svc, "huggingFaceToken", "");

        assertThat(svc.analyze(sampleDoc())).isPresent();
    }

    @Test
    @DisplayName("Parses Groq OpenAI-style JSON and maps PASS")
    void groq_pass() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse()
                    .setBody("{\"choices\":[{\"message\":{\"content\":\"PASS\\nFilename and category look routine.\"}}]}")
                    .addHeader("Content-Type", "application/json"));

            ExternalDocumentAiAnalysisService svc = new ExternalDocumentAiAnalysisService(new ObjectMapper());
            ReflectionTestUtils.setField(svc, "groqApiKey", "secret");
            ReflectionTestUtils.setField(svc, "groqBaseUrl", trimTrailingSlash(server.url("/").toString()));
            ReflectionTestUtils.setField(svc, "groqModel", "test-model");
            ReflectionTestUtils.setField(svc, "huggingFaceToken", "");

            Optional<DocumentAiAnalysisResult> r = svc.analyze(sampleDoc());
            assertThat(r).isPresent();
            assertThat(r.get().status()).isEqualTo(Document.AiVerificationStatus.PASSED);
            assertThat(r.get().notes()).contains("Filename");

            RecordedRequest req = server.takeRequest();
            assertThat(req.getPath()).isEqualTo("/openai/v1/chat/completions");
            assertThat(req.getHeader("Authorization")).isEqualTo("Bearer secret");
        }
    }

    @Test
    @DisplayName("Falls back to Hugging Face when Groq request fails")
    void groqFails_hfPass() throws Exception {
        try (MockWebServer groq = new MockWebServer(); MockWebServer hf = new MockWebServer()) {
            groq.start();
            hf.start();
            groq.enqueue(new MockResponse().setResponseCode(503));
            hf.enqueue(new MockResponse()
                    .setBody("[{\"generated_text\":\"FLAG\\nUnusual extension for this category.\"}]")
                    .addHeader("Content-Type", "application/json"));

            ExternalDocumentAiAnalysisService svc = new ExternalDocumentAiAnalysisService(new ObjectMapper());
            ReflectionTestUtils.setField(svc, "groqApiKey", "g-key");
            ReflectionTestUtils.setField(svc, "groqBaseUrl", trimTrailingSlash(groq.url("/").toString()));
            ReflectionTestUtils.setField(svc, "groqModel", "m1");
            ReflectionTestUtils.setField(svc, "huggingFaceToken", "hf-token");
            ReflectionTestUtils.setField(svc, "huggingFaceInferenceBaseUrl", trimTrailingSlash(hf.url("/").toString()));
            ReflectionTestUtils.setField(svc, "huggingFaceModel", "demo-model");

            Optional<DocumentAiAnalysisResult> r = svc.analyze(sampleDoc());
            assertThat(r).isPresent();
            assertThat(r.get().status()).isEqualTo(Document.AiVerificationStatus.FLAGGED);
            assertThat(r.get().notes()).contains("Unusual");

            assertThat(groq.takeRequest().getPath()).isEqualTo("/openai/v1/chat/completions");
            assertThat(hf.takeRequest().getPath()).isEqualTo("/models/demo-model");
        }
    }

    @Test
    @DisplayName("Falls back to deterministic local heuristic when assistant text is unparseable")
    void groq_unparseableContent_returnsHeuristicResult() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse()
                    .setBody("{\"choices\":[{\"message\":{\"content\":\"UNCLEAR\\nMaybe\"}}]}")
                    .addHeader("Content-Type", "application/json"));

            ExternalDocumentAiAnalysisService svc = new ExternalDocumentAiAnalysisService(new ObjectMapper());
            ReflectionTestUtils.setField(svc, "groqApiKey", "k");
            ReflectionTestUtils.setField(svc, "groqBaseUrl", trimTrailingSlash(server.url("/").toString()));
            ReflectionTestUtils.setField(svc, "groqModel", "m");
            ReflectionTestUtils.setField(svc, "huggingFaceToken", "");

            assertThat(svc.analyze(sampleDoc())).isPresent();
        }
    }

    @Test
    @DisplayName("Uses Hugging Face only when Groq key is unset")
    void hfOnly_whenGroqKeyBlank() throws Exception {
        try (MockWebServer hf = new MockWebServer()) {
            hf.start();
            hf.enqueue(new MockResponse()
                    .setBody("[{\"generated_text\":\"PASS\\nHF-only path.\"}]")
                    .addHeader("Content-Type", "application/json"));

            ExternalDocumentAiAnalysisService svc = new ExternalDocumentAiAnalysisService(new ObjectMapper());
            ReflectionTestUtils.setField(svc, "groqApiKey", "");
            ReflectionTestUtils.setField(svc, "huggingFaceToken", "hf-secret");
            ReflectionTestUtils.setField(svc, "huggingFaceInferenceBaseUrl", trimTrailingSlash(hf.url("/").toString()));
            ReflectionTestUtils.setField(svc, "huggingFaceModel", "tiny-model");

            Optional<DocumentAiAnalysisResult> r = svc.analyze(sampleDoc());
            assertThat(r).isPresent();
            assertThat(r.get().status()).isEqualTo(Document.AiVerificationStatus.PASSED);
            assertThat(r.get().notes()).contains("HF-only");
            assertThat(hf.takeRequest().getPath()).isEqualTo("/models/tiny-model");
        }
    }

    @Test
    @DisplayName("Groq FLAG with blank second line uses default note text")
    void groq_flag_defaultNote() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse()
                    .setBody("{\"choices\":[{\"message\":{\"content\":\"FLAG\\n\"}}]}")
                    .addHeader("Content-Type", "application/json"));

            ExternalDocumentAiAnalysisService svc = new ExternalDocumentAiAnalysisService(new ObjectMapper());
            ReflectionTestUtils.setField(svc, "groqApiKey", "k");
            ReflectionTestUtils.setField(svc, "groqBaseUrl", trimTrailingSlash(server.url("/").toString()));
            ReflectionTestUtils.setField(svc, "groqModel", "m");
            ReflectionTestUtils.setField(svc, "huggingFaceToken", "");

            Optional<DocumentAiAnalysisResult> r = svc.analyze(sampleDoc());
            assertThat(r).isPresent();
            assertThat(r.get().status()).isEqualTo(Document.AiVerificationStatus.FLAGGED);
            assertThat(r.get().notes()).containsIgnoringCase("flag");
        }
    }

    @Test
    @DisplayName("Parses HF object payload with generated_text at root")
    void hf_objectRoot_generatedText() throws Exception {
        try (MockWebServer hf = new MockWebServer()) {
            hf.start();
            hf.enqueue(new MockResponse()
                    .setBody("{\"generated_text\":\"PASS\\nRoot object form.\"}")
                    .addHeader("Content-Type", "application/json"));

            ExternalDocumentAiAnalysisService svc = new ExternalDocumentAiAnalysisService(new ObjectMapper());
            ReflectionTestUtils.setField(svc, "groqApiKey", "");
            ReflectionTestUtils.setField(svc, "huggingFaceToken", "t");
            ReflectionTestUtils.setField(svc, "huggingFaceInferenceBaseUrl", trimTrailingSlash(hf.url("/").toString()));
            ReflectionTestUtils.setField(svc, "huggingFaceModel", "m");

            Optional<DocumentAiAnalysisResult> r = svc.analyze(sampleDoc());
            assertThat(r).isPresent();
            assertThat(r.get().status()).isEqualTo(Document.AiVerificationStatus.PASSED);
            assertThat(r.get().notes()).contains("Root object");
        }
    }

    @Test
    @DisplayName("Groq empty choices falls through to HF when configured")
    void groqEmptyChoices_fallsBackToHf() throws Exception {
        try (MockWebServer groq = new MockWebServer(); MockWebServer hf = new MockWebServer()) {
            groq.start();
            hf.start();
            groq.enqueue(new MockResponse()
                    .setBody("{\"choices\":[]}")
                    .addHeader("Content-Type", "application/json"));
            hf.enqueue(new MockResponse()
                    .setBody("[{\"generated_text\":\"PASS\\nFallback.\"}]")
                    .addHeader("Content-Type", "application/json"));

            ExternalDocumentAiAnalysisService svc = new ExternalDocumentAiAnalysisService(new ObjectMapper());
            ReflectionTestUtils.setField(svc, "groqApiKey", "g");
            ReflectionTestUtils.setField(svc, "groqBaseUrl", trimTrailingSlash(groq.url("/").toString()));
            ReflectionTestUtils.setField(svc, "groqModel", "m");
            ReflectionTestUtils.setField(svc, "huggingFaceToken", "hf");
            ReflectionTestUtils.setField(svc, "huggingFaceInferenceBaseUrl", trimTrailingSlash(hf.url("/").toString()));
            ReflectionTestUtils.setField(svc, "huggingFaceModel", "mm");

            Optional<DocumentAiAnalysisResult> r = svc.analyze(sampleDoc());
            assertThat(r).isPresent();
            assertThat(r.get().status()).isEqualTo(Document.AiVerificationStatus.PASSED);
        }
    }

    @Test
    @DisplayName("Groq PASS with whitespace-only second line uses default passed note")
    void groq_pass_whitespaceSecondLine_defaultNote() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse()
                    .setBody("{\"choices\":[{\"message\":{\"content\":\"PASS\\n \"}}]}")
                    .addHeader("Content-Type", "application/json"));

            ExternalDocumentAiAnalysisService svc = new ExternalDocumentAiAnalysisService(new ObjectMapper());
            ReflectionTestUtils.setField(svc, "groqApiKey", "k");
            ReflectionTestUtils.setField(svc, "groqBaseUrl", trimTrailingSlash(server.url("/").toString()));
            ReflectionTestUtils.setField(svc, "groqModel", "m");
            ReflectionTestUtils.setField(svc, "huggingFaceToken", "");

            Optional<DocumentAiAnalysisResult> r = svc.analyze(sampleDoc());
            assertThat(r).isPresent();
            assertThat(r.get().status()).isEqualTo(Document.AiVerificationStatus.PASSED);
            assertThat(r.get().notes()).containsIgnoringCase("concern");
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
