package com.regulatory.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulatory.platform.entity.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Optional free-tier AI analysis using public APIs (keys required).
 * <p>
 * Priority: Groq (OpenAI-compatible, generous free tier) → Hugging Face Inference API → empty (caller simulates).
 * Analysis uses document metadata only (filename, MIME type, size, category) because binary upload is not yet stored.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalDocumentAiAnalysisService {

    private final ObjectMapper objectMapper;

    @Value("${app.ai.groq.api-key:}")
    private String groqApiKey;

    @Value("${app.ai.groq.base-url:https://api.groq.com}")
    private String groqBaseUrl;

    @Value("${app.ai.groq.model:llama-3.1-8b-instant}")
    private String groqModel;

    @Value("${app.ai.huggingface.api-token:}")
    private String huggingFaceToken;

    @Value("${app.ai.huggingface.inference-base-url:https://api-inference.huggingface.co}")
    private String huggingFaceInferenceBaseUrl;

    @Value("${app.ai.huggingface.model:HuggingFaceTB/SmolLM2-360M-Instruct}")
    private String huggingFaceModel;

    public Optional<DocumentAiAnalysisResult> analyze(Document doc) {
        if (groqApiKey != null && !groqApiKey.isBlank()) {
            Optional<DocumentAiAnalysisResult> r = tryGroq(doc);
            if (r.isPresent()) {
                return r;
            }
        }
        if (huggingFaceToken != null && !huggingFaceToken.isBlank()) {
            Optional<DocumentAiAnalysisResult> r = tryHuggingFace(doc);
            if (r.isPresent()) {
                return r;
            }
        }
        return Optional.empty();
    }

    private Optional<DocumentAiAnalysisResult> tryGroq(Document doc) {
        String prompt = buildPrompt(doc);
        try {
            String base = (groqBaseUrl == null || groqBaseUrl.isBlank())
                    ? "https://api.groq.com"
                    : groqBaseUrl.trim().replaceAll("/$", "");
            RestClient client = RestClient.builder()
                    .baseUrl(base)
                    .build();
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", groqModel,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "max_tokens", 120,
                    "temperature", 0.2
            ));
            String raw = client.post()
                    .uri("/openai/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + groqApiKey.trim())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseAssistantText(raw);
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Groq AI analysis failed for document {}: {}", doc.getId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<DocumentAiAnalysisResult> tryHuggingFace(Document doc) {
        String prompt = buildPrompt(doc);
        try {
            String hfBase = (huggingFaceInferenceBaseUrl == null || huggingFaceInferenceBaseUrl.isBlank())
                    ? "https://api-inference.huggingface.co"
                    : huggingFaceInferenceBaseUrl.trim().replaceAll("/$", "");
            String url = hfBase + "/models/" + huggingFaceModel;
            RestClient client = RestClient.builder().build();
            String payload = objectMapper.writeValueAsString(Map.of(
                    "inputs", prompt,
                    "parameters", Map.of("max_new_tokens", 120, "return_full_text", false)
            ));
            String raw = client.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + huggingFaceToken.trim())
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            return parseHuggingFaceText(raw);
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Hugging Face AI analysis failed for document {}: {}", doc.getId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private String buildPrompt(Document doc) {
        String ruleHint = ruleHintForCategory(doc.getDocumentCategory());
        return """
                You are a licensing document screening assistant. You only see metadata (not file contents).
                File name: %s
                Content type: %s
                Size bytes: %s
                Category: %s
                Rule focus for this category: %s

                Reply with EXACTLY two lines:
                Line 1: PASS or FLAG (uppercase, one word only)
                Line 2: One short sentence explaining why.
                """.formatted(
                safe(doc.getOriginalFileName()),
                safe(doc.getContentType()),
                doc.getFileSizeBytes() != null ? doc.getFileSizeBytes() : "unknown",
                safe(doc.getDocumentCategory()),
                ruleHint
        );
    }

    private String ruleHintForCategory(String category) {
        if (category == null) {
            return "Unknown category; flag if metadata suggests mismatch or missing context.";
        }
        return switch (category.trim().toUpperCase()) {
            case "REGISTRATION_DOC" -> "Expected ACRA/ROS registration evidence; flag if legal-entity context is unclear.";
            case "FLOOR_PLAN" -> "Expected revised premises/home floor plan for ECDC or Childminding assessment.";
            case "CCTV_AND_SAFETY_PROOF" -> "Expected CCTV installation or remedial safety evidence for site compliance.";
            case "ATTENDANCE_LOG" -> "Expected SCC attendance logs supporting SCFA subsidy audit period.";
            case "SUBSIDY_WITHDRAWAL_FORM" -> "Expected SCFA subsidy withdrawal/claim adjustment documentation.";
            case "ENVIRONMENT_COMPLIANCE_RECORD" -> "Expected environment compliance evidence (lighting/ventilation) for SCC checks.";
            case "STAFF_ROSTER" -> "Expected duty roster for validating staff-to-resident ratio in HFAA inspection.";
            case "SANITATION_AND_FIRE_CERT" -> "Expected safe and sanitary premises evidence including fire safety certification.";
            case "STAFF_MEDICAL_SCREENING" -> "Expected staff medical screening evidence for resident-care duties.";
            case "HOME_SAFETY_PHOTOS" -> "Expected home child-proofing and infant-safety photo evidence.";
            case "EQUIPMENT_INVENTORY" -> "Expected infant-care equipment inventory aligned to declared capacity.";
            case "CAPACITY_PLAN" -> "Expected capacity declaration with space-allocation rationale.";
            default -> "General compliance evidence; flag if metadata is suspicious or category does not fit file naming/type.";
        };
    }

    private static String safe(String s) {
        return s == null ? "unknown" : s;
    }

    private Optional<DocumentAiAnalysisResult> parseAssistantText(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return Optional.empty();
            }
            String text = choices.get(0).path("message").path("content").asText("");
            return mapPassFlagToAnalysis(text);
        } catch (Exception ex) {
            log.debug("Failed to parse Groq response: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<DocumentAiAnalysisResult> parseHuggingFaceText(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            // Router / various HF payloads: generated_text at root or in [0]
            String text = null;
            if (root.isArray() && !root.isEmpty()) {
                text = root.get(0).path("generated_text").asText(null);
            }
            if (text == null) {
                text = root.path("generated_text").asText(null);
            }
            if (text == null || text.isBlank()) {
                text = root.toString();
            }
            return mapPassFlagToAnalysis(text);
        } catch (Exception ex) {
            log.debug("Failed to parse HF response: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<DocumentAiAnalysisResult> mapPassFlagToAnalysis(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String firstLine = text.lines().findFirst().orElse("").trim().toUpperCase();
        Document.AiVerificationStatus status;
        if (firstLine.contains("FLAG")) {
            status = Document.AiVerificationStatus.FLAGGED;
        } else if (firstLine.contains("PASS")) {
            status = Document.AiVerificationStatus.PASSED;
        } else {
            return Optional.empty();
        }
        String notes = text.lines().skip(1).findFirst().orElse(text).trim();
        if (notes.isBlank()) {
            notes = status == Document.AiVerificationStatus.FLAGGED
                    ? "External AI flagged this document for review."
                    : "External AI found no major concerns from metadata.";
        }
        return Optional.of(new DocumentAiAnalysisResult(status, notes));
    }
}
