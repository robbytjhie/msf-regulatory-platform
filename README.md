# Regulatory & Licensing Platform

A Spring Boot + React system for managing the end-to-end licensing application
lifecycle between government officers and operators.

---

## Quick Start

This repository includes:
- `backend` (Spring Boot + H2 in-memory)
- `frontend-react` (Vite + React)
- `auth-node` (Fastify helper service, optional for local run)

Open these docs for full bootstrap:
- Windows desktop setup: `SETUP.md`
- Ubuntu server/WSL setup: `README.md` section "Run on Ubuntu"

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

Open `http://localhost:5173/login`.

Demo accounts:
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

**Full step-by-step guide** (first-time setup, pull/rebuild, pods, logs, troubleshooting, cluster reset): **`docs/MINIKUBE.md`** — includes **separate copy-paste tracks** for **Ubuntu / WSL (bash)** and **Windows PowerShell**.

Quick path (Windows PowerShell):

```powershell
cd infra/k8s
./deploy-minikube.ps1
minikube service frontend-react -n msf --url
```

On **Linux / WSL**, use `eval "$(minikube -p minikube docker-env)"` before `docker build`, then `kubectl apply -f infra/k8s/msf.yaml` — see **`docs/MINIKUBE.md`** for the complete bash flow.

---

## Security Scans

- SonarCloud: configured via `sonar-project.properties` and GitHub Action.
- Trivy: GitHub Action step `Trivy filesystem scan`.
- Set `SONAR_TOKEN` in repository secrets.

---

## IM8 / GovTech Security Baseline Implemented

The following controls were added to align with an IM8-style baseline for dev/staging:

- Login rate limiting on `/api/auth/login` (configurable max attempts and window)
- Security headers in backend responses:
  - HSTS
  - CSP (`default-src 'self'`)
  - `X-Content-Type-Options`
  - referrer policy
- Stricter CORS by environment variable:
  - `APP_CORS_ALLOWED_ORIGIN_PATTERNS`
- Kubernetes `securityContext` hardening in `infra/k8s/msf.yaml`:
  - `seccompProfile: RuntimeDefault`
  - `allowPrivilegeEscalation: false`
  - Linux capability drop (`ALL`)
- Persistent API audit log table (`api_audit_logs`) for API access events:
  - method, path, status, duration, client IP, origin, user identity (when available), user-agent

Tunable security environment variables:

- `APP_LOGIN_RATE_LIMIT_MAX_ATTEMPTS` (default `5`)
- `APP_LOGIN_RATE_LIMIT_WINDOW_SECONDS` (default `300`)
- `APP_CORS_ALLOWED_ORIGIN_PATTERNS` (comma-separated allowed origins/patterns)

---

## Authentication

Authentication uses **email/password login + JWT bearer token**.

- Login endpoint: `POST /api/auth/login`
- Backend issues signed JWT (HS256) via `JwtService`
- Frontend stores token in `localStorage` and sends `Authorization: Bearer <token>`
- Security enforcement is done by `JwtAuthFilter` + `SecurityConfig`
- Roles:
  - `OFFICER`
  - `OPERATOR`
  - `ADMIN`

Authorization is role-based at endpoint level:
- `/api/officer/**` -> `ROLE_OFFICER`
- `/api/operator/**` -> `ROLE_OPERATOR`
- `/api/notifications/**` -> authenticated officer/operator/admin

JWT secret source:
- `app.jwt.secret` from environment variable `APP_JWT_SECRET`
- dev fallback is set in `application.properties` for local testing

### Optional document AI (free-tier APIs)

Document verification can call an external model using **metadata only** (filename, MIME type, size, category). File contents are not sent until upload storage is implemented.

Priority: **Groq** first, then **Hugging Face Inference** if Groq returns no usable result or is not configured. If neither key is set, the backend keeps the **built-in random simulation**.

| Variable | Purpose |
|----------|---------|
| `GROQ_API_KEY` | [Groq](https://console.groq.com/) OpenAI-compatible chat API (free tier limits apply) |
| `GROQ_BASE_URL` | Optional API host override (default `https://api.groq.com`; useful for tests or proxies) |
| `GROQ_MODEL` | Optional override (default `llama-3.1-8b-instant`) |
| `HF_API_TOKEN` | [Hugging Face](https://huggingface.co/settings/tokens) inference token |
| `HF_INFERENCE_BASE_URL` | Optional inference host (default `https://api-inference.huggingface.co`) |
| `HF_MODEL` | Optional model id (default `HuggingFaceTB/SmolLM2-360M-Instruct`) |

Do not commit API keys; use environment variables or your secret manager.

---

## Run on Ubuntu (From Scratch)

These steps work for Ubuntu server or WSL Ubuntu.

### 1) Prerequisites

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk maven nodejs npm docker.io docker-compose-plugin git
```

Optional: ensure Java 21 is default

```bash
sudo update-alternatives --config java
sudo update-alternatives --config javac
java -version
mvn -version
```

### 2) Clone and run (dev mode)

```bash
git clone https://github.com/robbytjhie/msf-regulatory-platform.git
cd msf-regulatory-platform
```

Terminal 1:

```bash
cd backend
./mvnw spring-boot:run
```

Terminal 2:

```bash
cd frontend-react
npm install
npm run dev -- --host
```

Open `http://localhost:5173/login`.

### 3) Run with Docker Compose

```bash
cd msf-regulatory-platform
sudo service docker start
docker compose up --build
```

Open `http://localhost:3000`.

### 4) Run with Minikube (Ubuntu)

```bash
minikube start --driver=docker
kubectl config use-context minikube
eval "$(minikube -p minikube docker-env)"
docker build -t msf/backend:latest ./backend
docker build -t msf/auth-node:latest ./auth-node
docker build -t msf/frontend-react:latest ./frontend-react
kubectl apply -f infra/k8s/msf.yaml
kubectl -n msf get pods
minikube service frontend-react -n msf --url
```

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
├── frontend-react/
│   ├── src/pages/           React pages (login, dashboards, details, checklist)
│   ├── src/apiClient.js     Centralised fetch client
│   └── src/statusUtils.js   Shared status + workflow helper utilities
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
