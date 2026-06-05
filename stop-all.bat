@echo off
setlocal

powershell -ExecutionPolicy Bypass -File "%~dp0stop-all-runner.ps1"
if errorlevel 1 (
  echo.
  echo stop-all failed. Press any key to close.
  pause >nul
)

endlocal
