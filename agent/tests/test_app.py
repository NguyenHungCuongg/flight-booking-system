from datetime import datetime, timezone

from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage

from flight_agent.app import create_app, vn_today
from tests.fakes import FakeBackend, call, scripted

ROUTE = dict(origin="SGN", destination="DAD", date="2026-10-30", depart_before="12:00", max_price=2_000_000)
PAX = dict(full_name="Nguyễn Hùng Cường", gender="MALE", date_of_birth="2004-03-12")
COOKIE = "JSESSIONID=s1; XSRF-TOKEN=from-cookie"


def client(fake, *replies):
    app = create_app(model=scripted(*replies), backend_url="http://be",
                     transport=fake.transport, today=lambda: "2026-10-26")
    return TestClient(app)


def hold_flow():
    return (call("set_constraints", **ROUTE), call("search_flights"),
            call("propose_hold", flight_ids=[122], fare_family_id=1, **PAX), AIMessage(content="Bấm Giữ chỗ nhé."))


def test_vn_today_uses_utc_plus_7():
    assert vn_today(datetime(2026, 10, 26, 18, 30, tzinfo=timezone.utc)) == "2026-10-27"


def test_creating_a_chat_needs_a_session():
    c = client(FakeBackend())
    assert c.post("/agent/chats").status_code == 401
    assert c.post("/agent/chats", headers={"Cookie": COOKIE}).status_code == 201


def test_another_session_cannot_use_the_chat():
    c = client(FakeBackend(), *hold_flow())
    chat = c.post("/agent/chats", headers={"Cookie": COOKIE}).json()["chatId"]
    r = c.post(f"/agent/chats/{chat}/messages", json={"text": "hi"}, headers={"Cookie": "JSESSIONID=s2"})
    assert r.status_code == 404


def test_full_flow_forwards_the_incoming_csrf_header_only():
    fake = FakeBackend()
    c = client(fake, *hold_flow())
    h = {"Cookie": COOKIE, "X-XSRF-TOKEN": "from-header"}
    chat = c.post("/agent/chats", headers=h).json()["chatId"]
    out = c.post(f"/agent/chats/{chat}/messages", json={"text": "Đặt vé"}, headers=h).json()
    pid = out["cards"][-1]["proposalId"]
    held = c.post(f"/agent/chats/{chat}/proposals/{pid}/approve", headers=h)
    assert held.status_code == 200 and held.json()["cards"][0]["type"] == "held"
    posts = [q for q in fake.requests if q.method == "POST"]
    assert posts and all(q.headers["x-xsrf-token"] == "from-header" for q in posts)
    again = c.post(f"/agent/chats/{chat}/proposals/{pid}/approve", headers=h)
    assert again.status_code == 404 and again.json()["detail"] == "Thẻ này đã hết hiệu lực."


def test_missing_csrf_header_is_not_invented():
    fake = FakeBackend()
    c = client(fake, *hold_flow())
    chat = c.post("/agent/chats", headers={"Cookie": COOKIE}).json()["chatId"]
    c.post(f"/agent/chats/{chat}/messages", json={"text": "Đặt vé"}, headers={"Cookie": COOKIE})
    quote = [q for q in fake.requests if q.url.path == "/api/bookings/quote"][0]
    assert "x-xsrf-token" not in quote.headers


def test_empty_message_is_rejected():
    c = client(FakeBackend())
    chat = c.post("/agent/chats", headers={"Cookie": COOKIE}).json()["chatId"]
    assert c.post(f"/agent/chats/{chat}/messages", json={"text": ""}, headers={"Cookie": COOKIE}).status_code == 422
