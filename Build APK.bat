@echo off
rem Double-click to turn src\ps1_bedroom.html into releases\PS1-Bedroom-vX.Y.apk
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0android\build.ps1"
echo.
pause
