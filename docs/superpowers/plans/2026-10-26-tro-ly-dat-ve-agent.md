# Trợ lý đặt vé (agent) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khách đã đăng nhập gõ yêu cầu bằng lời trên `/assistant`. Agent tìm chuyến thật, hỏi thông tin hành khách và đưa ra thẻ duyệt. Khách bấm duyệt thì code giữ chỗ và đọc lại booking, rồi chuyển khách sang C-09 để thanh toán.

**Architecture:** Service Python (FastAPI) mới trong `agent/`, copy từ repo mini agent. Harness kiểm mọi lời gọi tool, model chỉ có ba tool và không có tool nào ghi booking. Frontend chuyển `/agent/*` tới service bằng rewrite. Service gọi Spring `/api/*` bằng cookie phiên của khách và chuyển tiếp nguyên header `X-XSRF-TOKEN`. Backend Java không đổi.

**Tech Stack:** Python ≥ 3.12, uv, LangChain 1.4 (`create_agent` + middleware), FastAPI, uvicorn, httpx, pytest. Next.js 16 (frontend có sẵn).

**Spec:** [docs/superpowers/specs/2026-10-26-tro-ly-dat-ve-agent-design.md](../specs/2026-10-26-tro-ly-dat-ve-agent-design.md)

## Global Constraints

- Làm trên nhánh `feature/flight-agent`. Không sửa `backend/`, roadmap, PRD, TDD, APP_FLOW. Không thêm vào CI hay `docker-compose.yml`.
- Code nằm trong `agent/`, package `flight_agent`. Nguồn copy: `F:\UIT\Kì 7\Agentic\BTVN\demo-flight-agent` (repo mini agent).
- Comment, prompt, chữ hiển thị và `hint` trả cho model đều viết tiếng Việt.
- Chỉ một chiều, đúng một người lớn: `adults=1`, `cabin=ECONOMY`, `type=ADULT`, `direction=OUTBOUND`, `baggage=[]`, không có `voucherCode`.
- `BUDGET = 10` lần gọi model mỗi lượt chat. Phát hiện lặp: cùng (tool, args) 3 lần (`LOOP_REPEAT = 3`) trong 6 lời gọi gần nhất (`LOOP_WINDOW = 6`).
- Tiền là `int` VND. Ngày là `YYYY-MM-DD`. Giờ so sánh dạng chuỗi `HH:MM` có số 0 ở đầu.
- "Hôm nay" là ngày theo giờ Việt Nam (UTC+7, không có giờ mùa hè), không dùng giờ của máy chủ.
- Service không bao giờ đọc cookie `XSRF-TOKEN` để đặt header `X-XSRF-TOKEN`. Header này chỉ được chuyển tiếp nguyên từ request đến.
- Model không có tool nào gọi `POST /api/bookings`. Chỉ endpoint duyệt mới gọi, và gửi đúng body đã lưu trong đề xuất.
- Cổng 8000. `AGENT_URL` mặc định `http://localhost:8000`, `BACKEND_URL` mặc định `http://localhost:8080`.
- Frontend: theo `DESIGN.md` (token màu theo vai trò, `card`/`card-hi`, `pillPrimary`/`pillLine`), line-height viết bằng px, không gọi `new Date()`/`Date.now()` lúc render.

## Review Focus

1. **Bấm "Giữ chỗ" trên thẻ cũ** sau khi đã có đề xuất mới hoặc ràng buộc vừa đổi: phải báo "Thẻ này đã hết hiệu lực", không được giữ chỗ theo đề xuất nào cả. Test ở Task 3 (`set_constraints` xoá đề xuất) và Task 4 (`test_approving_replaced_proposal_is_refused`).
2. **Bấm đúp "Giữ chỗ"**: hai request duyệt chạy song song chỉ tạo đúng một booking. Test ở Task 4 (`test_concurrent_approvals_create_one_booking`).
3. **Ràng buộc viết lỏng** như `"9:00"`, `"2.000.000"`, `"2 triệu"`: hoặc được chuẩn hoá thành `"09:00"`/`2000000`, hoặc trả `invalid_param` kèm gợi ý, không crash. Một giờ không có số 0 ở đầu mà lọt qua sẽ làm so sánh chuỗi sai. Test ở Task 1.
4. **"Thứ 6 này" lúc gần nửa đêm**: 18:30 UTC ngày 26/10 đã là ngày 27/10 ở Việt Nam. Test ở Task 5 (`test_vn_today_uses_utc_plus_7`).
5. **Service agent khởi động lại** trong khi trang vẫn giữ `chatId` cũ: gửi tin nhắn nhận 404. Frontend phải tự tạo chat mới và gửi lại một lần, không kẹt lỗi. Phía service đã có test 404 (`test_another_session_cannot_use_the_chat`, Task 5). Phía trang thì kiểm bằng tay ở Task 7 Step 5.

---

## File structure

```
agent/
  pyproject.toml, uv.lock     copy rồi sửa (Task 1)
  .gitignore, .env.example    copy rồi sửa (Task 1)
  README.md                   Task 5
  flight_agent/
    __init__.py
    model.py                  copy nguyên (Task 1)
    core.py                   Constraints, normalise, split_name (Task 1); Harness, Proposal, StopRun (Task 3)
    backend.py                Backend, BackendError: gọi Spring (Task 2)
    react.py                  system_prompt, run_turn (Task 4)
    chat.py                   Chat, Chats, turn, approve, INFRA_ERRORS (Task 4)
    app.py                    create_app, vn_today (Task 5)
  tests/
    fakes.py                  ScriptedModel, scripted, call, FakeBackend, itinerary (Task 3)
    test_core.py              Task 1
    test_backend.py           Task 2
    test_harness.py           Task 3
    test_chat.py              Task 4
    test_app.py               Task 5
frontend/
  next.config.ts              thêm rewrite /agent (Task 6)
  src/proxy.ts                thêm /assistant vào matcher (Task 6)
  src/lib/api.ts              export csrfToken (Task 6)
  src/components/site-header.tsx   thêm link "Trợ lý đặt vé" (Task 6)
  e2e/smoke.spec.ts           thêm /assistant vào PROTECTED (Task 6)
  src/app/assistant/page.tsx, assistant-chat.tsx, agent-api.ts   (Task 6)
CLAUDE.md                     một dòng về agent/ (Task 5)
```

Mọi lệnh Python chạy trong `agent/`: `cd agent && uv run pytest …`.

---

### Task 1: Khung `agent/`, ràng buộc và chuẩn hoá

**Files:**

- Create: `agent/pyproject.toml`, `agent/uv.lock`, `agent/.gitignore`, `agent/.env.example`, `agent/flight_agent/__init__.py`, `agent/flight_agent/model.py`, `agent/flight_agent/core.py`
- Test: `agent/tests/test_core.py`

**Interfaces:**

- Produces:
  - `Constraints(origin: str, destination: str, date: str, depart_before: str | None = None, max_price: int | None = None)`, là `@dataclass(frozen=True)`.
  - `Constraints.broken(origin: str, destination: str, departure: str, price: int) -> list[str]`: trả về tên các ràng buộc bị vi phạm, theo thứ tự `origin`, `destination`, `date`, `depart_before`, `max_price`. Tham số `departure` là chuỗi ISO có offset theo giờ địa phương, ví dụ `"2026-10-30T08:10:00+07:00"`: `[:10]` là ngày, `[11:16]` là giờ. Giá trị `None` nghĩa là không giới hạn.
  - `Constraints.is_ok(...) -> bool`, nhận cùng tham số với `broken` và trả `not broken(...)`.
  - `Constraints.to_prompt() -> str`.
  - `normalise(key: str, value) -> tuple[value | None, hint | None]` với `key` là `depart_before` hoặc `max_price`.
  - `split_name(full_name: str) -> tuple[str, str] | None`.

