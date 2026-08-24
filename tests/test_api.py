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


def test_dashboard_css_keeps_hidden_views_hidden(client):
    response = client.get("/assets/app.css")

    assert response.status_code == 200
    assert "[hidden]{display:none!important}" in response.text


def test_jc_registration_is_all_store_administrator(client):
    response = client.post(
        "/api/auth/register",
        json={
            "username": "jc",
            "password": "secure-password",
            "displayName": "James Chang",
            "storeNumber": 1,
            "email": "jc@example.com",
            "phoneNumber": "202-555-0100",
        },
    )
    assert response.status_code == 201
    assert response.json()["user"]["role"] == "Admin"
    assert response.json()["user"]["storeNumber"] == 99


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
    assert created["requestedBy"] == "Requester"
    denied = client.put(
        f"/api/work-orders/{created['id']}",
        json={**PAYLOAD, "status": "Scheduled", "statusNote": "planned"},
        headers=requester,
    )
    assert denied.status_code == 403


def test_requester_can_attach_media_to_new_work_order(client, tmp_path, monkeypatch):
    import lamworkorder.api as api

    monkeypatch.setattr(api, "UPLOADS", tmp_path)
    login = client.post(
        "/api/auth/login", json={"username": "requester", "password": "test-password"}
    )
    requester = {"Authorization": f"Bearer {login.json()['token']}"}
    created = client.post("/api/work-orders", json=PAYLOAD, headers=requester).json()
    uploaded = client.post(
        f"/api/work-orders/{created['id']}/attachments",
        files=[("files", ("proof.jpg", b"jpeg", "image/jpeg"))],
        headers=requester,
    )
    assert uploaded.status_code == 201


def test_full_edit_and_multiple_attachments(client, tmp_path, monkeypatch):
    import lamworkorder.api as api

    monkeypatch.setattr(api, "UPLOADS", tmp_path)
    created = client.post("/api/work-orders", json=PAYLOAD).json()
    edited = client.put(
        f"/api/work-orders/{created['id']}",
        json={**PAYLOAD, "storeNumber": 9, "title": "Fully edited", "status": "Scheduled", "statusNote": "Ready"},
    )
    assert edited.status_code == 200
    assert edited.json()["title"] == "Fully edited"
    assert edited.json()["storeNumber"] == PAYLOAD["storeNumber"]
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


def test_assignment_support_lists_technicians_and_notification_recipients(client):
    technicians = client.get("/api/technicians")
    assert technicians.status_code == 200
    assert [user["username"] for user in technicians.json()] == ["technician"]

    assignees = client.get("/api/assignees")
    assert assignees.status_code == 200
    assert {user["username"] for user in assignees.json()} == {
        "employee",
        "manager",
        "technician",
    }

    client.post(
        "/api/auth/register",
        json={
            "username": "jc",
            "password": "secure-password",
            "displayName": "James Chang",
            "storeNumber": 1,
            "email": "jc@example.com",
            "phoneNumber": "202-555-0100",
        },
    )
    recipients = client.get(
        "/api/work-order-notification-recipients",
        params={"storeNumber": 3},
    )
    assert recipients.status_code == 200
    assert {user["email"] for user in recipients.json()} == {
        "jc@example.com",
        "manager3@example.com",
    }


def test_admin_can_assign_employee_role(client):
    requester = next(
        user for user in client.get("/api/users").json() if user["username"] == "requester"
    )
    updated = client.patch(
        f"/api/users/{requester['id']}",
        json={
            "displayName": requester["displayName"],
            "storeNumber": requester["storeNumber"],
            "role": "Employee",
            "email": requester["email"],
            "phoneNumber": requester["phoneNumber"],
            "isActive": requester["isActive"],
        },
    )
    assert updated.status_code == 200
    assert updated.json()["role"] == "Employee"


def test_only_jc_can_delete_work_order(client, tmp_path, monkeypatch):
    import lamworkorder.api as api

    monkeypatch.setattr(api, "UPLOADS", tmp_path)
    created = client.post("/api/work-orders", json=PAYLOAD).json()
    uploaded = client.post(
        f"/api/work-orders/{created['id']}/attachments",
        files=[("files", ("proof.jpg", b"jpeg", "image/jpeg"))],
    )
    assert uploaded.status_code == 201
    assert any(tmp_path.iterdir())

    denied = client.delete(f"/api/work-orders/{created['id']}")
    assert denied.status_code == 403
    assert client.get(f"/api/work-orders/{created['id']}").status_code == 200

    jc = client.post(
        "/api/auth/register",
        json={
            "username": "jc",
            "password": "secure-password",
            "displayName": "James Chang",
            "storeNumber": 1,
            "email": "jc@example.com",
            "phoneNumber": "202-555-0100",
        },
    ).json()
    deleted = client.delete(
        f"/api/work-orders/{created['id']}",
        headers={"Authorization": f"Bearer {jc['token']}"},
    )
    assert deleted.status_code == 204
    assert client.get(f"/api/work-orders/{created['id']}").status_code == 404
    assert list(tmp_path.iterdir()) == []


def test_validation_and_missing(client):
    invalid = client.post("/api/work-orders", json={**PAYLOAD, "title": ""})
    assert invalid.status_code == 422
    invalid_store = client.post("/api/work-orders", json={**PAYLOAD, "storeNumber": 0})
    assert invalid_store.status_code == 422
    assert client.get(f"/api/work-orders/{uuid4()}").status_code == 404
