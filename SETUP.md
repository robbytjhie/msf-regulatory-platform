# Regulatory Platform — Setup Guide (Windows 11 PowerShell)

## Prerequisites
- **Java 17+** (`java -version`)
- **Maven** (bundled via `mvnw.cmd`) 
- **Python 3** or **Node.js** (for frontend server)

---

## Quick Start (Recommended)

Double-click **`start-all.cmd`** — opens two terminal windows:
- **Backend** on `http://localhost:8080`
- **Frontend** on `http://localhost:3000`

Then open: **http://localhost:3000/pages/login.html**

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
cd frontend
python -m http.server 3000
# OR: npx serve -p 3000 .
```

Open: **http://localhost:3000/pages/login.html**

---

## Demo Credentials

| Role     | Email                 | Password   |
|----------|-----------------------|------------|
| Officer  | officer@gov.sg        | password   |
| Operator | operator@acme.sg      | password   |
| Operator | operator2@beta.sg     | password   |

---

## Useful URLs

| URL                                    | Description            |
|----------------------------------------|------------------------|
| http://localhost:3000/pages/login.html | Login page             |
| http://localhost:8080/h2-console       | H2 in-memory DB viewer |
| http://localhost:8080/api/auth/login   | Auth API endpoint      |

H2 Console settings:
- JDBC URL: `jdbc:h2:mem:regulatorydb`
- Username: `sa` / Password: *(blank)*

---

## Bugs Fixed

| # | File | Issue | Fix |
|---|------|-------|-----|
| 1 | `backend/mvnw` | File corrupted — contained "Host not in allowlist" instead of shell script | Replaced with proper Maven Wrapper shell script |
| 2 | Root | No startup scripts for Windows | Added `start-all.cmd`, `start-backend.cmd`, `start-frontend.cmd` |
| 3 | Root | Frontend had no HTTP server config for port 3000 | `start-all.cmd` serves frontend via Python `http.server` on port 3000 |
