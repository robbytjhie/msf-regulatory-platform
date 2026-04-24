package com.regulatory.platform.controller;

import com.regulatory.platform.config.IntegrationTestBase;
import com.regulatory.platform.enums.ApplicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("OperatorController — UC1 Application Submission & Resubmission")
class OperatorControllerTest extends IntegrationTestBase {

    @BeforeEach
    void setup() { seedUsers(); }

    // ── Submit new application ────────────────────────────────────

    @Nested
    @DisplayName("POST /api/operator/applications")
    class Submit {

        @Test
        @DisplayName("Operator submits valid application → 201 with reference number")
        void submit_valid_returns201() throws Exception {
            mockMvc.perform(post("/api/operator/applications")
                            .header("Authorization", bearerOf(operator))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validSubmitJson()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.referenceNumber").isNotEmpty())
                    .andExpect(jsonPath("$.data.statusLabel").value("Submitted"))
                    .andExpect(jsonPath("$.data.internalStatus").doesNotExist())   // SPEC: hidden from operator
                    .andExpect(jsonPath("$.data.businessName").value("Test Cafe Pte Ltd"));
        }

        @Test
        @DisplayName("ROLE ISOLATION — Officer cannot submit as operator")
        void submit_byOfficer_returns403() throws Exception {
            mockMvc.perform(post("/api/operator/applications")
                            .header("Authorization", bearerOf(officer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validSubmitJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("No token → 403")
        void submit_noToken_returns403() throws Exception {
            mockMvc.perform(post("/api/operator/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validSubmitJson()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Missing required field businessName → 400")
        void submit_missingBusinessName_returns400() throws Exception {
            mockMvc.perform(post("/api/operator/applications")
                            .header("Authorization", bearerOf(operator))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"businessType":"Retail","businessAddress":"1 Test St","activityDescription":"desc"}
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("SPEC — internalStatus must be null in operator response")
        void submit_response_internalStatusHidden() throws Exception {
            mockMvc.perform(post("/api/operator/applications")
                            .header("Authorization", bearerOf(operator))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validSubmitJson()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.internalStatus").doesNotExist());
        }

        @Test
        @DisplayName("SPEC — assignedOfficerName must be null in operator response")
        void submit_response_officerNameHidden() throws Exception {
            mockMvc.perform(post("/api/operator/applications")
                            .header("Authorization", bearerOf(operator))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validSubmitJson()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.assignedOfficerName").doesNotExist());
        }
    }

    // ── List own applications ─────────────────────────────────────

    @Nested
    @DisplayName("GET /api/operator/applications")
    class ListApplications {

        @Test
        @DisplayName("Operator only sees their own applications")
        void list_returnsOnlyOwnApplications() throws Exception {
            seedApplication(operator, ApplicationStatus.APPLICATION_RECEIVED);
            seedApplication(operator2, ApplicationStatus.APPLICATION_RECEIVED);  // other operator

            mockMvc.perform(get("/api/operator/applications")
                            .header("Authorization", bearerOf(operator)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)));
        }

        @Test
        @DisplayName("Officer cannot access operator list endpoint")
        void list_byOfficer_returns403() throws Exception {
            mockMvc.perform(get("/api/operator/applications")
                            .header("Authorization", bearerOf(officer)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("SPEC — all status labels use operator-facing labels, no internalStatus")
        void list_statusLabelsAreOperatorFacing() throws Exception {
            seedApplication(operator, ApplicationStatus.APPLICATION_RECEIVED);

            mockMvc.perform(get("/api/operator/applications")
                            .header("Authorization", bearerOf(operator)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].statusLabel").value("Submitted"))   // operator label
                    .andExpect(jsonPath("$.data[0].internalStatus").doesNotExist());
        }

        @Test
        @DisplayName("SPEC — PENDING_APPROVAL maps to 'Under Review' for operator (not 'Pending Approval')")
        void list_pendingApproval_shownAsUnderReview() throws Exception {
            seedApplication(operator, ApplicationStatus.PENDING_APPROVAL);

            mockMvc.perform(get("/api/operator/applications")
                            .header("Authorization", bearerOf(operator)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].statusLabel").value("Under Review"))
                    .andExpect(jsonPath("$.data[0].internalStatus").doesNotExist());
        }
    }

    // ── Get application detail ────────────────────────────────────

    @Nested
    @DisplayName("GET /api/operator/applications/{id}")
    class GetDetail {

        @Test
        @DisplayName("Operator gets own application detail")
        void getDetail_ownApplication_returns200() throws Exception {
            var app = seedApplication(operator, ApplicationStatus.UNDER_REVIEW);

            mockMvc.perform(get("/api/operator/applications/" + app.getId())
                            .header("Authorization", bearerOf(operator)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(app.getId()))
                    .andExpect(jsonPath("$.data.businessName").value("Test Business"));
        }

        @Test
        @DisplayName("ROLE ISOLATION — Operator cannot access another operator's application")
        void getDetail_anotherOperatorsApp_returns403() throws Exception {
            var otherApp = seedApplication(operator2, ApplicationStatus.UNDER_REVIEW);

            mockMvc.perform(get("/api/operator/applications/" + otherApp.getId())
                            .header("Authorization", bearerOf(operator)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Non-existent application returns 404")
        void getDetail_nonExistent_returns404() throws Exception {
            mockMvc.perform(get("/api/operator/applications/99999")
                            .header("Authorization", bearerOf(operator)))
                    .andExpect(status().isNotFound());
        }
    }

    // ── Resubmission ─────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/operator/applications/{id}/resubmit")
    class Resubmit {

        @Test
        @DisplayName("Operator resubmits when in PENDING_PRE_SITE_RESUBMISSION → PRE_SITE_RESUBMITTED")
        void resubmit_pendingPreSite_succeeds() throws Exception {
            var app = seedApplication(operator, ApplicationStatus.PENDING_PRE_SITE_RESUBMISSION);

            mockMvc.perform(patch("/api/operator/applications/" + app.getId() + "/resubmit")
                            .header("Authorization", bearerOf(operator))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"activityDescription":"Updated activity description with more details"}
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.statusLabel").value("Pre-Site Resubmitted"))
                    .andExpect(jsonPath("$.data.submissionRound").value(2));
        }

        @Test
        @DisplayName("Operator resubmits post-site → POST_SITE_CLARIFICATION_RESUBMITTED")
        void resubmit_pendingPostSite_succeeds() throws Exception {
            var app = seedApplication(operator, ApplicationStatus.PENDING_POST_SITE_RESUBMISSION);

            mockMvc.perform(patch("/api/operator/applications/" + app.getId() + "/resubmit")
                            .header("Authorization", bearerOf(operator))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.statusLabel").value("Post-Site Resubmitted"));
        }

        @Test
        @DisplayName("Resubmit when not in resubmission state → 409 Conflict")
        void resubmit_wrongStatus_returns409() throws Exception {
            var app = seedApplication(operator, ApplicationStatus.UNDER_REVIEW);

            mockMvc.perform(patch("/api/operator/applications/" + app.getId() + "/resubmit")
                            .header("Authorization", bearerOf(operator))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("ROLE ISOLATION — Officer cannot trigger resubmission")
        void resubmit_byOfficer_returns403() throws Exception {
            var app = seedApplication(operator, ApplicationStatus.PENDING_PRE_SITE_RESUBMISSION);

            mockMvc.perform(patch("/api/operator/applications/" + app.getId() + "/resubmit")
                            .header("Authorization", bearerOf(officer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ROLE ISOLATION — Operator cannot resubmit another operator's application")
        void resubmit_anotherOperatorsApp_returns403() throws Exception {
            var app = seedApplication(operator2, ApplicationStatus.PENDING_PRE_SITE_RESUBMISSION);

            mockMvc.perform(patch("/api/operator/applications/" + app.getId() + "/resubmit")
                            .header("Authorization", bearerOf(operator))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────
    private String validSubmitJson() {
        return """
            {
              "businessName": "Test Cafe Pte Ltd",
              "businessType": "Food & Beverage",
              "businessAddress": "1 Orchard Road, Singapore 238801",
              "contactPhone": "+65 9123 4567",
              "activityDescription": "Café selling coffee, cakes and light meals"
            }
            """;
    }
}
