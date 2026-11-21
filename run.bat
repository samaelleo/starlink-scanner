@echo off
title Starlink Scanner - Windows Edition
cd /d "%~dp0"

echo [Starlink Scanner] Starting application...
python main.py
if %errorlevel% neq 0 (
    echo.
    echo Application exited with an error. Press any key to close.
    pause >nul
)
