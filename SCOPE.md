# SCOPE.md — Regulatory & Licensing Platform MVP

## What I Chose to Build

All three use cases are implemented, scoped to an MVP that demonstrates the full
end-to-end lifecycle without over-engineering secondary concerns.

### UC1 — Operator Application Submission & Resubmission ✅
- Full guided submission form (3-step wizard: Business Info → Documents → Review)
- Drag-and-drop document upload with simulated AI verification status (PENDING → PASSED/FLAGGED)
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
| Email notifications | Stubbed — log message on status change | Requires SMTP/SES config; behaviour is clear from the code. Wire `JavaMailSender` in 1–2 hours |
| File storage | In-memory only; no actual file persistence | Real work requires S3/GCS config. The upload API endpoint exists; storage is the only missing piece |
| AI document verification | Simulated on frontend (random PASSED/FLAGGED after 1.5s) | No LLM integration specified; hook is in place server-side via `Document.AiVerificationStatus` |
| Pagination | Not implemented on list endpoints | MVP scale; add `Pageable` to repository queries when needed |
| Admin portal | No admin-specific UI | Not in the three use cases; `UserRole.ADMIN` exists in the enum |
| Password reset / registration | No self-serve registration | Officers and operators are provisioned by an admin; demo users seeded via `DataSeeder` |
| Real-time notifications | No WebSocket | Polling or SSE can be added; notifications are logged server-side |
| Operator multi-user (org accounts) | Single user per organisation | Spec is silent on this; `organisationName` field exists for future use |

---

## Assumptions

1. **Status mapping is authoritative.** The table in UC2 is implemented exactly as
   specified. Any ambiguous transition (e.g. officer re-triggering UNDER_REVIEW from
   PRE_SITE_RESUBMITTED) is included in the state machine.

2. **Operators cannot see `PENDING_APPROVAL`.** The spec says "Operators cannot see
   the internal approval stage at any point." This is enforced by returning `null`
   for `internalStatus` in all operator responses, and the `getOperatorLabel()` method
   maps `PENDING_APPROVAL` → `"Under Review"`.

3. **"No need to re-enter entire application"** means a PATCH endpoint accepting only
   changed fields (`ResubmitRequest` has all nullable fields).

4. **Document upload** is a separate concern from form submission. Files can be added
   during submission or resubmission. The backend models are fully in place; the
   multipart endpoint is the only missing implementation.

5. **Checklist items are seeded** because the spec doesn't describe how they are
   created (presumably templated per licence type). The `DataSeeder` inserts a realistic
   5-item checklist for the demo application.

6. **"Automatic operator notification"** (UC2) is interpreted as a logged event.
   The service layer is structured to make adding an actual email call trivial.

---

## Tech Stack

**Backend:** Spring Boot 3.2 / Java 21, Spring Security (JWT/stateless), Spring Data JPA,
H2 in-memory, Lombok, Bean Validation. Layered architecture: entity → repository →
service → controller. No Spring MVC views — pure REST API.

**Frontend:** Vanilla HTML/CSS/JS (no build step required). Intentionally chosen to
keep the setup frictionless: `open pages/login.html` in a browser. The design system
uses CSS custom properties for full theming; IBM Plex Sans/Mono for a distinctive
government-utility aesthetic. A React or Next.js frontend would be the natural next step.

**Testing:** Unit tests on `StatusTransitionService` (the most critical business logic).
Integration tests with MockMvc would be the next priority.

---

## What I Would Do Next

1. **Multipart document upload endpoint** — `POST /api/operator/applications/{id}/documents`
   with S3/local storage
2. **Email notifications** — Spring Mail triggered in service layer on status transitions
3. **Integration tests** — MockMvc for all controller endpoints, especially the
   role-isolation paths (operator cannot see officer internals)
4. **Pagination + sorting** on list endpoints
5. **Officer assignment** — allow assigning applications to specific officers from
   an admin view
6. **Real-time updates** — Server-Sent Events for operator dashboard to update when
   officer submits feedback
