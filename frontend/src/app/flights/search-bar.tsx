"use client";

import { useState, type ReactNode } from "react";
import { pillLine } from "@/components/form";
import "@/components/landing/landing.css";
import SearchDock from "@/components/landing/search-dock";
import {
  SearchProvider,
  type SearchForm,
} from "@/components/landing/search-context";

/**
 * Ô tìm chuyến của C-02, dùng lại SearchDock của trang chủ. Có `summary` thì chỉ hiện dòng tóm tắt và nút
 * "Đổi tìm kiếm"; null (chưa có điều kiện hoặc điều kiện sai) thì ô tìm mở sẵn. Trang gắn `key` theo URL nên mỗi lần
 * tìm xong component mount lại: form lấy điều kiện mới và ô tìm đóng lại.
 */
export default function SearchBar({
  initial,
  summary,
}: {
  initial: Partial<SearchForm>;
  summary: ReactNode | null;
}) {
  const [open, setOpen] = useState(summary === null);
  return (
    <div className="grid gap-16">
      {summary !== null && (
        <div className="flex flex-wrap items-center justify-between gap-16 card p-16">
          {summary}
          <button
            type="button"
            className={pillLine}
            aria-expanded={open}
            onClick={() => setOpen((o) => !o)}
          >
            Đổi tìm kiếm
          </button>
        </div>
      )}
      {open && (
        <SearchProvider initial={initial}>
          <div className="sk t-auto">
            <SearchDock />
          </div>
        </SearchProvider>
      )}
    </div>
  );
}
