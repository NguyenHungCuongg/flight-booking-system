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
from datetime import date as Date, datetime
from typing import TypedDict
from uuid import uuid4

from flight_agent.backend import Backend, BackendError

KEYS = ("depart_before", "max_price")
BUDGET = 10          # tối đa số lần gọi model mỗi lượt chat
LOOP_WINDOW = 6      # xét 6 lời gọi tool gần nhất ...
LOOP_REPEAT = 3      # ... cùng (tool, args) 3 lần = lặp
RECENT_BOOKINGS = 3  # số booking gần nhất mà list_recent_passengers đọc
MY_BOOKINGS = 10     # số booking gần nhất mà list_my_bookings đọc
STATES = {"PENDING_PAYMENT": "chờ thanh toán", "ISSUED": "đã xuất vé", "EXPIRED": "hết hạn giữ chỗ",
          "CANCELLED": "đã huỷ", "REFUNDED": "đã hoàn"}
BOOKING_CODE = re.compile(r"[A-HJ-NP-Z2-9]{6}")     # BR-46, như BookingCodes.ALPHABET của backend
BAD_CODE = {"status": "invalid_param", "hint": "Mã booking gồm 6 chữ và số, ví dụ K7M2QX."}
PAX_KEYS = ("type", "lastName", "firstName", "gender", "dateOfBirth")
MATCHING = 5         # số mục tối đa của matching: khứ hồi ghép mọi cặp nên số mục tăng theo tích
DIRECTIONS = ("OUTBOUND", "RETURN")


# =====================================================================
# 1. RÀNG BUỘC LÀ DỮ LIỆU
# =====================================================================
def _vnd(n: int) -> str:
    return f"{n:,}".replace(",", ".")


def _money(n: int) -> str:
    return f"{_vnd(n)} đ"


def _when(iso: str) -> str:
    """Giờ ISO có offset của API → "08:10 ngày 30/10/2026", giữ nguyên múi giờ API đã trả."""
    return f"{iso[11:16]} ngày {iso[8:10]}/{iso[5:7]}/{iso[:4]}"


@dataclass(frozen=True)
class Constraints:
    origin: str
    destination: str
    date: str                           # ngày cất cánh theo giờ sân bay đi, YYYY-MM-DD
    depart_before: str | None = None    # HH:MM có số 0 ở đầu; None = không giới hạn
    max_price: int | None = None        # VND, tổng cả booking; None = không giới hạn
    adults: int = 1
    children: int = 0
    infants: int = 0
    return_date: str | None = None      # ngày cất cánh chiều về YYYY-MM-DD; None = một chiều

    @property
    def counts(self) -> str:
        return f"{self.adults} người lớn, {self.children} trẻ em, {self.infants} em bé"

    def to_prompt(self) -> str:
        before = self.depart_before or "không giới hạn"
        price = f"{_vnd(self.max_price)} đ" if self.max_price is not None else "không giới hạn"
        back = f", về ngày {self.return_date}" if self.return_date else ", một chiều"
        return (f"Ràng buộc hiện tại: {self.origin} → {self.destination} ngày {self.date}{back} cho {self.counts}, "
                f"chiều đi cất cánh trước {before}, giá tối đa (tổng khách trả: mọi chiều, gồm hành lý, sau giảm giá) "
                f"{price}.")

    def broken(self, origin: str, destination: str, departure: str, price: int,
               back: tuple[str, str, str] | None = None) -> list[str]:
        """Tên các ràng buộc bị vi phạm. `departure` là giờ địa phương ISO có offset, vd 2026-10-30T08:10:00+07:00.
        `back`: (sân bay đi, sân bay đến, giờ cất cánh) của chiều về; None nếu một chiều."""
        checks = [("origin", origin == self.origin),
                  ("destination", destination == self.destination),
                  ("date", departure[:10] == self.date),
                  ("depart_before", self.depart_before is None or departure[11:16] < self.depart_before),
                  ("max_price", self.max_price is None or price <= self.max_price),
                  ("return_date", back is None if self.return_date is None else back is not None
                   and (back[0], back[1], back[2][:10]) == (self.destination, self.origin, self.return_date))]
        return [name for name, ok in checks if not ok]

    def is_ok(self, origin: str, destination: str, departure: str, price: int,
              back: tuple[str, str, str] | None = None) -> bool:
        """Quy tắc duy nhất, dùng chung cho kiểm quyền và tiêu chí hoàn thành."""
        return not self.broken(origin, destination, departure, price, back)


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


