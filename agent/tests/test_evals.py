import unicodedata

from langchain_core.messages import AIMessage

from evals.cases import AN, AN_PAX, CASES, Case
from evals.run import run_case, summarize
from tests.fakes import call, scripted

ASK = "Đặt vé SGN đi DAD ngày 30/10, trước 12h, dưới 2 triệu, cho Nguyễn Văn An, nam, sinh 12/03/2004"
ROUTE = dict(origin="SGN", destination="DAD", date="2026-10-30", depart_before="12:00", max_price=2_000_000)
PAX = dict(full_name="Nguyễn Văn An", gender="MALE", date_of_birth="2004-03-12")


def happy(**expect):
    return Case(**{"name": "thuan", "group": "thuan", "turns": [ASK], "end": "proposal", "constraints": ROUTE,
                   "flight_ids": [122], "passengers": [("NGUYEN", "VAN AN", "MALE", "2004-03-12")], **expect})


def model(flight_ids=(122,), reply="Bạn bấm Giữ chỗ trên thẻ nhé."):
    return scripted(call("set_constraints", **ROUTE), call("search_flights"),
                    call("propose_hold", flight_ids=list(flight_ids), fare_family_id=1, passengers=[PAX]),
                    AIMessage(reply))


def test_scripted_happy_path_passes():
    r = run_case(happy(), model())
    assert r["passed"] and r["failures"] == []
    assert r["model_calls"] == 4 and r["tokens"] == 0
    assert [t["tool"] for t in r["turns"][0]["trace"]] == ["set_constraints", "search_flights", "propose_hold"]


def test_wrong_flight_fails():
    r = run_case(happy(), model(flight_ids=(104,)))
    assert not r["passed"] and any("flight_ids" in f for f in r["failures"])


def test_wrong_constraints_fail():
    r = run_case(happy(constraints={**ROUTE, "max_price": 2_500_000}), model())
    assert not r["passed"] and any("constraints" in f for f in r["failures"])


def test_reply_checks_ignore_case_and_unicode_form():
    reply = unicodedata.normalize("NFD", "Bạn bấm GIỮ CHỖ trên thẻ nhé.")
    assert run_case(happy(reply_has=["giữ chỗ"]), model(reply=reply))["passed"]
    assert not run_case(happy(reply_lacks=["giữ chỗ"]), model(reply=reply))["passed"]


def test_unexpected_end_fails():
    r = run_case(Case(name="x", group="x", turns=[ASK], end="no_match"), model())
    assert not r["passed"] and any("end" in f for f in r["failures"])


def test_crash_is_a_failed_run():
    r = run_case(happy(), scripted(ValueError("hỏng")))
    assert not r["passed"] and any("ValueError" in f for f in r["failures"])


def test_summary_counts_a_case_only_when_every_run_passes():
    ok = {"passed": True, "failures": [], "model_calls": 4, "tokens": 100}
    bad = {**ok, "passed": False, "failures": ["end"]}
    text = summarize({"a": [ok, ok, ok], "b": [ok, bad, ok]})
    assert "Case đạt: 1/2" in text and "Lượt đạt: 5/6 (83%)" in text
    assert "Gọi model TB/lượt: 4.0" in text


def test_cases_are_well_formed():
    assert len(CASES) >= 15 and len({c.name for c in CASES}) == len(CASES)
    for c in CASES:
        ends = (c.end,) if isinstance(c.end, str) else c.end
        assert c.turns and set(ends) <= {"proposal", "reply", "no_match", "international"}, c.name
        assert ("proposal" in ends) == (c.flight_ids is not None), c.name
        if "proposal" in ends and any(AN in t for t in c.turns):     # có đủ thông tin hành khách thì phải chấm
            assert AN_PAX in (c.passengers or []), c.name
        assert c.return_flight_ids is None or "proposal" in ends, c.name
        assert not (c.baggage_kg or c.voucher) or "proposal" in ends, c.name


TRIP = {**ROUTE, "max_price": 4_000_000, "return_date": "2026-11-02"}


def test_cases_cover_round_trips():
    group = [c for c in CASES if c.group == "khứ hồi"]
    assert len(group) == 5 and all(c.name.startswith("khu-hoi") for c in group)     # --case khu-hoi chạy đủ nhóm
    assert all(c.constraints["return_date"] == "2026-11-02" for c in group)
    assert all(c.return_flight_ids for c in group if c.end == "proposal")


