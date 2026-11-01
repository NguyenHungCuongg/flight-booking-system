# Trợ lý Bước 4: Tra cứu booking (chỉ đọc) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khách hỏi "booking K7M2QX hết hạn giữ chỗ lúc mấy giờ", "vé này hoàn được bao nhiêu" thì agent trả lời đúng giờ và số tiền lấy từ API, rồi dẫn khách sang C-09/C-10/C-11 để tự thao tác.

**Architecture:** Thêm ba tool chỉ đọc vào `Harness` (`agent/flight_agent/core.py`): `list_my_bookings`, `get_booking(code)`, `refund_quote(code, direction)`. Mỗi tool kiểm định dạng tham số rồi gọi đúng một `GET` của backend, trả bản rút gọn trong đó giờ và tiền đã định dạng sẵn để model chép lại (grounding, Buổi 6 Phần 05). Không thêm thẻ, không đổi `Constraints`, `Proposal` hay `is_done`.

**Tech Stack:** Python, LangChain, pytest với `httpx.MockTransport` (agent).

**Spec:** [docs/superpowers/specs/2026-10-26-tro-ly-dat-ve-agent-design.md](../specs/2026-10-26-tro-ly-dat-ve-agent-design.md) · Lộ trình: [Bước 4](2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md#bước-4--tra-cứu-booking-chỉ-đọc)

## Global Constraints

- Làm trên nhánh `feature/flight-agent`. Không sửa `backend/`, không sửa `frontend/` (trace hiện tên tool thô).
- Ba tool chỉ gọi `GET`, mỗi lần gọi tool đúng một request. `check_permission` không đòi ràng buộc cho chúng (chỉ `search_flights`, `propose_hold` cần).
- Mã booking (BR-46): bỏ khoảng trắng, in hoa, phải khớp `[A-HJ-NP-Z2-9]{6}` (bảng chữ `BookingCodes.ALPHABET`) trước khi ghép vào đường dẫn. `direction` in hoa, chỉ `OUTBOUND` hoặc `RETURN`.
- Booking của người khác: backend trả 404 `RESOURCE_NOT_FOUND` (BR-104), đi theo `backend_error` chung; prompt bảo model nói không tìm thấy.
- Tiền trả cho model là chuỗi `"1.550.000 đ"` (dùng `_vnd`), giờ là `"HH:MM ngày DD/MM/YYYY"` cắt từ chuỗi ISO có offset của API (API đã trả giờ Việt Nam cho `holdExpiresAt`, giờ sân bay cho chặng bay), không đổi múi giờ.
- Không trả số giấy tờ, liên hệ, thanh toán. Hành khách chỉ có họ tên.
- Ngoài phạm vi: huỷ, hoàn, đổi do agent thực hiện; tool `reschedule-options` (lộ trình ghi "nếu cần": C-11 tự tìm chuyến mới, agent chỉ dẫn link).

## Review Focus

1. **Mã booking viết thường hoặc có khoảng trắng** (`" k7m2qx "`): chuẩn hoá rồi gọi đúng `/api/bookings/K7M2QX`.
2. **Mã chứa ký tự lạ hoặc `../`**: bị chặn bằng `invalid_param`, không request nào tới backend.
3. **Booking đã xuất vé không có `holdExpiresAt` có nghĩa**: chỉ trả `hold_expires_at` khi trạng thái `PENDING_PAYMENT`, để model không đọc nhầm hạn giữ chỗ đã qua.
4. **Hoàn không được** (409 `DEADLINE_PASSED`, `FARE_RULE_NOT_ALLOWED`, `INVALID_STATE`): `backend_error` với `detail` tiếng Việt của backend, không dừng lượt.
5. **Model trả lời mà không gọi tool** (đoán giờ hay tiền): case eval chấm thêm tool phải có trong trace.

## File structure

```
agent/flight_agent/core.py     MY_BOOKINGS, STATES, _when, _booking_code, 3 tool mới, Harness.tools (Task 1)
agent/tests/fakes.py           journey(), booking(), FakeBackend(bookings=..., refunds=...) (Task 1)
agent/tests/test_harness.py    Task 1
agent/flight_agent/react.py    quy tắc prompt tra cứu (Task 2)
agent/evals/cases.py           field Case.calls, 4 case nhóm "tra cứu" (Task 2)
agent/evals/run.py             chấm Case.calls (Task 2)
agent/tests/test_evals.py      Task 2
agent/README.md                đoạn mô tả tool tra cứu (Task 2)
docs/.../roadmap-mo-rong.md    tick Bước 4, số liệu eval §3 (Task 2)
```

---

### Task 1: Ba tool tra cứu trong Harness

**Files:**

- Modify: `agent/flight_agent/core.py`
- Modify: `agent/tests/fakes.py`
- Test: `agent/tests/test_harness.py`

**Interfaces:**

- Consumes: `Harness.execute`, `Harness.backend_error`, `_vnd`, `FakeBackend`, `passenger()` (có sẵn).
- Produces:
  - `MY_BOOKINGS = 10`; `STATES: dict[str, str]` = `{"PENDING_PAYMENT": "chờ thanh toán", "ISSUED": "đã xuất vé", "EXPIRED": "hết hạn giữ chỗ", "CANCELLED": "đã huỷ", "REFUNDED": "đã hoàn"}`.
  - `_when(iso: str) -> str`: `"2026-10-30T08:10:00+07:00"` → `"08:10 ngày 30/10/2026"`.
  - `Harness.list_my_bookings() -> dict`: `GET /api/bookings?size=10` → `{"status": "ok", "bookings": [{"code", "state", "total", "routes": ["SGN → DAD 08:10 ngày 30/10/2026"], "hold_expires_at"?}]}`.
  - `Harness.get_booking(code: str) -> dict`: `GET /api/bookings/{code}` → `{"status": "ok", "booking": {"code", "state", "total", "hold_expires_at"?, "page": "/bookings/<code>", "passengers": ["NGUYEN HUNG CUONG"], "journeys": [{"direction", "state", "fare", "refundable", "refund_fee", "changeable", "change_fee", "flights": ["VN122 SGN 08:10 ngày 30/10/2026 → DAD 09:30 ngày 30/10/2026"], "refund_page": "/bookings/<code>/refund/outbound", "reschedule_page": "/bookings/<code>/reschedule/outbound"}]}}`.
  - `Harness.refund_quote(code: str, direction: str) -> dict`: `GET /api/bookings/{code}/journeys/{DIRECTION}/refund-quote` → `{"status": "ok", "journey_value", "fee", "refund_amount", "page": "/bookings/<code>/refund/<direction thường>"}`.
  - `hold_expires_at` chỉ có khi trạng thái là `PENDING_PAYMENT`. Mọi trường tiền là chuỗi `_vnd(n) + " đ"`.
  - Fakes: `journey(direction="OUTBOUND", flight_id=122, refundable=True, refund_fee=300_000, changeable=True, change_fee=300_000)` dạng `JourneyView` một chặng lấy từ `FLIGHTS`; `booking(code, status="ISSUED", hold_expires=EXPIRES, total=1_850_000, journeys=None, passengers=None)` dạng `BookingDetail`; `FakeBackend(bookings=(), refunds=None)`: `bookings` là các `BookingDetail` mới nhất trước, `GET /api/bookings` trả tóm tắt của chúng (cùng mã của `past`), chi tiết trả nguyên; `refunds` là `{(code, direction): RefundQuote dict | (status, code)}`.

- [ ] **Step 1: Write the failing tests** (cuối `agent/tests/test_harness.py`)

```python
HELD = booking("K7M2QX", "PENDING_PAYMENT", hold_expires="2026-10-26T15:40:00+07:00")
ISSUED = booking("ABC234", journeys=[journey(), journey("RETURN", 610, refundable=False)])
LOOKUP = dict(bookings=[HELD, ISSUED], refunds={
    ("ABC234", "OUTBOUND"): {"journeyValue": 1_850_000, "fee": 300_000, "refundAmount": 1_550_000},
    ("ABC234", "RETURN"): (409, "FARE_RULE_NOT_ALLOWED")})

def test_lookup_tools_need_no_constraints_and_only_get(): ...
    # list_my_bookings, get_booking("K7M2QX"), refund_quote("ABC234", "OUTBOUND") đều "ok" khi chưa set_constraints;
    # đúng 3 request, đều GET; request đầu có size=10
def test_my_bookings_show_state_total_route_and_hold_only_when_pending(): ...
    # bookings[0] == {"code": "K7M2QX", "state": "chờ thanh toán", "total": "1.850.000 đ",
    #   "routes": ["SGN → DAD 08:10 ngày 30/10/2026"], "hold_expires_at": "15:40 ngày 26/10/2026"}
    # bookings[1] không có khoá "hold_expires_at", state "đã xuất vé"
def test_get_booking_normalises_the_code_and_summarises(): ...
    # get_booking(" k7m2qx ") gọi /api/bookings/K7M2QX; booking["hold_expires_at"] == "15:40 ngày 26/10/2026",
    # page "/bookings/K7M2QX", passengers ["NGUYEN HUNG CUONG"];
    # journeys[0] == {"direction": "OUTBOUND", "state": "ACTIVE", "fare": "Phổ thông", "refundable": True,
    #   "refund_fee": "300.000 đ", "changeable": True, "change_fee": "300.000 đ",
    #   "flights": ["VN122 SGN 08:10 ngày 30/10/2026 → DAD 09:30 ngày 30/10/2026"],
    #   "refund_page": "/bookings/K7M2QX/refund/outbound", "reschedule_page": "/bookings/K7M2QX/reschedule/outbound"}
    # không chuỗi nào trong json.dumps(kết quả) chứa "B1234567" (số hộ chiếu của passenger())
def test_bad_booking_code_or_direction_never_reaches_the_backend(): ...
    # get_booking("../me"), get_booking("K7M2Q0"), refund_quote("ABC234", "BOTH") → "invalid_param", fake.requests == []
def test_someone_elses_booking_is_not_found_for_the_model(): ...
    # get_booking("ZZZ999") → status "backend_error", code "NOT_FOUND"; stop_reason None
def test_refund_quote_formats_money_and_links_c10(): ...
    # == {"status": "ok", "journey_value": "1.850.000 đ", "fee": "300.000 đ", "refund_amount": "1.550.000 đ",
    #     "page": "/bookings/ABC234/refund/outbound"}; refund_quote("abc234", "return") → backend_error
    #     code "FARE_RULE_NOT_ALLOWED", stop_reason None
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd agent && uv run pytest tests/test_harness.py -q`
Expected: FAIL (ImportError `booking`/`journey` từ `tests.fakes`).

- [ ] **Step 3: Thêm `journey`, `booking`, `bookings=`, `refunds=` vào `agent/tests/fakes.py`**

Tóm tắt trong `GET /api/bookings` theo `BookingSummary`: `code`, `status`, `totalAmount`, `holdExpiresAt`, `routes` (`direction`, `departureAirport`, `arrivalAirport`, `departureTime` của chặng đầu mỗi chiều). Route refund-quote khớp `/api/bookings/{code}/journeys/{direction}/refund-quote`; không có trong `refunds` thì 404.

- [ ] **Step 4: Run to verify the new tests fail on the missing tools**

Run: `cd agent && uv run pytest tests/test_harness.py -q`
Expected: các test mới FAIL ("Không có tool ..." / status `denied`), test cũ PASS.

- [ ] **Step 5: Implement the three tools in `agent/flight_agent/core.py`** và thêm vào `Harness.tools`. Docstring của mỗi tool là mô tả cho model, tiếng Việt, nói rõ: dùng khi nào, tiền và giờ đọc nguyên văn, `refund_quote` là số tiền hoàn thật (đã trừ phí, xét hạn), không tự tính từ `refund_fee`.

- [ ] **Step 6: Run the whole suite**

Run: `cd agent && uv run pytest -q`
Expected: tất cả PASS.

- [ ] **Step 7: Commit**

```bash
git add agent/flight_agent/core.py agent/tests/fakes.py agent/tests/test_harness.py
git commit -m "feat(agent): tool tra cứu booking và số tiền hoàn, chỉ đọc"
```

---

### Task 2: Prompt, case eval và chạy lại eval

**Files:**

- Modify: `agent/flight_agent/react.py` (SYSTEM_PROMPT)
- Modify: `agent/evals/cases.py`, `agent/evals/run.py`
- Test: `agent/tests/test_evals.py`
- Modify: `agent/README.md`, `docs/superpowers/plans/2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md`

**Interfaces:**

- Consumes: ba tool của Task 1, `booking()`/`journey()`/`FakeBackend(bookings=, refunds=)`.
- Produces: `Case.calls: list[str]` (mặc định rỗng): tên tool phải có trong `harness.trace` của lượt cuối; `grade` thêm lỗi `"calls: thiếu <tool>"`.

- [ ] **Step 1: Write the failing test** (`agent/tests/test_evals.py`)

```python
def test_required_tool_must_be_called():
    assert run_case(happy(calls=["search_flights"]), model())["passed"]
    r = run_case(happy(calls=["get_booking"]), model())
    assert not r["passed"] and "calls: thiếu get_booking" in r["failures"]
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd agent && uv run pytest tests/test_evals.py -q`
Expected: FAIL (`Case` không có tham số `calls`).

- [ ] **Step 3: Thêm `Case.calls` và chấm nó trong `evals/run.py::grade`**

- [ ] **Step 4: Run to verify it passes**

Run: `cd agent && uv run pytest -q`
Expected: tất cả PASS.

- [ ] **Step 5: Thêm quy tắc prompt** vào `SYSTEM_PROMPT`, một gạch đầu dòng: khách hỏi về booking đã đặt thì gọi `get_booking` nếu có mã, `list_my_bookings` nếu không; hỏi tiền hoàn thì gọi `refund_quote`, không tự tính; giờ và tiền chép đúng từ kết quả tool; `backend_error` không tìm thấy thì nói không tìm thấy booking đó trong tài khoản và nhờ kiểm tra lại mã; trợ lý không huỷ, hoàn hay đổi vé, mời khách làm trên trang ở trường `page`/`refund_page`/`reschedule_page`.

- [ ] **Step 6: Thêm 4 case nhóm "tra cứu"** vào `CASES`, backend `LOOKUP` như Task 1 (dựng trong `cases.py` bằng `booking`, `journey`):

| Tên                  | Lượt                                                   | end     | calls                  | reply_has            |
| -------------------- | ------------------------------------------------------ | ------- | ---------------------- | -------------------- |
| `tra-han-giu-cho`    | "Booking K7M2QX của mình hết hạn giữ chỗ lúc mấy giờ?" | `reply` | `["get_booking"]`      | `["15:40"]`          |
| `tra-tien-hoan`      | "Vé ABC234 chiều đi hoàn được bao nhiêu tiền?"         | `reply` | `["refund_quote"]`     | `["1.550.000"]`      |
| `tra-khong-co-ma`    | "Mình có booking nào đang chờ thanh toán không?"       | `reply` | `["list_my_bookings"]` | `["K7M2QX"]`         |
| `tra-cua-nguoi-khac` | "Kiểm tra giúp mình booking ZZZ999"                    | `reply` | `["get_booking"]`      | `["không tìm thấy"]` |

- [ ] **Step 7: Run the whole suite**

Run: `cd agent && uv run pytest -q`
Expected: tất cả PASS (test eval với model giả không đụng model thật).

- [ ] **Step 8: Chạy eval với model thật**

Run: `cd agent && uv run python -m evals.run > <workspace>/eval.txt`
Expected: 4 case mới đạt; tỉ lệ lượt đạt của 20 case cũ không thấp hơn 95% (mốc §3). Case cũ tụt thì sửa prompt rồi chạy lại case đó (`--case`).

- [ ] **Step 9: Cập nhật tài liệu**: README một đoạn về ba tool tra cứu; lộ trình tick Bước 4, link plan này, thêm dòng §3 với số liệu Step 8.

- [ ] **Step 10: Commit**

```bash
git add agent/ docs/superpowers/plans/
git commit -m "feat(agent): prompt và case eval tra cứu booking"
```
