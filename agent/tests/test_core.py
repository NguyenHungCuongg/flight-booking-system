from flight_agent.core import Constraints, normalise, split_name

C = Constraints("SGN", "DAD", "2026-10-30", depart_before="12:00", max_price=2_000_000)


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
