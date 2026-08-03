# LAMWorkOrder

LAMWorkOrder is an API-first work-order system rebuilt with Kotlin and Python.

| Path | Application |
| --- | --- |
| `backend/` | FastAPI API, SQLite persistence, and responsive web dashboard |
| `desktop/` | PySide6 Windows desktop client |
| `android/` | Native Kotlin Android client using Jetpack Compose |

## Quick start

Requires Python 3.11+. Create a virtual environment, then run:

```powershell
pip install -e ".[dev,desktop]"
uvicorn lamworkorder.main:app --reload --port 5080
```

Open <http://localhost:5080>. Launch Windows with `python -m lamworkorder_desktop`.
Android emulators use `http://10.0.2.2:5080`.

## Tests

```powershell
python -m pytest
python -m ruff check backend desktop tests
cd android
.\gradlew.bat test
```

The previous .NET implementation remains unchanged on `feature/v1-foundation`.
