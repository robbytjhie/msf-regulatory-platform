@echo off
echo Starting frontend on http://localhost:3000 ...
cd /d "%~dp0frontend"
where python >nul 2>nul && (
    python -m http.server 3000 & goto :done
)
where python3 >nul 2>nul && (
    python3 -m http.server 3000 & goto :done
)
where npx >nul 2>nul && (
    npx serve -p 3000 . & goto :done
)
echo ERROR: Python or Node.js not found. Install Python or Node.js to serve the frontend.
pause
exit /b 1
:done
echo Frontend running at http://localhost:3000/pages/login.html
