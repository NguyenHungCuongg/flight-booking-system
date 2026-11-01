"use client";

import { useSearch } from "./search-context";

/** Một tham số Admin đổi được, đọc từ SearchProvider; "…" khi chưa tải xong. `per` chia giá trị, VD 60 để đổi phút ra giờ. */
export function Setting({ name, per = 1 }: { name: string; per?: number }) {
  const value = useSearch().settings?.[name];
  return value === undefined ? "…" : (value / per).toLocaleString("vi-VN");
}
