export async function apiRequest(path, options = {}) {
  const token = localStorage.getItem("token");
  const headers = {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers || {}),
  };

  const response = await fetch(path, { ...options, headers });
  const json = await response.json();
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
