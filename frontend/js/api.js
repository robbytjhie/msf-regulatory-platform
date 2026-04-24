// api.js — centralised HTTP client for all API calls
const API_BASE = 'http://localhost:8080/api';

const api = {
  _token: () => localStorage.getItem('token'),

  _headers() {
    const h = { 'Content-Type': 'application/json' };
    const t = this._token();
    if (t) h['Authorization'] = `Bearer ${t}`;
    return h;
  },

  async _fetch(method, path, body) {
    const res = await fetch(`${API_BASE}${path}`, {
      method,
      headers: this._headers(),
      body: body ? JSON.stringify(body) : undefined,
    });
    const json = await res.json();
    if (!res.ok) throw new Error(json.message || `HTTP ${res.status}`);
    return json.data;
  },

  // Auth
  login: (email, password) =>
    api._fetch('POST', '/auth/login', { email, password }),

  // Operator
  submitApplication: (data) =>
    api._fetch('POST', '/operator/applications', data),
  listMyApplications: () =>
    api._fetch('GET', '/operator/applications'),
  getMyApplication: (id) =>
    api._fetch('GET', `/operator/applications/${id}`),
  resubmit: (id, data) =>
    api._fetch('PATCH', `/operator/applications/${id}/resubmit`, data),
  getFlaggedItems: (id) =>
    api._fetch('GET', `/operator/applications/${id}/checklist/flagged`),
  respondToItem: (itemId, data) =>
    api._fetch('POST', `/operator/checklist/${itemId}/respond`, data),

  // Officer
  listAllApplications: () =>
    api._fetch('GET', '/officer/applications'),
  getApplication: (id) =>
    api._fetch('GET', `/officer/applications/${id}`),
  submitFeedback: (id, data) =>
    api._fetch('POST', `/officer/applications/${id}/feedback`, data),
  getChecklist: (id) =>
    api._fetch('GET', `/officer/applications/${id}/checklist`),
  saveDraft: (id, data) =>
    api._fetch('PATCH', `/officer/applications/${id}/checklist/draft`, data),
  submitChecklist: (id, data) =>
    api._fetch('POST', `/officer/applications/${id}/checklist/submit`, data),

  // Shared notifications
  listNotifications: () =>
    api._fetch('GET', '/notifications/me'),
  markNotificationsRead: () =>
    api._fetch('PATCH', '/notifications/me/read-all'),
};
