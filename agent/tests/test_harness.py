from flight_agent.core import Harness
from tests.fakes import FakeBackend, passenger

ROUTE = dict(origin="SGN", destination="DAD", date="2026-10-30", depart_before="12:00", max_price=2_000_000)
PAX = dict(full_name="Nguyễn Hùng Cường", gender="MALE", date_of_birth="2004-03-12")


def ready(fake=None):
    fake = fake or FakeBackend()
    h = Harness()
    h.begin_turn(fake.backend(), "2026-10-26")
    return h, fake


def searched(fake=None):
    h, fake = ready(fake)
    h.execute("set_constraints", ROUTE)
    h.execute("search_flights", {})
    return h, fake


def test_search_and_hold_are_denied_before_constraints():
    h, _ = ready()
    assert h.execute("search_flights", {})["status"] == "denied"
    assert h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, **PAX))["status"] == "denied"


def test_set_constraints_normalises_and_shows_a_card():
    h, _ = ready()
    r = h.execute("set_constraints", {**ROUTE, "depart_before": "9:00", "max_price": "2.000.000"})
    assert r["status"] == "ok"
    assert h.constraints.depart_before == "09:00" and h.constraints.max_price == 2_000_000
    assert h.cards[-1]["type"] == "constraints"


def test_unknown_airport_and_past_date_are_invalid():
    h, _ = ready()
    assert h.execute("set_constraints", {**ROUTE, "destination": "XXX"})["status"] == "invalid_param"
    assert h.execute("set_constraints", {**ROUTE, "date": "2026-10-25"})["status"] == "invalid_param"


def test_international_route_hands_off():
    h, _ = ready()
    h.execute("set_constraints", {**ROUTE, "destination": "BKK"})
    assert h.stop_reason == "international"


def test_search_splits_matching_and_nearest():
    h, _ = ready()
    h.execute("set_constraints", ROUTE)
    r = h.execute("search_flights", {})
    assert [i["flights"] for i in r["matching"]] == ["VN122", "QH104"]           # rẻ nhất trước
    assert {i["flights"] for i in r["nearest"]} == {"VJ610", "VJ604"}            # mỗi chuyến chỉ phạm 1 ràng buộc
    assert h.stop_reason is None


def test_no_matching_flight_stops_with_the_nearest_in_the_question():
    h, _ = ready()
    h.execute("set_constraints", {**ROUTE, "max_price": 1_000_000})
    h.execute("search_flights", {})
    assert h.stop_reason == "no_match"
    assert "VN122" in h.handoff()["question"]


def test_hold_on_a_flight_that_breaks_constraints_is_denied():
    h, fake = searched()
    r = h.execute("propose_hold", dict(flight_ids=[610], fare_family_id=1, **PAX))
    assert r["status"] == "denied"
    assert h.proposal is None and not [c for c in h.cards if c["type"] == "proposal"]
    assert not [q for q in fake.requests if q.url.path == "/api/bookings/quote"]


def test_hold_on_a_flight_not_in_the_search_is_denied():
    h, _ = searched()
    assert h.execute("propose_hold", dict(flight_ids=[999], fare_family_id=1, **PAX))["status"] == "denied"


def test_valid_hold_quotes_and_waits_for_approval():
    h, fake = searched()
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, **PAX))
    assert r["status"] == "pending_approval"
    body = h.proposal.body
    assert body["journeys"] == [{"direction": "OUTBOUND", "flightIds": [122], "fareFamilyId": 1}]
    assert (body["passengers"][0]["lastName"], body["passengers"][0]["firstName"]) == ("NGUYEN", "HUNG CUONG")
    assert body["passengers"][0]["type"] == "ADULT" and body["baggage"] == []
    assert body["contact"] == {"name": "Nguyễn Hùng Cường", "email": "customer@demo.local", "phone": "0901234567"}
    assert h.proposal.total == 1_850_000
    assert h.cards[-1]["type"] == "proposal" and h.cards[-1]["proposalId"] == h.proposal.id
    assert not [q for q in fake.requests if q.url.path == "/api/bookings"]


