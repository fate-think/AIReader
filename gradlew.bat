@echo off
setlocal
set APP_HOME=%~dp0
powershell -NoProfile -ExecutionPolicy Bypass -File "%APP_HOME%gradle\wrapper\bootstrap.ps1" %*
exit /b %ERRORLEVEL%
