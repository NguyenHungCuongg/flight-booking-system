import Image from "next/image";
import Link from "next/link";
import type { ReactNode } from "react";
import { Logo } from "./landing/icons";
import { ThemeToggle } from "./theme-toggle";

/** Logo và chữ SkyLine, dẫn về trang chủ. Dùng ở đầu các màn tài khoản. */
export function Brand() {
  return (
    <Link
      href="/"
      aria-label="SkyLine, về trang chủ"
      className="inline-flex items-center gap-10 text-subheading font-black tracking-tight text-ink"
    >
      <span className="size-30 [&>svg]:size-full">
        <Logo />
      </span>
      SkyLine
    </Link>
  );
}

/** Tiêu đề màn: đậm nét, kết bằng dấu chấm theo hệ thống thiết kế. */
export function AuthHeading({
  title,
  children,
}: {
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="mb-28 grid gap-13">
      <h1 className="text-heading-lg font-black tracking-tight text-ink">{title}</h1>
      {children && <p className="text-muted">{children}</p>}
    </div>
  );
}

/**
 * Khung của C-03 đến C-06: form bên trái, khung thương hiệu Forest Ink & ảnh cột cờ bên phải.
 * Dưới 1024px chỉ còn cột form.
 */
export default function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-2">
      <div className="flex flex-col px-16 py-16 sm:px-38 lg:px-68">
        <header className="flex items-center justify-between gap-16">
          <Brand />
          <div className="flex items-center gap-12">
            <ThemeToggle />
            <Link
              href="/"
              className="text-caption font-semibold text-muted transition-colors hover:text-ink"
            >
              Về trang chủ
            </Link>
          </div>
        </header>
        <main className="mx-auto flex w-full max-w-440 flex-1 flex-col justify-center py-40">
          {children}
        </main>
      </div>

      <div
        aria-hidden="true"
        className="relative hidden overflow-hidden bg-forest-ink lg:block"
      >
        <Image
          src="/assets/images/viet-nam-flag.jpg"
          alt=""
          fill
          sizes="50vw"
          className="object-cover object-[50%_30%] mix-blend-luminosity opacity-45"
        />
        <div className="absolute inset-0 bg-linear-to-b from-forest-ink/80 via-spruce/35 to-forest-ink/92" />
        <p className="absolute top-68 left-59 text-display font-black tracking-tight text-pure-white">
          Sẵn sàng,
          <br />
          <span className="text-lime-voltage">bay thôi!</span>
        </p>
        <p className="absolute right-59 bottom-53 left-59 max-w-420 text-body text-pure-white/85">
          Vé của nhiều hãng ở một nơi. Giữ chỗ trước, thanh toán sau qua VNPay.
        </p>
      </div>
    </div>
  );
}
