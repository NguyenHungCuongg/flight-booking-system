import json
import threading
import time
from datetime import datetime, timezone

import httpx
import openai
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


def events(r) -> list[dict]:
    assert r.status_code == 200 and r.headers["content-type"].startswith("text/event-stream")
    assert "no-transform" in r.headers["cache-control"]     # thiếu thì lớp gzip của Next.js gom cả stream
    return [json.loads(block.removeprefix("data: ")) for block in r.text.split("\n\n") if block]


def sent(c, chat, text, headers) -> dict:
    """Gửi tin, trả kết quả của lượt: sự kiện cuối của stream."""
    last = events(c.post(f"/agent/chats/{chat}/messages", json={"text": text}, headers=headers))[-1]
    assert last["type"] == "turn"
    return last["turn"]


def hold_flow():
    return (call("set_constraints", **ROUTE), call("search_flights"),
            call("propose_hold", flight_ids=[122], fare_family_id=1, passengers=[PAX]), AIMessage(content="Bấm Giữ chỗ nhé."))


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
    assert c.get(f"/agent/chats/{chat}", headers={"Cookie": "JSESSIONID=s2"}).status_code == 404


def test_full_flow_forwards_the_incoming_csrf_header_only():
    fake = FakeBackend()
    c = client(fake, *hold_flow())
    h = {"Cookie": COOKIE, "X-XSRF-TOKEN": "from-header"}
    chat = c.post("/agent/chats", headers=h).json()["chatId"]
    out = sent(c, chat, "Đặt vé", h)
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


def test_owner_reads_back_every_turn_and_only_the_live_proposal_is_open():
    c = client(FakeBackend(), *hold_flow())
    h = {"Cookie": COOKIE}
    chat = c.post("/agent/chats", headers=h).json()["chatId"]
    out = sent(c, chat, "Đặt vé", h)
    pid = out["cards"][-1]["proposalId"]
    view = c.get(f"/agent/chats/{chat}", headers=h).json()
    assert view == {"entries": [{"from": "user", "text": "Đặt vé"}, {"from": "agent", "turn": out}],
                    "proposalId": pid}
    held = c.post(f"/agent/chats/{chat}/proposals/{pid}/approve", headers=h).json()
    view = c.get(f"/agent/chats/{chat}", headers=h).json()
    assert view["entries"][-1] == {"from": "agent", "turn": held} and len(view["entries"]) == 3
    assert view["proposalId"] is None


def test_a_new_proposal_closes_the_old_card_in_the_read_back():
    c = client(FakeBackend(), *hold_flow(), call("propose_hold", flight_ids=[104], fare_family_id=1, passengers=[PAX]),
               AIMessage(content="Mình đổi sang QH104."))
    h = {"Cookie": COOKIE}
    chat = c.post("/agent/chats", headers=h).json()["chatId"]
    first = sent(c, chat, "Đặt vé", h)
    second = sent(c, chat, "Đổi chuyến sớm hơn", h)
    view = c.get(f"/agent/chats/{chat}", headers=h).json()
    assert view["proposalId"] == second["cards"][-1]["proposalId"] != first["cards"][-1]["proposalId"]


def test_failed_approval_does_not_show_the_system_message_as_the_customer():
    c = client(FakeBackend(hold_error=(409, "SEATS_UNAVAILABLE")), *hold_flow(),
               call("propose_hold", flight_ids=[104], fare_family_id=1, passengers=[PAX]), AIMessage(content="Duyệt QH104 nhé."))
    h = {"Cookie": COOKIE}
    chat = c.post("/agent/chats", headers=h).json()["chatId"]
    pid = sent(c, chat, "Đặt vé", h)["cards"][-1]["proposalId"]
    c.post(f"/agent/chats/{chat}/proposals/{pid}/approve", headers=h)
    view = c.get(f"/agent/chats/{chat}", headers=h).json()
    assert [e["from"] for e in view["entries"]] == ["user", "agent", "agent"]


def test_reading_back_waits_for_the_running_turn():
    c = client(FakeBackend(search_delay=0.3), *hold_flow())
    h = {"Cookie": COOKIE}
    chat = c.post("/agent/chats", headers=h).json()["chatId"]
    sending = threading.Thread(target=lambda: c.post(f"/agent/chats/{chat}/messages", json={"text": "Đặt vé"},
                                                     headers=h))
    sending.start()
    time.sleep(0.1)                                     # lượt đang chờ search_flights
    view = c.get(f"/agent/chats/{chat}", headers=h).json()
    sending.join()
    assert [e["from"] for e in view["entries"]] == ["user", "agent"]


def test_crashed_turn_keeps_the_message_and_no_unseen_card_is_open():
    c = client(FakeBackend(), call("set_constraints", **ROUTE), call("search_flights"),
               call("propose_hold", flight_ids=[122], fare_family_id=1, passengers=[PAX]), RuntimeError("lỗi bất ngờ"))
    h = {"Cookie": COOKIE}
    chat = c.post("/agent/chats", headers=h).json()["chatId"]
    r = c.post(f"/agent/chats/{chat}/messages", json={"text": "Đặt vé"}, headers=h)
    assert "turn" not in [e["type"] for e in events(r)]
    assert c.get(f"/agent/chats/{chat}", headers=h).json() == {"entries": [{"from": "user", "text": "Đặt vé"}],
                                                               "proposalId": None}


def test_edit_closes_the_proposal_on_the_service_too():
    c = client(FakeBackend(), *hold_flow())
    h = {"Cookie": COOKIE}
    chat = c.post("/agent/chats", headers=h).json()["chatId"]
    pid = sent(c, chat, "Đặt vé", h)["cards"][-1]["proposalId"]
    assert c.post(f"/agent/chats/{chat}/proposals/{pid}/dismiss", headers=h).status_code == 200
    assert c.get(f"/agent/chats/{chat}", headers=h).json()["proposalId"] is None
    assert c.post(f"/agent/chats/{chat}/proposals/{pid}/approve", headers=h).status_code == 404


def test_each_tool_step_streams_before_the_turn():
    c = client(FakeBackend(), *hold_flow())
    h = {"Cookie": COOKIE}
    chat = c.post("/agent/chats", headers=h).json()["chatId"]
    ev = events(c.post(f"/agent/chats/{chat}/messages", json={"text": "Đặt vé"}, headers=h))
    assert [e["type"] for e in ev] == ["step", "step", "step", "turn"]
    assert [{k: v for k, v in e.items() if k != "type"} for e in ev[:-1]] == ev[-1]["turn"]["trace"]


def test_llm_outage_mid_stream_ends_with_a_handoff():
    err = openai.APITimeoutError(request=httpx.Request("POST", "http://llm"))
    c = client(FakeBackend(), call("set_constraints", **ROUTE), err)
    h = {"Cookie": COOKIE}
    chat = c.post("/agent/chats", headers=h).json()["chatId"]
    ev = events(c.post(f"/agent/chats/{chat}/messages", json={"text": "Đặt vé"}, headers=h))
    assert [e["type"] for e in ev] == ["step", "turn"]
    assert ev[-1]["turn"]["cards"][-1]["type"] == "handoff"
