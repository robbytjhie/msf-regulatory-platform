package com.regulatory.platform.security;

import com.regulatory.platform.config.IntegrationTestBase;
import com.regulatory.platform.enums.ApplicationStatus;
import org.junit.jupiter.api.*;
import org.springframework.test.annotation.DirtiesContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("SECURITY — Role Isolation & Data Leakage Prevention")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RoleIsolationTest extends IntegrationTestBase {

    @BeforeEach
    void setup() { seedUsers(); }

    // ── Endpoint cross-role access ────────────────────────────────

    @Test @Order(1)
    @DisplayName("Operator cannot call any /api/officer/** endpoint")
    void operator_cannotAccessOfficerEndpoints() throws Exception {
        var app = seedApplication(operator, ApplicationStatus.UNDER_REVIEW);

        mockMvc.perform(get("/api/officer/applications").header("Authorization", bearerOf(operator)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/officer/applications/" + app.getId()).header("Authorization", bearerOf(operator)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/officer/applications/" + app.getId() + "/checklist").header("Authorization", bearerOf(operator)))
                .andExpect(status().isForbidden());
    }

    @Test @Order(2)
    @DisplayName("Officer cannot call any /api/operator/** endpoint")
    void officer_cannotAccessOperatorEndpoints() throws Exception {
        var app = seedApplication(operator, ApplicationStatus.UNDER_REVIEW);

        mockMvc.perform(get("/api/operator/applications").header("Authorization", bearerOf(officer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/operator/applications/" + app.getId()).header("Authorization", bearerOf(officer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/operator/applications/" + app.getId() + "/checklist/flagged")
                        .header("Authorization", bearerOf(officer)))
                .andExpect(status().isForbidden());
    }

    @Test @Order(3)
    @DisplayName("Anonymous cannot access any protected endpoint")
    void anonymous_cannotAccessAnyProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/operator/applications")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/officer/applications")).andExpect(status().isForbidden());
    }

    // ── SPEC: internalStatus leakage prevention ───────────────────

    @Test @Order(4)
    @DisplayName("SPEC — operator response uses mapped status label for PENDING_APPROVAL")
    void pendingApproval_operatorNeverSeesLabel() throws Exception {
        var app = seedApplication(operator, ApplicationStatus.PENDING_APPROVAL);

        var result = mockMvc.perform(get("/api/operator/applications/" + app.getId())
                        .header("Authorization", bearerOf(operator)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .contains("Pending Approval")
                .doesNotContain("\"internalStatus\":\"PENDING_APPROVAL\"");
    }

    @Test @Order(5)
    @DisplayName("SPEC — internalStatus field absent from operator response for PENDING_APPROVAL")
    void operatorResponse_neverContainsInternalStatus() throws Exception {
        var app = seedApplication(operator, ApplicationStatus.PENDING_APPROVAL);

        var result = mockMvc.perform(get("/api/operator/applications/" + app.getId())
                        .header("Authorization", bearerOf(operator)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("\"internalStatus\":\"PENDING_APPROVAL\"");
    }

    @Test @Order(6)
    @DisplayName("SPEC — Officer email not in operator response")
    void operatorResponse_officerEmailNotExposed() throws Exception {
        var app = seedApplication(operator, ApplicationStatus.UNDER_REVIEW);

        var result = mockMvc.perform(get("/api/operator/applications/" + app.getId())
                        .header("Authorization", bearerOf(operator)))
                .andExpect(status().isOk())
                .andReturn();

        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .doesNotContain("officer@test.gov.sg");
    }

    @Test @Order(7)
    @DisplayName("SPEC — Officer list response contains internalStatus")
    void officerResponse_containsInternalStatus() throws Exception {
        seedApplication(operator, ApplicationStatus.PENDING_APPROVAL);

        mockMvc.perform(get("/api/officer/applications/" + applicationRepository.findAll().get(0).getId())
                        .header("Authorization", bearerOf(officer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.internalStatus").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.statusLabel").value("Route to Approval"));
    }

    // ── Operator data isolation ───────────────────────────────────

    @Test @Order(8)
    @DisplayName("Operator A cannot read Operator B's application")
    void operatorA_cannotReadOperatorBApplication() throws Exception {
        var appB = seedApplication(operator2, ApplicationStatus.UNDER_REVIEW);

        mockMvc.perform(get("/api/operator/applications/" + appB.getId())
                        .header("Authorization", bearerOf(operator)))
                .andExpect(status().isForbidden());
    }

    @Test @Order(9)
    @DisplayName("Operator A's list never includes Operator B's apps")
    void operatorA_listDoesNotIncludeOperatorBApps() throws Exception {
        seedApplication(operator, ApplicationStatus.APPLICATION_RECEIVED);
        seedApplication(operator2, ApplicationStatus.APPLICATION_RECEIVED);

        var result = mockMvc.perform(get("/api/operator/applications")
                        .header("Authorization", bearerOf(operator)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("Other Operator")
                .doesNotContain("operator2@test.com");
    }

    // ── JWT tampering ─────────────────────────────────────────────

    @Test @Order(10)
    @DisplayName("Tampered JWT token is rejected")
    void tamperedJwt_returns403() throws Exception {
        String validToken = tokenFor(operator);
        String tampered = validToken.substring(0, validToken.lastIndexOf('.') + 1) + "TAMPERED";

        mockMvc.perform(get("/api/operator/applications")
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(status().isForbidden());
    }

    @Test @Order(11)
    @DisplayName("Malformed JWT is rejected")
    void malformedJwt_returns403() throws Exception {
        mockMvc.perform(get("/api/operator/applications")
                        .header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isForbidden());
    }
}
