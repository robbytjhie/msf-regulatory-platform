@echo off
echo Starting backend on http://localhost:8080 ...
cd /d "%~dp0backend"
call mvnw.cmd spring-boot:run
