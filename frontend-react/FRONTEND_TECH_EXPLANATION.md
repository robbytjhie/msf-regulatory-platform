# Frontend Tech and Class/Module Explanation

This document explains why the frontend uses the current tech stack, and what each main module/page is responsible for.

## Why This Tech Stack

- **React (functional components + hooks)**  
  Chosen for fast UI iteration, component reusability, and straightforward local state management with `useState`, `useEffect`, and `useMemo`.

- **React Router**  
  Provides role-based route separation (`officer` vs `operator`) and deep-linking to specific pages (application detail, checklist, etc.).

- **Fetch via shared `apiClient`**  
  A single request wrapper centralizes auth headers, error mapping, and cache behavior. This keeps API handling consistent across pages.

- **Session storage for auth (`sessionStorage`)**  
  Used to isolate login context per browser tab and reduce role/session leakage during officer/operator manual testing.

- **Polling (2s intervals)**  
  Used for near-real-time collaboration between officer and operator views without adding websocket infrastructure.  
  Examples: dashboard updates, application detail updates, AI verification progress.

- **Plain CSS (`App.css`)**  
  Keeps styling simple for assessment scope while still supporting shared utility classes and consistent badge/layout behavior.

## Core Modules

- **`src/apiClient.js`**  
  Central API layer.  
  Responsibilities:
  - attach JWT token headers
  - enforce `cache: "no-store"` to avoid stale role-to-role data
  - convert backend/system errors into user-readable messages
  - expose typed endpoint functions used by pages

- **`src/AppRoutes.jsx`**  
  Route table and auth guard.  
  Responsibilities:
  - enforce login check
  - enforce role check
  - redirect unauthorized users to correct dashboard

- **`src/statusUtils.js`**  
  Status presentation and ownership helper utilities.  
  Responsibilities:
  - map status to badge style
  - map status to “Pending Officer/Operator Action”
  - define status buckets (`NEEDS_ACTION`, `IN_PROGRESS`, `TERMINAL`) used by dashboards

- **`src/documentRequirements.js`**  
  Configuration-driven requirement catalog by licensing track.  
  Responsibilities:
  - define required/optional document categories
  - provide examples and AI guidance text
  - generate required category list for validation

## Page-Level Responsibilities

- **`src/pages/LoginPage.jsx`**  
  Authentication entry point and demo account autofill.

- **`src/pages/OperatorDashboardPage.jsx`**  
  Operator list view with search/sort/pagination + 2s polling.  
  Purpose: show operator-owned applications and whether operator action is required.

- **`src/pages/OperatorSubmitPage.jsx`**  
  New application flow.  
  Purpose:
  - collect basic application details
  - enforce licensing track + business type selection
  - collect supporting documents by category
  - validate required categories before submit

- **`src/pages/OperatorApplicationPage.jsx`**  
  Operator application detail/resubmission workflow + polling.  
  Purpose:
  - show status timeline and documents
  - show unresolved officer-returned docs
  - allow file replacement + revalidation
  - allow checklist clarification responses when requested

- **`src/pages/OfficerDashboardPage.jsx`**  
  Officer list view with filters/search/sort/pagination + 2s polling.  
  Purpose: triage all applications and quickly identify records needing officer action.

- **`src/pages/OfficerApplicationPage.jsx`**  
  Officer review/detail page + polling.  
  Purpose:
  - show full applicant details and documents
  - support per-document return comments
  - enforce valid status transitions in dropdown
  - open checklist page when available

- **`src/pages/OfficerChecklistPage.jsx`**  
  Officer checklist execution page.  
  Purpose:
  - update checklist item status/comments when officer owns action
  - enforce submit rules (e.g., no pending items)
  - lock checklist when resolution/finalization conditions are reached

## Test Files (Purpose)

- **`*.test.jsx` / `*.test.js` in `src/pages` and root**  
  Focus on behavioral safety around:
  - polling behavior
  - role/status mappings
  - page-level critical flows

This protects high-risk workflow changes from regressions while keeping test maintenance manageable.
