"""Gọi Spring /api/* thay mặt khách, bằng cookie phiên của chính khách.

X-XSRF-TOKEN chỉ được chuyển tiếp nguyên từ request đến, không bao giờ đọc từ cookie:
nếu tự đọc thì một trang lạ cũng giữ chỗ được thay khách (spec §5.3).
"""
import httpx


class BackendError(Exception):
    """Lỗi ProblemDetail của Spring; status 0 = không kết nối được."""

    def __init__(self, status: int, code: str, detail: str, errors: list[dict]):
        super().__init__(f"{status} {code}: {detail}")
        self.status, self.code, self.detail, self.errors = status, code, detail, errors


class Backend:
    def __init__(self, base_url: str, cookie: str, csrf: str | None, transport: httpx.BaseTransport | None = None):
        self.base_url, self.cookie, self.csrf, self.transport = base_url, cookie, csrf, transport

    def get(self, path: str, params: dict | None = None) -> dict | list:
        return self._send("GET", path, params=params)

    def post(self, path: str, body: dict) -> dict:
        headers = {"X-XSRF-TOKEN": self.csrf} if self.csrf is not None else {}
        return self._send("POST", path, json=body, headers=headers)

    def _send(self, method: str, path: str, headers: dict | None = None, **kwargs):
        try:
            with httpx.Client(base_url=self.base_url, transport=self.transport, timeout=30) as client:
                r = client.request(method, path, headers={"Cookie": self.cookie, **(headers or {})}, **kwargs)
        except httpx.TransportError:
            raise BackendError(0, "NETWORK", "Không kết nối được backend.", [])
        if r.is_success:
            return r.json()
        try:
            problem = r.json()
        except ValueError:
            problem = {}
        raise BackendError(r.status_code, problem.get("code", "INTERNAL_ERROR"),
                           problem.get("detail", "Đã có lỗi xảy ra."), problem.get("errors", []))
