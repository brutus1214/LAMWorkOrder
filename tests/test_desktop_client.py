import httpx
from lamworkorder_desktop.client import ApiClient


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
