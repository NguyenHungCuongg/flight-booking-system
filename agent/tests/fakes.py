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

CODE = "K7M2QX"
EXPIRES = "2026-10-30T06:25:00+07:00"


def problem(status, code, errors=()):
    return httpx.Response(status, json={"code": code, "detail": f"Lỗi {code}", "errors": list(errors)})


class FakeBackend:
    def __init__(self, flights=FLIGHTS, me_status=200, quote_error=None, hold_error=None, detail_patch=None,
                 hold_delay=0.0, search_delay=0.0):
        self.flights, self.me_status, self.quote_error, self.hold_error = flights, me_status, quote_error, hold_error
        self.detail_patch, self.hold_delay, self.search_delay = detail_patch or {}, hold_delay, search_delay
        self.requests: list[httpx.Request] = []
        self.holds: list[dict] = []
        self.transport = httpx.MockTransport(self.handle)

    def backend(self, csrf="t") -> Backend:
        return Backend("http://be", "JSESSIONID=s1", csrf, self.transport)

    def _price(self, journey):
        it = next(i for i in self.flights if [s["flightId"] for s in i["segments"]] == journey["flightIds"])
        return next(f["totalPrice"] for f in it["fareOptions"] if f["fareFamilyId"] == journey["fareFamilyId"])

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
            return httpx.Response(200, json={"itineraries": self.flights})
        if route == ("POST", "/api/bookings/quote"):
            if self.quote_error:
                return problem(*self.quote_error)
            body = json.loads(request.content)
            return httpx.Response(200, json={"totalAmount": self._price(body["journeys"][0])})
        if route == ("POST", "/api/bookings"):
            body = json.loads(request.content)
            self.holds.append(body)
            time.sleep(self.hold_delay)
            if self.hold_error and len(self.holds) == 1:
                return problem(*self.hold_error)
            return httpx.Response(201, json={"code": CODE, "status": "PENDING_PAYMENT", "holdExpiresAt": EXPIRES,
                                             "totalAmount": self._price(body["journeys"][0])})
        if route == ("GET", f"/api/bookings/{CODE}"):
            return httpx.Response(200, json=self._detail())
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
                "totalAmount": self._price(journey), "contact": body["contact"],
                "journeys": [{"direction": journey["direction"], "segments": segments}],
                "passengers": body["passengers"], **self.detail_patch}
