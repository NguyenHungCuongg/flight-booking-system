import httpx
import pytest

from flight_agent.backend import Backend, BackendError

seen = []


def echo(request):
    seen.append(request)
    if request.url.path == "/api/fail":
        return httpx.Response(409, json={"code": "SEATS_UNAVAILABLE", "detail": "Hết ghế",
                                         "errors": [{"field": "x", "message": "y"}]})
    return httpx.Response(200, json={"ok": True})


def backend(csrf):
    seen.clear()
    return Backend("http://be", "JSESSIONID=s1; XSRF-TOKEN=from-cookie", csrf, httpx.MockTransport(echo))


def test_cookie_is_forwarded_verbatim():
    backend(None).get("/api/me")
    assert seen[0].headers["cookie"] == "JSESSIONID=s1; XSRF-TOKEN=from-cookie"


def test_post_forwards_the_incoming_csrf_header():
    backend("from-header").post("/api/bookings/quote", {})
    assert seen[0].headers["x-xsrf-token"] == "from-header"


def test_csrf_is_never_taken_from_the_cookie():
    backend(None).post("/api/bookings/quote", {})
    assert "x-xsrf-token" not in seen[0].headers


def test_problem_detail_becomes_backend_error():
    with pytest.raises(BackendError) as e:
        backend("t").post("/api/fail", {})
    assert (e.value.status, e.value.code, e.value.detail) == (409, "SEATS_UNAVAILABLE", "Hết ghế")
    assert e.value.errors == [{"field": "x", "message": "y"}]


def test_unreachable_backend_is_a_network_error():
    def down(request):
        raise httpx.ConnectError("refused")
    with pytest.raises(BackendError) as e:
        Backend("http://be", "", None, httpx.MockTransport(down)).get("/api/me")
    assert (e.value.status, e.value.code) == (0, "NETWORK")
