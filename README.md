# LAMWorkOrder

LAMWorkOrder is a shared work-order system built with Python and Kotlin.

The Android client and API include bearer-token login, editable user profiles, server-enforced role permissions, full work-order editing for managers/admins, technician status updates, and multiple photo/video attachments. Existing SQLite work orders are upgraded in place and are not deleted.

| Folder | Application |
| --- | --- |
| `backend/` | FastAPI backend, SQLite database, and web dashboard |
| `desktop/` | PySide6 Windows desktop application |
| `android/` | Native Kotlin Android application using Jetpack Compose |
| `tests/` | Python backend tests |

The web dashboard, Windows app, and Android app all connect to the same Python backend.

## 1. Requirements

Install the following on Windows:

- Python 3.11 or newer
- Git
- Android Studio
- Android SDK 35
- Java/JDK 17 (Android Studio normally includes this)

Check Python:

```powershell
py --version
```

## 2. Open the correct project branch

Open PowerShell:

```powershell
cd C:\JC_SW\LAMWorkOrder
git switch agent/kotlin-python-restart
git pull
```

## 3. First-time Python setup

Run these commands from the main project folder, not from the `backend` folder:

```powershell
cd C:\JC_SW\LAMWorkOrder
py -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -e ".[dev,desktop]"
```

If PowerShell blocks `Activate.ps1`, run this once in that PowerShell window:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
```

You only create the virtual environment and install the packages the first time, or when dependencies change.

## 4. Start the backend and web dashboard

Open PowerShell and run:

```powershell
cd C:\JC_SW\LAMWorkOrder
.\.venv\Scripts\Activate.ps1
python -m uvicorn lamworkorder.main:app --host 0.0.0.0 --port 5080 --reload
```

Keep this PowerShell window open. The backend stops if you close it.

Open these addresses in Chrome:

- Web dashboard: <http://localhost:5080>
- Interactive API documentation: <http://localhost:5080/docs>
- API schema: <http://localhost:5080/openapi.json>

The SQLite database file `lamworkorder.db` is created automatically in the project folder.

## 5. Start the Windows desktop application

The backend must be running first.

Open a second PowerShell window:

```powershell
cd C:\JC_SW\LAMWorkOrder
.\.venv\Scripts\Activate.ps1
python -m lamworkorder_desktop
```

You can also use the installed command:

```powershell
lamworkorder-desktop
```

## 6. Open the Android app in Android Studio

1. Start Android Studio.
2. Select **Open**.
3. Open only this folder:

   ```text
   C:\JC_SW\LAMWorkOrder\android
   ```

4. Wait for Gradle Sync to finish.
5. Accept any request to install Android SDK 35 or other missing components.
6. Open **Tools > Device Manager**.
7. Create and start an Android emulator if one is not already available.
8. Select the emulator at the top of Android Studio.
9. Press the green **Run** button.

The Android emulator is already configured to call:

```text
http://10.0.2.2:5080/
```

Do not change it to `localhost`. Inside an emulator, `localhost` means the emulator itself. `10.0.2.2` connects to the Windows computer.

## 7. Run on a physical Android phone or Z Fold

The phone and computer must be connected to the same Wi-Fi network.

Find the computer's local IPv4 address:

```powershell
ipconfig
```

Look under the active Wi-Fi or Ethernet adapter. An example address is `192.168.1.50`.

Open:

```text
android\app\build.gradle.kts
```

Change:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:5080/\"")
```

to your computer's address, for example:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.50:5080/\"")
```

Keep the final slash. Then click **Sync Project with Gradle Files**.

On the phone:

1. Enable Developer options.
2. Enable USB debugging.
3. Connect the phone with a USB data cable.
4. Approve the debugging request on the phone.
5. Select the phone in Android Studio.
6. Press **Run**.

The backend must use `--host 0.0.0.0`, as shown above. Allow Python or port `5080` through Windows Firewall when prompted.

To test the network before running the app, open this address in the phone's browser, replacing the example IP:

```text
http://192.168.1.50:5080/docs
```

If the API documentation opens on the phone, the connection is working.

## 8. Backend addresses

| Client | Backend address |
| --- | --- |
| Web browser on the Windows computer | `http://localhost:5080` |
| Windows desktop app | `http://localhost:5080` |
| Android Studio emulator | `http://10.0.2.2:5080/` |
| Physical Android phone | `http://YOUR-PC-IP:5080/` |

## 9. Run tests

Python tests and code checks:

```powershell
cd C:\JC_SW\LAMWorkOrder
.\.venv\Scripts\Activate.ps1
python -m pytest
python -m ruff check backend desktop tests
```

Android unit tests:

```powershell
cd C:\JC_SW\LAMWorkOrder\android
.\gradlew.bat test
```

## 10. Normal daily startup

Each time you work on the project:

1. Start the backend in PowerShell.
2. Keep that window open.
3. Open <http://localhost:5080> for the web dashboard.
4. Start the Windows desktop app in a second PowerShell window, if needed.
5. Open the `android` folder in Android Studio and run the Android app.

Backend:

```powershell
cd C:\JC_SW\LAMWorkOrder
.\.venv\Scripts\Activate.ps1
python -m uvicorn lamworkorder.main:app --host 0.0.0.0 --port 5080 --reload
```

Windows app:

```powershell
cd C:\JC_SW\LAMWorkOrder
.\.venv\Scripts\Activate.ps1
python -m lamworkorder_desktop
```

## 11. Common problems

### `No module named uvicorn` or `No module named lamworkorder`

Activate the virtual environment and reinstall the project:

```powershell
cd C:\JC_SW\LAMWorkOrder
.\.venv\Scripts\Activate.ps1
pip install -e ".[dev,desktop]"
```

### Port 5080 is already in use

An older backend may still be running. Close its PowerShell window, or find the process:

```powershell
netstat -ano | findstr :5080
```

### Android shows a network error

Check all of these:

- The backend PowerShell window is still running.
- The emulator uses `http://10.0.2.2:5080/`.
- A physical phone uses the computer's current IPv4 address.
- The phone and computer are on the same network.
- Windows Firewall allows port `5080`.
- The URL ends with `/`.

### Gradle Sync fails

In Android Studio, verify the project JDK:

1. Open **File > Settings > Build, Execution, Deployment > Build Tools > Gradle**.
2. Set **Gradle JDK** to Android Studio's embedded JDK 17.
3. Select **File > Sync Project with Gradle Files**.

### Reset local sample data

Stop the backend first. Rename `lamworkorder.db` as a backup, then start the backend again. A new database will be created automatically.

Do not delete the database if it contains work orders you need.

## Current development status

This branch is the Kotlin/Python replacement foundation. The previous .NET implementation remains available on the `feature/v1-foundation` branch.
