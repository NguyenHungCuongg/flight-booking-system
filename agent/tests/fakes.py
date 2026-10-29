"""Model giả phát lại kịch bản có sẵn, và backend Spring giả bằng httpx.MockTransport."""
import json
import time
from datetime import datetime, timedelta

import httpx
from langchain_core.language_models import GenericFakeChatModel
from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, ChatResult

from flight_agent.backend import Backend


class ScriptedModel(GenericFakeChatModel):
    """Phát lại một danh sách câu trả lời cố định. Mỗi câu là một AIMessage hoặc một Exception."""

    def bind_tools(self, tools, **kwargs):
        return self

    def _next(self):
        item = next(self.messages)
        if isinstance(item, Exception):
            raise item
        return item

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        return ChatResult(generations=[ChatGeneration(message=self._next())])


def scripted(*replies):
    return ScriptedModel(messages=iter(replies))


_ids = iter(range(10**6))


def call(name, **args):
    return AIMessage(content="", tool_calls=[{"name": name, "args": args, "id": f"call-{next(_ids)}"}])


def itinerary(flight_id: int, number: str, time: str, price: int, fare_id: int = 1) -> dict:
    """Một hành trình bay thẳng SGN→DAD ngày 2026-10-30, đúng dạng Itinerary của GET /api/flights/search."""
    depart = datetime.fromisoformat(f"2026-10-30T{time}:00+07:00")
    point = lambda airport, city, at: {"airport": airport, "city": city, "time": at.isoformat(),
                                       "timezone": "Asia/Ho_Chi_Minh"}
    return {
        "stops": 0, "totalDurationMinutes": 80,
        "segments": [{"flightId": flight_id, "flightNumber": number,
                      "airline": {"code": number[:2], "name": number[:2]},
                      "departure": point("SGN", "TP. Hồ Chí Minh", depart),
                      "arrival": point("DAD", "Đà Nẵng", depart + timedelta(minutes=80)),
                      "durationMinutes": 80, "aircraftModel": "A321", "layoverMinutesAfter": None}],
        "fareOptions": [{"fareFamilyId": fare_id, "name": "Phổ thông", "cabinClass": "ECONOMY", "carryOnKg": 7,
                         "checkedBaggageKg": 0, "refundable": False, "refundFee": 0, "changeable": True,
                         "changeFee": 300_000, "adultPrice": price, "totalPrice": price, "seatsLeft": 9}],
    }


FLIGHTS = [itinerary(610, "VJ610", "14:20", 1_150_000), itinerary(122, "VN122", "08:10", 1_850_000),
           itinerary(104, "QH104", "06:00", 1_920_000), itinerary(604, "VJ604", "08:30", 2_480_000)]

AIRPORTS = [{"code": code, "name": code, "city": code, "countryCode": country, "timezone": tz, "active": True}
            for code, country, tz in (("SGN", "VN", "Asia/Ho_Chi_Minh"), ("DAD", "VN", "Asia/Ho_Chi_Minh"),
                                      ("HAN", "VN", "Asia/Ho_Chi_Minh"), ("BKK", "TH", "Asia/Bangkok"))]

PERCENT = {"ADULT": 100, "CHILD": 90, "INFANT": 10}     # BR-21, mặc định pricing.*_percent


