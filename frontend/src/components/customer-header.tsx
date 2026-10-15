import Link from "next/link";
import { Brand } from "./auth-shell";

const LINKS = [
  ["/", "Tìm chuyến bay"],
  ["/bookings", "Đặt chỗ của tôi"],
  ["/account", "Tài khoản"],
] as const;

/** Đầu trang của C-07, C-08, C-09. */
export default function CustomerHeader() {
  return (
    <header className="mx-auto flex max-w-page flex-wrap items-center justify-between gap-16 px-16 py-22 sm:px-38">
      <Brand />
      <nav
        aria-label="Khách hàng"
        className="flex flex-wrap gap-x-22 gap-y-8 text-caption"
      >
        {LINKS.map(([href, label]) => (
          <Link
            key={href}
            href={href}
            className="text-muted transition-colors hover:text-ink"
          >
            {label}
          </Link>
        ))}
      </nav>
    </header>
  );
}
