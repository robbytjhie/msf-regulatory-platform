package com.regulatory.platform.service;

import com.regulatory.platform.dto.request.ApplicationSubmitRequest;
import com.regulatory.platform.dto.request.OfficerFeedbackRequest;
import com.regulatory.platform.dto.request.ResubmitRequest;
import com.regulatory.platform.dto.response.ApplicationDetailResponse;
import com.regulatory.platform.dto.response.ApplicationSummaryResponse;
import com.regulatory.platform.entity.User;

import java.util.List;

public interface ApplicationService {

    // UC1 — Operator submits a new application
    ApplicationDetailResponse submit(ApplicationSubmitRequest request, User operator);

    // UC1 — Operator resubmits with updated data
    ApplicationDetailResponse resubmit(Long applicationId, ResubmitRequest request, User operator);

    // UC2 — Officer fetches full detail view
    ApplicationDetailResponse getForOfficer(Long applicationId, User officer);

    // UC1 — Operator fetches their own application (filtered view)
    ApplicationDetailResponse getForOperator(Long applicationId, User operator);

    // UC2 — Officer lists all applications (dashboard)
    List<ApplicationSummaryResponse> listForOfficer(User officer);

    // UC1 — Operator lists their own applications
    List<ApplicationSummaryResponse> listForOperator(User operator);

    // UC2 — Officer requests more info / sets status
    ApplicationDetailResponse submitOfficerFeedback(Long applicationId,
                                                     OfficerFeedbackRequest request,
                                                     User officer);
}
