"""Một chat = lịch sử tin nhắn + harness, nằm trong bộ nhớ của service.

turn() chạy model; approve() thì không: khi khách bấm "Giữ chỗ", code gửi đúng body đã duyệt.
"""
import json
import logging
import sqlite3
import threading
import time
from dataclasses import asdict, dataclass, field
from pathlib import Path
from uuid import uuid4

import openai
from langchain_core.exceptions import ModelAPIError, ModelConnectionError, ModelRateLimitError, ModelTimeoutError
from langchain_core.messages import AIMessage, HumanMessage, messages_from_dict, messages_to_dict

from flight_agent.backend import Backend, BackendError
from flight_agent.core import Constraints, Harness, Proposal, StopRun
from flight_agent.react import run_turn

CHAT_TTL_SECONDS = 86_400.0     # giữ chat tối đa 24 giờ
MAX_CHATS = 500                 # giới hạn số chat lưu trữ

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
    def __init__(self, db_path: str | Path = ":memory:"):
        self._chats: dict[str, Chat] = {}
        self._db_lock = threading.RLock()
        self._conn = sqlite3.connect(str(db_path), check_same_thread=False)
        with self._db_lock, self._conn:
            self._conn.execute(
                "CREATE TABLE IF NOT EXISTS chats ("
                "id TEXT PRIMARY KEY, session TEXT NOT NULL, updated_at REAL NOT NULL, payload TEXT NOT NULL)"
            )

    def create(self, session: str) -> Chat:
        chat = Chat(uuid4().hex, session)
        self._chats[chat.id] = chat
        self.save(chat)
        return chat

    def save(self, chat: Chat) -> None:
        h = chat.harness
        if h.proposal and h.proposal.is_expired():
            h.proposal = None
        payload = json.dumps({
            "shown": chat.shown,
            "history": messages_to_dict(chat.history),
            "constraints": asdict(h.constraints) if h.constraints else None,
            "found": h.found,
            "proposal": asdict(h.proposal) if h.proposal else None,
            "known_names": {k: list(v) for k, v in h.known_names.items()},
        }, ensure_ascii=False)
        now = time.time()
        with self._db_lock, self._conn:
            self._conn.execute("DELETE FROM chats WHERE updated_at < ?", (now - CHAT_TTL_SECONDS,))
            self._conn.execute(
                "INSERT OR REPLACE INTO chats(id, session, updated_at, payload) VALUES (?, ?, ?, ?)",
                (chat.id, chat.session, now, payload),
            )
            self._conn.execute(
                "DELETE FROM chats WHERE id NOT IN (SELECT id FROM chats ORDER BY updated_at DESC LIMIT ?)",
                (MAX_CHATS,),
            )

    def get(self, chat_id: str, session: str) -> Chat | None:
        now = time.time()
        chat = self._chats.get(chat_id)
        if chat is None:
            with self._db_lock:
                row = self._conn.execute(
                    "SELECT session, updated_at, payload FROM chats WHERE id = ?", (chat_id,)
                ).fetchone()
            if not row or row[0] != session or row[1] < now - CHAT_TTL_SECONDS:
                return None
            data = json.loads(row[2])
            h = Harness(
                constraints=Constraints(**data["constraints"]) if data.get("constraints") else None,
                found=data.get("found") or [],
                proposal=Proposal(**data["proposal"]) if data.get("proposal") else None,
                known_names={k: (v[0], v[1]) for k, v in (data.get("known_names") or {}).items()},
            )
            chat = Chat(chat_id, session, harness=h,
                        history=list(messages_from_dict(data.get("history") or [])),
                        shown=data.get("shown") or [])
            self._chats[chat_id] = chat
        if chat.session != session:
            return None
        if chat.harness.proposal and chat.harness.proposal.is_expired(now):
            chat.harness.proposal = None
        return chat


trace_log = logging.getLogger("flight_agent.trace")


def log_turn(chat_id: str, action: str, harness: Harness, latency_ms: int) -> dict:
    """Ghi một dòng JSON trace cấu trúc sau mỗi lượt, không chứa tham số PII của hành khách."""
    entry = {
        "chat_id": chat_id,
        "action": action,
        "steps": [{"tool": t["tool"], "status": t["status"]} for t in harness.trace],
        "stop_reason": harness.stop_reason,
        "model_calls": harness.model_calls,
        "tokens": harness.tokens_used,
        "latency_ms": latency_ms,
    }
    trace_log.info(json.dumps(entry, ensure_ascii=False))
    return entry


