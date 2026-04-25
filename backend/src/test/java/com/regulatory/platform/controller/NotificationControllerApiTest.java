package com.regulatory.platform.controller;

import com.regulatory.platform.config.IntegrationTestBase;
import com.regulatory.platform.enums.ApplicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("NotificationController API")
class NotificationControllerApiTest extends IntegrationTestBase {

    @BeforeEach
    void setup() {
        seedUsers();
    }

    @Test
    @DisplayName("Officer can list own notifications")
    void list_forOfficer_returns200() throws Exception {
        seedApplication(operator, ApplicationStatus.APPLICATION_RECEIVED);

        mockMvc.perform(get("/api/notifications/me")
                        .header("Authorization", bearerOf(officer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("Operator can mark all notifications as read")
    void markAllRead_forOperator_returns200() throws Exception {
        mockMvc.perform(patch("/api/notifications/me/read-all")
                        .header("Authorization", bearerOf(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Notifications marked as read"));
    }

    @Test
    @DisplayName("Unauthenticated request is rejected")
    void list_withoutToken_returns403() throws Exception {
        mockMvc.perform(get("/api/notifications/me"))
                .andExpect(status().isForbidden());
    }
}
