import os

os.environ["LAMWORKORDER_DATABASE_URL"] = "sqlite://"

import pytest
from fastapi.testclient import TestClient
from lamworkorder.auth import hash_password
from lamworkorder.database import Base, get_session
from lamworkorder.main import create_app
from lamworkorder.models import User
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool


@pytest.fixture
def client():
    engine = create_engine(
        "sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool
    )
    testing_session = sessionmaker(bind=engine, expire_on_commit=False)
    Base.metadata.create_all(engine)
    with testing_session() as session:
        for username, role in [
            ("admin", "Admin"),
            ("technician", "Technician"),
            ("requester", "Requester"),
        ]:
            session.add(
                User(
                    username=username,
                    password_hash=hash_password("test-password"),
                    display_name=username.title(),
                    role=role,
                )
            )
        session.commit()
    app = create_app()

    def override_session():
        with testing_session() as session:
            yield session

    app.dependency_overrides[get_session] = override_session
    with TestClient(app) as test_client:
        response = test_client.post(
            "/api/auth/login", json={"username": "admin", "password": "test-password"}
        )
        test_client.headers["Authorization"] = f"Bearer {response.json()['token']}"
        yield test_client
