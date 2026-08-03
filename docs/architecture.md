# Architecture

```text
Android (Kotlin/Compose) ─┐
Web (HTML/CSS/JS) ───────┼─> FastAPI -> repository -> SQLite
Windows (PySide6) ───────┘
```

FastAPI owns validation, workflow rules, OpenAPI, and persistence. SQLAlchemy keeps storage
replaceable while SQLite makes local setup immediate. Native clients share the JSON contract.