- [ ] **Step 1: Copy repo cũ và dọn**

```bash
SRC="/f/UIT/Kì 7/Agentic/BTVN/demo-flight-agent"
mkdir -p agent/flight_agent agent/tests
cp "$SRC/pyproject.toml" "$SRC/uv.lock" "$SRC/.gitignore" "$SRC/.env.example" agent/
cp "$SRC/flight_agent/__init__.py" "$SRC/flight_agent/model.py" agent/flight_agent/
```

Trong `agent/pyproject.toml`: đổi `name` thành `"skyline-agent"`, giữ `requires-python` và `[tool.pytest.ini_options]`. Trong `agent/.gitignore`: chỉ giữ các mục Secrets, Python, Tooling. Trong `agent/.env.example`: giữ ba biến `OPENAI_*`, thêm `BACKEND_URL=http://localhost:8080` kèm comment tiếng Việt.

- [ ] **Step 2: Thêm dependency**

Run: `cd agent && uv add fastapi uvicorn httpx && uv remove --dev httpx`
Expected: `uv.lock` được cập nhật; `httpx` nằm trong `dependencies`, không còn trong nhóm `dev`.

- [ ] **Step 3: Viết test hỏng**

```python
# agent/tests/test_core.py
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
```

- [ ] **Step 4: Chạy để thấy test hỏng**

Run: `cd agent && uv run pytest tests/test_core.py -v`
Expected: FAIL với `ImportError` (chưa có `core.py`).

- [ ] **Step 5: Viết `agent/flight_agent/core.py`**

- `to_prompt()` sinh câu tiếng Việt, ví dụ `"Ràng buộc hiện tại: SGN → DAD ngày 2026-10-30, cất cánh trước 12:00, giá tối đa 2.000.000 đ."`. Giá trị `None` viết là "không giới hạn". Số tiền dùng dấu chấm ngăn hàng nghìn: `f"{n:,}".replace(",", ".")`.
- `normalise` copy nguyên `_normalise` từ `memory.py` của repo cũ, kể cả quy tắc từ chối phần thập phân. Chỉ dịch `hint` sang tiếng Việt và thay `KEYS` bằng `("depart_before", "max_price")`.
- `split_name` đi theo đúng các bước sau (BR-12):

```python
def split_name(full_name: str) -> tuple[str, str] | None:
    text = full_name.replace("đ", "d").replace("Đ", "D")
    text = "".join(c for c in unicodedata.normalize("NFD", text) if unicodedata.category(c) != "Mn")
    words = re.sub(r"[^A-Z ]", "", text.upper()).split()
    return (words[0], " ".join(words[1:])) if len(words) >= 2 else None
```

- [ ] **Step 6: Chạy test để thấy pass**

Run: `cd agent && uv run pytest tests/test_core.py -v`
Expected: 7 passed.

- [ ] **Step 7: Commit**

```bash
git add agent
git commit -m "feat(agent): khung agent/, ràng buộc và chuẩn hoá tên"
```

---

### Task 2: Client gọi backend

**Files:**

- Create: `agent/flight_agent/backend.py`
- Test: `agent/tests/test_backend.py`

**Interfaces:**

- Produces:
  - `BackendError(Exception)` có các thuộc tính `status: int`, `code: str`, `detail: str`, `errors: list[dict]`. Mỗi phần tử của `errors` có dạng `{"field", "message"}`, giống `ProblemDetail` của Spring.
  - `Backend(base_url: str, cookie: str, csrf: str | None, transport: httpx.BaseTransport | None = None)`.
  - `Backend.get(path: str, params: dict | None = None) -> dict | list`.
  - `Backend.post(path: str, body: dict) -> dict`.
  - `path` bắt đầu bằng `/api/...`.

- [ ] **Step 1: Viết test hỏng**

```python
# agent/tests/test_backend.py
import httpx
import pytest

from flight_agent.backend import Backend, BackendError

seen = []


def echo(request):
    seen.append(request)
    if request.url.path == "/api/fail":
        return httpx.Response(409, json={"code": "SEATS_UNAVAILABLE", "detail": "Hết ghế",
                                         "errors": [{"field": "x", "message": "y"}]})
    return httpx.Response(200, json={"ok": True})


def backend(csrf):
    seen.clear()
    return Backend("http://be", "JSESSIONID=s1; XSRF-TOKEN=from-cookie", csrf, httpx.MockTransport(echo))


def test_cookie_is_forwarded_verbatim():
    backend(None).get("/api/me")
    assert seen[0].headers["cookie"] == "JSESSIONID=s1; XSRF-TOKEN=from-cookie"


def test_post_forwards_the_incoming_csrf_header():
    backend("from-header").post("/api/bookings/quote", {})
    assert seen[0].headers["x-xsrf-token"] == "from-header"


def test_csrf_is_never_taken_from_the_cookie():
    backend(None).post("/api/bookings/quote", {})
    assert "x-xsrf-token" not in seen[0].headers


def test_problem_detail_becomes_backend_error():
    with pytest.raises(BackendError) as e:
        backend("t").post("/api/fail", {})
    assert (e.value.status, e.value.code, e.value.detail) == (409, "SEATS_UNAVAILABLE", "Hết ghế")
    assert e.value.errors == [{"field": "x", "message": "y"}]


def test_unreachable_backend_is_a_network_error():
    def down(request):
        raise httpx.ConnectError("refused")
    with pytest.raises(BackendError) as e:
        Backend("http://be", "", None, httpx.MockTransport(down)).get("/api/me")
    assert (e.value.status, e.value.code) == (0, "NETWORK")
```

- [ ] **Step 2: Chạy để thấy test hỏng**

Run: `cd agent && uv run pytest tests/test_backend.py -v`
Expected: FAIL với `ImportError`.

- [ ] **Step 3: Viết `backend.py`**

Dùng `httpx.Client(base_url=..., transport=..., timeout=30)`. Header `Cookie` gửi nguyên ở mọi request. Header `X-XSRF-TOKEN` chỉ thêm ở `post`, và chỉ khi `csrf` khác `None`. Gặp response không phải 2xx thì đọc JSON của `ProblemDetail`; thiếu trường nào thì dùng mặc định: `code="INTERNAL_ERROR"`, `detail="Đã có lỗi xảy ra."`, `errors=[]`. Gặp `httpx.TransportError` thì ném `BackendError(0, "NETWORK", "Không kết nối được backend.", [])`.

- [ ] **Step 4: Chạy test để thấy pass**

Run: `cd agent && uv run pytest tests/test_backend.py -v`
Expected: 5 passed.

- [ ] **Step 5: Commit**

```bash
git add agent/flight_agent/backend.py agent/tests/test_backend.py
git commit -m "feat(agent): client gọi backend, chuyển tiếp cookie và X-XSRF-TOKEN"
```

---

### Task 3: Harness và ba tool

**Files:**

- Modify: `agent/flight_agent/core.py` (thêm vào sau phần của Task 1)
- Create: `agent/tests/fakes.py`
- Test: `agent/tests/test_harness.py`

**Interfaces:**

