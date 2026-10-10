"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import {
  Field,
  Notice,
  PASSWORD_PATTERN,
  PASSWORD_RULE,
  pillDark,
  textLink,
  useSubmit,
} from "@/components/form";
import { apiFetch } from "@/lib/api";

function BadLink() {
  return (
    <Notice>
      Link đặt lại mật khẩu không hợp lệ, đã hết hạn hoặc đã được dùng.{" "}
      <Link href="/forgot-password" className={textLink}>
        Gửi lại link mới
      </Link>
    </Notice>
  );
}

export default function ResetForm() {
  const router = useRouter();
  const token = useSearchParams().get("token");
  const [badToken, setBadToken] = useState(false);
  const { pending, errors, formError, setErrors, submit } = useSubmit();

  const onSubmit = submit(
    async (data) => {
      const newPassword = String(data.get("newPassword"));
      if (newPassword !== String(data.get("confirm"))) {
        setErrors({ confirm: "Mật khẩu nhập lại chưa khớp." });
        return;
      }
      await apiFetch("/auth/reset-password", {
        method: "POST",
        body: { token, newPassword },
      });
      router.replace("/login?reset=1");
    },
    (e) => {
      if (e.code !== "INVALID_TOKEN") return false;
      setBadToken(true);
      return true;
    },
  );

  // UF-06 bước 3: token sai hoặc đã dùng thì gợi ý làm lại từ C-05.
  if (!token || badToken) return <BadLink />;

  return (
    <form onSubmit={onSubmit} className="grid gap-22">
      <Field
        label="Mật khẩu mới"
        name="newPassword"
        type="password"
        autoComplete="new-password"
        required
        pattern={PASSWORD_PATTERN}
        invalidMessage={PASSWORD_RULE}
        hint={PASSWORD_RULE}
        error={errors.newPassword}
      />
      <Field
        label="Nhập lại mật khẩu mới"
        name="confirm"
        type="password"
        autoComplete="new-password"
        required
        error={errors.confirm}
      />
      {formError && <Notice>{formError}</Notice>}
      <div className="mt-11">
        <button type="submit" className={pillDark} disabled={pending}>
          {pending ? "Đang lưu..." : "Đặt mật khẩu mới"}
        </button>
      </div>
    </form>
  );
}
