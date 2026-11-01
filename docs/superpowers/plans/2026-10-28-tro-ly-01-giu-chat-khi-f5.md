# Trợ lý Bước 1: Giữ cuộc trò chuyện khi F5 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tải lại `/assistant` giữa chừng vẫn thấy đủ tin nhắn, bước tool và thẻ; thẻ duyệt còn hiệu lực vẫn bấm "Giữ chỗ" được.

**Architecture:** Service lưu thêm danh sách lượt đã hiển thị trong `Chat` và mở `GET /agent/chats/{id}`. Frontend lưu `chatId` vào `sessionStorage`, mở trang thì đọc lại chat. Chat vẫn chỉ nằm trong RAM của service.

**Tech Stack:** FastAPI, pytest (agent); Next.js 16 (frontend).

**Spec:** [docs/superpowers/specs/2026-10-26-tro-ly-dat-ve-agent-design.md](../specs/2026-10-26-tro-ly-dat-ve-agent-design.md) · Lộ trình: [Bước 1](2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md#bước-1--giữ-cuộc-trò-chuyện-khi-tải-lại-trang)

## Global Constraints

- Làm trên nhánh `feature/flight-agent`. Không sửa `backend/`.
- Không giới hạn số chat giữ trong RAM (quyết định cho mục "Cần quyết khi viết plan": một người dùng, chat nhỏ; thêm giới hạn khi bộ nhớ thật sự tăng).
- Không lưu chat xuống đĩa, không có danh sách chat cũ (ngoài phạm vi theo lộ trình).
- Dữ liệu đọc lại dùng đúng dạng `Entry` mà trang đang hiển thị: `{"from": "user", "text"}` hoặc `{"from": "agent", "turn": {reply, trace, cards}}`.
- Thẻ duyệt chỉ còn hiệu lực khi `proposalId` của nó bằng `harness.proposal.id`; GET trả `proposalId` của đề xuất còn hiệu lực (hoặc `null`).
- GET kiểm `JSESSIONID` như các endpoint khác (sai phiên thì 404).
- Trang nhận 404 khi đọc lại thì xoá `chatId` và mở chat trống, không báo lỗi.

## Review Focus

1. **Lượt duyệt thất bại rồi agent chạy tiếp** (`approve` gọi lại `turn` với tin "[Hệ thống] ..."): tin hệ thống không được hiện như tin của khách khi đọc lại.
2. **F5 khi một lượt đang chạy:** GET chờ `Chat.lock`, trả về sau khi lượt xong, không trả dữ liệu dở dang.
3. **Đọc lại khi sessionStorage có `chatId` của phiên cũ** (đăng nhập lại đổi `JSESSIONID`): 404 → chat trống, không kẹt lỗi.

## File structure

```
agent/flight_agent/chat.py     Chat.shown (Task 1)
agent/flight_agent/app.py      ghi lượt vào Chat.shown, GET /agent/chats/{id} (Task 1)
agent/tests/test_app.py        Task 1
agent/README.md                danh sách endpoint (Task 1)
frontend/src/app/assistant/agent-api.ts       agentGet, Entry, ChatView (Task 2)
frontend/src/app/assistant/assistant-chat.tsx sessionStorage + đọc lại (Task 2)
```

---

### Task 1: Service lưu lượt đã hiển thị và cho đọc lại

**Files:** Modify `agent/flight_agent/chat.py`, `agent/flight_agent/app.py`, `agent/tests/test_app.py`, `agent/README.md`.

- [ ] **Step 1: Viết test.** Thêm vào `agent/tests/test_app.py`:

```python
def test_owner_reads_back_every_turn_and_only_the_live_proposal_is_open():
    c = client(FakeBackend(), *hold_flow())
    h = {"Cookie": COOKIE}
    chat = c.post("/agent/chats", headers=h).json()["chatId"]
    out = c.post(f"/agent/chats/{chat}/messages", json={"text": "Đặt vé"}, headers=h).json()
    pid = out["cards"][-1]["proposalId"]
    view = c.get(f"/agent/chats/{chat}", headers=h).json()
    assert view == {"entries": [{"from": "user", "text": "Đặt vé"}, {"from": "agent", "turn": out}],
                    "proposalId": pid}
    held = c.post(f"/agent/chats/{chat}/proposals/{pid}/approve", headers=h).json()
    view = c.get(f"/agent/chats/{chat}", headers=h).json()
    assert view["entries"][-1] == {"from": "agent", "turn": held} and len(view["entries"]) == 3
    assert view["proposalId"] is None


def test_a_new_proposal_closes_the_old_card_in_the_read_back():
    pax = dict(full_name="Nguyễn Hùng Cường", gender="MALE", date_of_birth="2004-03-12")
    c = client(FakeBackend(), *hold_flow(), call("propose_hold", flight_ids=[104], fare_family_id=1, **pax),
               AIMessage(content="Mình đổi sang QH104."))
    h = {"Cookie": COOKIE}
    chat = c.post("/agent/chats", headers=h).json()["chatId"]
    first = c.post(f"/agent/chats/{chat}/messages", json={"text": "Đặt vé"}, headers=h).json()
    second = c.post(f"/agent/chats/{chat}/messages", json={"text": "Đổi chuyến sớm hơn"}, headers=h).json()
    view = c.get(f"/agent/chats/{chat}", headers=h).json()
    assert view["proposalId"] == second["cards"][-1]["proposalId"] != first["cards"][-1]["proposalId"]


def test_failed_approval_does_not_show_the_system_message_as_the_customer():
    pax = dict(full_name="Nguyễn Hùng Cường", gender="MALE", date_of_birth="2004-03-12")
    c = client(FakeBackend(hold_error=(409, "SEATS_UNAVAILABLE")), *hold_flow(),
               call("propose_hold", flight_ids=[104], fare_family_id=1, **pax), AIMessage(content="Duyệt QH104 nhé."))
    h = {"Cookie": COOKIE}
    chat = c.post("/agent/chats", headers=h).json()["chatId"]
    pid = c.post(f"/agent/chats/{chat}/messages", json={"text": "Đặt vé"}, headers=h).json()["cards"][-1]["proposalId"]
    c.post(f"/agent/chats/{chat}/proposals/{pid}/approve", headers=h)
    view = c.get(f"/agent/chats/{chat}", headers=h).json()
    assert [e["from"] for e in view["entries"]] == ["user", "agent", "agent"]
```

Và mở rộng `test_another_session_cannot_use_the_chat` thêm:

```python
    assert c.get(f"/agent/chats/{chat}", headers={"Cookie": "JSESSIONID=s2"}).status_code == 404
```

- [ ] **Step 2: Chạy test.** `cd agent && uv run pytest tests/test_app.py`
      Expected: 4 test mới hoặc mở rộng FAIL (GET trả 405), các test cũ PASS.

- [ ] **Step 3: Cài đặt.** `chat.py`: thêm trường `shown: list = field(default_factory=list)` vào `Chat`, comment: các lượt trang đã hiển thị (tin của khách và kết quả mỗi lượt), để dựng lại khung chat khi F5. `app.py`: endpoint gửi tin và endpoint duyệt giữ `chat.lock` (RLock) quanh lời gọi `turn`/`approve` và việc ghi vào `chat.shown`; tin của khách ghi ở endpoint gửi tin, nên tin "[Hệ thống]" mà `approve` tự gửi không bao giờ vào `shown`. Thêm:

```python
    @app.get("/agent/chats/{chat_id}")
    def read_chat(chat_id: str, request: Request):
        chat = chat_of(request, chat_id)
        with chat.lock:     # đang có lượt chạy thì chờ lượt xong
            proposal = chat.harness.proposal
            return {"entries": chat.shown, "proposalId": proposal.id if proposal else None}
```

- [ ] **Step 4: Chạy lại.** `cd agent && uv run pytest`
      Expected: tất cả PASS.

- [ ] **Step 5: README.** Ghi trong `agent/README.md` rằng chat nằm trong RAM: trang đọc lại bằng `GET /agent/chats/{id}` khi F5, restart service thì mất.

- [ ] **Step 6: Commit.** `feat(agent): đọc lại cuộc trò chuyện bằng GET /agent/chats/{id}`

### Task 2: Trang giữ `chatId` qua F5

**Files:** Modify `frontend/src/app/assistant/agent-api.ts`, `frontend/src/app/assistant/assistant-chat.tsx`.

- [ ] **Step 1: `agent-api.ts`.** Chuyển kiểu `Entry` từ `assistant-chat.tsx` sang đây (export), thêm `export type ChatView = { entries: Entry[]; proposalId: string | null }`. Tách phần gửi request của `agentPost` thành hàm chung theo method; `agentGet<T>(path)` dùng chung phần xử lý lỗi, không gửi `X-XSRF-TOKEN`.

- [ ] **Step 2: `assistant-chat.tsx`.** Hằng `CHAT_KEY = "assistant-chat"`. `newChat` ghi `chatId` vào `sessionStorage`. `useEffect` lúc mount (không đọc `sessionStorage` lúc render): có `chatId` thì `busy` trong lúc gọi `agentGet<ChatView>`; thành công thì đặt `entries` và cho mọi thẻ duyệt có `proposalId` khác `view.proposalId` vào `closed`; 404 thì xoá khoá và `chatId`, không báo lỗi; lỗi khác hiện `Notice` như hiện tại.

- [ ] **Step 3: Kiểm tra.** `cd frontend && npm run lint && npm run build`
      Expected: không lỗi.

- [ ] **Step 4: Commit.** `feat(frontend): /assistant giữ cuộc trò chuyện khi tải lại trang`

### Task 3: Kiểm bằng tay và cập nhật lộ trình

- [ ] **Step 1: Bằng tay** (backend profile `dev`, service agent, `npm run dev`): chat tới thẻ duyệt, F5, thấy đủ tin nhắn và bước tool, bấm "Giữ chỗ" ra thẻ "Đã giữ chỗ". Restart service rồi F5: chat trống, không có lỗi.
      Expected: đúng như trên.

- [ ] **Step 2: Lộ trình.** Trong `2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md` §1: đánh dấu cột "Xong" của Bước 1 và gắn link plan này vào cột "Bước".

- [ ] **Step 3: Commit.** `docs(agent): xong Bước 1 của lộ trình trợ lý`