- Consumes: `Constraints`, `normalise`, `split_name` (Task 1); `Backend`, `BackendError` (Task 2).
- Produces (trong `core.py`):
  - `BUDGET = 10`, `LOOP_WINDOW = 6`, `LOOP_REPEAT = 3`; `class StopRun(Exception)`.
  - `@dataclass Proposal(id: str, body: dict, total: int, card: dict)`. `body` là `BookingRequest` dạng JSON. `total` là `totalAmount` của báo giá.
  - `@dataclass Harness` với các trường sau:
    - Giữ qua các lượt: `backend: Backend | None = None`, `today: str = ""`, `constraints: Constraints | None = None`, `found: list[dict]` (hành trình rút gọn của lần tìm gần nhất), `proposal: Proposal | None = None`, `airports: dict[str, dict] | None = None` (cache của `/api/airports` theo `code`).
    - Đặt lại mỗi lượt: `trace: list[dict]`, `cards: list[dict]`, `stop_reason: str | None`, `model_calls: int`, `recent: deque`.
  - Các method:
    - `begin_turn(backend: Backend, today: str) -> None`: đặt `backend` và `today`, rồi xoá mọi trạng thái của lượt.
    - `tools -> list` (property): `[self.set_constraints, self.search_flights, self.propose_hold]`.
    - `set_constraints(origin: str, destination: str, date: str, depart_before: str | None = None, max_price: int | None = None) -> dict`.
    - `search_flights() -> dict`.
    - `propose_hold(flight_ids: list[int], fare_family_id: int, full_name: str, gender: str, date_of_birth: str) -> dict`.
    - `stop(reason: str) -> None`, `before_model() -> None`, `check_permission(tool: str, args: dict) -> str | None`, `is_looping(tool: str, args: dict) -> bool`, `execute(tool: str, args: dict) -> dict`.
    - `is_done(detail: dict, proposal: Proposal) -> bool`, `handoff() -> dict`.
  - Thẻ trong `cards` (khoá camelCase, frontend ở Task 6 đọc đúng các tên này):
    - `{"type": "constraints", "origin", "destination", "date", "departBefore", "maxPrice"}`
    - `{"type": "proposal", "proposalId", "segments": [{"flightNumber", "from", "to", "depart", "arrive"}], "fare", "passenger": {"lastName", "firstName", "gender", "dateOfBirth"}, "contact": {"name", "email", "phone"}, "totalAmount"}`
    - `{"type": "held", "code", "holdExpiresAt", "totalAmount"}` (Task 4 tạo)
    - `{"type": "handoff", "doneSoFar": [str], "tried": [str], "question": str}`
  - Mỗi phần tử của `trace` có dạng `{"tool", "args", "status"}`, và mỗi tool result có `status`.
- Produces (trong `tests/fakes.py`):
  - `ScriptedModel`, `scripted(*replies)`, `call(name, **args) -> AIMessage`: copy từ `tests/test_run.py` của repo cũ, bỏ phần `with_structured_output`.
  - `itinerary(flight_id: int, number: str, time: str, price: int, fare_id: int = 1) -> dict`: một hành trình bay thẳng SGN→DAD ngày 2026-10-30, đúng dạng `Itinerary` của `GET /api/flights/search`, với `fareOptions[0] = {"fareFamilyId": fare_id, "name": "Phổ thông", "totalPrice": price, "adultPrice": price, "seatsLeft": 9, ...}`.
  - `FLIGHTS`: bốn hành trình `VJ610 14:20 1_150_000` (id 610), `VN122 08:10 1_850_000` (id 122), `QH104 06:00 1_920_000` (id 104), `VJ604 08:30 2_480_000` (id 604).
  - `FakeBackend(flights=FLIGHTS, me_status=200, quote_error: tuple[int, str, list] | None = None, hold_error: tuple[int, str] | None = None, detail_patch: dict | None = None, hold_delay=0.0)` gồm:
    - thuộc tính `requests: list[httpx.Request]`, `holds: list[dict]` (body của mỗi lần `POST /api/bookings`, kể cả lần thất bại), `transport: httpx.MockTransport`;
    - method `backend(csrf="t") -> Backend`, tạo `Backend("http://be", "JSESSIONID=s1", csrf, self.transport)`.
    - Các route:
      - `GET /api/airports`: SGN, DAD, HAN có `countryCode "VN"`, BKK có `"TH"`.
      - `GET /api/me`: `{"fullName": "Nguyễn Hùng Cường", "email": "customer@demo.local", "phone": "0901234567"}`, hoặc trả `me_status` kèm `ProblemDetail`.
      - `GET /api/flights/search`: `{"itineraries": flights}`.
      - `POST /api/bookings/quote`: `{"totalAmount": <totalPrice của gói đã chọn>}`, hoặc `quote_error`, là bộ `(status, code, errors)`.
      - `POST /api/bookings`: chờ `hold_delay` giây rồi trả 201 `{"code": "K7M2QX", "status": "PENDING_PAYMENT", "holdExpiresAt": "2026-10-30T06:25:00+07:00", "totalAmount": ...}`. Nếu có `hold_error` thì chỉ trả lỗi đó ở lần gọi đầu.
      - `GET /api/bookings/K7M2QX`: `BookingDetail` dựng từ body của lần giữ chỗ cuối (một hành trình `OUTBOUND`, có `segments[].flightId`/`departureAirport`/`departureTime`/`arrivalAirport`, một hành khách, `holdExpiresAt` và `totalAmount` giống response giữ chỗ), sau đó ghi đè bằng `detail_patch`.

- [ ] **Step 1: Viết `tests/fakes.py`** theo Interfaces ở trên.

- [ ] **Step 2: Viết test hỏng**

