@echo off
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0collect-enrichment.ps1" %*
exit /b %ERRORLEVEL%
