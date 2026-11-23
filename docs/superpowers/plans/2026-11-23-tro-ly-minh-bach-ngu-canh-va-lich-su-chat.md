# Minh bạch Ngữ cảnh/Nguồn dữ liệu & Lịch sử Trò chuyện cho Trợ lý Đặt vé Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Giúp người dùng nhìn thấy rõ trợ lý AI đang nắm ngữ cảnh gì và lấy thông tin hành khách cũ từ đơn đặt nào, đồng thời xem lại, mở lại và xoá các cuộc trò chuyện gần đây của chính tài khoản mình ngay cả sau khi đổi phiên đăng nhập.

**Architecture:** Giữ nguyên kiến trúc **Single ReAct Agent + Code-controlled Harness** (`agent/` + `/assistant`) và **không sửa backend Java**. Tách lịch sử trò chuyện (transcript xem lại theo `user_id` trong SQLite, không nhồi vào prompt của cuộc trò chuyện mới) khỏi bộ nhớ truy xuất trực tiếp từ DB (`list_recent_passengers` phát thẻ `memory_used` kèm mã booking nguồn). Trên frontend, tách các khối hiển thị thành `chat-cards.tsx` (thẻ nguồn bộ nhớ, thanh ngữ cảnh phiên, bước xử lý tiếng Việt thân thiện) và `chat-history.tsx` (danh sách lịch sử trò chuyện bên trái có thể mở lại/xoá và xử lý phiên hết hạn 401).

**Tech Stack:** Python 3.12+, FastAPI, `sqlite3` (stdlib), `pytest`, Next.js 16 (Cache Components), TypeScript, Tailwind CSS v4 (`DESIGN.md` tokens), Playwright.

**Spec:** Thiết kế nền [docs/superpowers/specs/2026-10-26-tro-ly-dat-ve-agent-design.md](../specs/2026-10-26-tro-ly-dat-ve-agent-design.md) · Lộ trình trước đó [docs/superpowers/plans/2026-11-17-tro-ly-dat-ve-agent-roadmap-v2.md](2026-11-17-tro-ly-dat-ve-agent-roadmap-v2.md) · Cơ sở lý thuyết [docs/ref/agentic-ai/Buoi 7 - Memory, context engineering, prompt caching.md](../../ref/agentic-ai/Buoi%207%20-%20Memory,%20context%20engineering,%20prompt%20caching.md) · Hệ thống giao diện [DESIGN.md](../../../DESIGN.md).

## Global Constraints

- **Không sửa `backend/`**: dùng nguyên `GET /api/me` và `GET /api/bookings` hiện có của Spring Boot.
- **Giữ nguyên 5 bất biến an toàn của agent** (spec §2): không tạo/huỷ booking khi chưa bấm duyệt; trạng thái hoàn thành do `is_done` xác nhận; chuyển tiếp nguyên header `X-XSRF-TOKEN` (không đọc từ cookie); service `agent` tắt không làm hỏng các trang khác; trần ngân sách/lặp/bế tắc/timeout không đổi.
- **Không nhồi transcript cuộc trò chuyện cũ vào LLM**: lịch sử chat chỉ phục vụ hiển thị/mở lại đúng phiên đó; cuộc trò chuyện mới luôn bắt đầu với `history` rỗng, truy xuất dữ liệu thật qua tool.
- **Không thêm dependency mới**: dùng `sqlite3` (stdlib) ở Python và `DESIGN.md` role tokens (`paper`, `surface`, `ink`, `muted`, `accent`, `line`, `tint`, `card`, `card-hi`, `pillPrimary`, `pillLine`) ở frontend — không dùng mã hex cứng, không bo pill (`9999px`).
- **Next.js 16 Cache Components**: tuyệt đối không gọi `new Date()`, `Date.now()`, `Math.random()` trong lúc render ở mọi component (kể cả Client Component). Mọi mốc giờ hiển thị định dạng từ chuỗi ISO `+07:00` bằng `clock()`/`dmy()` trong `frontend/src/lib/booking.ts`.
- **Ngôn ngữ**: toàn bộ nhãn UI, thông báo lỗi, docstring và comment đều bằng tiếng Việt.

## Review Focus

