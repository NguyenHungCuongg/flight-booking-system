"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import {
  Field,
  Notice,
  PASSWORD_PATTERN,
  PASSWORD_RULE,
  pillPrimary,
  text,
  textLink,
  useSubmit,
} from "@/components/form";
import { apiFetch, safeNext, type User } from "@/lib/api";

export default function RegisterForm() {
  const next = useSearchParams().get("next");
  const { pending, errors, formError, setErrors, submit } = useSubmit();

  const onSubmit = submit(
    async (data) => {
      // FR-01: đăng ký xong backend đăng nhập luôn, nên chuyển trang như sau khi đăng nhập.
      const user = await apiFetch<User>("/auth/register", {
        method: "POST",
        body: {
          fullName: text(data, "fullName"),
          email: text(data, "email"),
          phone: text(data, "phone"),
          password: String(data.get("password")),
        },
      });
      // Tải lại hẳn trang: client router còn giữ kết quả chuyển hướng về /login của proxy.ts từ lúc chưa đăng nhập
      // (VD /checkout), router.replace sẽ dùng lại kết quả đó.
      window.location.replace(safeNext(next, user.role));
    },
    (e) => {
      if (e.code !== "EMAIL_ALREADY_USED") return false;
      setErrors({ email: e.detail });
      return true;
    },
  );

  return (
    <form onSubmit={onSubmit} className="grid gap-16">
      <Field
        label="Họ và tên"
        name="fullName"
        autoComplete="name"
        maxLength={100}
        required
        error={errors.fullName}
      />
      <Field
        label="Email"
        name="email"
        type="email"
        autoComplete="email"
        maxLength={255}
        required
        error={errors.email}
      />
      <Field
        label="Số điện thoại"
        name="phone"
        type="tel"
        autoComplete="tel"
        maxLength={20}
        required
        error={errors.phone}
      />
      <Field
        label="Mật khẩu"
        name="password"
        type="password"
        autoComplete="new-password"
        required
        pattern={PASSWORD_PATTERN}
        invalidMessage={PASSWORD_RULE}
        hint={PASSWORD_RULE}
        error={errors.password}
      />
      {formError && <Notice>{formError}</Notice>}
      <div className="mt-11 flex flex-wrap items-center gap-x-22 gap-y-16">
        <button type="submit" className={pillPrimary} disabled={pending}>
          {pending ? "Đang tạo tài khoản..." : "Tạo tài khoản"}
        </button>
        <p className="text-caption text-muted">
          Đã có tài khoản?{" "}
          <Link
            href={next ? `/login?next=${encodeURIComponent(next)}` : "/login"}
            className={`text-ink ${textLink}`}
          >
            Đăng nhập
          </Link>
        </p>
      </div>
      <p className="text-caption text-muted">
        Bằng việc đăng ký, bạn đồng ý với{" "}
        <Link href="/chinh-sach/dieu-khoan" className={`text-ink ${textLink}`}>
          Điều khoản & điều kiện
        </Link>{" "}
        và{" "}
        <Link
          href="/chinh-sach/quyen-rieng-tu"
          className={`text-ink ${textLink}`}
        >
          Chính sách quyền riêng tư
        </Link>{" "}
        của SkyLine.
      </p>
    </form>
  );
}
