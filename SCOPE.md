# SCOPE.md — Regulatory & Licensing Platform MVP

## What I Chose to Build

All three use cases are implemented, scoped to an MVP that demonstrates the full
end-to-end lifecycle without over-engineering secondary concerns.

### UC1 — Operator Application Submission & Resubmission ✅
- Guided submission form with conditional document upload sections (visible after required track/business selection)
- Drag-and-drop document upload with AI verification status surfaced to both operator and officer views
- Resubmission modal with partial-field update (only changed fields required)
- Round counter increments per resubmission; all previous data retained

### UC2 — Officer Application Review & Feedback ✅
- Officer dashboard with stats, filter tabs (All / Needs Action / In Progress / Completed)
- Full application detail view with tabs: Details, Documents, Comments, Status History
- Feedback modal: set new status, add section-linked comments, predefined templates
- Status machine enforced server-side — illegal transitions throw 409 Conflict
- Role-specific status labels: `ApplicationStatus` enum holds both officer and operator
  labels. `internalStatus` is `null` in all operator-facing API responses

### UC3 — On-Site Assessment & Post-Site Clarification ✅
- Officer checklist page with per-item status (Satisfactory / Needs Clarification)
- Draft save for iPad use case
- Automatic status transition on checklist submit (→ AWAITING_POST_SITE_CLARIFICATION
  if any items flagged, → PENDING_APPROVAL if all satisfactory)
- Operator sees ONLY flagged items — never the full checklist (enforced in repository
  query and separate DTO factory method)
- Multi-round clarification threads per checklist item

---

## What I Explicitly Deferred / Mocked

| Feature | Decision | Rationale |
|---|---|---|
| Email notifications | Implemented via `JavaMailSender` with env-driven SMTP and hardcoded demo recipients | Meets MVP notification requirement; production rollout should switch to configurable recipient routing + template service |
| File storage | Local placeholder document storage for demo downloads | Enables officer download/open flow without external object store; production should migrate to multipart binary upload + S3/GCS |
| AI document verification | Metadata-based backend verification (external AI optional + deterministic heuristic fallback) | No binary/OCR extraction yet; current checks rely on filename/type/size/category metadata |
| Pagination | Not implemented on list endpoints | MVP scale; add `Pageable` to repository queries when needed |
| Admin portal | No admin-specific UI | Not in the three use cases; `UserRole.ADMIN` exists in the enum |
| Password reset / registration | No self-serve registration | Officers and operators are provisioned by an admin; demo users seeded via `DataSeeder` |
| Real-time notifications | Implemented with SSE + automatic 2s polling fallback | Covers live UX in unstable/local environments; can be hardened with reconnect backoff and brokered events later |
| Operator multi-user (org accounts) | Single user per organisation | Spec is silent on this; `organisationName` field exists for future use |

---

## Assumptions

1. **Status mapping is authoritative.** The table in UC2 is implemented exactly as
   specified. Any ambiguous transition (e.g. officer re-triggering UNDER_REVIEW from
   PRE_SITE_RESUBMITTED) is included in the state machine.

2. **Internal status remains hidden from operator APIs.** `internalStatus` is always
   `null` for operator-facing responses; operators rely on the mapped `statusLabel`
   for workflow guidance.

3. **"No need to re-enter entire application"** means a PATCH endpoint accepting only
   changed fields (`ResubmitRequest` has all nullable fields).

4. **Document upload** is a separate concern from form submission. Files can be added
   during submission or resubmission. The backend models are fully in place; the
   multipart endpoint is the only missing implementation.

5. **Checklist items are seeded** because the spec doesn't describe how they are
   created (presumably templated per licence type). The `DataSeeder` inserts a realistic
   5-item checklist for the demo application.

6. **"Automatic operator notification"** (UC2) is implemented as in-app notifications
   with SSE live push and external email attempts. If SMTP fails, in-app notification
   persistence still succeeds (non-blocking fallback).

7. **AI fallback state is an explicit extension.** `MANUAL_OFFICER_VALIDATION` was added
   to support business continuity when AI verification is unavailable.

---

## Tech Stack

**Backend:** Spring Boot 3.2 / Java 21, Spring Security (JWT/stateless), Spring Data JPA,
H2 in-memory, Lombok, Bean Validation. Layered architecture: entity → repository →
service → controller. No Spring MVC views — pure REST API.

**Frontend:** React + Vite (`frontend-react`) with route guards and role-based pages
for officer/operator workflows. The design system keeps the same government-utility
visual language (status badges, cards, workflow cues), with improved interaction states
(loading, confirmation, pagination, and explicit pending-action labels).

**Testing:** Unit + integration tests across status transitions, auth/role isolation,
officer/operator controller paths, notification stream auth, seeding controls, and
coverage-sensitive regression paths.

---

## What I Would Do Next

1. **True multipart binary upload/download** — replace placeholder local files with
   secure binary persistence and content scanning.
2. **Template-driven notification service** — externalize email templates/recipients and
   add delivery telemetry + retry policy.
3. **Pagination + sorting** on list endpoints for production-scale datasets.
4. **Admin operations** — officer assignment UI + user lifecycle management.
5. **Advanced AI validation** — OCR/content extraction and policy rule explainability.
6. **Observability hardening** — metrics dashboards, alerting, and distributed tracing.
