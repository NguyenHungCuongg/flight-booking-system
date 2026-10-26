import threading

import pytest
from langchain_core.messages import AIMessage

from flight_agent.chat import Chats, approve, turn
from tests.fakes import FakeBackend, call, scripted

ROUTE = dict(origin="SGN", destination="DAD", date="2026-10-30", depart_before="12:00", max_price=2_000_000)
PAX = dict(full_name="Nguyễn Hùng Cường", gender="MALE", date_of_birth="2004-03-12")
TODAY = "2026-10-26"


def proposed(fake, *extra):
    chat = Chats().create("s1")
    model = scripted(call("set_constraints", **ROUTE), call("search_flights"),
                     call("propose_hold", flight_ids=[122], fare_family_id=1, **PAX),
                     AIMessage(content="Bạn bấm Giữ chỗ để mình giữ ghế nhé."), *extra)
    out = turn(chat, model, "Đặt giúp mình 1 vé SGN đi Đà Nẵng thứ 6 này, trước 12h, dưới 2 triệu",
               fake.backend(), TODAY)
    return chat, model, out


def test_a_turn_ends_with_the_proposal_and_never_holds():
    fake = FakeBackend()
    _, _, out = proposed(fake)
    assert [c["type"] for c in out["cards"]] == ["constraints", "proposal"]
    assert out["reply"] == "Bạn bấm Giữ chỗ để mình giữ ghế nhé."
    assert fake.holds == []


def test_model_claiming_it_held_the_seat_is_not_a_hold():
    fake = FakeBackend()
    chat = Chats().create("s1")
    out = turn(chat, scripted(call("set_constraints", **ROUTE), call("search_flights"),
                              AIMessage(content="Đã giữ chỗ xong!")), "Đặt vé", fake.backend(), TODAY)
    assert "held" not in [c["type"] for c in out["cards"]]
    assert fake.holds == []


def test_approval_holds_the_exact_body_and_reads_it_back():
    fake = FakeBackend()
    chat, model, out = proposed(fake)
    proposal = chat.harness.proposal
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    assert fake.holds == [proposal.body]
    assert result["cards"] == [{"type": "held", "code": "K7M2QX",
                                "holdExpiresAt": "2026-10-30T06:25:00+07:00", "totalAmount": 1_850_000}]
    assert any(q.url.path == "/api/bookings/K7M2QX" for q in fake.requests)


def test_a_proposal_can_be_approved_only_once():
    fake = FakeBackend()
    chat, model, out = proposed(fake)
    pid = out["cards"][-1]["proposalId"]
    approve(chat, model, pid, fake.backend(), TODAY)
    with pytest.raises(LookupError):
        approve(chat, model, pid, fake.backend(), TODAY)
    assert len(fake.holds) == 1


def test_approving_replaced_proposal_is_refused():
    fake = FakeBackend()
    chat = Chats().create("s1")
    out = turn(chat, scripted(call("set_constraints", **ROUTE), call("search_flights"),
                              call("propose_hold", flight_ids=[122], fare_family_id=1, **PAX),
                              call("propose_hold", flight_ids=[104], fare_family_id=1, **PAX),
                              AIMessage(content="Mình đổi sang QH104.")), "Đặt vé", fake.backend(), TODAY)
    first = [c for c in out["cards"] if c["type"] == "proposal"][0]["proposalId"]
    with pytest.raises(LookupError):
        approve(chat, scripted(), first, fake.backend(), TODAY)
    assert fake.holds == []


def test_concurrent_approvals_create_one_booking():
    fake = FakeBackend(hold_delay=0.05)
    chat, model, out = proposed(fake)
    pid, results = out["cards"][-1]["proposalId"], []

    def click():
        try:
            results.append(approve(chat, model, pid, fake.backend(), TODAY))
        except LookupError:
            results.append("refused")

    threads = [threading.Thread(target=click) for _ in range(2)]
    [t.start() for t in threads]
    [t.join() for t in threads]
    assert len(fake.holds) == 1 and results.count("refused") == 1


def test_read_back_mismatch_hands_off_instead_of_held():
    for patch in ({"status": "EXPIRED"}, {"totalAmount": 1_900_000}):
        fake = FakeBackend(detail_patch=patch)
        chat, model, out = proposed(fake)
        result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
        assert [c["type"] for c in result["cards"]] == ["handoff"]
        assert "K7M2QX" in result["cards"][0]["question"]


def test_sold_out_on_approval_lets_the_agent_propose_another_flight():
    fake = FakeBackend(hold_error=(409, "SEATS_UNAVAILABLE"))
    chat, model, out = proposed(fake, call("propose_hold", flight_ids=[104], fare_family_id=1, **PAX),
                                AIMessage(content="VN122 vừa hết ghế, bạn duyệt QH104 nhé."))
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    proposals = [c for c in result["cards"] if c["type"] == "proposal"]
    assert proposals[0]["segments"][0]["flightNumber"] == "QH104"
    assert len(fake.holds) == 1                     # chỉ lần giữ thất bại


def test_budget_ends_the_turn_with_a_handoff():
    fake = FakeBackend()
    dates = [f"2026-11-{d:02d}" for d in range(1, 12)]
    out = turn(Chats().create("s1"), scripted(*[call("set_constraints", **{**ROUTE, "date": d}) for d in dates]),
               "Đặt vé", fake.backend(), TODAY)
    assert out["cards"][-1]["type"] == "handoff"
    assert "10 lần" in out["cards"][-1]["question"]


def test_loop_ends_the_turn_with_a_handoff():
    fake = FakeBackend()
    out = turn(Chats().create("s1"), scripted(call("set_constraints", **ROUTE),
                                              *[call("search_flights") for _ in range(5)]),
               "Đặt vé", fake.backend(), TODAY)
    assert out["cards"][-1]["type"] == "handoff"
    assert len(out["trace"]) == 4


def test_llm_outage_is_a_handoff_not_a_crash():
    import openai
    import httpx
    err = openai.APITimeoutError(request=httpx.Request("POST", "http://llm"))
    out = turn(Chats().create("s1"), scripted(err), "Đặt vé", FakeBackend().backend(), TODAY)
    assert out["cards"][-1]["type"] == "handoff"
    assert "thử lại sau" in out["cards"][-1]["question"]


def test_chat_belongs_to_its_session():
    chats = Chats()
    chat = chats.create("s1")
    assert chats.get(chat.id, "s1") is chat
    assert chats.get(chat.id, "s2") is None
    assert chats.get("nope", "s1") is None
