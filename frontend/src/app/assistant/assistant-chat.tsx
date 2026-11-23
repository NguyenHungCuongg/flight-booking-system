"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import SiteHeader from "@/components/site-header";
import { Notice, pillLine, pillPrimary, textLink } from "@/components/form";
import {
  AgentError,
  agentDelete,
  agentGet,
  agentPost,
  agentStream,
  replyParts,
  type ChatSummary,
  type ChatView,
  type Entry,
  type Step,
  type Turn,
} from "./agent-api";
import { ActiveContextBar, CardView, Steps } from "./chat-cards";
import ChatHistory from "./chat-history";

/** chatId đang mở trong tab này: F5 thì đọc lại đúng cuộc trò chuyện đó. */
const CHAT_KEY = "assistant-chat";

function proposalIds(entries: Entry[]) {
  return entries.flatMap((e) =>
    e.from === "agent"
      ? e.turn.cards.flatMap((c) =>
          c.type === "proposal" || c.type === "cancel_proposal"
            ? [c.proposalId]
            : [],
        )
      : [],
  );
}

/** Trang /assistant: chat với trợ lý kèm lịch sử trò chuyện và minh bạch ngữ cảnh. Thanh toán vẫn ở C-09. */
export default function AssistantChat() {
  const router = useRouter();
  const chatId = useRef<string | null>(null);
  const initStarted = useRef(false);
  const abortRef = useRef<AbortController | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [chats, setChats] = useState<ChatSummary[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(true);
  const [entries, setEntries] = useState<Entry[]>([]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Các bước tool của lượt đang chạy, nhận dần qua stream.
  const [steps, setSteps] = useState<Step[]>([]);
  // Thẻ duyệt đã bấm "Giữ chỗ" hoặc "Sửa" thì không còn nút.
  const [closed, setClosed] = useState<ReadonlySet<string>>(new Set());

  const latest = proposalIds(entries).at(-1);
  const hasOpenProposal = Boolean(latest && !closed.has(latest));

  const redirectIfExpired = useCallback(
    (e: unknown) => {
      if (e instanceof AgentError && e.status === 401) {
        router.replace("/login?next=%2Fassistant");
        return true;
      }
      return false;
    },
    [router],
  );

  const refreshChats = useCallback(async () => {
    try {
      const data = await agentGet<{ chats: ChatSummary[] }>("/chats");
      setChats(data.chats);
    } catch (e) {
      if (redirectIfExpired(e)) return;
      // Service agent tắt hoặc lỗi mạng: giữ danh sách trống, không làm hỏng trang.
    } finally {
      setLoadingHistory(false);
    }
  }, [redirectIfExpired]);

  function stopStream() {
    abortRef.current?.abort();
    abortRef.current = null;
    setStreaming(false);
  }

  function resetChat() {
    stopStream();
    sessionStorage.removeItem(CHAT_KEY);
    chatId.current = null;
    setActiveId(null);
    setEntries([]);
    setText("");
    setSteps([]);
    setClosed(new Set());
    setError(null);
    setBusy(false);
    inputRef.current?.focus();
  }

  const run = useCallback(
    async (work: () => Promise<Turn | void>) => {
      setBusy(true);
      setError(null);
      try {
        const turn = await work();
        if (turn) setEntries((list) => [...list, { from: "agent", turn }]);
      } catch (e) {
        if (e instanceof DOMException && e.name === "AbortError") return;
        if (redirectIfExpired(e)) return;
        setError(
          e instanceof AgentError
            ? e.message
            : "Đã có lỗi xảy ra. Hãy thử lại.",
        );
      } finally {
        abortRef.current = null;
        setStreaming(false);
        // Cùng nhịp với setEntries: các bước tạm không hiện cùng trace của lượt.
        setSteps([]);
        setBusy(false);
      }
    },
    [redirectIfExpired],
  );

  const openChat = useCallback(
    async (id: string) => {
      stopStream();
      chatId.current = id;
      setActiveId(id);
      sessionStorage.setItem(CHAT_KEY, id);
      await run(async () => {
        try {
          const view = await agentGet<ChatView>(`/chats/${id}`);
          setEntries(view.entries);
          setClosed(
            new Set(
              proposalIds(view.entries).filter((p) => p !== view.proposalId),
            ),
          );
        } catch (e) {
          // Chat đã hết hạn hoặc thuộc tài khoản khác: mở chat trống, không báo lỗi.
          if (!(e instanceof AgentError && e.status === 404)) throw e;
          sessionStorage.removeItem(CHAT_KEY);
          chatId.current = null;
          setActiveId(null);
          setEntries([]);
          setClosed(new Set());
          setChats((list) => list.filter((c) => c.chatId !== id));
        }
      });
    },
    [run],
  );

  // Nạp danh sách lịch sử và dựng lại chat đang mở (nếu có) một lần sau khi hydrate.
  useEffect(() => {
    if (initStarted.current) return;
    initStarted.current = true;
    void refreshChats();
    const id = sessionStorage.getItem(CHAT_KEY);
    if (id && !chatId.current) {
      void openChat(id);
    }
  }, [openChat, refreshChats]);

  async function newChat() {
    const { chatId: id } = await agentPost<{ chatId: string }>("/chats");
    chatId.current = id;
    setActiveId(id);
    sessionStorage.setItem(CHAT_KEY, id);
    return id;
  }

  function send(event: FormEvent) {
    event.preventDefault();
    const message = text.trim();
    if (!message || busy) return;
    setText("");
    setEntries((list) => [...list, { from: "user", text: message }]);
    const onStep = (s: Step) => setSteps((list) => [...list, s]);
    const controller = new AbortController();
    abortRef.current = controller;
    setStreaming(true);
    void run(async () => {
      const id = chatId.current ?? (await newChat());
      try {
        const result = await agentStream(
          `/chats/${id}/messages`,
          { text: message },
          onStep,
          controller.signal,
        );
        void refreshChats();
        return result;
      } catch (e) {
        // Service khởi động lại hoặc chat bị xoá: tạo chat mới và gửi lại một lần.
        if (!(e instanceof AgentError && e.status === 404)) throw e;
        const fresh = await newChat();
        const result = await agentStream(
          `/chats/${fresh}/messages`,
          { text: message },
          onStep,
          controller.signal,
        );
        void refreshChats();
        return result;
      }
    });
  }

  async function deleteChat(id: string) {
    if (busy) return;
    try {
      await agentDelete(`/chats/${id}`);
      setChats((list) => list.filter((c) => c.chatId !== id));
      if (chatId.current === id) resetChat();
    } catch (e) {
      if (redirectIfExpired(e)) return;
      if (e instanceof AgentError && e.status === 404) {
        setChats((list) => list.filter((c) => c.chatId !== id));
        if (chatId.current === id) resetChat();
        return;
      }
      setError(
        e instanceof AgentError ? e.message : "Không xoá được cuộc trò chuyện.",
      );
    }
  }

  function close(proposalId: string) {
    setClosed((set) => new Set(set).add(proposalId));
  }

  // "Sửa": báo service bỏ đề xuất để F5 không mở lại thẻ. Lỗi thì bỏ qua, thẻ trên trang đã đóng.
  function edit(proposalId: string) {
    close(proposalId);
    agentPost(`/chats/${chatId.current}/proposals/${proposalId}/dismiss`).catch(
      () => {},
    );
  }

  function hold(proposalId: string) {
    void run(async () => {
      try {
        const out = await agentPost<Turn>(
          `/chats/${chatId.current}/proposals/${proposalId}/approve`,
        );
        void refreshChats();
        return out;
      } finally {
        close(proposalId);
      }
    });
  }

  return (
    <div className="min-h-dvh">
      <SiteHeader />
      <main className="mx-auto grid max-w-page gap-24 px-16 pt-16 pb-80 sm:px-38 lg:grid-cols-[260px_minmax(0,1fr)] lg:items-start">
        <ChatHistory
          chats={chats}
          activeId={activeId}
          loading={loadingHistory}
          busy={busy}
          onSelect={(id) => void openChat(id)}
          onNew={resetChat}
          onDelete={(id) => void deleteChat(id)}
        />
        <section className="grid min-w-0 gap-20">
          <h1 className="text-heading-lg font-bold">Trợ lý đặt vé</h1>
          <ActiveContextBar
            entries={entries}
            hasOpenProposal={hasOpenProposal}
          />
          {entries.length === 0 && (
            <p className="text-muted">
              Gõ yêu cầu bằng lời, ví dụ “Đặt giúp mình 1 vé SGN đi Đà Nẵng thứ
              6 này, bay trước 12h, dưới 2 triệu”. Trợ lý đặt được vé một chiều
              hoặc khứ hồi, chọn hạng ghế, cho nhiều hành khách, kèm hành lý ký
              gửi và mã giảm giá, và chỉ giữ chỗ khi bạn bấm “Giữ chỗ”.
            </p>
          )}
          <ol aria-label="Cuộc trò chuyện" className="grid gap-16">
            {entries.map((entry, i) =>
              entry.from === "user" ? (
                <li
                  key={i}
                  className="max-w-[80%] justify-self-end rounded-inner bg-accent-soft px-14 py-10"
                >
                  {entry.text}
                </li>
              ) : (
                <li key={i} className="grid gap-12">
                  {/* Thẻ "Đã giữ chỗ" và thẻ bàn giao đã nói đúng câu trả lời, không lặp lại. */}
                  {entry.turn.reply &&
                    !entry.turn.cards.some(
                      (c) =>
                        c.type === "held" ||
                        c.type === "cancelled" ||
                        c.type === "handoff",
                    ) && (
                      <p className="whitespace-pre-line">
                        {replyParts(entry.turn.reply).map((part, k) =>
                          typeof part === "string" ? (
                            part
                          ) : (
                            <Link
                              key={k}
                              href={part.href}
                              className={textLink}
                            >
                              {part.href}
                            </Link>
                          ),
                        )}
                      </p>
                    )}
                  {entry.turn.trace.length > 0 && (
                    <Steps steps={entry.turn.trace} label="Các bước đã chạy" />
                  )}
                  {entry.turn.cards.map((card, j) => (
                    <CardView
                      key={j}
                      card={card}
                      open={
                        (card.type === "proposal" ||
                          card.type === "cancel_proposal") &&
                        card.proposalId === latest &&
                        !closed.has(card.proposalId)
                      }
                      busy={busy}
                      onHold={() =>
                        (card.type === "proposal" ||
                          card.type === "cancel_proposal") &&
                        hold(card.proposalId)
                      }
                      onEdit={() =>
                        (card.type === "proposal" ||
                          card.type === "cancel_proposal") &&
                        edit(card.proposalId)
                      }
                    />
                  ))}
                </li>
              ),
            )}
          </ol>
          {steps.length > 0 && (
            <Steps steps={steps} label="Các bước đang chạy" live />
          )}
          {busy && (
            <p role="status" className="text-caption text-muted">
              Trợ lý đang xử lý…
            </p>
          )}
          {error && <Notice>{error}</Notice>}
          <form onSubmit={send} className="flex gap-8">
            <label htmlFor="assistant-input" className="sr-only">
              Tin nhắn cho trợ lý
            </label>
            <input
              ref={inputRef}
              id="assistant-input"
              value={text}
              onChange={(e) => setText(e.target.value)}
              disabled={busy}
              maxLength={1000}
              autoComplete="off"
              placeholder="Nhắn cho trợ lý…"
              className="min-h-40 min-w-0 flex-1 rounded-inner border border-line-strong bg-surface px-14 py-8 transition-[border-color,box-shadow] duration-200 focus:border-accent focus:shadow-[0_0_0_3px_var(--color-accent-soft)] focus:outline-none"
            />
            {busy && streaming ? (
              <button
                type="button"
                className={pillLine}
                onClick={stopStream}
              >
                Dừng
              </button>
            ) : (
              <button
                type="submit"
                className={
                  busy
                    ? pillPrimary
                    : pillPrimary.replace(
                        "disabled:cursor-wait",
                        "disabled:cursor-not-allowed",
                      )
                }
                disabled={busy || !text.trim()}
              >
                Gửi
              </button>
            )}
          </form>
        </section>
      </main>
    </div>
  );
}
