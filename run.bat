@echo off
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven not found. Install Maven, or open this folder in IntelliJ and run JobPilotApplication.
  echo See README.md for details.
  pause
  exit /b 1
)
call mvn spring-boot:run
pause
