"""Bộ case eval: các lượt chat của khách và kết quả mong đợi, chấm bằng code trong evals/run.py.

Backend giả (tests/fakes.py) trả cùng 4 chuyến SGN→DAD ngày 2026-10-30 cho mọi lần tìm.
"""
from dataclasses import dataclass, field

TODAY = "2026-10-26"    # thứ Hai; các chuyến giả bay thứ Sáu 2026-10-30


@dataclass
class Case:
    name: str
    group: str
    turns: list[str]
    end: str                                        # "proposal", "reply", hoặc một stop_reason ("no_match", ...)
    constraints: dict | None = None                 # tham số của Constraints; None = không chấm
    flight_ids: list[int] | None = None
    passenger: tuple[str, str, str, str] | None = None     # họ, tên, giới tính, ngày sinh
    reply_has: list[str] = field(default_factory=list)
    reply_lacks: list[str] = field(default_factory=list)
    today: str = TODAY
    backend: dict = field(default_factory=dict)     # kwargs của FakeBackend


CASES: list[Case] = []