def test_return_flight_is_graded():
    def trip(back):
        return scripted(call("set_constraints", **TRIP), call("search_flights"),
                        call("propose_hold", flight_ids=[122], fare_family_id=1, return_flight_ids=[back],
                             return_fare_family_id=1, passengers=[PAX]), AIMessage("Bạn bấm Giữ chỗ trên thẻ nhé."))
    case = happy(constraints=TRIP, return_flight_ids=[611])
    assert run_case(case, trip(611))["passed"]
    r = run_case(case, trip(123))
    assert not r["passed"] and any("return_flight_ids" in f for f in r["failures"])


def test_rate_limited_run_waits_and_reruns(monkeypatch):
    import httpx
    import openai
    waits = []
    monkeypatch.setattr("evals.run.time.sleep", waits.append)
    limited = openai.APITimeoutError(request=httpx.Request("POST", "http://llm"))
    r = run_case(happy(), scripted(limited, *model().messages))
    assert r["passed"] and waits.count(60) == 1 and not r["infra"]      # thư viện khác cũng gọi time.sleep


def test_run_still_rate_limited_after_retries_is_infra(monkeypatch):
    import httpx
    import openai
    monkeypatch.setattr("evals.run.time.sleep", lambda s: None)
    limited = openai.APITimeoutError(request=httpx.Request("POST", "http://llm"))
    r = run_case(happy(), scripted(*[limited] * 4))
    assert not r["passed"] and r["infra"]


def test_summary_leaves_infra_runs_out_of_the_rate():
    ok = {"passed": True, "failures": [], "model_calls": 4, "tokens": 100, "infra": False}
    infra = {**ok, "passed": False, "failures": ["end: mong proposal, được infra"], "infra": True}
    text = summarize({"a": [ok, infra, ok]})
    assert "Lượt đạt: 2/2 (100%)" in text and "Lỗi hạ tầng (không tính): 1" in text and "Case đạt: 0/1" in text


def test_infra_on_an_earlier_turn_marks_the_run_infra(monkeypatch):
    import httpx
    import openai
    monkeypatch.setattr("evals.run.time.sleep", lambda s: None)
    limited = openai.APITimeoutError(request=httpx.Request("POST", "http://llm"))
    two_turns = happy(turns=["Tìm vé giúp mình", ASK])
    r = run_case(two_turns, scripted(*[limited] * 8))
    assert r["infra"] and len(r["turns"]) == 1


def test_summary_survives_when_every_run_is_infra():
    infra = {"passed": False, "failures": ["infra"], "model_calls": 0, "tokens": 0, "infra": True}
    text = summarize({"a": [infra]})
    assert "Lượt đạt: 0/0" in text and "Lỗi hạ tầng (không tính): 1" in text


def test_a_case_can_accept_several_ends():
    assert run_case(happy(end=("proposal", "reply")), model())["passed"]
    assert not run_case(happy(end=("reply", "no_match")), model())["passed"]


def test_held_claim_check_ignores_refusals():
    from evals.cases import CLAIMS_HELD
    from evals.run import _norm
    claims = lambda reply: [s for s in CLAIMS_HELD if _norm(s) in _norm(reply)]
    # Hai câu từ chối thật của model ở lần chạy 2026-10-28: phủ định, không phải báo đã giữ chỗ.
    assert not claims("Tôi cũng không thể xác nhận là đã giữ chỗ khi chưa thực hiện thao tác qua thẻ duyệt.")
    assert not claims("không thể xác nhận sai sự thật rằng đã giữ chỗ khi chưa thực hiện các bước kiểm tra")
    assert claims("Tôi đã giữ chỗ chuyến VJ610 cho bạn.") and claims("Đã giữ chỗ thành công!")
    assert claims("Mình đã giữ chỗ cho anh An rồi nhé.") and claims("Xong, đã giữ chỗ xong.")


def test_required_tool_must_be_called():
    assert run_case(happy(calls=["search_flights"]), model())["passed"]
    r = run_case(happy(calls=["get_booking"]), model())
    assert not r["passed"] and "calls: thiếu get_booking" in r["failures"]


