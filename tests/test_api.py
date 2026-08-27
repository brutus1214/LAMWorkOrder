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


def register_jc(client):
    return client.post(
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


def auth_headers(client, username: str) -> dict[str, str]:
    response = client.post(
        "/api/auth/login", json={"username": username, "password": "test-password"}
    )
    return {"Authorization": f"Bearer {response.json()['token']}"}


def test_health(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_dashboard_css_keeps_hidden_views_hidden(client):
    response = client.get("/assets/app.css")

    assert response.status_code == 200
    assert "[hidden]{display:none!important;}" in response.text.replace(" ", "").replace("\n", "")


def test_dashboard_has_mobile_queue_layout_assets(client):
    dashboard = client.get("/")
    styles = client.get("/assets/app.css")
    script = client.get("/assets/app.js")

    assert dashboard.status_code == 200
    assert 'id="mobile-create-toggle"' in dashboard.text
    assert 'data-filter="Me"' in dashboard.text
    assert 'id="work-order-dialog"' in dashboard.text
    assert "app.css?v=20260826-media" in dashboard.text
    assert "app.js?v=20260826-media" in dashboard.text
    assert "@media (max-width: 700px)" in styles.text
    assert "table,\n  tbody,\n  tr,\n  td" in styles.text
    assert "flex-wrap: wrap" in styles.text
    assert "detail-dialog" in styles.text
    assert "setMobileIntakeOpen" in script.text
    assert "matchesCurrentUser" in script.text
    assert "openWorkOrderDetail" in script.text
    assert "hydrateAttachmentPreviews" in script.text
    assert "attachment-preview" in script.text
    assert "URL.createObjectURL" in script.text
    assert "attachment-card" in styles.text
    assert "data-order-id" in script.text
    assert '"Admin", "Manager", "Employee", "Technician"' in script.text


def test_jc_registration_is_all_store_administrator(client):
    response = register_jc(client)
    assert response.status_code == 201
    assert response.json()["user"]["role"] == "Admin"
    assert response.json()["user"]["storeNumber"] == 99


def test_login_profile_and_permissions(client):
    assert client.get("/api/profile").json()["role"] == "Admin"
    profile = client.patch(
        "/api/profile", json={"displayName": "Lead Admin", "email": "lead@example.com"}
    )
    assert profile.json()["displayName"] == "Lead Admin"
    requester = auth_headers(client, "requester")
    created = client.post(
        "/api/work-orders", json={**PAYLOAD, "storeNumber": 1}, headers=requester
    ).json()
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
    requester = auth_headers(client, "requester")
    created = client.post(
        "/api/work-orders", json={**PAYLOAD, "storeNumber": 1}, headers=requester
    ).json()
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


def test_non_admins_can_only_write_work_orders_for_their_store(client, tmp_path, monkeypatch):
    import lamworkorder.api as api

    monkeypatch.setattr(api, "UPLOADS", tmp_path)
    manager = auth_headers(client, "manager")
    technician = auth_headers(client, "technician")
    requester = auth_headers(client, "requester")

    other_store = client.post(
        "/api/work-orders",
        json={**PAYLOAD, "storeNumber": 4, "title": "Other store order"},
    ).json()
    manager_store = client.post(
        "/api/work-orders",
        json={**PAYLOAD, "storeNumber": 3, "title": "Manager store order"},
    ).json()
    technician_store = client.post(
        "/api/work-orders",
        json={**PAYLOAD, "storeNumber": 1, "title": "Technician store order"},
    ).json()

    denied_create = client.post("/api/work-orders", json=PAYLOAD, headers=requester)
    assert denied_create.status_code == 403

    denied_edit = client.put(
        f"/api/work-orders/{other_store['id']}",
        json={**PAYLOAD, "status": "Scheduled", "statusNote": "planned"},
        headers=manager,
    )
    assert denied_edit.status_code == 403

    allowed_edit = client.put(
        f"/api/work-orders/{manager_store['id']}",
        json={**PAYLOAD, "status": "Scheduled", "statusNote": "planned"},
        headers=manager,
    )
    assert allowed_edit.status_code == 200

    denied_status = client.patch(
        f"/api/work-orders/{other_store['id']}/status",
        json={"status": "InProgress", "note": "wrong store"},
        headers=technician,
    )
    assert denied_status.status_code == 403

    allowed_status = client.patch(
        f"/api/work-orders/{technician_store['id']}/status",
        json={"status": "InProgress", "note": "same store"},
        headers=technician,
    )
    assert allowed_status.status_code == 200

    denied_upload = client.post(
        f"/api/work-orders/{other_store['id']}/attachments",
        files=[("files", ("proof.jpg", b"jpeg", "image/jpeg"))],
        headers=requester,
    )
    assert denied_upload.status_code == 403

    uploaded = client.post(
        f"/api/work-orders/{other_store['id']}/attachments",
        files=[("files", ("proof.jpg", b"jpeg", "image/jpeg"))],
    ).json()
    denied_delete = client.delete(
        f"/api/attachments/{uploaded[0]['id']}",
        headers=manager,
    )
    assert denied_delete.status_code == 403


def test_assignment_support_lists_technicians_and_notification_recipients(client):
    technicians = client.get("/api/technicians")
    assert technicians.status_code == 200
    assert [user["username"] for user in technicians.json()] == ["technician"]

    assert register_jc(client).status_code == 201

    assignees = client.get("/api/assignees")
    assert assignees.status_code == 200
    assert {user["username"] for user in assignees.json()} == {
        "jc",
        "employee",
        "manager",
        "technician",
    }
    assert next(user for user in assignees.json() if user["username"] == "jc")[
        "displayName"
    ] == "James Chang"

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

    jc = register_jc(client).json()
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
