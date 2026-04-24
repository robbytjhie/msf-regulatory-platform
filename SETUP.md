# Regulatory Platform — Setup Guide (Windows Desktop)

## Prerequisites
- **Java 21 recommended** (`java -version`)
- **Maven** (bundled via `mvnw.cmd`) 
- **Node.js + npm** (for React frontend)
- **Docker Desktop** (for Docker/Minikube flows)
- **Minikube + kubectl** (optional, for Kubernetes local deployment)

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

Demo accounts:

| Role     | Email                 | Password   |
|----------|-----------------------|------------|
| Officer  | officer@gov.sg        | password   |
| Operator | operator@acme.sg      | password   |
| Operator | operator2@beta.sg     | password   |

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

## Bugs Fixed

| # | File | Issue | Fix |
|---|------|-------|-----|
| 1 | `backend/mvnw` | File corrupted — contained "Host not in allowlist" instead of shell script | Replaced with proper Maven Wrapper shell script |
| 2 | Root | No startup scripts for Windows | Added `start-all.cmd`, `start-backend.cmd`, `start-frontend.cmd` |
| 3 | Root | Frontend startup was legacy static hosting | Updated scripts to use React Vite dev server on port 5173 |
