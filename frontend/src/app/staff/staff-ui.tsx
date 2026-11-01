"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import type { ReactNode } from "react";
import SiteHeader from "@/components/site-header";
import { textLink } from "@/components/form";
import {
  DEFAULT_PAGE_SIZE,
  PAGE_SIZES,
  pageHref,
  pageOf,
  sizeOf,
} from "@/lib/staff";

export const tableBox =
  "card overflow-x-auto [&_tbody_tr]:transition-colors [&_tbody_tr:hover]:bg-tint/60";
export const th =
  "bg-tint/60 px-8 py-9 text-left text-table font-semibold whitespace-nowrap text-ink-2 first:pl-16 last:pr-16";
export const td =
  "border-t border-line px-8 py-9 text-left text-table align-top first:pl-16 last:pr-16";

/**
 * Khung trang dùng chung cho các màn hình của Staff (S-01–S-06).
 */
export function StaffPage({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-dvh">
      <SiteHeader area="staff" />
      <main className="mx-auto grid max-w-page gap-28 px-16 pt-16 pb-80 sm:px-38">
        {children}
      </main>
    </div>
  );
}

/**
 * Điều hướng phân trang dùng chung cho các bảng kết quả của Staff.
 */
export function Pager({
  path,
  params,
  totalPages,
}: {
  path: string;
  params: URLSearchParams;
  totalPages: number;
}) {
  const router = useRouter();
  const page = pageOf(params);
  const size = sizeOf(params);
  if (totalPages === 0 && page <= 1) return null;

  const changeSize = (next: string) => {
    const p = new URLSearchParams(params);
    if (Number(next) === DEFAULT_PAGE_SIZE) p.delete("size");
    else p.set("size", next);
    router.push(pageHref(path, p, 1));
  };

  return (
    <nav
      aria-label="Phân trang"
      className="flex flex-wrap items-center gap-16 text-caption"
    >
      <label className="flex items-center gap-8 text-muted">
        Số dòng mỗi trang
        <select
          value={size}
          onChange={(e) => changeSize(e.target.value)}
          className="cursor-pointer rounded-inner border border-line-strong bg-surface px-8 py-4 text-ink"
        >
          {PAGE_SIZES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </label>
      {page > 1 && (
        <Link
          href={pageHref(path, params, Math.min(page - 1, totalPages))}
          className={textLink}
        >
          {page > totalPages ? "Về trang cuối" : "Trang trước"}
        </Link>
      )}
      <span className="text-muted">{`Trang ${page}/${totalPages}`}</span>
      {page < totalPages && (
        <Link href={pageHref(path, params, page + 1)} className={textLink}>
          Trang sau
        </Link>
      )}
    </nav>
  );
}
