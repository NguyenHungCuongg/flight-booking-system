import json

from flight_agent.core import Harness
from tests.fakes import BAGGAGE, FakeBackend, booking, journey, passenger

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
    assert h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX]))["status"] == "denied"


def test_set_constraints_normalises_and_shows_a_card():
    h, _ = ready()
    r = h.execute("set_constraints", {**ROUTE, "depart_before": "9:00", "max_price": "2.000.000"})
    assert r["status"] == "ok"
    assert h.constraints.depart_before == "09:00" and h.constraints.max_price == 2_000_000
    assert h.cards[-1]["type"] == "constraints"


def test_set_constraints_takes_passenger_counts():
    h, _ = ready()
    assert h.execute("set_constraints", {**ROUTE, "adults": "2", "infants": 1})["status"] == "ok"
    assert (h.constraints.adults, h.constraints.children, h.constraints.infants) == (2, 0, 1)
    assert (h.cards[-1]["adults"], h.cards[-1]["children"], h.cards[-1]["infants"]) == (2, 0, 1)


def test_bad_passenger_counts_are_invalid():
    h, _ = ready()
    for bad in ({"adults": 0}, {"children": -1}, {"infants": "một"}):
        assert h.execute("set_constraints", {**ROUTE, **bad})["status"] == "invalid_param"
    assert h.constraints is None


def test_search_sends_the_passenger_counts_and_prices_the_whole_booking():
    h, fake = ready()
    h.execute("set_constraints", {**ROUTE, "max_price": 4_000_000, "adults": 2, "children": 1})
    r = h.execute("search_flights", {})
    q = next(q for q in fake.requests if q.url.path == "/api/flights/search")
    assert (q.url.params["adults"], q.url.params["children"], q.url.params["infants"]) == ("2", "1", "0")
    # VN122 1.850.000 x (2 + 0,9) = 5.365.000 vượt 4 triệu; không còn chuyến nào trước 12h thoả
    assert r["matching"] == [] and h.stop_reason == "no_match"


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
    r = h.execute("propose_hold", dict(flight_ids=[610], fare_family_id=1, passengers=[PAX]))
    assert r["status"] == "denied"
    assert h.proposal is None and not [c for c in h.cards if c["type"] == "proposal"]
    assert not [q for q in fake.requests if q.url.path == "/api/bookings/quote"]


def test_hold_on_a_flight_not_in_the_search_is_denied():
    h, _ = searched()
    assert h.execute("propose_hold", dict(flight_ids=[999], fare_family_id=1, passengers=[PAX]))["status"] == "denied"


def test_valid_hold_quotes_and_waits_for_approval():
    h, fake = searched()
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX]))
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
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[{**PAX, "full_name": "Cường"}]))
    assert r["status"] == "invalid_param"


FAMILY = [PAX, dict(full_name="Nguyễn Bảo Ngọc", gender="FEMALE", date_of_birth="2018-06-01"),
          dict(full_name="Nguyễn Minh Khang", gender="MALE", date_of_birth="2026-02-10")]


def family():
    h, fake = ready()
    h.execute("set_constraints", {**ROUTE, "max_price": 5_000_000, "children": 1, "infants": 1})
    h.execute("search_flights", {})
    return h, fake


def test_family_hold_types_each_passenger_and_shows_fares():
    h, _ = family()
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=FAMILY))
    assert (r["status"], r["total"]) == ("pending_approval", 1_850_000 + 1_665_000 + 185_000)
    assert [p["type"] for p in h.proposal.body["passengers"]] == ["ADULT", "CHILD", "INFANT"]
    card = h.cards[-1]
    assert [(p["lastName"], p["firstName"], p["type"]) for p in card["passengers"]] == [
        ("NGUYEN", "HUNG CUONG", "ADULT"), ("NGUYEN", "BAO NGOC", "CHILD"), ("NGUYEN", "MINH KHANG", "INFANT")]
    assert [(f["type"], f["count"], f["unitPrice"]) for f in card["journeys"][0]["fares"]] == [
        ("ADULT", 1, 1_850_000), ("CHILD", 1, 1_665_000), ("INFANT", 1, 185_000)]