```python
# agent/tests/test_harness.py
from flight_agent.core import Harness
from tests.fakes import FakeBackend

ROUTE = dict(origin="SGN", destination="DAD", date="2026-10-30", depart_before="12:00", max_price=2_000_000)
PAX = dict(full_name="Nguyễn Hùng Cường", gender="MALE", date_of_birth="2004-03-12")


def ready(fake=None):
    fake = fake or FakeBackend()
    h = Harness()
    h.begin_turn(fake.backend(), "2026-10-26")
    return h, fake


def searched(fake=None):
    h, fake = ready(fake)
    h.execute("set_constraints", ROUTE)
    h.execute("search_flights", {})
    return h, fake


def test_search_and_hold_are_denied_before_constraints():
    h, _ = ready()
    assert h.execute("search_flights", {})["status"] == "denied"
    assert h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, **PAX))["status"] == "denied"


def test_set_constraints_normalises_and_shows_a_card():
    h, _ = ready()
    r = h.execute("set_constraints", {**ROUTE, "depart_before": "9:00", "max_price": "2.000.000"})
    assert r["status"] == "ok"
    assert h.constraints.depart_before == "09:00" and h.constraints.max_price == 2_000_000
    assert h.cards[-1]["type"] == "constraints"


def test_unknown_airport_and_past_date_are_invalid():
    h, _ = ready()
    assert h.execute("set_constraints", {**ROUTE, "destination": "XXX"})["status"] == "invalid_param"
    assert h.execute("set_constraints", {**ROUTE, "date": "2026-10-25"})["status"] == "invalid_param"


def test_international_route_hands_off():
    h, _ = ready()
    h.execute("set_constraints", {**ROUTE, "destination": "BKK"})
    assert h.stop_reason == "international"


def test_search_splits_matching_and_nearest():
    h, _ = ready()
    h.execute("set_constraints", ROUTE)
    r = h.execute("search_flights", {})
    assert [i["flights"] for i in r["matching"]] == ["VN122", "QH104"]           # rẻ nhất trước
    assert {i["flights"] for i in r["nearest"]} == {"VJ610", "VJ604"}            # mỗi chuyến chỉ phạm 1 ràng buộc
    assert h.stop_reason is None


def test_no_matching_flight_stops_with_the_nearest_in_the_question():
    h, _ = ready()
    h.execute("set_constraints", {**ROUTE, "max_price": 1_000_000})
    h.execute("search_flights", {})
    assert h.stop_reason == "no_match"
    assert "VN122" in h.handoff()["question"]


def test_hold_on_a_flight_that_breaks_constraints_is_denied():
    h, fake = searched()
    r = h.execute("propose_hold", dict(flight_ids=[610], fare_family_id=1, **PAX))
    assert r["status"] == "denied"
    assert h.proposal is None and not [c for c in h.cards if c["type"] == "proposal"]
    assert not [q for q in fake.requests if q.url.path == "/api/bookings/quote"]


def test_hold_on_a_flight_not_in_the_search_is_denied():
    h, _ = searched()
    assert h.execute("propose_hold", dict(flight_ids=[999], fare_family_id=1, **PAX))["status"] == "denied"


def test_valid_hold_quotes_and_waits_for_approval():
    h, fake = searched()
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, **PAX))
    assert r["status"] == "pending_approval"
    body = h.proposal.body
    assert body["journeys"] == [{"direction": "OUTBOUND", "flightIds": [122], "fareFamilyId": 1}]
    assert (body["passengers"][0]["lastName"], body["passengers"][0]["firstName"]) == ("NGUYEN", "HUNG CUONG")
    assert body["passengers"][0]["type"] == "ADULT" and body["baggage"] == []
    assert body["contact"] == {"name": "Nguyễn Hùng Cường", "email": "customer@demo.local", "phone": "0901234567"}
    assert h.proposal.total == 1_850_000
    assert h.cards[-1]["type"] == "proposal" and h.cards[-1]["proposalId"] == h.proposal.id
    assert not [q for q in fake.requests if q.url.path == "/api/bookings"]


def test_one_word_name_is_invalid():
    h, _ = searched()
    assert h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, full_name="Cường",
                                          gender="MALE", date_of_birth="2004-03-12"))["status"] == "invalid_param"


def test_changing_constraints_drops_the_pending_proposal():
    h, _ = searched()
    h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, **PAX))
    h.execute("set_constraints", {**ROUTE, "max_price": 1_900_000})
    assert h.proposal is None and h.found == []


def test_backend_validation_error_goes_back_to_the_model():
    h, _ = searched(FakeBackend(quote_error=(400, "PASSENGER_RULES_VIOLATED",
                                             [{"field": "passengers[0].dateOfBirth", "message": "Sai loại khách"}])))
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, **PAX))
    assert r["status"] == "backend_error" and r["code"] == "PASSENGER_RULES_VIOLATED"
    assert h.stop_reason is None


def test_passport_error_hands_off_as_international():
    h, _ = searched(FakeBackend(quote_error=(400, "PASSENGER_RULES_VIOLATED",
                                             [{"field": "passengers[0].passportNumber", "message": "Cần hộ chiếu"}])))
    h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, **PAX))
    assert h.stop_reason == "international"


def test_expired_session_and_staff_account_hand_off():
    for status, reason in ((401, "unauthenticated"), (403, "forbidden")):
        h, _ = searched(FakeBackend(me_status=status))
        h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, **PAX))
        assert h.stop_reason == reason


def test_handoff_has_one_question_and_the_trace():
    h, _ = ready()
    h.execute("set_constraints", {**ROUTE, "max_price": 1_000_000})
    h.execute("search_flights", {})
    card = h.handoff()
    assert card["type"] == "handoff" and card["question"].count("?") == 1
    assert card["tried"][-1].startswith("search_flights")
```

- [ ] **Step 3: Chạy để thấy test hỏng**

Run: `cd agent && uv run pytest tests/test_harness.py -v`
Expected: FAIL với `ImportError: cannot import name 'Harness'`.

- [ ] **Step 4: Viết Harness trong `core.py`**

Lấy khung `Harness` của repo cũ (`stop`, `before_model`, `is_looping`, `execute`) và đổi như sau:

- **`execute`:** thứ tự kiểm là lặp, rồi `check_permission`, rồi gọi tool. `TypeError` thành `invalid_param`. `BackendError` được xử lý thế này:
  - `401` → `stop("unauthenticated")`;
  - `403` → `stop("forbidden")`;
  - `PASSENGER_RULES_VIOLATED` có `field` kết thúc bằng `passportNumber` hoặc `passportExpiry` → `stop("international")`;
  - các lỗi còn lại trả `{"status": "backend_error", "code", "detail", "errors"}` cho model.

  Mỗi lần gọi đều append `{"tool", "args", "status"}` vào `trace`. Bỏ `get_booking` khỏi ngoại lệ của phát hiện lặp vì tool này không còn.

- **`check_permission`:**
  - tool lạ → chặn;
  - `search_flights`/`propose_hold` khi `constraints is None` → `"Chưa có ràng buộc. Gọi set_constraints trước."`;
  - `propose_hold` khi `(flight_ids, fare_family_id)` không trùng một hành trình trong `found` có `broken == []` → chặn, kèm `to_prompt()`.
- **`set_constraints`:**
  - chuẩn hoá `depart_before`/`max_price` bằng `normalise` (bỏ qua nếu là `None`);
  - lấy `airports` một lần từ `GET /api/airports`; mã lạ thì `invalid_param` (hint liệt kê các mã có), hai sân bay khác `countryCode` thì `stop("international")`;
  - ngày trước `today` hoặc sai định dạng → `invalid_param`;
  - khi thành công: đặt `constraints`, xoá `found` và `proposal`, thêm thẻ `constraints`, trả `{"status": "ok", "constraints": to_prompt()}`.
- **`search_flights`:**
  - gọi `GET /api/flights/search` với `from`, `to`, `date`, `adults=1`, `cabin=ECONOMY`;
  - rút gọn mỗi hành trình thành `{"flight_ids", "flights" ("VN122" hoặc "VN122+VN130"), "segments" (dạng thẻ proposal), "depart", "stops", "fare_family_id", "fare", "price", "seats_left", "broken"}`, lấy giá từ `fareOptions[0]` (rẻ nhất), bỏ hành trình không có `fareOptions`;
  - lưu vào `found`;
  - trả `{"status": "ok", "matching": [...]}` (các hành trình `broken == []`, xếp theo `price`) cùng `"nearest"` (tối đa 2 hành trình rẻ nhất có `broken` dài đúng 1);
  - nếu `matching` rỗng thì `stop("no_match")`.
- **`propose_hold`:**
  - `gender` phải là `MALE` hoặc `FEMALE`; `split_name` trả `None` thì `invalid_param`;
  - gọi `GET /api/me` để lấy contact, dựng body như test, rồi gọi `POST /api/bookings/quote`;
  - `totalAmount > max_price` → `denied`;
  - qua hết thì tạo `Proposal(uuid4().hex, body, total, card)`, gán vào `self.proposal` (đè đề xuất cũ), thêm thẻ, trả `{"status": "pending_approval", "total": total}`.