1. **Migration SQLite từ schema cũ (`session`) sang schema mới (`owner`, `title`)**: file `chats.sqlite3` đã tồn tại trong volume Docker `agentdata` có cột `session TEXT NOT NULL`; khởi tạo `Chats(db_path)` phải nhận diện schema cũ và tạo lại bảng sạch để không văng `NOT NULL constraint failed` và không gán nhầm chat ẩn danh cho tài khoản khác.
2. **Cách ly tài khoản theo `user_id` và xử lý phiên hết hạn (`401`)**: người dùng A đăng nhập lại (`JSESSIONID` mới, cùng `user_id`) vẫn đọc/mở/xoá được chat của mình; người dùng B (`user_id` khác) luôn nhận `404`; cookie `JSESSIONID` cũ hết hạn khiến `/api/me` trả `401` phải trả `401` từ `/agent/chats` và frontend tự chuyển hướng về `/login?next=/assistant`.
3. **Xoá cuộc trò chuyện đang mở hoặc đang chạy lượt (`DELETE /agent/chats/{id}`)**: xoá chat phải đánh dấu `chat.deleted = True` để `Chats.save()` trong luồng nền không ghi hồi sinh bản ghi vừa xoá; trên UI nếu xoá đúng chat đang mở thì dừng stream, xoá `sessionStorage` và đưa khung chat về trạng thái trống.
4. **Chat rỗng & định dạng tiêu đề**: chat mới tạo nhưng chưa có tin nhắn nào của khách (`title == ""`) không được liệt kê trong `GET /agent/chats`; tiêu đề rút gọn một dòng, gộp khoảng trắng/xuống dòng và cắt tối đa 60 ký tự kèm `…`.
5. **Minh bạch không lọt PII thừa & không làm vỡ khi `agent` tắt**: thẻ `memory_used` chỉ hiện họ tên, giới tính, ngày sinh và mã booking nguồn (tuyệt đối không hiện số hộ chiếu/CCCD); nếu service `agent` tắt (5xx/network), panel lịch sử không làm sập `/assistant` và các trang khác vẫn hoạt động bình thường.

## File Structure

```text
agent/flight_agent/chat.py          Đổi quyền sở hữu Chat sang `owner` (user_id), thêm `title`, `deleted`,
                                    TTL 7 ngày, `list_for()`, `delete()`, migration SQLite an toàn (Task 1)
agent/flight_agent/app.py           Xác thực `owner` qua GET /api/me, thêm GET /agent/chats và
                                    DELETE /agent/chats/{chat_id}, rate-limit theo owner (Task 2)
agent/flight_agent/core.py          `list_recent_passengers()` đính kèm thẻ `memory_used` với mã booking nguồn (Task 3)
agent/tests/fakes.py                Hỗ trợ `me_by_cookie` (nhiều user khác nhau) trong FakeBackend (Task 2)
agent/tests/test_chat.py            Test owner, title, list_for, delete, deleted-flag, migration SQLite (Task 1)
agent/tests/test_app.py             Test xác thực /api/me, đổi JSESSIONID cùng user, cách ly khác user,
                                    GET /agent/chats, DELETE /agent/chats/{id} (Task 2)
agent/tests/test_harness.py         Test thẻ `memory_used` từ `list_recent_passengers` (không có số giấy tờ) (Task 3)
frontend/src/app/assistant/
  agent-api.ts                      Thêm kiểu `ChatSummary`, thẻ `memory_used`, `agentDelete()`, `describeStep()` (Task 4)
  chat-cards.tsx                    Tách `Steps` (mô tả tiếng Việt + `<details>` kỹ thuật), `MemoryUsedCard`,
                                    `ActiveContextBar`, `ConstraintsCard`, `ProposalCard`, `CardView` (Task 4)
  chat-history.tsx                  Sidebar/panel "Lịch sử trò chuyện" (danh sách, chọn mở lại, xoá, tạo mới) (Task 4)
  assistant-chat.tsx                Ghép bố cục 2 cột (lịch sử + khung chat), nạp/chuyển/xoá chat, xử lý 401 (Task 4)
frontend/e2e/assistant.spec.ts      Cập nhật test bước xử lý & bổ sung E2E lịch sử chat, thẻ nguồn, 401 redirect (Task 4)
```

---

### Task 1: Lưu bền vững `Chats` theo `owner`, `title`, danh sách & xoá (SQLite migration)

**Files:**
- Modify: `agent/flight_agent/chat.py:22-125`
- Test: `agent/tests/test_chat.py`

**Interfaces:**
- Consumes: `Harness`, `Constraints`, `Proposal`, `messages_to_dict`, `messages_from_dict` trong `agent/flight_agent/chat.py`.
- Produces (trong `agent/flight_agent/chat.py`):
  - `CHAT_TTL_SECONDS = 7 * 86_400.0` (7 ngày), `LIST_CHATS_LIMIT = 20`, `TITLE_MAX = 60`.
  - `make_title(text: str) -> str`: gộp mọi khoảng trắng/xuống dòng thành 1 dấu cách, cắt tối đa `TITLE_MAX` ký tự (kèm `"…"` nếu vượt).
  - `Chat`: đổi trường `session: str` thành `owner: str`, thêm `title: str = ""`, `deleted: bool = False`.
  - `Chats.create(self, owner: str) -> Chat`
  - `Chats.save(self, chat: Chat) -> None`: bỏ qua nếu `chat.deleted` là `True`; tự suy ra `chat.title` từ tin đầu tiên `from == "user"` trong `chat.shown` nếu `chat.title == ""`.
  - `Chats.get(self, chat_id: str, owner: str) -> Chat | None`
  - `Chats.list_for(self, owner: str, limit: int = LIST_CHATS_LIMIT) -> list[dict]`: trả danh sách `{"chatId": str, "title": str, "updatedAt": str}` (ISO `+07:00`, chỉ gồm chat có `title != ""` và chưa quá `CHAT_TTL_SECONDS`, sắp mới nhất trước).
  - `Chats.delete(self, chat_id: str, owner: str) -> bool`: đánh dấu `deleted = True` (dưới `chat.lock` nếu đang có trong RAM), xoá khỏi RAM và SQLite; trả `True` nếu xoá được, `False` nếu không tồn tại hoặc không thuộc `owner`.

