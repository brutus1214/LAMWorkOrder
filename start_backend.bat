@echo off
setlocal

cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" (
    echo Creating Python virtual environment...
    py -3 -m venv .venv
    if errorlevel 1 py -m venv .venv
    if errorlevel 1 (
        echo Failed to create .venv. Install Python 3.11 or newer, then run this again.
        pause
        exit /b 1
    )
)

set "PYTHON=.venv\Scripts\python.exe"

echo Installing latest local project code...
"%PYTHON%" -m pip install -e ".[dev,desktop]"
if errorlevel 1 (
    echo Failed to install project dependencies.
    pause
    exit /b 1
)

set "PORT_PID="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":5081 .*LISTENING"') do set "PORT_PID=%%P"
if defined PORT_PID (
    echo.
    echo Port 5081 is already in use by process %PORT_PID%.
    choice /M "Stop that process and restart the backend"
    if errorlevel 2 (
        echo Backend was not restarted.
        pause
        exit /b 1
    )
    taskkill /PID %PORT_PID% /T /F
    if errorlevel 1 (
        echo Failed to stop process %PORT_PID%. Close the old backend window, then run this again.
        pause
        exit /b 1
    )
    timeout /t 2 /nobreak >nul
    set "PORT_PID="
    for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":5081 .*LISTENING"') do set "PORT_PID=%%P"
    if defined PORT_PID (
        echo Port 5081 is still in use. Close the old backend window, then run this again.
        pause
        exit /b 1
    )
)

echo.
echo Starting LAMWorkOrder backend at http://localhost:5081
echo Keep this window open while using the app.
echo.

"%PYTHON%" -m uvicorn lamworkorder.main:app --host 0.0.0.0 --port 5081 --reload

pause