def passenger_type(dob: Date, day: Date) -> str:
    """BR-10: loại khách theo tuổi tròn năm tại ngày bay, như PassengerRules.typeAt; backend vẫn kiểm lại."""
    age = day.year - dob.year - ((day.month, day.day) < (dob.month, dob.day))
    return "ADULT" if age >= 12 else "CHILD" if age >= 2 else "INFANT"


def _people(passengers: list[dict]) -> list[tuple]:
    """Tập hành khách không theo thứ tự: backend có thể sắp lại."""
    return sorted(tuple(p.get(k) for k in PAX_KEYS) for p in passengers)


def _bags(lines: list[tuple], passengers: list[dict]) -> list[tuple]:
    """Các cặp (passengerIndex, kg) của một chiều thành các cặp (hành khách, kg) không theo thứ tự.
    Chỉ số không trỏ tới hành khách nào thì hành khách là ()."""
    pax = lambda i: (tuple(passengers[i].get(k) for k in PAX_KEYS)
                     if isinstance(i, int) and 0 <= i < len(passengers) else ())
    return sorted((pax(i), kg) for i, kg in lines)


def _booking_code(code: str) -> str | None:
    """Mã đã chuẩn hoá, hoặc None nếu không đúng dạng: chặn mọi chuỗi lạ trước khi ghép vào đường dẫn API."""
    code = str(code).strip().upper()
    return code if BOOKING_CODE.fullmatch(code) else None


def _day(text) -> Date | None:
    """Ngày dạng YYYY-MM-DD, hoặc None nếu sai dạng hay không có thật."""
    try:
        return Date.fromisoformat(text) if re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(text)) else None
    except ValueError:
        return None


def _journeys(item: dict) -> list[dict]:
    """Các chiều của một mục tìm chuyến, theo thứ tự đi rồi về."""
    return [item["outbound"], item["return"]] if "outbound" in item else [item]


# =====================================================================
# HARNESS
# =====================================================================
class StopRun(Exception):
    """Ném ra để dừng lượt chat; lý do nằm ở Harness.stop_reason."""