- [ ] **Step 1: Write the failing tests** trong `agent/tests/test_chat.py`

Thêm các test sau (và cập nhật `test_in_memory_chat_cache_respects_ttl_and_max_chats` dùng `CHAT_TTL_SECONDS + 60`):

```python
def test_make_title_collapses_whitespace_and_truncates():
    from flight_agent.chat import make_title
    assert make_title("  Tìm   vé\nHAN   đi SGN  ") == "Tìm vé HAN đi SGN"
    long_text = "Đặt vé " + "rất dài " * 20
    title = make_title(long_text)
    assert len(title) == 60 and title.endswith("…")


def test_list_for_orders_newest_first_skips_empty_and_isolates_owners(tmp_path):
    store = Chats(tmp_path / "chats.sqlite3")
    empty = store.create("user:3")
    c1 = store.create("user:3")
    c1.shown.append({"from": "user", "text": "Vé HAN đi SGN ngày mai"})
    store.save(c1)
    c1.updated_at -= 100
    store._conn.execute("UPDATE chats SET updated_at = ? WHERE id = ?", (c1.updated_at, c1.id))
    store._conn.commit()
    c2 = store.create("user:3")
    c2.shown.append({"from": "user", "text": "Hoàn vé mất bao nhiêu phí?"})
    store.save(c2)
    other = store.create("user:4")
    other.shown.append({"from": "user", "text": "Chat của người khác"})
    store.save(other)

    listed = store.list_for("user:3")
    assert [x["chatId"] for x in listed] == [c2.id, c1.id]
    assert listed[0]["title"] == "Hoàn vé mất bao nhiêu phí?"
    assert listed[0]["updatedAt"].endswith("+07:00")
    assert empty.id not in [x["chatId"] for x in listed]


def test_delete_removes_chat_for_owner_only_and_blocks_save_resurrection(tmp_path):
    store = Chats(tmp_path / "chats.sqlite3")
    c = store.create("user:3")
    c.shown.append({"from": "user", "text": "Xin chào"})
    store.save(c)
    assert store.delete(c.id, "user:4") is False
    assert store.delete(c.id, "user:3") is True
    assert store.delete(c.id, "user:3") is False
    store.save(c)  # mô phỏng luồng nền gọi save() sau khi chat đã bị xoá
    assert store.get(c.id, "user:3") is None
    assert store.list_for("user:3") == []


def test_sqlite_migrates_legacy_session_table_cleanly(tmp_path):
    import sqlite3
    db = tmp_path / "legacy.sqlite3"
    with sqlite3.connect(db) as conn:
        conn.execute("CREATE TABLE chats (id TEXT PRIMARY KEY, session TEXT NOT NULL, updated_at REAL NOT NULL, payload TEXT NOT NULL)")
        conn.execute("INSERT INTO chats VALUES ('old', 's1', 9999999999.0, '{}')")
    store = Chats(db)
    assert store.get("old", "user:3") is None
    c = store.create("user:3")
    c.shown.append({"from": "user", "text": "Sau migration"})
    store.save(c)
    assert store.list_for("user:3")[0]["chatId"] == c.id
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd agent && uv run pytest tests/test_chat.py -k "make_title or list_for or blocks_save_resurrection or legacy_session_table" -v`
Expected: FAIL (`ImportError` / `AttributeError: 'Chats' object has no attribute 'list_for'`).

- [ ] **Step 3: Implement `owner`, `title`, `deleted`, `list_for`, `delete`, và migration SQLite trong `agent/flight_agent/chat.py`**

