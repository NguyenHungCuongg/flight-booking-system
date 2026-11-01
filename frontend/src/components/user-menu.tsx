"use client";

import Link from "next/link";
import { useId, useState } from "react";
import { apiFetch, homeOf, type User } from "@/lib/api";

const item =
  "block px-14 py-8 text-caption text-ink transition-colors hover:bg-tint";

/** Đăng xuất rồi tải lại trang chủ, để mọi header đọc lại GET /me. Phiên đã hết hạn thì backend trả 401, vẫn coi là xong. */
async function logout() {
  await apiFetch("/auth/logout", { method: "POST" }).catch(() => {});
  // router.push("/") không đủ: đang ở "/" thì header không mount lại và vẫn giữ người dùng cũ.
  // eslint-disable-next-line @next/next/no-location-assign-relative-destination -- cố ý tải lại cả trang
  window.location.assign("/");
}

/**
 * Nút tên người dùng và menu thả xuống (Tài khoản, Đặt chỗ hoặc Trang làm việc, Đăng xuất). Dùng Popover API nên
 * trình duyệt tự đóng menu khi bấm ra ngoài hoặc nhấn Esc.
 */
export default function UserMenu({
  user,
  className,
}: {
  user: User;
  className?: string;
}) {
  const id = useId();
  const [pos, setPos] = useState<{ top: number; right: number }>();
  return (
    <>
      <button
        type="button"
        popoverTarget={id}
        aria-label={`Menu tài khoản: ${user.fullName}`}
        className={className}
        // Menu nằm ở top layer nên tự đặt ngay dưới nút, căn mép phải theo nút.
        // ponytail: đo vị trí lúc mở, chuyển sang CSS anchor positioning khi các trình duyệt đều hỗ trợ.
        onClick={(e) => {
          const r = e.currentTarget.getBoundingClientRect();
          setPos({
            top: r.bottom + 8,
            right: document.documentElement.clientWidth - r.right,
          });
        }}
      >
        {user.fullName.split(" ").at(-1)} ▾
      </button>
      {/* text-caption đặt ở đây để nút Đăng xuất kế thừa, vì CSS của landing (.sk button) ghi đè cỡ chữ của nút. */}
      <div
        id={id}
        popover="auto"
        style={pos}
        // Chọn một mục thì đóng menu, kể cả khi mục đó là trang đang mở (không có điều hướng để gỡ menu).
        onClick={(e) =>
          (e.target as Element).closest("a") && e.currentTarget.hidePopover()
        }
        className="fixed inset-auto m-0 w-240 overflow-hidden rounded-inner bg-surface py-8 text-caption text-ink shadow-card-hover"
      >
        <div className="border-b border-line px-14 pt-4 pb-8">
          <p className="text-caption font-bold break-words">{user.fullName}</p>
          <p className="text-caption break-words text-muted">{user.email}</p>
        </div>
        {user.role === "CUSTOMER" ? (
          <Link href="/bookings" className={item}>
            Đặt chỗ của tôi
          </Link>
        ) : (
          <Link href={homeOf(user.role)} className={item}>
            Trang làm việc
          </Link>
        )}
        <Link href="/account" className={item}>
          Tài khoản
        </Link>
        <button
          type="button"
          onClick={logout}
          className={`${item} w-full text-left`}
        >
          Đăng xuất
        </button>
      </div>
    </>
  );
}