- **`is_done(detail, proposal)`:** trả `True` khi đủ các điều kiện:
  - `status == "PENDING_PAYMENT"`;
  - có đúng một journey, và journey đó là `OUTBOUND`;
  - danh sách `flightId` của segments bằng `proposal.body["journeys"][0]["flightIds"]`;
  - có đúng một hành khách;
  - `totalAmount == proposal.total`;
  - `constraints.is_ok(seg0.departureAirport, segN.arrivalAirport, seg0.departureTime, totalAmount)`.
- **`handoff()`:**
  - `doneSoFar` gồm `to_prompt()` nếu có ràng buộc, cộng thêm `"Chưa giữ chỗ, chưa trừ tiền."`;
  - `tried` là các dòng `f"{tool}({args}) → {status}"`;
  - `question` lấy theo `stop_reason` từ bảng dưới đây. Mỗi câu có đúng một dấu `?`; `{…}` lấy từ `nearest[0]`, hoặc từ lời gọi cuối với `loop`.

| `stop_reason`     | `question`                                                                                                                                                                                                             |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `no_match`        | `Không có chuyến nào thoả mọi ràng buộc. Gần nhất là {flights} lúc {HH:MM}, {giá} đ. Bạn muốn nới giờ bay hay giá tối đa?` (không có `nearest`: `Không có chuyến nào ngày {date}. Bạn muốn đổi sang ngày khác không?`) |
| `budget`          | `Trợ lý đã dùng hết 10 lần gọi mô hình cho lượt này. Bạn muốn thử lại hay tự đặt trên /flights?`                                                                                                                       |
| `loop`            | `Trợ lý lặp lại {tool} mà không tiến triển. Bạn muốn thử lại hay tự đặt trên /flights?`                                                                                                                                |
| `infra`           | `Mô hình AI đang quá tải hoặc không phản hồi. Bạn thử lại sau ít phút nhé?`                                                                                                                                            |
| `unauthenticated` | `Phiên đăng nhập đã hết hạn. Bạn đăng nhập lại rồi thử tiếp nhé?`                                                                                                                                                      |
| `forbidden`       | `Trợ lý chỉ dùng cho tài khoản khách hàng. Bạn đăng nhập bằng tài khoản khách hàng nhé?`                                                                                                                               |
| `international`   | `Hành trình quốc tế cần khai hộ chiếu, trợ lý chưa hỗ trợ. Bạn đặt chuyến này trên /flights nhé?`                                                                                                                      |
| `hold_mismatch`   | `Booking {code} đọc lại không khớp với thẻ đã duyệt. Bạn kiểm tra ở /bookings/{code} nhé?` (Task 4 gán `code`)                                                                                                         |

- [ ] **Step 5: Chạy test để thấy pass**

Run: `cd agent && uv run pytest -v`
Expected: toàn bộ test của Task 1–3 pass.

- [ ] **Step 6: Commit**

```bash
git add agent/flight_agent/core.py agent/tests/fakes.py agent/tests/test_harness.py
git commit -m "feat(agent): harness với set_constraints, search_flights, propose_hold"
```

---

### Task 4: Lượt chat ReAct và duyệt giữ chỗ

**Files:**

- Create: `agent/flight_agent/react.py`, `agent/flight_agent/chat.py`
- Test: `agent/tests/test_chat.py`

**Interfaces:**

- Consumes: `Harness`, `Proposal`, `StopRun`, `BUDGET` (Task 3); `Backend`, `BackendError` (Task 2); `fakes.py` (Task 3).
- Produces:
  - `react.system_prompt(harness: Harness) -> str`.
  - `react.run_turn(harness: Harness, model, history: list, text: str) -> tuple[str, list]`: trả về câu trả lời cuối của model và lịch sử mới gồm toàn bộ `out["messages"]`.
  - `chat.INFRA_ERRORS`: copy từ `run.py` của repo cũ.
  - `@dataclass chat.Chat(id: str, session: str, harness: Harness, history: list, lock: threading.RLock)`.
  - `chat.Chats` có `create(session: str) -> Chat` và `get(chat_id: str, session: str) -> Chat | None`. `get` trả `None` khi không có chat hoặc khi `session` khác với lúc tạo.
  - `chat.turn(chat: Chat, model, text: str, backend: Backend, today: str) -> dict`.
  - `chat.approve(chat: Chat, model, proposal_id: str, backend: Backend, today: str) -> dict`, ném `LookupError` khi đề xuất không còn hiệu lực.
  - Cả hai hàm trả `{"reply": str, "trace": list[dict], "cards": list[dict]}`.

- [ ] **Step 1: Viết test hỏng**

