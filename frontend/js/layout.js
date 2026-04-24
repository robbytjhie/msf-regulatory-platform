// layout.js — shared utilities for all authenticated pages

// ── Auth guard ──────────────────────────────────────────────────
function requireAuth(expectedRole) {
  const token = localStorage.getItem('token');
  const user = JSON.parse(localStorage.getItem('user') || 'null');
  if (!token || !user) { window.location.href = '/pages/login.html'; return null; }
  if (expectedRole && user.role !== expectedRole) {
    window.location.href = user.role === 'OFFICER'
      ? '/pages/officer-dashboard.html'
      : '/pages/operator-dashboard.html';
    return null;
  }
  return user;
}

function logout() {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
  window.location.href = '/pages/login.html';
}

// ── Status helpers ──────────────────────────────────────────────
const STATUS_BADGE = {
  'Submitted':                    'badge-blue',
  'Under Review':                 'badge-amber',
  'Application Received':         'badge-blue',
  'Pending Pre-Site Resubmission':'badge-red',
  'Pre-Site Resubmitted':         'badge-amber',
  'Pending Site Visit':           'badge-purple',
  'Site Visit Scheduled':         'badge-purple',
  'Site Visit Done':              'badge-purple',
  'Pending Post-Site Clarification': 'badge-red',
  'Awaiting Post-Site Clarification': 'badge-amber',
  'Awaiting Post-Site Resubmission':  'badge-red',
  'Pending Post-Site Resubmission':   'badge-amber',
  'Post-Site Clarification Resubmitted': 'badge-amber',
  'Post-Site Resubmitted':        'badge-amber',
  'Route to Approval':            'badge-purple',
  'Pending Approval':             'badge-purple',
  'Approved':                     'badge-green',
  'Rejected':                     'badge-red',
};

function statusBadge(label) {
  const cls = STATUS_BADGE[label] || 'badge-gray';
  return `<span class="badge ${cls}">${escHtml(label)}</span>`;
}

// ── Formatting helpers ──────────────────────────────────────────
function escHtml(str) {
  return String(str ?? '')
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
    .replace(/"/g,'&quot;').replace(/'/g,'&#39;');
}

function fmtDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-SG', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  });
}

function fmtDateShort(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-SG', {
    day: '2-digit', month: 'short', year: 'numeric'
  });
}

function fmtBytes(bytes) {
  if (!bytes) return '';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

// ── Sidebar rendering ───────────────────────────────────────────
function renderSidebar(user, activeNav) {
  const isOfficer = user.role === 'OFFICER';
  const initials = user.fullName.split(' ').map(n => n[0]).join('').slice(0,2).toUpperCase();

  const officerNav = [
    { id: 'dashboard', icon: '⊞', label: 'Dashboard', href: '/pages/officer-dashboard.html' },
    { id: 'applications', icon: '📋', label: 'All Applications', href: '/pages/officer-dashboard.html' },
  ];
  const operatorNav = [
    { id: 'dashboard', icon: '⊞', label: 'My Applications', href: '/pages/operator-dashboard.html' },
    { id: 'submit', icon: '＋', label: 'New Application', href: '/pages/operator-submit.html' },
  ];
  const navItems = isOfficer ? officerNav : operatorNav;

  return `
    <div class="sidebar-brand">
      <div class="sidebar-brand-logo">GOV.SG · REGULATORY</div>
      <div class="sidebar-brand-name">Licensing &amp; Regulatory<br>Management System</div>
    </div>
    <nav class="sidebar-nav">
      <div class="sidebar-section">${isOfficer ? 'Officer' : 'Operator'} Portal</div>
      ${navItems.map(item => `
        <div class="nav-item ${item.id === activeNav ? 'active' : ''}"
             onclick="window.location.href='${item.href}'">
          <span class="nav-icon">${item.icon}</span>
          ${item.label}
        </div>
      `).join('')}
    </nav>
    <div class="sidebar-footer">
      <div class="sidebar-user-avatar">${initials}</div>
      <div>
        <div class="sidebar-user-name">${escHtml(user.fullName)}</div>
        <div class="sidebar-user-role">${user.role}</div>
      </div>
      <button class="sidebar-logout" onclick="logout()" title="Sign out">⏻</button>
    </div>
  `;
}

// ── Toast notification ──────────────────────────────────────────
let _toastTimer;
function toast(msg, type = 'success') {
  let el = document.getElementById('_toast');
  if (!el) {
    el = document.createElement('div');
    el.id = '_toast';
    el.style.cssText = `
      position:fixed; bottom:24px; right:24px; z-index:9999;
      padding:12px 20px; border-radius:6px; font-size:13.5px; font-weight:600;
      box-shadow:0 4px 16px rgba(0,0,0,.15); transition:all .2s ease;
      max-width:360px; line-height:1.4;
    `;
    document.body.appendChild(el);
  }
  el.textContent = msg;
  el.style.background = type === 'error' ? '#c8392b' : type === 'warning' ? '#d4870a' : '#1a4d2e';
  el.style.color = '#fff';
  el.style.opacity = '1';
  clearTimeout(_toastTimer);
  _toastTimer = setTimeout(() => { el.style.opacity = '0'; }, 3500);
}

// ── AI verification badge ───────────────────────────────────────
function aiVerificationBadge(status) {
  const map = {
    PENDING:    ['file-ai-pending', '⏳ Pending'],
    PROCESSING: ['file-ai-pending', '⟳ Verifying'],
    PASSED:     ['file-ai-pass',    '✓ Verified'],
    FLAGGED:    ['file-ai-flag',    '⚠ Flagged'],
    FAILED:     ['file-ai-flag',    '✗ Failed'],
  };
  const [cls, label] = map[status] || ['file-ai-pending', status];
  return `<span class="file-ai-badge ${cls}">${label}</span>`;
}

// ── Officer status options for feedback modal ───────────────────
const OFFICER_STATUS_OPTIONS = [
  { value: 'UNDER_REVIEW',                    label: 'Under Review' },
  { value: 'PENDING_PRE_SITE_RESUBMISSION',   label: 'Request Pre-Site Resubmission' },
  { value: 'SITE_VISIT_SCHEDULED',            label: 'Schedule Site Visit' },
  { value: 'SITE_VISIT_DONE',                 label: 'Mark Site Visit Done' },
  { value: 'AWAITING_POST_SITE_CLARIFICATION',label: 'Request Post-Site Clarification' },
  { value: 'PENDING_APPROVAL',                label: 'Route to Approval' },
  { value: 'APPROVED',                        label: 'Approve Application' },
  { value: 'REJECTED',                        label: 'Reject Application' },
];

const COMMENT_TEMPLATES = [
  'Please provide a certified copy of your business registration certificate.',
  'The uploaded document is unclear or illegible. Please resubmit a higher quality scan.',
  'Your site plan does not include the required fire exit markings.',
  'The floor area stated in the application does not match the site plan.',
  'Please provide evidence of compliance with NEA food hygiene requirements.',
  'The business address provided is incomplete. Please include the full postal code.',
];
