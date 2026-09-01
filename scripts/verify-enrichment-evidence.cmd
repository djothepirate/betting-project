@echo off
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0verify-enrichment-evidence.ps1" %*
exit /b %ERRORLEVEL%
