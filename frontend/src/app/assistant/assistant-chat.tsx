"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import SiteHeader from "@/components/site-header";
import { Notice, pillLine, pillPrimary, textLink } from "@/components/form";
import { clock, DIRECTION_LABEL, dmy, PASSENGER_LABEL } from "@/lib/booking";
import { vnd } from "../flights/itineraries";
import {
  AgentError,
  agentGet,
  agentPost,
  agentStream,
  replyParts,
  type Card,
  type ChatView,
  type Entry,
  type Step,
  type Turn,
} from "./agent-api";

/** chatId của tab này: F5 thì đọc lại chat, đóng tab thì hết. */
const CHAT_KEY = "assistant-chat";

function proposalIds(entries: Entry[]) {
  return entries.flatMap((e) =>
    e.from === "agent"
      ? e.turn.cards.flatMap((c) =>
          c.type === "proposal" ? [c.proposalId] : [],
        )
      : [],
  );
}

const chip =
  "rounded-inner border border-line bg-surface px-11 py-4 text-caption";

function short(args: Record<string, unknown>) {
  const text = JSON.stringify(args);
  return text.length > 80 ? text.slice(0, 79) + "…" : text;
}

function Steps({ steps, label }: { steps: Step[]; label: string }) {
  return (
    <ul
      aria-label={label}
      className="grid gap-2 font-mono text-badge text-muted"
    >
      {steps.map((t, j) => (
        <li key={j}>{`${t.tool}(${short(t.args)}) → ${t.status}`}</li>
      ))}
    </ul>
  );
}

function ConstraintsCard({
  card,
}: {
  card: Extract<Card, { type: "constraints" }>;
}) {
  return (
    <div className="grid gap-8 card p-16">
      <p className="text-caption font-semibold text-muted">Ràng buộc</p>
      <ul className="flex flex-wrap gap-8">
        <li className={chip}>{`${card.origin} → ${card.destination}`}</li>
        <li className={chip}>
          {card.returnDate
            ? `Đi ${dmy(card.date)}, về ${dmy(card.returnDate)}`
            : dmy(card.date)}
        </li>
        <li className={chip}>
          {(
            [
              ["ADULT", card.adults],
              ["CHILD", card.children],
              ["INFANT", card.infants],
            ] as const
          )
            .filter(([type, n]) => n > 0 || type === "ADULT")
            .map(([type, n]) => `${n} ${PASSENGER_LABEL[type].toLowerCase()}`)
            .join(", ")}
        </li>
        <li className={chip}>
          {card.departBefore
            ? `${card.returnDate ? "Chiều đi cất cánh" : "Cất cánh"} trước ${card.departBefore}`
            : "Giờ bay: không giới hạn"}
        </li>
        <li className={chip}>
          {card.maxPrice !== null
            ? `Tối đa ${vnd(card.maxPrice)}`
            : "Giá: không giới hạn"}
        </li>
      </ul>
    </div>
  );
}

