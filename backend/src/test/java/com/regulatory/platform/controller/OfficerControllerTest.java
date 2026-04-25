package com.regulatory.platform.controller;

import com.regulatory.platform.config.IntegrationTestBase;
import com.regulatory.platform.repository.DocumentRepository;
import com.regulatory.platform.service.LocalDocumentStorageService;
import com.regulatory.platform.enums.ApplicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.nio.file.Files;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("OfficerController — UC2 Review & Feedback")
class OfficerControllerTest extends IntegrationTestBase {
    @Autowired private DocumentRepository documentRepository;
    @Autowired private LocalDocumentStorageService localDocumentStorageService;

    @BeforeEach
    void setup() { seedUsers(); }

    // ── List all applications ─────────────────────────────────────

    @Nested
    @DisplayName("GET /api/officer/applications")
    class ListApplications {

        @Test
        @DisplayName("Officer sees all applications across all operators")
        void list_officerSeesAll() throws Exception {
            seedApplication(operator, ApplicationStatus.APPLICATION_RECEIVED);
            seedApplication(operator2, ApplicationStatus.UNDER_REVIEW);

            mockMvc.perform(get("/api/officer/applications")
                            .header("Authorization", bearerOf(officer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))));
        }

        @Test
        @DisplayName("ROLE ISOLATION — Operator cannot access officer list endpoint")
        void list_byOperator_returns403() throws Exception {
            mockMvc.perform(get("/api/officer/applications")
                            .header("Authorization", bearerOf(operator)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("SPEC — Officer list includes internalStatus field")
        void list_officerResponseIncludesInternalStatus() throws Exception {
            seedApplication(operator, ApplicationStatus.UNDER_REVIEW);

            mockMvc.perform(get("/api/officer/applications")
                            .header("Authorization", bearerOf(officer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].internalStatus").value("UNDER_REVIEW"))
                    .andExpect(jsonPath("$.data[0].statusLabel").value("Under Review"));
        }
    }

    // ── Get application detail ────────────────────────────────────

    @Nested
    @DisplayName("GET /api/officer/applications/{id}")
    class GetDetail {

        @Test
        @DisplayName("Officer gets full application detail with internalStatus")
        void getDetail_returns200WithInternalStatus() throws Exception {
            var app = seedApplication(operator, ApplicationStatus.UNDER_REVIEW);

            mockMvc.perform(get("/api/officer/applications/" + app.getId())
                            .header("Authorization", bearerOf(officer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.internalStatus").value("UNDER_REVIEW"))
                    .andExpect(jsonPath("$.data.operatorName").value("Test Operator"))
                    .andExpect(jsonPath("$.data.operatorEmail").value("operator@test.com"));
        }

        @Test
        @DisplayName("ROLE ISOLATION — Operator cannot call officer detail endpoint")
        void getDetail_byOperator_returns403() throws Exception {
            var app = seedApplication(operator, ApplicationStatus.UNDER_REVIEW);

            mockMvc.perform(get("/api/officer/applications/" + app.getId())
                            .header("Authorization", bearerOf(operator)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Non-existent application returns 404")
        void getDetail_nonExistent_returns404() throws Exception {
            mockMvc.perform(get("/api/officer/applications/99999")
                            .header("Authorization", bearerOf(officer)))
                    .andExpect(status().isNotFound());
        }
    }

    // ── Officer feedback ─────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/officer/applications/{id}/feedback")
    class Feedback {

        @Test
        @DisplayName("Officer sets status to UNDER_REVIEW from APPLICATION_RECEIVED")
        void feedback_validTransition_updatesStatus() throws Exception {
            var app = seedApplication(operator, ApplicationStatus.APPLICATION_RECEIVED);

            mockMvc.perform(post("/api/officer/applications/" + app.getId() + "/feedback")
                            .header("Authorization", bearerOf(officer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"newStatus":"UNDER_REVIEW","statusNotes":"Starting review","comments":[]}
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.internalStatus").value("UNDER_REVIEW"));
        }

        @Test
        @DisplayName("Officer requests resubmission with section-linked comments")
        void feedback_requestResubmission_commentsLinkedToSection() throws Exception {
            var app = seedApplication(operator, ApplicationStatus.UNDER_REVIEW);

            mockMvc.perform(post("/api/officer/applications/" + app.getId() + "/feedback")
                            .header("Authorization", bearerOf(officer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "newStatus": "PENDING_PRE_SITE_RESUBMISSION",
                                  "statusNotes": "Documents incomplete",
                                  "comments": [
                                    {
                                      "commentText": "Please provide certified business registration",
                                      "targetSection": "business_registration"
                                    },
                                    {
                                      "commentText": "Site plan missing fire exit markings",
                                      "targetSection": "site_plan"
                                    }
                                  ]
                                }
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.internalStatus").value("PENDING_PRE_SITE_RESUBMISSION"))
                    .andExpect(jsonPath("$.data.officerComments", hasSize(2)))
                    .andExpect(jsonPath("$.data.officerComments[0].targetSection").value("business_registration"));
        }

        @Test
        @DisplayName("SPEC — Officer approves application → APPROVED")
        void feedback_approve_setsApproved() throws Exception {
            var app = seedApplication(operator, ApplicationStatus.PENDING_APPROVAL);

            mockMvc.perform(post("/api/officer/applications/" + app.getId() + "/feedback")
                            .header("Authorization", bearerOf(officer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"newStatus":"APPROVED","statusNotes":"All requirements met","comments":[]}
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.internalStatus").value("APPROVED"));
        }

        @Test
        @DisplayName("Invalid status transition → 409 Conflict")
        void feedback_invalidTransition_returns409() throws Exception {
            var app = seedApplication(operator, ApplicationStatus.APPLICATION_RECEIVED);

            // Cannot jump APPLICATION_RECEIVED → APPROVED (skip states)
            mockMvc.perform(post("/api/officer/applications/" + app.getId() + "/feedback")
                            .header("Authorization", bearerOf(officer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"newStatus":"APPROVED","statusNotes":"","comments":[]}
                            """))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Transition from terminal APPROVED state → 409")
        void feedback_fromApproved_returns409() throws Exception {
            var app = seedApplication(operator, ApplicationStatus.APPROVED);

            mockMvc.perform(post("/api/officer/applications/" + app.getId() + "/feedback")
                            .header("Authorization", bearerOf(officer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"newStatus":"UNDER_REVIEW","statusNotes":"","comments":[]}
                            """))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("ROLE ISOLATION — Operator cannot submit officer feedback")
        void feedback_byOperator_returns403() throws Exception {
            var app = seedApplication(operator, ApplicationStatus.APPLICATION_RECEIVED);

            mockMvc.perform(post("/api/officer/applications/" + app.getId() + "/feedback")
                            .header("Authorization", bearerOf(operator))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"newStatus":"UNDER_REVIEW","statusNotes":"","comments":[]}
                            """))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Missing newStatus in request → 400")
        void feedback_missingStatus_returns400() throws Exception {
            var app = seedApplication(operator, ApplicationStatus.APPLICATION_RECEIVED);

            mockMvc.perform(post("/api/officer/applications/" + app.getId() + "/feedback")
                            .header("Authorization", bearerOf(officer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"statusNotes":"notes only","comments":[]}
                            """))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── Status history audit trail ────────────────────────────────

    @Nested
    @DisplayName("Status History — audit trail correctness")
    class StatusHistory {

        @Test
        @DisplayName("Status history is appended on every transition")
        void feedback_statusHistoryRecorded() throws Exception {
            var app = seedApplication(operator, ApplicationStatus.APPLICATION_RECEIVED);

            // Transition 1
            mockMvc.perform(post("/api/officer/applications/" + app.getId() + "/feedback")
                            .header("Authorization", bearerOf(officer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"newStatus":"UNDER_REVIEW","statusNotes":"Step 1","comments":[]}
                            """))
                    .andExpect(status().isOk());

            // Verify history in officer detail view
            mockMvc.perform(get("/api/officer/applications/" + app.getId())
                            .header("Authorization", bearerOf(officer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.statusHistory", hasSize(greaterThanOrEqualTo(1))))
                    .andExpect(jsonPath("$.data.statusHistory[0].toStatusLabel").value("Under Review"));
        }

        @Test
        @DisplayName("SPEC — Operator history uses operator-facing labels (no internal names)")
        void statusHistory_operatorSeesOperatorLabels() throws Exception {
            // Submit from operator side
            var submitResult = mockMvc.perform(post("/api/operator/applications")
                            .header("Authorization", bearerOf(operator))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "businessName":"X",
                                  "licensingTrack":"ECDC",
                                  "businessType":"Retail",
                                  "businessAddress":"1 St",
                                  "contactPhone":"+65 9000 0001",
                                  "activityDescription":"desc",
                                  "documents":[
                                    {
                                      "originalFileName":"registration_doc_acra_extract.txt",
                                      "contentType":"text/plain",
                                      "fileSizeBytes":520,
                                      "documentCategory":"REGISTRATION_DOC"
                                    },
                                    {
                                      "originalFileName":"floor-plan.pdf",
                                      "contentType":"application/pdf",
                                      "fileSizeBytes":888,
                                      "documentCategory":"FLOOR_PLAN"
                                    }
                                  ]
                                }
                                """))
                    .andExpect(status().isCreated())
                    .andReturn();

            // Operator detail should have operator-label history
            var body = objectMapper.readTree(submitResult.getResponse().getContentAsString());
            long id = body.path("data").path("id").asLong();

            mockMvc.perform(get("/api/operator/applications/" + id)
                            .header("Authorization", bearerOf(operator)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.statusHistory[0].toStatusLabel").value("Submitted"));
        }
    }

    @Nested
    @DisplayName("GET /api/officer/documents/{id}/download")
    class DownloadDocument {
        @Test
        @DisplayName("Officer can download existing stored document")
        void download_existingDocument_returnsFile() throws Exception {
            var submitResult = mockMvc.perform(post("/api/operator/applications")
                            .header("Authorization", bearerOf(operator))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "businessName":"X",
                                  "licensingTrack":"ECDC",
                                  "businessType":"Retail",
                                  "businessAddress":"1 St",
                                  "contactPhone":"+65 9000 0001",
                                  "activityDescription":"desc",
                                  "documents":[
                                    {
                                      "originalFileName":"registration_doc_acra_extract.txt",
                                      "contentType":"text/plain",
                                      "fileSizeBytes":520,
                                      "documentCategory":"REGISTRATION_DOC"
                                    },
                                    {
                                      "originalFileName":"floor-plan.pdf",
                                      "contentType":"application/pdf",
                                      "fileSizeBytes":888,
                                      "documentCategory":"FLOOR_PLAN"
                                    }
                                  ]
                                }
                                """))
                    .andExpect(status().isCreated())
                    .andReturn();

            long appId = objectMapper.readTree(submitResult.getResponse().getContentAsString())
                    .path("data").path("id").asLong();
            var app = documentRepository.findByIdWithApplicationAndOperator(
                            objectMapper.readTree(mockMvc.perform(get("/api/officer/applications/" + appId)
                                            .header("Authorization", bearerOf(officer)))
                                    .andReturn().getResponse().getContentAsString())
                                    .path("data").path("documents").get(0).path("id").asLong())
                    .orElseThrow();

            mockMvc.perform(get("/api/officer/documents/" + app.getId() + "/download")
                            .header("Authorization", bearerOf(officer)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", containsString("filename=")))
                    .andExpect(header().string("Content-Type", not(isEmptyOrNullString())));
        }

        @Test
        @DisplayName("Missing stored file returns 404")
        void download_missingStoredFile_returns404() throws Exception {
            var app = seedApplication(operator, ApplicationStatus.APPLICATION_RECEIVED);
            var doc = documentRepository.save(com.regulatory.platform.entity.Document.builder()
                    .application(app)
                    .originalFileName("x.txt")
                    .storedFileName("missing-file.txt")
                    .contentType("text/plain")
                    .fileSizeBytes(10L)
                    .documentCategory("REGISTRATION_DOC")
                    .submissionRound(1)
                    .build());
            Files.deleteIfExists(localDocumentStorageService.resolve("missing-file.txt"));

            mockMvc.perform(get("/api/officer/documents/" + doc.getId() + "/download")
                            .header("Authorization", bearerOf(officer)))
                    .andExpect(status().isNotFound());
        }
    }
}