def test_one_word_name_is_invalid():
    h, _ = searched()
    assert h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, full_name="Cường",
                                          gender="MALE", date_of_birth="2004-03-12"))["status"] == "invalid_param"


def test_changing_constraints_drops_the_pending_proposal():
    h, _ = searched()
    h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, **PAX))
    h.execute("set_constraints", {**ROUTE, "max_price": 1_900_000})
    assert h.proposal is None and h.found == []


def test_backend_validation_error_goes_back_to_the_model():
    h, _ = searched(FakeBackend(quote_error=(400, "PASSENGER_RULES_VIOLATED",
                                             [{"field": "passengers[0].dateOfBirth", "message": "Sai loại khách"}])))
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, **PAX))
    assert r["status"] == "backend_error" and r["code"] == "PASSENGER_RULES_VIOLATED"
    assert h.stop_reason is None


def test_passport_error_hands_off_as_international():
    h, _ = searched(FakeBackend(quote_error=(400, "PASSENGER_RULES_VIOLATED",
                                             [{"field": "passengers[0].passportNumber", "message": "Cần hộ chiếu"}])))
    h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, **PAX))
    assert h.stop_reason == "international"


def test_expired_session_and_staff_account_hand_off():
    for status, reason in ((401, "unauthenticated"), (403, "forbidden")):
        h, _ = searched(FakeBackend(me_status=status))
        h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, **PAX))
        assert h.stop_reason == reason


def test_handoff_has_one_question_and_the_trace():
    h, _ = ready()
    h.execute("set_constraints", {**ROUTE, "max_price": 1_000_000})
    h.execute("search_flights", {})
    card = h.handoff()
    assert card["type"] == "handoff" and card["question"].count("?") == 1
    assert card["tried"][-1].startswith("search_flights")


CUONG = passenger("NGUYEN", "HUNG CUONG")
PAST = [("AAA111", [CUONG, passenger("TRAN", "THI MAI", "FEMALE", "2005-01-02")]),
        ("BBB222", [CUONG, passenger("NGUYEN", "BE NA", type="CHILD", dob="2020-05-05")]),
        ("CCC333", [passenger("LE", "VAN AN", dob="1990-09-09")]),
        ("DDD444", [passenger("PHAM", "MINH", dob="1980-01-01")])]


def test_recent_passengers_are_unique_adults_without_documents():
    h, _ = ready(FakeBackend(past=PAST))
    r = h.execute("list_recent_passengers", {})      # không cần ràng buộc trước
    assert r == {"status": "ok", "passengers": [
        {"full_name": "NGUYEN HUNG CUONG", "gender": "MALE", "date_of_birth": "2004-03-12"},
        {"full_name": "TRAN THI MAI", "gender": "FEMALE", "date_of_birth": "2005-01-02"},
        {"full_name": "LE VAN AN", "gender": "MALE", "date_of_birth": "1990-09-09"}]}


def test_recent_passengers_reads_at_most_three_bookings_with_get_only():
    h, fake = ready(FakeBackend(past=PAST))
    h.execute("list_recent_passengers", {})
    assert all(q.method == "GET" for q in fake.requests) and len(fake.requests) == 4
    assert fake.requests[0].url.params["size"] == "3"
    assert "/api/bookings/DDD444" not in [q.url.path for q in fake.requests]


def test_no_past_booking_gives_an_empty_list():
    h, _ = ready()
    assert h.execute("list_recent_passengers", {}) == {"status": "ok", "passengers": []}


def test_reused_passenger_goes_into_the_hold_unchanged():
    h, _ = searched(FakeBackend(past=PAST))
    p = h.execute("list_recent_passengers", {})["passengers"][0]
    h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, **p))
    sent = h.proposal.body["passengers"][0]
    assert (sent["lastName"], sent["firstName"], sent["gender"], sent["dateOfBirth"]) == \
        ("NGUYEN", "HUNG CUONG", "MALE", "2004-03-12")
