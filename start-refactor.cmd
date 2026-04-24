@echo off
echo Starting refactored stack...

start "Backend (Spring Boot)" cmd /k "cd /d %~dp0backend && mvnw.cmd spring-boot:run"
timeout /t 4 /nobreak >nul
start "Frontend React (Vite)" cmd /k "cd /d %~dp0frontend-react && npm install && npm run dev"

echo.
echo Open http://localhost:5173
pause
