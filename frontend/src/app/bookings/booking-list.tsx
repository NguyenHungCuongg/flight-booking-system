"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import SiteHeader from "@/components/site-header";
import { Notice, textLink } from "@/components/form";
import { ApiError, apiFetch, type Schemas } from "@/lib/api";
import {
  DIRECTION_LABEL,
  STATUS_LABEL,
  clock,
  dmy,
  type BookingStatus,
} from "@/lib/booking";
import { vnd } from "../flights/itineraries";

type Paged = Schemas["PagedModelBookingSummary"];
type Load = { qs: string; data: Paged } | { qs: string; error: string };

const STATUSES = Object.keys(STATUS_LABEL) as BookingStatus[];

/** Link giữ bộ lọc trạng thái; trang đếm từ 1 trên URL (API đếm từ 0). */
function href(status: string, page = 1) {
  const u = new URLSearchParams();
  if (status) u.set("status", status);
  if (page > 1) u.set("page", String(page));
  const q = u.toString();
  return q ? `/bookings?${q}` : "/bookings";
}

/** C-08 (APP_FLOW §2.1, FR-40): booking của tôi, mới nhất trước, lọc theo trạng thái, 20 booking mỗi trang. */
export default function BookingList() {
  const router = useRouter();
  const params = useSearchParams();
  const status = params.get("status") ?? "";
  const page = Math.max(1, Math.floor(Number(params.get("page"))) || 1);
  const query = new URLSearchParams({ page: String(page - 1) });
  if (status) query.set("status", status);
  const qs = query.toString();

  const [load, setLoad] = useState<Load>();
  useEffect(() => {
    let live = true;
    apiFetch<Paged>(`/bookings?${qs}`).then(
      (data) => live && setLoad({ qs, data }),
      (e: ApiError) => {
        if (!live) return;
        if (e.status === 401) router.replace("/login?next=%2Fbookings");
        else setLoad({ qs, error: e.detail });
      },
    );
    return () => {
      live = false;
    };
  }, [qs, router]);
  const current = load?.qs === qs ? load : undefined;

  let body;
  if (!current) {
    body = (
      <div aria-hidden="true" className="grid gap-16 motion-safe:animate-pulse">
        {[0, 1, 2].map((i) => (
          <div key={i} className="h-120 border border-line bg-tint" />
        ))}
      </div>
    );
  } else if ("error" in current) {
    body = <Notice>{current.error}</Notice>;
  } else if (current.data.content.length === 0) {
    body = (
      <p className="text-muted">
        {page > 1
          ? "Trang này không có đặt chỗ nào. "
          : status
            ? "Không có đặt chỗ nào ở trạng thái này. "
            : "Bạn chưa có đặt chỗ nào. "}
        <Link href="/flights" className={textLink}>
          Tìm chuyến bay
        </Link>
      </p>
    );
  } else {
    const { content, page: meta } = current.data;
    body = (
      <>
        <ul aria-label="Danh sách đặt chỗ" className="grid gap-16">
          {content.map((b) => (
            <li key={b.code}>
              <Link
                href={`/bookings/${b.code}`}
                className="grid gap-8 border border-line p-22 transition-colors hover:border-ink sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
              >
                <div className="grid gap-4">
                  <p>
                    <span className="font-bold">{b.code}</span>
                    <span className="text-muted">{`, ${STATUS_LABEL[b.status]}`}</span>
                  </p>
                  {b.routes.map((r) => (
                    <p key={r.direction} className="text-caption">
                      {`${DIRECTION_LABEL[r.direction]}: ${r.departureAirport} → ${r.arrivalAirport}, ${clock(r.departureTime)} ${dmy(r.departureTime)}`}
                    </p>
                  ))}
                  {b.status === "PENDING_PAYMENT" && (
                    <p className="text-caption text-muted">
                      {`Giữ chỗ đến ${clock(b.holdExpiresAt)} ${dmy(b.holdExpiresAt)} (giờ Việt Nam)`}
                    </p>
                  )}
                </div>
                <p className="text-subheading font-bold">
                  {vnd(b.totalAmount)}
                </p>
              </Link>
            </li>
          ))}
        </ul>
        {meta.totalPages > 1 && (
          <nav
            aria-label="Phân trang"
            className="flex items-center gap-22 text-caption"
          >
            {page > 1 && (
              <Link href={href(status, page - 1)} className={textLink}>
                Trang trước
              </Link>
            )}
            <span className="text-muted">{`Trang ${page}/${meta.totalPages}`}</span>
            {page < meta.totalPages && (
              <Link href={href(status, page + 1)} className={textLink}>
                Trang sau
              </Link>
            )}
          </nav>
        )}
      </>
    );
  }

  return (
    <div className="min-h-dvh bg-paper">
      <SiteHeader />
      <main className="mx-auto grid max-w-page gap-38 px-16 pt-22 pb-119 sm:px-38">
        <h1 className="text-heading-lg font-bold">Đặt chỗ của tôi.</h1>
        {params.get("payment") === "failed" && (
          <Notice>
            Thanh toán chưa thành công. Hãy mở đặt chỗ cần thanh toán bên dưới
            để thử lại.
          </Notice>
        )}
        <nav
          aria-label="Lọc theo trạng thái"
          className="flex flex-wrap gap-x-22 gap-y-8 text-caption"
        >
          {["", ...STATUSES].map((s) => (
            <Link
              key={s}
              href={href(s)}
              aria-current={s === status ? "page" : undefined}
              className={
                s === status
                  ? "font-bold underline underline-offset-4"
                  : "text-muted transition-colors hover:text-ink"
              }
            >
              {s ? STATUS_LABEL[s as BookingStatus] : "Tất cả"}
            </Link>
          ))}
        </nav>
        {body}
      </main>
    </div>
  );
}