# Một hành khách của propose_hold. Kiểu có trường để schema tool là object có properties (Gemini cần); không viết
# docstring vì nó lọt vào schema gửi cho model.
class PassengerArg(TypedDict):
    full_name: str
    gender: str         # MALE hoặc FEMALE
    date_of_birth: str  # YYYY-MM-DD


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
    # cache GET /api/airlines/{code}/baggage-options theo hãng: các mức {id, weightKg, price}, kg tăng dần
    baggage_prices: dict[str, list[dict]] = field(default_factory=dict)
    # full_name -> (họ, tên) của hành khách cũ: họ nhiều chữ ("TON NU") thì split_name tách sai.
    known_names: dict[str, tuple[str, str]] = field(default_factory=dict)
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
        return [self.set_constraints, self.search_flights, self.list_recent_passengers, self.propose_hold,
                self.list_my_bookings, self.get_booking, self.refund_quote]

    # ---- các tool của model
    def set_constraints(self, origin: str, destination: str, date: str, depart_before: str | None = None,
                        max_price: int | None = None, adults: int = 1, children: int = 0, infants: int = 0,
                        return_date: str | None = None) -> dict:
        """Đặt hoặc thay ràng buộc của chuyến cần tìm. origin, destination: mã IATA sân bay. date: ngày cất cánh
        YYYY-MM-DD. depart_before: giờ cất cánh chậm nhất HH:MM, bỏ trống nếu khách không nói. max_price: số tiền tối
        đa khách trả cho cả booking (VND, số nguyên), gồm hành lý và sau giảm giá, bỏ trống nếu khách không nói.
        adults, children (2 đến dưới 12 tuổi), infants (dưới 2 tuổi): số khách theo tuổi tại ngày bay. return_date:
        ngày cất cánh chiều về YYYY-MM-DD nếu khách cần khứ hồi, không trước date; bỏ trống nếu một chiều.
        depart_before chỉ áp cho chiều đi; max_price là tổng cả hai chiều."""
        try:
            counts = {"adults": int(adults), "children": int(children), "infants": int(infants)}
        except (TypeError, ValueError):
            counts = None
        if counts is None or counts["adults"] < 1 or min(counts.values()) < 0:
            return {"status": "invalid_param", "hint": "adults là số nguyên từ 1, children và infants từ 0."}
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
        if (day := _day(date)) is None or day.isoformat() < self.today:
            return {"status": "invalid_param", "hint": f"date phải là ngày YYYY-MM-DD từ hôm nay ({self.today})."}
        if return_date is not None and ((back := _day(return_date)) is None or back < day):
            return {"status": "invalid_param",
                    "hint": f"return_date phải là ngày YYYY-MM-DD, không trước ngày đi {date}."}
        self.constraints = c = Constraints(origin, destination, date, **limits, **counts, return_date=return_date)
        self.found, self.proposal = [], None
        self.cards.append({"type": "constraints", "origin": c.origin, "destination": c.destination, "date": c.date,
                           "returnDate": c.return_date, "departBefore": c.depart_before, "maxPrice": c.max_price,
                           **counts})
        return {"status": "ok", "constraints": c.to_prompt()}

    def search_flights(self) -> dict:
        """Tìm chuyến hạng phổ thông cho số khách trong ràng buộc hiện tại; price là tổng cả booking.
        matching: tối đa 5 hành trình thoả mọi ràng buộc, rẻ nhất trước. nearest: tối đa 2 hành trình rẻ nhất chỉ vi
        phạm một ràng buộc. Khứ hồi: mỗi mục có outbound và return (mỗi chiều dạng như một hành trình một chiều), price
        là tổng hai chiều; chỉ ghép cặp chiều về cất cánh sau khi chiều đi hạ cánh."""
        c = self.constraints
        legs = self._legs(c.origin, c.destination, c.date)
        if c.return_date is None:
            self.found = [{**o, "broken": c.broken(o["segments"][0]["from"], o["segments"][-1]["to"], o["depart"],
                                                   o["price"])} for o in legs]
        else:
            backs = self._legs(c.destination, c.origin, c.return_date)
            # BR-04: chiều về cất cánh sau khi chiều đi hạ cánh; chuỗi giờ có offset nên so bằng datetime
            self.found = [{"outbound": o, "return": r, "price": o["price"] + r["price"],
                           "broken": c.broken(o["segments"][0]["from"], o["segments"][-1]["to"], o["depart"],
                                              o["price"] + r["price"],
                                              back=(r["segments"][0]["from"], r["segments"][-1]["to"], r["depart"]))}
                          for o in legs for r in backs
                          if datetime.fromisoformat(r["depart"]) > datetime.fromisoformat(o["segments"][-1]["arrive"])]
        matching = sorted((i for i in self.found if not i["broken"]), key=lambda i: i["price"])[:MATCHING]
        if not matching:
            self.stop("no_match")
        return {"status": "ok", "matching": matching, "nearest": self._nearest()}

    def _legs(self, origin: str, destination: str, day: str) -> list[dict]:
        """Một lần tìm chuyến cho một chiều: mỗi hành trình có gói giá thành một dict rút gọn, lấy gói rẻ nhất."""
        c = self.constraints
        data = self.backend.get("/api/flights/search", {"from": origin, "to": destination, "date": day,
                                                        "adults": c.adults, "children": c.children,
                                                        "infants": c.infants, "cabin": "ECONOMY"})
        legs = []
        for it in data["itineraries"]:
            if not it["fareOptions"]:
                continue
            segs, fare = it["segments"], it["fareOptions"][0]      # gói rẻ nhất
            legs.append({
                "flight_ids": [s["flightId"] for s in segs],
                "airline": segs[0]["airline"]["code"],     # hãng của itinerary là hãng chặng đầu, như FlightApi
                "flights": "+".join(s["flightNumber"] for s in segs),
                "segments": [{"flightNumber": s["flightNumber"], "from": s["departure"]["airport"],
                              "to": s["arrival"]["airport"], "depart": s["departure"]["time"],
                              "arrive": s["arrival"]["time"]} for s in segs],
                "depart": segs[0]["departure"]["time"], "stops": it["stops"], "fare_family_id": fare["fareFamilyId"],
                "fare": fare["name"], "price": fare["totalPrice"], "seats_left": fare["seatsLeft"],
            })
        return legs

    def list_recent_passengers(self) -> dict:
        """Hành khách trong vài booking gần nhất của khách, không trùng nhau, mới nhất trước. Dùng khi
        khách muốn đặt cho mình hoặc cho người đã đặt trước đây; đọc tên ra và hỏi khách xác nhận trước khi
        propose_hold. Danh sách rỗng thì hỏi khách thông tin hành khách."""
        page = self.backend.get("/api/bookings", {"size": RECENT_BOOKINGS})
        passengers = []
        for summary in page["content"]:
            for p in self.backend.get(f"/api/bookings/{summary['code']}")["passengers"]:
                # Không trả số giấy tờ: model chỉ cần đủ tham số cho propose_hold.
                item = {"full_name": f"{p['lastName']} {p['firstName']}", "gender": p["gender"],
                        "date_of_birth": p["dateOfBirth"]}
                if item not in passengers:
                    passengers.append(item)
                    self.known_names[item["full_name"]] = (p["lastName"], p["firstName"])
        return {"status": "ok", "passengers": passengers}

    def propose_hold(self, flight_ids: list[int], fare_family_id: int, passengers: list[PassengerArg],
                     return_flight_ids: list[int] | None = None, return_fare_family_id: int | None = None,
                     baggage_kg: int | None = None, voucher_code: str | None = None) -> dict:
        """Báo giá và đưa thẻ duyệt giữ chỗ cho khách. flight_ids, fare_family_id: lấy đúng từ một hành trình trong
        matching. Khứ hồi: return_flight_ids, return_fare_family_id lấy từ return của cùng mục trong matching.
        passengers: mỗi hành khách một mục, đủ số người lớn, trẻ em, em bé trong ràng buộc; full_name: họ tên đầy đủ,
        gender: MALE hoặc FEMALE, date_of_birth: YYYY-MM-DD. Loại khách do hệ thống tính từ ngày sinh.
        baggage_kg: số kg hành lý ký gửi mua thêm cho mỗi người lớn và trẻ em trên mỗi chiều, chọn từ
        baggage_options; bỏ trống nếu khách không mua. voucher_code: mã giảm giá khách đưa; bỏ trống nếu khách không
        có mã, không tự bịa mã.
        Chỉ trả pending_approval: khách tự bấm Giữ chỗ trên thẻ."""
        c = self.constraints
        # Model có thể gửi sai dạng (chuỗi JSON, object đơn): trả invalid_param để model tự sửa, không làm hỏng lượt chat.
        if not isinstance(passengers, list) or not passengers:
            return {"status": "invalid_param", "hint": "passengers là danh sách, ít nhất một hành khách."}
        built = []
        for i, p in enumerate(passengers, 1):
            bad = lambda hint: {"status": "invalid_param", "hint": f"Hành khách {i}: {hint}"}
            if not isinstance(p, dict):
                return bad("phải là object có full_name, gender, date_of_birth.")
            if p.get("gender") not in ("MALE", "FEMALE"):
                return bad("gender phải là MALE hoặc FEMALE.")
            full_name = str(p.get("full_name", ""))
            if (name := self.known_names.get(full_name) or split_name(full_name)) is None:
                return bad("cần đủ họ và tên, ví dụ Nguyễn Văn An.")
            try:
                dob = Date.fromisoformat(str(p.get("date_of_birth")))
            except ValueError:
                dob = None
            if dob is None or dob > Date.fromisoformat(c.date):
                return bad(f"date_of_birth phải là ngày YYYY-MM-DD, không sau ngày bay {c.date}.")
            built.append({"type": passenger_type(dob, Date.fromisoformat(c.date)), "lastName": name[0],
                          "firstName": name[1], "gender": p["gender"], "dateOfBirth": dob.isoformat()})
        counts = [sum(p["type"] == t for p in built) for t in ("ADULT", "CHILD", "INFANT")]
        if counts != [c.adults, c.children, c.infants]:
            return {"status": "invalid_param",
                    "hint": f"Theo ngày sinh có {counts[0]} người lớn, {counts[1]} trẻ em, {counts[2]} em bé, khác "
                            f"ràng buộc ({c.counts}). Gọi set_constraints với đúng số khách rồi search_flights lại, "
                            "hoặc hỏi lại ngày sinh."}
        me = self.backend.get("/api/me")
        it = self._found(flight_ids, fare_family_id, return_flight_ids, return_fare_family_id)
        # Bảng giá hành lý của hãng chặng đầu mỗi chiều: backend so mức hành lý với hãng của itinerary.
        for j in _journeys(it):
            if j["airline"] not in self.baggage_prices:
                options = self.backend.get(f"/api/airlines/{j['airline']}/baggage-options")
                self.baggage_prices[j["airline"]] = sorted(
                    ({k: o[k] for k in ("id", "weightKg", "price")} for o in options), key=lambda o: o["weightKg"])
        prices = {d: self.baggage_prices[j["airline"]] for d, j in zip(DIRECTIONS, _journeys(it))}
        on_sale = sorted(set.intersection(*({o["weightKg"] for o in p} for p in prices.values())))
        try:
            kg = int(baggage_kg or 0)       # None và 0 là không mua
        except (TypeError, ValueError):
            kg = -1
        if kg < 0 or (kg and kg not in on_sale):
            return {"status": "invalid_param",
                    "hint": f"baggage_kg phải là một trong các mức {', '.join(map(str, on_sale))} kg, hoặc bỏ trống."
                    if on_sale else "Hành trình này không có mức hành lý ký gửi nào bán cho mọi chiều: bỏ trống "
                                    "baggage_kg."}
        body = {"journeys": [{"direction": d, "flightIds": j["flight_ids"], "fareFamilyId": j["fare_family_id"]}
                             for d, j in zip(DIRECTIONS, _journeys(it))],
                "passengers": built,
                # BR-22: em bé không mua hành lý
                "baggage": [{"passengerIndex": i, "direction": d, "baggageOptionId": o["id"]}
                            for d, p in prices.items() for o in p if kg and o["weightKg"] == kg
                            for i, x in enumerate(built) if x["type"] != "INFANT"],
                "contact": {"name": me["fullName"], "email": me["email"], "phone": me["phone"]},
                # như VoucherApi.check: booking đọc lại trả mã đã chuẩn hoá, is_done so khớp chính xác
                "voucherCode": str(voucher_code or "").strip().upper() or None}
        quote = self.backend.post("/api/bookings/quote", body)
        total = quote["totalAmount"]
        if self.constraints.max_price is not None and total > self.constraints.max_price:
            drop = " Hỏi khách bỏ hành lý hay nới giá tối đa." if kg else ""
            return {"status": "denied",
                    "hint": f"Báo giá {_vnd(total)} đ vượt giá tối đa.{drop} {self.constraints.to_prompt()}"}
        pid = uuid4().hex
        count = sum(x["type"] != "INFANT" for x in built)
        code = body["voucherCode"]
        card = {"type": "proposal", "proposalId": pid,
                "journeys": [{"direction": d, "segments": j["segments"], "fare": j["fare"], "fares": q["passengerFares"],
                              "baggage": {"weightKg": kg, "count": count, "total": q["baggageTotal"]} if kg else None}
                             for d, j, q in zip(DIRECTIONS, _journeys(it), quote["journeys"])],
                "passengers": built, "contact": body["contact"],
                "discount": {"code": code, "amount": quote["discountTotal"]} if code else None, "totalAmount": total}
        self.proposal = Proposal(pid, body, total, card)
        self.cards.append(card)
        result = {"status": "pending_approval", "total": total}
        if not kg and on_sale:      # chỉ mời mức mua được: mọi chiều đều bán
            result["baggage_options"] = {d: [f"{o['weightKg']} kg: {_money(o['price'])}" for o in p
                                             if o["weightKg"] in on_sale] for d, p in prices.items()}
        return result

    # ---- tra cứu: chỉ GET, mỗi tool một request; giờ và tiền định dạng sẵn để model chép nguyên văn
    def list_my_bookings(self) -> dict:
        """Các booking gần nhất của khách, mới nhất trước: mã, trạng thái, tổng tiền, chặng, hạn giữ chỗ (chỉ khi
        đang chờ thanh toán). Dùng khi khách hỏi về booking mà không nói mã."""
        page = self.backend.get("/api/bookings", {"size": MY_BOOKINGS})
        out = []
        for b in page["content"]:
            item = {"code": b["code"], "state": STATES.get(b["status"], b["status"]), "total": _money(b["totalAmount"]),
                    "routes": [f"{r['departureAirport']} → {r['arrivalAirport']} {_when(r['departureTime'])}"
                               for r in b["routes"]]}
            if b["status"] == "PENDING_PAYMENT":
                item["hold_expires_at"] = _when(b["holdExpiresAt"])
            out.append(item)
        return {"status": "ok", "bookings": out}

    def get_booking(self, code: str) -> dict:
        """Chi tiết một booking của khách theo mã 6 ký tự: trạng thái, tổng tiền, hạn giữ chỗ (chỉ khi đang chờ thanh
        toán), hành khách, từng chiều bay với điều kiện hoàn đổi của gói giá và trang để khách tự thao tác.
        refund_fee chỉ là phí theo gói; số tiền hoàn thật phải hỏi refund_quote."""
        if (code := _booking_code(code)) is None:
            return BAD_CODE
        b = self.backend.get(f"/api/bookings/{code}")
        info = {"code": code, "state": STATES.get(b["status"], b["status"]), "total": _money(b["totalAmount"])}
        if b["status"] == "PENDING_PAYMENT":
            info["hold_expires_at"] = _when(b["holdExpiresAt"])
        info["page"] = f"/bookings/{code}"
        info["passengers"] = [f"{p['lastName']} {p['firstName']}" for p in b["passengers"]]
        info["journeys"] = [{
            "direction": j["direction"], "state": j["status"], "fare": j["fareFamilyName"],
            "refundable": j["refundable"], "refund_fee": _money(j["refundFee"]),
            "changeable": j["changeable"], "change_fee": _money(j["changeFee"]),
            "flights": [f"{s['flightNumber']} {s['departureAirport']} {_when(s['departureTime'])} → "
                        f"{s['arrivalAirport']} {_when(s['arrivalTime'])}" for s in j["segments"]],
            "refund_page": f"/bookings/{code}/refund/{j['direction'].lower()}",
            "reschedule_page": f"/bookings/{code}/reschedule/{j['direction'].lower()}",
        } for j in b["journeys"]]
        return {"status": "ok", "booking": info}

    def refund_quote(self, code: str, direction: str) -> dict:
        """Số tiền khách được hoàn nếu hoàn một chiều của booking, đã trừ phí và xét hạn hoàn. direction: OUTBOUND
        (chiều đi) hoặc RETURN (chiều về). Không hoàn được thì backend_error có lý do trong detail. Trợ lý không gửi
        yêu cầu hoàn: khách tự làm ở page."""
        if (code := _booking_code(code)) is None:
            return BAD_CODE
        direction = str(direction).strip().upper()
        if direction not in ("OUTBOUND", "RETURN"):
            return {"status": "invalid_param", "hint": "direction phải là OUTBOUND (chiều đi) hoặc RETURN (chiều về)."}
        q = self.backend.get(f"/api/bookings/{code}/journeys/{direction}/refund-quote")
        return {"status": "ok", "journey_value": _money(q["journeyValue"]), "fee": _money(q["fee"]),
                "refund_amount": _money(q["refundAmount"]), "page": f"/bookings/{code}/refund/{direction.lower()}"}

    def _found(self, flight_ids, fare_family_id, return_flight_ids=None, return_fare_family_id=None) -> dict | None:
        """Mục của lần tìm gần nhất có đúng các chiều khách chọn: một chiều không được có tham số chiều về."""
        chosen = [(flight_ids, fare_family_id)]
        if return_flight_ids is not None or return_fare_family_id is not None:
            chosen.append((return_flight_ids, return_fare_family_id))
        return next((i for i in self.found
                     if [(j["flight_ids"], j["fare_family_id"]) for j in _journeys(i)] == chosen), None)

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
            it = self._found(*(args.get(k) for k in ("flight_ids", "fare_family_id", "return_flight_ids",
                                                     "return_fare_family_id")))
            if it is None or it["broken"]:
                chosen = f"Hành trình {args.get('flight_ids')} với gói {args.get('fare_family_id')}"
                back = args.get("return_flight_ids") is not None or args.get("return_fare_family_id") is not None
                if back:
                    chosen += f", chiều về {args.get('return_flight_ids')} với gói {args.get('return_fare_family_id')}"
                if self.constraints.return_date:
                    trip = " Khứ hồi: return_flight_ids và return_fare_family_id lấy từ return của cùng mục trong matching."
                else:
                    trip = " Một chiều: không truyền return_flight_ids, return_fare_family_id." if back else ""
                return (f"{chosen} không có trong matching của lần tìm gần nhất.{trip} "
                        f"{self.constraints.to_prompt()}")
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
        # Từng chiều theo thứ tự: hướng, chuyến, gói giá và hành lý của từng hành khách phải đúng như thẻ đã duyệt
        got = [(j.get("direction"), [s["flightId"] for s in j.get("segments") or []], j.get("fareFamilyName"),
                _bags([(x.get("passengerIndex"), x.get("weightKg")) for x in j.get("baggage") or []],
                      detail.get("passengers") or []))
               for j in journeys]
        want = [(b["direction"], b["flightIds"], c["fare"],
                 _bags([(x["passengerIndex"], c["baggage"]["weightKg"]) for x in proposal.body["baggage"]
                        if x["direction"] == b["direction"]], proposal.body["passengers"]))
                for b, c in zip(proposal.body["journeys"], proposal.card["journeys"])]
        if got != want:
            return False
        out = journeys[0]["segments"]
        back = journeys[1]["segments"] if len(journeys) == 2 else None
        return (detail.get("status") == "PENDING_PAYMENT"
                and _people(detail.get("passengers") or []) == _people(proposal.body["passengers"])
                and detail.get("totalAmount") == proposal.total
                and detail.get("voucherCode") == proposal.body["voucherCode"]
                and self.constraints is not None
                and self.constraints.is_ok(out[0]["departureAirport"], out[-1]["arrivalAirport"],
                                           out[0]["departureTime"], detail["totalAmount"],
                                           back and (back[0]["departureAirport"], back[-1]["arrivalAirport"],
                                                     back[0]["departureTime"])))

    # ---- 4. BÀN GIAO: đủ để khách nắm tình hình trong 30 giây
    def handoff(self) -> dict:
        done = [self.constraints.to_prompt()] if self.constraints else []
        done.append(f"Đã tạo booking {self.held_code} nhưng đọc lại không khớp." if self.held_code
                    else "Chưa giữ chỗ, chưa trừ tiền.")
        last = self.trace[-1]["tool"] if self.trace else "-"
        retry = "Bạn muốn thử lại hay tự đặt trên /flights?"
        c = self.constraints
        if near := self._nearest():
            n = near[0]
            legs = ", về ".join(f"{j['flights']} lúc {_when(j['depart'])}" for j in _journeys(n))
            no_match = (f"Không có chuyến nào thoả mọi ràng buộc. Gần nhất là {legs}, {_vnd(n['price'])} đ. "
                        "Bạn muốn nới giờ bay hay giá tối đa?")
        elif c and c.return_date:
            no_match = (f"Không có cặp chuyến khứ hồi nào đi ngày {c.date}, về ngày {c.return_date}. "
                        "Bạn muốn đổi ngày không?")
        else:
            no_match = f"Không có chuyến nào ngày {c.date if c else '-'}. Bạn muốn đổi sang ngày khác không?"
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