def test_passengers_not_matching_the_counts_are_invalid():
    h, fake = searched()                                   # ràng buộc 1 người lớn
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=FAMILY[:2]))
    assert r["status"] == "invalid_param" and "1 người lớn, 1 trẻ em, 0 em bé" in r["hint"]
    assert not [q for q in fake.requests if q.url.path == "/api/bookings/quote"]


def test_bad_birth_date_names_the_passenger():
    h, _ = searched()
    for dob in ("12/03/2004", "2026-10-31"):
        r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1,
                                           passengers=[{**PAX, "date_of_birth": dob}]))
        assert r["status"] == "invalid_param" and "Hành khách 1" in r["hint"]


def test_no_passenger_is_invalid():
    h, _ = searched()
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[]))
    assert r["status"] == "invalid_param"


def test_changing_constraints_drops_the_pending_proposal():
    h, _ = searched()
    h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX]))
    h.execute("set_constraints", {**ROUTE, "max_price": 1_900_000})
    assert h.proposal is None and h.found == []


def test_backend_validation_error_goes_back_to_the_model():
    h, _ = searched(FakeBackend(quote_error=(400, "PASSENGER_RULES_VIOLATED",
                                             [{"field": "passengers[0].dateOfBirth", "message": "Sai loại khách"}])))
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX]))
    assert r["status"] == "backend_error" and r["code"] == "PASSENGER_RULES_VIOLATED"
    assert h.stop_reason is None


def test_passport_error_hands_off_as_international():
    h, _ = searched(FakeBackend(quote_error=(400, "PASSENGER_RULES_VIOLATED",
                                             [{"field": "passengers[0].passportNumber", "message": "Cần hộ chiếu"}])))
    h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX]))
    assert h.stop_reason == "international"


def test_expired_session_and_staff_account_hand_off():
    for status, reason in ((401, "unauthenticated"), (403, "forbidden")):
        h, _ = searched(FakeBackend(me_status=status))
        h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX]))
        assert h.stop_reason == reason


def test_handoff_has_one_question_and_the_trace():
    h, _ = ready()
    h.execute("set_constraints", {**ROUTE, "max_price": 1_000_000})
    h.execute("search_flights", {})
    card = h.handoff()
    assert card["type"] == "handoff" and card["question"].count("?") == 1
    assert card["tried"][-1].startswith("search_flights")


TRIP = {**ROUTE, "max_price": 4_000_000, "return_date": "2026-11-02"}
ANY_TIME = {"depart_before": None, "max_price": None}


def test_set_constraints_takes_a_return_date():
    h, _ = ready()
    assert h.execute("set_constraints", TRIP)["status"] == "ok"
    assert h.constraints.return_date == "2026-11-02" and h.cards[-1]["returnDate"] == "2026-11-02"
    h.execute("set_constraints", ROUTE)
    assert h.constraints.return_date is None and h.cards[-1]["returnDate"] is None


def test_return_before_departure_or_malformed_is_invalid():
    h, _ = ready()
    for bad in ("2026-10-29", "02/11/2026"):
        r = h.execute("set_constraints", {**TRIP, "return_date": bad})
        assert r["status"] == "invalid_param" and "không trước ngày đi 2026-10-30" in r["hint"]
    assert h.constraints is None
    h.execute("set_constraints", TRIP)
    h.execute("set_constraints", {**TRIP, "return_date": "2026-10-29"})
    assert h.constraints.return_date == "2026-11-02"                     # ràng buộc cũ giữ nguyên


