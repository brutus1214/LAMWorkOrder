import os

import httpx


class ApiClient:
    def __init__(self, base_url: str | None = None):
        self.base_url = (
            base_url or os.getenv("LAMWORKORDER_API_URL", "http://127.0.0.1:5080")
        ).rstrip("/")

    def list_work_orders(self, search: str = "", status: str = "") -> list[dict]:
        params = {
            key: value for key, value in {"search": search, "status": status}.items() if value
        }
        with httpx.Client(base_url=self.base_url, timeout=10) as client:
            response = client.get("/api/work-orders", params=params)
            response.raise_for_status()
            return response.json()

    def create_work_order(self, payload: dict) -> dict:
        with httpx.Client(base_url=self.base_url, timeout=10) as client:
            response = client.post("/api/work-orders", json=payload)
            response.raise_for_status()
            return response.json()

    def update_status(self, work_order_id: str, status: str) -> dict:
        with httpx.Client(base_url=self.base_url, timeout=10) as client:
            response = client.patch(
                f"/api/work-orders/{work_order_id}/status", json={"status": status}
            )
            response.raise_for_status()
            return response.json()
