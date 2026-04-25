export const NEEDS_ACTION = new Set([
  "APPLICATION_RECEIVED",
  "MANUAL_OFFICER_VALIDATION",
  "UNDER_REVIEW",
  "PRE_SITE_RESUBMITTED",
  "POST_SITE_CLARIFICATION_RESUBMITTED",
  "SITE_VISIT_DONE",
]);

export const IN_PROGRESS = new Set([
  "MANUAL_OFFICER_VALIDATION",
  "UNDER_REVIEW",
  "PENDING_PRE_SITE_RESUBMISSION",
  "SITE_VISIT_SCHEDULED",
  "AWAITING_POST_SITE_CLARIFICATION",
  "PENDING_POST_SITE_RESUBMISSION",
  "PENDING_APPROVAL",
]);

export const TERMINAL = new Set(["APPROVED", "REJECTED"]);

export function pendingOwnerByInternalStatus(internalStatus) {
  if (!internalStatus) return "In Progress";
  if (NEEDS_ACTION.has(internalStatus)) return "Pending Officer Action";
  if (internalStatus === "PENDING_PRE_SITE_RESUBMISSION" || internalStatus === "PENDING_POST_SITE_RESUBMISSION") {
    return "Pending Operator Action";
  }
  return "In Progress";
}

export function pendingOwnerByStatusLabel(statusLabel) {
  if (statusLabel === "Pending Pre-Site Resubmission" || statusLabel === "Pending Post-Site Resubmission") {
    return "Pending Your Action";
  }
  if (statusLabel === "Approved" || statusLabel === "Rejected") return statusLabel;
  return "In Progress";
}

export function statusClass(label) {
  const map = {
    Submitted: "badge-blue",
    "Under Review": "badge-amber",
    "Application Received": "badge-blue",
    "Manual Officer Validation": "badge-amber",
    "Pending Pre-Site Resubmission": "badge-red",
    "Pre-Site Resubmitted": "badge-amber",
    "Pending Site Visit": "badge-purple",
    "Site Visit Scheduled": "badge-purple",
    "Site Visit Done": "badge-purple",
    "Pending Post-Site Clarification": "badge-red",
    "Awaiting Post-Site Clarification": "badge-amber",
    "Awaiting Post-Site Resubmission": "badge-red",
    "Pending Post-Site Resubmission": "badge-amber",
    "Post-Site Clarification Resubmitted": "badge-amber",
    "Post-Site Resubmitted": "badge-amber",
    "Route to Approval": "badge-purple",
    "Pending Approval": "badge-purple",
    Approved: "badge-green",
    Rejected: "badge-red",
  };
  return map[label] || "badge-gray";
}
