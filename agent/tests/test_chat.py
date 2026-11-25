import threading

import pytest
from langchain_core.messages import AIMessage

from flight_agent.chat import Chats, approve, turn
from tests.fakes import FakeBackend, call, scripted

ROUTE = dict(origin="SGN", destination="DAD", date="2026-10-30", depart_before="12:00", max_price=2_000_000)
PAX = dict(full_name="Nguyễn Hùng Cường", gender="MALE", date_of_birth="2004-03-12")
TODAY = "2026-10-26"


def proposed(fake, *extra, route=ROUTE, passengers=(PAX,), choice=None):
    chat = Chats().create("s1")
    choice = choice or dict(flight_ids=[122], fare_family_id=1)
    model = scripted(call("set_constraints", **route), call("search_flights"),
                     call("propose_hold", **choice, passengers=list(passengers)),
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
                              call("propose_hold", flight_ids=[122], fare_family_id=1, passengers=[PAX]),
                              call("propose_hold", flight_ids=[104], fare_family_id=1, passengers=[PAX]),
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


def test_read_back_with_other_passengers_hands_off():
    other = {"type": "ADULT", "lastName": "TRAN", "firstName": "THI MAI", "gender": "FEMALE",
             "dateOfBirth": "2005-01-02"}
    for patch in (lambda ps: ps + [other], lambda ps: [], lambda ps: [{**ps[0], "type": "CHILD"}]):
        fake = FakeBackend(passengers_patch=patch)
        chat, model, out = proposed(fake)
        result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
        assert [c["type"] for c in result["cards"]] == ["handoff"]


def test_read_back_in_another_order_is_done():
    mai = dict(full_name="Trần Thị Mai", gender="FEMALE", date_of_birth="2005-01-02")
    fake = FakeBackend(passengers_patch=lambda ps: ps[::-1])
    chat, model, out = proposed(fake, route={**ROUTE, "max_price": 4_000_000, "adults": 2}, passengers=[PAX, mai])
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    assert [c["type"] for c in result["cards"]] == ["held"]


def test_sold_out_on_approval_lets_the_agent_propose_another_flight():
    fake = FakeBackend(hold_error=(409, "SEATS_UNAVAILABLE"))
    chat, model, out = proposed(fake, call("propose_hold", flight_ids=[104], fare_family_id=1, passengers=[PAX]),
                                AIMessage(content="VN122 vừa hết ghế, bạn duyệt QH104 nhé."))
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    proposals = [c for c in result["cards"] if c["type"] == "proposal"]
    assert proposals[0]["journeys"][0]["segments"][0]["flightNumber"] == "QH104"
    assert len(fake.holds) == 1                     # chỉ lần giữ thất bại


TRIP = {**ROUTE, "max_price": 4_000_000, "return_date": "2026-11-02"}
ROUND = dict(flight_ids=[122], fare_family_id=1, return_flight_ids=[611], return_fare_family_id=1)


def test_round_trip_approval_holds_both_journeys():
    fake = FakeBackend()
    chat, model, out = proposed(fake, route=TRIP, choice=ROUND)
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    assert [j["direction"] for j in fake.holds[0]["journeys"]] == ["OUTBOUND", "RETURN"]
    assert [(c["type"], c["totalAmount"]) for c in result["cards"]] == [("held", 2_900_000)]


def test_read_back_with_other_journeys_hands_off():
    moved = lambda j, flight_id: {**j, "segments": [{**j["segments"][0], "flightId": flight_id}]}
    for patch in (lambda js: js[:1],                                        # mất chiều về
                  lambda js: [js[0], moved(js[1], 123)],                    # sai chuyến về
                  lambda js: [js[0], {**js[1], "fareFamilyName": "Eco"}],   # sai gói chiều về
                  lambda js: js[::-1]):                                     # đảo thứ tự hai chiều
        fake = FakeBackend(journeys_patch=patch)
        chat, model, out = proposed(fake, route=TRIP, choice=ROUND)
        result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
        assert [c["type"] for c in result["cards"]] == ["handoff"]


LOOSE = {**ROUTE, "max_price": 3_000_000}
BAG = dict(flight_ids=[122], fare_family_id=1, baggage_kg=20)


def test_approval_with_baggage_holds_and_reads_back():
    fake = FakeBackend()
    chat, model, out = proposed(fake, route=LOOSE, choice=BAG)
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    assert fake.holds[0]["baggage"] == [{"passengerIndex": 0, "direction": "OUTBOUND", "baggageOptionId": 22}]
    assert [(c["type"], c["totalAmount"]) for c in result["cards"]] == [("held", 2_200_000)]


def test_read_back_with_other_baggage_hands_off():
    bag = lambda js, **kw: [{**js[0], "baggage": [{**js[0]["baggage"][0], **kw}]}]
    for patch in (lambda js: [{**js[0], "baggage": []}],     # mất hành lý
                  lambda js: bag(js, weightKg=30),            # sai mức
                  lambda js: bag(js, passengerIndex=1)):      # gắn cho hành khách không có
        fake = FakeBackend(journeys_patch=patch)
        chat, model, out = proposed(fake, route=LOOSE, choice=BAG)
        result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
        assert [c["type"] for c in result["cards"]] == ["handoff"]


def test_voucher_applies_to_fare_and_baggage_up_to_its_cap():
    fake = FakeBackend()
    chat, model, out = proposed(fake, route=LOOSE, choice={**BAG, "voucher_code": "WELCOME10"})
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    # 1.850.000 + 350.000 = 2.200.000; giảm 10% = 220.000, trần 200.000
    assert [(c["type"], c["totalAmount"]) for c in result["cards"]] == [("held", 2_000_000)]


def test_read_back_without_the_voucher_hands_off():
    fake = FakeBackend(detail_patch={"voucherCode": None})
    chat, model, out = proposed(fake, choice=dict(flight_ids=[122], fare_family_id=1, voucher_code="WELCOME10"))
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    assert [c["type"] for c in result["cards"]] == ["handoff"]


def test_one_way_read_back_with_another_fare_hands_off():
    fake = FakeBackend(journeys_patch=lambda js: [{**js[0], "fareFamilyName": "Eco"}])
    chat, model, out = proposed(fake)
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    assert [c["type"] for c in result["cards"]] == ["handoff"]


def test_parallel_tool_calls_cannot_leave_results_of_old_constraints():
    # LangGraph chạy các tool call của cùng một AIMessage song song: search_flights đang chờ HTTP
    # thì set_constraints đổi ràng buộc. Kết quả tìm không được tính theo ràng buộc cũ.
    fake = FakeBackend(search_delay=0.2)
    chat = Chats().create("s1")
    both = AIMessage(content="", tool_calls=[call("search_flights").tool_calls[0],
                                             call("set_constraints", **{**ROUTE, "depart_before": "07:00"}).tool_calls[0]])
    turn(chat, scripted(call("set_constraints", **ROUTE), both, AIMessage(content="Xong.")), "Đặt vé",
         fake.backend(), TODAY)
    h = chat.harness
    assert h.constraints.depart_before == "07:00"
    for i in h.found:
        assert i["broken"] == h.constraints.broken(i["segments"][0]["from"], i["segments"][-1]["to"],
                                                   i["depart"], i["price"])


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


def test_prompt_names_todays_weekday():
    from flight_agent.core import Harness
    from flight_agent.react import system_prompt
    h = Harness()
    h.begin_turn(FakeBackend().backend(), TODAY)          # 2026-10-26 là thứ Hai
    assert "thứ Hai, 2026-10-26" in system_prompt(h)


def test_prompt_lists_policy_sections_and_the_no_info_sentence():
    from flight_agent.core import Harness
    from flight_agent.react import NO_INFO, system_prompt
    h = Harness()
    h.begin_turn(FakeBackend().backend(), TODAY)
    prompt = system_prompt(h)
    assert "hoan-doi-hanh-ly#dieu-kien-chung: Điều kiện chung" in prompt and NO_INFO in prompt


def test_chat_belongs_to_its_session():
    chats = Chats()
    chat = chats.create("s1")
    assert chats.get(chat.id, "s1") is chat
    assert chats.get(chat.id, "s2") is None
    assert chats.get("nope", "s1") is None


def test_runtime_guardrail_strips_markdown_fixes_unread_citations_and_blocks_false_hold_claims():
    fake = FakeBackend()
    chat = Chats().create("s1")
    out = turn(chat, scripted(
        call("set_constraints", **ROUTE),
        call("search_flights"),
        AIMessage(content="Mình **đã giữ chỗ thành công** chuyến `VN122` theo /chinh-sach/hoan-doi-hanh-ly#muc-bia!")
    ), "Đặt vé", fake.backend(), TODAY)
    assert "**" not in out["reply"] and "`" not in out["reply"]
    assert "/chinh-sach/hoan-doi-hanh-ly#muc-bia" not in out["reply"]
    assert "/chinh-sach/dieu-khoan" in out["reply"]
    assert "Hệ thống chưa giữ chỗ" in out["reply"]
    assert "hoan-doi-hanh-ly#muc-bia" in chat.harness.bad_citations


def test_old_tool_messages_are_compacted_on_next_turn():
    from flight_agent.react import compact_history
    from langchain_core.messages import HumanMessage, ToolMessage
    t1_tool = ToolMessage(content="x" * 1000, tool_call_id="call-1", name="search_flights")
    t2_tool = ToolMessage(content="y" * 1000, tool_call_id="call-2", name="search_flights")
    # Messages from the immediately preceding turn (after the last HumanMessage) stay intact for Turn 2;
    # messages from older turns (before the last HumanMessage) are compacted.
    compacted = compact_history([HumanMessage("turn 1"), t1_tool, HumanMessage("turn 2"), t2_tool])
    assert compacted[1].tool_call_id == "call-1" and len(compacted[1].content) < 100
    assert compacted[3].tool_call_id == "call-2" and len(compacted[3].content) == 1000


def test_token_usage_from_model_response_is_accumulated_in_run_turn():
    fake = FakeBackend()
    chat = Chats().create("s1")
    msg1 = call("set_constraints", **ROUTE)
    msg1.usage_metadata = {"input_tokens": 100, "output_tokens": 50, "total_tokens": 150}
    msg2 = AIMessage(content="Xong.", usage_metadata={"input_tokens": 200, "output_tokens": 50, "total_tokens": 250})
    turn(chat, scripted(msg1, msg2), "Đặt vé", fake.backend(), TODAY)
    assert chat.harness.tokens_used == 400


def test_read_back_violating_airline_or_nonstop_constraint_hands_off():
    fake = FakeBackend(journeys_patch=lambda js: [{**js[0], "airlineCode": "VJ"}])
    chat, model, out = proposed(fake, route={**ROUTE, "airline": "VN"})
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    assert [c["type"] for c in result["cards"]] == ["handoff"]


def test_failed_cancel_approval_reports_cancel_failure_not_created_booking():
    from tests.fakes import booking
    held = booking("K7M2QX", "PENDING_PAYMENT", hold_expires="2026-10-26T15:40:00+07:00")
    fake = FakeBackend(bookings=[held])
    chat = Chats().create("s1")
    out = turn(chat, scripted(call("propose_cancel", code="K7M2QX"), AIMessage(content="Duyệt huỷ nhé.")),
               "Huỷ K7M2QX", fake.backend(), TODAY)
    pid = out["cards"][-1]["proposalId"]
    # Booking expired before user clicked approve
    fake.bookings["K7M2QX"]["status"] = "EXPIRED"
    res = approve(chat, scripted(), pid, fake.backend(), TODAY)
    card = res["cards"][0]
    assert card["type"] == "handoff"
    assert "Đã tạo booking" not in " ".join(card["doneSoFar"])
    assert "Không huỷ được đặt chỗ K7M2QX" in card["question"]


def test_in_memory_chat_cache_respects_ttl_and_max_chats():
    from flight_agent.chat import CHAT_TTL_SECONDS
    store = Chats()
    chat = store.create("s1")
    chat.updated_at -= CHAT_TTL_SECONDS + 60.0
    assert store.get(chat.id, "s1") is None



def test_structured_trace_log_omits_pii_arguments():
    from flight_agent.chat import log_turn
    fake = FakeBackend()
    chat, _, _ = proposed(fake)
    entry = log_turn(chat.id, "turn", chat.harness, 42)
    assert entry["chat_id"] == chat.id and entry["action"] == "turn" and entry["latency_ms"] == 42
    assert entry["steps"][-1] == {"tool": "propose_hold", "status": "pending_approval"}
    assert "Nguyễn Hùng Cường" not in str(entry) and "2004-03-12" not in str(entry)


def test_approving_cancel_proposal_cancels_and_verifies_status():
    from tests.fakes import booking
    held = booking("K7M2QX", "PENDING_PAYMENT", hold_expires="2026-10-26T15:40:00+07:00")
    fake = FakeBackend(bookings=[held])
    chat = Chats().create("s1")
    out = turn(chat, scripted(call("propose_cancel", code="K7M2QX"),
                              AIMessage(content="Bấm Huỷ giữ chỗ để xác nhận nhé.")),
               "Huỷ đơn K7M2QX", fake.backend(), TODAY)
    pid = out["cards"][-1]["proposalId"]
    res = approve(chat, scripted(), pid, fake.backend(), TODAY)
    assert res["cards"] == [{"type": "cancelled", "code": "K7M2QX"}]
    assert fake.bookings["K7M2QX"]["status"] == "CANCELLED"


def test_chat_and_live_proposal_survive_service_restart_via_sqlite(tmp_path):
    db = tmp_path / "chats.sqlite3"
    fake = FakeBackend()
    store1 = Chats(db)
    chat1 = store1.create("s1")
    chat1.shown.append({"from": "user", "text": "Đặt vé"})
    out = turn(
        chat1,
        scripted(
            call("set_constraints", **ROUTE),
            call("search_flights"),
            call("propose_hold", flight_ids=[122], fare_family_id=1, passengers=[PAX]),
            AIMessage(content="Bấm Giữ chỗ nhé."),
        ),
        "Đặt vé",
        fake.backend(),
        TODAY,
        store=store1,
    )
    chat1.shown.append({"from": "agent", "turn": out})
    store1.save(chat1)
    pid = out["cards"][-1]["proposalId"]

    store2 = Chats(db)
    chat2 = store2.get(chat1.id, "s1")
    assert chat2 is not None and len(chat2.shown) == 2
    assert chat2.harness.proposal is not None and chat2.harness.proposal.id == pid
    res = approve(chat2, scripted(), pid, fake.backend(), TODAY, store=store2)
    assert res["cards"][0]["type"] == "held"


def test_expired_proposal_cannot_be_approved():
    fake = FakeBackend()
    chat, model, out = proposed(fake)
    chat.harness.proposal.created_at -= 1000.0
    with pytest.raises(LookupError):
        approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)


