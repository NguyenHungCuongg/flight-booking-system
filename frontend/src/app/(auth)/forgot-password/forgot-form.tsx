"use client";

import Link from "next/link";
import { useState } from "react";
import {
  Field,
  Notice,
  pillDark,
  text,
  textLink,
  useSubmit,
} from "@/components/form";
import { apiFetch } from "@/lib/api";

export default function ForgotForm() {
  const [sent, setSent] = useState(false);
  const { pending, errors, formError, submit } = useSubmit();

  const onSubmit = submit(async (data) => {
    await apiFetch("/auth/forgot-password", {
      method: "POST",
      body: { email: text(data, "email") },
    });
    setSent(true);
  });

  // FR-03: luôn báo cùng một câu, không cho biết email có tài khoản hay không.
  if (sent) {
    return (
      <div className="grid gap-22">
        <Notice tone="ok">
          Nếu email này có tài khoản, chúng tôi đã gửi link đặt lại mật khẩu.
          Link có hiệu lực trong 30 phút.
        </Notice>
        <Link
          href="/login"
          className={`justify-self-start text-caption ${textLink}`}
        >
          Về đăng nhập
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} className="grid gap-22">
      <Field
        label="Email"
        name="email"
        type="email"
        autoComplete="email"
        required
        error={errors.email}
      />
      {formError && <Notice>{formError}</Notice>}
      <div className="mt-11 flex flex-wrap items-center gap-x-22 gap-y-16">
        <button type="submit" className={pillDark} disabled={pending}>
          {pending ? "Đang gửi..." : "Gửi link đặt lại"}
        </button>
        <Link href="/login" className={`text-caption ${textLink}`}>
          Về đăng nhập
        </Link>
      </div>
    </form>
  );
}