Cập nhật `agent/flight_agent/chat.py`:
- Đặt `CHAT_TTL_SECONDS = 7 * 86_400.0`, `LIST_CHATS_LIMIT = 20`, `TITLE_MAX = 60`, múi giờ `VN = timezone(timedelta(hours=7))`.
- Viết `make_title(text: str) -> str`.
- Trong `Chats.__init__`: kiểm tra `PRAGMA table_info(chats)`; nếu bảng đã tồn tại nhưng thiếu cột `owner` (schema cũ dùng `session`), chạy `DROP TABLE chats` rồi tạo bảng `chats(id TEXT PRIMARY KEY, owner TEXT NOT NULL, title TEXT NOT NULL DEFAULT '', updated_at REAL NOT NULL, payload TEXT NOT NULL)` kèm index `idx_chats_owner_updated ON chats(owner, updated_at DESC)`.
- Trong `Chats.save(chat)`: nếu `chat.deleted` trả ngay; nếu `not chat.title`, lấy từ mục đầu tiên `e.get("from") == "user"` trong `chat.shown` qua `make_title`; ghi cột `owner` và `title`.
- Cài đặt `Chats.list_for(owner, limit=LIST_CHATS_LIMIT)` truy vấn SQLite với `WHERE owner = ? AND title != '' AND updated_at >= ? ORDER BY updated_at DESC LIMIT ?`, đổi `updated_at` sang `datetime.fromtimestamp(ts, VN).isoformat(timespec="seconds")`.
- Cài đặt `Chats.delete(chat_id, owner)`: kiểm tra sở hữu (trong RAM hoặc SQLite), nếu có trong RAM lấy `chat.lock` đặt `chat.deleted = True` và pop khỏi `_chats`, rồi `DELETE FROM chats WHERE id = ? AND owner = ?`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd agent && uv run pytest tests/test_chat.py -v`
Expected: Tất cả PASS.

- [ ] **Step 5: Commit**

```bash
git add agent/flight_agent/chat.py agent/tests/test_chat.py
git commit -m "feat(agent): gan lich su chat theo owner, ho tro tieu de, liet ke va xoa"
```

---

### Task 2: Xác thực `user_id` qua `/api/me` và thêm API `GET/DELETE /agent/chats`

**Files:**
- Modify: `agent/flight_agent/app.py:45-141`
- Modify: `agent/tests/fakes.py:159-231`
- Test: `agent/tests/test_app.py`

**Interfaces:**
- Consumes: `Chats.create(owner)`, `Chats.get(chat_id, owner)`, `Chats.list_for(owner)`, `Chats.delete(chat_id, owner)` từ Task 1; `Backend.get("/api/me")`, `BackendError` từ `agent/flight_agent/backend.py`.
- Produces (HTTP endpoints trong `agent/flight_agent/app.py`):
  - `GET /agent/chats` → `200 {"chats": [{"chatId": str, "title": str, "updatedAt": str}, ...]}` (hoặc `401`/`403`/`503`).
  - `POST /agent/chats` → `201 {"chatId": str}` (chủ sở hữu là `f"user:{me['id']}"`).
  - `GET /agent/chats/{chat_id}` → `200 {"entries": [...], "proposalId": str | None}` (hoặc `401`/`404`).
  - `DELETE /agent/chats/{chat_id}` → `204 No Content` (hoặc `401`/`404`).
  - `POST /agent/chats/{chat_id}/messages`, `/proposals/{proposal_id}/dismiss`, `/proposals/{proposal_id}/approve` đều xác thực qua `require_owner(request)` (đọc `GET /api/me` từ cookie phiên; rate-limit tính theo `owner`).

- [ ] **Step 1: Mở rộng `FakeBackend` để hỗ trợ nhiều user theo cookie và viết failing tests trong `agent/tests/test_app.py`**

Trong `agent/tests/fakes.py`, thêm tham số `me_by_session: dict[str, dict | int] | None = None` vào `FakeBackend.__init__`: khi xử lý `GET /api/me`, nếu `me_by_session` có key khớp giá trị `JSESSIONID` trong header `Cookie`, trả về user dict hoặc status lỗi tương ứng; mặc định vẫn giữ `id: 3` như cũ.

Trong `agent/tests/test_app.py`, cập nhật `test_another_session_cannot_use_the_chat` thành kiểm tra khác user (`s2` thuộc `id: 4`) và bổ sung các test mới:

```python
def test_same_user_with_new_session_cookie_still_reads_and_lists_chats():
    fake = FakeBackend(me_by_session={
        "s1": {"id": 3, "fullName": "A", "email": "a@demo.local", "phone": "090", "role": "CUSTOMER", "status": "ACTIVE"},
        "s2": {"id": 3, "fullName": "A", "email": "a@demo.local", "phone": "090", "role": "CUSTOMER", "status": "ACTIVE"},
        "s3": {"id": 4, "fullName": "B", "email": "b@demo.local", "phone": "091", "role": "CUSTOMER", "status": "ACTIVE"},
    })
    c = client(fake, *hold_flow())
    chat = c.post("/agent/chats", headers={"Cookie": "JSESSIONID=s1"}).json()["chatId"]
    sent(c, chat, "Tìm vé SGN đi Đà Nẵng", {"Cookie": "JSESSIONID=s1"})

    listed = c.get("/agent/chats", headers={"Cookie": "JSESSIONID=s2"})
    assert listed.status_code == 200 and [x["chatId"] for x in listed.json()["chats"]] == [chat]
    assert c.get(f"/agent/chats/{chat}", headers={"Cookie": "JSESSIONID=s2"}).status_code == 200
    assert c.get("/agent/chats", headers={"Cookie": "JSESSIONID=s3"}).json()["chats"] == []
    assert c.get(f"/agent/chats/{chat}", headers={"Cookie": "JSESSIONID=s3"}).status_code == 404