def test_round_trip_search_pairs_both_directions_and_prices_the_total():
    h, fake = ready()
    h.execute("set_constraints", TRIP)
    r = h.execute("search_flights", {})
    searches = [(q.url.params["from"], q.url.params["to"], q.url.params["date"])
                for q in fake.requests if q.url.path == "/api/flights/search"]
    assert searches == [("SGN", "DAD", "2026-10-30"), ("DAD", "SGN", "2026-11-02")]
    assert [(i["outbound"]["flights"], i["return"]["flights"], i["price"]) for i in r["matching"]] == [
        ("VN122", "VJ611", 2_900_000), ("QH104", "VJ611", 2_970_000), ("VJ604", "VJ611", 3_530_000),
        ("VN122", "VN123", 3_600_000), ("QH104", "VN123", 3_670_000)]
    assert r["matching"][0]["return"]["fare_family_id"] == 1 and "broken" not in r["matching"][0]["return"]


def test_matching_shows_at_most_five():
    h, _ = ready()
    h.execute("set_constraints", {**TRIP, **ANY_TIME})
    assert len(h.execute("search_flights", {})["matching"]) == 5         # 4 chiều đi x 2 chiều về = 8 cặp
    assert len(h.found) == 8


def test_same_day_return_pairs_only_flights_that_leave_after_landing():
    h, _ = ready()
    h.execute("set_constraints", {**TRIP, **ANY_TIME, "return_date": "2026-10-30"})
    h.execute("search_flights", {})
    pairs = {(i["outbound"]["flights"], i["return"]["flights"]) for i in h.found}
    assert ("VN122", "VN127") in pairs and ("VJ610", "VN127") not in pairs   # VJ610 hạ cánh 15:40, VN127 đi 12:00


def test_round_trip_without_a_match_names_both_flights_of_the_nearest():
    h, _ = ready()
    h.execute("set_constraints", {**TRIP, **ANY_TIME, "max_price": 2_000_000})
    h.execute("search_flights", {})
    q = h.handoff()["question"]
    assert h.stop_reason == "no_match" and "VJ610" in q and "VJ611" in q and "2.200.000" in q


def test_no_return_flight_that_day_names_both_dates():
    h, _ = ready()
    h.execute("set_constraints", {**TRIP, "return_date": "2026-11-03"})
    h.execute("search_flights", {})
    q = h.handoff()["question"]
    assert h.stop_reason == "no_match" and "2026-10-30" in q and "2026-11-03" in q


ROUND = dict(flight_ids=[122], fare_family_id=1, return_flight_ids=[611], return_fare_family_id=1)


def round_trip():
    h, fake = ready()
    h.execute("set_constraints", TRIP)
    h.execute("search_flights", {})
    return h, fake


def test_round_trip_hold_quotes_both_journeys_and_shows_each():
    h, _ = round_trip()
    r = h.execute("propose_hold", {**ROUND, "passengers": [PAX]})
    assert (r["status"], r["total"]) == ("pending_approval", 2_900_000)
    assert h.proposal.body["journeys"] == [{"direction": "OUTBOUND", "flightIds": [122], "fareFamilyId": 1},
                                           {"direction": "RETURN", "flightIds": [611], "fareFamilyId": 1}]
    assert [(j["direction"], j["segments"][0]["flightNumber"], j["fare"], j["fares"][0]["total"])
            for j in h.cards[-1]["journeys"]] == [("OUTBOUND", "VN122", "Phổ thông", 1_850_000),
                                                  ("RETURN", "VJ611", "Phổ thông", 1_050_000)]


def test_hold_needs_exactly_the_listed_journeys():
    h, fake = round_trip()
    for bad in ({**ROUND, "return_flight_ids": None, "return_fare_family_id": None},     # thiếu chiều về
                {**ROUND, "return_fare_family_id": None},                               # nửa tham số chiều về
                {**ROUND, "flight_ids": [610]}):                                         # VJ610 phạm giờ bay
        assert h.execute("propose_hold", {**bad, "passengers": [PAX]})["status"] == "denied"
    assert not [q for q in fake.requests if q.url.path == "/api/bookings/quote"]
    h, _ = searched()                                                                    # ràng buộc một chiều
    assert h.execute("propose_hold", {**ROUND, "passengers": [PAX]})["status"] == "denied"