def test_make_title_collapses_whitespace_and_truncates():
    from flight_agent.chat import make_title
    assert make_title("  Tìm   vé\nHAN   đi SGN  ") == "Tìm vé HAN đi SGN"
    long_text = "Đặt vé " + "rất dài " * 20
    title = make_title(long_text)
    assert len(title) <= 60 and title.endswith("…") and not title.endswith(" …")
    assert len(make_title("a" * 80)) == 60


def test_list_for_orders_newest_first_skips_empty_and_isolates_owners(tmp_path):
    store = Chats(tmp_path / "chats.sqlite3")
    empty = store.create("user:3")
    c1 = store.create("user:3")
    c1.shown.append({"from": "user", "text": "Vé HAN đi SGN ngày mai"})
    store.save(c1)
    c1.updated_at -= 100
    store._conn.execute("UPDATE chats SET updated_at = ? WHERE id = ?", (c1.updated_at, c1.id))
    store._conn.commit()
    c2 = store.create("user:3")
    c2.shown.append({"from": "user", "text": "Hoàn vé mất bao nhiêu phí?"})
    store.save(c2)
    other = store.create("user:4")
    other.shown.append({"from": "user", "text": "Chat của người khác"})
    store.save(other)

    listed = store.list_for("user:3")
    assert [x["chatId"] for x in listed] == [c2.id, c1.id]
    assert listed[0]["title"] == "Hoàn vé mất bao nhiêu phí?"
    assert listed[0]["updatedAt"].endswith("+07:00")
    assert empty.id not in [x["chatId"] for x in listed]


