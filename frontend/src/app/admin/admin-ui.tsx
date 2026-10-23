"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import SiteHeader from "@/components/site-header";
import { pillPrimary, pillLine } from "@/components/form";

export const tableBox =
  "card overflow-x-auto [&_tbody_tr]:transition-colors [&_tbody_tr:hover]:bg-tint/60";
export const th =
  "bg-tint/60 px-8 py-9 text-left text-table font-semibold whitespace-nowrap text-ink-2 first:pl-16 last:pr-16";
export const td =
  "border-t border-line px-8 py-9 text-left text-table align-top first:pl-16 last:pr-16";

/**
 * Khung giao diện chuẩn cho tất cả các màn hình Admin (A-01–A-10).
 */
export function AdminPage({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-dvh text-ink">
      <SiteHeader area="admin" />
      <main className="mx-auto grid max-w-page gap-28 px-16 pt-16 pb-80 sm:px-38">
        {children}
      </main>
    </div>
  );
}

/**
 * Thanh chuyển Tab dùng cho Chi tiết hãng (A-03) và Báo cáo (A-10).
 */
export function AdminTabs({
  tabs,
  activeKey,
  onChange,
}: {
  tabs: readonly { key: string; label: string }[];
  activeKey: string;
  onChange: (key: string) => void;
}) {
  return (
    <div role="tablist" className="flex border-b border-line gap-16">
      {tabs.map((tab) => {
        const active = tab.key === activeKey;
        return (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(tab.key)}
            className={`pb-11 text-caption font-bold transition-colors ${
              active
                ? "border-b-2 border-accent text-ink"
                : "text-muted hover:text-ink"
            }`}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}

/**
 * Modal Popup chuẩn cho các form thêm / sửa / xác nhận.
 */
export function AdminModal({
  title,
  isOpen,
  onClose,
  children,
}: {
  title: string;
  isOpen: boolean;
  onClose: () => void;
  children: ReactNode;
}) {
  if (!isOpen) return null;
  return (
    <ModalDialog title={title} onClose={onClose}>
      {children}
    </ModalDialog>
  );
}

/** `<dialog>` gốc mở bằng showModal(): focus vào trong, nền phía sau không bấm được, Escape đóng; đóng thì trả focus. */
function ModalDialog({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  // Nút đã mở hộp thoại, đọc một lần lúc mở (chỉ render ở trình duyệt, sau một thao tác của người dùng).
  const [opener] = useState(() => document.activeElement as HTMLElement | null);
  useEffect(() => {
    const dialog = ref.current;
    dialog?.showModal();
    return () => {
      dialog?.close();
      opener?.focus();
    };
  }, [opener]);

  return (
    <dialog
      ref={ref}
      aria-label={title}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      className="m-auto w-[calc(100%-32px)] max-w-560 rounded-card bg-surface p-16 text-ink shadow-card-hover backdrop:bg-deep-ink/50 backdrop:backdrop-blur-xs"
    >
      <div className="flex items-center justify-between border-b border-line pb-16 mb-22">
        <h2 className="text-subheading font-bold">{title}</h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="Đóng"
          className="text-muted hover:text-ink text-caption font-bold"
        >
          ✕
        </button>
      </div>
      {children}
    </dialog>
  );
}

/**
 * Dialog xác nhận hai bước (dành cho Huỷ chuyến bay, Xoá dữ liệu, Khoá tài khoản).
 */
export function ConfirmModal({
  title,
  message,
  confirmLabel = "Xác nhận",
  isOpen,
  isLoading,
  onConfirm,
  onClose,
}: {
  title: string;
  message: string;
  confirmLabel?: string;
  isOpen: boolean;
  isLoading?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <AdminModal title={title} isOpen={isOpen} onClose={onClose}>
      <div className="grid gap-16">
        <p className="text-caption text-ink leading-relaxed">{message}</p>
        <div className="flex justify-end gap-11">
          <button
            type="button"
            onClick={onClose}
            disabled={isLoading}
            className={pillLine}
          >
            Huỷ bỏ
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={isLoading}
            className={pillPrimary}
          >
            {isLoading ? "Đang xử lý..." : confirmLabel}
          </button>
        </div>
      </div>
    </AdminModal>
  );
}