```python
# agent/tests/test_chat.py
import threading

import pytest
from langchain_core.messages import AIMessage

from flight_agent.chat import Chats, approve, turn
from tests.fakes import FakeBackend, call, scripted

ROUTE = dict(origin="SGN", destination="DAD", date="2026-10-30", depart_before="12:00", max_price=2_000_000)
PAX = dict(full_name="Nguyễn Hùng Cường", gender="MALE", date_of_birth="2004-03-12")
TODAY = "2026-10-26"


def proposed(fake, *extra):
    chat = Chats().create("s1")
    model = scripted(call("set_constraints", **ROUTE), call("search_flights"),
                     call("propose_hold", flight_ids=[122], fare_family_id=1, **PAX),
                     AIMessage(content="Bạn bấm Giữ chỗ để mình giữ ghế nhé."), *extra)
    out = turn(chat, model, "Đặt giúp mình 1 vé SGN đi Đà Nẵng thứ 6 này, trước 12h, dưới 2 triệu",
               fake.backend(), TODAY)
    return chat, model, out


def test_a_turn_ends_with_the_proposal_and_never_holds():
    fake = FakeBackend()
    _, _, out = proposed(fake)
    assert [c["type"] for c in out["cards"]] == ["constraints", "proposal"]
    assert out["reply"] == "Bạn bấm Giữ chỗ để mình giữ ghế nhé."
    assert fake.holds == []


def test_model_claiming_it_held_the_seat_is_not_a_hold():
    fake = FakeBackend()
    chat = Chats().create("s1")
    out = turn(chat, scripted(call("set_constraints", **ROUTE), call("search_flights"),
                              AIMessage(content="Đã giữ chỗ xong!")), "Đặt vé", fake.backend(), TODAY)
    assert "held" not in [c["type"] for c in out["cards"]]
    assert fake.holds == []


def test_approval_holds_the_exact_body_and_reads_it_back():
    fake = FakeBackend()
    chat, model, out = proposed(fake)
    proposal = chat.harness.proposal
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    assert fake.holds == [proposal.body]
    assert result["cards"] == [{"type": "held", "code": "K7M2QX",
                                "holdExpiresAt": "2026-10-30T06:25:00+07:00", "totalAmount": 1_850_000}]
    assert any(q.url.path == "/api/bookings/K7M2QX" for q in fake.requests)


def test_a_proposal_can_be_approved_only_once():
    fake = FakeBackend()
    chat, model, out = proposed(fake)
    pid = out["cards"][-1]["proposalId"]
    approve(chat, model, pid, fake.backend(), TODAY)
    with pytest.raises(LookupError):
        approve(chat, model, pid, fake.backend(), TODAY)
    assert len(fake.holds) == 1


def test_approving_replaced_proposal_is_refused():
    fake = FakeBackend()
    chat = Chats().create("s1")
    out = turn(chat, scripted(call("set_constraints", **ROUTE), call("search_flights"),
                              call("propose_hold", flight_ids=[122], fare_family_id=1, **PAX),
                              call("propose_hold", flight_ids=[104], fare_family_id=1, **PAX),
                              AIMessage(content="Mình đổi sang QH104.")), "Đặt vé", fake.backend(), TODAY)
    first = [c for c in out["cards"] if c["type"] == "proposal"][0]["proposalId"]
    with pytest.raises(LookupError):
        approve(chat, scripted(), first, fake.backend(), TODAY)
    assert fake.holds == []


def test_concurrent_approvals_create_one_booking():
    fake = FakeBackend(hold_delay=0.05)
    chat, model, out = proposed(fake)
    pid, results = out["cards"][-1]["proposalId"], []

    def click():
        try:
            results.append(approve(chat, model, pid, fake.backend(), TODAY))
        except LookupError:
            results.append("refused")

    threads = [threading.Thread(target=click) for _ in range(2)]
    [t.start() for t in threads]
    [t.join() for t in threads]
    assert len(fake.holds) == 1 and results.count("refused") == 1


def test_read_back_mismatch_hands_off_instead_of_held():
    for patch in ({"status": "EXPIRED"}, {"totalAmount": 1_900_000}):
        fake = FakeBackend(detail_patch=patch)
        chat, model, out = proposed(fake)
        result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
        assert [c["type"] for c in result["cards"]] == ["handoff"]
        assert "K7M2QX" in result["cards"][0]["question"]


def test_sold_out_on_approval_lets_the_agent_propose_another_flight():
    fake = FakeBackend(hold_error=(409, "SEATS_UNAVAILABLE"))
    chat, model, out = proposed(fake, call("propose_hold", flight_ids=[104], fare_family_id=1, **PAX),
                                AIMessage(content="VN122 vừa hết ghế, bạn duyệt QH104 nhé."))
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    proposals = [c for c in result["cards"] if c["type"] == "proposal"]
    assert proposals[0]["segments"][0]["flightNumber"] == "QH104"
    assert len(fake.holds) == 1                     # chỉ lần giữ thất bại


def test_budget_ends_the_turn_with_a_handoff():
    fake = FakeBackend()
    dates = [f"2026-11-{d:02d}" for d in range(1, 12)]
    out = turn(Chats().create("s1"), scripted(*[call("set_constraints", **{**ROUTE, "date": d}) for d in dates]),
               "Đặt vé", fake.backend(), TODAY)
    assert out["cards"][-1]["type"] == "handoff"
    assert "10 lần" in out["cards"][-1]["question"]


def test_loop_ends_the_turn_with_a_handoff():
    fake = FakeBackend()
    out = turn(Chats().create("s1"), scripted(call("set_constraints", **ROUTE),
                                              *[call("search_flights") for _ in range(5)]),
               "Đặt vé", fake.backend(), TODAY)
    assert out["cards"][-1]["type"] == "handoff"
    assert len(out["trace"]) == 4


def test_llm_outage_is_a_handoff_not_a_crash():
    import openai
    import httpx
    err = openai.APITimeoutError(request=httpx.Request("POST", "http://llm"))
    out = turn(Chats().create("s1"), scripted(err), "Đặt vé", FakeBackend().backend(), TODAY)
    assert out["cards"][-1]["type"] == "handoff"
    assert "thử lại sau" in out["cards"][-1]["question"]


def test_chat_belongs_to_its_session():
    chats = Chats()
    chat = chats.create("s1")
    assert chats.get(chat.id, "s1") is chat
    assert chats.get(chat.id, "s2") is None
    assert chats.get("nope", "s1") is None
```

- [ ] **Step 2: Chạy để thấy test hỏng**

Run: `cd agent && uv run pytest tests/test_chat.py -v`
Expected: FAIL với `ImportError`.

- [ ] **Step 3: Viết `react.py`**

Giữ cấu trúc `run_react` của repo cũ: middleware `budget` gọi `harness.before_model()`, middleware `through_harness` trả `ToolMessage(content=json.dumps(result, ensure_ascii=False))`. Gọi `agent.invoke({"messages": [*history, HumanMessage(text)]})`, trả `(out["messages"][-1].text, out["messages"])`.

`system_prompt(harness)` viết bằng tiếng Việt, gồm các ý sau:

- hôm nay là `harness.today` (giờ Việt Nam), đổi ngày tương đối sang `YYYY-MM-DD`;
- chỉ đặt một chiều cho một người lớn; khứ hồi, nhiều khách hay trẻ em thì nói chưa hỗ trợ và mời dùng /flights;
- gọi `set_constraints` trước rồi mới `search_flights`, mặc định chọn hành trình rẻ nhất trong `matching`;
- hỏi họ tên đầy đủ, giới tính, ngày sinh nếu chưa có, không được bịa;
- sau `pending_approval` thì nhắc khách bấm "Giữ chỗ" trên thẻ, không bao giờ nói đã giữ chỗ;
- gặp `denied` hay `backend_error` thì đọc `hint`/`detail` rồi sửa, hoặc hỏi lại khách.

Nếu đã có ràng buộc thì nối thêm `to_prompt()` vào cuối prompt.

- [ ] **Step 4: Viết `chat.py`**

- **`Chats`:** dùng `dict` với id là `uuid4().hex`.
- **`turn`:**
  - `with chat.lock:`, rồi `harness.begin_turn(backend, today)`, rồi `run_turn`;
  - bắt `StopRun` (harness đã dừng), bắt `INFRA_ERRORS` → `harness.stop("infra")`;
  - nếu có `stop_reason`: thêm thẻ `handoff()`, `reply` là câu hỏi của thẻ, lịch sử chỉ nối thêm `HumanMessage(text)` và `AIMessage(question)`, vì lịch sử dở dang của lần `invoke` bị huỷ có thể chứa tool call chưa có kết quả;
  - trả `{"reply", "trace": harness.trace, "cards": harness.cards}`.
- **`approve`:**
  - `with chat.lock:`, rồi `harness.begin_turn(backend, today)`;
  - nếu `harness.proposal` là `None` hoặc `id` khác `proposal_id` thì `raise LookupError`;
  - lấy đề xuất ra rồi đặt `harness.proposal = None` **trước** khi gọi backend, để mỗi đề xuất chỉ dùng được một lần;
  - `POST /api/bookings` với `proposal.body`:
    - `BackendError` 401 hoặc 403 → `stop` theo cùng quy tắc của `execute` rồi trả bàn giao;
    - lỗi khác → `turn(chat, model, f"[Hệ thống] Giữ chỗ thất bại: {e.code}: {e.detail}. Đề xuất chuyến khác nếu còn chuyến thoả ràng buộc.", backend, today)`. `RLock` cho phép gọi lồng như vậy;
  - giữ chỗ thành công thì `GET /api/bookings/{code}`:
    - `is_done` đúng → thẻ `held` (lấy `holdExpiresAt` và `totalAmount` từ `detail`), đồng thời nối `AIMessage(f"Đã giữ chỗ {code}, hết hạn lúc {giờ}.")` vào lịch sử để các lượt sau model biết;
    - `is_done` sai → `stop("hold_mismatch")` (handoff dùng `code`).

- [ ] **Step 5: Chạy test để thấy pass**

Run: `cd agent && uv run pytest -v`
Expected: toàn bộ test pass.

- [ ] **Step 6: Commit**

```bash
git add agent/flight_agent/react.py agent/flight_agent/chat.py agent/tests/test_chat.py
git commit -m "feat(agent): lượt chat ReAct, duyệt giữ chỗ và đọc lại booking"
```

