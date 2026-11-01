import type { ReactNode } from "react";
import { Brand } from "./auth-shell";
import { ThemeToggle } from "./theme-toggle";

/** Khung chung cho trang trạng thái toàn màn (404, lỗi): đầu trang thương hiệu, mã lớn, lời giải thích và nút hành động. */
export default function StatusPage({
  code,
  title,
  children,
  actions,
}: {
  code: string;
  title: string;
  children?: ReactNode;
  actions: ReactNode;
}) {
  return (
    <div className="flex min-h-dvh flex-col px-16 py-16 sm:px-38">
      <header className="flex items-center justify-between gap-16">
        <Brand />
        <ThemeToggle />
      </header>
      <main className="mx-auto flex w-full max-w-520 flex-1 flex-col items-center justify-center gap-18 py-40 text-center">
        <p className="text-display-xl font-black tracking-tight text-accent max-sm:text-display">
          {code}
        </p>
        <h1 className="text-heading font-black tracking-tight text-ink">
          {title}
        </h1>
        {children && <p className="text-muted">{children}</p>}
        <div className="flex flex-wrap gap-11 pt-6">{actions}</div>
      </main>
    </div>
  );
}