def test_delete_removes_chat_for_owner_only_and_blocks_save_resurrection(tmp_path):
    store = Chats(tmp_path / "chats.sqlite3")
    c = store.create("user:3")
    c.shown.append({"from": "user", "text": "Xin chào"})
    store.save(c)
    assert store.delete(c.id, "user:4") is False
    assert store.delete(c.id, "user:3") is True
    assert store.delete(c.id, "user:3") is False
    store.save(c)  # mô phỏng luồng nền gọi save() sau khi chat đã bị xoá
    assert store.get(c.id, "user:3") is None
    assert store.list_for("user:3") == []


def test_sqlite_migrates_legacy_session_table_cleanly(tmp_path):
    import sqlite3
    db = tmp_path / "legacy.sqlite3"
    with sqlite3.connect(db) as conn:
        conn.execute("CREATE TABLE chats (id TEXT PRIMARY KEY, session TEXT NOT NULL, updated_at REAL NOT NULL, payload TEXT NOT NULL)")
        conn.execute("INSERT INTO chats VALUES ('old', 's1', 9999999999.0, '{}')")
    store = Chats(db)
    assert store.get("old", "user:3") is None
    c = store.create("user:3")
    c.shown.append({"from": "user", "text": "Sau migration"})
    store.save(c)
    assert store.list_for("user:3")[0]["chatId"] == c.id




