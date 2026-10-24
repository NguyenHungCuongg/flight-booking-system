"use client";

import Link from "next/link";
import { pillLine, pillPrimary } from "@/components/form";
import { ThemeToggle } from "@/components/theme-toggle";
import UserMenu from "@/components/user-menu";
import { useCurrentUser } from "@/lib/use-current-user";
import { Logo } from "./icons";

const LINKS = [
  ["/flights", "Tìm chuyến bay", true],
  ["#cach-dat-ve", "Cách đặt vé", false],
  ["#doi-hoan", "Đổi và hoàn vé", false],
  ["#ho-tro", "Hỗ trợ", false],
  ["/bookings", "Đặt chỗ của tôi", true],
] as const;

export default function Nav() {
  const user = useCurrentUser();

  return (
    <div className="sticky top-0 z-40 border-b border-line bg-paper/85 backdrop-blur-md">
      <header className="mx-auto flex max-w-page flex-wrap items-center justify-between gap-16 px-16 py-12 sm:px-38">
        <a
          href="#top"
          aria-label="SkyLine, về đầu trang"
          className="inline-flex items-center gap-10 text-subheading font-black tracking-tight text-ink"
        >
          <span className="size-32 [&>svg]:size-full">
            <Logo />
          </span>
          SkyLine
        </a>

        <nav
          aria-label="Điều hướng chính"
          className="flex flex-wrap items-center gap-x-8 gap-y-8 text-caption"
        >
          {LINKS.map(([href, label, isRoute]) => {
            const cls =
              "rounded-inner px-12 py-6 font-semibold text-ink-2 transition-colors duration-200 hover:bg-accent-soft hover:text-accent";
            return isRoute ? (
              <Link key={href} href={href} className={cls}>
                {label}
              </Link>
            ) : (
              <a key={href} href={href} className={cls}>
                {label}
              </a>
            );
          })}

          <ThemeToggle />

          {user === undefined ? (
            <span className="min-w-120" aria-hidden="true" />
          ) : user === null ? (
            <div className="flex items-center gap-8">
              <Link href="/login" className={pillPrimary}>
                Đăng nhập
              </Link>
              <Link href="/register" className={`${pillLine} max-sm:hidden`}>
                Đăng ký
              </Link>
            </div>
          ) : (
            <UserMenu user={user} className={pillLine} />
          )}
        </nav>
      </header>
    </div>
  );
}
