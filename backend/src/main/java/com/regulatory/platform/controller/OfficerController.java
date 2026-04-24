package com.regulatory.platform.controller;

import com.regulatory.platform.dto.request.ChecklistSubmitRequest;
import com.regulatory.platform.dto.request.OfficerFeedbackRequest;
import com.regulatory.platform.dto.response.ApiResponse;
import com.regulatory.platform.dto.response.ApplicationDetailResponse;
import com.regulatory.platform.dto.response.ApplicationSummaryResponse;
import com.regulatory.platform.dto.response.ChecklistItemResponse;
import com.regulatory.platform.entity.User;
import com.regulatory.platform.repository.UserRepository;
import com.regulatory.platform.service.ApplicationService;
import com.regulatory.platform.service.ChecklistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/officer")
@RequiredArgsConstructor
public class OfficerController {

    private final ApplicationService applicationService;
    private final ChecklistService checklistService;
    private final UserRepository userRepository;

    // UC2: Dashboard — all applications
    @GetMapping("/applications")
    public ResponseEntity<ApiResponse<List<ApplicationSummaryResponse>>> list(
            @AuthenticationPrincipal UserDetails principal) {

        User officer = resolveUser(principal);
        return ResponseEntity.ok(ApiResponse.ok(applicationService.listForOfficer(officer)));
    }

    // UC2: Full application detail with all documents and comments
    @GetMapping("/applications/{id}")
    public ResponseEntity<ApiResponse<ApplicationDetailResponse>> get(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {

        User officer = resolveUser(principal);
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getForOfficer(id, officer)));
    }

    // UC2: Submit feedback — set status + add section-linked comments
    @PostMapping("/applications/{id}/feedback")
    public ResponseEntity<ApiResponse<ApplicationDetailResponse>> submitFeedback(
            @PathVariable Long id,
            @Valid @RequestBody OfficerFeedbackRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        User officer = resolveUser(principal);
        return ResponseEntity.ok(ApiResponse.ok(applicationService.submitOfficerFeedback(id, request, officer)));
    }

    // UC3: Get full checklist for site assessment
    @GetMapping("/applications/{id}/checklist")
    public ResponseEntity<ApiResponse<List<ChecklistItemResponse>>> getChecklist(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {

        User officer = resolveUser(principal);
        return ResponseEntity.ok(ApiResponse.ok(checklistService.getFullChecklist(id, officer)));
    }

    // UC3: Save checklist as draft (iPad use case)
    @PatchMapping("/applications/{id}/checklist/draft")
    public ResponseEntity<ApiResponse<Void>> saveDraft(
            @PathVariable Long id,
            @Valid @RequestBody ChecklistSubmitRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        User officer = resolveUser(principal);
        checklistService.saveDraft(id, request, officer);
        return ResponseEntity.ok(ApiResponse.ok("Draft saved", null));
    }

    // UC3: Submit final checklist — triggers automatic status transition
    @PostMapping("/applications/{id}/checklist/submit")
    public ResponseEntity<ApiResponse<Void>> submitChecklist(
            @PathVariable Long id,
            @Valid @RequestBody ChecklistSubmitRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        User officer = resolveUser(principal);
        checklistService.submitChecklist(id, request, officer);
        return ResponseEntity.ok(ApiResponse.ok("Checklist submitted", null));
    }

    private User resolveUser(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername()).orElseThrow();
    }
}
