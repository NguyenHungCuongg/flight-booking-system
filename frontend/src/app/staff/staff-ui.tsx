"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import SiteHeader from "@/components/site-header";
import { textLink } from "@/components/form";
import { pageHref, pageOf } from "@/lib/staff";

export const tableBox = "overflow-x-auto";
export const th =
  "py-11 pr-16 text-left text-caption font-bold border-b border-line";
export const td =
  "py-11 pr-16 text-left text-caption border-b border-line align-top";

/**
 * Khung trang dùng chung cho các màn hình của Staff (S-01–S-06).
 */
export function StaffPage({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-dvh bg-paper">
      <SiteHeader area="staff" />
      <main className="mx-auto grid max-w-page gap-38 px-16 pt-22 pb-119 sm:px-38">
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
  const page = pageOf(params);
  if (totalPages <= 1 && page <= 1) return null;

  return (
    <nav
      aria-label="Phân trang"
      className="flex items-center gap-22 text-caption"
    >
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

