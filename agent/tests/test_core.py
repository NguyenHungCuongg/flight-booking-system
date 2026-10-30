from datetime import date as Date

from flight_agent.core import Constraints, normalise, passenger_type, split_name

C = Constraints("SGN", "DAD", "2026-10-30", depart_before="12:00", max_price=2_000_000)
RT = Constraints("SGN", "DAD", "2026-10-30", return_date="2026-11-02")
BACK = ("DAD", "SGN", "2026-11-02T17:00:00+07:00")


def test_valid_morning_flight_under_price_breaks_nothing():
    assert C.broken("SGN", "DAD", "2026-10-30T08:10:00+07:00", 1_850_000) == []
    assert C.is_ok("SGN", "DAD", "2026-10-30T08:10:00+07:00", 1_850_000)


def test_broken_lists_each_violated_constraint():
    assert C.broken("SGN", "DAD", "2026-10-30T14:20:00+07:00", 1_150_000) == ["depart_before"]
    assert C.broken("SGN", "DAD", "2026-10-30T08:30:00+07:00", 2_480_000) == ["max_price"]
    assert C.broken("SGN", "HAN", "2026-10-31T08:10:00+07:00", 1_000_000) == ["destination", "date"]


def test_missing_limits_mean_no_limit():
    loose = Constraints("SGN", "DAD", "2026-10-30")
    assert loose.is_ok("SGN", "DAD", "2026-10-30T23:50:00+07:00", 99_000_000)


def test_prompt_states_every_constraint():
    text = C.to_prompt()
    for part in ("SGN", "DAD", "2026-10-30", "12:00", "2.000.000"):
        assert part in text


def test_prompt_states_passenger_counts():
    text = Constraints("SGN", "DAD", "2026-10-30", adults=2, children=1, infants=1).to_prompt()
    assert "2 người lớn, 1 trẻ em, 1 em bé" in text and "tổng" in text


def test_prompt_states_one_way_or_the_return_date():
    assert "về ngày 2026-11-02" in RT.to_prompt()
    assert "một chiều" in C.to_prompt()


def test_round_trip_breaks_without_the_right_return_leg():
    out = ("SGN", "DAD", "2026-10-30T08:10:00+07:00", 3_600_000)
    assert RT.broken(*out, back=BACK) == []
    assert RT.broken(*out) == ["return_date"]
    assert RT.broken(*out, back=("DAD", "SGN", "2026-11-03T17:00:00+07:00")) == ["return_date"]
    assert RT.broken(*out, back=("DAD", "HAN", "2026-11-02T17:00:00+07:00")) == ["return_date"]
    assert C.broken("SGN", "DAD", "2026-10-30T08:10:00+07:00", 1_850_000, back=BACK) == ["return_date"]


def test_normalise_pads_time_and_reads_grouped_prices():
    assert normalise("depart_before", "9:00") == ("09:00", None)
    assert normalise("max_price", "2.000.000") == (2_000_000, None)
    assert normalise("max_price", 2_000_000) == (2_000_000, None)


def test_normalise_rejects_loose_text_with_a_hint():
    for key, value in (("max_price", "2 triệu"), ("max_price", "2000000.5"), ("depart_before", "9h"),
                       ("depart_before", "25:00")):
        stored, hint = normalise(key, value)
        assert stored is None and hint


def test_split_name_follows_br12():
    assert split_name("Nguyễn Hùng Cường") == ("NGUYEN", "HUNG CUONG")
    assert split_name("Đặng Thị Ánh") == ("DANG", "THI ANH")
    assert split_name("  nguyễn   văn  a ") == ("NGUYEN", "VAN A")
    assert split_name("Cường") is None
    assert split_name("NGUYEN HUNG CUONG") == ("NGUYEN", "HUNG CUONG")     # tên lấy lại từ booking cũ


def test_passenger_type_uses_whole_years_on_the_flight_day():
    day = Date(2026, 10, 30)
    assert passenger_type(Date(2014, 10, 30), day) == "ADULT"      # tròn 12 đúng hôm bay
    assert passenger_type(Date(2014, 10, 31), day) == "CHILD"
    assert passenger_type(Date(2024, 10, 30), day) == "CHILD"      # tròn 2 đúng hôm bay
    assert passenger_type(Date(2024, 10, 31), day) == "INFANT"
