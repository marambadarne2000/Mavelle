@echo off
cd /d "%~dp0"
where pwsh >nul 2>&1
if errorlevel 1 (
  echo PowerShell 7 is required. See README.md and docs/FRESH_SETUP.md.
  pause
  exit /b 1
)
pwsh -NoProfile -File "%~dp0scripts\Start-Local.ps1" -Restart
if errorlevel 1 (
  pause
  exit /b 1
)
start "" "http://127.0.0.1:5173/#home"
