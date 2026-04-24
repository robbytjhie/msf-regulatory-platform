package com.regulatory.platform.controller;

import com.regulatory.platform.config.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@DisplayName("AuthController — Login")
class AuthControllerTest extends IntegrationTestBase {

    @BeforeEach
    void setup() { seedUsers(); }

    @Test
    @DisplayName("Officer can login with valid credentials and receives JWT")
    void login_officer_validCredentials_returnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginReq("officer@test.gov.sg", "password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("OFFICER"))
                .andExpect(jsonPath("$.data.email").value("officer@test.gov.sg"));
    }

    @Test
    @DisplayName("Operator can login and receives OPERATOR role")
    void login_operator_validCredentials_returnsOperatorRole() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginReq("operator@test.com", "password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("OPERATOR"));
    }

    @Test
    @DisplayName("Wrong password returns 401")
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginReq("officer@test.gov.sg", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Unknown email returns 401")
    void login_unknownEmail_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginReq("nobody@test.com", "password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Missing email fails Bean Validation with 400")
    void login_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Invalid email format fails Bean Validation with 400")
    void login_invalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginReq("not-an-email", "password"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Unauthenticated access to protected endpoint returns 401/403")
    void noToken_protectedEndpoint_returns401or403() throws Exception {
        mockMvc.perform(post("/api/officer/applications/1/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    record LoginReq(String email, String password) {}
}
