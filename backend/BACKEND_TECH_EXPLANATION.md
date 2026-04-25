# Backend Tech and Class/Module Explanation

This document explains why the backend uses the current technologies and what the key classes/modules are responsible for.

## Why This Backend Stack

- **Spring Boot (Web + Validation + Security + Data JPA)**  
  Chosen for fast API delivery with strong defaults, clear layering, and mature security/database support.

- **Spring Security + JWT**  
  Enables stateless role-based API authorization (`OFFICER`, `OPERATOR`) and simple frontend token usage.

- **JPA/Hibernate**  
  Maps domain entities (`Application`, `Document`, `ChecklistItem`, etc.) to relational storage with repository abstraction.

- **H2 for local/demo**  
  Lightweight embedded DB that makes demo/testing repeatable and easy to bootstrap.

- **Async document verification (`@Async`)**  
  Simulates non-blocking AI verification so submission APIs remain responsive while progress is polled by UI.

- **Global exception mapping**  
  Converts technical errors into stable API response shape and user-friendly frontend messages.

## Key Packages and Responsibilities

- **`controller`**  
  HTTP entry points, request/response contracts, role-protected routes.
  - `AuthController`: login + rate-limit guard + JWT issuance.
  - `OperatorController`: submit/resubmit/view operator-owned applications.
  - `OfficerController`: review transitions, checklist access, document download.
  - `ChecklistController`: checklist draft/submit/respond flows.

- **`service` / `service.impl`**  
  Business logic and workflow orchestration.
  - `ApplicationServiceImpl`: UC1/UC2 state transitions, comments, notifications.
  - `ChecklistServiceImpl`: UC3 checklist updates, visibility/editability rules, resulting status transitions.
  - `StatusTransitionService`: central transition matrix and role-gating.
  - `DocumentVerificationService`: async AI lifecycle (`PENDING -> PROCESSING -> FINAL`).
  - `ExternalDocumentAiAnalysisService`: external provider call chain + deterministic local fallback.
  - `LocalDocumentStorageService`: local placeholder file write/read for document download endpoints.

- **`security` + `config`**  
  Authentication/authorization and infrastructure configuration.
  - `SecurityConfig`: route policy, stateless mode, JWT filter registration, CORS behavior.
  - `JwtAuthFilter`: per-request token extraction and `SecurityContext` setup.
  - `JwtService`: token generation/validation utilities.
  - `DataSeeder`: deterministic demo reset/seed data for repeatable walkthroughs.

- **`entity` / `repository`**  
  Domain model + persistence contracts.
  - Entities represent workflow objects and relationships.
  - Repositories encapsulate query patterns (with some eager-fetch methods for API response safety).

- **`dto.request` / `dto.response`**  
  API boundary models that isolate transport shape from entity internals.

## Workflow Design Notes (Why)

- **Centralized transition matrix** avoids mismatch between pages/endpoints and prevents invalid jumps.
- **Role-gated destinations** enforce that only operators can submit resubmissions, while officers control review transitions.
- **Checklist visibility vs editability split** supports post-stage audit viewing while still enforcing ownership of updates.
- **Deterministic AI fallback** guarantees every document gets an actionable reason even when external AI is unavailable.
- **Notifications + status history** preserve traceability and allow both roles to see lifecycle progress.

## Complex Methods Worth Reading First

- `ApplicationServiceImpl.submit()`  
  Validates required documents, creates application/documents, routes to AI/manual flow, records history.

- `ApplicationServiceImpl.submitOfficerFeedback()`  
  Validates transition, attaches targeted comments, handles checklist seeding trigger, emits notifications.

- `ChecklistServiceImpl.submitChecklist()`  
  Applies checklist values, blocks pending rows, computes next status, writes history + notifications.

- `StatusTransitionService.validate()`  
  Single source of truth for transition legality and role ownership rules.

- `ExternalDocumentAiAnalysisService.analyze()`  
  Provider chain with parsing + deterministic local heuristic fallback.
