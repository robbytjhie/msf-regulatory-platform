@echo off
echo =============================================
echo  Regulatory Platform - Dev Startup
echo  Backend  -> http://localhost:8080
echo  Frontend -> http://localhost:5173
echo =============================================
echo.

REM Start backend in a new window via helper script
start "Backend (Spring Boot)" cmd /k ""%~dp0start-backend.cmd""

REM Wait a few seconds for backend to start
timeout /t 5 /nobreak >nul

REM Start frontend server in a new window via helper script
start "Frontend (Vite 5173)" cmd /k ""%~dp0start-frontend.cmd""

echo.
echo Both servers starting in separate windows.
echo.
echo  Login page: http://localhost:5173/login
echo  H2 Console: http://localhost:8080/h2-console
echo.
echo Demo credentials:
echo   Officer:  officer@gov.sg  / password
echo   Operator: operator@acme.sg / password
echo.
pause
