"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useCurrentUser } from "@/lib/use-current-user";
import { Brand } from "./auth-shell";
import { pillDark, pillLine } from "./form";
import UserMenu from "./user-menu";

const NAV: Record<
  "customer" | "staff" | "admin",
  { label: string; links: readonly (readonly [string, string])[] }
> = {
  customer: {
    label: "Khách hàng",
    links: [
      ["/flights", "Tìm chuyến bay"],
      ["/bookings", "Đặt chỗ của tôi"],
    ],
  },
  staff: {
    label: "Nhân viên",
    links: [
      ["/staff/bookings", "Đặt chỗ"],
      ["/staff/refunds", "Yêu cầu hoàn"],
      ["/staff/flights", "Chuyến bay"],
    ],
  },
  admin: {
    label: "Quản trị viên",
    links: [
      ["/admin/flights", "Chuyến bay"],
      ["/admin/airports", "Sân bay"],
      ["/admin/airlines", "Hãng bay"],
      ["/admin/vouchers", "Voucher"],
      ["/admin/users", "Tài khoản"],
      ["/admin/settings", "Tham số"],
      ["/admin/reports", "Báo cáo"],
    ],
  },
};

/** Đầu trang chung của khu khách hàng, nhân viên và quản trị viên: mục đang mở được đánh dấu, bên phải là menu tên. */
export default function SiteHeader({
  area = "customer",
}: {
  area?: "customer" | "staff" | "admin";
} = {}) {
  const pathname = usePathname();
  const user = useCurrentUser();
  const nav = NAV[area];

  return (
    <header className="mx-auto flex max-w-page flex-wrap items-center justify-between gap-16 px-16 py-22 sm:px-38">
      <Brand />
      <div className="flex flex-wrap items-center gap-x-22 gap-y-8">
        <nav
          aria-label={nav.label}
          className="flex flex-wrap gap-x-22 gap-y-8 text-caption"
        >
          {nav.links.map(([href, label]) => {
            const active = pathname.startsWith(href);
            return (
              <Link
                key={href}
                href={href}
                aria-current={active ? "page" : undefined}
                className={
                  active
                    ? "font-bold text-ink"
                    : "text-muted transition-colors hover:text-ink"
                }
              >
                {label}
              </Link>
            );
          })}
        </nav>
        {/* Đang hỏi /me: giữ chỗ trống để không nháy nút Đăng nhập. */}
        {user === undefined ? (
          <span className="min-w-120" aria-hidden="true" />
        ) : user === null ? (
          <div className="flex gap-11">
            <Link href="/login" className={pillDark}>
              Đăng nhập
            </Link>
            <Link href="/register" className={pillLine}>
              Đăng ký
            </Link>
          </div>
        ) : (
          <UserMenu user={user} className={pillLine} />
        )}
      </div>
    </header>
  );
}
