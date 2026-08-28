# LAMWorkOrder

LAMWorkOrder is a shared work-order system built with Python and Kotlin.

The Android client and API include bearer-token login, editable user profiles, server-enforced role permissions, role-scoped work-order updates, and multiple photo/video attachments. Existing SQLite work orders are upgraded in place and are not deleted.

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
python -m uvicorn lamworkorder.main:app --host 0.0.0.0 --port 5081 --reload
```

Keep this PowerShell window open. The backend stops if you close it.

Open these addresses in Chrome:

- Web dashboard: <http://localhost:5081>
- Interactive API documentation: <http://localhost:5081/docs>
- API schema: <http://localhost:5081/openapi.json>

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

The current Version 1 Android client is configured in `android/app/build.gradle.kts` to call:

```text
http://50.190.210.154:5081/
```

Keep the final slash when changing this address for a development environment.

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
buildConfigField("String", "API_BASE_URL", "\"http://50.190.210.154:5081/\"")
```

to your computer's address, for example:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.50:5081/\"")
```

Keep the final slash. Then click **Sync Project with Gradle Files**.

On the phone:

1. Enable Developer options.
2. Enable USB debugging.
3. Connect the phone with a USB data cable.
4. Approve the debugging request on the phone.
5. Select the phone in Android Studio.
6. Press **Run**.

The backend must use `--host 0.0.0.0`, as shown above. Allow Python or port `5081` through Windows Firewall when prompted.

To test the network before running the app, open this address in the phone's browser, replacing the example IP:

```text
http://192.168.1.50:5081/docs
```

If the API documentation opens on the phone, the connection is working.

## 8. Backend versions and addresses

The reserved backend ports are:

| Backend version | Port |
| --- | ---: |
| Legacy | `5080` |
| Version 1 (current) | `5081` |
| Version 2 | `5082` |

| Client | Backend address |
| --- | --- |
| Web browser on the Windows computer | `http://localhost:5081` |
| Windows desktop app | `http://50.190.210.154:5081` |
| Android Version 1 client | `http://50.190.210.154:5081/` |
| Local development override | `http://YOUR-PC-IP:5081/` |

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
3. Open <http://localhost:5081> for the web dashboard.
4. Start the Windows desktop app in a second PowerShell window, if needed.
5. Open the `android` folder in Android Studio and run the Android app.

Backend:

```powershell
cd C:\JC_SW\LAMWorkOrder
.\.venv\Scripts\Activate.ps1
python -m uvicorn lamworkorder.main:app --host 0.0.0.0 --port 5081 --reload
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

### Port 5081 is already in use

An older backend may still be running. Close its PowerShell window, or find the process:

```powershell
netstat -ano | findstr :5081
```

### Android shows a network error

Check all of these:

- The backend PowerShell window is still running.
- Version 1 uses `http://50.190.210.154:5081/` unless you intentionally set a local development override.
- A physical phone using a local override uses the computer's current IPv4 address.
- The phone and computer are on the same network.
- Windows Firewall allows port `5081`.
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
