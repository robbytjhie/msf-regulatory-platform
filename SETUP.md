# Regulatory Platform — Setup Guide (Windows Desktop)

## Prerequisites
- **Java 21** (`java -version`)
- **Maven Wrapper 3.9.6** (bundled via `mvnw.cmd`)
- **Node.js + npm** (for React frontend; supports current Vite 8 toolchain)
- **Docker Desktop** (for Docker/Minikube flows)
- **Minikube + kubectl** (optional, for Kubernetes local deployment)

Project-pinned framework/runtime versions from source:
- Backend: Spring Boot `3.4.5`, Java `21`, JJWT `0.12.5`
- Frontend: React `19.2.5`, React Router DOM `7.14.2`, Vite `8.0.10`, Vitest `4.1.5`, Playwright `1.59.1`
- Auth helper service: Fastify `5.8.5`, `@fastify/cors` `11.2.0`, `dotenv` `17.4.2`

---

## Quick Start (Recommended)

Double-click **`start-all.cmd`** — opens two terminal windows:
- **Backend** on `http://localhost:8080`
- **Frontend** on `http://localhost:5173`

Then open: **http://localhost:5173/login**

---

## Manual Start (PowerShell)

### 1. Start the Backend
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```
Backend ready when you see: `Started PlatformApplication`

### 2. Start the Frontend (in a second terminal)
```powershell
cd frontend-react
npm install
npm run dev
```

Open: **http://localhost:5173/login**

---

## Authentication Model (Current)

- Login is **email/password** (`POST /api/auth/login`)
- Backend returns a **JWT bearer token**
- Frontend stores token and sends `Authorization: Bearer <token>` for protected APIs
- Role-based access:
  - `OFFICER`
  - `OPERATOR`
  - `ADMIN`
- Login endpoint has rate limiting (configurable by env vars)

Demo accounts:

| Role     | Email                 | Password   |
|----------|-----------------------|------------|
| Officer  | officer@gov.sg        | password   |
| Operator | operator@acme.sg      | password   |
| Operator | operator2@beta.sg     | password   |

---

## Security Environment Variables (Optional)

You can override defaults via environment variables before starting backend:

- `APP_CORS_ALLOWED_ORIGIN_PATTERNS`  
  Example: `https://your-frontend.gov.sg,https://staging.example.sg`
- `APP_LOGIN_RATE_LIMIT_MAX_ATTEMPTS`  
  Example: `5`
- `APP_LOGIN_RATE_LIMIT_WINDOW_SECONDS`  
  Example: `300`

PowerShell example:

```powershell
$env:APP_CORS_ALLOWED_ORIGIN_PATTERNS="http://localhost:5173,http://127.0.0.1:5173"
$env:APP_LOGIN_RATE_LIMIT_MAX_ATTEMPTS="5"
$env:APP_LOGIN_RATE_LIMIT_WINDOW_SECONDS="300"
```

---

## Useful URLs

| URL                             | Description            |
|---------------------------------|------------------------|
| http://localhost:5173/login     | Login page             |
| http://localhost:8080/h2-console| H2 in-memory DB viewer |
| http://localhost:8080/api/auth/login | Auth API endpoint |

H2 Console settings:
- JDBC URL: `jdbc:h2:mem:regulatorydb`
- Username: `sa` / Password: *(blank)*

---

## Docker Run (Windows)

```powershell
cd d:\MSF
docker compose up --build
```

Open: **http://localhost:3000**

---

## Minikube Run (Windows + Docker Desktop)

1. Start Docker Desktop and wait until engine is running.
2. Ensure local context is Minikube (not cloud cluster).

```powershell
cd d:\MSF
minikube start --driver=docker
kubectl config use-context minikube
minikube -p minikube docker-env --shell powershell | Invoke-Expression
docker build -t msf/backend:latest .\backend
docker build -t msf/auth-node:latest .\auth-node
docker build -t msf/frontend-react:latest .\frontend-react
kubectl apply -f .\infra\k8s\msf.yaml
kubectl -n msf rollout status deploy/backend
kubectl -n msf rollout status deploy/auth-node
kubectl -n msf rollout status deploy/frontend-react
minikube service frontend-react -n msf --url
```

If you see `ImagePullBackOff`, rebuild images after running `minikube docker-env`.

---