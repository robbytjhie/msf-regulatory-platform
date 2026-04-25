export async function apiRequest(path, options = {}) {
  const token = sessionStorage.getItem("token") || localStorage.getItem("token");
  const headers = {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers || {}),
  };

  const response = await fetch(path, { ...options, headers });
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
    throw new Error(json.message || `HTTP ${response.status}`);
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
