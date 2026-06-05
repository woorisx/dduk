@echo off
setlocal

powershell -ExecutionPolicy Bypass -File "%~dp0ai-all-start-runner.ps1"
if errorlevel 1 (
  echo.
  echo ai-all-start failed. Press any key to close.
  pause >nul
)

endlocal