---

### Task 5: Service FastAPI

**Files:**

- Create: `agent/flight_agent/app.py`, `agent/README.md`
- Modify: `CLAUDE.md` (mục Commands, thêm một dòng)
- Test: `agent/tests/test_app.py`

**Interfaces:**

- Consumes: `Chats`, `turn`, `approve` (Task 4); `Backend` (Task 2); `make_model` (Task 1).
- Produces (frontend ở Task 6 gọi đúng các endpoint này):
  - `vn_today(now: datetime | None = None) -> str`: ngày ở UTC+7, dùng `timezone(timedelta(hours=7))`, không dùng `zoneinfo` vì Windows thiếu tzdata.
  - `create_app(model=None, backend_url: str | None = None, transport: httpx.BaseTransport | None = None, today: Callable[[], str] = vn_today) -> FastAPI`. `model=None` thì gọi `make_model()`. `backend_url=None` thì đọc `BACKEND_URL` (mặc định `http://localhost:8080`).
  - `POST /agent/chats`:
    - trả `201 {"chatId"}`;
    - không có cookie `JSESSIONID` thì trả `401 {"detail": "Bạn cần đăng nhập."}`.
  - `POST /agent/chats/{chat_id}/messages`:
    - body `{"text": str}`, dài 1–1000 ký tự;
    - trả `200 {"reply", "trace", "cards"}`;
    - chat không có hoặc của phiên khác thì trả `404 {"detail": "Không tìm thấy cuộc trò chuyện."}`.
  - `POST /agent/chats/{chat_id}/proposals/{proposal_id}/approve`:
    - trả `200`, cùng dạng với messages;
    - chat không thấy thì `404` như trên;
    - gặp `LookupError` thì `404 {"detail": "Thẻ này đã hết hiệu lực."}`.
  - Mỗi request tự tạo `Backend(backend_url, request.headers.get("cookie", ""), request.headers.get("x-xsrf-token"), transport)` và lấy `today()`.

- [ ] **Step 1: Viết test hỏng**

```python
# agent/tests/test_app.py
from datetime import datetime, timezone

from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage

from flight_agent.app import create_app, vn_today
from tests.fakes import FakeBackend, call, scripted

ROUTE = dict(origin="SGN", destination="DAD", date="2026-10-30", depart_before="12:00", max_price=2_000_000)
PAX = dict(full_name="Nguyễn Hùng Cường", gender="MALE", date_of_birth="2004-03-12")
COOKIE = "JSESSIONID=s1; XSRF-TOKEN=from-cookie"


def client(fake, *replies):
    app = create_app(model=scripted(*replies), backend_url="http://be",
                     transport=fake.transport, today=lambda: "2026-10-26")
    return TestClient(app)


def hold_flow():
    return (call("set_constraints", **ROUTE), call("search_flights"),
            call("propose_hold", flight_ids=[122], fare_family_id=1, **PAX), AIMessage(content="Bấm Giữ chỗ nhé."))


def test_vn_today_uses_utc_plus_7():
    assert vn_today(datetime(2026, 10, 26, 18, 30, tzinfo=timezone.utc)) == "2026-10-27"


def test_creating_a_chat_needs_a_session():
    c = client(FakeBackend())
    assert c.post("/agent/chats").status_code == 401
    assert c.post("/agent/chats", headers={"Cookie": COOKIE}).status_code == 201


def test_another_session_cannot_use_the_chat():
    c = client(FakeBackend(), *hold_flow())
    chat = c.post("/agent/chats", headers={"Cookie": COOKIE}).json()["chatId"]
    r = c.post(f"/agent/chats/{chat}/messages", json={"text": "hi"}, headers={"Cookie": "JSESSIONID=s2"})
    assert r.status_code == 404


def test_full_flow_forwards_the_incoming_csrf_header_only():
    fake = FakeBackend()
    c = client(fake, *hold_flow())
    h = {"Cookie": COOKIE, "X-XSRF-TOKEN": "from-header"}
    chat = c.post("/agent/chats", headers=h).json()["chatId"]
    out = c.post(f"/agent/chats/{chat}/messages", json={"text": "Đặt vé"}, headers=h).json()
    pid = out["cards"][-1]["proposalId"]
    held = c.post(f"/agent/chats/{chat}/proposals/{pid}/approve", headers=h)
    assert held.status_code == 200 and held.json()["cards"][0]["type"] == "held"
    posts = [q for q in fake.requests if q.method == "POST"]
    assert posts and all(q.headers["x-xsrf-token"] == "from-header" for q in posts)
    again = c.post(f"/agent/chats/{chat}/proposals/{pid}/approve", headers=h)
    assert again.status_code == 404 and again.json()["detail"] == "Thẻ này đã hết hiệu lực."


def test_missing_csrf_header_is_not_invented():
    fake = FakeBackend()
    c = client(fake, *hold_flow())
    chat = c.post("/agent/chats", headers={"Cookie": COOKIE}).json()["chatId"]
    c.post(f"/agent/chats/{chat}/messages", json={"text": "Đặt vé"}, headers={"Cookie": COOKIE})
    quote = [q for q in fake.requests if q.url.path == "/api/bookings/quote"][0]
    assert "x-xsrf-token" not in quote.headers


def test_empty_message_is_rejected():
    c = client(FakeBackend())
    chat = c.post("/agent/chats", headers={"Cookie": COOKIE}).json()["chatId"]
    assert c.post(f"/agent/chats/{chat}/messages", json={"text": ""}, headers={"Cookie": COOKIE}).status_code == 422
```

- [ ] **Step 2: Chạy để thấy test hỏng**

Run: `cd agent && uv run pytest tests/test_app.py -v`
Expected: FAIL với `ImportError`.

- [ ] **Step 3: Viết `app.py`**

Endpoint viết dạng `def` (không `async`) để FastAPI chạy trong threadpool. `RLock` của `Chat` đã bảo vệ trường hợp chạy song song. Đọc `.env` bằng `load_dotenv()` ở `create_app`.

- [ ] **Step 4: Chạy test để thấy pass**

Run: `cd agent && uv run pytest -v`
Expected: toàn bộ test pass.

- [ ] **Step 5: Chạy thử service**

Run: `cd agent && cp .env.example .env` (điền key thật) rồi `uv run uvicorn flight_agent.app:create_app --factory --port 8000`
Expected: `Uvicorn running on http://127.0.0.1:8000`; `curl -X POST localhost:8000/agent/chats` trả 401.

- [ ] **Step 6: Viết `agent/README.md` và dòng trong `CLAUDE.md`**

README ngắn bằng tiếng Việt: agent là gì (một câu, link tới spec), cách cài (`uv sync`), cách test (`uv run pytest`), cách chạy (lệnh ở Step 5), các biến trong `.env`.

Thêm vào mục Commands của `CLAUDE.md` (viết tiếng Anh như phần còn lại của file):

`- Experimental booking agent (branch feature/flight-agent, spec docs/superpowers/specs/2026-10-26-tro-ly-dat-ve-agent-design.md): cd agent && uv sync, uv run pytest, run uv run uvicorn flight_agent.app:create_app --factory --port 8000 (reads agent/.env); the frontend rewrites /agent/* to AGENT_URL (default http://localhost:8000).`

- [ ] **Step 7: Commit**

```bash
git add agent/flight_agent/app.py agent/tests/test_app.py agent/README.md CLAUDE.md
git commit -m "feat(agent): service FastAPI cho chat và duyệt giữ chỗ"
```

