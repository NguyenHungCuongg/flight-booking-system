"use client";

import Link from "next/link";
import { pillLine, pillPrimary, textLink } from "@/components/form";
import { clock, DIRECTION_LABEL, dmy, PASSENGER_LABEL } from "@/lib/booking";
import { vnd } from "../flights/itineraries";
import {
  describeStep,
  replyParts,
  type Card,
  type Entry,
  type Step,
} from "./agent-api";

const chip =
  "rounded-inner border border-line bg-surface px-11 py-4 text-caption";

function short(args: Record<string, unknown>) {
  const text = JSON.stringify(args);
  return text.length > 80 ? text.slice(0, 79) + "…" : text;
}

export function Steps({
  steps,
  label,
  live = false,
}: {
  steps: Step[];
  label: string;
  live?: boolean;
}) {
  return (
    <div className="grid gap-4">
      <ul
        aria-label={label}
        className="grid gap-2 text-caption text-muted"
      >
        {steps.map((t, j) => (
          <li key={j}>{describeStep(t)}</li>
        ))}
      </ul>
      {!live && (
        <details className="text-badge text-muted">
          <summary className="cursor-pointer select-none">
            {`Chi tiết kỹ thuật (${steps.length} bước)`}
          </summary>
          <ul className="mt-6 grid gap-2 font-mono">
            {steps.map((t, j) => (
              <li key={j}>{`${t.tool}(${short(t.args)}) → ${t.status}`}</li>
            ))}
          </ul>
        </details>
      )}
    </div>
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
        {card.departAfter && (
          <li className={chip}>{`Từ ${card.departAfter}`}</li>
        )}
        {card.cabin && card.cabin !== "ECONOMY" && (
          <li className={chip}>{`Hạng ${card.cabin}`}</li>
        )}
        {card.airline && <li className={chip}>{`Hãng ${card.airline}`}</li>}
        {card.nonstop && <li className={chip}>Bay thẳng</li>}
        {card.refundable && <li className={chip}>Hoàn được</li>}
        {card.changeable && <li className={chip}>Đổi được</li>}
      </ul>
    </div>
  );
}

function MemoryUsedCard({
  card,
}: {
  card: Extract<Card, { type: "memory_used" }>;
}) {
  return (
    <div className="grid gap-8 card p-16">
      <p className="text-caption font-semibold text-muted">
        Thông tin hành khách từ đặt chỗ gần đây
      </p>
      <ul className="grid gap-6 text-caption">
        {card.passengers.map((p, i) => (
          <li key={`${p.fullName}-${p.dateOfBirth}-${i}`}>
            {`${p.fullName}, ${p.gender === "MALE" ? "Nam" : "Nữ"}, sinh ${dmy(p.dateOfBirth)} · từ đơn `}
            <Link href={`/bookings/${p.bookingCode}`} className={textLink}>
              {p.bookingCode}
            </Link>
          </li>
        ))}
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

/** Tóm tắt nhanh ngữ cảnh trợ lý đang nắm trong phiên hiện tại (suy ra từ các thẻ đã trả về). */
export function ActiveContextBar({
  entries,
  hasOpenProposal,
}: {
  entries: Entry[];
  hasOpenProposal: boolean;
}) {
  let constraints: Extract<Card, { type: "constraints" }> | null = null;
  let memory: Extract<Card, { type: "memory_used" }> | null = null;
  for (const e of entries) {
    if (e.from !== "agent") continue;
    for (const c of e.turn.cards) {
      if (c.type === "constraints") constraints = c;
      if (c.type === "memory_used") memory = c;
    }
  }
  if (!constraints && !memory && !hasOpenProposal) return null;

  return (
    <section
      aria-label="Ngữ cảnh trợ lý đang nắm"
      className="grid gap-8 rounded-card border border-line bg-tint/60 p-14 text-caption"
    >
      <p className="font-semibold text-muted">
        Trợ lý đang nắm trong phiên này
      </p>
      <ul className="flex flex-wrap gap-8">
        {constraints && (
          <li className={chip}>
            {`${constraints.origin} → ${constraints.destination} · ${
              constraints.returnDate
                ? `${dmy(constraints.date)} – ${dmy(constraints.returnDate)}`
                : dmy(constraints.date)
            }`}
          </li>
        )}
        {constraints && constraints.maxPrice !== null && (
          <li className={chip}>{`Tối đa ${vnd(constraints.maxPrice)}`}</li>
        )}
        {memory &&
          memory.passengers.map((p, i) => (
            <li key={`${p.fullName}-${i}`} className={chip}>
              {`${p.fullName} (từ đơn ${p.bookingCode})`}
            </li>
          ))}
        {hasOpenProposal && (
          <li className="rounded-badge bg-accent-soft px-10 py-4 text-badge font-semibold text-accent">
            Đang chờ bạn duyệt đề xuất
          </li>
        )}
      </ul>
    </section>
  );
}

export function CardView(props: {
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
    case "memory_used":
      return <MemoryUsedCard card={card} />;
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
    case "cancel_proposal":
      return (
        <div className="grid gap-12 card-hi p-16">
          <p className="text-caption font-semibold text-muted">
            Duyệt huỷ giữ chỗ
          </p>
          <p className="font-semibold">{`Đặt chỗ ${card.code} (${vnd(card.totalAmount)})`}</p>
          {card.routes.length > 0 && (
            <ul className="grid gap-4 text-caption">
              {card.routes.map((r, i) => (
                <li key={i}>{r}</li>
              ))}
            </ul>
          )}
          {props.open && (
            <div className="flex flex-wrap gap-8">
              <button
                type="button"
                className={pillPrimary}
                disabled={props.busy}
                onClick={props.onHold}
              >
                Huỷ giữ chỗ
              </button>
              <button
                type="button"
                className={pillLine}
                disabled={props.busy}
                onClick={props.onEdit}
              >
                Đóng
              </button>
            </div>
          )}
        </div>
      );
    case "cancelled":
      return (
        <div className="grid gap-12 card p-16">
          <p className="font-semibold">{`Đã huỷ giữ chỗ ${card.code}.`}</p>
        </div>
      );
    case "handoff":
      return (
        <div className="grid gap-8 card p-16">
          <p className="font-semibold">
            {replyParts(card.question).map((part, k) =>
              typeof part === "string" ? (
                part
              ) : (
                <Link key={k} href={part.href} className={textLink}>
                  {part.href}
                </Link>
              ),
            )}
          </p>
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
