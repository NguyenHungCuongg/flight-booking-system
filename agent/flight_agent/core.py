"""Ràng buộc, chuẩn hoá và harness của trợ lý đặt vé.

    Agent = Model + Harness

MODEL đề xuất lời gọi tool. HARNESS (file này) quyết định lời gọi có được chạy không, ghi lại,
dừng lượt chat, và dùng CODE để quyết định việc đã xong hay chưa.
"""
import json
import re
import threading
import unicodedata
from collections import deque
from dataclasses import dataclass, field
from datetime import date as Date
from uuid import uuid4

from flight_agent.backend import Backend, BackendError

KEYS = ("depart_before", "max_price")
BUDGET = 10          # tối đa số lần gọi model mỗi lượt chat
LOOP_WINDOW = 6      # xét 6 lời gọi tool gần nhất ...
LOOP_REPEAT = 3      # ... cùng (tool, args) 3 lần = lặp


# =====================================================================
# 1. RÀNG BUỘC LÀ DỮ LIỆU
# =====================================================================
def _vnd(n: int) -> str:
    return f"{n:,}".replace(",", ".")


@dataclass(frozen=True)
class Constraints:
    origin: str
    destination: str
    date: str                           # ngày cất cánh theo giờ sân bay đi, YYYY-MM-DD
    depart_before: str | None = None    # HH:MM có số 0 ở đầu; None = không giới hạn
    max_price: int | None = None        # VND; None = không giới hạn

    def to_prompt(self) -> str:
        before = self.depart_before or "không giới hạn"
        price = f"{_vnd(self.max_price)} đ" if self.max_price is not None else "không giới hạn"
        return (f"Ràng buộc hiện tại: {self.origin} → {self.destination} ngày {self.date}, "
                f"cất cánh trước {before}, giá tối đa {price}.")

    def broken(self, origin: str, destination: str, departure: str, price: int) -> list[str]:
        """Tên các ràng buộc bị vi phạm. `departure` là giờ địa phương ISO có offset, vd 2026-10-30T08:10:00+07:00."""
        checks = [("origin", origin == self.origin),
                  ("destination", destination == self.destination),
                  ("date", departure[:10] == self.date),
                  ("depart_before", self.depart_before is None or departure[11:16] < self.depart_before),
                  ("max_price", self.max_price is None or price <= self.max_price)]
        return [name for name, ok in checks if not ok]

    def is_ok(self, origin: str, destination: str, departure: str, price: int) -> bool:
        """Quy tắc duy nhất, dùng chung cho kiểm quyền và tiêu chí hoàn thành."""
        return not self.broken(origin, destination, departure, price)


def normalise(key: str, value):
    """Trả (giá trị đã chuẩn hoá, None), hoặc (None, hint) nếu không đọc được."""
    if key not in KEYS:
        return None, f"Không có ràng buộc {key!r}. Chỉ dùng {', '.join(KEYS)}."
    raw = str(value).strip()
    if key == "depart_before":
        m = re.fullmatch(r"(\d{1,2}):(\d{2})", raw)
        if not m or int(m[1]) > 23 or int(m[2]) > 59:
            return None, "depart_before phải là giờ dạng HH:MM, ví dụ 09:00."
        return f"{int(m[1]):02d}:{m[2]}", None      # có số 0 ở đầu: Constraints.broken so sánh chuỗi
    if isinstance(value, float) and value.is_integer():
        raw = str(int(value))
    # Chữ số, có thể ngăn hàng nghìn ("2,000,000", "2.000.000"). Phần thập phân bị từ chối:
    # bỏ dấu "." của "2000000.0" sẽ thành 20.000.000.
    if not re.fullmatch(r"\d{1,3}([.,_]\d{3})+|\d+", raw) or (price := int(re.sub(r"\D", "", raw))) <= 0:
        return None, "max_price phải là số tiền VND dương, ví dụ 2000000."
    return price, None


def split_name(full_name: str) -> tuple[str, str] | None:
    """BR-12: bỏ dấu, in hoa, gộp khoảng trắng; từ đầu là họ, phần còn lại là tên đệm và tên."""
    text = full_name.replace("đ", "d").replace("Đ", "D")
    text = "".join(c for c in unicodedata.normalize("NFD", text) if unicodedata.category(c) != "Mn")
    words = re.sub(r"[^A-Z ]", "", text.upper()).split()
    return (words[0], " ".join(words[1:])) if len(words) >= 2 else None


# =====================================================================
# HARNESS
# =====================================================================
class StopRun(Exception):
    """Ném ra để dừng lượt chat; lý do nằm ở Harness.stop_reason."""


