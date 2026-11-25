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
  const runTokenRef = useRef(0);
  const abortRef = useRef<AbortController | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [chats, setChats] = useState<ChatSummary[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(true);
  const [entries, setEntries] = useState<Entry[]>([]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const [holding, setHolding] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
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

  const stopStream = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setStreaming(false);
  }, []);

  const resetChat = useCallback(() => {
    runTokenRef.current += 1;
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
  }, [stopStream]);

  const run = useCallback(
    async (
      work: (token: number, signal?: AbortSignal) => Promise<Turn | void>,
      opts?: { abortable?: boolean; streaming?: boolean },
    ) => {
      stopStream();
      const token = ++runTokenRef.current;
      let controller: AbortController | undefined;
      if (opts?.abortable) {
        controller = new AbortController();
        abortRef.current = controller;
      }
      setStreaming(Boolean(opts?.streaming));
      setBusy(true);
      setError(null);
      try {
        const turn = await work(token, controller?.signal);
        if (token !== runTokenRef.current) return;
        if (turn) setEntries((list) => [...list, { from: "agent", turn }]);
      } catch (e) {
        if (token !== runTokenRef.current) return;
        if (e instanceof DOMException && e.name === "AbortError") return;
        if (redirectIfExpired(e)) return;
        setError(
          e instanceof AgentError
            ? e.message
            : "Đã có lỗi xảy ra. Hãy thử lại.",
        );
      } finally {
        if (token === runTokenRef.current) {
          abortRef.current = null;
          setStreaming(false);
          // Cùng nhịp với setEntries: các bước tạm không hiện cùng trace của lượt.
          setSteps([]);
          setBusy(false);
        }
      }
    },
    [redirectIfExpired, stopStream],
  );

  const openChat = useCallback(
    async (id: string) => {
      await run(
        async (token, signal) => {
          try {
            const view = await agentGet<ChatView>(`/chats/${id}`, signal);
            if (token !== runTokenRef.current) return;
            chatId.current = id;
            setActiveId(id);
            sessionStorage.setItem(CHAT_KEY, id);
            setEntries(view.entries);
            setClosed(
              new Set(
                proposalIds(view.entries).filter((p) => p !== view.proposalId),
              ),
            );
          } catch (e) {
            if (token !== runTokenRef.current) return;
            // Chat đã hết hạn hoặc thuộc tài khoản khác: mở chat trống, không báo lỗi.
            if (!(e instanceof AgentError && e.status === 404)) throw e;
            if (sessionStorage.getItem(CHAT_KEY) === id) {
              sessionStorage.removeItem(CHAT_KEY);
            }
            if (chatId.current === id) {
              chatId.current = null;
              setActiveId(null);
              setEntries([]);
              setClosed(new Set());
            }
            setChats((list) => list.filter((c) => c.chatId !== id));
          }
        },
        { abortable: true },
      );
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

  async function newChat(token: number, signal?: AbortSignal) {
    const { chatId: id } = await agentPost<{ chatId: string }>(
      "/chats",
      undefined,
      signal,
    );
    if (token !== runTokenRef.current || signal?.aborted) {
      throw new DOMException("Aborted", "AbortError");
    }
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
    void run(
      async (token, signal) => {
        const onStep = (s: Step) => {
          if (token === runTokenRef.current) setSteps((list) => [...list, s]);
        };
        const id = chatId.current ?? (await newChat(token, signal));
        try {
          const result = await agentStream(
            `/chats/${id}/messages`,
            { text: message },
            onStep,
            signal,
          );
          if (token === runTokenRef.current) void refreshChats();
          return result;
        } catch (e) {
          // Service khởi động lại hoặc chat bị xoá: tạo chat mới và gửi lại một lần.
          if (!(e instanceof AgentError && e.status === 404)) throw e;
          const fresh = await newChat(token, signal);
          const result = await agentStream(
            `/chats/${fresh}/messages`,
            { text: message },
            onStep,
            signal,
          );
          if (token === runTokenRef.current) void refreshChats();
          return result;
        }
      },
      { abortable: true, streaming: true },
    );
  }

  async function deleteChat(id: string) {
    if (holding || deletingId === id) return;
    if (chatId.current === id) resetChat();
    setDeletingId(id);
    try {
      await agentDelete(`/chats/${id}`);
      setChats((list) => list.filter((c) => c.chatId !== id));
    } catch (e) {
      if (redirectIfExpired(e)) return;
      if (e instanceof AgentError && e.status === 404) {
        setChats((list) => list.filter((c) => c.chatId !== id));
        return;
      }
      setError(
        e instanceof AgentError ? e.message : "Không xoá được cuộc trò chuyện.",
      );
    } finally {
      setDeletingId((cur) => (cur === id ? null : cur));
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
    const id = chatId.current;
    if (!id) return;
    setHolding(true);
    void run(async (token) => {
      try {
        const out = await agentPost<Turn>(
          `/chats/${id}/proposals/${proposalId}/approve`,
        );
        if (token === runTokenRef.current) void refreshChats();
        return out;
      } finally {
        setHolding(false);
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
          holding={holding}
          deletingId={deletingId}
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
