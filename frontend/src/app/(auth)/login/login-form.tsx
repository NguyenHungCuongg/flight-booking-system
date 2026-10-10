"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import {
  Field,
  Notice,
  pillDark,
  text,
  textLink,
  useSubmit,
} from "@/components/form";
import { apiFetch, safeNext, type User } from "@/lib/api";

export default function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const next = params.get("next");
  const { pending, formError, submit } = useSubmit();
  const withNext = (path: string) =>
    next ? `${path}?next=${encodeURIComponent(next)}` : path;

  const onSubmit = submit(async (data) => {
    const user = await apiFetch<User>("/auth/login", {
      method: "POST",
      body: {
        email: text(data, "email"),
        password: String(data.get("password")),
      },
    });
    router.replace(safeNext(next, user.role));
  });

  return (
    <form onSubmit={onSubmit} className="grid gap-22">
      {params.get("reset") === "1" && (
        <Notice tone="ok">
          Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới.
        </Notice>
      )}
      <Field
        label="Email"
        name="email"
        type="email"
        autoComplete="email"
        required
      />
      <div className="grid gap-11">
        <Field
          label="Mật khẩu"
          name="password"
          type="password"
          autoComplete="current-password"
          required
        />
        <Link
          href="/forgot-password"
          className={`justify-self-start text-caption ${textLink}`}
        >
          Quên mật khẩu?
        </Link>
      </div>
      {formError && <Notice>{formError}</Notice>}
      <div className="mt-11 flex flex-wrap items-center gap-x-22 gap-y-16">
        <button type="submit" className={pillDark} disabled={pending}>
          {pending ? "Đang đăng nhập..." : "Đăng nhập"}
        </button>
        <p className="text-caption text-muted">
          Chưa có tài khoản?{" "}
          <Link href={withNext("/register")} className={`text-ink ${textLink}`}>
            Đăng ký
          </Link>
        </p>
      </div>
    </form>
  );
}
