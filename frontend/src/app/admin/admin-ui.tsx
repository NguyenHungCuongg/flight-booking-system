"use client";

import type { ReactNode } from "react";
import SiteHeader from "@/components/site-header";
import { pillDark, pillLine } from "@/components/form";

export const tableBox = "overflow-x-auto";
export const th =
  "py-11 pr-16 text-left text-caption font-bold border-b border-line whitespace-nowrap";
export const td =
  "py-11 pr-16 text-left text-caption border-b border-line align-top";

/**
 * Khung giao diện chuẩn cho tất cả các màn hình Admin (A-01–A-10).
 */
export function AdminPage({
  children,
}: {
  children: ReactNode;
}) {
  return (
    <div className="min-h-dvh bg-paper text-ink">
      <SiteHeader area="admin" />
      <main className="mx-auto grid max-w-page gap-38 px-16 pt-22 pb-119 sm:px-38">
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
    <div className="flex border-b border-line gap-22">
      {tabs.map((tab) => {
        const active = tab.key === activeKey;
        return (
          <button
            key={tab.key}
            type="button"
            onClick={() => onChange(tab.key)}
            className={`pb-11 text-caption font-bold transition-colors ${
              active
                ? "border-b-2 border-ink text-ink"
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
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title}
      className="fixed inset-0 z-50 flex items-center justify-center bg-deep-ink/40 p-16 backdrop-blur-xs"
    >
      <div className="w-full max-w-560 border border-line-strong bg-paper p-22 shadow-lg">
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
      </div>
    </div>
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
      <div className="grid gap-22">
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
            className={pillDark}
          >
            {isLoading ? "Đang xử lý..." : confirmLabel}
          </button>
        </div>
      </div>
    </AdminModal>
  );
}
