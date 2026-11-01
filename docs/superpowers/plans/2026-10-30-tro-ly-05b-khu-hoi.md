# Trợ lý Bước 5b: Khứ hồi Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khách đặt một booking khứ hồi trong một lượt chat. Thẻ duyệt hiện từng chiều (chặng, gói giá, giá từng loại khách), và `is_done` kiểm cả `OUTBOUND` lẫn `RETURN` của booking đọc lại, kể cả gói giá của từng chiều.

**Architecture:** `Constraints` có thêm `return_date`. `search_flights` gọi tìm chuyến hai lần (đi, rồi về theo chiều ngược lại), ghép mọi cặp thoả BR-04 thành một mục có `outbound`, `return` và `price` là tổng hai chiều; kết quả một chiều giữ nguyên dạng cũ. `propose_hold` nhận thêm `return_flight_ids`, `return_fare_family_id`, dựng body hai chiều từ đúng mục đã tìm. Thẻ duyệt đổi sang danh sách `journeys`. `is_done` so từng chiều (hướng, chuyến, tên gói giá).

**Tech Stack:** Python, LangChain, pytest với `httpx.MockTransport` (agent); Next.js 16, TypeScript (frontend).

**Spec:** [docs/superpowers/specs/2026-10-26-tro-ly-dat-ve-agent-design.md](../specs/2026-10-26-tro-ly-dat-ve-agent-design.md) · Lộ trình: [Bước 5](2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md#bước-5--mở-rộng-phạm-vi-đặt-vé) · Plan trước: [5a](2026-10-29-tro-ly-05a-nhieu-hanh-khach.md)

## Global Constraints

- Nhánh `feature/flight-agent`. Không sửa `backend/`.
- Vẫn hạng phổ thông, không hành lý, không voucher (5c làm sau).
- Đã quyết (chủ dự án, 2026-10-29): `max_price` là tổng cả booking, tức mọi hành khách và cả hai chiều. So với `price` của mục khứ hồi (tổng `totalPrice` của hai chiều) và `totalAmount` của báo giá. Tìm cả hai chiều trong một lần gọi `search_flights`.
- Quyết trong plan này: `depart_before` chỉ áp cho chiều đi. Không có ràng buộc giờ cho chiều về. Thêm `return_depart_before` khi eval hoặc khách thật cần.
- Chiều về tìm `destination → origin` vào `return_date`, cùng số khách. `return_date` không được trước `date`; về trong ngày thì được.
- BR-04: chỉ ghép cặp có giờ cất cánh của chặng đầu chiều về **sau** giờ hạ cánh của chặng cuối chiều đi. So bằng `datetime.fromisoformat` vì chuỗi có offset. Backend kiểm lại; lỗi trả về dạng `backend_error` như MVP.
- Loại khách vẫn tính tại `constraints.date`, vì backend tính tuổi theo ngày cất cánh của chặng đầu (`QuoteService` gọi `PassengerRules.checkDetails` với `segments.getFirst()`).
- Mỗi mục trong kết quả `search_flights` một chiều giữ đúng các khóa cũ, để case eval cũ không tụt. Mục khứ hồi lồng nhau: `{"outbound": chiều, "return": chiều, "price": tổng, "broken": [...]}`, mỗi chiều có đúng các khóa của mục một chiều trừ `broken`.
- `matching` tối đa `MATCHING = 5` mục, cho cả hai kiểu (khứ hồi ghép mọi cặp nên số mục tăng theo tích). `found` vẫn giữ mọi mục để kiểm quyền.
- Thẻ `proposal` thay `segments`, `fare`, `fares` bằng `journeys: [{direction, segments, fare, fares}]`, dùng cho cả một chiều. Thẻ hiện mọi khoản bị tính của từng chiều.
- `is_done` so tên gói giá (`fareFamilyName` của `JourneyView` với `fare` của thẻ). Đây là việc hoãn từ review 5a. Tên gói không trùng trong cùng một hãng (seed `V102`), và chuyến đã khớp thì hãng cũng khớp.
- Eval (chủ dự án chọn, 2026-10-30): 5b chỉ chạy case mới bằng `--case khu-hoi`, k=3.

## Review Focus

1. **`return_date` trước `date` hoặc sai định dạng** ("02/11/2026"): `invalid_param`, ràng buộc cũ giữ nguyên.
2. **Về trong ngày mà chuyến về cất cánh trước khi chuyến đi hạ cánh**: không được ghép (VJ610 hạ cánh 15:40, VN127 cất cánh 12:00).
3. **`propose_hold` thiếu, thừa hoặc chỉ có nửa tham số chiều về** (khứ hồi không có `return_flight_ids`; có `return_flight_ids` mà không có `return_fare_family_id`; một chiều mà có tham số chiều về): `denied`, không gọi báo giá.
4. **Booking đọc lại mất chiều về, sai chuyến về, hoặc sai gói giá** (cả khi một chiều): `is_done` sai, bàn giao `hold_mismatch`.
5. **Ngày về không có chuyến nào**: bàn giao `no_match` nêu cả hai ngày, không nói chung chung "không có chuyến nào ngày đi".

## File structure

```
agent/flight_agent/core.py     Constraints.return_date, broken(back=), set_constraints, search_flights, handoff (Task 1);
                               propose_hold(return_*), check_permission, is_done (Task 2)
agent/tests/fakes.py           itinerary() theo tuyến và ngày, RETURNS, tìm chuyến lọc theo tuyến và ngày (Task 1);
                               báo giá, giá và chi tiết nhiều chiều, journeys_patch (Task 2)
agent/tests/test_core.py       Task 1
agent/tests/test_harness.py    Task 1, 2
agent/tests/test_chat.py       Task 2
frontend/src/app/assistant/agent-api.ts       kiểu thẻ (Task 3)
frontend/src/app/assistant/assistant-chat.tsx thẻ ràng buộc, thẻ duyệt, câu giới thiệu (Task 3)
agent/flight_agent/react.py    quy tắc prompt (Task 4)
agent/evals/cases.py, run.py   Case.return_flight_ids, sửa case khu-hoi, 4 case nhóm "khứ hồi" (Task 4)
agent/tests/test_evals.py      Task 4
agent/README.md, roadmap       Task 4
```

---

### Task 1: Ngày về trong ràng buộc và tìm cả hai chiều

**Files:** `agent/flight_agent/core.py`, `agent/tests/fakes.py`, `agent/tests/test_core.py`, `agent/tests/test_harness.py`, `agent/evals/cases.py` (chỉ docstring)

**Produces:**

- `Constraints.return_date: str | None = None` (đặt sau `infants`).
- `Constraints.broken(origin, destination, departure, price, back: tuple[str, str, str] | None = None) -> list[str]`, với `back` = (sân bay đi, sân bay đến, giờ cất cánh ISO) của chiều về. Có thêm tên vi phạm `"return_date"`. `is_ok` nhận cùng tham số.
- `set_constraints(..., return_date: str | None = None)`. Thẻ `constraints` có `returnDate`.
- Mục khứ hồi trong `found`/`matching`/`nearest`: `{"outbound", "return", "price", "broken"}`.
- Hàm module `_journeys(item: dict) -> list[dict]`: `[item]` nếu một chiều, `[item["outbound"], item["return"]]` nếu khứ hồi.
- `fakes.RETURNS`. `FakeBackend(returns=RETURNS)`, `FakeBackend.pool` (= `flights + returns`). `itinerary(..., origin="SGN", destination="DAD", day="2026-10-30")`.

- [ ] **Step 1: Write the failing tests**

`test_core.py`:

```python
RT = Constraints("SGN", "DAD", "2026-10-30", return_date="2026-11-02")
BACK = ("DAD", "SGN", "2026-11-02T17:00:00+07:00")


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
```

`test_harness.py`:

```python
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
        assert h.execute("set_constraints", {**TRIP, "return_date": bad})["status"] == "invalid_param"
    assert h.constraints is None


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
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd agent && uv run pytest tests/test_core.py tests/test_harness.py -q`
Expected: FAIL (`Constraints` không có `return_date`; `set_constraints` không nhận `return_date`).

- [ ] **Step 3: Implement `fakes.py`**

- `itinerary(flight_id, number, time, price, fare_id=1, origin="SGN", destination="DAD", day="2026-10-30")`. Tên thành phố lấy từ `CITY = {"SGN": "TP. Hồ Chí Minh", "DAD": "Đà Nẵng"}`. Docstring bỏ "SGN→DAD ngày 2026-10-30".
- `RETURNS` (sau `FLIGHTS`):

```python
BACK = dict(origin="DAD", destination="SGN", day="2026-11-02")
RETURNS = [itinerary(611, "VJ611", "20:00", 1_050_000, **BACK), itinerary(123, "VN123", "17:00", 1_750_000, **BACK),
           itinerary(127, "VN127", "12:00", 1_500_000, origin="DAD", destination="SGN")]     # về trong ngày 30/10
```

- `FakeBackend.__init__` nhận `returns=RETURNS`. Property `pool` trả `self.flights + self.returns`. `GET /api/flights/search` chỉ trả các hành trình của `pool` có sân bay đi của chặng đầu, sân bay đến của chặng cuối và ngày cất cánh (`time[:10]`) trùng `from`, `to`, `date` (như backend thật), rồi tính giá theo số khách như cũ.
- Docstring đầu `evals/cases.py`: đổi "trả cùng 4 chuyến SGN→DAD ngày 2026-10-30 cho mọi lần tìm" thành "trả đúng tuyến và ngày: 4 chuyến SGN→DAD ngày 2026-10-30, 2 chuyến về DAD→SGN ngày 2026-11-02 và 1 chuyến về trong ngày 30/10".

- [ ] **Step 4: Implement `core.py`**

`Constraints`:

- `to_prompt`: sau ngày đi thêm `f", về ngày {self.return_date}"` nếu có, không có thì `", một chiều"`. Đổi "cất cánh trước" thành "chiều đi cất cánh trước", và "giá tối đa (tổng cả booking)" thành "giá tối đa (tổng cả booking, mọi chiều)".
- `broken`: thêm check `("return_date", ok)`. `ok` khi không có `return_date` và `back is None`, hoặc khi có `return_date` và `back` là `(destination, origin, ...)` với `back[2][:10] == return_date`.

`set_constraints`:

- Docstring thêm: "return_date: ngày cất cánh chiều về YYYY-MM-DD nếu khách cần khứ hồi, không trước date; bỏ trống nếu một chiều. depart_before chỉ áp cho chiều đi; max_price là tổng cả hai chiều."
- Tách việc đọc ngày thành `_day(text) -> Date | None` (dùng `re.fullmatch` rồi `Date.fromisoformat` như hiện tại, sai thì `None`) và dùng cho cả `date` lẫn `return_date`. `return_date` sai dạng hoặc trước `date` thì `invalid_param` với hint `f"return_date phải là ngày YYYY-MM-DD, không trước ngày đi {date}."`.
- Thẻ thêm `"returnDate": c.return_date`.

`search_flights`:

- Tách phần rút gọn hành trình ra `_legs(self, origin, destination, day) -> list[dict]`. Hàm này gọi một `GET /api/flights/search` và trả mỗi hành trình có gói giá một dict đúng các khóa cũ, trừ `broken`.
- Một chiều: `found = [{**leg, "broken": c.broken(...)}]` như cũ.
- Khứ hồi: `_legs` thêm một lần cho `(c.destination, c.origin, c.return_date)`. `found` gồm mọi cặp `(o, r)` thoả BR-04 (`datetime.fromisoformat(r["depart"]) > datetime.fromisoformat(o["segments"][-1]["arrive"])`), dạng `{"outbound": o, "return": r, "price": o["price"] + r["price"], "broken": c.broken(o["segments"][0]["from"], o["segments"][-1]["to"], o["depart"], o["price"] + r["price"], back=(r["segments"][0]["from"], r["segments"][-1]["to"], r["depart"]))}`.
- `matching = sorted(...)[:MATCHING]`, thêm `MATCHING = 5` vào nhóm hằng số đầu file (chú thích: khứ hồi ghép mọi cặp nên số mục tăng theo tích).
- Docstring thêm: "Khứ hồi: mỗi mục có outbound và return (mỗi chiều dạng như một hành trình một chiều), price là tổng hai chiều; chỉ ghép cặp chiều về cất cánh sau khi chiều đi hạ cánh. matching tối đa 5 mục."

`_journeys(item)` như ở Produces.

`handoff`, câu `no_match`:

- Có `nearest`: `f"Gần nhất là {', về '.join(f'{j['flights']} lúc {_when(j['depart'])}' for j in _journeys(n))}, {_vnd(n['price'])} đ."`, rồi giữ câu hỏi nới giờ bay hay giá như cũ.
- Không có `nearest`: một chiều giữ nguyên câu cũ. Khứ hồi: `f"Không có cặp chuyến khứ hồi nào đi ngày {c.date}, về ngày {c.return_date}. Bạn muốn đổi ngày không?"`.

- [ ] **Step 5: Run to verify they pass**

Run: `cd agent && uv run pytest -q`
Expected: tất cả PASS, gồm các test một chiều cũ.

- [ ] **Step 6: Commit**

```bash
git add agent/
git commit -m "feat(agent): ngày về trong ràng buộc, tìm và ghép cặp chuyến khứ hồi"
```

---

### Task 2: Đề xuất hai chiều và `is_done` so từng chiều

**Files:** `agent/flight_agent/core.py`, `agent/tests/fakes.py`, `agent/tests/test_harness.py`, `agent/tests/test_chat.py`

**Consumes:** Task 1 `_journeys`, mục khứ hồi trong `found`, `Constraints.broken(back=)`/`is_ok(back=)`, `fakes.RETURNS`, `FakeBackend.pool`.

**Produces:**

- `propose_hold(flight_ids, fare_family_id, passengers, return_flight_ids: list[int] | None = None, return_fare_family_id: int | None = None)`.
- `_found(self, flight_ids, fare_family_id, return_flight_ids=None, return_fare_family_id=None) -> dict | None`.
- Thẻ `proposal`: `journeys: [{"direction": "OUTBOUND" | "RETURN", "segments", "fare", "fares"}]` thay cho `segments`, `fare`, `fares`.
- `FakeBackend(journeys_patch=None)`: hàm nhận danh sách chiều của booking đọc lại và trả danh sách mới.

- [ ] **Step 1: Write the failing tests**

`test_harness.py`. Sửa `test_family_hold_types_each_passenger_and_shows_fares`: `card["fares"]` thành `card["journeys"][0]["fares"]`. Rồi thêm:

```python
ROUND = dict(flight_ids=[122], fare_family_id=1, return_flight_ids=[611], return_fare_family_id=1)


def round_trip():
    h, fake = ready()
    h.execute("set_constraints", TRIP)
    h.execute("search_flights", {})
    return h, fake


def test_round_trip_hold_quotes_both_journeys_and_shows_each():
    h, _ = round_trip()
    r = h.execute("propose_hold", {**ROUND, "passengers": [PAX]})
    assert r == {"status": "pending_approval", "total": 2_900_000}
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
```

`test_chat.py`. `proposed()` nhận thêm `choice=None` (mặc định `dict(flight_ids=[122], fare_family_id=1)`) và gọi `call("propose_hold", **choice, passengers=list(passengers))`. Sửa `test_sold_out_on_approval_lets_the_agent_propose_another_flight`: `proposals[0]["segments"]` thành `proposals[0]["journeys"][0]["segments"]`. Rồi thêm:

```python
TRIP = {**ROUTE, "max_price": 4_000_000, "return_date": "2026-11-02"}
ROUND = dict(flight_ids=[122], fare_family_id=1, return_flight_ids=[611], return_fare_family_id=1)


def test_round_trip_approval_holds_both_journeys():
    fake = FakeBackend()
    chat, model, out = proposed(fake, route=TRIP, choice=ROUND)
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    assert [j["direction"] for j in fake.holds[0]["journeys"]] == ["OUTBOUND", "RETURN"]
    assert [(c["type"], c["totalAmount"]) for c in result["cards"]] == [("held", 2_900_000)]


def test_read_back_with_other_journeys_hands_off():
    moved = lambda j, flight_id: {**j, "segments": [{**j["segments"][0], "flightId": flight_id}]}
    for patch in (lambda js: js[:1],                                        # mất chiều về
                  lambda js: [js[0], moved(js[1], 123)],                    # sai chuyến về
                  lambda js: [js[0], {**js[1], "fareFamilyName": "Eco"}],   # sai gói chiều về
                  lambda js: js[::-1]):                                     # đảo thứ tự hai chiều
        fake = FakeBackend(journeys_patch=patch)
        chat, model, out = proposed(fake, route=TRIP, choice=ROUND)
        result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
        assert [c["type"] for c in result["cards"]] == ["handoff"]


def test_one_way_read_back_with_another_fare_hands_off():
    fake = FakeBackend(journeys_patch=lambda js: [{**js[0], "fareFamilyName": "Eco"}])
    chat, model, out = proposed(fake)
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    assert [c["type"] for c in result["cards"]] == ["handoff"]
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd agent && uv run pytest -q`
Expected: FAIL (`propose_hold` không nhận `return_flight_ids`; thẻ chưa có `journeys`; `FakeBackend` không nhận `journeys_patch`).

- [ ] **Step 3: Implement `fakes.py`**

- `_adult_price`, `_detail` tra chuyến trong `self.pool` thay vì `self.flights`.
- `_price(body)`: cộng `fare_total` của mọi chiều.
- `_quote(body)`: mỗi chiều của body một mục `journeys` (đúng `direction` của chiều đó, `passengerFares` tính theo giá chiều đó). `fareTotal` và `totalAmount` là tổng các chiều.
- `_detail()`: mỗi chiều của body một `JourneyView` rút gọn `{"direction", "fareFamilyName", "segments"}`. `fareFamilyName` là `name` của `fareOptions` có `fareFamilyId` đã gửi. Áp `journeys_patch` nếu có, như `passengers_patch`.

- [ ] **Step 4: Implement `core.py`**

- `DIRECTIONS = ("OUTBOUND", "RETURN")` vào nhóm hằng số.
- `_found(...)`: so `[(j["flight_ids"], j["fare_family_id"]) for j in _journeys(i)]` với danh sách chiều khách chọn. Danh sách đó là `[(flight_ids, fare_family_id)]`, thêm `(return_flight_ids, return_fare_family_id)` nếu một trong hai tham số chiều về khác `None`.
- `check_permission`: gọi `_found` với bốn tham số lấy từ `args.get(...)`. Nếu ràng buộc là khứ hồi thì hint thêm "Khứ hồi: return_flight_ids và return_fare_family_id lấy từ return của cùng mục trong matching."
- `propose_hold`:
  - Docstring thêm: "Khứ hồi: return_flight_ids, return_fare_family_id lấy từ return của cùng mục trong matching."
  - Lấy `it = self._found(...)` trước khi dựng body. `body["journeys"]` dựng từ `zip(DIRECTIONS, _journeys(it))` (`direction`, `flightIds`, `fareFamilyId`).
  - Thẻ: `"journeys"` dựng từ `zip(DIRECTIONS, _journeys(it), quote["journeys"])`, mỗi mục `{"direction", "segments": j["segments"], "fare": j["fare"], "fares": q["passengerFares"]}`. Bỏ `segments`, `fare`, `fares` ở mức thẻ.
- `is_done`: thay hai dòng kiểm hành trình bằng phép so từng chiều. `got = [(j.get("direction"), [s["flightId"] for s in j.get("segments") or []], j.get("fareFamilyName")) for j in detail journeys]` phải bằng `want = [(b["direction"], b["flightIds"], c["fare"]) for b, c in zip(proposal.body["journeys"], proposal.card["journeys"])]`. Giữ các kiểm khác (trạng thái, hành khách, tổng tiền, có ràng buộc). `is_ok` nhận chặng đầu và cuối của chiều đi, cùng `back=(sân bay đi, sân bay đến, giờ cất cánh)` của chiều về nếu booking có hai chiều.

- [ ] **Step 5: Run to verify they pass**

Run: `cd agent && uv run pytest -q`
Expected: tất cả PASS.

- [ ] **Step 6: Commit**

```bash
git add agent/
git commit -m "feat(agent): đề xuất giữ chỗ khứ hồi, is_done so từng chiều và gói giá"
```

---

### Task 3: Thẻ trên trang `/assistant`

**Files:** `frontend/src/app/assistant/agent-api.ts`, `frontend/src/app/assistant/assistant-chat.tsx`

**Consumes:** Task 1 thẻ `constraints` có `returnDate`; Task 2 thẻ `proposal` có `journeys`.

- [ ] **Step 1: Kiểu thẻ.**
  - `constraints` thêm `returnDate: string | null`.
  - `proposal` bỏ `segments`, `fare`, `fares`, thêm `journeys: { direction: "OUTBOUND" | "RETURN"; segments: Segment[]; fare: string; fares: { type: PassengerType; count: number; unitPrice: number; total: number }[] }[]`.

- [ ] **Step 2: Vẽ thẻ.**
  - Chip ngày của thẻ ràng buộc: `"Đi {dmy(date)}, về {dmy(returnDate)}"` khi có `returnDate`, không có thì giữ như cũ.
  - Thẻ duyệt: mỗi chiều một khối. Khối có tiêu đề `DIRECTION_LABEL[direction]` (từ `@/lib/booking`), danh sách chặng như cũ, rồi dòng "Gói giá" và "Giá vé" của chiều đó. Hai dòng này dùng lại markup của `dl` hiện có. Sau các khối là "Hành khách", "Liên hệ" và tổng tiền như cũ.
  - Câu giới thiệu khi chat trống vẫn ghi "một chiều cho một người lớn", cũ từ trước 5a. Đổi thành: "Trợ lý đặt được vé một chiều hoặc khứ hồi, hạng phổ thông, cho nhiều hành khách, và chỉ giữ chỗ khi bạn bấm “Giữ chỗ”."

- [ ] **Step 3: Verify**

Run: `cd frontend && npm run lint && npx tsc --noEmit`
Expected: không lỗi.

Bằng tay (khi có backend profile `dev`, agent và `npm run dev`): seed không có tuyến SGN–DAD, nên gõ "Đặt vé khứ hồi SGN đi HAN, đi <ngày kia>, về <3 ngày sau>, cho <họ tên, giới tính, ngày sinh>". Thẻ duyệt phải có hai khối "Chiều đi", "Chiều về" và tổng bằng hai chiều cộng lại. F5 vẫn thấy đủ thẻ. Bấm "Giữ chỗ" ra thẻ "Đã giữ chỗ".

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/assistant/
git commit -m "feat(assistant): thẻ duyệt hiện từng chiều, thẻ ràng buộc có ngày về"
```

---

### Task 4: Prompt, case eval và chạy eval khứ hồi

**Files:** `agent/flight_agent/react.py`, `agent/evals/cases.py`, `agent/evals/run.py`, `agent/tests/test_evals.py`, `agent/README.md`, `docs/superpowers/plans/2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md`

**Consumes:** Task 1, 2 (tham số `set_constraints`, `propose_hold`, body hai chiều).

**Produces:** `Case.return_flight_ids: list[int] | None = None`, trường cuối của dataclass.

- [ ] **Step 1: Write the failing tests** (`test_evals.py`)

```python
TRIP = {**ROUTE, "max_price": 4_000_000, "return_date": "2026-11-02"}


def test_cases_cover_round_trips():
    group = [c for c in CASES if c.group == "khứ hồi"]
    assert len(group) == 5 and all(c.name.startswith("khu-hoi") for c in group)     # --case khu-hoi chạy đủ nhóm
    assert all(c.constraints["return_date"] == "2026-11-02" for c in group)
    assert all(c.return_flight_ids for c in group if c.end == "proposal")


def test_return_flight_is_graded():
    def trip(back):
        return scripted(call("set_constraints", **TRIP), call("search_flights"),
                        call("propose_hold", flight_ids=[122], fare_family_id=1, return_flight_ids=[back],
                             return_fare_family_id=1, passengers=[PAX]), AIMessage("Bạn bấm Giữ chỗ trên thẻ nhé."))
    case = happy(constraints=TRIP, return_flight_ids=[611])
    assert run_case(case, trip(611))["passed"]
    r = run_case(case, trip(123))
    assert not r["passed"] and any("return_flight_ids" in f for f in r["failures"])
```

Trong `test_cases_are_well_formed` thêm `assert c.return_flight_ids is None or "proposal" in ends, c.name`.

- [ ] **Step 2: Run to verify they fail**

Run: `cd agent && uv run pytest tests/test_evals.py -q`
Expected: FAIL (`Case` không có `return_flight_ids`; chưa có nhóm "khứ hồi").

- [ ] **Step 3: Case và cách chấm.**

`Case` thêm trường `return_flight_ids: list[int] | None = None` ở **cuối** dataclass. Các case dựng `Case(...)` theo vị trí tới `passengers`, nên chèn giữa sẽ làm lệch tham số.

`run.py::grade`: khi có đề xuất và `case.return_flight_ids` khác `None`, lấy `flightIds` của chiều thứ hai trong body (không có thì `None`). Khác mong đợi thì thêm lỗi `f"return_flight_ids: được {got}"`.

Sửa `khu-hoi`: `group="khứ hồi"`, `constraints=R(return_date="2026-11-02")`, `reply_has=["sinh"]`. Khách chưa cho thông tin hành khách nên model phải hỏi. Thêm 4 case nhóm "khứ hồi", với `RT = "2026-11-02"`:

| Tên                     | Lượt của khách                                                                                      | Kết quả mong đợi                                                                        |
| ----------------------- | --------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `khu-hoi-du-thong-tin`  | `f"Đặt vé khứ hồi SGN đi DAD, đi 30/10 bay trước 12h, về 2/11, cho {AN}"`                           | `proposal`, `R(depart_before="12:00", return_date=RT)`, `[122]`, về `[611]`, `[AN_PAX]` |
| `khu-hoi-tong-gia`      | `f"Vé khứ hồi Sài Gòn - Đà Nẵng, đi 30/10, về 2/11, tổng cả hai chiều không quá 2tr5, cho {AN}"`    | `proposal`, `R(max_price=2_500_000, return_date=RT)`, `[610]`, về `[611]`, `[AN_PAX]`   |
| `khu-hoi-qua-re`        | `"Khứ hồi SGN - DAD đi 30/10, về 2/11, tổng dưới 2 triệu"`                                          | `no_match`, `R(max_price=2_000_000, return_date=RT)`                                    |
| `khu-hoi-them-chieu-ve` | `[f"Đặt vé SGN đi DAD ngày 30/10, bay trước 12h, cho {AN}", "À mình mua luôn vé về ngày 2/11 nhé"]` | `proposal`, `R(depart_before="12:00", return_date=RT)`, `[122]`, về `[611]`, `[AN_PAX]` |

Đáp án tính từ backend giả, cho 1 người lớn. VN122 + VJ611 = 2.900.000 là cặp rẻ nhất thoả giờ bay. Chỉ VJ610 + VJ611 = 2.200.000 dưới 2,5 triệu. Không cặp nào dưới 2 triệu.

- [ ] **Step 4: Prompt.** Trong `SYSTEM_PROMPT`:
  - Câu phạm vi đổi thành: "Đặt vé một chiều hoặc khứ hồi, hạng phổ thông. Khứ hồi thì truyền return_date vào set_constraints; depart_before chỉ áp cho chiều đi."
  - Câu `max_price` đổi "max_price là tổng tiền cả booking" thành "max_price là tổng tiền cả booking (mọi hành khách, cả hai chiều nếu khứ hồi)".
  - Câu `propose_hold` thêm: "Khứ hồi thì thêm return_flight_ids và return_fare_family_id lấy từ return của cùng mục đó."

- [ ] **Step 5: Run the whole suite**

Run: `cd agent && uv run pytest -q`
Expected: tất cả PASS.

- [ ] **Step 6: Chạy eval với model thật**

Run: `cd agent && uv run python -m evals.run --case khu-hoi > <workspace>/eval.txt`
Expected: 5 case nhóm "khứ hồi", mỗi case 3/3. Case nào trượt thì đọc trace trong `evals/results/` rồi sửa prompt, sau đó chạy lại đúng case đó. Ví dụ `khu-hoi-them-chieu-ve` mất `depart_before` ở lượt 2 thì thêm vào câu `set_constraints`: "set_constraints thay toàn bộ ràng buộc: truyền lại các ràng buộc cũ còn giữ." Sửa prompt thì chạy lại thêm `--case doi` và `--case thuan` để chắc case cũ không tụt. Không chạy đủ bộ: theo lộ trình, đủ bộ chạy sau 5c.

- [ ] **Step 7: Tài liệu.**
  - README, đoạn phạm vi: trợ lý đặt được vé khứ hồi. Hai chiều tìm trong một lần `search_flights` và được ghép thành cặp thoả BR-04. `max_price` là tổng hai chiều, `depart_before` chỉ áp cho chiều đi. Thẻ duyệt hiện từng chiều, và `is_done` so hướng, chuyến và gói giá của từng chiều.
  - Lộ trình, §2: gắn link plan này vào dòng 5b và ghi "5b xong" (chưa tick cả Bước 5).
  - Lộ trình, §3: thêm một dòng, cột "Số case" ghi "5 (chỉ khứ hồi)".

- [ ] **Step 8: Commit**

```bash
git add agent/ docs/superpowers/plans/
git commit -m "feat(agent): prompt và case eval khứ hồi"
```