def test_denied_hold_hint_names_the_real_cause():
    h, _ = searched()                       # một chiều: model điền tham số chiều về rỗng thay vì bỏ trống
    r = h.execute("propose_hold", {"flight_ids": [122], "fare_family_id": 1, "return_flight_ids": [],
                                   "return_fare_family_id": 0, "passengers": [PAX]})
    assert r["status"] == "denied" and "Một chiều: không truyền return_flight_ids" in r["hint"]
    h, _ = round_trip()
    r = h.execute("propose_hold", {**ROUND, "return_flight_ids": [999], "passengers": [PAX]})
    assert r["status"] == "denied" and "chiều về [999]" in r["hint"]


PRICES = ["15 kg: 250.000 đ", "20 kg: 350.000 đ", "25 kg: 450.000 đ", "30 kg: 550.000 đ"]


def test_hold_offers_baggage_prices_per_direction():
    h, fake = round_trip()                                   # VN122 đi, VJ611 về: hai hãng
    r = h.execute("propose_hold", {**ROUND, "passengers": [PAX]})
    assert r["baggage_options"] == {"OUTBOUND": PRICES, "RETURN": PRICES}
    assert [j["baggage"] for j in h.cards[-1]["journeys"]] == [None, None]
    paths = {q.url.path for q in fake.requests}
    assert {"/api/airlines/VN/baggage-options", "/api/airlines/VJ/baggage-options"} <= paths


def test_only_weights_sold_on_every_direction_are_offered(monkeypatch):
    monkeypatch.setitem(BAGGAGE, "VJ", BAGGAGE["VJ"][:2])      # hãng chiều về chỉ bán 15 và 20 kg
    h, _ = round_trip()
    r = h.execute("propose_hold", {**ROUND, "passengers": [PAX]})
    assert r["baggage_options"] == {"OUTBOUND": PRICES[:2], "RETURN": PRICES[:2]}
    monkeypatch.setitem(BAGGAGE, "VJ", [])                     # hãng chiều về không bán hành lý
    h, _ = round_trip()
    r = h.execute("propose_hold", {**ROUND, "passengers": [PAX]})
    assert r["status"] == "pending_approval" and "baggage_options" not in r
    r = h.execute("propose_hold", {**ROUND, "passengers": [PAX], "baggage_kg": 20})
    assert r["status"] == "invalid_param" and "không có mức hành lý" in r["hint"]


def test_baggage_goes_to_every_adult_and_child_on_every_direction():
    h, _ = ready()
    h.execute("set_constraints", {**TRIP, "max_price": 8_000_000, "children": 1, "infants": 1})
    h.execute("search_flights", {})
    r = h.execute("propose_hold", {**ROUND, "passengers": FAMILY, "baggage_kg": 20})
    assert (r["status"], r["total"]) == ("pending_approval", 5_800_000 + 4 * 350_000) and "baggage_options" not in r
    assert h.proposal.body["baggage"] == [                   # em bé (BR-22) không mua
        {"passengerIndex": 0, "direction": "OUTBOUND", "baggageOptionId": 22},
        {"passengerIndex": 1, "direction": "OUTBOUND", "baggageOptionId": 22},
        {"passengerIndex": 0, "direction": "RETURN", "baggageOptionId": 12},
        {"passengerIndex": 1, "direction": "RETURN", "baggageOptionId": 12}]
    assert [j["baggage"] for j in h.cards[-1]["journeys"]] == [{"weightKg": 20, "count": 2, "total": 700_000}] * 2


def test_baggage_weight_must_be_on_sale_or_empty():
    h, fake = searched()
    for bad in (23, "hai mươi", -5):
        r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX], baggage_kg=bad))
        assert r["status"] == "invalid_param" and "15, 20, 25, 30" in r["hint"]
    assert not [q for q in fake.requests if q.url.path == "/api/bookings/quote"]
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX], baggage_kg=0))
    assert r["status"] == "pending_approval" and h.proposal.body["baggage"] == []


