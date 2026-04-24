# Regulatory & Licensing Platform

A Spring Boot + Vanilla JS system for managing the end-to-end licensing application
lifecycle between government officers and operators.

---

## Quick Start (Refactored Stack)

This repository now includes:
- `backend` (Spring Boot + H2 in-memory)
- `frontend-react` (Vite + React)

### Local Dev (2 terminals)

```bash
# terminal 1
cd backend
./mvnw spring-boot:run

# terminal 2
cd frontend-react
npm install
npm run dev
```

Open `http://localhost:5173`.

Login with previous credentials:
- Officer: `officer@gov.sg / password`
- Operator (ACME): `operator@acme.sg / password`
- Operator (Beta): `operator2@beta.sg / password`

---

## Docker Run

```bash
docker compose up --build
```

Open `http://localhost:3000`.

---

## Minikube Deploy

```powershell
cd infra/k8s
./deploy-minikube.ps1
minikube service frontend-react -n msf --url
```

---

## Security Scans

- SonarCloud: configured via `sonar-project.properties` and GitHub Action.
- Trivy: GitHub Action step `Trivy filesystem scan`.
- Set `SONAR_TOKEN` in repository secrets.

---

## Legacy Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+ (or use the included `mvnw` wrapper)

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

The server starts at **http://localhost:8080**.
H2 console is available at **http://localhost:8080/h2-console**
(JDBC URL: `jdbc:h2:mem:regulatorydb`, no password).

Demo data is seeded automatically on startup:

| Role | Email | Password |
|---|---|---|
| Officer | officer@gov.sg | password |
| Operator (ACME) | operator@acme.sg | password |
| Operator (Beta) | operator2@beta.sg | password |

### Frontend

No build step required. Open the pages directly in a browser:

```bash
open frontend/pages/login.html
# or on Linux:
xdg-open frontend/pages/login.html
```

> **CORS note:** The backend allows `localhost:*` origins. If you serve the frontend
> from a local file (e.g. via `file://`), some browsers block cross-origin requests.
> Use a simple dev server: `npx serve frontend` then open `http://localhost:3000/pages/login.html`

---

## Project Structure

```
regulatory-platform/
├── backend/
│   └── src/main/java/com/regulatory/platform/
│       ├── entity/          JPA entities (Application, User, Document, …)
│       ├── enums/           ApplicationStatus (with role-specific labels), UserRole, …
│       ├── repository/      Spring Data JPA repositories
│       ├── service/         Business logic, StatusTransitionService (state machine)
│       ├── controller/      REST controllers (Auth, Operator, Officer)
│       ├── dto/             Request/Response records
│       ├── security/        JWT filter, UserDetailsService
│       └── config/          SecurityConfig, DataSeeder
├── frontend/
│   ├── css/style.css        Design system (CSS variables, components)
│   ├── js/
│   │   ├── api.js           Centralised fetch client
│   │   └── layout.js        Shared utilities, sidebar, status helpers
│   └── pages/
│       ├── login.html
│       ├── officer-dashboard.html
│       ├── officer-application.html
│       ├── officer-checklist.html
│       ├── operator-dashboard.html
│       ├── operator-submit.html
│       └── operator-application.html
├── SCOPE.md
└── README.md
```

---

## Key Design Decisions

### Status Machine
`StatusTransitionService` holds a static `Map<ApplicationStatus, Set<ApplicationStatus>>`
of valid transitions. Any illegal transition throws `InvalidStatusTransitionException`
(HTTP 409). Role gating is enforced in the same method: resubmission transitions can
only be triggered by `OPERATOR`, and status-setting transitions only by `OFFICER`.

### Role-Specific Status Labels
`ApplicationStatus` enum has both `officerLabel` and `operatorLabel`. The spec's constraint
that "operators cannot see the internal approval stage" is enforced by:
1. `getOperatorLabel()` maps `PENDING_APPROVAL` → `"Under Review"`
2. `ApplicationDetailResponse.forOperator()` sets `internalStatus = null`

### Operator Checklist Isolation (UC3)
The repository has two separate queries:
- `findByApplicationIdOrderBySortOrderAsc` — returns all items (officer only)
- `findByApplicationIdAndStatusOrderBySortOrderAsc(NEEDS_CLARIFICATION)` — flagged only

