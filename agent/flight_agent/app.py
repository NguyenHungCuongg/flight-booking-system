"""Service FastAPI: Next.js chuyển /agent/* tới đây. Chạy bằng
uv run uvicorn flight_agent.app:create_app --factory --port 8000
"""
import json
import logging
import os
import queue
import threading
from collections.abc import Callable
from datetime import datetime, timedelta, timezone

import httpx
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from flight_agent.backend import Backend
from flight_agent.chat import Chat, Chats, approve, turn
from flight_agent.model import make_model

log = logging.getLogger(__name__)
VN = timezone(timedelta(hours=7))   # Việt Nam không có giờ mùa hè; không dùng zoneinfo vì Windows thiếu tzdata


def vn_today(now: datetime | None = None) -> str:
    return (now or datetime.now(timezone.utc)).astimezone(VN).date().isoformat()


class Message(BaseModel):
    text: str = Field(min_length=1, max_length=1000)


def create_app(model=None, backend_url: str | None = None, transport: httpx.BaseTransport | None = None,
               today: Callable[[], str] = vn_today) -> FastAPI:
    load_dotenv()
    model = model or make_model()
    backend_url = backend_url or os.environ.get("BACKEND_URL", "http://localhost:8080")
    chats = Chats()
    app = FastAPI(title="SkyLine agent")

    def backend(request: Request) -> Backend:
        # Cookie và X-XSRF-TOKEN chuyển tiếp nguyên từ request của trình duyệt (spec §5.2, §5.3).
        return Backend(backend_url, request.headers.get("cookie", ""), request.headers.get("x-xsrf-token"),
                       transport)

    def chat_of(request: Request, chat_id: str) -> Chat:
        chat = chats.get(chat_id, request.cookies.get("JSESSIONID", ""))
        if chat is None:
            raise HTTPException(404, "Không tìm thấy cuộc trò chuyện.")
        return chat

    # Endpoint dạng def (không async): FastAPI chạy trong threadpool, Chat.lock lo phần chạy song song.
    @app.post("/agent/chats", status_code=201)
    def create_chat(request: Request):
        session = request.cookies.get("JSESSIONID")
        if not session:
            raise HTTPException(401, "Bạn cần đăng nhập.")
        return {"chatId": chats.create(session).id}

    @app.get("/agent/chats/{chat_id}")
    def read_chat(chat_id: str, request: Request):
        chat = chat_of(request, chat_id)
        with chat.lock:     # đang có lượt chạy thì chờ lượt xong
            proposal = chat.harness.proposal
            # Bản sao: FastAPI chuyển sang JSON sau khi đã nhả lock.
            return {"entries": list(chat.shown), "proposalId": proposal.id if proposal else None}

    # Tin của khách chỉ ghi ở đây: tin "[Hệ thống]" mà approve() tự gửi cho model không hiện ra.
    # Ghi trước khi chạy lượt: lượt hỏng thì trang vẫn đang hiện tin này.
    # Trả text/event-stream: mỗi bước tool xong là một sự kiện "step", cuối cùng là "turn".
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

    @app.post("/agent/chats/{chat_id}/proposals/{proposal_id}/approve")
    def approve_proposal(chat_id: str, proposal_id: str, request: Request):
        chat = chat_of(request, chat_id)
        with chat.lock:
            try:
                out = approve(chat, model, proposal_id, backend(request), today())
            except LookupError:
                raise HTTPException(404, "Thẻ này đã hết hiệu lực.")
            chat.shown.append({"from": "agent", "turn": out})
            return out

    return app
