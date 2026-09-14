from pathlib import Path

import httpx

from .config import API_BASE_URL


class ApiClient:
    def __init__(self, base_url: str | None = None):
        self.base_url = (base_url or API_BASE_URL).rstrip("/")
        self.token: str | None = None
        self.user: dict | None = None

    def _request(self, method: str, path: str, **kwargs):
        headers = kwargs.pop("headers", {})
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        with httpx.Client(base_url=self.base_url, timeout=30) as client:
            response = client.request(method, path, headers=headers, **kwargs)
            response.raise_for_status()
            return None if response.status_code == 204 else response.json()

    def login(self, username: str, password: str) -> dict:
        result = self._request(
            "POST", "/api/auth/login", json={"username": username, "password": password}
        )
        self.token, self.user = result["token"], result["user"]
        return self.user

    def logout(self):
        try:
            self._request("POST", "/api/auth/logout")
        finally:
            self.token = None
            self.user = None

    def update_profile(self, display_name: str, email: str | None):
        self.user = self._request(
            "PATCH", "/api/profile", json={"displayName": display_name, "email": email}
        )
        return self.user

    def list_work_orders(self, search: str = "", status: str = "") -> list[dict]:
        params = {
            key: value for key, value in {"search": search, "status": status}.items() if value
        }
        return self._request("GET", "/api/work-orders", params=params)

    def list_assignees(self) -> list[dict]:
        return self._request("GET", "/api/assignees")

    def create_work_order(self, payload: dict) -> dict:
        return self._request("POST", "/api/work-orders", json=payload)

    def upload_attachments(self, work_order_id: str, paths: list[str]):
        opened = []
        try:
            files = []
            for name in paths:
                handle = Path(name).open("rb")
                opened.append(handle)
                files.append(("files", (Path(name).name, handle)))
            return self._request(
                "POST", f"/api/work-orders/{work_order_id}/attachments", files=files
            )
        finally:
            for handle in opened:
                handle.close()

    def update_status(self, work_order_id: str, status: str) -> dict:
        return self._request(
            "PATCH", f"/api/work-orders/{work_order_id}/status", json={"status": status}
        )
