function toReadableMessage(rawMessage, statusCode) {
  const m = (rawMessage || "").trim();
  if (!m) return `Request failed (HTTP ${statusCode})`;
  if (m.includes("Transition from")) {
    return "Status update cannot be submitted from the current state. Please refresh and choose a valid next status.";
  }
  if (m.includes("Missing required document categories")) {
    return "Some required documents are missing. Please upload all required items and try again.";
  }
  if (m.includes("Target document does not belong to this application")) {
    return "One selected document could not be matched to this application. Please refresh and try again.";
  }
  if (m.includes("Application is not awaiting resubmission")) {
    return "This application is currently not open for resubmission.";
  }
  if (m.includes("Checklist has pending items")) {
    return "Checklist submission is blocked. Please update all pending items before submitting.";
  }
  if (statusCode === 401) return "Your session has expired. Please sign in again.";
  if (statusCode === 403) return "You do not have permission to perform this action.";
  if (statusCode === 404) return "The requested record was not found.";
  if (statusCode >= 500) return "Server error occurred. Please try again in a moment.";
  return m;
}

export async function apiRequest(path, options = {}) {
  const token = sessionStorage.getItem("token") || localStorage.getItem("token");
  const headers = {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers || {}),
  };

  const response = await fetch(path, { cache: "no-store", ...options, headers });
  const text = await response.text();
  let json;
  try {
    json = text ? JSON.parse(text) : {};
  } catch {
    const hint = text.trim().slice(0, 280) || `HTTP ${response.status}`;
    throw new Error(
      hint.includes("CORS")
        ? `${hint} — allow your browser origin via APP_CORS_ALLOWED_ORIGIN_PATTERNS on the backend (see docs).`
        : hint
    );
  }
  if (!response.ok) {
    throw new Error(toReadableMessage(json.message || "", response.status));
  }
  return json.data;
}

export const api = {
  login: (email, password) =>
    apiRequest("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }),
  listOfficerApps: () => apiRequest("/api/officer/applications"),
  listOperatorApps: () => apiRequest("/api/operator/applications"),
  submitApplication: (payload) =>
    apiRequest("/api/operator/applications", { method: "POST", body: JSON.stringify(payload) }),
  getOperatorApplication: (id) => apiRequest(`/api/operator/applications/${id}`),
  getOperatorDocumentStatuses: (id) => apiRequest(`/api/operator/applications/${id}/documents/status`),
  reuploadOperatorDocument: (applicationId, documentId, payload) =>
    apiRequest(`/api/operator/applications/${applicationId}/documents/${documentId}/reupload-validate`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  resubmitApplication: (id, payload) =>
    apiRequest(`/api/operator/applications/${id}/resubmit`, { method: "PATCH", body: JSON.stringify(payload) }),
  getFlaggedItems: (id) => apiRequest(`/api/operator/applications/${id}/checklist/flagged`),
  respondToItem: (itemId, message) =>
    apiRequest(`/api/operator/checklist/${itemId}/respond`, {
      method: "POST",
      body: JSON.stringify({ message }),
    }),
  getOfficerApplication: (id) => apiRequest(`/api/officer/applications/${id}`),
  submitOfficerFeedback: (id, payload) =>
    apiRequest(`/api/officer/applications/${id}/feedback`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  getChecklist: (id) => apiRequest(`/api/officer/applications/${id}/checklist`),
  saveChecklistDraft: (id, payload) =>
    apiRequest(`/api/officer/applications/${id}/checklist/draft`, {
      method: "PATCH",
      body: JSON.stringify(payload),
    }),
  submitChecklist: (id, payload) =>
    apiRequest(`/api/officer/applications/${id}/checklist/submit`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),
};
