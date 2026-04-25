package com.regulatory.platform.controller;

import com.regulatory.platform.config.IntegrationTestBase;
import com.regulatory.platform.entity.ChecklistItem;
import com.regulatory.platform.enums.ApplicationStatus;
import com.regulatory.platform.enums.ChecklistItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("ChecklistController — UC3 On-Site Assessment & Post-Site Clarification")
class ChecklistControllerTest extends IntegrationTestBase {

    @BeforeEach
    void setup() { seedUsers(); }

    // ── Officer: get full checklist ───────────────────────────────

    @Nested
    @DisplayName("GET /api/officer/applications/{id}/checklist")
    class GetFullChecklist {

        @Test
        @DisplayName("Officer retrieves full checklist after site visit scheduled")
        void getChecklist_siteVisitScheduled_returns200() throws Exception {
            var app = seedApplicationWithChecklist(operator);

            mockMvc.perform(get("/api/officer/applications/" + app.getId() + "/checklist")
                            .header("Authorization", bearerOf(officer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].itemCode").value("TEST_01"))
                    .andExpect(jsonPath("$.data[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("Officer can still view checklist after case moves to PENDING_APPROVAL")
        void getChecklist_pendingApproval_returns200() throws Exception {
            var app = seedApplicationWithChecklist(operator);
            app.setStatus(ApplicationStatus.PENDING_APPROVAL);
            applicationRepository.save(app);

            mockMvc.perform(get("/api/officer/applications/" + app.getId() + "/checklist")
                            .header("Authorization", bearerOf(officer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(2)));
        }

        @Test
        @DisplayName("Checklist unavailable before site visit is scheduled → 403")
        void getChecklist_beforeSiteVisit_returns403() throws Exception {
            var app = seedApplication(operator, ApplicationStatus.UNDER_REVIEW);

            mockMvc.perform(get("/api/officer/applications/" + app.getId() + "/checklist")
                            .header("Authorization", bearerOf(officer)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ROLE ISOLATION — Operator cannot access full officer checklist endpoint")
        void getChecklist_byOperator_returns403() throws Exception {
            var app = seedApplicationWithChecklist(operator);

            mockMvc.perform(get("/api/officer/applications/" + app.getId() + "/checklist")
                            .header("Authorization", bearerOf(operator)))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Officer: save draft ───────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/officer/applications/{id}/checklist/draft")
    class SaveDraft {

        @Test
        @DisplayName("Officer saves checklist as draft — no status transition")
        void saveDraft_updatesItemsWithoutStatusChange() throws Exception {
            var app = seedApplicationWithChecklist(operator);
            var items = checklistItemRepository.findByApplicationIdOrderBySortOrderAsc(app.getId());

            mockMvc.perform(patch("/api/officer/applications/" + app.getId() + "/checklist/draft")
                            .header("Authorization", bearerOf(officer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildChecklistPayload(items, "SATISFACTORY", "Looks good")))
                    .andExpect(status().isOk());

            // Application status should NOT have changed
            var updated = applicationRepository.findById(app.getId()).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(updated.getStatus())
                    .isEqualTo(ApplicationStatus.SITE_VISIT_SCHEDULED);

            // But items should be updated
            var updatedItems = checklistItemRepository.findByApplicationIdOrderBySortOrderAsc(app.getId());
            org.assertj.core.api.Assertions.assertThat(updatedItems)
                    .allMatch(i -> i.getStatus() == ChecklistItemStatus.SATISFACTORY);
        }
    }

    // ── Officer: submit checklist ─────────────────────────────────

    @Nested
    @DisplayName("POST /api/officer/applications/{id}/checklist/submit")
    class SubmitChecklist {

        @Test
        @DisplayName("All items satisfactory → status moves to PENDING_APPROVAL")
        void submitChecklist_allSatisfactory_movesToPendingApproval() throws Exception {
            var app = seedApplicationWithChecklist(operator);
            var items = checklistItemRepository.findByApplicationIdOrderBySortOrderAsc(app.getId());

            mockMvc.perform(post("/api/officer/applications/" + app.getId() + "/checklist/submit")
                            .header("Authorization", bearerOf(officer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildChecklistPayload(items, "SATISFACTORY", "All good")))
                    .andExpect(status().isOk());

            var updated = applicationRepository.findById(app.getId()).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(updated.getStatus())
                    .isEqualTo(ApplicationStatus.PENDING_APPROVAL);
        }

        @Test
        @DisplayName("Any item flagged → status moves to AWAITING_POST_SITE_CLARIFICATION")
        void submitChecklist_hasFlaggedItem_movesToClarification() throws Exception {
            var app = seedApplicationWithChecklist(operator);
            var items = checklistItemRepository.findByApplicationIdOrderBySortOrderAsc(app.getId());

            // First item satisfactory, second needs clarification
            String payload = """
                {
                  "items": [
                    {"itemId": %d, "status": "SATISFACTORY", "officerComment": "OK"},
                    {"itemId": %d, "status": "NEEDS_CLARIFICATION", "officerComment": "Provide certificate"}
                  ]
                }
                """.formatted(items.get(0).getId(), items.get(1).getId());

            mockMvc.perform(post("/api/officer/applications/" + app.getId() + "/checklist/submit")
                            .header("Authorization", bearerOf(officer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk());

            var updated = applicationRepository.findById(app.getId()).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(updated.getStatus())
                    .isEqualTo(ApplicationStatus.AWAITING_POST_SITE_CLARIFICATION);
        }

        @Test
        @DisplayName("Checklist submit blocked when any item is still PENDING")
        void submitChecklist_withPendingItems_returns400() throws Exception {
            var app = seedApplicationWithChecklist(operator);
            var items = checklistItemRepository.findByApplicationIdOrderBySortOrderAsc(app.getId());

            String payload = """
                {
                  "items": [
                    {"itemId": %d, "status": "PENDING", "officerComment": "Not assessed yet"},
                    {"itemId": %d, "status": "SATISFACTORY", "officerComment": "OK"}
                  ]
                }
                """.formatted(items.get(0).getId(), items.get(1).getId());

            mockMvc.perform(post("/api/officer/applications/" + app.getId() + "/checklist/submit")
                            .header("Authorization", bearerOf(officer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("Checklist has pending items")));
        }

        @Test
        @DisplayName("ROLE ISOLATION — Operator cannot submit officer checklist")
        void submitChecklist_byOperator_returns403() throws Exception {
            var app = seedApplicationWithChecklist(operator);

            mockMvc.perform(post("/api/officer/applications/" + app.getId() + "/checklist/submit")
                            .header("Authorization", bearerOf(operator))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"items\":[]}"))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Operator: flagged items only (SPEC constraint) ────────────

    @Nested
    @DisplayName("GET /api/operator/applications/{id}/checklist/flagged")
    class GetFlaggedItems {

        @Test
        @DisplayName("SPEC — Operator sees ONLY flagged items, not full checklist")
        void getFlaggedItems_operatorSeesOnlyFlagged() throws Exception {
            var app = seedApplicationWithChecklist(operator);
            var items = checklistItemRepository.findByApplicationIdOrderBySortOrderAsc(app.getId());

            // Flag only the second item
            items.get(0).setStatus(ChecklistItemStatus.SATISFACTORY);
            items.get(1).setStatus(ChecklistItemStatus.NEEDS_CLARIFICATION);
            items.get(1).setOfficerComment("Please provide fire safety certificate");
            checklistItemRepository.saveAll(items);

            mockMvc.perform(get("/api/operator/applications/" + app.getId() + "/checklist/flagged")
                            .header("Authorization", bearerOf(operator)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)))                    // only 1 of 2
                    .andExpect(jsonPath("$.data[0].itemCode").value("TEST_02"))
                    .andExpect(jsonPath("$.data[0].officerComment")
                            .value("Please provide fire safety certificate"));
        }

        @Test
        @DisplayName("SPEC — No items flagged returns empty list (not 404)")
        void getFlaggedItems_noneFlag_returnsEmptyList() throws Exception {
            var app = seedApplicationWithChecklist(operator);
            var items = checklistItemRepository.findByApplicationIdOrderBySortOrderAsc(app.getId());
            items.forEach(i -> i.setStatus(ChecklistItemStatus.SATISFACTORY));
            checklistItemRepository.saveAll(items);

            mockMvc.perform(get("/api/operator/applications/" + app.getId() + "/checklist/flagged")
                            .header("Authorization", bearerOf(operator)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }

        @Test
        @DisplayName("ROLE ISOLATION — Operator2 cannot see operator's flagged items")
        void getFlaggedItems_anotherOperator_returns403() throws Exception {
            var app = seedApplicationWithChecklist(operator);

            mockMvc.perform(get("/api/operator/applications/" + app.getId() + "/checklist/flagged")
                            .header("Authorization", bearerOf(operator2)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ROLE ISOLATION — Officer cannot call operator checklist/flagged endpoint")
        void getFlaggedItems_byOfficer_returns403() throws Exception {
            var app = seedApplicationWithChecklist(operator);

            mockMvc.perform(get("/api/operator/applications/" + app.getId() + "/checklist/flagged")
                            .header("Authorization", bearerOf(officer)))
                    .andExpect(status().isForbidden());
        }
    }

    // ── Operator: respond to flagged item ─────────────────────────

    @Nested
    @DisplayName("POST /api/operator/checklist/{itemId}/respond")
    class RespondToItem {

        @Test
        @DisplayName("Operator responds to a NEEDS_CLARIFICATION item")
        void respond_validFlaggedItem_succeeds() throws Exception {
            var app = seedApplicationWithChecklist(operator);
            var items = checklistItemRepository.findByApplicationIdOrderBySortOrderAsc(app.getId());
            var flaggedItem = items.get(0);
            flaggedItem.setStatus(ChecklistItemStatus.NEEDS_CLARIFICATION);
            flaggedItem.setOfficerComment("Clarify this");
            checklistItemRepository.save(flaggedItem);

            mockMvc.perform(post("/api/operator/checklist/" + flaggedItem.getId() + "/respond")
                            .header("Authorization", bearerOf(operator))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"message":"Here is the required certificate, uploaded above"}
                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.operatorResponse")
                            .value("Here is the required certificate, uploaded above"));
        }

        @Test
        @DisplayName("Operator cannot respond to a SATISFACTORY item")
        void respond_satisfactoryItem_returns403() throws Exception {
            var app = seedApplicationWithChecklist(operator);
            var items = checklistItemRepository.findByApplicationIdOrderBySortOrderAsc(app.getId());
            var satisfactoryItem = items.get(0);
            satisfactoryItem.setStatus(ChecklistItemStatus.SATISFACTORY);
            checklistItemRepository.save(satisfactoryItem);

            mockMvc.perform(post("/api/operator/checklist/" + satisfactoryItem.getId() + "/respond")
                            .header("Authorization", bearerOf(operator))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"message":"Trying to respond anyway"}
                            """))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ROLE ISOLATION — Operator2 cannot respond to operator's checklist item")
        void respond_anotherOperatorsItem_returns403() throws Exception {
            var app = seedApplicationWithChecklist(operator);
            var items = checklistItemRepository.findByApplicationIdOrderBySortOrderAsc(app.getId());
            var flaggedItem = items.get(0);
            flaggedItem.setStatus(ChecklistItemStatus.NEEDS_CLARIFICATION);
            checklistItemRepository.save(flaggedItem);

            mockMvc.perform(post("/api/operator/checklist/" + flaggedItem.getId() + "/respond")
                            .header("Authorization", bearerOf(operator2))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"message":"Trying to respond to someone else's item"}
                            """))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Empty message → 400")
        void respond_emptyMessage_returns400() throws Exception {
            var app = seedApplicationWithChecklist(operator);
            var items = checklistItemRepository.findByApplicationIdOrderBySortOrderAsc(app.getId());
            var item = items.get(0);
            item.setStatus(ChecklistItemStatus.NEEDS_CLARIFICATION);
            checklistItemRepository.save(item);

            mockMvc.perform(post("/api/operator/checklist/" + item.getId() + "/respond")
                            .header("Authorization", bearerOf(operator))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"message":""}
                            """))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── Helper ────────────────────────────────────────────────────
    private String buildChecklistPayload(List<ChecklistItem> items,
                                          String status, String comment) {
        var sb = new StringBuilder("{\"items\":[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"itemId\":%d,\"status\":\"%s\",\"officerComment\":\"%s\"}"
                    .formatted(items.get(i).getId(), status, comment));
        }
        sb.append("]}");
        return sb.toString();
    }
}
