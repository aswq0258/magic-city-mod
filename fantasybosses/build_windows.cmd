@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build_windows.ps1"
set "RESULT=%ERRORLEVEL%"
echo.
if not "%RESULT%"=="0" echo Build failed. See build-output.log or console above.
pause
exit /b %RESULT%
