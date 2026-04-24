package com.regulatory.platform.controller;

import com.regulatory.platform.dto.request.ApplicationSubmitRequest;
import com.regulatory.platform.dto.request.OperatorClarificationRequest;
import com.regulatory.platform.dto.request.ResubmitRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/operator")
@RequiredArgsConstructor
public class OperatorController {

    private final ApplicationService applicationService;
    private final ChecklistService checklistService;
    private final UserRepository userRepository;

    // UC1: Submit new application
    @PostMapping("/applications")
    public ResponseEntity<ApiResponse<ApplicationDetailResponse>> submit(
            @Valid @RequestBody ApplicationSubmitRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        User operator = resolveUser(principal);
        ApplicationDetailResponse response = applicationService.submit(request, operator);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Application submitted", response));
    }

    // UC1: List own applications
    @GetMapping("/applications")
    public ResponseEntity<ApiResponse<List<ApplicationSummaryResponse>>> list(
            @AuthenticationPrincipal UserDetails principal) {

        User operator = resolveUser(principal);
        return ResponseEntity.ok(ApiResponse.ok(applicationService.listForOperator(operator)));
    }

    // UC1: Get application detail (operator view — filtered status labels)
    @GetMapping("/applications/{id}")
    public ResponseEntity<ApiResponse<ApplicationDetailResponse>> get(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {

        User operator = resolveUser(principal);
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getForOperator(id, operator)));
    }

    // UC1: Resubmit — only changed fields required
    @PatchMapping("/applications/{id}/resubmit")
    public ResponseEntity<ApiResponse<ApplicationDetailResponse>> resubmit(
            @PathVariable Long id,
            @RequestBody ResubmitRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        User operator = resolveUser(principal);
        return ResponseEntity.ok(ApiResponse.ok("Resubmission received", applicationService.resubmit(id, request, operator)));
    }

    // UC3: Get flagged checklist items ONLY (spec: operator never sees full checklist)
    @GetMapping("/applications/{id}/checklist/flagged")
    public ResponseEntity<ApiResponse<List<ChecklistItemResponse>>> getFlaggedItems(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {

        User operator = resolveUser(principal);
        return ResponseEntity.ok(ApiResponse.ok(checklistService.getFlaggedItemsForOperator(id, operator)));
    }

    // UC3: Respond to a specific flagged checklist item
    @PostMapping("/checklist/{itemId}/respond")
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> respondToItem(
            @PathVariable Long itemId,
            @Valid @RequestBody OperatorClarificationRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        User operator = resolveUser(principal);
        return ResponseEntity.ok(ApiResponse.ok(checklistService.respondToItem(itemId, request, operator)));
    }

    private User resolveUser(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername()).orElseThrow();
    }
}
