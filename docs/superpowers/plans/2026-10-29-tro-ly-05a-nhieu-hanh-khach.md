# Trợ lý Bước 5a: Nhiều hành khách Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khách đặt một booking cho nhiều người (người lớn, trẻ em, em bé). Thẻ duyệt hiện từng hành khách và giá theo từng loại khách, `is_done` kiểm đúng từng hành khách của booking đọc lại.

**Architecture:** `Constraints` có thêm `adults`, `children`, `infants` (mặc định 1, 0, 0), `search_flights` gửi đúng số khách nên `totalPrice` là tổng cả booking. `propose_hold` nhận danh sách hành khách; code tính loại khách từ ngày sinh tại ngày bay (BR-10) và từ chối nếu số khách theo loại khác ràng buộc. `is_done` so tập hành khách (loại, họ, tên, giới tính, ngày sinh) của booking đọc lại với body đã duyệt. Frontend vẽ thẻ ràng buộc có số khách và thẻ duyệt có danh sách hành khách cùng giá từng loại.

**Tech Stack:** Python, LangChain, pytest với `httpx.MockTransport` (agent); Next.js 16, TypeScript (frontend).

**Spec:** [docs/superpowers/specs/2026-10-26-tro-ly-dat-ve-agent-design.md](../specs/2026-10-26-tro-ly-dat-ve-agent-design.md) · Lộ trình: [Bước 5](2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md#bước-5--mở-rộng-phạm-vi-đặt-vé)

## Global Constraints

- Nhánh `feature/flight-agent`. Không sửa `backend/`.
- Vẫn một chiều, hạng phổ thông, không hành lý, không voucher (5b, 5c làm sau).
- `max_price` là tổng tiền cả booking: so với `totalPrice` của kết quả tìm (đã tính cho đủ số khách, BR-21) và `totalAmount` của báo giá. Khách nói giá mỗi vé thì model nhân với số khách chiếm ghế (người lớn + trẻ em).
- BR-11 (ít nhất 1 người lớn, em bé không quá số người lớn, tối đa `booking.max_seated_passengers` khách chiếm ghế) do backend kiểm khi tìm chuyến và báo giá; lỗi về model dạng `backend_error`. Harness chỉ chặn `adults < 1` hoặc số âm hay không phải số nguyên. Không cứng hoá số 9 vì đó là cài đặt.
- Loại khách do code tính, model không truyền: tuổi tròn năm tại `constraints.date` (ngày cất cánh chặng đầu, giờ địa phương), `ADULT` ≥ 12, `CHILD` 2–11, `INFANT` < 2, giống `PassengerRules.typeAt`. Backend vẫn là nơi kiểm cuối.
- Thẻ duyệt hiện mọi khoản bị tính: từng hành khách kèm loại, và `passengerFares` của báo giá (loại, số người, đơn giá, thành tiền).
- `is_done` so tập hành khách không theo thứ tự (backend có thể sắp lại).

## Review Focus

1. **Số khách theo ngày sinh khác ràng buộc** (ràng buộc 2 người lớn nhưng một người sinh 2018): `invalid_param`, không gọi báo giá.
2. **Ngày sinh sau ngày bay hoặc sai định dạng**: `invalid_param` chỉ rõ hành khách thứ mấy.
3. **Booking đọc lại thiếu một hành khách, thừa một hành khách, hoặc đổi loại khách**: `is_done` sai, bàn giao `hold_mismatch`.
4. **Sinh nhật đúng ngày bay** (tròn 2 hoặc 12 tuổi đúng hôm đó): tính lên loại lớn hơn, như `Period.between`.
5. **Model đổi số khách mà không tìm lại**: `set_constraints` xoá `found`, nên `propose_hold` bị chặn tới khi tìm lại với đúng số khách.

## File structure

```
agent/flight_agent/core.py     Constraints(adults, children, infants), set_constraints, search_flights (Task 1);
                               passenger_type, propose_hold(passengers), list_recent_passengers, is_done (Task 2)
agent/tests/fakes.py           giá tìm chuyến và báo giá theo số khách, passengerFares (Task 1, 2)
agent/tests/test_core.py       Task 1, 2
agent/tests/test_harness.py    Task 1, 2
agent/tests/test_chat.py       Task 2
frontend/src/app/assistant/agent-api.ts       kiểu thẻ (Task 3)
frontend/src/app/assistant/assistant-chat.tsx thẻ ràng buộc và thẻ duyệt (Task 3)
agent/flight_agent/react.py    quy tắc prompt (Task 4)
agent/evals/cases.py, run.py   Case.passengers, sửa case hai-nguoi, 4 case nhóm "nhiều khách" (Task 4)
agent/tests/test_evals.py      Task 4
agent/README.md, roadmap       Task 4
```

---

### Task 1: Số khách trong ràng buộc và lúc tìm chuyến

**Files:** `agent/flight_agent/core.py`, `agent/tests/fakes.py`, `agent/tests/test_core.py`, `agent/tests/test_harness.py`

**Produces:** `Constraints.adults/children/infants`, `Constraints.seated`, `set_constraints(..., adults=1, children=0, infants=0)`, thẻ `constraints` có `adults`, `children`, `infants`; `fakes.PERCENT`, `fakes.fare_total(price, types)`.

- [ ] **Step 1: Write the failing tests**

`test_core.py`:

```python
def test_prompt_states_passenger_counts():
    text = Constraints("SGN", "DAD", "2026-10-30", adults=2, children=1, infants=1).to_prompt()
    assert "2 người lớn, 1 trẻ em, 1 em bé" in text and "tổng" in text
```

`test_harness.py`:

```python
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
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd agent && uv run pytest tests/test_core.py tests/test_harness.py -q`
Expected: FAIL (`Constraints` không có `adults`; `set_constraints` không nhận `adults`).

- [ ] **Step 3: Implement**

`Constraints`: thêm `adults: int = 1`, `children: int = 0`, `infants: int = 0` sau `max_price`; property `seated` = `adults + children`. `to_prompt` thêm `f" cho {self.adults} người lớn, {self.children} trẻ em, {self.infants} em bé"` sau ngày, và đổi chữ "giá tối đa" thành "giá tối đa (tổng cả booking)".

`set_constraints`: thêm tham số `adults: int = 1, children: int = 0, infants: int = 0`, docstring nói rõ trẻ em 2 đến dưới 12 tuổi, em bé dưới 2 tuổi tại ngày bay, và `max_price` là tổng cả booking. Kiểm trước khi đọc sân bay:

```python
try:
    counts = {"adults": int(adults), "children": int(children), "infants": int(infants)}
except (TypeError, ValueError):
    counts = None
if counts is None or counts["adults"] < 1 or min(counts.values()) < 0:
    return {"status": "invalid_param", "hint": "adults là số nguyên từ 1, children và infants từ 0."}
```

Dựng `Constraints(origin, destination, date, **limits, **counts)`; thẻ `constraints` thêm `"adults"`, `"children"`, `"infants"`.

`search_flights`: gửi `"adults": c.adults, "children": c.children, "infants": c.infants`; docstring: "cho số khách trong ràng buộc, hạng phổ thông; price là tổng cả booking".

`fakes.py`:

```python
PERCENT = {"ADULT": 100, "CHILD": 90, "INFANT": 10}     # BR-21, mặc định pricing.*_percent


def fare_total(price: int, types) -> int:
    return sum(price * PERCENT[t] // 100 for t in types)
```

`handle` cho `GET /api/flights/search`: đọc `adults`, `children`, `infants` từ params (thiếu thì 1, 0, 0), trả `self.flights` với mỗi `fareOptions[*].totalPrice = fare_total(adultPrice, types)` (bản sao, không sửa `FLIGHTS`).

- [ ] **Step 4: Run to verify they pass**

Run: `cd agent && uv run pytest -q`
Expected: tất cả PASS.

- [ ] **Step 5: Commit**

```bash
git add agent/
git commit -m "feat(agent): số người lớn, trẻ em, em bé trong ràng buộc và lúc tìm chuyến"
```

---

### Task 2: Nhiều hành khách trong đề xuất và `is_done`

**Files:** `agent/flight_agent/core.py`, `agent/tests/fakes.py`, `agent/tests/test_core.py`, `agent/tests/test_harness.py`, `agent/tests/test_chat.py`, `agent/evals/cases.py` (chỉ để `tests/test_evals.py` còn chạy), `agent/tests/test_evals.py`

**Consumes:** Task 1 `Constraints.adults/children/infants`, `fakes.fare_total`.

**Produces:** `passenger_type(dob, day)`, `propose_hold(flight_ids, fare_family_id, passengers)`, thẻ `proposal` có `passengers` (mỗi mục `lastName`, `firstName`, `gender`, `dateOfBirth`, `type`) và `fares` (`passengerFares` của báo giá); bỏ trường `passenger`.

- [ ] **Step 1: Write the failing tests**

`test_core.py`:

```python
def test_passenger_type_uses_whole_years_on_the_flight_day():
    day = Date(2026, 10, 30)
    assert passenger_type(Date(2014, 10, 30), day) == "ADULT"      # tròn 12 đúng hôm bay
    assert passenger_type(Date(2014, 10, 31), day) == "CHILD"
    assert passenger_type(Date(2024, 10, 30), day) == "CHILD"      # tròn 2 đúng hôm bay
    assert passenger_type(Date(2024, 10, 31), day) == "INFANT"
```

`test_harness.py` (sửa mọi `**PAX` thành `passengers=[PAX]`, `**p` thành `passengers=[p]`, rồi thêm):

```python
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
    assert r == {"status": "pending_approval", "total": 1_850_000 + 1_665_000 + 185_000}
    assert [p["type"] for p in h.proposal.body["passengers"]] == ["ADULT", "CHILD", "INFANT"]
    card = h.cards[-1]
    assert [(p["lastName"], p["firstName"], p["type"]) for p in card["passengers"]] == [
        ("NGUYEN", "HUNG CUONG", "ADULT"), ("NGUYEN", "BAO NGOC", "CHILD"), ("NGUYEN", "MINH KHANG", "INFANT")]
    assert [(f["type"], f["count"], f["unitPrice"]) for f in card["fares"]] == [
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
    assert h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[]))["status"] == \
        "invalid_param"


def test_recent_passengers_include_children():
    h, _ = ready(FakeBackend(past=PAST))
    names = [p["full_name"] for p in h.execute("list_recent_passengers", {})["passengers"]]
    assert "NGUYEN BE NA" in names
```

Sửa `test_recent_passengers_are_unique_adults_without_documents` thành `test_recent_passengers_are_unique_without_documents`, danh sách mong đợi có thêm `{"full_name": "NGUYEN BE NA", "gender": "MALE", "date_of_birth": "2020-05-05"}` ở vị trí thứ 3 (sau TRAN THI MAI, trước LE VAN AN).

`test_chat.py` (sửa `**PAX` thành `passengers=[PAX]`, rồi thêm):

```python
def test_read_back_with_other_passengers_hands_off():
    other = {"type": "ADULT", "lastName": "TRAN", "firstName": "THI MAI", "gender": "FEMALE",
             "dateOfBirth": "2005-01-02"}
    for patch in (lambda ps: ps + [other], lambda ps: [], lambda ps: [{**ps[0], "type": "CHILD"}]):
        fake = FakeBackend(passengers_patch=patch)
        chat, model, out = proposed(fake)
        result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
        assert [c["type"] for c in result["cards"]] == ["handoff"]


def test_read_back_in_another_order_is_done():
    fake = FakeBackend(passengers_patch=lambda ps: ps[::-1])
    chat, model, out = proposed(fake)
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    assert [c["type"] for c in result["cards"]] == ["held"]
```

`test_read_back_in_another_order_is_done` chỉ có nghĩa khi đề xuất có từ 2 khách: `proposed()` nhận thêm tham số `route` và `passengers` (mặc định `ROUTE`, `[PAX]`), test này gọi `proposed(fake, route={**ROUTE, "max_price": 4_000_000, "adults": 2}, passengers=[PAX, MAI])` với `MAI = dict(full_name="Trần Thị Mai", gender="FEMALE", date_of_birth="2005-01-02")`. Đưa `*extra` của `proposed` thành tham số từ khoá `extra=()` để có chỗ cho hai tham số mới.

`test_evals.py`: `PAX` dùng trong `call("propose_hold", ..., passengers=[PAX])`; `happy()` dùng `"passengers": [("NGUYEN", "VAN AN", "MALE", "2004-03-12")]` (đổi tên trường `Case.passenger` thành `passengers` ngay ở task này, xem Step 3).

- [ ] **Step 2: Run to verify they fail**

Run: `cd agent && uv run pytest -q`
Expected: FAIL (`passenger_type` chưa có; `propose_hold` không nhận `passengers`; `FakeBackend` không nhận `passengers_patch`).

- [ ] **Step 3: Implement**

`core.py`:

```python
def passenger_type(dob: Date, day: Date) -> str:
    """BR-10: loại khách theo tuổi tròn năm tại ngày bay, như PassengerRules.typeAt; backend vẫn kiểm lại."""
    age = day.year - dob.year - ((day.month, day.day) < (dob.month, dob.day))
    return "ADULT" if age >= 12 else "CHILD" if age >= 2 else "INFANT"
```

Kiểu tham số cho schema tool (Gemini cần object có `properties`, không nhận `dict` trần):

```python
class PassengerArg(TypedDict):
    full_name: str
    gender: str          # MALE hoặc FEMALE
    date_of_birth: str   # YYYY-MM-DD
```

`propose_hold(self, flight_ids: list[int], fare_family_id: int, passengers: list[PassengerArg])`. Docstring: mỗi hành khách một mục, đủ số người lớn, trẻ em, em bé trong ràng buộc; loại khách do code tính từ ngày sinh. Thân hàm, theo thứ tự:

1. `passengers` rỗng → `invalid_param` "Cần ít nhất một hành khách."
2. Với hành khách thứ `i` (đếm từ 1): `gender` sai, tên một chữ (dùng `known_names` rồi `split_name` như cũ), `date_of_birth` không phải `YYYY-MM-DD` hoặc sau `constraints.date` → `invalid_param` có tiền tố `f"Hành khách {i}: "`. Dựng `{"type": passenger_type(...), "lastName", "firstName", "gender", "dateOfBirth"}`.
3. Đếm theo loại; khác `(c.adults, c.children, c.infants)` → `invalid_param`: `f"Theo ngày sinh có {a} người lớn, {ch} trẻ em, {inf} em bé, khác ràng buộc. Gọi set_constraints với đúng số khách rồi search_flights lại, hoặc hỏi lại ngày sinh."` (định dạng `"{a} người lớn, {ch} trẻ em, {inf} em bé"` giống `to_prompt`).
4. Như cũ: `GET /api/me`, dựng body với `"passengers": built`, báo giá, so `max_price`.
5. Thẻ: `"passengers": built` (đã đủ 5 trường), `"fares": quote["journeys"][0]["passengerFares"]`; bỏ `"passenger"`.

`list_recent_passengers`: bỏ điều kiện `p["type"] != "ADULT"`; docstring bỏ chữ "người lớn".

`is_done`: thay `len(detail.get("passengers") or []) == 1` bằng

```python
and _people(detail.get("passengers") or []) == _people(proposal.body["passengers"])
```

với

```python
PAX_KEYS = ("type", "lastName", "firstName", "gender", "dateOfBirth")


def _people(passengers: list[dict]) -> list[tuple]:
    """Tập hành khách không theo thứ tự: backend có thể sắp lại."""
    return sorted(tuple(p.get(k) for k in PAX_KEYS) for p in passengers)
```

`fakes.py`: báo giá trả `totalAmount = fare_total(price, types)` và `journeys: [{"direction", "fareTotal", "baggageTotal": 0, "discount": 0, "passengerFares": [...]}]`, mỗi loại có mặt một mục `{"type", "count", "unitPrice": price * PERCENT[t] // 100, "total"}` theo thứ tự ADULT, CHILD, INFANT; `_price` của booking tạo ra và `_detail` cũng dùng `fare_total`. `FakeBackend(passengers_patch=None)`: `_detail` trả `passengers = passengers_patch(body["passengers"])` nếu có.

`evals/cases.py`: đổi trường `passenger: tuple | None` thành `passengers: list[tuple[str, str, str, str]] | None`, mọi case đang truyền `AN_PAX` hay tuple hành khách thì bọc trong danh sách. `evals/run.py::grade` so `[(p["lastName"], p["firstName"], p["gender"], p["dateOfBirth"]) for p in body["passengers"]]` với `case.passengers`.

- [ ] **Step 4: Run to verify they pass**

Run: `cd agent && uv run pytest -q`
Expected: tất cả PASS.

- [ ] **Step 5: Commit**

```bash
git add agent/
git commit -m "feat(agent): đề xuất giữ chỗ cho nhiều hành khách, is_done kiểm từng người"
```

---

### Task 3: Thẻ trên trang `/assistant`

**Files:** `frontend/src/app/assistant/agent-api.ts`, `frontend/src/app/assistant/assistant-chat.tsx`

**Consumes:** Task 1 thẻ `constraints` có `adults`, `children`, `infants`; Task 2 thẻ `proposal` có `passengers`, `fares`.

- [ ] **Step 1: Kiểu thẻ.** `constraints` thêm `adults: number; children: number; infants: number`. `proposal` thay `passenger` bằng `passengers: { lastName; firstName; gender: "MALE" | "FEMALE"; dateOfBirth; type: PassengerType }[]` và thêm `fares: { type: PassengerType; count: number; unitPrice: number; total: number }[]`, với `type PassengerType = "ADULT" | "CHILD" | "INFANT"`.

- [ ] **Step 2: Vẽ thẻ.**
  - Thẻ ràng buộc thêm một chip số khách, ví dụ "2 người lớn, 1 trẻ em" (bỏ loại có số 0; người lớn luôn hiện).
  - Thẻ duyệt: mục "Hành khách" là danh sách, mỗi dòng `"{họ} {tên}, {Nam|Nữ}, {Người lớn|Trẻ em|Em bé}, sinh {dd/mm/yyyy}"`. Thêm mục "Giá vé" liệt kê mỗi `fares` một dòng `"{số} {loại} × {đơn giá} = {thành tiền}"`. Tổng tiền giữ nguyên. Nhãn loại khách viết một lần trong file (`const TYPE = {ADULT: "người lớn", ...}`), dùng `vnd` sẵn có.

- [ ] **Step 3: Verify**

Run: `cd frontend && npm run lint && npx tsc --noEmit`
Expected: không lỗi.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/assistant/
git commit -m "feat(assistant): thẻ hiện số khách, từng hành khách và giá từng loại khách"
```

---

### Task 4: Prompt, case eval và chạy lại eval

**Files:** `agent/flight_agent/react.py`, `agent/evals/cases.py`, `agent/tests/test_evals.py`, `agent/README.md`, `docs/superpowers/plans/2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md`

**Consumes:** Task 1, 2 (tham số `set_constraints`, `propose_hold`, `Case.passengers`).

- [ ] **Step 1: Write the failing test** (`test_evals.py`)

```python
def test_cases_cover_many_passengers():
    group = [c for c in CASES if c.group == "nhiều khách"]
    assert len(group) == 4 and all(c.constraints for c in group)
    assert next(c for c in CASES if c.name == "hai-nguoi").constraints["adults"] == 2
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd agent && uv run pytest tests/test_evals.py -q`
Expected: FAIL (chưa có nhóm "nhiều khách").

- [ ] **Step 3: Prompt.** Trong `SYSTEM_PROMPT`:
  - Câu phạm vi đổi thành: chỉ đặt vé một chiều, hạng phổ thông; khứ hồi thì nói chưa hỗ trợ và mời khách đặt trên /flights.
  - Thêm: khách đi nhiều người thì truyền `adults`, `children` (2 đến dưới 12 tuổi), `infants` (dưới 2 tuổi) vào `set_constraints`; `max_price` là tổng cả booking, khách nói giá mỗi vé thì nhân với số người lớn cộng trẻ em.
  - Câu hành khách đổi thành: cần họ tên đầy đủ, giới tính và ngày sinh của từng hành khách; `propose_hold` nhận danh sách `passengers`, đủ người; loại khách do hệ thống tính từ ngày sinh.

- [ ] **Step 4: Case.** Sửa `hai-nguoi`: `end="reply"`, `constraints=R(adults=2)`, `reply_has=["sinh"]` (phải hỏi thông tin hai người). Thêm nhóm "nhiều khách" (`MAI = "Trần Thị Mai, nữ, sinh ngày 2/1/2005"`, `MAI_PAX = ("TRAN", "THI MAI", "FEMALE", "2005-01-02")`):

| Tên                      | Lượt của khách                                                                                                                                                                | Kết quả mong đợi                                                                                                       |
| ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `hai-nguoi-du-thong-tin` | `f"Đặt 2 vé SGN đi DAD ngày 30/10, bay trước 12h, cho {AN} và {MAI}"`                                                                                                         | `proposal`, `R(depart_before="12:00", adults=2)`, `[122]`, `[AN_PAX, MAI_PAX]`                                         |
| `gia-moi-ve`             | `f"2 vé SGN đi DAD ngày 30/10, trước 12h, mỗi vé không quá 2 triệu, cho {AN} và {MAI}"`                                                                                       | `proposal`, `R(depart_before="12:00", max_price=4_000_000, adults=2)`, `[122]`, `[AN_PAX, MAI_PAX]`                    |
| `ca-nha`                 | `"Đặt vé SGN đi DAD ngày 30/10 cho cả nhà: mình là Nguyễn Văn An, nam, sinh 12/03/1990; bé Nguyễn Bảo Ngọc, nữ, sinh 01/06/2018; bé Nguyễn Minh Khang, nam, sinh 10/02/2026"` | `proposal`, `R(children=1, infants=1)`, `[610]`, ba hành khách theo đúng thứ tự khách kể                               |
| `sai-loai-khach`         | `"Đặt 2 vé người lớn SGN đi DAD ngày 30/10 cho Nguyễn Văn An, nam, sinh 12/03/1990 và Nguyễn Bảo Ngọc, nữ, sinh 01/06/2018"`                                                  | `proposal`, `R(children=1)`, `[610]`, hai hành khách (bé Ngọc là trẻ em: model phải sửa ràng buộc sau `invalid_param`) |

Hành khách của `ca-nha` và `sai-loai-khach`: `("NGUYEN", "VAN AN", "MALE", "1990-03-12")`, `("NGUYEN", "BAO NGOC", "FEMALE", "2018-06-01")`, `("NGUYEN", "MINH KHANG", "MALE", "2026-02-10")`.

- [ ] **Step 5: Run the whole suite**

Run: `cd agent && uv run pytest -q`
Expected: tất cả PASS.

- [ ] **Step 6: Chạy eval với model thật**

Run: `cd agent && uv run python -m evals.run > <workspace>/eval.txt`
Expected: 4 case mới đạt; tỉ lệ lượt đạt của các case cũ không thấp hơn mốc mới nhất ở §3. Case cũ tụt thì sửa prompt rồi chạy lại case đó (`--case`). Free tier 500 request/ngày: hết quota giữa chừng thì ghi lại, chạy phần còn lại sau khi quota reset.

- [ ] **Step 7: Tài liệu.** README: đoạn phạm vi nói trợ lý đặt được nhiều hành khách (loại khách tính từ ngày sinh, `is_done` kiểm từng người), `list_recent_passengers` trả cả trẻ em. Lộ trình: dòng Bước 5 gắn link plan này và ghi "5a xong" (chưa tick cả Bước 5), thêm dòng §3.

- [ ] **Step 8: Commit**

```bash
git add agent/ docs/superpowers/plans/
git commit -m "feat(agent): prompt và case eval nhiều hành khách"
```
