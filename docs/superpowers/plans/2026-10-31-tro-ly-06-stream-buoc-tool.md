# Trợ lý Bước 6: Stream bước tool (SSE) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trong lúc agent chạy một lượt, trang `/assistant` hiện dần từng bước tool đã xong, thay vì đứng yên tới khi có câu trả lời.

**Architecture:** Endpoint gửi tin trả `text/event-stream`. Lượt chat chạy trong một luồng riêng giữ `Chat.lock`; `Harness` báo mỗi dòng trace qua callback `on_step` vào một hàng đợi, generator của `StreamingResponse` đọc hàng đợi và gửi từng sự kiện. Frontend đọc bằng `fetch` cùng `ReadableStream` và hiện các bước tạm cho tới khi có sự kiện `turn`.

**Tech Stack:** FastAPI `StreamingResponse`, pytest (agent); Next.js 16, Playwright (frontend).

**Spec:** [docs/superpowers/specs/2026-10-26-tro-ly-dat-ve-agent-design.md](../specs/2026-10-26-tro-ly-dat-ve-agent-design.md) · Lộ trình: [Bước 6](2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md#bước-6--stream-bước-tool-sse)

## Global Constraints

- Làm trên nhánh `feature/flight-agent`. Không sửa `backend/`.
- **Không chạy eval ở bước này** (chủ dự án quyết, 2026-10-31). Eval gọi model thật nên tốn quota free tier và phải chờ lâu, trong khi Bước 6 không đổi prompt, tool hay cách model quyết định. `turn()` mà `evals/run.py` gọi chỉ có thêm tham số tuỳ chọn `on_step`, nên mốc ở §3 của lộ trình vẫn đúng. Đây là ngoại lệ của quy tắc "từ Bước 4 trở đi, mỗi bước thêm case eval và chạy lại". `uv run pytest` vẫn chạy `tests/test_evals.py` (offline), đủ để biết code eval còn chạy.
- **Thay hẳn endpoint** `POST /agent/chats/{id}/messages` bằng bản stream, không giữ bản JSON (chủ dự án quyết, 2026-10-31). Các endpoint khác giữ JSON. Endpoint duyệt cũng không stream: thường nó không gọi model, chỉ khi giữ chỗ thất bại mới chạy một lượt, mà trường hợp này hiếm.
- **Chỉ stream bước tool, không stream token** của câu trả lời cuối (chủ dự án quyết, 2026-10-31).
- Mỗi bước tool có **một sự kiện, gửi khi bước đó xong**, mang đúng dòng trace `{tool, args, status}`. Không có sự kiện "bắt đầu", khác với lộ trình: tool chỉ gọi backend vài chục ms, thời gian chờ nằm ở các lần gọi model, và dòng "Trợ lý đang xử lý…" đã cho khách biết đang chờ.
- Định dạng stream: mỗi sự kiện là một dòng `data: <JSON>` rồi một dòng trống. Có hai loại: `{"type": "step", "tool", "args", "status"}`, và cuối cùng `{"type": "turn", "turn": {reply, trace, cards}}`. `trace` của `turn` bằng đúng các sự kiện `step` theo thứ tự.
- Lỗi hạ tầng giữa chừng (model quá tải, hết ngân sách, lặp, 401 hay 403) vẫn kết thúc bằng sự kiện `turn` có thẻ bàn giao, vì `turn()` đã lo việc này. Lượt hỏng vì lỗi khác (lỗi code, sai key) thì stream đóng mà không có `turn`, service ghi traceback, giống lỗi 500 trước đây. Trang thấy stream đóng mà chưa có `turn` thì báo "Trợ lý đang không hoạt động. Hãy thử lại sau."
- Lỗi trước khi stream vẫn trả JSON với mã cũ: 404 khi không tìm thấy chat (trang dựa vào mã này để tạo chat mới và gửi lại), 422 khi tin rỗng.
- `Chat.lock` là `RLock`: lấy và nhả phải trong cùng một luồng, và luồng chạy lượt giữ lock suốt lượt. Không lấy lock trong generator, vì Starlette gọi `next()` của generator đồng bộ trên các luồng khác nhau của threadpool.
- Comment, tin nhắn lỗi và tài liệu viết tiếng Việt.

## Review Focus

1. **Sự kiện bị cắt giữa hai chunk**, kể cả giữa hai byte của một chữ có dấu: trang vẫn đọc đúng từng sự kiện. Test: Task 3, `agent-stream.spec.ts`.
2. **Stream đóng mà không có `turn`** (lượt hỏng, mất mạng, tắt service giữa chừng): trang báo lỗi, các bước tạm biến mất, ô nhập mở lại, tin của khách vẫn còn. Test: Task 2 `test_crashed_turn_...`, Task 3 E2E "Stream đóng mà không có câu trả lời".
3. **Khách F5 hoặc đóng tab giữa lượt:** lượt vẫn chạy hết trong luồng riêng, `shown` có đủ lượt, GET đọc lại chờ lock. Test: `test_reading_back_waits_for_the_running_turn` (giữ nguyên) và kiểm bằng tay ở Task 4.
4. **Tool call song song** (LangGraph chạy song song các tool call trong cùng một `AIMessage`): sự kiện không lẫn và không thiếu, đúng thứ tự trace. Test: Task 2 `test_each_tool_step_streams_before_the_turn` so sự kiện với trace; `tool_lock` đã xếp các tool chạy lần lượt.
5. **Chat mất sau khi restart service:** 404 vẫn là JSON trả trước khi stream, nên trang tạo chat mới rồi gửi lại. Test: `test_another_session_cannot_use_the_chat` (giữ nguyên).

## File structure

```
agent/flight_agent/core.py      Harness.on_step, begin_turn(on_step), _execute báo dòng trace (Task 2)
agent/flight_agent/chat.py      turn(..., on_step=None) (Task 2)
agent/flight_agent/app.py       endpoint gửi tin trả StreamingResponse (Task 2)
agent/tests/test_app.py         helper events()/sent(), test mới (Task 2)
agent/README.md                 endpoint gửi tin trả SSE (Task 2)
frontend/src/app/assistant/agent-api.ts        sseEvents, agentStream, Step (Task 3)
frontend/src/app/assistant/assistant-chat.tsx  các bước tạm khi đang chạy (Task 3)
frontend/e2e/agent-stream.spec.ts              unit test sseEvents (Task 3)
frontend/e2e/assistant.spec.ts                 E2E gửi tin qua stream (Task 3)
```

---

### Task 1: Thử stream qua rewrite của Next.js trước

Rủi ro chính của lộ trình: rewrite `/agent/*` có thể gom cả response rồi mới trả. Lớp nén gzip của `next start` (mặc định `compress: true`) cũng có thể giữ chunk lại. Thử trước khi viết code thật. Các file thử nằm ngoài repo, không commit.

- [ ] **Step 1: App thử.** Tạo `sse_spike.py` trong một thư mục tạm: FastAPI có `POST /agent/spike` trả `StreamingResponse` với `media_type="text/event-stream"`, từ một generator đồng bộ gửi `data: {i}\n\n` 3 lần, mỗi lần cách nhau `time.sleep(1)`.

- [ ] **Step 2: Script đọc chunk.** Tạo `observe.mjs` cùng thư mục: `fetch(process.argv[2], { method: "POST", headers: { "Accept-Encoding": "gzip, deflate, br" } })` (giống header trình duyệt gửi), in `content-encoding` của response, rồi in `+<ms>` và nội dung từng chunk khi duyệt `res.body` bằng `for await`.

- [ ] **Step 3: Chạy qua `npm run dev`.** Tắt service agent thật nếu đang chạy, rồi chạy `cd agent && uv run uvicorn --app-dir <thư mục tạm> sse_spike:app --port 8000`. Chạy `cd frontend && npm run dev`, sau đó `node <thư mục tạm>/observe.mjs http://localhost:3000/agent/spike`.
      Expected: 3 chunk, cách nhau khoảng 1000 ms.

- [ ] **Step 4: Chạy qua `next start`.** `cd frontend && npm run build && npx next start -p 3100`, rồi `node <thư mục tạm>/observe.mjs http://localhost:3100/agent/spike`.
      Expected: 3 chunk, cách nhau khoảng 1000 ms.

- [ ] **Step 5: Chỉ làm nếu Step 3 hoặc 4 bị gom.** Thêm `headers={"Cache-Control": "no-cache, no-transform"}` vào `StreamingResponse` của app thử (middleware nén bỏ qua response có `no-transform`) rồi chạy lại cả hai bước. Vẫn bị gom thì dùng phương án dự phòng: Route Handler `frontend/src/app/agent/chats/[chatId]/messages/route.ts` tự `fetch` tới `${process.env.AGENT_URL ?? "http://localhost:8000"}/agent/chats/{chatId}/messages`, chuyển tiếp `cookie`, `x-xsrf-token`, `content-type` và body, rồi trả `new Response(res.body, { status: res.status, headers })`. Đồng thời chuyển rewrite `/agent/:path*` trong `next.config.ts` sang nhóm `fallback`, vì rewrite trả dạng mảng chạy trước dynamic route và sẽ che Route Handler này. Thử lại đường dự phòng bằng `observe.mjs` trước khi sang Task 2.

- [ ] **Step 6: Ghi kết quả.** Thêm mục "Kết quả thử stream" ở cuối plan này: dev và start có gom không, `content-encoding` là gì, cần header hay phương án dự phòng. Task 2 Step 3 dùng kết quả này.

- [ ] **Step 7: Commit.** Plan này kèm kết quả thử: `docs(agent): plan Bước 6, kết quả thử stream qua rewrite`

### Task 2: Service stream từng bước tool

**Files:** Modify `agent/flight_agent/core.py`, `agent/flight_agent/chat.py`, `agent/flight_agent/app.py`, `agent/tests/test_app.py`, `agent/README.md`.

**Interfaces:**

- Produces: `Harness.on_step: Callable[[dict], None] | None` (đặt lại mỗi lượt); `Harness.begin_turn(backend, today, on_step=None)`; `turn(chat, model, text, backend, today, on_step=None) -> dict`; endpoint gửi tin trả các sự kiện `step` và `turn` như mục Global Constraints.

- [ ] **Step 1: Viết test.** Trong `agent/tests/test_app.py`, thêm `import json`, `import httpx`, `import openai` và hai helper:

```python
def events(r) -> list[dict]:
    assert r.status_code == 200 and r.headers["content-type"].startswith("text/event-stream")
    return [json.loads(block.removeprefix("data: ")) for block in r.text.split("\n\n") if block]


def sent(c, chat, text, headers) -> dict:
    """Gửi tin, trả kết quả của lượt: sự kiện cuối của stream."""
    last = events(c.post(f"/agent/chats/{chat}/messages", json={"text": text}, headers=headers))[-1]
    assert last["type"] == "turn"
    return last["turn"]
```

Các test đang đọc `.json()` của endpoint gửi tin (`test_full_flow_...`, `test_owner_reads_back_...`, `test_a_new_proposal_...`, `test_failed_approval_...`, `test_edit_closes_...`) chuyển sang `sent(c, chat, "<tin>", h)`. Test `test_crashed_turn_...` tạo app bằng `client(...)`, không cần `raise_server_exceptions=False` nữa, và thay dòng kiểm 500 bằng `assert "turn" not in [e["type"] for e in events(r)]`. Phần kiểm GET đọc lại của test này giữ nguyên. Thêm:

```python
def test_each_tool_step_streams_before_the_turn():
    c = client(FakeBackend(), *hold_flow())
    h = {"Cookie": COOKIE}
    chat = c.post("/agent/chats", headers=h).json()["chatId"]
    ev = events(c.post(f"/agent/chats/{chat}/messages", json={"text": "Đặt vé"}, headers=h))
    assert [e["type"] for e in ev] == ["step", "step", "step", "turn"]
    assert [{k: v for k, v in e.items() if k != "type"} for e in ev[:-1]] == ev[-1]["turn"]["trace"]


def test_llm_outage_mid_stream_ends_with_a_handoff():
    err = openai.APITimeoutError(request=httpx.Request("POST", "http://llm"))
    c = client(FakeBackend(), call("set_constraints", **ROUTE), err)
    h = {"Cookie": COOKIE}
    chat = c.post("/agent/chats", headers=h).json()["chatId"]
    ev = events(c.post(f"/agent/chats/{chat}/messages", json={"text": "Đặt vé"}, headers=h))
    assert [e["type"] for e in ev] == ["step", "turn"]
    assert ev[-1]["turn"]["cards"][-1]["type"] == "handoff"
```

- [ ] **Step 2: Chạy test.** `cd agent && uv run pytest tests/test_app.py`
      Expected: các test dùng `events()` hoặc `sent()` FAIL ở assert `content-type` (endpoint còn trả `application/json`), các test khác PASS.

- [ ] **Step 3: Cài đặt.**
  - `core.py`: thêm trường `on_step: Callable[[dict], None] | None = None` vào nhóm "Đặt lại mỗi lượt" của `Harness`, comment "báo từng dòng trace cho trang (stream SSE)". `begin_turn` nhận thêm `on_step=None` và gán vào trường này. Trong `_execute`, ngay sau `self.trace.append(...)`, nếu có `on_step` thì gọi với đúng dict vừa thêm. `_execute` chạy dưới `tool_lock`, nên sự kiện ra đúng thứ tự trace.
  - `chat.py`: `turn` nhận thêm `on_step=None` và truyền vào `h.begin_turn`. `approve` giữ nguyên: lượt nó tự gọi không stream.
  - `app.py`: endpoint gửi tin (thêm `import json`, `logging`, `queue`, `threading`, `StreamingResponse`; `log = logging.getLogger(__name__)`). Nếu Task 1 cần header thì thêm `headers={"Cache-Control": "no-cache, no-transform"}`:

```python
    @app.post("/agent/chats/{chat_id}/messages")
    def send(chat_id: str, message: Message, request: Request):
        chat = chat_of(request, chat_id)            # 404 trả JSON trước khi stream
        back, day, events = backend(request), today(), queue.Queue()

        # Lượt chạy trong luồng riêng: RLock phải nhả trên đúng luồng đã lấy nó, còn generator bên dưới
        # bị Starlette gọi trên nhiều luồng khác nhau. Khách đóng trang thì lượt vẫn chạy hết và ghi vào shown.
        def run():
            try:
                with chat.lock:
                    chat.shown.append({"from": "user", "text": message.text})
                    out = turn(chat, model, message.text, back, day,
                               on_step=lambda t: events.put({"type": "step", **t}))
                    chat.shown.append({"from": "agent", "turn": out})
                events.put({"type": "turn", "turn": out})
            except Exception:
                log.exception("Lượt chat hỏng")     # stream đóng không có "turn", trang báo lỗi
            finally:
                events.put(None)

        threading.Thread(target=run, daemon=True).start()

        def stream():
            while (event := events.get()) is not None:
                yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"

        return StreamingResponse(stream(), media_type="text/event-stream")
```

Nếu Task 1 phải dùng phương án dự phòng thì làm Route Handler và sửa `next.config.ts` trong task này, và commit cùng.

- [ ] **Step 4: Chạy lại.** `cd agent && uv run pytest`
      Expected: tất cả PASS, kể cả `tests/test_evals.py`.

- [ ] **Step 5: README.** Trong `agent/README.md` ghi: endpoint gửi tin trả `text/event-stream`, mỗi bước tool xong là một sự kiện `step`, cuối cùng là `turn`; lượt hỏng thì stream đóng mà không có `turn`.

- [ ] **Step 6: Commit.** `feat(agent): stream từng bước tool của lượt chat bằng SSE`

### Task 3: Trang hiện dần các bước

**Files:** Modify `frontend/src/app/assistant/agent-api.ts`, `frontend/src/app/assistant/assistant-chat.tsx`, `frontend/e2e/assistant.spec.ts`. Create `frontend/e2e/agent-stream.spec.ts`.

**Interfaces:**

- Consumes: các sự kiện của Task 2.
- Produces: `export type Step = Turn["trace"][number]`; `export type StreamEvent = ({ type: "step" } & Step) | { type: "turn"; turn: Turn }`; `export async function* sseEvents(body: ReadableStream<Uint8Array>): AsyncGenerator<StreamEvent>`; `export async function agentStream(path: string, body: unknown, onStep: (step: Step) => void): Promise<Turn>`.

- [ ] **Step 1: Viết unit test.** `frontend/e2e/agent-stream.spec.ts`, import `sseEvents` từ `../src/app/assistant/agent-api` (Playwright đọc `paths` của tsconfig, nên import `@/lib/api` bên trong vẫn chạy). Test "sự kiện bị cắt giữa hai chunk, kể cả giữa một chữ có dấu": mã hoá chuỗi `data: {"type":"step","tool":"search_flights","args":{},"status":"ok"}\n\ndata: {"type":"turn","turn":{"reply":"Đặt xong","trace":[],"cards":[]}}\n\n` bằng `TextEncoder`, cắt thành 3 chunk: byte 0–19, từ byte 20 tới giữa hai byte của "Đ" (`bytes.indexOf(0xc4) + 1`), và phần còn lại. Đưa vào một `ReadableStream` rồi gom kết quả của `sseEvents`. `expect` bằng đúng hai object đã mã hoá.

- [ ] **Step 2: Chạy test.** `cd frontend && npx playwright test agent-stream`
      Expected: FAIL vì `sseEvents` chưa có.

- [ ] **Step 3: `agent-api.ts`.** Tách phần `fetch` và xử lý lỗi của `agentFetch` thành `agentResponse(method, path, headers, body): Promise<Response>`, trả response khi `ok` và ném `AgentError` như hiện tại. `agentFetch` đổi thành `(await agentResponse(...)).json()`. Tách phần dựng header của `agentPost` (Content-Type, `X-XSRF-TOKEN`) thành hàm dùng chung cho `agentStream`. `sseEvents` đọc bằng `body.pipeThrough(new TextDecoderStream()).getReader()`, không dùng `for await` trên `ReadableStream` vì Safari chưa hỗ trợ. Hàm giữ phần chưa đủ một sự kiện, tách theo `"\n\n"`, bỏ tiền tố `"data: "` rồi `JSON.parse`. `agentStream` gọi `onStep` cho mỗi `step` và trả `turn` khi nhận được. Đọc stream lỗi, hoặc stream hết mà chưa có `turn`, thì ném `new AgentError(0, DOWN)`.

- [ ] **Step 4: `assistant-chat.tsx`.** Thêm state `steps: Step[]` cho các bước của lượt đang chạy. Tách thẻ `ul` hiện trace thành component `Steps({ steps, label })` và dùng ở hai chỗ: trace của mỗi lượt (`label="Các bước đã chạy"`), và các bước tạm ngay trên dòng "Trợ lý đang xử lý…" khi `steps.length > 0` (`label="Các bước đang chạy"`). `send()` dùng `agentStream(..., (s) => setSteps((list) => [...list, s]))` cho cả lần gửi đầu và lần gửi lại sau 404. `run()` gọi `setSteps([])` trong `finally`, cùng nhịp với `setEntries`, nên các bước tạm và trace của lượt không hiện cùng lúc.

- [ ] **Step 5: E2E.** Trong `frontend/e2e/assistant.spec.ts`:
  - Helper `proposal()` còn dạng thẻ của MVP (`segments` và `passenger` nằm thẳng trong thẻ), trong khi `ProposalCard` đọc `journeys` và `passengers`, nên dựng thẻ sẽ lỗi. Đổi theo kiểu `Card` proposal trong `agent-api.ts`: `journeys` gồm một chiều `OUTBOUND` với `segments` cũ, `fare: "Phổ thông"`, `fares` là 1 người lớn giá 1_850_000, `baggage: null`; `passengers` có `type: "ADULT"`; `discount: null`.
  - Helper `sse(...events)` nối các `data: ${JSON.stringify(e)}\n\n`.
  - Test "Gửi tin: hiện các bước rồi câu trả lời": route `**/agent/chats` trả `{ status: 201, json: { chatId: "new" } }`, route `**/agent/chats/new/messages` trả `{ contentType: "text/event-stream", body: sse(step search_flights ok, turn có reply "Bấm Giữ chỗ nhé.", trace là bước đó, cards [proposal("p1", "VN122")]) }`. Điền ô "Tin nhắn cho trợ lý", bấm "Gửi". Expect: thấy "Bấm Giữ chỗ nhé.", `search_flights({}) → ok` có đúng 1 lần, có 1 nút "Giữ chỗ".
  - Test "Stream đóng mà không có câu trả lời thì báo lỗi": như trên nhưng body chỉ có sự kiện `step`. Expect: thấy "Trợ lý đang không hoạt động. Hãy thử lại sau.", `search_flights({}) → ok` có 0 lần, ô nhập enabled, tin của khách vẫn hiện.

- [ ] **Step 6: Chạy test.** `cd frontend && npx playwright test agent-stream assistant` (tự bật `npm run dev`, không cần backend), rồi `npm run lint && npm run build`.
      Expected: tất cả PASS, lint và build không lỗi.

- [ ] **Step 7: Commit.** `feat(assistant): hiện dần các bước tool trong lúc trợ lý chạy`

### Task 4: Kiểm bằng tay và cập nhật lộ trình

- [ ] **Step 1: Bằng tay** (backend profile `dev`, service agent với model thật, `npm run dev`; tốn khoảng 5–10 request model). Gõ "Đặt giúp mình 1 vé SGN đi Đà Nẵng thứ 6 này, dưới 2 triệu": các bước `set_constraints`, `search_flights` hiện dần trước câu trả lời. Gửi một tin khác rồi F5 khi các bước đang hiện: trang đợi lượt xong rồi dựng lại đủ lượt, không lỗi.
      Expected: đúng như trên.

- [ ] **Step 2: Lộ trình.** Trong `2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md`:
  - §1: đánh dấu cột "Xong" của Bước 6 và gắn link plan này vào cột "Bước".
  - Mục Bước 6: thêm dòng "**Đã quyết (chủ dự án, 2026-10-31).**" ghi bốn điều: thay hẳn endpoint gửi tin; không stream token; mỗi bước tool một sự kiện khi bước xong, không có sự kiện bắt đầu; không chạy eval vì bước này không đổi hành vi model, eval tốn quota và thời gian chờ.
  - §3: không thêm dòng, vì không có lần chạy eval mới.

- [ ] **Step 3: Commit.** `docs(agent): xong Bước 6 của lộ trình trợ lý`

## Ngoài phạm vi

- Stream endpoint duyệt (`approve`), stream từng token.
- Safari giữ chunk cho tới khi nhận đủ 1024 byte (tài liệu streaming của Next.js), nên vài sự kiện đầu có thể hiện cùng lúc. Chấp nhận được: E2E và demo đều chạy trên Chromium.

## Kết quả thử stream

Thử ngày 2026-10-31 (Task 1), 3 sự kiện cách nhau 1 giây, đọc bằng `observe.mjs` với `Accept-Encoding: gzip, deflate, br`:

| Đường đi                        | Không có header                          | `Cache-Control: no-cache, no-transform` |
| ------------------------------- | ---------------------------------------- | --------------------------------------- |
| Gọi thẳng service (cổng 8000)   | stream dần                               | —                                       |
| `npm run dev` (cổng 3000)       | gom: một chunk ở +3006 ms, `gzip`        | stream dần (+0, +997, +1998 ms)         |
| `next start` (cổng 3100)        | gom: một chunk ở +3006 ms, `gzip`        | stream dần (+1, +997, +1998 ms)         |

Thủ phạm là lớp nén gzip của Next.js chứ không phải rewrite. Task 2 thêm `headers={"Cache-Control": "no-cache, no-transform"}`; không cần phương án dự phòng.
