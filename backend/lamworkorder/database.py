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
    if "work_orders" not in inspector.get_table_names():
        return

    columns = {column["name"] for column in inspector.get_columns("work_orders")}
    if "store_number" not in columns:
        with engine.begin() as connection:
            connection.exec_driver_sql(
                "ALTER TABLE work_orders "
                "ADD COLUMN store_number INTEGER NOT NULL DEFAULT 1"
            )


def get_session() -> Iterator[Session]:
    with SessionLocal() as session:
        yield session
