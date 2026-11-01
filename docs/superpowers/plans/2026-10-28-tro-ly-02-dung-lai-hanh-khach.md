# Trợ lý Bước 2: Dùng lại hành khách đã đặt Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khách gõ "đặt cho mình như lần trước" thì agent lấy hành khách từ booking cũ, hỏi khách xác nhận, rồi đưa thẻ duyệt với đúng hành khách đó.

**Architecture:** Thêm tool chỉ đọc `Harness.list_recent_passengers()` trong `agent/flight_agent/core.py`: một lời gọi `GET /api/bookings?size=3` rồi `GET /api/bookings/{code}` cho từng booking trong trang đó, gộp các hành khách người lớn trùng nhau. Không có kho memory riêng: dữ liệu luôn đọc từ backend (lộ trình Bước 2, "Vì sao không dùng memory riêng").

**Tech Stack:** Python, LangChain, pytest với `httpx.MockTransport` (agent).

**Spec:** [docs/superpowers/specs/2026-10-26-tro-ly-dat-ve-agent-design.md](../specs/2026-10-26-tro-ly-dat-ve-agent-design.md) · Lộ trình: [Bước 2](2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md#bước-2--dùng-lại-hành-khách-đã-đặt)

## Global Constraints

- Làm trên nhánh `feature/flight-agent`. Không sửa `backend/`, không sửa `frontend/` (trace hiện tên tool thô, không cần nhãn mới).
- Tool chỉ gọi GET. Tối đa `RECENT_BOOKINGS = 3` booking, tức tối đa 4 lời gọi backend mỗi lần gọi tool.
- Mỗi hành khách trả về đúng ba trường `full_name` (`"<lastName> <firstName>"`), `gender`, `date_of_birth`. Không trả số giấy tờ, quốc tịch, vé hay liên hệ.
- Chỉ lấy hành khách `ADULT` (MVP chỉ đặt cho một người lớn). Gộp trùng theo (họ, tên, giới tính, ngày sinh), giữ thứ tự booking mới nhất trước (backend trả `ORDER BY created_at DESC`).
- Lấy mọi trạng thái booking (cả `EXPIRED`, `CANCELLED`): thông tin hành khách vẫn đúng.
- `check_permission` không đòi ràng buộc cho tool này (chỉ `search_flights`, `propose_hold` cần). `propose_hold` giữ nguyên chữ ký; thẻ duyệt vẫn là chốt chặn cuối.

## Review Focus

1. **Khách chưa có booking nào:** tool trả danh sách rỗng, model phải hỏi thông tin chứ không bịa.
2. **Tên đã chuẩn hoá đi qua `split_name` lần nữa** (`NGUYEN HUNG CUONG` → `("NGUYEN", "HUNG CUONG")`), kể cả tên một chữ đệm hoặc nhiều chữ.
3. **Lỗi backend giữa chừng** (một chi tiết booking trả 404/500): đi theo `backend_error` chung của `execute`, không làm crash lượt.

## File structure

```
agent/flight_agent/core.py     RECENT_BOOKINGS, Harness.list_recent_passengers, Harness.tools (Task 1)
agent/tests/fakes.py           FakeBackend(past=...) phục vụ GET /api/bookings và chi tiết booking cũ (Task 1)
agent/tests/test_harness.py    Task 1
agent/tests/test_core.py       split_name trên tên đã chuẩn hoá (Task 1)
agent/flight_agent/react.py    quy tắc prompt (Task 2)
agent/README.md                danh sách tool (Task 2)
```

---

### Task 1: Tool `list_recent_passengers`

**Files:** Modify `agent/flight_agent/core.py`, `agent/tests/fakes.py`, `agent/tests/test_harness.py`, `agent/tests/test_core.py`.

- [ ] **Step 1: Backend giả.** `FakeBackend.__init__` nhận thêm `past: list[tuple[str, list[dict]]] = ()` (mã booking, danh sách hành khách dạng `PassengerView`, mới nhất trước). `GET /api/bookings` trả `{"content": [{"code": c, ...} for c in past[:size]], "page": {...}}` theo tham số `size` (mặc định 20 như Spring). `GET /api/bookings/{c}` với `c` trong `past` trả `{"code": c, "status": "ISSUED", "passengers": [...]}`. Thêm hàm `passenger(last, first, gender="MALE", dob="2004-03-12", type="ADULT")` trả đủ trường `PassengerView`, kể cả `passportNumber`.

- [ ] **Step 2: Viết test.** Thêm vào `agent/tests/test_harness.py`:

```python
CUONG = passenger("NGUYEN", "HUNG CUONG")
PAST = [("AAA111", [CUONG, passenger("TRAN", "THI MAI", "FEMALE", "2005-01-02")]),
        ("BBB222", [CUONG, passenger("NGUYEN", "BE NA", type="CHILD", dob="2020-05-05")]),
        ("CCC333", [passenger("LE", "VAN AN", dob="1990-09-09")]),
        ("DDD444", [passenger("PHAM", "MINH", dob="1980-01-01")])]


def test_recent_passengers_are_unique_adults_without_documents():
    h, _ = ready(FakeBackend(past=PAST))
    r = h.execute("list_recent_passengers", {})      # không cần ràng buộc trước
    assert r == {"status": "ok", "passengers": [
        {"full_name": "NGUYEN HUNG CUONG", "gender": "MALE", "date_of_birth": "2004-03-12"},
        {"full_name": "TRAN THI MAI", "gender": "FEMALE", "date_of_birth": "2005-01-02"},
        {"full_name": "LE VAN AN", "gender": "MALE", "date_of_birth": "1990-09-09"}]}


def test_recent_passengers_reads_at_most_three_bookings_with_get_only():
    h, fake = ready(FakeBackend(past=PAST))
    h.execute("list_recent_passengers", {})
    assert all(q.method == "GET" for q in fake.requests) and len(fake.requests) == 4
    assert fake.requests[0].url.params["size"] == "3"
    assert "/api/bookings/DDD444" not in [q.url.path for q in fake.requests]


def test_no_past_booking_gives_an_empty_list():
    h, _ = ready()
    assert h.execute("list_recent_passengers", {}) == {"status": "ok", "passengers": []}


def test_reused_passenger_goes_into_the_hold_unchanged():
    h, _ = searched(FakeBackend(past=PAST))
    p = h.execute("list_recent_passengers", {})["passengers"][0]
    h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, **p))
    sent = h.proposal.body["passengers"][0]
    assert (sent["lastName"], sent["firstName"], sent["gender"], sent["dateOfBirth"]) == \
        ("NGUYEN", "HUNG CUONG", "MALE", "2004-03-12")
```

Thêm vào `test_split_name_follows_br12` trong `agent/tests/test_core.py`:

```python
    assert split_name("NGUYEN HUNG CUONG") == ("NGUYEN", "HUNG CUONG")     # tên lấy lại từ booking cũ
```

- [ ] **Step 3: Chạy test.** `cd agent && uv run pytest tests/test_harness.py tests/test_core.py`
      Expected: 4 test mới FAIL (`denied`, "Không có tool list_recent_passengers"), test `split_name` PASS (đã đúng, giữ làm test hồi quy), các test cũ PASS.

- [ ] **Step 4: Cài đặt.** Trong `core.py`: hằng `RECENT_BOOKINGS = 3` cạnh `BUDGET`, comment "số booking gần nhất mà list_recent_passengers đọc". Thêm `self.list_recent_passengers` vào `Harness.tools` và method:

```python
    def list_recent_passengers(self) -> dict:
        """Hành khách người lớn trong vài booking gần nhất của khách, không trùng nhau, mới nhất trước. Dùng khi
        khách muốn đặt cho mình hoặc cho người đã đặt trước đây; đọc tên ra và hỏi khách xác nhận trước khi
        propose_hold. Danh sách rỗng thì hỏi khách thông tin hành khách."""
        page = self.backend.get("/api/bookings", {"size": RECENT_BOOKINGS})
        passengers = []
        for summary in page["content"]:
            for p in self.backend.get(f"/api/bookings/{summary['code']}")["passengers"]:
                if p["type"] != "ADULT":
                    continue
                # Không trả số giấy tờ: model chỉ cần đủ tham số cho propose_hold.
                item = {"full_name": f"{p['lastName']} {p['firstName']}", "gender": p["gender"],
                        "date_of_birth": p["dateOfBirth"]}
                if item not in passengers:
                    passengers.append(item)
        return {"status": "ok", "passengers": passengers}
```

- [ ] **Step 5: Chạy lại.** `cd agent && uv run pytest`
      Expected: tất cả PASS.

- [ ] **Step 6: Commit.** `feat(agent): tool list_recent_passengers đọc hành khách từ booking cũ`

### Task 2: Prompt và README

**Files:** Modify `agent/flight_agent/react.py`, `agent/README.md`.

- [ ] **Step 1: Prompt.** Trong `SYSTEM_PROMPT`, thay quy tắc "Cần họ tên đầy đủ, giới tính và ngày sinh ... không được bịa." bằng:

```
- Cần họ tên đầy đủ, giới tính và ngày sinh của hành khách. Nếu khách muốn đặt cho mình, như lần trước, hoặc cho \
người đã từng đặt, gọi list_recent_passengers, đọc tên và ngày sinh ra rồi hỏi khách xác nhận đúng người nào trước \
khi gọi propose_hold. Không có trong danh sách thì hỏi khách, không được bịa.
```

- [ ] **Step 2: README.** Thêm `list_recent_passengers` vào chỗ `agent/README.md` liệt kê tool, ghi: chỉ đọc, lấy từ tối đa 3 booking gần nhất, không trả số giấy tờ.

- [ ] **Step 3: Chạy test.** `cd agent && uv run pytest`
      Expected: tất cả PASS.

- [ ] **Step 4: Commit.** `feat(agent): prompt gợi ý dùng lại hành khách đã đặt`

### Task 3: Kiểm bằng tay và cập nhật lộ trình

- [ ] **Step 1: Bằng tay** (backend profile `dev`, service agent với model thật, `npm run dev`): đăng nhập `customer@demo.local` (đã có booking, hoặc tự đặt một vé trước), gõ "đặt cho mình như lần trước, Sài Gòn đi Đà Nẵng ngày <ngày tới>". Agent gọi `list_recent_passengers`, hỏi xác nhận; trả lời "đúng rồi" thì ra thẻ duyệt với đúng hành khách đó.
      Expected: đúng như trên.

- [ ] **Step 2: Lộ trình.** Trong `2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md` §1: đánh dấu cột "Xong" của Bước 2 và gắn link plan này vào cột "Bước".

- [ ] **Step 3: Commit.** `docs(agent): xong Bước 2 của lộ trình trợ lý`
