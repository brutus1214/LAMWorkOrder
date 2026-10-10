from datetime import UTC, datetime

import httpx
from lamworkorder_desktop.client import ApiClient
from lamworkorder_desktop.work_order_filters import filter_work_orders, format_created_date


def test_client_builds_filter_request(monkeypatch):
    request_seen = {}

    def handler(request):
        request_seen["url"] = str(request.url)
        return httpx.Response(200, json=[])

    transport = httpx.MockTransport(handler)

    class TestHttpClient(httpx.Client):
        def __init__(self, *args, **kwargs):
            kwargs["transport"] = transport
            super().__init__(*args, **kwargs)

    monkeypatch.setattr(httpx, "Client", TestHttpClient)
    assert ApiClient("http://example.test/").list_work_orders("pump", "New") == []
    assert "search=pump" in request_seen["url"]
    assert "status=New" in request_seen["url"]


def test_client_lists_assignees(monkeypatch):
    request_seen = {}

    def handler(request):
        request_seen["url"] = str(request.url)
        return httpx.Response(200, json=[{"displayName": "Employee One"}])

    transport = httpx.MockTransport(handler)

    class TestHttpClient(httpx.Client):
        def __init__(self, *args, **kwargs):
            kwargs["transport"] = transport
            super().__init__(*args, **kwargs)

    monkeypatch.setattr(httpx, "Client", TestHttpClient)
    assert ApiClient("http://example.test/").list_assignees() == [
        {"displayName": "Employee One"}
    ]
    assert str(request_seen["url"]).endswith("/api/assignees")


def test_client_deletes_account_and_clears_session(monkeypatch):
    request_seen = {}

    def handler(request):
        request_seen["method"] = request.method
        request_seen["url"] = str(request.url)
        request_seen["authorization"] = request.headers.get("Authorization")
        return httpx.Response(204)

    transport = httpx.MockTransport(handler)

    class TestHttpClient(httpx.Client):
        def __init__(self, *args, **kwargs):
            kwargs["transport"] = transport
            super().__init__(*args, **kwargs)

    monkeypatch.setattr(httpx, "Client", TestHttpClient)
    client = ApiClient("http://example.test/")
    client.token = "session-token"
    client.user = {"username": "requester"}

    client.delete_account()

    assert request_seen == {
        "method": "DELETE",
        "url": "http://example.test/api/profile",
        "authorization": "Bearer session-token",
    }
    assert client.token is None
    assert client.user is None


def test_all_filter_only_shows_recent_work_orders():
    now = datetime(2026, 8, 18, 12, tzinfo=UTC)
    orders = [
        work_order("recent", "New", "2026-08-01T12:00:00Z"),
        work_order("completed", "Completed", "2026-08-01T12:00:00Z"),
        work_order("older", "New", "2026-07-01T12:00:00Z"),
    ]

    assert [order["id"] for order in filter_work_orders(orders, "All", now)] == ["recent"]


def test_status_filter_can_show_older_matching_work_orders():
    now = datetime(2026, 8, 18, 12, tzinfo=UTC)
    orders = [work_order("older", "Completed", "2026-07-01T12:00:00Z")]

    assert [order["id"] for order in filter_work_orders(orders, "Completed", now)] == ["older"]


def test_created_date_formats_compactly_for_list_display():
    assert format_created_date("2026-08-18T12:30:00Z") == "8/18/26"


def work_order(order_id: str, status: str, created_at: str) -> dict:
    return {
        "id": order_id,
        "status": status,
        "createdAt": created_at,
    }