def test_baggage_over_the_price_limit_is_denied_with_a_hint():
    h, _ = searched()                                        # tối đa 2 triệu; VN122 1.850.000 + 20 kg 350.000
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX], baggage_kg=20))
    assert r["status"] == "denied" and "2.200.000" in r["hint"] and "bỏ hành lý" in r["hint"]
    assert h.proposal is None


def test_voucher_code_is_sent_upper_case_and_shown_on_the_card():
    h, _ = searched()
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX], voucher_code=" welcome10 "))
    assert (r["status"], r["total"]) == ("pending_approval", 1_665_000)          # 1.850.000 - 10%
    assert h.proposal.body["voucherCode"] == "WELCOME10"
    assert h.cards[-1]["discount"] == {"code": "WELCOME10", "amount": 185_000} and h.cards[-1]["totalAmount"] == 1_665_000
    h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX], voucher_code=""))
    assert h.proposal.body["voucherCode"] is None and h.cards[-1]["discount"] is None


def test_rejected_voucher_goes_back_to_the_model():
    h, _ = searched()
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX], voucher_code="GIAM50"))
    assert (r["status"], r["code"], r["detail"]) == ("backend_error", "VOUCHER_INVALID", "Mã giảm giá không tồn tại")
    assert h.proposal is None and h.stop_reason is None


CUONG = passenger("NGUYEN", "HUNG CUONG")
PAST = [("AAA111", [CUONG, passenger("TRAN", "THI MAI", "FEMALE", "2005-01-02")]),
        ("BBB222", [CUONG, passenger("NGUYEN", "BE NA", type="CHILD", dob="2020-05-05")]),
        ("CCC333", [passenger("LE", "VAN AN", dob="1990-09-09")]),
        ("DDD444", [passenger("PHAM", "MINH", dob="1980-01-01")])]


def test_recent_passengers_are_unique_and_include_children_without_documents():
    h, _ = ready(FakeBackend(past=PAST))
    r = h.execute("list_recent_passengers", {})      # không cần ràng buộc trước
    assert r == {"status": "ok", "passengers": [
        {"full_name": "NGUYEN HUNG CUONG", "gender": "MALE", "date_of_birth": "2004-03-12"},
        {"full_name": "TRAN THI MAI", "gender": "FEMALE", "date_of_birth": "2005-01-02"},
        {"full_name": "NGUYEN BE NA", "gender": "MALE", "date_of_birth": "2020-05-05"},
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
    h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[p]))
    sent = h.proposal.body["passengers"][0]
    assert (sent["lastName"], sent["firstName"], sent["gender"], sent["dateOfBirth"]) == \
        ("NGUYEN", "HUNG CUONG", "MALE", "2004-03-12")


def test_reused_multi_word_last_name_keeps_last_time_split():
    h, _ = searched(FakeBackend(past=[("AAA111", [passenger("TON NU", "THI HA", "FEMALE")])]))
    p = h.execute("list_recent_passengers", {})["passengers"][0]
    h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[p]))
    sent = h.proposal.body["passengers"][0]
    assert (sent["lastName"], sent["firstName"]) == ("TON NU", "THI HA")


HELD = booking("K7M2QX", "PENDING_PAYMENT", hold_expires="2026-10-26T15:40:00+07:00")
ISSUED = booking("ABC234", journeys=[journey(), journey("RETURN", 610, refundable=False)])
LOOKUP = dict(bookings=[HELD, ISSUED], refunds={
    ("ABC234", "OUTBOUND"): {"journeyValue": 1_850_000, "fee": 300_000, "refundAmount": 1_550_000},
    ("ABC234", "RETURN"): (409, "FARE_RULE_NOT_ALLOWED")})


