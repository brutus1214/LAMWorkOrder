from uuid import uuid4

PAYLOAD = {
    "storeNumber": 3,
    "title": "Replace line filter",
    "description": "Filter housing is leaking.",
    "requestedBy": "A. Rivera",
    "location": "Line 2 / Bay 4",
    "priority": "High",
    "assignedTo": "Maintenance",
}


def test_health(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_login_profile_and_permissions(client):
    assert client.get("/api/profile").json()["role"] == "Admin"
    profile = client.patch(
        "/api/profile", json={"displayName": "Lead Admin", "email": "lead@example.com"}
    )
    assert profile.json()["displayName"] == "Lead Admin"
    login = client.post(
        "/api/auth/login", json={"username": "requester", "password": "test-password"}
    )
    requester = {"Authorization": f"Bearer {login.json()['token']}"}
    created = client.post("/api/work-orders", json=PAYLOAD, headers=requester).json()
    denied = client.put(
        f"/api/work-orders/{created['id']}",
        json={**PAYLOAD, "status": "Scheduled", "statusNote": "planned"},
        headers=requester,
    )
    assert denied.status_code == 403


def test_full_edit_and_multiple_attachments(client, tmp_path, monkeypatch):
    import lamworkorder.api as api

    monkeypatch.setattr(api, "UPLOADS", tmp_path)
    created = client.post("/api/work-orders", json=PAYLOAD).json()
    edited = client.put(
        f"/api/work-orders/{created['id']}",
        json={**PAYLOAD, "title": "Fully edited", "status": "Scheduled", "statusNote": "Ready"},
    )
    assert edited.status_code == 200
    assert edited.json()["title"] == "Fully edited"
    uploaded = client.post(
        f"/api/work-orders/{created['id']}/attachments",
        files=[
            ("files", ("one.jpg", b"jpeg", "image/jpeg")),
            ("files", ("two.mp4", b"video", "video/mp4")),
        ],
    )
    assert uploaded.status_code == 201
    assert len(uploaded.json()) == 2
    refreshed = client.get(f"/api/work-orders/{created['id']}").json()
    assert {item["originalName"] for item in refreshed["attachments"]} == {"one.jpg", "two.mp4"}


def test_create_list_filter_and_update(client):
    created_response = client.post("/api/work-orders", json=PAYLOAD)
    assert created_response.status_code == 201
    created = created_response.json()
    assert created["workOrderNumber"].startswith("WO-3-")
    assert created["workOrderNumber"].endswith("-0001")
    assert created["storeNumber"] == 3
    assert created["status"] == "New"

    second_response = client.post(
        "/api/work-orders",
        json={**PAYLOAD, "title": "Replace second filter"},
    )
    assert second_response.status_code == 201
    assert second_response.json()["workOrderNumber"].endswith("-0002")

    other_store_response = client.post(
        "/api/work-orders",
        json={**PAYLOAD, "storeNumber": 6, "title": "Replace store six filter"},
    )
    assert other_store_response.status_code == 201
    assert other_store_response.json()["workOrderNumber"].startswith("WO-6-")
    assert other_store_response.json()["workOrderNumber"].endswith("-0001")

    filtered = client.get("/api/work-orders", params={"priority": "High", "search": "line filter"})
    assert filtered.status_code == 200
    assert [item["id"] for item in filtered.json()] == [created["id"]]

    updated = client.patch(
        f"/api/work-orders/{created['id']}/status",
        json={"status": "InProgress", "note": "Technician dispatched"},
    )
    assert updated.status_code == 200
    assert updated.json()["status"] == "InProgress"
    assert updated.json()["statusNote"] == "Technician dispatched"


def test_validation_and_missing(client):
    invalid = client.post("/api/work-orders", json={**PAYLOAD, "title": ""})
    assert invalid.status_code == 422
    invalid_store = client.post("/api/work-orders", json={**PAYLOAD, "storeNumber": 0})
    assert invalid_store.status_code == 422
    assert client.get(f"/api/work-orders/{uuid4()}").status_code == 404