function ProposalCard({
  card,
  open,
  busy,
  onHold,
  onEdit,
}: {
  card: Extract<Card, { type: "proposal" }>;
  open: boolean;
  busy: boolean;
  onHold: () => void;
  onEdit: () => void;
}) {
  return (
    <div className="grid gap-12 card-hi p-16">
      <p className="text-caption font-semibold text-muted">Duyệt giữ chỗ</p>
      {card.journeys.map((j) => (
        <section
          key={j.direction}
          aria-label={DIRECTION_LABEL[j.direction]}
          className="grid gap-8"
        >
          <p className="font-bold">{DIRECTION_LABEL[j.direction]}</p>
          <ul className="grid gap-4">
            {j.segments.map((s) => (
              <li key={s.flightNumber + s.depart} className="font-semibold">
                {`${s.flightNumber}: ${s.from} ${clock(s.depart)} → ${s.to} ${clock(s.arrive)}, ${dmy(s.depart)}`}
              </li>
            ))}
          </ul>
          <dl className="grid gap-4 text-caption sm:grid-cols-[120px_minmax(0,1fr)]">
            <dt className="text-muted">Gói giá</dt>
            <dd>{j.fare}</dd>
            <dt className="text-muted">Giá vé</dt>
            <dd>
              <ul className="grid gap-2">
                {j.fares.map((f) => (
                  <li key={f.type}>
                    {`${f.count} ${PASSENGER_LABEL[f.type].toLowerCase()} × ${vnd(f.unitPrice)} = ${vnd(f.total)}`}
                  </li>
                ))}
              </ul>
            </dd>
            {j.baggage && (
              <>
                <dt className="text-muted">Hành lý ký gửi</dt>
                <dd>{`${j.baggage.count} kiện ${j.baggage.weightKg} kg = ${vnd(j.baggage.total)}`}</dd>
              </>
            )}
          </dl>
        </section>
      ))}
      <dl className="grid gap-4 text-caption sm:grid-cols-[120px_minmax(0,1fr)]">
        <dt className="text-muted">Hành khách</dt>
        <dd>
          <ul className="grid gap-2">
            {card.passengers.map((p, i) => (
              <li key={i}>
                {`${p.lastName} ${p.firstName}, ${p.gender === "MALE" ? "Nam" : "Nữ"}, ${PASSENGER_LABEL[p.type]}, sinh ${dmy(p.dateOfBirth)}`}
              </li>
            ))}
          </ul>
        </dd>
        {card.discount && (
          <>
            <dt className="text-muted">Giảm giá</dt>
            <dd>{`${card.discount.code}: −${vnd(card.discount.amount)}`}</dd>
          </>
        )}
        <dt className="text-muted">Liên hệ</dt>
        <dd>{`${card.contact.name}, ${card.contact.email}, ${card.contact.phone}`}</dd>
      </dl>
      <p className="text-subheading font-bold text-accent">
        {vnd(card.totalAmount)}
      </p>
      {open && (
        <div className="flex flex-wrap gap-8">
          <button
            type="button"
            className={pillPrimary}
            disabled={busy}
            onClick={onHold}
          >
            Giữ chỗ
          </button>
          <button
            type="button"
            className={pillLine}
            disabled={busy}
            onClick={onEdit}
          >
            Sửa
          </button>
        </div>
      )}
    </div>
  );
}

function CardView(props: {
  card: Card;
  open: boolean;
  busy: boolean;
  onHold: () => void;
  onEdit: () => void;
}) {
  const { card } = props;
  switch (card.type) {
    case "constraints":
      return <ConstraintsCard card={card} />;
    case "proposal":
      return <ProposalCard {...props} card={card} />;
    case "held":
      return (
        <div className="grid gap-12 card p-16">
          <p className="font-semibold">
            {`Đã giữ chỗ ${card.code}, hết hạn lúc ${clock(card.holdExpiresAt)} ${dmy(card.holdExpiresAt)}.`}
          </p>
          <Link
            href={`/bookings/${card.code}`}
            className={`${pillPrimary} justify-self-start`}
          >
            Thanh toán VNPay
          </Link>
        </div>
      );
    case "handoff":
      return (
        <div className="grid gap-8 card p-16">
          <p className="font-semibold">{card.question}</p>
          <details className="text-caption text-muted">
            <summary className="cursor-pointer">Đã làm và đã thử</summary>
            <ul className="mt-8 grid gap-4">
              {card.doneSoFar.map((line, i) => (
                <li key={i}>{line}</li>
              ))}
            </ul>
            <ul className="mt-8 grid gap-4 font-mono">
              {card.tried.map((line, i) => (
                <li key={i}>{line}</li>
              ))}
            </ul>
          </details>
        </div>
      );
  }
}