def fare_total(price: int, types) -> int:
    return sum(price * PERCENT[t] // 100 for t in types)


CODE = "K7M2QX"
EXPIRES = "2026-10-30T06:25:00+07:00"


def passenger(last, first, gender="MALE", dob="2004-03-12", type="ADULT"):
    """Một hành khách đúng dạng PassengerView của chi tiết booking."""
    return {"type": type, "lastName": last, "firstName": first, "gender": gender, "dateOfBirth": dob,
            "nationality": "VN", "passportNumber": "B1234567", "passportExpiry": "2030-01-01", "tickets": []}


def journey(direction="OUTBOUND", flight_id=122, refundable=True, refund_fee=300_000, changeable=True,
            change_fee=300_000):
    """Một chiều đúng dạng JourneyView, một chặng lấy từ FLIGHTS."""
    s = next(s for i in FLIGHTS for s in i["segments"] if s["flightId"] == flight_id)
    return {"direction": direction, "status": "ACTIVE", "airlineCode": s["airline"]["code"],
            "fareFamilyName": "Phổ thông", "cabinClass": "ECONOMY", "carryOnKg": 7, "checkedBaggageKg": 0,
            "refundable": refundable, "refundFee": refund_fee, "changeable": changeable, "changeFee": change_fee,
            "discount": 0, "baggage": [],
            "segments": [{"flightId": flight_id, "flightNumber": s["flightNumber"],
                          "departureAirport": s["departure"]["airport"], "departureTime": s["departure"]["time"],
                          "arrivalAirport": s["arrival"]["airport"], "arrivalTime": s["arrival"]["time"],
                          "status": "SCHEDULED", "adultPrice": 1_850_000}]}


def booking(code, status="ISSUED", hold_expires=EXPIRES, total=1_850_000, journeys=None, passengers=None):
    """Một booking đúng dạng BookingDetail."""
    return {"code": code, "status": status, "holdExpiresAt": hold_expires, "issuedAt": None,
            "createdAt": "2026-10-26T15:20:00+07:00",
            "contact": {"name": "Nguyễn Hùng Cường", "email": "customer@demo.local", "phone": "0901234567"},
            "voucherCode": None, "fareTotal": total, "baggageTotal": 0, "discountTotal": 0, "totalAmount": total,
            "journeys": journeys or [journey()], "passengers": passengers or [passenger("NGUYEN", "HUNG CUONG")],
            "payments": [], "refundRequests": [], "reschedules": []}


def problem(status, code, errors=()):
    return httpx.Response(status, json={"code": code, "detail": f"Lỗi {code}", "errors": list(errors)})


class FakeBackend:
    def __init__(self, flights=FLIGHTS, me_status=200, quote_error=None, hold_error=None, detail_patch=None,
                 hold_delay=0.0, search_delay=0.0, past=(), bookings=(), refunds=None, passengers_patch=None):
        self.flights, self.me_status, self.quote_error, self.hold_error = flights, me_status, quote_error, hold_error
        self.detail_patch, self.hold_delay, self.search_delay = detail_patch or {}, hold_delay, search_delay
        self.past = dict(past)      # mã booking cũ -> hành khách, mới nhất trước
        self.bookings = {b["code"]: b for b in bookings}    # BookingDetail đầy đủ, mới nhất trước
        self.refunds = refunds or {}    # (mã, chiều) -> RefundQuote, hoặc (status, code) lỗi
        self.passengers_patch = passengers_patch    # hành khách đã gửi -> hành khách của booking đọc lại
        self.requests: list[httpx.Request] = []
        self.holds: list[dict] = []
        self.transport = httpx.MockTransport(self.handle)

    def backend(self, csrf="t") -> Backend:
        return Backend("http://be", "JSESSIONID=s1", csrf, self.transport)

    def _adult_price(self, journey):
        it = next(i for i in self.flights if [s["flightId"] for s in i["segments"]] == journey["flightIds"])
        return next(f["adultPrice"] for f in it["fareOptions"] if f["fareFamilyId"] == journey["fareFamilyId"])

    def _price(self, body):
        return fare_total(self._adult_price(body["journeys"][0]), [p["type"] for p in body["passengers"]])

    def _quote(self, body):
        """QuoteResponse một chiều: passengerFares theo thứ tự ADULT, CHILD, INFANT như QuoteService."""
        price, types = self._adult_price(body["journeys"][0]), [p["type"] for p in body["passengers"]]
        fares = [{"type": t, "count": types.count(t), "unitPrice": price * PERCENT[t] // 100,
                  "total": types.count(t) * (price * PERCENT[t] // 100)} for t in PERCENT if t in types]
        total = fare_total(price, types)
        return {"journeys": [{"direction": "OUTBOUND", "fareTotal": total, "baggageTotal": 0, "discount": 0,
                              "passengerFares": fares}],
                "fareTotal": total, "baggageTotal": 0, "discountTotal": 0, "totalAmount": total}

    def handle(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        route = (request.method, request.url.path)
        if route == ("GET", "/api/airports"):
            return httpx.Response(200, json=AIRPORTS)
        if route == ("GET", "/api/me"):
            if self.me_status != 200:
                return problem(self.me_status, "UNAUTHENTICATED" if self.me_status == 401 else "FORBIDDEN")
            return httpx.Response(200, json={"id": 3, "fullName": "Nguyễn Hùng Cường",
                                             "email": "customer@demo.local", "phone": "0901234567",
                                             "role": "CUSTOMER", "status": "ACTIVE"})
        if route == ("GET", "/api/flights/search"):
            time.sleep(self.search_delay)
            n = {k: int(request.url.params.get(k, d)) for k, d in (("adults", 1), ("children", 0), ("infants", 0))}
            types = ["ADULT"] * n["adults"] + ["CHILD"] * n["children"] + ["INFANT"] * n["infants"]
            priced = [{**i, "fareOptions": [{**f, "totalPrice": fare_total(f["adultPrice"], types)}
                                            for f in i["fareOptions"]]} for i in self.flights]
            return httpx.Response(200, json={"itineraries": priced})
        if route == ("POST", "/api/bookings/quote"):
            if self.quote_error:
                return problem(*self.quote_error)
            return httpx.Response(200, json=self._quote(json.loads(request.content)))
        if route == ("POST", "/api/bookings"):
            body = json.loads(request.content)
            self.holds.append(body)
            time.sleep(self.hold_delay)
            if self.hold_error and len(self.holds) == 1:
                return problem(*self.hold_error)
            return httpx.Response(201, json={"code": CODE, "status": "PENDING_PAYMENT", "holdExpiresAt": EXPIRES,
                                             "totalAmount": self._price(body)})
        if request.method == "GET" and (key := request.url.path.removeprefix("/api/bookings/")) in self.bookings:
            return httpx.Response(200, json=self.bookings[key])     # trước CODE: booking dựng sẵn có thể trùng mã
        if route == ("GET", f"/api/bookings/{CODE}"):
            return httpx.Response(200, json=self._detail())
        if route == ("GET", "/api/bookings"):
            size = int(request.url.params.get("size", 20))
            content = [_summary(b) for b in self.bookings.values()] + [{"code": c} for c in self.past]
            return httpx.Response(200, json={"content": content[:size],
                                             "page": {"size": size, "number": 0, "totalElements": len(content)}})
        if request.method == "GET" and request.url.path.endswith("/refund-quote"):
            _, _, _, code, _, direction, _ = request.url.path.split("/")
            quote = self.refunds.get((code, direction))
            if isinstance(quote, tuple):
                return problem(*quote)
            return httpx.Response(200, json=quote) if quote else problem(404, "NOT_FOUND")
        if request.method == "GET" and (code := request.url.path.removeprefix("/api/bookings/")) in self.past:
            return httpx.Response(200, json={"code": code, "status": "ISSUED", "passengers": self.past[code]})
        return problem(404, "NOT_FOUND")

    def _detail(self):
        body = self.holds[-1]
        journey = body["journeys"][0]
        by_id = {s["flightId"]: s for i in self.flights for s in i["segments"]}
        segments = [{"flightId": f, "flightNumber": by_id[f]["flightNumber"],
                     "departureAirport": by_id[f]["departure"]["airport"],
                     "departureTime": by_id[f]["departure"]["time"],
                     "arrivalAirport": by_id[f]["arrival"]["airport"], "arrivalTime": by_id[f]["arrival"]["time"]}
                    for f in journey["flightIds"]]
        return {"code": CODE, "status": "PENDING_PAYMENT", "holdExpiresAt": EXPIRES,
                "totalAmount": self._price(body), "contact": body["contact"],
                "journeys": [{"direction": journey["direction"], "segments": segments}],
                "passengers": (self.passengers_patch or list)(body["passengers"]), **self.detail_patch}


def _summary(b: dict) -> dict:
    """BookingSummary của một BookingDetail."""
    return {"code": b["code"], "status": b["status"], "contactName": b["contact"]["name"],
            "totalAmount": b["totalAmount"], "holdExpiresAt": b["holdExpiresAt"], "createdAt": b["createdAt"],
            "routes": [{"direction": j["direction"], "departureAirport": j["segments"][0]["departureAirport"],
                        "arrivalAirport": j["segments"][-1]["arrivalAirport"],
                        "departureTime": j["segments"][0]["departureTime"]} for j in b["journeys"]]}