@dataclass
class Proposal:
    id: str
    body: dict      # BookingRequest dạng JSON, gửi nguyên khi khách duyệt
    total: int      # totalAmount của báo giá
    card: dict


@dataclass
class Harness:
    # Giữ qua các lượt chat
    backend: Backend | None = None
    today: str = ""
    constraints: Constraints | None = None
    found: list[dict] = field(default_factory=list)     # hành trình rút gọn của lần tìm gần nhất
    proposal: Proposal | None = None                     # đề xuất duy nhất còn hiệu lực
    airports: dict[str, dict] | None = None              # cache GET /api/airports theo code
    # Đặt lại mỗi lượt
    trace: list[dict] = field(default_factory=list)
    cards: list[dict] = field(default_factory=list)
    stop_reason: str | None = None
    model_calls: int = 0
    recent: deque = field(default_factory=lambda: deque(maxlen=LOOP_WINDOW))
    held_code: str | None = None                         # booking đọc lại không khớp (hold_mismatch)
    # LangGraph chạy các tool call của cùng một AIMessage song song: mỗi lúc chỉ một tool được chạy.
    tool_lock: threading.Lock = field(default_factory=threading.Lock)

    def begin_turn(self, backend: Backend, today: str) -> None:
        self.backend, self.today = backend, today
        self.trace, self.cards, self.stop_reason, self.model_calls = [], [], None, 0
        self.recent, self.held_code = deque(maxlen=LOOP_WINDOW), None

    @property
    def tools(self) -> list:
        return [self.set_constraints, self.search_flights, self.propose_hold]

    # ---- ba tool của model
    def set_constraints(self, origin: str, destination: str, date: str, depart_before: str | None = None,
                        max_price: int | None = None) -> dict:
        """Đặt hoặc thay ràng buộc của chuyến cần tìm. origin, destination: mã IATA sân bay. date: ngày cất cánh
        YYYY-MM-DD. depart_before: giờ cất cánh chậm nhất HH:MM, bỏ trống nếu khách không nói. max_price: giá tối đa
        (VND, số nguyên), bỏ trống nếu khách không nói."""
        limits = {}
        for key, value in (("depart_before", depart_before), ("max_price", max_price)):
            if value is not None:
                limits[key], hint = normalise(key, value)
                if hint:
                    return {"status": "invalid_param", "hint": hint}
        if self.airports is None:
            self.airports = {a["code"]: a for a in self.backend.get("/api/airports")}
        origin, destination = origin.strip().upper(), destination.strip().upper()
        if unknown := [c for c in (origin, destination) if c not in self.airports]:
            return {"status": "invalid_param",
                    "hint": f"Không có sân bay {', '.join(unknown)}. Các mã có: {', '.join(sorted(self.airports))}."}
        if self.airports[origin]["countryCode"] != self.airports[destination]["countryCode"]:
            self.stop("international")
            return {"status": "denied", "hint": "Trợ lý chưa hỗ trợ hành trình quốc tế."}
        try:
            past = not re.fullmatch(r"\d{4}-\d{2}-\d{2}", date) or Date.fromisoformat(date).isoformat() < self.today
        except ValueError:
            past = True
        if past:
            return {"status": "invalid_param", "hint": f"date phải là ngày YYYY-MM-DD từ hôm nay ({self.today})."}
        self.constraints = c = Constraints(origin, destination, date, **limits)
        self.found, self.proposal = [], None
        self.cards.append({"type": "constraints", "origin": c.origin, "destination": c.destination, "date": c.date,
                           "departBefore": c.depart_before, "maxPrice": c.max_price})
        return {"status": "ok", "constraints": c.to_prompt()}

    def search_flights(self) -> dict:
        """Tìm chuyến một chiều cho 1 người lớn, hạng phổ thông, theo ràng buộc hiện tại. matching: các hành trình
        thoả mọi ràng buộc, rẻ nhất trước. nearest: tối đa 2 hành trình rẻ nhất chỉ vi phạm một ràng buộc."""
        c = self.constraints
        data = self.backend.get("/api/flights/search", {"from": c.origin, "to": c.destination, "date": c.date,
                                                        "adults": 1, "cabin": "ECONOMY"})
        self.found = []
        for it in data["itineraries"]:
            if not it["fareOptions"]:
                continue
            segs, fare = it["segments"], it["fareOptions"][0]      # gói rẻ nhất
            first, last = segs[0]["departure"], segs[-1]["arrival"]
            self.found.append({
                "flight_ids": [s["flightId"] for s in segs],
                "flights": "+".join(s["flightNumber"] for s in segs),
                "segments": [{"flightNumber": s["flightNumber"], "from": s["departure"]["airport"],
                              "to": s["arrival"]["airport"], "depart": s["departure"]["time"],
                              "arrive": s["arrival"]["time"]} for s in segs],
                "depart": first["time"], "stops": it["stops"], "fare_family_id": fare["fareFamilyId"],
                "fare": fare["name"], "price": fare["totalPrice"], "seats_left": fare["seatsLeft"],
                "broken": c.broken(first["airport"], last["airport"], first["time"], fare["totalPrice"]),
            })
        matching = sorted((i for i in self.found if not i["broken"]), key=lambda i: i["price"])
        if not matching:
            self.stop("no_match")
        return {"status": "ok", "matching": matching, "nearest": self._nearest()}

    def propose_hold(self, flight_ids: list[int], fare_family_id: int, full_name: str, gender: str,
                     date_of_birth: str) -> dict:
        """Báo giá và đưa thẻ duyệt giữ chỗ cho khách. flight_ids, fare_family_id: lấy đúng từ một hành trình trong
        matching. full_name: họ tên đầy đủ của hành khách. gender: MALE hoặc FEMALE. date_of_birth: YYYY-MM-DD.
        Chỉ trả pending_approval: khách tự bấm Giữ chỗ trên thẻ."""
        if gender not in ("MALE", "FEMALE"):
            return {"status": "invalid_param", "hint": "gender phải là MALE hoặc FEMALE."}
        if (name := split_name(full_name)) is None:
            return {"status": "invalid_param", "hint": "Cần đủ họ và tên của hành khách, ví dụ Nguyễn Văn An."}
        me = self.backend.get("/api/me")
        passenger = {"type": "ADULT", "lastName": name[0], "firstName": name[1], "gender": gender,
                     "dateOfBirth": date_of_birth}
        body = {"journeys": [{"direction": "OUTBOUND", "flightIds": flight_ids, "fareFamilyId": fare_family_id}],
                "passengers": [passenger], "baggage": [],
                "contact": {"name": me["fullName"], "email": me["email"], "phone": me["phone"]}}
        total = self.backend.post("/api/bookings/quote", body)["totalAmount"]
        if self.constraints.max_price is not None and total > self.constraints.max_price:
            return {"status": "denied",
                    "hint": f"Báo giá {_vnd(total)} đ vượt giá tối đa. {self.constraints.to_prompt()}"}
        it = self._found(flight_ids, fare_family_id)
        pid = uuid4().hex
        card = {"type": "proposal", "proposalId": pid, "segments": it["segments"], "fare": it["fare"],
                "passenger": {k: passenger[k] for k in ("lastName", "firstName", "gender", "dateOfBirth")},
                "contact": body["contact"], "totalAmount": total}
        self.proposal = Proposal(pid, body, total, card)
        self.cards.append(card)
        return {"status": "pending_approval", "total": total}

    def _found(self, flight_ids, fare_family_id) -> dict | None:
        return next((i for i in self.found
                     if i["flight_ids"] == flight_ids and i["fare_family_id"] == fare_family_id), None)

    def _nearest(self) -> list[dict]:
        return sorted((i for i in self.found if len(i["broken"]) == 1), key=lambda i: i["price"])[:2]

    def stop(self, reason: str) -> None:
        self.stop_reason = self.stop_reason or reason

    # ---- ngân sách: mọi lần gọi model đi qua đây
    def before_model(self) -> None:
        if self.stop_reason:
            raise StopRun
        if self.model_calls >= BUDGET:
            self.stop("budget")
            raise StopRun
        self.model_calls += 1

    # ---- 2. KIỂM QUYỀN: chạy TRƯỚC tool, dựa trên DỮ LIỆU ràng buộc
    def check_permission(self, tool: str, args: dict) -> str | None:
        """Trả None nếu được chạy, hoặc lý do bị chặn."""
        if tool not in {t.__name__ for t in self.tools}:
            return f"Không có tool {tool}."
        if tool in ("search_flights", "propose_hold") and self.constraints is None:
            return "Chưa có ràng buộc. Gọi set_constraints trước."
        if tool == "propose_hold":
            it = self._found(args.get("flight_ids"), args.get("fare_family_id"))
            if it is None or it["broken"]:
                return (f"Hành trình {args.get('flight_ids')} với gói {args.get('fare_family_id')} không có trong "
                        f"matching của lần tìm gần nhất. {self.constraints.to_prompt()}")
        return None

    # ---- phát hiện lặp: cùng (tool, args) nhiều lần
    def is_looping(self, tool: str, args: dict) -> bool:
        fp = (tool, json.dumps(args, sort_keys=True))
        looping = self.recent.count(fp) + 1 >= LOOP_REPEAT
        self.recent.append(fp)
        return looping

    # ---- mọi lời gọi tool đều đi qua đây
    def execute(self, tool: str, args: dict) -> dict:
        with self.tool_lock:
            return self._execute(tool, args)

    def _execute(self, tool: str, args: dict) -> dict:
        if self.is_looping(tool, args):
            self.stop("loop")
            result = {"status": "denied", "hint": f"Lặp: {tool} với cùng tham số đã gọi {LOOP_REPEAT} lần."}
        elif reason := self.check_permission(tool, args):
            result = {"status": "denied", "hint": reason}
        else:
            try:
                result = {t.__name__: t for t in self.tools}[tool](**args)
            except TypeError as e:
                result = {"status": "invalid_param", "hint": f"{e}. Kiểm tra tên tham số của tool."}
            except BackendError as e:
                result = self.backend_error(e)
        self.trace.append({"tool": tool, "args": args, "status": result["status"]})
        return result

    def backend_error(self, e: BackendError) -> dict:
        """401/403 và lỗi hộ chiếu dừng lượt chat; lỗi khác trả cho model tự sửa."""
        if e.status == 401:
            self.stop("unauthenticated")
        elif e.status == 403:
            self.stop("forbidden")
        elif e.code == "PASSENGER_RULES_VIOLATED" and any(
                err.get("field", "").endswith(("passportNumber", "passportExpiry")) for err in e.errors):
            self.stop("international")
        return {"status": "backend_error", "code": e.code, "detail": e.detail, "errors": e.errors}

    # ---- 3. "XONG" DO CODE QUYẾT ĐỊNH: đọc lại booking, không tin lời model
    def is_done(self, detail: dict, proposal: Proposal) -> bool:
        journeys = detail.get("journeys") or []
        segs = (journeys[0].get("segments") or []) if len(journeys) == 1 else []
        return (detail.get("status") == "PENDING_PAYMENT"
                and len(journeys) == 1 and journeys[0].get("direction") == "OUTBOUND"
                and bool(segs) and [s["flightId"] for s in segs] == proposal.body["journeys"][0]["flightIds"]
                and len(detail.get("passengers") or []) == 1
                and detail.get("totalAmount") == proposal.total
                and self.constraints is not None
                and self.constraints.is_ok(segs[0]["departureAirport"], segs[-1]["arrivalAirport"],
                                           segs[0]["departureTime"], detail["totalAmount"]))

    # ---- 4. BÀN GIAO: đủ để khách nắm tình hình trong 30 giây
    def handoff(self) -> dict:
        done = [self.constraints.to_prompt()] if self.constraints else []
        done.append(f"Đã tạo booking {self.held_code} nhưng đọc lại không khớp." if self.held_code
                    else "Chưa giữ chỗ, chưa trừ tiền.")
        last = self.trace[-1]["tool"] if self.trace else "-"
        retry = "Bạn muốn thử lại hay tự đặt trên /flights?"
        if near := self._nearest():
            n = near[0]
            no_match = (f"Không có chuyến nào thoả mọi ràng buộc. Gần nhất là {n['flights']} lúc {n['depart'][11:16]}, "
                        f"{_vnd(n['price'])} đ. Bạn muốn nới giờ bay hay giá tối đa?")
        else:
            no_match = (f"Không có chuyến nào ngày {self.constraints.date if self.constraints else '-'}. "
                        "Bạn muốn đổi sang ngày khác không?")
        questions = {
            "no_match": no_match,
            "budget": f"Trợ lý đã dùng hết {BUDGET} lần gọi mô hình cho lượt này. {retry}",
            "loop": f"Trợ lý lặp lại {last} mà không tiến triển. {retry}",
            "infra": "Mô hình AI đang quá tải hoặc không phản hồi. Bạn thử lại sau ít phút nhé?",
            "unauthenticated": "Phiên đăng nhập đã hết hạn. Bạn đăng nhập lại rồi thử tiếp nhé?",
            "forbidden": "Trợ lý chỉ dùng cho tài khoản khách hàng. Bạn đăng nhập bằng tài khoản khách hàng nhé?",
            "international": "Hành trình quốc tế cần khai hộ chiếu, trợ lý chưa hỗ trợ. "
                             "Bạn đặt chuyến này trên /flights nhé?",
            "hold_mismatch": f"Booking {self.held_code} đọc lại không khớp với thẻ đã duyệt. "
                             f"Bạn kiểm tra ở /bookings/{self.held_code} nhé?",
        }
        return {"type": "handoff", "doneSoFar": done,
                "tried": [f"{t['tool']}({json.dumps(t['args'], ensure_ascii=False)}) → {t['status']}"
                          for t in self.trace],
                "question": questions.get(self.stop_reason, retry)}
