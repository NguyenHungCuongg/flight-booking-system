"""Service FastAPI: Next.js chuyển /agent/* tới đây. Chạy bằng
uv run uvicorn flight_agent.app:create_app --factory --port 8000
"""
import os
from collections.abc import Callable
from datetime import datetime, timedelta, timezone

import httpx
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel, Field

from flight_agent.backend import Backend
from flight_agent.chat import Chat, Chats, approve, turn
from flight_agent.model import make_model

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

    @app.post("/agent/chats/{chat_id}/messages")
    def send(chat_id: str, message: Message, request: Request):
        return turn(chat_of(request, chat_id), model, message.text, backend(request), today())

    @app.post("/agent/chats/{chat_id}/proposals/{proposal_id}/approve")
    def approve_proposal(chat_id: str, proposal_id: str, request: Request):
        chat = chat_of(request, chat_id)
        try:
            return approve(chat, model, proposal_id, backend(request), today())
        except LookupError:
            raise HTTPException(404, "Thẻ này đã hết hiệu lực.")

    return app