---

### Task 6: Trang `/assistant`

**Files:**

- Modify: `frontend/next.config.ts`, `frontend/src/proxy.ts`, `frontend/src/lib/api.ts`, `frontend/src/components/site-header.tsx`, `frontend/e2e/smoke.spec.ts`
- Create: `frontend/src/app/assistant/page.tsx`, `frontend/src/app/assistant/assistant-chat.tsx`, `frontend/src/app/assistant/agent-api.ts`

**Interfaces:**

- Consumes: các endpoint và dạng thẻ của Task 3 và Task 5.
- Produces (`agent-api.ts`):
  - type `Card` là union bốn loại thẻ ở Task 3; type `Turn = { reply: string; trace: { tool: string; args: Record<string, unknown>; status: string }[]; cards: Card[] }`.
  - class `AgentError extends Error`, có `readonly status: number`.
  - `agentPost<T>(path: string, body?: unknown): Promise<T>`: gọi `fetch("/agent" + path)` với method POST, `credentials: "same-origin"`, header `X-XSRF-TOKEN` lấy từ `csrfToken()`.
    - Lỗi mạng, 500, 502, 503, 504 → `AgentError(status hoặc 0, "Trợ lý đang không hoạt động. Hãy thử lại sau.")`.
    - Các lỗi khác → `AgentError(status, detail)`.

Đọc `DESIGN.md` và `frontend/node_modules/next/dist/docs/` (Next 16) trước khi viết.

- [ ] **Step 1: Nối dây**

- `next.config.ts`: thêm `{ source: "/agent/:path*", destination: \`${process.env.AGENT_URL ?? "http://localhost:8000"}/agent/:path\*\` }`vào mảng`rewrites`.
- `proxy.ts`: thêm `"/assistant/:path*"` vào `matcher`.
- `lib/api.ts`: thêm `export` cho `csrfToken`.
- `site-header.tsx`: thêm `["/assistant", "Trợ lý đặt vé"]` vào cuối `NAV.customer.links`.
- `smoke.spec.ts`: thêm `"/assistant"` vào `PROTECTED`.

- [ ] **Step 2: Viết `agent-api.ts`** theo Interfaces.

- [ ] **Step 3: Viết `page.tsx` và `assistant-chat.tsx`**

- **`page.tsx`:** `metadata.title = "Trợ lý đặt vé | SkyLine"`, render `<AssistantChat />`.
- **`assistant-chat.tsx`** (`"use client"`):
  - Bố cục: `SiteHeader`, `h1` "Trợ lý đặt vé", danh sách lượt chat, ô nhập và nút gửi (`pillPrimary`).
  - Gửi tin nhắn:
    - chưa có `chatId` thì `POST /chats` trước;
    - `POST /chats/{id}/messages` nhận `404` thì bỏ `chatId`, tạo chat mới và gửi lại **một lần**, vì service có thể đã khởi động lại (Review Focus 5);
    - trong lúc chờ thì khoá ô nhập và hiện "Trợ lý đang xử lý…".
  - Mỗi lượt của agent hiện ba phần:
    - `reply` (một đoạn văn);
    - `trace` dạng chữ đơn cách màu `muted`, mỗi dòng `tool(args) → status`;
    - các thẻ.
  - Các thẻ:
    - **`constraints`:** chip tuyến, ngày, giờ, giá. `null` hiện "không giới hạn".
    - **`proposal`:** dùng `card-hi`, gồm segments (số hiệu, giờ đi/đến qua `clock`, ngày qua `dmy`), gói giá, hành khách, liên hệ, tổng tiền qua `vnd`, kèm nút "Giữ chỗ" (`pillPrimary`) và "Sửa" (`pillLine`). Chỉ thẻ proposal mới nhất chưa duyệt và chưa bị "Sửa" mới có nút. Trong lúc chờ duyệt, cả hai nút bị khoá. "Sửa" chỉ ẩn nút. Duyệt nhận `404` thì hiện `Notice` với `detail`.
    - **`held`:** "Đã giữ chỗ {code}, hết hạn lúc {clock(holdExpiresAt)} {dmy(...)}" kèm `Link` `pillPrimary` "Thanh toán VNPay" tới `/bookings/{code}`.
    - **`handoff`:** câu `question` nổi bật, `doneSoFar` và `tried` thu gọn trong `<details>`.
  - Lỗi `AgentError` hiện qua `Notice`.
  - Không gọi `new Date()` hay `Date.now()` lúc render.

- [ ] **Step 4: Kiểm tra lint và build**

Run: `cd frontend && npm run lint && npm run build`
Expected: không có lỗi, và build liệt kê route `/assistant`.

- [ ] **Step 5: Smoke test**

Run: `cd frontend && npx playwright test smoke -g assistant`
Expected: 1 passed (`/assistant chưa đăng nhập thì chuyển về /login?next=`).

- [ ] **Step 6: Commit**

```bash
git add frontend
git commit -m "feat(frontend): trang /assistant cho trợ lý đặt vé"
```

---

### Task 7: Chạy thật toàn luồng

Task này không viết code mới, chỉ sửa lỗi nếu chạy thật phát hiện ra. Cần key LLM thật trong `agent/.env`.

- [ ] **Step 1: Bật hạ tầng**

Run: `docker compose up -d postgres mailpit`, backend `cd backend && ./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"`, agent (lệnh ở Task 5 Step 5), frontend `cd frontend && npm run dev`.
Expected: http://localhost:3000 mở được và link "Trợ lý đặt vé" hiện trên header sau khi đăng nhập.

- [ ] **Step 2: Chọn một ngày có chuyến trong seed**

Run: `curl "http://localhost:8080/api/flights/search?from=SGN&to=DAD&date=<ngày>&adults=1&cabin=ECONOMY"` với `<ngày>` cách hôm nay vài ngày.
Expected: `itineraries` không rỗng. Nếu rỗng thì seed `V106` đã cũ quá 58 ngày; làm mới bằng `docker compose down -v` (CLAUDE.md).

- [ ] **Step 3: Đi hết flow**

Đăng nhập `customer@demo.local` / `Demo@1234`, mở `/assistant`, gõ "Đặt giúp mình 1 vé SGN đi Đà Nẵng ngày <ngày>, bay trước 12h, dưới <giá> triệu", rồi trả lời họ tên, giới tính, ngày sinh khi agent hỏi, và bấm "Giữ chỗ".
Expected: lần lượt có thẻ ràng buộc, các bước tool, câu hỏi thông tin, thẻ duyệt, rồi thẻ "Đã giữ chỗ". Bấm "Thanh toán VNPay" thì `/bookings/{code}` hiện booking chờ thanh toán có đồng hồ đếm ngược.

- [ ] **Step 4: Thử bàn giao**

Trong cùng chat, gõ một yêu cầu với giá tối đa rất thấp (ví dụ 100.000 đ).
Expected: thẻ bàn giao nêu chuyến gần nhất và hỏi nới giờ hay nới giá.

- [ ] **Step 5: Thử service khởi động lại**

Tắt rồi bật lại uvicorn, sau đó gửi tiếp một tin nhắn trong trang đang mở.
Expected: trang tự tạo chat mới và trả lời bình thường, không hiện lỗi.

- [ ] **Step 6: Commit các sửa lỗi (nếu có)**

```bash
git add -A agent frontend
git commit -m "fix(agent): sửa lỗi phát hiện khi chạy thật"
```
