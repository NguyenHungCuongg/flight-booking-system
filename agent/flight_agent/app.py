"""Service FastAPI: Next.js chuyển /agent/* tới đây. Chạy bằng
uv run uvicorn flight_agent.app:create_app --factory --port 8000
"""
import json
import logging
import os
import queue
import threading
import time
from collections import deque
from collections.abc import Callable
from datetime import datetime, timedelta, timezone

import httpx
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Request, Response
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from flight_agent.backend import Backend, BackendError
from flight_agent.chat import Chat, Chats, approve, turn
from flight_agent.model import make_model

log = logging.getLogger(__name__)
VN = timezone(timedelta(hours=7))   # Việt Nam không có giờ mùa hè; không dùng zoneinfo vì Windows thiếu tzdata


def vn_today(now: datetime | None = None) -> str:
    return (now or datetime.now(timezone.utc)).astimezone(VN).date().isoformat()


class Message(BaseModel):
    text: str = Field(min_length=1, max_length=1000)


def create_app(model=None, backend_url: str | None = None, transport: httpx.BaseTransport | None = None,
               today: Callable[[], str] = vn_today, rate_limit: int = 15,
               rate_window: float = 60.0, db_path: str | None = None) -> FastAPI:
    load_dotenv()
    model = model or make_model()
    backend_url = backend_url or os.environ.get("BACKEND_URL", "http://localhost:8080")
    chats = Chats(db_path or os.environ.get("AGENT_DB_PATH", ":memory:"))
    rate_buckets: dict[str, deque[float]] = {}
    rate_lock = threading.Lock()
    app = FastAPI(title="SkyLine agent")

    def check_rate_limit(owner: str) -> None:
        now = time.monotonic()
        with rate_lock:
            bucket = rate_buckets.setdefault(owner, deque())
            while bucket and now - bucket[0] > rate_window:
                bucket.popleft()
            if len(bucket) >= rate_limit:
                raise HTTPException(429, "Bạn gửi tin nhắn quá nhanh. Vui lòng chờ ít giây rồi thử lại.")
            bucket.append(now)

    def backend(request: Request) -> Backend:
        # Cookie và X-XSRF-TOKEN chuyển tiếp nguyên từ request của trình duyệt (spec §5.2, §5.3).
        return Backend(backend_url, request.headers.get("cookie", ""), request.headers.get("x-xsrf-token"),
                       transport)

    def require_owner(request: Request) -> str:
        """Xác thực phiên với backend qua GET /api/me và trả về định danh tài khoản `user:<id>`."""
        if not request.cookies.get("JSESSIONID"):
            raise HTTPException(401, "Bạn cần đăng nhập.")
        try:
            me = backend(request).get("/api/me")
        except BackendError as e:
            if e.status == 401:
                raise HTTPException(401, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
            if e.status == 403:
                raise HTTPException(403, "Trợ lý chỉ dùng cho tài khoản khách hàng.")
            raise HTTPException(503, "Không kết nối được hệ thống. Hãy thử lại sau.")
        return f"user:{me['id']}"

    def chat_of(request: Request, chat_id: str, owner: str | None = None) -> Chat:
        chat = chats.get(chat_id, owner or require_owner(request))
        if chat is None:
            raise HTTPException(404, "Không tìm thấy cuộc trò chuyện.")
        return chat

    # Endpoint dạng def (không async): FastAPI chạy trong threadpool, Chat.lock lo phần chạy song song.
    @app.get("/agent/chats")
    def list_chats(request: Request):
        return {"chats": chats.list_for(require_owner(request))}

    @app.post("/agent/chats", status_code=201)
    def create_chat(request: Request):
        return {"chatId": chats.create(require_owner(request)).id}

    @app.get("/agent/chats/{chat_id}")
    def read_chat(chat_id: str, request: Request):
        chat = chat_of(request, chat_id)
        with chat.lock:     # đang có lượt chạy thì chờ lượt xong
            proposal = chat.harness.proposal
            # Bản sao: FastAPI chuyển sang JSON sau khi đã nhả lock.
            return {"entries": list(chat.shown), "proposalId": proposal.id if proposal else None}

    @app.delete("/agent/chats/{chat_id}", status_code=204)
    def delete_chat(chat_id: str, request: Request):
        if not chats.delete(chat_id, require_owner(request)):
            raise HTTPException(404, "Không tìm thấy cuộc trò chuyện.")
        return Response(status_code=204)

    # Tin của khách chỉ ghi ở đây: tin "[Hệ thống]" mà approve() tự gửi cho model không hiện ra.
    # Ghi trước khi chạy lượt: lượt hỏng thì trang vẫn đang hiện tin này.
    # Trả text/event-stream: mỗi bước tool xong là một sự kiện "step", cuối cùng là "turn".
    @app.post("/agent/chats/{chat_id}/messages")
    def send(chat_id: str, message: Message, request: Request):
        owner = require_owner(request)
        chat = chat_of(request, chat_id, owner)     # 404 trả JSON trước khi stream
        check_rate_limit(owner)
        back, day, events = backend(request), today(), queue.Queue()

        # Lượt chạy trong luồng riêng: RLock phải nhả trên đúng luồng đã lấy nó, còn generator bên dưới
        # bị Starlette gọi trên nhiều luồng khác nhau. Khách đóng trang thì lượt vẫn chạy hết và ghi vào shown.
        def run():
            try:
                with chat.lock:
                    chat.shown.append({"from": "user", "text": message.text})
                    chats.save(chat)
                    out = turn(chat, model, message.text, back, day,
                               on_step=lambda t: events.put({"type": "step", **t}), store=chats)
                    chat.shown.append({"from": "agent", "turn": out})
                    chats.save(chat)
                events.put({"type": "turn", "turn": out})
            except Exception:
                log.exception("Lượt chat hỏng")     # stream đóng không có "turn", trang báo lỗi
            finally:
                events.put(None)

        threading.Thread(target=run, daemon=True).start()

        def stream():
            while (event := events.get()) is not None:
                yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"

        # no-transform: lớp gzip của Next.js không nén nên không gom các sự kiện lại (plan Bước 6, Task 1).
        return StreamingResponse(stream(), media_type="text/event-stream",
                                 headers={"Cache-Control": "no-cache, no-transform"})

    # Khách bấm "Sửa": đề xuất hết hiệu lực, F5 thì thẻ không mở lại.
    @app.post("/agent/chats/{chat_id}/proposals/{proposal_id}/dismiss")
    def dismiss_proposal(chat_id: str, proposal_id: str, request: Request):
        chat = chat_of(request, chat_id)
        with chat.lock:
            if chat.harness.proposal and chat.harness.proposal.id == proposal_id:
                chat.harness.proposal = None
                chats.save(chat)

    @app.post("/agent/chats/{chat_id}/proposals/{proposal_id}/approve")
    def approve_proposal(chat_id: str, proposal_id: str, request: Request):
        chat = chat_of(request, chat_id)
        with chat.lock:
            try:
                out = approve(chat, model, proposal_id, backend(request), today(), store=chats)
            except LookupError:
                raise HTTPException(404, "Thẻ này đã hết hiệu lực.")
            chat.shown.append({"from": "agent", "turn": out})
            chats.save(chat)
            return out

    return app
