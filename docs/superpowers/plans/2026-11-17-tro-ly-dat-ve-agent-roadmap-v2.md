# Lộ trình Nâng cấp Trợ lý Đặt vé (Agent Roadmap v2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nâng cấp trợ lý đặt vé SkyLine (`agent/` và `/assistant`) để người dùng thực tế đặt được đủ các nhu cầu cốt lõi (chọn gói giá/hạng ghế, lọc hãng/bay thẳng/khung giờ, tách hành lý theo chiều, xem phương án đổi chuyến, huỷ giữ chỗ qua thẻ duyệt) đồng thời bổ sung các lớp Harness còn thiếu từ giáo trình Agentic AI (`docs/ref/agentic-ai` Buổi 1–9: Runtime Guardrail, Tool-output Compaction, Stall Detection, Timeout/Rate-limit, Structured Logging, SQLite State Persistence) mà không bị over-engineering và không phải sửa Backend Java.

**Architecture:** Giữ nguyên kiến trúc **Single ReAct Agent + Code-controlled Harness** (`agent = model + harness`, Bậc 3 trên thang tự chủ Buổi 8–9) đã chứng minh độ ổn định cao ở giai đoạn 1. Mở rộng dữ liệu `Constraints` và `Proposal` trong `agent/flight_agent/core.py`, thêm lớp hậu kiểm & nén ngữ cảnh trong `agent/flight_agent/react.py`, thêm lưu trữ SQLite & log cấu trúc trong `agent/flight_agent/chat.py`, thêm rate-limit trong `agent/flight_agent/app.py`, và bổ sung điều khiển phiên chat trên `frontend/src/app/assistant/`.

**Tech Stack:** Python 3.12+, FastAPI, LangChain (`create_agent`), `httpx`, `sqlite3` (stdlib), `pytest` (offline fake model & `MockTransport`), Next.js 16 + TypeScript + Tailwind CSS.

**Spec:** [docs/superpowers/specs/2026-10-26-tro-ly-dat-ve-agent-design.md](../specs/2026-10-26-tro-ly-dat-ve-agent-design.md) · Lộ trình giai đoạn 1: [2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md](2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md) · Tham chiếu lý thuyết: `docs/ref/agentic-ai/` (Buổi 1–9).

---

## 1. Bảng lộ trình tổng thể (Bước 8 → Bước 13)

Làm theo thứ tự 3 đợt ưu tiên. Mỗi Task dưới đây có thể thực thi trực tiếp hoặc tách thành file plan riêng `YYYY-MM-DD-tro-ly-NN-<ten>.md` nếu cần.

| Bước (Task) | Đợt | Hạng mục nâng cấp | Lớp Harness (Buổi) | Cần trước | Kết quả kiểm chứng được | Xong |
|---|---|---|---|---|---|---|
| **Bước 8** (Task 1) | Đợt 1 (Khả thi) | Mở rộng `Constraints`: chọn hạng ghế (`cabin`), gói giá hoàn/đổi, lọc hãng (`airline`), bay thẳng (`nonstop`), khung giờ (`depart_after`) & cập nhật từng phần | `context`, `tools`, `permission` (Buổi 1, 2, 3) | Bước 7 | Đặt được gói `Economy Classic`/`BUSINESS`, lọc đúng hãng & bay thẳng trước khi cắt `matching[:5]`, sửa lẻ 1 ràng buộc không mất trường cũ | ☑ |
| **Bước 9** (Task 2) | Đợt 1 (Khả thi) | Tách hành lý ký gửi theo chiều (`return_baggage_kg`) + UX nút "Cuộc trò chuyện mới", nút "Dừng" & link `/flights?...` kèm query params | `tools`, `handoff`, `UX` (Buổi 1, 3) | Bước 8 | Mua hành lý riêng chiều đi/chiều về; handoff sinh link `/flights?from=...` bấm vào điền sẵn form; bấm "Cuộc trò chuyện mới" và "Dừng" hoạt động đúng | ☑ |
| **Bước 10** (Task 3) | Đợt 2 (Độ bền) | Runtime Output Guardrail + Tool-output Compaction + Stall Detection (`is_stalling`) | `verification`, `context`, `termination` (Buổi 1, 3, 6, 7, 8–9) | Bước 8 | Chặn câu báo "đã giữ chỗ" láo và link `/chinh-sach/` bịa ngay tại runtime; nén `ToolMessage` cũ trong `chat.history`; dừng sớm với `stall` sau 4 lỗi tool liên tiếp | ☑ |
| **Bước 11** (Task 4) | Đợt 2 (Độ bền) | Wall-clock Timeout & Token Budget mỗi lượt + Rate Limit theo phiên + Structured Trace Log | `termination`, `permission`, `logging` (Buổi 1, 3) | Bước 10 | Ngắt lượt khi quá 90s hoặc 40.000 token; trả HTTP 429 khi vượt 15 tin nhắn/phút; ghi log JSON cấu trúc không chứa PII sau mỗi lượt | ☑ |
| **Bước 12** (Task 5) | Đợt 3 (Tiện ích) | Tra cứu phương án đổi chuyến (`reschedule_options`) & Huỷ giữ chỗ qua thẻ duyệt HITL (`propose_cancel`) | `tools`, `human approval`, `verification` (Buổi 1, 3, 8–9) | Bước 9 | Trả lời đúng chuyến thay thế và tiền bù từ `GET .../reschedule-options`; huỷ đơn `PENDING_PAYMENT` qua thẻ duyệt và đọc lại xác nhận `CANCELLED` | ☑ |
| **Bước 13** (Task 6) | Đợt 3 (Tiện ích) | Lưu bền vững `Chats` xuống SQLite (`sqlite3`) + TTL cho Chat & Proposal | `state` (Buổi 1, 7, 8–9) | Bước 12 | Khởi động lại service vẫn đọc lại được cuộc trò chuyện và bấm duyệt được `Proposal` chưa quá hạn 15 phút; tự dọn chat quá hạn | ☐ |

---

## 2. Đã cân nhắc và bỏ qua (Chống Over-engineering)