def test_expired_backend_session_returns_401_on_chat_endpoints():
    c = client(FakeBackend(me_status=401))
    assert c.get("/agent/chats", headers={"Cookie": COOKIE}).status_code == 401
    assert c.post("/agent/chats", headers={"Cookie": COOKIE}).status_code == 401


def test_delete_chat_endpoint_isolates_owners_and_returns_204():
    fake = FakeBackend(me_by_session={
        "s1": {"id": 3, "fullName": "A", "email": "a@demo.local", "phone": "090", "role": "CUSTOMER", "status": "ACTIVE"},
        "s2": {"id": 4, "fullName": "B", "email": "b@demo.local", "phone": "091", "role": "CUSTOMER", "status": "ACTIVE"},
    })
    c = client(fake, *hold_flow())
    chat = c.post("/agent/chats", headers={"Cookie": "JSESSIONID=s1"}).json()["chatId"]
    sent(c, chat, "Đặt vé", {"Cookie": "JSESSIONID=s1"})
    assert c.delete(f"/agent/chats/{chat}", headers={"Cookie": "JSESSIONID=s2"}).status_code == 404
    assert c.delete(f"/agent/chats/{chat}", headers={"Cookie": "JSESSIONID=s1"}).status_code == 204
    assert c.get(f"/agent/chats/{chat}", headers={"Cookie": "JSESSIONID=s1"}).status_code == 404
    assert c.get("/agent/chats", headers={"Cookie": "JSESSIONID=s1"}).json()["chats"] == []
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd agent && uv run pytest tests/test_app.py -k "same_user_with_new_session or expired_backend_session or delete_chat_endpoint" -v`
Expected: FAIL (`404`/`405` vì chưa có `GET /agent/chats` và `DELETE /agent/chats/{id}`).

- [ ] **Step 3: Implement `require_owner`, `GET /agent/chats`, `DELETE /agent/chats/{chat_id}` trong `agent/flight_agent/app.py`**

Trong `create_app()` tại `agent/flight_agent/app.py`:
- Viết helper `require_owner(request: Request) -> str`:
  - Không có cookie `JSESSIONID` → `raise HTTPException(401, "Bạn cần đăng nhập.")`.
  - Gọi `backend(request).get("/api/me")`:
    - `BackendError` có `status == 401` → `raise HTTPException(401, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")`.
    - `BackendError` có `status == 403` → `raise HTTPException(403, "Trợ lý chỉ dùng cho tài khoản khách hàng.")`.
    - `BackendError` khác → `raise HTTPException(503, "Không kết nối được hệ thống. Hãy thử lại sau.")`.
  - Trả về `f"user:{me['id']}"`.
- Cập nhật `chat_of(request, chat_id)` và `check_rate_limit(owner)` dùng `require_owner(request)`.
- Thêm `@app.get("/agent/chats")` trả `{"chats": chats.list_for(require_owner(request))}`.
- Thêm `@app.delete("/agent/chats/{chat_id}", status_code=204)` gọi `chats.delete(chat_id, require_owner(request))`, trả `404` nếu `False`, trả `Response(status_code=204)` nếu `True`.

- [ ] **Step 4: Run full agent test suite to verify everything passes**

Run: `cd agent && uv run pytest -v`
Expected: Tất cả PASS (không có regression ở `test_chat.py`, `test_harness.py`, `test_app.py`).

- [ ] **Step 5: Commit**

```bash
git add agent/flight_agent/app.py agent/tests/fakes.py agent/tests/test_app.py
git commit -m "feat(agent): xac thuc user_id qua /api/me va them API liet ke/xoa lich su chat"
```

---

### Task 3: Minh bạch nguồn bộ nhớ hành khách (`memory_used` card)

**Files:**
- Modify: `agent/flight_agent/core.py:438-453`
- Test: `agent/tests/test_harness.py`

**Interfaces:**
- Consumes: `Harness.list_recent_passengers()`, `Harness.cards` trong `agent/flight_agent/core.py`.
- Produces:
  - Khi `list_recent_passengers()` tìm thấy ít nhất 1 hành khách, thêm vào `self.cards` thẻ:
    ```python
    {
        "type": "memory_used",
        "source": "recent_bookings",
        "passengers": [
            {
                "fullName": "NGUYEN HUNG CUONG",
                "gender": "MALE",
                "dateOfBirth": "2004-03-12",
                "bookingCode": "AAA111",
            },
            ...
        ],
    }
    ```
  - Giữ nguyên giá trị trả về của tool cho model (`{"status": "ok", "passengers": [...]}`) để không làm thay đổi hành vi LLM và các eval hiện có. Nếu không có booking cũ nào (`passengers == []`), không thêm thẻ `memory_used`.

- [ ] **Step 1: Write the failing tests** trong `agent/tests/test_harness.py`

```python
def test_recent_passengers_emits_memory_used_card_with_source_booking_code():
    h, _ = ready(FakeBackend(past=PAST))
    r = h.execute("list_recent_passengers", {})
    assert r["status"] == "ok" and len(r["passengers"]) == 4
    card = h.cards[-1]
    assert card == {
        "type": "memory_used",
        "source": "recent_bookings",
        "passengers": [
            {"fullName": "NGUYEN HUNG CUONG", "gender": "MALE", "dateOfBirth": "2004-03-12", "bookingCode": "AAA111"},
            {"fullName": "TRAN THI MAI", "gender": "FEMALE", "dateOfBirth": "2005-01-02", "bookingCode": "AAA111"},
            {"fullName": "NGUYEN BE NA", "gender": "MALE", "dateOfBirth": "2020-05-05", "bookingCode": "BBB222"},
            {"fullName": "LE VAN AN", "gender": "MALE", "dateOfBirth": "1990-09-09", "bookingCode": "CCC333"},
        ],
    }
    assert "B1234567" not in json.dumps(card)


def test_empty_recent_passengers_does_not_emit_memory_used_card():
    h, _ = ready()
    h.execute("list_recent_passengers", {})
    assert h.cards == []
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd agent && uv run pytest tests/test_harness.py -k "memory_used" -v`
Expected: FAIL (`IndexError` vì `h.cards` đang rỗng).

- [ ] **Step 3: Cập nhật `Harness.list_recent_passengers()` trong `agent/flight_agent/core.py`**

Trong vòng lặp `for summary in page["content"]`, song song với `passengers` trả cho model, ghi thêm danh sách `sourced` chứa `{"fullName", "gender", "dateOfBirth", "bookingCode": summary["code"]}` cho từng hành khách duy nhất (ưu tiên mã booking mới nhất chứa hành khách đó). Sau vòng lặp, nếu `sourced` không rỗng thì `self.cards.append({"type": "memory_used", "source": "recent_bookings", "passengers": sourced})`.

- [ ] **Step 4: Run full agent tests to verify they pass**

Run: `cd agent && uv run pytest -v`
Expected: Tất cả PASS.

- [ ] **Step 5: Commit**

```bash
git add agent/flight_agent/core.py agent/tests/test_harness.py
git commit -m "feat(agent): them the memory_used ghi ro nguon booking cua hanh khach cu"
```

---

### Task 4: Frontend `/assistant` — Minh bạch ngữ cảnh, Bước xử lý thân thiện & Sidebar Lịch sử Trò chuyện

**Files:**
- Modify: `frontend/src/app/assistant/agent-api.ts`
- Create: `frontend/src/app/assistant/chat-cards.tsx`
- Create: `frontend/src/app/assistant/chat-history.tsx`
- Modify: `frontend/src/app/assistant/assistant-chat.tsx`
- Test: `frontend/e2e/assistant.spec.ts`

**Interfaces:**
- Consumes: API `GET /agent/chats`, `DELETE /agent/chats/{chat_id}`, `GET /agent/chats/{chat_id}`, `POST /agent/chats`, `POST /agent/chats/{chat_id}/messages`, và thẻ `memory_used` từ Tasks 1–3; `clock`, `dmy`, `DIRECTION_LABEL`, `PASSENGER_LABEL` từ `frontend/src/lib/booking.ts`; `pillPrimary`, `pillLine`, `Notice`, `textLink` từ `frontend/src/components/form.tsx`.
- Produces:
  - Trong `frontend/src/app/assistant/agent-api.ts`:
    - `ChatSummary = { chatId: string; title: string; updatedAt: string }`
    - `Card` bổ sung biến thể `| { type: "memory_used"; source: "recent_bookings"; passengers: { fullName: string; gender: "MALE" | "FEMALE"; dateOfBirth: string; bookingCode: string }[] }`
    - `agentDelete(path: string): Promise<void>` (gửi `DELETE` kèm `X-XSRF-TOKEN`, không parse JSON khi `204`).
    - `describeStep(step: Step): string`: chuyển từng tên tool sang mô tả tiếng Việt ngắn gọn kèm tham số quan trọng (ví dụ `list_recent_passengers` → `"Tra cứu hành khách từ các đặt chỗ gần đây"`, `get_booking` → `"Xem chi tiết đặt chỗ K7M2QX"`, `search_flights` → `"Tìm chuyến bay phù hợp"`, `read_policy` → `"Đọc quy định chính sách SkyLine"`).
  - Trong `frontend/src/app/assistant/chat-cards.tsx`:
    - `Steps({ steps, label, live }: { steps: Step[]; label: string; live?: boolean })`: khi `live` là `true` (đang stream SSE), hiển thị trực tiếp danh sách mô tả tiếng Việt; khi đã xong (`live` là `false`), hiển thị dòng mô tả tiếng Việt gọn kèm `<details><summary>Chi tiết kỹ thuật ({steps.length} bước)</summary>...</details>` chứa `tool(args) → status` monospace.
    - `MemoryUsedCard({ card })`: thẻ `card` có tiêu đề `"Thông tin hành khách từ đặt chỗ gần đây"`, mỗi hành khách hiện họ tên, giới tính, ngày sinh (`dmy`) và link bấm được tới `/bookings/{bookingCode}`.
    - `ActiveContextBar({ entries, hasOpenProposal })`: suy ra từ `entries` (thẻ `constraints` gần nhất, thẻ `memory_used` gần nhất, và cờ `hasOpenProposal`); chỉ hiển thị khi có ít nhất 1 mục; nằm gọn phía trên khung hội thoại để người dùng nhìn 1 lần biết ngay trợ lý đang nhớ gì trong phiên hiện tại.
    - `CardView`: giữ toàn bộ 6 loại thẻ cũ + thêm `case "memory_used"`.
  - Trong `frontend/src/app/assistant/chat-history.tsx`:
    - `ChatHistory({ chats, activeId, loading, busy, onSelect, onNew, onDelete })`:
      - Trên desktop (`lg:`): cột trái `260px` (`card p-14`), tiêu đề `"Lịch sử trò chuyện"`, nút duy nhất `"Cuộc trò chuyện mới"` (bỏ nút trùng ở tiêu đề cột phải để tránh trùng lặp UI và giữ selector Playwright duy nhất), danh sách cuộc trò chuyện (tiêu đề 1 dòng `truncate`, giờ cập nhật `${clock(c.updatedAt)} ${dmy(c.updatedAt)}`, highlight mục đang chọn bằng `bg-accent-soft text-accent font-semibold`), mỗi dòng có nút `"Xoá"` (`aria-label="Xoá cuộc trò chuyện <title>"`).
      - Trên mobile (`< lg`): gói trong `<details>` gập gọn `"Lịch sử trò chuyện (N)"` (render cùng một danh sách, không nhân đôi DOM nút `"Cuộc trò chuyện mới"`).
      - Khi `chats` rỗng: hiện dòng hướng dẫn ngắn `"Chưa có cuộc trò chuyện nào được lưu."`.
  - Trong `frontend/src/app/assistant/assistant-chat.tsx`:
    - Khi mount: gọi `GET /agent/chats` nạp danh sách lịch sử; nếu gặp `AgentError(401)` thì `router.replace("/login?next=/assistant")`; nếu gặp 5xx/network (`status === 0 || status >= 500`) thì không crash, giữ danh sách rỗng và chỉ báo lỗi khi gửi tin.
    - Chọn 1 chat trong `ChatHistory`: gọi `GET /agent/chats/{id}`, lưu `id` vào `sessionStorage`, cập nhật `entries` và `closed`.
    - Sau khi gửi tin nhắn đầu tiên (tạo `chatId` mới) hoặc sau mỗi lượt xong: làm mới `GET /agent/chats` để danh sách lịch sử luôn khớp thứ tự mới nhất.
    - Bấm xoá chat: gọi `agentDelete(/chats/${id})`, loại khỏi danh sách; nếu trùng `chatId.current` thì gọi `resetChat()`.

- [ ] **Step 1: Write the failing E2E tests** trong `frontend/e2e/assistant.spec.ts`

Cập nhật 2 assertion cũ về dòng trace raw (mở `<details>` `"Chi tiết kỹ thuật"` hoặc kiểm tra cả mô tả tiếng Việt lẫn chi tiết kỹ thuật) và thêm các test mới (mọi test đều mock `/agent/chats` trả `{"chats": []}` nếu không cần lịch sử sẵn):

```ts
test("Hiển thị thẻ nguồn hành khách từ đặt chỗ gần đây và thanh ngữ cảnh phiên", async ({
  page,
}) => {
  await page.route("**/agent/chats", (r) =>
    r.request().method() === "GET"
      ? r.fulfill({ json: { chats: [] } })
      : r.fulfill({ status: 201, json: { chatId: "new" } }),
  );
  await page.route("**/agent/chats/new/messages", (r) =>
    r.fulfill({
      contentType: "text/event-stream",
      body: sse({
        type: "turn",
        turn: {
          reply: "Mình đã lấy thông tin hành khách từ đơn trước.",
          trace: [{ tool: "list_recent_passengers", args: {}, status: "ok" }],
          cards: [
            {
              type: "memory_used",
              source: "recent_bookings",
              passengers: [
                {
                  fullName: "NGUYEN VAN AN",
                  gender: "MALE",
                  dateOfBirth: "1998-05-15",
                  bookingCode: "K7M2QX",
                },
              ],
            },
          ],
        },
      }),
    }),
  );
  await page.goto("/assistant");
  await page.getByRole("textbox").fill("Đặt cho khách lần trước");
  await page.getByRole("button", { name: "Gửi" }).click();
  await expect(
    page.getByText("Thông tin hành khách từ đặt chỗ gần đây"),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "K7M2QX" })).toHaveAttribute(
    "href",
    "/bookings/K7M2QX",
  );
  await expect(
    page.getByText("Tra cứu hành khách từ các đặt chỗ gần đây"),
  ).toBeVisible();
});

test("Lịch sử trò chuyện: mở lại chat cũ và xoá chat đang mở", async ({
  page,
}) => {
  const chats = [
    {
      chatId: "old-1",
      title: "Tìm vé SGN đi Đà Nẵng",
      updatedAt: "2026-11-23T18:00:00+07:00",
    },
  ];
  await page.route("**/agent/chats", (r) => r.fulfill({ json: { chats } }));
  await page.route("**/agent/chats/old-1", (r) =>
    r.request().method() === "DELETE"
      ? r.fulfill({ status: 204 })
      : r.fulfill({ json: VIEW }),
  );
  await page.goto("/assistant");
  await page.getByRole("button", { name: /Tìm vé SGN đi Đà Nẵng/ }).click();
  await expect(page.getByText("Đổi chuyến sớm hơn")).toBeVisible();
  await page
    .getByRole("button", { name: "Xoá cuộc trò chuyện Tìm vé SGN đi Đà Nẵng" })
    .click();
  await expect(page.getByText("Đổi chuyến sớm hơn")).toHaveCount(0);
  await expect(page.getByText("Gõ yêu cầu bằng lời")).toBeVisible();
});

test("Phiên hết hạn (401 từ /agent/chats) chuyển hướng về /login", async ({
  page,
}) => {
  await page.route("**/agent/chats", (r) =>
    r.fulfill({ status: 401, json: { detail: "Phiên đăng nhập đã hết hạn." } }),
  );
  await page.goto("/assistant");
  await expect(page).toHaveURL(/\/login\?next=%2Fassistant/);
});

test("Service agent tắt (503 khi nạp lịch sử) vẫn mở trang trống không crash", async ({
  page,
}) => {
  await page.route("**/agent/chats", (r) =>
    r.fulfill({ status: 503, json: { detail: "Service unavailable" } }),
  );
  await page.goto("/assistant");
  await expect(page.getByText("Gõ yêu cầu bằng lời")).toBeVisible();
  await expect(page.getByRole("textbox")).toBeEnabled();
});
```

- [ ] **Step 2: Implement `agent-api.ts`, `chat-cards.tsx`, `chat-history.tsx`, và cập nhật `assistant-chat.tsx`**

Thực hiện đúng theo block **Interfaces** ở trên:
1. Cập nhật `frontend/src/app/assistant/agent-api.ts`: thêm `ChatSummary`, thẻ `memory_used`, `agentDelete()`, `describeStep()`.
2. Tạo `frontend/src/app/assistant/chat-cards.tsx`: di chuyển `Steps`, `ConstraintsCard`, `ProposalCard`, `CardView` từ `assistant-chat.tsx`, bổ sung `MemoryUsedCard` và `ActiveContextBar`.
3. Tạo `frontend/src/app/assistant/chat-history.tsx`: hiển thị sidebar lịch sử trò chuyện theo đúng token `DESIGN.md` (nền `surface`, viền `line`, bo góc `10px`, không dùng `new Date()`/`Date.now()`).
4. Cập nhật `frontend/src/app/assistant/assistant-chat.tsx`: bố cục 2 cột `lg:grid-cols-[260px_minmax(0,1fr)]`, tích hợp `ChatHistory` và `ActiveContextBar`, nạp danh sách lịch sử khi mount, redirect `/login?next=/assistant` khi `401`, xoá & chuyển đổi giữa các cuộc trò chuyện.

- [ ] **Step 3: Run lint, typecheck/build, và Playwright E2E**

Run:
```powershell
cd frontend
npm run lint
npm run build
```
Sau đó rebuild lại container Docker `agent` và `frontend` rồi chạy Playwright kiểm chứng trên server thật:
```powershell
cd ..
docker compose up -d --build agent frontend
cd frontend
$env:E2E_BASE_URL="http://localhost:3000"
npx playwright test e2e/assistant.spec.ts e2e/agent-links.spec.ts e2e/agent-stream.spec.ts
```
Expected: `eslint` 0 lỗi, `next build` thành công (Cache Components pass), cả 5 container `healthy`, toàn bộ Playwright tests PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/assistant/ frontend/e2e/assistant.spec.ts
git commit -m "feat(assistant): hien thi nguon bo nho, thanh ngu canh va sidebar lich su tro chuyen"
```
