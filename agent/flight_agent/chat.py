"""Một chat = lịch sử tin nhắn + harness, nằm trong bộ nhớ của service.

turn() chạy model; approve() thì không: khi khách bấm "Giữ chỗ", code gửi đúng body đã duyệt.
"""
import threading
from dataclasses import dataclass, field
from uuid import uuid4

import openai
from langchain_core.exceptions import ModelAPIError, ModelConnectionError, ModelRateLimitError, ModelTimeoutError
from langchain_core.messages import AIMessage, HumanMessage

from flight_agent.backend import Backend, BackendError
from flight_agent.core import Harness, StopRun
from flight_agent.react import run_turn

# Lỗi tạm thời của API LLM không phải lỗi của agent. Key hay tên model sai (401/404) vẫn làm crash.
INFRA_ERRORS = (openai.RateLimitError, openai.APITimeoutError, openai.APIConnectionError,
                openai.InternalServerError,
                # bản không phụ thuộc nhà cung cấp (client Gemini ném các lỗi này)
                ModelRateLimitError, ModelTimeoutError, ModelConnectionError, ModelAPIError)


@dataclass
class Chat:
    id: str
    session: str                # JSESSIONID lúc tạo chat
    harness: Harness = field(default_factory=Harness)
    history: list = field(default_factory=list)
    # Các lượt trang đã hiển thị (tin của khách, kết quả mỗi lượt) để dựng lại khung chat khi F5.
    shown: list = field(default_factory=list)
    lock: threading.RLock = field(default_factory=threading.RLock)


class Chats:
    def __init__(self):
        self._chats: dict[str, Chat] = {}

    def create(self, session: str) -> Chat:
        chat = Chat(uuid4().hex, session)
        self._chats[chat.id] = chat
        return chat

    def get(self, chat_id: str, session: str) -> Chat | None:
        chat = self._chats.get(chat_id)
        return chat if chat and chat.session == session else None


def _result(chat: Chat, reply: str) -> dict:
    return {"reply": reply, "trace": chat.harness.trace, "cards": chat.harness.cards}


def _handoff(chat: Chat, *before) -> dict:
    """Harness đã dừng: thêm thẻ bàn giao, câu hỏi của thẻ là câu trả lời của lượt này."""
    card = chat.harness.handoff()
    chat.harness.cards.append(card)
    chat.history = [*chat.history, *before, AIMessage(card["question"])]
    return _result(chat, card["question"])


def turn(chat: Chat, model, text: str, backend: Backend, today: str) -> dict:
    with chat.lock:
        h = chat.harness
        h.begin_turn(backend, today)
        before = h.proposal
        try:
            reply, chat.history = run_turn(h, model, chat.history, text)
        except StopRun:
            pass
        except INFRA_ERRORS:
            h.stop("infra")
        except Exception:
            h.proposal = before     # lượt hỏng không trả thẻ nào, nên đề xuất của nó không được duyệt
            raise
        if h.stop_reason:
            # Lần invoke bị huỷ có thể còn tool call chưa có kết quả: chỉ giữ tin nhắn của khách.
            return _handoff(chat, HumanMessage(text))
        return _result(chat, reply)


def approve(chat: Chat, model, proposal_id: str, backend: Backend, today: str) -> dict:
    with chat.lock:
        h = chat.harness
        h.begin_turn(backend, today)
        proposal = h.proposal
        if proposal is None or proposal.id != proposal_id:
            raise LookupError(proposal_id)
        h.proposal = None           # trước khi gọi backend: mỗi đề xuất chỉ duyệt được một lần
        try:
            created = backend.post("/api/bookings", proposal.body)
        except BackendError as e:
            if e.status in (401, 403):
                h.backend_error(e)
                return _handoff(chat)
            return turn(chat, model, f"[Hệ thống] Giữ chỗ thất bại: {e.code}: {e.detail}. "
                                     "Đề xuất chuyến khác nếu còn chuyến thoả ràng buộc.", backend, today)
        code = created["code"]
        try:
            detail = backend.get(f"/api/bookings/{code}")
            done = h.is_done(detail, proposal)
        except BackendError:
            done = False
        if not done:
            h.held_code = code
            h.stop("hold_mismatch")
            return _handoff(chat)
        expires = detail["holdExpiresAt"]
        reply = f"Đã giữ chỗ {code}, hết hạn lúc {expires[11:16]} ngày {expires[8:10]}/{expires[5:7]}/{expires[:4]}."
        chat.history = [*chat.history, AIMessage(reply)]
        h.cards.append({"type": "held", "code": code, "holdExpiresAt": expires, "totalAmount": detail["totalAmount"]})
        return _result(chat, reply)
