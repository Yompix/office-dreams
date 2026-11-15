@echo off
rem Unity Boost RU - uninstall. Removes only files installed by Unity Boost.
powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -File "%~dp0install.ps1" -Uninstall
if errorlevel 1 pause