/** Trang /assistant: chat với trợ lý. Thẻ duyệt chỉ giữ chỗ khi khách bấm "Giữ chỗ"; thanh toán vẫn ở C-09. */
export default function AssistantChat() {
  const chatId = useRef<string | null>(null);
  const [entries, setEntries] = useState<Entry[]>([]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Các bước tool của lượt đang chạy, nhận dần qua stream.
  const [steps, setSteps] = useState<Step[]>([]);
  // Thẻ duyệt đã bấm "Giữ chỗ" hoặc "Sửa" thì không còn nút.
  const [closed, setClosed] = useState<ReadonlySet<string>>(new Set());

  const latest = proposalIds(entries).at(-1);

  // Đọc sessionStorage sau khi hydrate. Thẻ duyệt không phải đề xuất còn hiệu lực thì dựng lại không có nút.
  useEffect(() => {
    const id = sessionStorage.getItem(CHAT_KEY);
    // StrictMode chạy effect hai lần: lần hai không đọc lại để khỏi ghi đè tin khách vừa gửi.
    if (!id || chatId.current) return;
    chatId.current = id;
    void run(async () => {
      try {
        const view = await agentGet<ChatView>(`/chats/${id}`);
        setEntries(view.entries);
        setClosed(
          new Set(
            proposalIds(view.entries).filter((p) => p !== view.proposalId),
          ),
        );
      } catch (e) {
        // Service đã restart hoặc phiên đăng nhập đã đổi: mở chat trống, không báo lỗi.
        if (!(e instanceof AgentError && e.status === 404)) throw e;
        sessionStorage.removeItem(CHAT_KEY);
        chatId.current = null;
      }
    });
  }, []);

  async function newChat() {
    const { chatId: id } = await agentPost<{ chatId: string }>("/chats");
    chatId.current = id;
    sessionStorage.setItem(CHAT_KEY, id);
    return id;
  }

  async function run(work: () => Promise<Turn | void>) {
    setBusy(true);
    setError(null);
    try {
      const turn = await work();
      if (turn) setEntries((list) => [...list, { from: "agent", turn }]);
    } catch (e) {
      setError(
        e instanceof AgentError ? e.message : "Đã có lỗi xảy ra. Hãy thử lại.",
      );
    } finally {
      // Cùng nhịp với setEntries: các bước tạm không hiện cùng trace của lượt.
      setSteps([]);
      setBusy(false);
    }
  }

  function send(event: FormEvent) {
    event.preventDefault();
    const message = text.trim();
    if (!message || busy) return;
    setText("");
    setEntries((list) => [...list, { from: "user", text: message }]);
    const onStep = (s: Step) => setSteps((list) => [...list, s]);
    void run(async () => {
      const id = chatId.current ?? (await newChat());
      try {
        return await agentStream(
          `/chats/${id}/messages`,
          { text: message },
          onStep,
        );
      } catch (e) {
        // Service khởi động lại thì chat cũ mất: tạo chat mới và gửi lại một lần.
        if (!(e instanceof AgentError && e.status === 404)) throw e;
        const fresh = await newChat();
        return agentStream(
          `/chats/${fresh}/messages`,
          { text: message },
          onStep,
        );
      }
    });
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
        return await agentPost<Turn>(
          `/chats/${chatId.current}/proposals/${proposalId}/approve`,
        );
      } finally {
        close(proposalId);
      }
    });
  }

  return (
    <div className="min-h-dvh">
      <SiteHeader />
      <main className="mx-auto grid max-w-page gap-28 px-16 pt-16 pb-80 sm:px-38">
        <h1 className="text-heading-lg font-bold">Trợ lý đặt vé</h1>
        {entries.length === 0 && (
          <p className="text-muted">
            Gõ yêu cầu bằng lời, ví dụ “Đặt giúp mình 1 vé SGN đi Đà Nẵng thứ 6
            này, bay trước 12h, dưới 2 triệu”. Trợ lý đặt được vé một chiều hoặc
            khứ hồi, hạng phổ thông, cho nhiều hành khách, kèm hành lý ký gửi và
            mã giảm giá, và chỉ giữ chỗ khi bạn bấm “Giữ chỗ”.
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
                    (c) => c.type === "held" || c.type === "handoff",
                  ) && (
                    <p className="whitespace-pre-line">
                      {replyParts(entry.turn.reply).map((part, k) =>
                        typeof part === "string" ? (
                          part
                        ) : (
                          <Link key={k} href={part.href} className={textLink}>
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
                      card.type === "proposal" &&
                      card.proposalId === latest &&
                      !closed.has(card.proposalId)
                    }
                    busy={busy}
                    onHold={() =>
                      card.type === "proposal" && hold(card.proposalId)
                    }
                    onEdit={() =>
                      card.type === "proposal" && edit(card.proposalId)
                    }
                  />
                ))}
              </li>
            ),
          )}
        </ol>
        {steps.length > 0 && <Steps steps={steps} label="Các bước đang chạy" />}
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
            id="assistant-input"
            value={text}
            onChange={(e) => setText(e.target.value)}
            disabled={busy}
            maxLength={1000}
            autoComplete="off"
            placeholder="Nhắn cho trợ lý…"
            className="min-h-40 min-w-0 flex-1 rounded-inner border border-line-strong bg-surface px-14 py-8 transition-[border-color,box-shadow] duration-200 focus:border-accent focus:shadow-[0_0_0_3px_var(--color-accent-soft)] focus:outline-none"
          />
          <button
            type="submit"
            className={pillPrimary}
            disabled={busy || !text.trim()}
          >
            Gửi
          </button>
        </form>
      </main>
    </div>
  );
}