def _result(chat: Chat, reply: str) -> dict:
    return {"reply": reply, "trace": chat.harness.trace, "cards": chat.harness.cards}


def _handoff(chat: Chat, *before) -> dict:
    """Harness đã dừng: thêm thẻ bàn giao, câu hỏi của thẻ là câu trả lời của lượt này."""
    card = chat.harness.handoff()
    chat.harness.cards.append(card)
    chat.history = [*chat.history, *before, AIMessage(card["question"])]
    return _result(chat, card["question"])


def turn(chat: Chat, model, text: str, backend: Backend, today: str, on_step=None,
         store: Chats | None = None) -> dict:
    with chat.lock:
        t0 = time.monotonic()
        h = chat.harness
        h.begin_turn(backend, today, on_step)
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
            out = _handoff(chat, HumanMessage(text))
        else:
            out = _result(chat, reply)
        if store is not None:
            store.save(chat)
        log_turn(chat.id, "turn", h, int((time.monotonic() - t0) * 1000))
        return out


def approve(chat: Chat, model, proposal_id: str, backend: Backend, today: str,
            store: Chats | None = None) -> dict:
    with chat.lock:
        t0 = time.monotonic()
        h = chat.harness
        h.begin_turn(backend, today)
        proposal = h.proposal
        if proposal is not None and proposal.is_expired():
            h.proposal = proposal = None
        if proposal is None or proposal.id != proposal_id:
            raise LookupError(proposal_id)
        h.proposal = None           # trước khi gọi backend: mỗi đề xuất chỉ duyệt được một lần
        if proposal.action == "cancel":
            code = proposal.body["code"]
            try:
                backend.post(f"/api/bookings/{code}/cancel")
                detail = backend.get(f"/api/bookings/{code}")
                done = detail.get("status") == "CANCELLED"
            except BackendError as e:
                if e.status in (401, 403):
                    h.backend_error(e)
                    out = _handoff(chat)
                    if store is not None:
                        store.save(chat)
                    return out
                done = False
            if not done:
                h.held_code = code
                h.stop("hold_mismatch")
                out = _handoff(chat)
                if store is not None:
                    store.save(chat)
                return out
            reply = f"Đã huỷ giữ chỗ {code}."
            chat.history = [*chat.history, AIMessage(reply)]
            h.cards.append({"type": "cancelled", "code": code})
            if store is not None:
                store.save(chat)
            log_turn(chat.id, "approve_cancel", h, int((time.monotonic() - t0) * 1000))
            return _result(chat, reply)
        try:
            created = backend.post("/api/bookings", proposal.body)
        except BackendError as e:
            if e.status in (401, 403):
                h.backend_error(e)
                out = _handoff(chat)
                if store is not None:
                    store.save(chat)
                return out
            return turn(chat, model, f"[Hệ thống] Giữ chỗ thất bại: {e.code}: {e.detail}. "
                                     "Đề xuất chuyến khác nếu còn chuyến thoả ràng buộc.", backend, today, store=store)
        code = created["code"]
        try:
            detail = backend.get(f"/api/bookings/{code}")
            done = h.is_done(detail, proposal)
        except BackendError:
            done = False
        if not done:
            h.held_code = code
            h.stop("hold_mismatch")
            out = _handoff(chat)
            if store is not None:
                store.save(chat)
            return out
        expires = detail["holdExpiresAt"]
        reply = f"Đã giữ chỗ {code}, hết hạn lúc {expires[11:16]} ngày {expires[8:10]}/{expires[5:7]}/{expires[:4]}."
        chat.history = [*chat.history, AIMessage(reply)]
        h.cards.append({"type": "held", "code": code, "holdExpiresAt": expires, "totalAmount": detail["totalAmount"]})
        if store is not None:
            store.save(chat)
        log_turn(chat.id, "approve", h, int((time.monotonic() - t0) * 1000))
        return _result(chat, reply)
