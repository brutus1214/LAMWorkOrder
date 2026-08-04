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
