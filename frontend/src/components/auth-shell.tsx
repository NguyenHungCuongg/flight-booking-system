import Image from "next/image";
import Link from "next/link";
import type { ReactNode } from "react";
import { Logo } from "./landing/icons";

/** Logo và chữ SkyLine, dẫn về trang chủ. Dùng ở đầu các màn tài khoản. */
export function Brand() {
  return (
    <Link
      href="/"
      aria-label="SkyLine, về trang chủ"
      className="inline-flex items-center gap-11 text-subheading font-bold"
    >
      <span className="size-38 [&>svg]:size-full">
        <Logo />
      </span>
      SkyLine
    </Link>
  );
}

/** Tiêu đề màn: 52px đậm, kết bằng dấu chấm theo DESIGN.md. */
export function AuthHeading({
  title,
  children,
}: {
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="mb-38 grid gap-13">
      <h1 className="text-heading-lg font-bold">{title}</h1>
      {children && <p className="text-muted">{children}</p>}
    </div>
  );
}

/**
 * Khung của C-03 đến C-06: form bên trái, bầu trời đêm và máy bay bên phải (cùng ảnh với hero của landing).
 * Dưới 1024px chỉ còn cột form.
 */
export default function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-dvh bg-paper lg:grid-cols-2">
      <div className="flex flex-col px-16 py-22 sm:px-38 lg:px-68">
        <header className="flex items-center justify-between gap-16">
          <Brand />
          <Link
            href="/"
            className="text-caption text-muted transition-colors hover:text-ink"
          >
            Về trang chủ
          </Link>
        </header>
        <main className="mx-auto flex w-full max-w-440 flex-1 flex-col justify-center py-53">
          {children}
        </main>
      </div>

      <div
        aria-hidden="true"
        className="relative hidden overflow-hidden bg-[#090a14] lg:block"
      >
        <Image
          src="/assets/images/night-sky.jpg"
          alt=""
          fill
          priority
          sizes="50vw"
          className="object-cover"
        />
        <div className="absolute inset-0 bg-linear-to-b from-[#090a14]/50 via-[#090a14]/10 to-[#090a14]/70" />
        <p className="absolute top-68 left-59 text-display font-bold text-pure-white">
          Cất
          <br />
          cánh.
        </p>
        <Image
          src="/assets/images/skyline-climb-jet.webp"
          alt=""
          width={1600}
          height={505}
          sizes="60vw"
          className="absolute right-[-5%] bottom-[16%] w-[108%] max-w-none motion-safe:animate-[jet-in_1.2s_var(--ease-out)_both]"
        />
        <p className="absolute right-59 bottom-53 left-59 max-w-420 text-body text-pure-white/80">
          Vé của nhiều hãng ở một nơi. Giữ chỗ trước, thanh toán sau qua VNPay.
        </p>
      </div>
    </div>
  );
}
