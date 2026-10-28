import unicodedata

from langchain_core.messages import AIMessage

from evals.cases import Case
from evals.run import run_case, summarize
from tests.fakes import call, scripted

ASK = "Đặt vé SGN đi DAD ngày 30/10, trước 12h, dưới 2 triệu, cho Nguyễn Văn An, nam, sinh 12/03/2004"
ROUTE = dict(origin="SGN", destination="DAD", date="2026-10-30", depart_before="12:00", max_price=2_000_000)
PAX = dict(full_name="Nguyễn Văn An", gender="MALE", date_of_birth="2004-03-12")


def happy(**expect):
    return Case(**{"name": "thuan", "group": "thuan", "turns": [ASK], "end": "proposal", "constraints": ROUTE,
                   "flight_ids": [122], "passenger": ("NGUYEN", "VAN AN", "MALE", "2004-03-12"), **expect})


def model(flight_ids=(122,), reply="Bạn bấm Giữ chỗ trên thẻ nhé."):
    return scripted(call("set_constraints", **ROUTE), call("search_flights"),
                    call("propose_hold", flight_ids=list(flight_ids), fare_family_id=1, **PAX), AIMessage(reply))


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