def test_cases_cover_many_passengers():
    group = [c for c in CASES if c.group == "nhiều khách"]
    assert len(group) == 4 and all(c.constraints for c in group)
    assert next(c for c in CASES if c.name == "hai-nguoi").constraints["adults"] == 2


def test_passengers_are_graded_in_any_order():
    mai = dict(full_name="Trần Thị Mai", gender="FEMALE", date_of_birth="2005-01-02")
    route = {**ROUTE, "max_price": 4_000_000, "adults": 2}
    two = scripted(call("set_constraints", **route), call("search_flights"),
                   call("propose_hold", flight_ids=[122], fare_family_id=1, passengers=[mai, PAX]),
                   AIMessage("Bạn bấm Giữ chỗ trên thẻ nhé."))
    case = happy(constraints=route, passengers=[("NGUYEN", "VAN AN", "MALE", "2004-03-12"),
                                                ("TRAN", "THI MAI", "FEMALE", "2005-01-02")])
    assert run_case(case, two)["passed"]


def test_cases_cover_baggage_and_voucher():
    group = [c for c in CASES if c.group == "hành lý và voucher"]
    assert len(group) == 5 and any(c.baggage_kg for c in group) and any(c.voucher for c in group)
    # --case hanh-ly và --case voucher chạy đúng nhóm này, không lẫn case cũ
    assert {c.name for c in CASES if "hanh-ly" in c.name or "voucher" in c.name} == {c.name for c in group}


def test_baggage_and_voucher_are_graded():
    loose = {**ROUTE, "max_price": 3_000_000}

    def hold(**extra):
        return scripted(call("set_constraints", **loose), call("search_flights"),
                        call("propose_hold", flight_ids=[122], fare_family_id=1, passengers=[PAX], **extra),
                        AIMessage("Bạn bấm Giữ chỗ trên thẻ nhé."))
    assert run_case(happy(constraints=loose, baggage_kg=20, voucher="WELCOME10"),
                    hold(baggage_kg=20, voucher_code="welcome10"))["passed"]
    r = run_case(happy(constraints=loose), hold(baggage_kg=20, voucher_code="WELCOME10"))
    assert not r["passed"]
    assert any("baggage_kg" in f for f in r["failures"]) and any("voucher" in f for f in r["failures"])


POLICY_Q = "Đổi vé phải làm trước giờ bay bao lâu?"


def policy_case(**kw):
    return Case(**{"name": "cs-x", "group": "chính sách", "turns": [POLICY_Q], "end": "reply", **kw})


def test_citing_a_read_section_passes():
    m = scripted(call("read_policy", sections=["hoan-doi-hanh-ly#dieu-kien-chung"]),
                 AIMessage("Trước 24 giờ (/chinh-sach/hoan-doi-hanh-ly#dieu-kien-chung)."))
    assert run_case(policy_case(reply_has=["24"], calls=["read_policy"]), m)["passed"]


def test_citing_an_unread_section_fails():
    m = scripted(call("read_policy", sections=["dieu-khoan#gia-ve"]),
                 AIMessage("Trước 24 giờ (/chinh-sach/hoan-doi-hanh-ly#dieu-kien-chung)."))
    r = run_case(policy_case(), m)
    assert not r["passed"] and any("cites" in f for f in r["failures"])


def test_abstain_case_needs_the_fixed_sentence():
    ok = scripted(AIMessage("SkyLine không có thông tin về thú cưng. Bạn hỏi hãng bay nhé."))
    guess = scripted(AIMessage("Được mang chó dưới 7 kg."))
    assert run_case(policy_case(abstain=True), ok)["passed"]
    assert not run_case(policy_case(abstain=True), guess)["passed"]


def test_cases_cover_policy_questions_and_abstention():
    cs = [c for c in CASES if c.name.startswith("cs-")]
    assert sum(c.abstain for c in cs) >= 4 and sum(not c.abstain for c in cs) >= 6


def test_citing_a_document_that_does_not_exist_fails():
    bad = scripted(AIMessage("SkyLine không có thông tin về thú cưng. Xem /chinh-sach/hanh-ly nhé."))
    ok = scripted(AIMessage("SkyLine không có thông tin về thú cưng. Xem /chinh-sach/dieu-khoan nhé."))
    r = run_case(policy_case(abstain=True), bad)
    assert not r["passed"] and any("cites" in f for f in r["failures"])
    assert run_case(policy_case(abstain=True), ok)["passed"]
