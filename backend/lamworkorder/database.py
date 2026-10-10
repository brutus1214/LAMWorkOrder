from collections.abc import Iterator

from sqlalchemy import create_engine, inspect
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from .config import get_settings


class Base(DeclarativeBase):
    pass


settings = get_settings()
connect_args = {"check_same_thread": False} if settings.database_url.startswith("sqlite") else {}
engine = create_engine(settings.database_url, connect_args=connect_args)
SessionLocal = sessionmaker(bind=engine, expire_on_commit=False)


def ensure_schema() -> None:
    """Apply small, backwards-compatible upgrades for existing local databases."""
    inspector = inspect(engine)
    tables = inspector.get_table_names()
    if "users" in tables:
        user_columns = {column["name"] for column in inspector.get_columns("users")}
        with engine.begin() as connection:
            if "store_number" not in user_columns:
                connection.exec_driver_sql(
                    "ALTER TABLE users ADD COLUMN store_number INTEGER NOT NULL DEFAULT 1"
                )
            if "phone_number" not in user_columns:
                connection.exec_driver_sql(
                    "ALTER TABLE users ADD COLUMN phone_number VARCHAR(30)"
                )

    if "work_orders" not in tables:
        return

    columns = {column["name"] for column in inspector.get_columns("work_orders")}
    if "store_number" not in columns:
        with engine.begin() as connection:
            connection.exec_driver_sql(
                "ALTER TABLE work_orders ADD COLUMN store_number INTEGER NOT NULL DEFAULT 1"
            )
    if "created_by_id" not in columns:
        with engine.begin() as connection:
            connection.exec_driver_sql(
                "ALTER TABLE work_orders ADD COLUMN created_by_id VARCHAR(36)"
            )


def get_session() -> Iterator[Session]:
    with SessionLocal() as session:
        yield session
