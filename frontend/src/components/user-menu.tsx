"use client";

import Link from "next/link";
import { useId } from "react";
import { apiFetch, homeOf, type User } from "@/lib/api";

const item =
  "block px-16 py-11 text-caption text-ink transition-colors hover:bg-tint";

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
  return (
    <>
      <button
        type="button"
        popoverTarget={id}
        aria-label={`Menu tài khoản: ${user.fullName}`}
        className={className}
      >
        {user.fullName.split(" ").at(-1)} ▾
      </button>
      {/* ponytail: vị trí cố định ở góc phải trên, dùng CSS anchor positioning khi các trình duyệt đều hỗ trợ. */}
      <div
        id={id}
        popover="auto"
        className="fixed inset-auto top-72 right-16 m-0 w-260 border border-line-strong bg-paper py-8 text-ink"
      >
        <div className="border-b border-line px-16 pt-4 pb-11">
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