| Thành phần (từ `docs/ref/agentic-ai`) | Lý do bỏ qua |
|---|---|
| **Multi-Agent (Manager-Worker, Debate, Group Chat)** (Buổi 8–9) | Đặt vé là chuỗi bước phụ thuộc tuần tự. Thực nghiệm ở Buổi 8–9 chỉ ra Multi-Agent làm giảm hiệu quả (-39% đến -70%) trên tác vụ tuần tự, khuếch đại sai số và tốn ~15× token. |
| **RAG truyền thống (Vector DB Chroma/Qdrant, Embedding, BM25 + RRF, Cross-Encoder Rerank)** (Buổi 6) | Dữ liệu chuyến bay/giá/booking là dữ liệu cấu trúc thời gian thực (phải gọi API). Kho chính sách chỉ có 3 file Markdown (304 dòng) chứa biến `{{key}}` đọc từ DB; cơ chế Progressive Disclosure (`read_policy`) hiện tại chính xác tuyệt đối và tốn 0ms embedding. |
| **Plan-then-Execute & LLM Reflection** (Buổi 3) | Hội thoại đặt vé thay đổi theo từng câu trả lời của khách nên kế hoạch cứng nhanh lỗi thời; dùng LLM chấm lại LLM tốn gấp đôi chi phí và độ trễ trong khi `check_permission`, `is_done` và `guard_reply` chạy bằng code tốn 0 token. |
| **Code Mode (`run_code` sandbox)** (Buổi 7) | Chỉ phù hợp khi xử lý hàng trăm file/log. Đặt vé chỉ gọi vài REST API đã rút gọn; thêm code sandbox mở ra rủi ro bảo mật không cần thiết. |
| **Persistent User Memory (`MEMORY.md` / Vector Memory sở thích)** (Buổi 7) | Hành khách cũ đã lấy trực tiếp từ DB thật qua `list_recent_passengers()`. Lưu sở thích cá nhân dài hạn đem lại ít giá trị trong phiên 3–5 lượt nhưng đòi hỏi thêm UI quản lý/xoá dữ liệu cá nhân. |
| **Tách thành LangGraph `StateGraph` lớn hoặc hệ thống `SKILL.md` động** (Buổi 4, 8–9) | Với 11 tools trong cùng miền đặt vé của khách hàng, `create_agent` + 2 middleware giữ code gọn và dễ test nhất. |
| **Agent tự thanh toán VNPay, tự gửi hoàn/đổi vé không qua duyệt, hoặc thu thập hộ chiếu quốc tế qua chat** | Rủi ro tài chính trực tiếp và bảo mật dữ liệu định danh cá nhân (gửi số hộ chiếu sang LLM bên thứ ba). Giữ ranh giới bàn giao sang `/flights?...` hoặc `/bookings/{code}`. |

---

## Global Constraints

