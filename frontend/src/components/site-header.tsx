"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useCurrentUser } from "@/lib/use-current-user";
import { Brand } from "./auth-shell";
import { pillDark, pillLine } from "./form";
import UserMenu from "./user-menu";

const LINKS = [
  ["/flights", "Tìm chuyến bay"],
  ["/bookings", "Đặt chỗ của tôi"],
] as const;

/** Đầu trang chung của khu khách hàng (C-02, C-08, C-09, C-12): mục đang mở được đánh dấu, bên phải là menu tên. */
export default function SiteHeader() {
  const pathname = usePathname();
  const user = useCurrentUser();
  return (
    <header className="mx-auto flex max-w-page flex-wrap items-center justify-between gap-16 px-16 py-22 sm:px-38">
      <Brand />
      <div className="flex flex-wrap items-center gap-x-22 gap-y-8">
        <nav
          aria-label="Khách hàng"
          className="flex flex-wrap gap-x-22 gap-y-8 text-caption"
        >
          {LINKS.map(([href, label]) => {
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