The `ChecklistItemResponse` has two factory methods: `full()` and `flaggedOnly()`.
The officer endpoint calls the former; the operator endpoint calls the latter.

---

## API Reference

### Auth
| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/login` | Returns JWT token |

### Operator (requires `ROLE_OPERATOR`)
| Method | Path | Description |
|---|---|---|
| POST | `/api/operator/applications` | Submit new application |
| GET | `/api/operator/applications` | List own applications |
| GET | `/api/operator/applications/{id}` | Get application detail (operator view) |
| PATCH | `/api/operator/applications/{id}/resubmit` | Resubmit with updated fields |
| GET | `/api/operator/applications/{id}/checklist/flagged` | Get flagged checklist items only |
| POST | `/api/operator/checklist/{itemId}/respond` | Respond to a flagged item |

### Officer (requires `ROLE_OFFICER`)
| Method | Path | Description |
|---|---|---|
| GET | `/api/officer/applications` | List all applications |
| GET | `/api/officer/applications/{id}` | Full application detail |
| POST | `/api/officer/applications/{id}/feedback` | Set status + add comments |
| GET | `/api/officer/applications/{id}/checklist` | Full site checklist |
| PATCH | `/api/officer/applications/{id}/checklist/draft` | Save checklist draft |
| POST | `/api/officer/applications/{id}/checklist/submit` | Submit final checklist |

### Notifications (requires auth)
| Method | Path | Description |
|---|---|---|
| GET | `/api/notifications/me` | List notifications for current user |
| PATCH | `/api/notifications/me/read-all` | Mark current user's notifications as read |

---

## Running Tests

```bash
cd backend
./mvnw test
```

Current test: `StatusTransitionServiceTest` — covers legal/illegal transitions and
role-gating rules. This is the most critical business logic unit to test.

---

## AI Usage

### Tools Used
- **Claude (Anthropic)** — primary coding assistant throughout

### How Claude Was Used

**Prompting approach:**

Rather than pasting the full spec and asking for complete code, I broke the work into
discrete engineering layers and prompted with specific context each time.

Example prompts used:

> "Here is my `ApplicationStatus` enum with officer and operator labels. Generate the
> `StatusTransitionService` with a static transition map. Role-gate resubmission
> transitions to OPERATOR only. Throw a custom `InvalidStatusTransitionException`."

> "Here are my entities and the spec's status mapping table. Generate
> `ApplicationDetailResponse` with two static factory methods — `forOfficer` and
> `forOperator`. The operator version must set `internalStatus = null` and use the
> `getOperatorLabel()` method. Never expose PENDING_APPROVAL to the operator."

> "Generate a `ChecklistServiceImpl` where `getFlaggedItemsForOperator` queries only
> items with status `NEEDS_CLARIFICATION`, using the `flaggedOnly()` DTO factory. The
> full checklist is officer-only. Include the draft-save endpoint."

**Validation and corrections:**

- The initial `StatusTransitionService` Claude generated used `EnumSet` and allowed
  bidirectional transitions. I corrected this to a one-directional `Map` and added
  explicit role gating.
- The first draft of `ApplicationDetailResponse` used `@JsonIgnore` annotations rather
  than factory methods. I rewrote this to use the factory pattern so the filtering
  logic lives in one place, not scattered across Jackson annotations.
- Claude's initial `SecurityConfig` used the deprecated `WebSecurityConfigurerAdapter`.
  I updated it to the Spring Boot 3 lambda DSL pattern.
- The `DataSeeder` initially used `@PostConstruct` — changed to `CommandLineRunner` to
  ensure JPA is fully initialised before seed runs.

**Where Claude was less helpful:**

- The frontend CSS was written manually. Claude's initial suggestion was generic and
  did not match the government-utility aesthetic intended.
- Claude initially placed business logic in controllers. I refactored all non-trivial
  logic into the service layer.

**Honest assessment:**

Claude significantly accelerated the boilerplate (entities, repositories, DTOs,
security filter). The business logic — status machine rules, role isolation, the
operator vs officer DTO split — required careful human review. The AI output was
treated as a first draft that needed validation, not a finished product.

---

## Known Gaps

- No actual file persistence (upload API returns 200 but files are not stored)
- No email notifications (logged server-side only)
- No pagination on list endpoints
- No admin UI for user provisioning
- AI verification is simulated on the frontend only
