"use client";

import { useState } from "react";
import { pillLine } from "@/components/form";
import { clock, dmy } from "@/lib/booking";
import type { ChatSummary } from "./agent-api";

/** Danh sách lịch sử các cuộc trò chuyện đã lưu của tài khoản hiện tại (không dùng new Date/Date.now khi render). */
export default function ChatHistory({
  chats,
  activeId,
  loading,
  busy,
  holding,
  deletingId,
  onSelect,
  onNew,
  onDelete,
}: {
  chats: ChatSummary[];
  activeId: string | null;
  loading: boolean;
  busy: boolean;
  holding: boolean;
  deletingId: string | null;
  onSelect: (chatId: string) => void;
  onNew: () => void;
  onDelete: (chatId: string) => void;
}) {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <aside
      aria-label="Lịch sử trò chuyện"
      className="card grid gap-12 self-start p-14"
    >
      <div className="flex items-center justify-between gap-8">
        <h2 className="text-caption font-bold text-ink">Lịch sử trò chuyện</h2>
        <button
          type="button"
          className="rounded-inner px-8 py-4 text-badge font-semibold text-muted hover:bg-tint hover:text-ink lg:hidden"
          aria-expanded={mobileOpen}
          onClick={() => setMobileOpen((v) => !v)}
        >
          {mobileOpen ? "Ẩn" : `Xem (${chats.length})`}
        </button>
      </div>
      <button
        type="button"
        className={`${pillLine} w-full`}
        onClick={onNew}
      >
        Cuộc trò chuyện mới
      </button>
      <div className={mobileOpen ? "grid gap-6" : "hidden gap-6 lg:grid"}>
        {loading ? (
          <p className="text-badge text-muted">Đang tải lịch sử…</p>
        ) : chats.length === 0 ? (
          <p className="text-badge text-muted">
            Chưa có cuộc trò chuyện nào được lưu.
          </p>
        ) : (
          <ul className="grid gap-4">
            {chats.map((c) => {
              const active = c.chatId === activeId;
              return (
                <li
                  key={c.chatId}
                  className={`flex items-start gap-4 rounded-inner border transition-colors duration-150 ${
                    active
                      ? "border-accent/30 bg-accent-soft"
                      : "border-transparent hover:bg-tint"
                  }`}
                >
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => {
                      setMobileOpen(false);
                      onSelect(c.chatId);
                    }}
                    className="grid min-w-0 flex-1 gap-2 px-10 py-8 text-left disabled:opacity-60"
                  >
                    <span
                      className={`truncate text-caption ${
                        active ? "font-bold text-accent" : "font-medium text-ink"
                      }`}
                    >
                      {c.title}
                    </span>
                    <span className="text-badge text-muted">
                      {`${clock(c.updatedAt)} · ${dmy(c.updatedAt)}`}
                    </span>
                  </button>
                  <button
                    type="button"
                    aria-label={`Xoá cuộc trò chuyện ${c.title}`}
                    disabled={holding || deletingId === c.chatId}
                    onClick={() => onDelete(c.chatId)}
                    className="shrink-0 rounded-inner px-8 py-8 text-badge font-semibold text-muted transition-colors hover:text-danger disabled:opacity-60"
                  >
                    Xoá
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </aside>
  );
}
