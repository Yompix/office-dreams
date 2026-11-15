@echo off
rem Unity Boost RU - installer. Runs install.ps1 with built-in Windows PowerShell.
powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -File "%~dp0install.ps1"
if errorlevel 1 pause
