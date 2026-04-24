package com.regulatory.platform.service;

import com.regulatory.platform.dto.request.ChecklistSubmitRequest;
import com.regulatory.platform.dto.request.OperatorClarificationRequest;
import com.regulatory.platform.dto.response.ChecklistItemResponse;
import com.regulatory.platform.entity.User;

import java.util.List;

public interface ChecklistService {

    // Officer: get full checklist for an application
    List<ChecklistItemResponse> getFullChecklist(Long applicationId, User officer);

    // Officer: save checklist as draft (iPad use case)
    void saveDraft(Long applicationId, ChecklistSubmitRequest request, User officer);

    // Officer: submit final checklist — triggers status transition
    void submitChecklist(Long applicationId, ChecklistSubmitRequest request, User officer);

    // Operator: get ONLY flagged items (spec constraint)
    List<ChecklistItemResponse> getFlaggedItemsForOperator(Long applicationId, User operator);

    // Operator: respond to a flagged checklist item
    ChecklistItemResponse respondToItem(Long checklistItemId,
                                        OperatorClarificationRequest request,
                                        User operator);
}