def test_lookup_tools_need_no_constraints_and_only_get():
    h, fake = ready(FakeBackend(**LOOKUP))
    assert h.execute("list_my_bookings", {})["status"] == "ok"
    assert h.execute("get_booking", {"code": "K7M2QX"})["status"] == "ok"
    assert h.execute("refund_quote", {"code": "ABC234", "direction": "OUTBOUND"})["status"] == "ok"
    assert [q.method for q in fake.requests] == ["GET"] * 3
    assert fake.requests[0].url.params["size"] == "10"


def test_my_bookings_show_state_total_route_and_hold_only_when_pending():
    h, _ = ready(FakeBackend(**LOOKUP))
    held, issued = h.execute("list_my_bookings", {})["bookings"]
    assert held == {"code": "K7M2QX", "state": "chờ thanh toán", "total": "1.850.000 đ",
                    "routes": ["SGN → DAD 08:10 ngày 30/10/2026"], "hold_expires_at": "15:40 ngày 26/10/2026"}
    assert issued["state"] == "đã xuất vé" and "hold_expires_at" not in issued


def test_get_booking_normalises_the_code_and_summarises():
    h, fake = ready(FakeBackend(**LOOKUP))
    r = h.execute("get_booking", {"code": " k7m2qx "})
    assert fake.requests[-1].url.path == "/api/bookings/K7M2QX"
    b = r["booking"]
    assert (b["hold_expires_at"], b["page"], b["passengers"]) == \
        ("15:40 ngày 26/10/2026", "/bookings/K7M2QX", ["NGUYEN HUNG CUONG"])
    assert b["journeys"][0] == {
        "direction": "OUTBOUND", "state": "ACTIVE", "fare": "Phổ thông", "refundable": True,
        "refund_fee": "300.000 đ", "changeable": True, "change_fee": "300.000 đ",
        "flights": ["VN122 SGN 08:10 ngày 30/10/2026 → DAD 09:30 ngày 30/10/2026"],
        "refund_page": "/bookings/K7M2QX/refund/outbound", "reschedule_page": "/bookings/K7M2QX/reschedule/outbound"}
    assert "B1234567" not in json.dumps(r)


def test_bad_booking_code_or_direction_never_reaches_the_backend():
    h, fake = ready(FakeBackend(**LOOKUP))
    assert h.execute("get_booking", {"code": "../me"})["status"] == "invalid_param"
    assert h.execute("get_booking", {"code": "K7M2Q0"})["status"] == "invalid_param"
    assert h.execute("refund_quote", {"code": "ABC234", "direction": "BOTH"})["status"] == "invalid_param"
    assert fake.requests == []


def test_someone_elses_booking_is_not_found_for_the_model():
    h, _ = ready(FakeBackend(**LOOKUP))
    r = h.execute("get_booking", {"code": "ZZZ999"})
    assert (r["status"], r["code"]) == ("backend_error", "NOT_FOUND") and h.stop_reason is None


def test_refund_quote_formats_money_and_links_c10():
    h, _ = ready(FakeBackend(**LOOKUP))
    assert h.execute("refund_quote", {"code": "ABC234", "direction": "OUTBOUND"}) == {
        "status": "ok", "journey_value": "1.850.000 đ", "fee": "300.000 đ", "refund_amount": "1.550.000 đ",
        "page": "/bookings/ABC234/refund/outbound"}
    r = h.execute("refund_quote", {"code": "abc234", "direction": "return"})
    assert (r["status"], r["code"]) == ("backend_error", "FARE_RULE_NOT_ALLOWED") and h.stop_reason is None


def test_malformed_passengers_are_invalid_not_a_crash():
    h, _ = searched()
    for bad in (["Nguyễn Văn An"], '[{"full_name": "x"}]', {"full_name": "Nguyễn Văn An"}, [None]):
        r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=bad))
        assert r["status"] == "invalid_param"


def test_missing_refund_direction_is_invalid_not_a_crash():
    h, _ = ready(FakeBackend(**LOOKUP))
    assert h.execute("refund_quote", {"code": "ABC234", "direction": None})["status"] == "invalid_param"
