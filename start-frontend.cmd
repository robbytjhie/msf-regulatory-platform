@echo off
echo Starting React frontend on http://localhost:5173 ...
cd /d "%~dp0frontend-react"
where npm >nul 2>nul && (
    npm install
    npm run dev
    goto :done
)
echo ERROR: npm not found. Install Node.js (includes npm) to run the frontend.
pause
exit /b 1
:done
echo Frontend running at http://localhost:5173/login