- **Không sửa `backend/`**: toàn bộ API cần dùng (`GET /api/flights/search?cabin=`, `GET /api/bookings/{code}/journeys/{direction}/reschedule-options?date=`, `POST /api/bookings/{code}/cancel`) đã có sẵn trong Spring Boot.
- **Giữ nguyên 5 bất biến an toàn của MVP** ([spec §2](file:///f:/UIT/Flight%20Booking%20System/docs/superpowers/specs/2026-10-26-tro-ly-dat-ve-agent-design.md#L29-L35)):
  1. Không có thao tác ghi (`POST /api/bookings`, `POST /api/bookings/{code}/cancel`) nào chạy khi khách chưa bấm nút duyệt trên thẻ, và dữ liệu thực thi phải khớp 100% với thẻ đã duyệt.
  2. Trạng thái hoàn thành (`held` hoặc `cancelled`) luôn do code đọc lại `GET /api/bookings/{code}` để xác nhận (`is_done`), lời của model không bao giờ được tin.
  3. Không làm hỏng CSRF: `Backend` chỉ chuyển tiếp nguyên header `X-XSRF-TOKEN` từ trình duyệt, không tự đọc từ cookie.
  4. Service agent tắt hoặc lỗi không làm hỏng các trang khác của ứng dụng.
  5. Mọi lượt chat đều chịu trần cứng (số bước, thời gian, token, phát hiện lặp và bế tắc) và dừng bất thường luôn trả về thẻ `handoff`.
- **Ngôn ngữ**: toàn bộ docstring của tool, prompt, thông báo lỗi cho người dùng và comment giải thích đều bằng tiếng Việt.
- **Không thêm dependency nặng**: dùng thư viện chuẩn Python (`sqlite3`, `time`, `logging`, `urllib.parse`), không cài thêm Redis, Chroma hay ORM.

## Review Focus

1. **Cập nhật lẻ một ràng buộc làm mất ràng buộc cũ**: khách đã đặt `SGN → DAD ngày 2026-10-30, 2 người lớn, bay trước 12:00`, lượt sau nhắn `"đổi sang bay thẳng"` và model chỉ gọi `set_constraints(nonstop=True)` → code phải giữ nguyên `origin`, `destination`, `date`, `adults=2`, `depart_before="12:00"` thay vì xoá về mặc định.
2. **Chuyến phù hợp bị cắt mất bởi `MATCHING = 5` trước khi lọc**: khi khách yêu cầu hãng `VN`, bay thẳng (`nonstop=True`), hoặc gói hoàn được (`refundable=True`), bộ lọc trong `Constraints.broken()` phải chạy **trước** bước cắt `[:MATCHING]` để các chuyến `VJ` rẻ hơn nhưng vi phạm không chiếm mất 5 vị trí đầu.
3. **`POST /api/bookings/{code}/cancel` trả `204 No Content` (body rỗng)**: `Backend._send()` gọi `r.json()` trực tiếp sẽ văng `JSONDecodeError` nếu không kiểm tra `r.content`.
4. **Tool-output compaction làm hỏng lượt đang chạy hoặc làm mất `tool_call_id`**: khi nén `ToolMessage` của các lượt trước trong `chat.history`, phải giữ nguyên `tool_call_id` và `name` của `ToolMessage` để Gemini/OpenAI không trả lỗi 400 do lệch cặp `AIMessage.tool_calls` ↔ `ToolMessage`.
5. **Duyệt `Proposal` đã hết hạn (sau 15 phút) hoặc duyệt chéo loại đề xuất**: bấm duyệt trên `Proposal` quá hạn `PROPOSAL_TTL_SECONDS` phải bị từ chối như thẻ hết hiệu lực (404), và `approve()` phải phân nhánh đúng giữa `action == "hold"` và `action == "cancel"`.

## File Structure

```
agent/flight_agent/backend.py      Hỗ trợ body=None trong post() và response 204 No Content (Task 5)
agent/flight_agent/core.py         Mở rộng Constraints, _legs/search_flights (gói giá, cabin, bộ lọc),
                                   return_baggage_kg, flights_url, is_stalling, timeout/token budget,
                                   reschedule_options, propose_cancel (Task 1, 2, 3, 4, 5)
agent/flight_agent/react.py        SYSTEM_PROMPT mới, guard_reply(), compact_history() (Task 1, 2, 3, 5)
agent/flight_agent/chat.py         Hỗ trợ approve cancel, token tracking, log_turn(), SQLite Chats + TTL (Task 3, 4, 5, 6)
agent/flight_agent/app.py          Rate limit theo JSESSIONID, cấu hình AGENT_DB_PATH (Task 4, 6)
agent/tests/fakes.py               Hỗ trợ nhiều gói giá trong tìm chuyến, reschedule-options, cancel booking (Task 1, 5)
agent/tests/test_core.py           Test bộ lọc mới và chuẩn hoá trong Constraints (Task 1)
agent/tests/test_harness.py        Test chọn gói giá/cabin, cập nhật từng phần, hành lý theo chiều,
                                   flights_url, stall, timeout, reschedule_options, propose_cancel (Task 1–5)
agent/tests/test_chat.py           Test guard_reply, compact_history, approve cancel, SQLite persistence & TTL (Task 3, 5, 6)
agent/tests/test_app.py            Test rate limit 429 và đọc lại chat từ SQLite (Task 4, 6)
agent/evals/cases.py               Thêm case eval cho gói giá/bộ lọc và tra cứu đổi chuyến/huỷ giữ chỗ (Task 1, 5)
frontend/src/app/assistant/
  agent-api.ts                     Mở rộng kiểu Card (cancel_proposal, cancelled, bộ lọc constraints),
                                   INTERNAL_LINK hỗ trợ query string, AbortSignal cho agentStream (Task 1, 2, 5)
  assistant-chat.tsx               Hiển thị chip bộ lọc mới, thẻ duyệt huỷ, nút "Cuộc trò chuyện mới" & "Dừng" (Task 1, 2, 5)
```

---

### Task 1 (Bước 8): Mở rộng `Constraints`, chọn Gói giá / Hạng ghế & Cập nhật từng phần

**Files:**
- Modify: `agent/flight_agent/core.py`
- Modify: `agent/flight_agent/react.py`
- Modify: `agent/tests/fakes.py`
- Modify: `frontend/src/app/assistant/agent-api.ts`
- Modify: `frontend/src/app/assistant/assistant-chat.tsx`
- Test: `agent/tests/test_core.py`, `agent/tests/test_harness.py`

**Interfaces:**
- Consumes: `CABINS`, `_conditions`, `Backend.get` trong `agent/flight_agent/core.py`.
- Produces:
  - Mở rộng `Constraints`:
    ```python
    @dataclass(frozen=True)
    class Constraints:
        origin: str
        destination: str
        date: str
        depart_before: str | None = None
        depart_after: str | None = None
        max_price: int | None = None
        adults: int = 1
        children: int = 0
        infants: int = 0
        return_date: str | None = None
        cabin: str = "ECONOMY"
        airline: str | None = None
        nonstop: bool = False
        refundable: bool = False
        changeable: bool = False
    ```
  - `Constraints.broken(self, origin: str, destination: str, departure: str, price: int, back: tuple[str, str, str] | None = None, *, airline: str | None = None, stops: int = 0, refundable: bool = True, changeable: bool = True) -> list[str]`: kiểm tra thêm `depart_after` (`departure[11:16] >= self.depart_after`), `airline` (`self.airline is None or airline == self.airline`), `nonstop` (`not self.nonstop or stops == 0`), `refundable` (`not self.refundable or refundable`), `changeable` (`not self.changeable or changeable`).
  - `Harness.set_constraints(self, origin: str | None = None, destination: str | None = None, date: str | None = None, depart_before: str | None = None, depart_after: str | None = None, max_price: int | None = None, adults: int | None = None, children: int | None = None, infants: int | None = None, return_date: str | None = None, cabin: str | None = None, airline: str | None = None, nonstop: bool | None = None, refundable: bool | None = None, changeable: bool | None = None) -> dict`:
    - Nếu đã có `self.constraints`, tham số nào truyền `None` sẽ kế thừa từ `self.constraints`; truyền `""` hoặc `"NONE"` cho `depart_before`, `depart_after`, `max_price`, `return_date`, `airline` sẽ xoá giới hạn đó về `None` (với `max_price=0` cũng xoá về `None`).
    - Nếu chưa có `self.constraints`, bắt buộc phải có đủ `origin`, `destination`, `date`.
    - `cabin` chuẩn hoá in hoa, phải thuộc `CABINS` (`ECONOMY`, `PREMIUM_ECONOMY`, `BUSINESS`, `FIRST`).
    - `airline` (nếu có) chuẩn hoá in hoa, kiểm trong `GET /api/airlines` (như `airline_rules`).
  - Trong `Harness._legs`: gửi `"cabin": c.cabin` tới `/api/flights/search`; giữ tất cả `fareOptions` trong trường `"fares": [{"fare_family_id", "fare", "conditions", "price", "seats_left", "refundable", "changeable"}]`; chọn gói rẻ nhất trong `fareOptions` thoả `c.refundable` và `c.changeable` (nếu không có gói nào thoả thì lấy `fareOptions[0]`) làm gói mặc định của chặng để tính `price`, `fare_family_id`, `refundable`, `changeable` và kiểm `c.broken(...)`.
  - Trong `Harness._found`: cho phép chọn bất kỳ `fare_family_id` nào nằm trong `j["fares"]` của hành trình đó (không bắt buộc phải là `fareOptions[0]`), và trả bản sao hành trình với `fare_family_id`, `fare`, `price`, `conditions`, `refundable`, `changeable` khớp đúng gói đã chọn, đồng thời tính lại `broken` theo gói đó.

- [ ] **Step 1: Write the failing tests** trong `agent/tests/test_core.py` và `agent/tests/test_harness.py`

```python
# Trong agent/tests/test_core.py:
def test_broken_checks_airline_nonstop_depart_after_and_fare_flags():
    c = Constraints("SGN", "DAD", "2026-10-30", depart_after="08:00", airline="VN",
                    nonstop=True, refundable=True)
    assert c.broken("SGN", "DAD", "2026-10-30T08:10:00+07:00", 1_850_000,
                    airline="VN", stops=0, refundable=True) == []
    assert c.broken("SGN", "DAD", "2026-10-30T07:30:00+07:00", 1_850_000,
                    airline="VJ", stops=1, refundable=False) == [
        "depart_after", "airline", "nonstop", "refundable"
    ]

# Trong agent/tests/test_harness.py:
def test_set_constraints_merges_partial_updates_and_clears_with_none_keyword():
    h, _ = ready()
    h.execute("set_constraints", ROUTE)  # SGN->DAD, 2026-10-30, depart_before=12:00, max_price=2_000_000
    r = h.execute("set_constraints", {"airline": "VN", "nonstop": True, "max_price": "NONE"})
    assert r["status"] == "ok"
    assert (h.constraints.origin, h.constraints.destination, h.constraints.date) == ("SGN", "DAD", "2026-10-30")
    assert h.constraints.depart_before == "12:00" and h.constraints.max_price is None
    assert h.constraints.airline == "VN" and h.constraints.nonstop is True

def test_search_and_hold_support_refundable_fare_family_and_cabin():
    h, fake = ready()
    h.execute("set_constraints", {"origin": "SGN", "destination": "DAD", "date": "2026-10-30",
                                  "refundable": True, "max_price": 2_500_000})
    r = h.execute("search_flights", {})
    assert r["status"] == "ok" and r["matching"][0]["flights"] == "VN122"
    assert r["matching"][0]["fare"] == "Economy Classic" and r["matching"][0]["fare_family_id"] == 2
    hold = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=2, passengers=[PAX]))
    assert hold["status"] == "pending_approval" and h.proposal.total == 2_150_000
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd agent && uv run pytest tests/test_core.py tests/test_harness.py -k "airline_nonstop or merges_partial or refundable_fare" -v`
Expected: FAIL (`TypeError: Constraints.__init__() got an unexpected keyword argument`).

- [ ] **Step 3: Cập nhật `agent/tests/fakes.py` để hành trình `VN122` có thêm gói `Economy Classic` (`fareFamilyId=2`, `refundable=True`, `adultPrice=2_150_000`)**

Trong `tests/fakes.py`, cho `itinerary()` nhận thêm `extra_fares: list[dict] = ()` và gắn gói số 2 (`fareFamilyId=2`, `"name": "Economy Classic"`, `"refundable": True`, `"refundFee": 600_000`, `"checkedBaggageKg": 23`, `"adultPrice": 2_150_000`, `"totalPrice": 2_150_000`) vào `VN122` trong `FLIGHTS`; trong `FakeBackend.handle` (`/api/flights/search`), lọc `fareOptions` theo `request.url.params.get("cabin", "ECONOMY")`.

- [ ] **Step 4: Implement `Constraints` mở rộng, `set_constraints` gộp từng phần, và chọn gói giá trong `agent/flight_agent/core.py` & `react.py`**

Cập nhật `Constraints`, `normalise` (hỗ trợ cả `depart_after`), `set_constraints`, `_legs`, `search_flights`, `_found` và `SYSTEM_PROMPT` (bỏ câu giới hạn "chỉ đặt gói rẻ nhất", hướng dẫn truyền `refundable=True`, `cabin`, `airline`, `nonstop`, `depart_after` khi khách yêu cầu). Cập nhật kiểu `Card` (`constraints`) trong `frontend/src/app/assistant/agent-api.ts` và `ConstraintsCard` trong `assistant-chat.tsx` để hiển thị hạng ghế (khi khác `ECONOMY`), hãng, bay thẳng, hoàn/đổi.

- [ ] **Step 5: Run test suites to verify they pass**

Run: `cd agent && uv run pytest -q` và `cd frontend && npm run build`
Expected: Tất cả pytest PASS và Next.js build thành công.

- [ ] **Step 6: Commit**

```bash
git add agent/ frontend/src/app/assistant/
git commit -m "feat(agent): ho tro chon goi gia, hang ghe, loc hang bay va cap nhat le rang buoc"
```

---

### Task 2 (Bước 9): Tách hành lý theo chiều & Hoàn thiện UX Chat / Deep-link Handoff

**Files:**
- Modify: `agent/flight_agent/core.py`
- Modify: `agent/flight_agent/react.py`
- Modify: `frontend/src/app/assistant/agent-api.ts`
- Modify: `frontend/src/app/assistant/assistant-chat.tsx`
- Test: `agent/tests/test_harness.py`, `agent/tests/test_chat.py`

**Interfaces:**
- Consumes: `Constraints`, `Harness.propose_hold`, `Harness.is_done`, `Harness.handoff` từ Task 1.
- Produces:
  - `flights_url(c: Constraints | None) -> str` trong `agent/flight_agent/core.py`: trả `"/flights"` nếu `c is None`, ngược lại trả `"/flights?from=SGN&to=DAD&date=2026-10-30&adults=1&children=0&infants=0&cabin=ECONOMY"` (kèm `&returnDate=...` nếu `c.return_date` có giá trị, đúng thứ tự tham số mà `formFromParams` trong `frontend/src/lib/search-query.ts` đọc).
  - `Harness.propose_hold(..., baggage_kg: int | None = None, return_baggage_kg: int | None = None, voucher_code: str | None = None) -> dict`:
    - Với vé một chiều: `return_baggage_kg` phải là `None` (nếu truyền khác `None` trả `invalid_param`).
    - Với vé khứ hồi: nếu `return_baggage_kg is None`, mặc định lấy bằng `baggage_kg` (giữ tương thích ngược); nếu truyền số cụ thể (kể cả `0`), chiều đi dùng `baggage_kg or 0` và chiều về dùng `return_baggage_kg`. Kiểm từng chiều với bảng giá hành lý của hãng chiều đó.
  - `INTERNAL_LINK` trong `frontend/src/app/assistant/agent-api.ts`: `/(\/(?:chinh-sach|flights|bookings)\b(?:[/?#&=[A-Za-z0-9_-]+)*)/` để bắt cả link `/flights?from=...`.
  - `agentStream(path: string, body: unknown, onStep: (step: Step) => void, signal?: AbortSignal): Promise<Turn>` trong `agent-api.ts`.
  - Nút `"Cuộc trò chuyện mới"` và nút `"Dừng"` trên `frontend/src/app/assistant/assistant-chat.tsx`.

- [ ] **Step 1: Write the failing tests** trong `agent/tests/test_harness.py`

```python
def test_round_trip_supports_different_baggage_per_direction():
    h, _ = round_trip()
    r = h.execute("propose_hold", {**ROUND, "passengers": [PAX], "baggage_kg": 0, "return_baggage_kg": 20})
    assert (r["status"], r["total"]) == ("pending_approval", 2_900_000 + 350_000)
    assert h.proposal.body["baggage"] == [{"passengerIndex": 0, "direction": "RETURN", "baggageOptionId": 12}]
    assert [j["baggage"] for j in h.cards[-1]["journeys"]] == [
        None,
        {"weightKg": 20, "count": 1, "total": 350_000},
    ]

def test_handoff_links_to_prefilled_flights_url():
    h, _ = ready()
    h.execute("set_constraints", {**ROUTE, "adults": 2})
    h.stop("budget")
    q = h.handoff()["question"]
    assert "/flights?from=SGN&to=DAD&date=2026-10-30&adults=2&children=0&infants=0&cabin=ECONOMY" in q
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd agent && uv run pytest tests/test_harness.py -k "different_baggage_per_direction or prefilled_flights_url" -v`
Expected: FAIL.

- [ ] **Step 3: Implement `return_baggage_kg` và `flights_url` trong `agent/flight_agent/core.py` & `react.py`**

Cập nhật `propose_hold` để xử lý mức hành lý theo từng chiều `zip(DIRECTIONS, (kg_out, kg_ret))` và kiểm tra mức kg có bán trên đúng chiều đó; cập nhật `handoff()` dùng `flights_url(self.constraints)`.

- [ ] **Step 4: Cập nhật `agent-api.ts` và `assistant-chat.tsx`**

Cập nhật regex `INTERNAL_LINK`, truyền `signal` vào `fetch` trong `agentResponse`/`agentStream`, và thêm nút `"Cuộc trò chuyện mới"` (cạnh tiêu đề `"Trợ lý đặt vé"`, khi `entries.length > 0`) cùng nút `"Dừng"` (cạnh nút `"Gửi"` khi `busy` đang chạy).

- [ ] **Step 5: Run tests and frontend build**

Run: `cd agent && uv run pytest -q` và `cd frontend && npm run lint && npm run build`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add agent/ frontend/src/app/assistant/
git commit -m "feat(agent): tach hanh ly theo chieu, prefill link /flights va nut dieu khien chat"
```

---

### Task 3 (Bước 10): Runtime Output Guardrail, Tool-Output Compaction & Stall Detection

**Files:**
- Modify: `agent/flight_agent/core.py`
- Modify: `agent/flight_agent/react.py`
- Modify: `agent/flight_agent/chat.py`
- Test: `agent/tests/test_harness.py`, `agent/tests/test_chat.py`

**Interfaces:**
- Consumes: `DOCS` từ `flight_agent.policies`, `Harness` từ `flight_agent.core`.
- Produces:
  - `STALL_LIMIT = 4` trong `agent/flight_agent/core.py`; `Harness.failed_steps: int = 0` (đặt lại về `0` ở `begin_turn`). Trong `Harness._execute`: nếu `result["status"] == "ok"` thì `self.failed_steps = 0`; ngược lại tăng `self.failed_steps += 1`, và nếu `self.failed_steps >= STALL_LIMIT` thì gọi `self.stop("stall")`. Thêm câu hỏi `"stall"` vào `Harness.handoff()`: `"Trợ lý gặp lỗi liên tiếp qua 4 bước mà chưa xử lý được yêu cầu. Bạn muốn thử lại hay tự đặt trên {flights_url}?"`.
  - `compact_history(history: list) -> list` trong `agent/flight_agent/react.py`: duyệt `history` của các lượt trước, mọi `ToolMessage` có `content` dài quá 300 ký tự (như kết quả JSON của `search_flights`, `list_my_bookings`, `read_policy`) được thay bằng bản sao `ToolMessage(content='{"status": "ok", "compacted": true}', tool_call_id=msg.tool_call_id, name=msg.name)`.
  - `guard_reply(reply: str, harness: Harness) -> str` trong `agent/flight_agent/react.py`:
    1. Bỏ cú pháp Markdown: `**bold**` → `bold`, `` `code` `` → `code`, đầu dòng `^#{1,6}\s+` → rỗng.
    2. Mọi link `/chinh-sach/<slug>#<id>` trong `reply` mà `<slug>#<id>` không nằm trong tập mục đã đọc thành công của `harness.trace` lượt đó (hoặc `/chinh-sach/<slug>` mà `<slug>` không thuộc `DOCS`) được thay bằng `/chinh-sach/dieu-khoan`.
    3. Nếu `reply` chứa cụm trong `CLAIMS_HELD` (`"đã giữ chỗ chuyến"`, `"đã giữ chỗ cho"`, `"đã giữ chỗ thành công"`, `"đã giữ chỗ xong"`) mà `harness.cards` không có thẻ `type == "held"`, nối thêm câu: `" (Lưu ý: Hệ thống chưa giữ chỗ. Bạn cần bấm Giữ chỗ trên thẻ đề xuất để xác nhận.)"`.

- [ ] **Step 1: Write the failing tests** trong `agent/tests/test_harness.py` và `agent/tests/test_chat.py`

```python
# Trong agent/tests/test_harness.py:
def test_four_consecutive_failed_tool_calls_stop_as_stall():
    h, _ = ready()
    for bad_date in ("2026-01-01", "2026-01-02", "2026-01-03", "2026-01-04"):
        h.execute("set_constraints", {**ROUTE, "date": bad_date})
    assert h.stop_reason == "stall"
    assert "4 bước" in h.handoff()["question"]

# Trong agent/tests/test_chat.py:
def test_runtime_guardrail_strips_markdown_fixes_unread_citations_and_blocks_false_hold_claims():
    fake = FakeBackend()
    chat = Chats().create("s1")
    out = turn(chat, scripted(
        call("set_constraints", **ROUTE),
        call("search_flights"),
        AIMessage(content="Mình **đã giữ chỗ thành công** chuyến `VN122` theo /chinh-sach/hoan-doi-hanh-ly#muc-bia!")
    ), "Đặt vé", fake.backend(), TODAY)
    assert "**" not in out["reply"] and "`" not in out["reply"]
    assert "/chinh-sach/hoan-doi-hanh-ly#muc-bia" not in out["reply"]
    assert "/chinh-sach/dieu-khoan" in out["reply"]
    assert "Hệ thống chưa giữ chỗ" in out["reply"]

def test_old_tool_messages_are_compacted_on_next_turn():
    from flight_agent.react import compact_history
    from langchain_core.messages import ToolMessage
    big = ToolMessage(content="x" * 1000, tool_call_id="call-1", name="search_flights")
    compacted = compact_history([big])
    assert compacted[0].tool_call_id == "call-1" and len(compacted[0].content) < 100
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd agent && uv run pytest tests/test_harness.py tests/test_chat.py -k "stall or guardrail or compacted" -v`
Expected: FAIL.

- [ ] **Step 3: Implement `STALL_LIMIT`, `guard_reply`, và `compact_history` trong `core.py`, `react.py`, `chat.py`**

Gọi `compact_history(history)` trước khi truyền vào `agent.invoke` trong `run_turn()`, và áp dụng `guard_reply(reply, h)` trước khi trả kết quả trong `turn()`.

- [ ] **Step 4: Run test suite**

Run: `cd agent && uv run pytest -q`
Expected: Tất cả PASS.

- [ ] **Step 5: Commit**

```bash
git add agent/flight_agent/core.py agent/flight_agent/react.py agent/flight_agent/chat.py agent/tests/
git commit -m "feat(agent): them runtime guardrail, nen tool output cu va stall detection"
```

---

### Task 4 (Bước 11): Timeout tổng, Token Budget, Rate Limit & Structured Trace Log

**Files:**
- Modify: `agent/flight_agent/core.py`
- Modify: `agent/flight_agent/react.py`
- Modify: `agent/flight_agent/chat.py`
- Modify: `agent/flight_agent/app.py`
- Test: `agent/tests/test_harness.py`, `agent/tests/test_app.py`

**Interfaces:**
- Consumes: `Harness.before_model`, `turn`, `approve`, `create_app`.
- Produces:
  - `TURN_TIMEOUT_SECONDS = 90.0` và `TOKEN_BUDGET = 40_000` trong `agent/flight_agent/core.py`.
  - `Harness.started_at: float = 0.0` và `Harness.tokens_used: int = 0` (reset ở `begin_turn`). Trong `before_model()`, nếu `time.monotonic() - self.started_at > TURN_TIMEOUT_SECONDS` thì `self.stop("timeout"); raise StopRun`; nếu `self.tokens_used >= TOKEN_BUDGET` thì `self.stop("budget"); raise StopRun`. Thêm câu hỏi `"timeout"` vào `handoff()`.
  - Trong `run_turn()` (`react.py`): sau mỗi `handler(request)` trong middleware `budget`, cộng `getattr(response, "usage_metadata", None)` (`total_tokens`) vào `harness.tokens_used`.
  - `log_turn(chat_id: str, action: str, harness: Harness, latency_ms: int) -> dict` trong `agent/flight_agent/chat.py`: phát một dòng JSON qua `logging.getLogger("flight_agent.trace").info(...)` gồm `{"chat_id", "action", "steps": [{"tool": t["tool"], "status": t["status"]} for t in harness.trace], "stop_reason": harness.stop_reason, "model_calls": harness.model_calls, "tokens": harness.tokens_used, "latency_ms": latency_ms}` (tuyệt đối không ghi `args` hay tin nhắn để không lọt PII).
  - Rate limit trong `create_app(..., rate_limit: int = 15, rate_window: float = 60.0)` (`agent/flight_agent/app.py`): đếm số lần gọi `POST /agent/chats/{chat_id}/messages` theo `JSESSIONID` trong cửa sổ trượt `rate_window` giây; vượt `rate_limit` trả `HTTPException(429, "Bạn gửi tin nhắn quá nhanh. Vui lòng chờ ít giây rồi thử lại.")`.

- [ ] **Step 1: Write the failing tests** trong `agent/tests/test_harness.py` và `agent/tests/test_app.py`

```python
# Trong agent/tests/test_harness.py:
def test_turn_stops_on_wall_clock_timeout_or_token_budget():
    h, _ = ready()
    h.started_at -= 100.0
    with pytest.raises(StopRun):
        h.before_model()
    assert h.stop_reason == "timeout"

    h2, _ = ready()
    h2.tokens_used = 40_000
    with pytest.raises(StopRun):
        h2.before_model()
    assert h2.stop_reason == "budget"

# Trong agent/tests/test_app.py:
def test_rate_limit_blocks_excessive_messages_per_session():
    app = create_app(model=scripted(*hold_flow() * 3), backend_url="http://be",
                     transport=FakeBackend().transport, today=lambda: "2026-10-26", rate_limit=2)
    c = TestClient(app)
    chat = c.post("/agent/chats", headers={"Cookie": COOKIE}).json()["chatId"]
    assert c.post(f"/agent/chats/{chat}/messages", json={"text": "1"}, headers={"Cookie": COOKIE}).status_code == 200
    assert c.post(f"/agent/chats/{chat}/messages", json={"text": "2"}, headers={"Cookie": COOKIE}).status_code == 200
    r = c.post(f"/agent/chats/{chat}/messages", json={"text": "3"}, headers={"Cookie": COOKIE})
    assert r.status_code == 429 and "quá nhanh" in r.json()["detail"]
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd agent && uv run pytest tests/test_harness.py tests/test_app.py -k "wall_clock_timeout or rate_limit" -v`
Expected: FAIL.

- [ ] **Step 3: Implement timeout, token budget, structured trace log, và rate limit**

Cập nhật `core.py`, `react.py`, `chat.py`, `app.py`.

- [ ] **Step 4: Run test suite**

Run: `cd agent && uv run pytest -q`
Expected: Tất cả PASS.

- [ ] **Step 5: Commit**

```bash
git add agent/flight_agent/ agent/tests/
git commit -m "feat(agent): them timeout luot, token budget, rate limit va structured trace log"
```

---

### Task 5 (Bước 12): Tra cứu Đổi chuyến (`reschedule_options`) & Huỷ giữ chỗ qua thẻ duyệt (`propose_cancel`)

**Files:**
- Modify: `agent/flight_agent/backend.py`
- Modify: `agent/flight_agent/core.py`
- Modify: `agent/flight_agent/chat.py`
- Modify: `agent/flight_agent/react.py`
- Modify: `agent/tests/fakes.py`
- Modify: `frontend/src/app/assistant/agent-api.ts`
- Modify: `frontend/src/app/assistant/assistant-chat.tsx`
- Test: `agent/tests/test_backend.py`, `agent/tests/test_harness.py`, `agent/tests/test_chat.py`

**Interfaces:**
- Consumes: `Backend`, `_booking_code`, `_when`, `_money`, `Proposal`, `approve`.
- Produces:
  - `Backend.post(self, path: str, body: dict | None = None) -> dict` trong `agent/flight_agent/backend.py`: chỉ truyền `json=body` khi `body is not None`; trong `_send`, `if r.is_success: return r.json() if r.content else {}`.
  - `Harness.reschedule_options(self, code: str, direction: str, date: str) -> dict`:
    - Kiểm `_booking_code(code)`, `direction in ("OUTBOUND", "RETURN")`, `_day(date) >= today`.
    - Gọi `GET /api/bookings/{code}/journeys/{direction}/reschedule-options?date={date}`.
    - Trả tối đa 5 phương án: `{"status": "ok", "options": [{"flights": "VN124", "depart": "14:00 ngày 31/10/2026", "arrive": "15:20 ngày 31/10/2026", "change_fee": "300.000 đ", "amount_due": "450.000 đ"}], "page": f"/bookings/{code}/reschedule/{direction.lower()}"}`.
  - Mở rộng `Proposal`: thêm trường `action: str = "hold"` (`"hold"` hoặc `"cancel"`).
  - `Harness.propose_cancel(self, code: str) -> dict`:
    - Kiểm `_booking_code(code)`, gọi `GET /api/bookings/{code}`.
    - Nếu `b["status"] != "PENDING_PAYMENT"` thì trả `{"status": "denied", "hint": f"Chỉ huỷ được đặt chỗ đang chờ thanh toán (trạng thái hiện tại: {STATES.get(b['status'], b['status'])})."}`.
    - Nếu đang `PENDING_PAYMENT`, lưu `Proposal(id=pid, body={"code": code}, total=b["totalAmount"], card={"type": "cancel_proposal", "proposalId": pid, "code": code, "totalAmount": b["totalAmount"], "routes": [...]}, action="cancel")`, thêm `card` vào `self.cards`, trả `{"status": "pending_approval", "code": code}`.
  - Trong `approve()` (`agent/flight_agent/chat.py`): nếu `proposal.action == "cancel"`, gọi `backend.post(f"/api/bookings/{code}/cancel")`, đọc lại `detail = backend.get(f"/api/bookings/{code}")`, kiểm tra `detail.get("status") == "CANCELLED"` (nếu lệch thì `stop("hold_mismatch")` + `_handoff`), nếu đúng trả thẻ `{"type": "cancelled", "code": code}` và câu trả lời `"Đã huỷ giữ chỗ {code}."`.

- [ ] **Step 1: Write the failing tests** trong `agent/tests/test_backend.py`, `agent/tests/test_harness.py`, `agent/tests/test_chat.py`

```python
# Trong agent/tests/test_backend.py:
def test_post_with_no_body_and_204_response_returns_empty_dict():
    def no_content(request):
        return httpx.Response(204)
    b = Backend("http://be", "JSESSIONID=s1", "t", httpx.MockTransport(no_content))
    assert b.post("/api/bookings/K7M2QX/cancel") == {}

# Trong agent/tests/test_harness.py:
def test_reschedule_options_formats_flights_and_amount_due():
    h, _ = ready(FakeBackend(**LOOKUP, reschedules={
        ("ABC234", "OUTBOUND", "2026-10-31"): [
            {"itinerary": FLIGHTS[1], "quote": {"oldFareTotal": 1_850_000, "newFareTotal": 2_000_000,
                                                "changeFeeTotal": 300_000, "amountDue": 450_000}}
        ]
    }))
    r = h.execute("reschedule_options", {"code": "ABC234", "direction": "OUTBOUND", "date": "2026-10-31"})
    assert r["status"] == "ok" and r["options"][0]["amount_due"] == "450.000 đ"
    assert r["page"] == "/bookings/ABC234/reschedule/outbound"

def test_propose_cancel_only_allows_pending_payment_bookings():
    h, _ = ready(FakeBackend(**LOOKUP))
    assert h.execute("propose_cancel", {"code": "ABC234"})["status"] == "denied"
    r = h.execute("propose_cancel", {"code": "K7M2QX"})
    assert r["status"] == "pending_approval" and h.proposal.action == "cancel"
    assert h.cards[-1]["type"] == "cancel_proposal"

# Trong agent/tests/test_chat.py:
def test_approving_cancel_proposal_cancels_and_verifies_status():
    fake = FakeBackend(**LOOKUP)
    chat = Chats().create("s1")
    out = turn(chat, scripted(call("propose_cancel", code="K7M2QX"),
                              AIMessage(content="Bấm Huỷ giữ chỗ để xác nhận nhé.")),
               "Huỷ đơn K7M2QX", fake.backend(), TODAY)
    pid = out["cards"][-1]["proposalId"]
    res = approve(chat, scripted(), pid, fake.backend(), TODAY)
    assert res["cards"] == [{"type": "cancelled", "code": "K7M2QX"}]
    assert fake.bookings["K7M2QX"]["status"] == "CANCELLED"
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd agent && uv run pytest tests/test_backend.py tests/test_harness.py tests/test_chat.py -k "204_response or reschedule_options or propose_cancel or approving_cancel" -v`
Expected: FAIL.

- [ ] **Step 3: Implement `204` handling, `reschedule_options`, `propose_cancel`, và nhánh `approve` cancel**

Cập nhật `backend.py`, `fakes.py`, `core.py` (thêm 2 tool vào `Harness.tools`), `chat.py`, `react.py`, và thêm hiển thị thẻ `cancel_proposal` (nút `"Huỷ giữ chỗ"` và `"Đóng"`) + thẻ `cancelled` trong `frontend/src/app/assistant/agent-api.ts` và `assistant-chat.tsx`.

- [ ] **Step 4: Run test suites and frontend build**

Run: `cd agent && uv run pytest -q` và `cd frontend && npm run lint && npm run build`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add agent/ frontend/src/app/assistant/
git commit -m "feat(agent): them reschedule_options va huy giu cho PENDING_PAYMENT qua the duyet"
```

---

### Task 6 (Bước 13): Lưu bền vững `Chats` bằng SQLite + TTL cho Chat & Proposal

**Files:**
- Modify: `agent/flight_agent/core.py`
- Modify: `agent/flight_agent/chat.py`
- Modify: `agent/flight_agent/app.py`
- Modify: `agent/.gitignore`
- Test: `agent/tests/test_chat.py`, `agent/tests/test_app.py`

**Interfaces:**
- Consumes: `Chat`, `Chats`, `Harness`, `Proposal`, `Constraints`, `sqlite3`, `messages_to_dict` / `messages_from_dict` từ `langchain_core.messages`.
- Produces:
  - `PROPOSAL_TTL_SECONDS = 900.0` (15 phút) trong `agent/flight_agent/core.py`; thêm `created_at: float = field(default_factory=time.time)` vào `Proposal` và phương thức `Proposal.is_expired(now: float | None = None) -> bool`.
  - `CHAT_TTL_SECONDS = 86_400.0` (24 giờ) và `MAX_CHATS = 500` trong `agent/flight_agent/chat.py`.
  - `Chats(db_path: str | Path = ":memory:")`:
    - Tạo bảng `chats(id TEXT PRIMARY KEY, session TEXT NOT NULL, updated_at REAL NOT NULL, payload TEXT NOT NULL)` trong SQLite.
    - `Chats.save(chat: Chat) -> None`: lưu trạng thái `Chat` (`shown`, `history`, `harness.constraints`, `harness.found`, `harness.proposal`, `harness.known_names`) xuống SQLite; được gọi tự động cuối `create()`, `turn()`, `approve()`, và `dismiss`.
    - Khi `get(chat_id, session)`: nếu chưa có trong `_chats` RAM nhưng có trong SQLite và chưa quá `CHAT_TTL_SECONDS`, khôi phục lại `Chat` vào RAM; nếu `proposal` đã `is_expired()` thì đặt `chat.harness.proposal = None`.

- [ ] **Step 1: Write the failing tests** trong `agent/tests/test_chat.py` và `agent/tests/test_app.py`

```python
# Trong agent/tests/test_chat.py:
def test_chat_and_live_proposal_survive_service_restart_via_sqlite(tmp_path):
    db = tmp_path / "chats.sqlite3"
    fake = FakeBackend()
    store1 = Chats(db)
    chat1 = store1.create("s1")
    out = turn(chat1, scripted(*hold_flow()), "Đặt vé", fake.backend(), TODAY, store=store1)
    pid = out["cards"][-1]["proposalId"]

    # Giả lập khởi động lại service: tạo Chats mới đọc từ cùng file SQLite
    store2 = Chats(db)
    chat2 = store2.get(chat1.id, "s1")
    assert chat2 is not None and len(chat2.shown) == 2
    assert chat2.harness.proposal is not None and chat2.harness.proposal.id == pid
    res = approve(chat2, scripted(), pid, fake.backend(), TODAY, store=store2)
    assert res["cards"][0]["type"] == "held"

def test_expired_proposal_cannot_be_approved():
    fake = FakeBackend()
    chat, model, out = proposed(fake)
    chat.harness.proposal.created_at -= 1000.0
    with pytest.raises(LookupError):
        approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd agent && uv run pytest tests/test_chat.py -k "survive_service_restart or expired_proposal" -v`
Expected: FAIL.

- [ ] **Step 3: Implement SQLite persistence và TTL trong `core.py`, `chat.py`, `app.py`**

Thêm `*.sqlite3` vào `agent/.gitignore`. Trong `create_app`, truyền `db_path=os.environ.get("AGENT_DB_PATH", ":memory:")`.

- [ ] **Step 4: Run full test suites**

Run: `cd agent && uv run pytest -q` và `cd frontend && npm run lint && npm run build`
Expected: Tất cả PASS.

- [ ] **Step 5: Commit**

```bash
git add agent/
git commit -m "feat(agent): luu chat xuong SQLite va them TTL cho chat va proposal"
```

---

## 3. Số liệu Eval (Cập nhật sau mỗi đợt)

| Ngày | Sau bước | Model | Số case | Tỉ lệ đạt | Gọi model TB | Token TB | Ghi chú |
|---|---|---|---|---|---|---|---|
| 2026-10-30 | Mốc GĐ 1 (5a/7) | gemini-3.5-flash-lite | 28 (book) + 11 (cs) | 99% / 91% | 3.5 / 2.1 | ~7.000–13.300 | Mốc cơ sở trước khi nâng cấp v2 |
| — | Bước 8–9 (Đợt 1) | — | — | — | — | — | Đo lại sau khi thêm gói giá, bộ lọc và hành lý theo chiều |
| — | Bước 10–11 (Đợt 2) | — | — | — | — | — | Kỳ vọng Token TB ở các case nhiều lượt giảm nhờ `compact_history` |
| — | Bước 12–13 (Đợt 3) | — | — | — | — | — | Đo thêm case `reschedule_options` và `propose_cancel` |
