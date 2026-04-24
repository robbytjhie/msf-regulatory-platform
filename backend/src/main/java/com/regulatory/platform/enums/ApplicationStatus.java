package com.regulatory.platform.enums;

/**
 * Internal application statuses with role-specific display labels.
 * Operators NEVER see the internal approval stage — only Approved/Rejected outcome.
 * Spec: UC2 Status Mapping table.
 */
public enum ApplicationStatus {

    APPLICATION_RECEIVED("Application Received", "Submitted"),
    UNDER_REVIEW("Under Review", "Under Review"),
    PENDING_PRE_SITE_RESUBMISSION("Pending Pre-Site Resubmission", "Pending Pre-Site Resubmission"),
    PRE_SITE_RESUBMITTED("Pre-Site Resubmitted", "Pre-Site Resubmitted"),
    SITE_VISIT_SCHEDULED("Site Visit Scheduled", "Pending Site Visit"),
    SITE_VISIT_DONE("Site Visit Done", "Site Visit Done"),
    AWAITING_POST_SITE_CLARIFICATION("Awaiting Post-Site Clarification", "Pending Post-Site Clarification"),
    PENDING_POST_SITE_RESUBMISSION("Pending Post-Site Resubmission", "Awaiting Post-Site Resubmission"),
    POST_SITE_CLARIFICATION_RESUBMITTED("Post-Site Clarification Resubmitted", "Post-Site Resubmitted"),
    PENDING_APPROVAL("Pending Approval", null), // Operators never see this label
    APPROVED("Approved", "Approved"),
    REJECTED("Rejected", "Rejected");

    private final String officerLabel;
    private final String operatorLabel; // null = not shown to operator until outcome

    ApplicationStatus(String officerLabel, String operatorLabel) {
        this.officerLabel = officerLabel;
        this.operatorLabel = operatorLabel;
    }

    public String getOfficerLabel() {
        return officerLabel;
    }

    /**
     * Returns the operator-visible label.
     * PENDING_APPROVAL is intentionally hidden — operators see no label until final decision.
     */
    public String getOperatorLabel() {
        if (this == PENDING_APPROVAL) {
            // Spec constraint: "Operators cannot see the internal approval stage at any point"
            return "Under Review";
        }
        return operatorLabel != null ? operatorLabel : officerLabel;
    }

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }
}
