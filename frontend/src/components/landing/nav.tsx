"use client";

import Link from "next/link";
import { pillLine } from "@/components/form";
import { ThemeToggle } from "@/components/theme-toggle";
import UserMenu from "@/components/user-menu";
import { useCurrentUser } from "@/lib/use-current-user";
import { Icon, Logo } from "./icons";

const LINKS = [
  ["/flights", "Tìm chuyến bay", true],
  ["#cach-dat-ve", "Cách đặt vé", false],
  ["#doi-hoan", "Đổi và hoàn vé", false],
  ["#ho-tro", "Hỗ trợ", false],
  ["/bookings", "Đặt chỗ của tôi", true],
] as const;

const links = (cls: string) =>
  LINKS.map(([href, label, isRoute]) =>
    isRoute ? (
      <Link key={href} href={href} className={cls}>
        {label}
      </Link>
    ) : (
      <a key={href} href={href} className={cls}>
        {label}
      </a>
    ),
  );

export default function Nav() {
  const user = useCurrentUser();

  return (
    <div className="sticky top-0 z-40 border-b border-line bg-paper">
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
          {links(
            "rounded-inner px-12 py-6 font-semibold text-ink-2 transition-colors duration-200 hover:bg-accent-soft hover:text-accent max-sm:hidden",
          )}

          {/* Màn hẹp: gom các liên kết vào menu Popover để thanh dính chỉ còn một hàng. */}
          <button
            type="button"
            popoverTarget="nav-menu"
            aria-label="Menu điều hướng"
            className="grid size-40 place-items-center rounded-inner border border-line-strong bg-surface text-ink sm:hidden"
          >
            <Icon name="menu" className="size-20 shrink-0" />
          </button>
          <div
            id="nav-menu"
            popover="auto"
            onClick={(e) =>
              (e.target as Element).closest("a") && e.currentTarget.hidePopover()
            }
            className="fixed inset-x-16 top-64 m-0 rounded-inner border border-line bg-surface py-8 text-caption shadow-card-hover"
          >
            {links(
              "flex min-h-44 items-center px-16 font-semibold text-ink transition-colors hover:bg-tint",
            )}
          </div>

          <ThemeToggle />

          {user === undefined ? (
            <span className="min-w-120" aria-hidden="true" />
          ) : user === null ? (
            <div className="flex items-center gap-12">
              <Link
                href="/login"
                className="px-12 py-8 text-caption font-bold text-ink transition-colors hover:text-accent"
              >
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
