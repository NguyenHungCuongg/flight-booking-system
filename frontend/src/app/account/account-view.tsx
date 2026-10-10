"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { Brand } from "@/components/auth-shell";
import {
  Field,
  FormSkeleton,
  Notice,
  PASSWORD_PATTERN,
  PASSWORD_RULE,
  pillDark,
  pillLine,
  text,
  useSubmit,
} from "@/components/form";
import { ApiError, apiFetch, homeOf, type Role, type User } from "@/lib/api";

const ROLE_LABEL: Record<Role, string> = {
  CUSTOMER: "Khách hàng",
  STAFF: "Nhân viên",
  ADMIN: "Quản trị viên",
};

const LOGIN_AGAIN = "/login?next=%2Faccount";

/** Một khối cài đặt: tiêu đề và mô tả bên trái, form bên phải; dưới 1024px xếp dọc. */
function Section({
  title,
  desc,
  children,
}: {
  title: string;
  desc: string;
  children: ReactNode;
}) {
  return (
    <section className="grid gap-22 border-t border-line pt-38 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)] lg:gap-80">
      <div className="grid content-start gap-8">
        <h2 className="text-subheading font-bold">{title}</h2>
        <p className="text-muted">{desc}</p>
      </div>
      {children}
    </section>
  );
}

function ProfileForm({
  user,
  onSaved,
}: {
  user: User;
  onSaved: (u: User) => void;
}) {
  const [saved, setSaved] = useState(false);
  const { pending, errors, formError, submit } = useSubmit();

  const onSubmit = submit(async (data) => {
    setSaved(false);
    onSaved(
      await apiFetch<User>("/me", {
        method: "PUT",
        body: { fullName: text(data, "fullName"), phone: text(data, "phone") },
      }),
    );
    setSaved(true);
  });

  return (
    <form
      onSubmit={onSubmit}
      onChange={() => setSaved(false)}
      className="grid gap-22"
    >
      <Field
        label="Email"
        name="email"
        type="email"
        defaultValue={user.email}
        readOnly
        hint="Email dùng để đăng nhập, không đổi được."
      />
      <Field
        label="Họ và tên"
        name="fullName"
        autoComplete="name"
        maxLength={100}
        required
        defaultValue={user.fullName}
        error={errors.fullName}
      />
      <Field
        label="Số điện thoại"
        name="phone"
        type="tel"
        autoComplete="tel"
        maxLength={20}
        required
        defaultValue={user.phone}
        error={errors.phone}
      />
      {formError && <Notice>{formError}</Notice>}
      <div className="flex flex-wrap items-center gap-22">
        <button type="submit" className={pillDark} disabled={pending}>
          {pending ? "Đang lưu..." : "Lưu hồ sơ"}
        </button>
        <p role="status" className="text-caption text-muted">
          {saved && "Đã lưu hồ sơ."}
        </p>
      </div>
    </form>
  );
}

function PasswordForm() {
  const [done, setDone] = useState(false);
  const { pending, errors, formError, setErrors, submit } = useSubmit();

  const onSubmit = submit(async (data, form) => {
    setDone(false);
    const newPassword = String(data.get("newPassword"));
    if (newPassword !== String(data.get("confirm"))) {
      setErrors({ confirm: "Mật khẩu nhập lại chưa khớp." });
      return;
    }
    // Sai mật khẩu hiện tại trả 400 có errors[currentPassword] (Plan 02), hiện ngay dưới ô đó.
    await apiFetch("/me/password", {
      method: "PUT",
      body: {
        currentPassword: String(data.get("currentPassword")),
        newPassword,
      },
    });
    form.reset();
    setDone(true);
  });

  return (
    <form
      onSubmit={onSubmit}
      onChange={() => setDone(false)}
      className="grid gap-22"
    >
      <Field
        label="Mật khẩu hiện tại"
        name="currentPassword"
        type="password"
        autoComplete="current-password"
        required
        error={errors.currentPassword}
      />
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
      <div className="flex flex-wrap items-center gap-22">
        <button type="submit" className={pillDark} disabled={pending}>
          {pending ? "Đang đổi..." : "Đổi mật khẩu"}
        </button>
        <p role="status" className="text-caption text-muted">
          {done && "Đã đổi mật khẩu."}
        </p>
      </div>
    </form>
  );
}

export default function AccountView() {
  const router = useRouter();
  const [user, setUser] = useState<User>();
  const [loadError, setLoadError] = useState<string>();
  const [leaving, setLeaving] = useState(false);

  useEffect(() => {
    apiFetch<User>("/me").then(setUser, (e: ApiError) => {
      // Phiên hết hạn hoặc tài khoản bị khoá (BR-102): đăng nhập lại rồi quay về đây.
      if (e.status === 401) router.replace(LOGIN_AGAIN);
      else setLoadError(e.detail);
    });
  }, [router]);

  async function logout() {
    setLeaving(true);
    // Phiên đã hết hạn thì backend trả 401, kết quả vẫn là đã đăng xuất.
    await apiFetch("/auth/logout", { method: "POST" }).catch(() => {});
    router.replace("/login");
  }

  return (
    <div className="min-h-dvh bg-paper">
      <header className="mx-auto flex max-w-page items-center justify-between gap-16 px-16 py-22 sm:px-38">
        <Brand />
        <div className="flex items-center gap-22">
          {user && (
            <Link
              href={homeOf(user.role)}
              className="hidden text-caption text-muted transition-colors hover:text-ink sm:inline"
            >
              {user.role === "CUSTOMER" ? "Đặt chỗ của tôi" : "Trang làm việc"}
            </Link>
          )}
          <button
            type="button"
            className={pillLine}
            onClick={logout}
            disabled={leaving}
          >
            {leaving ? "Đang đăng xuất..." : "Đăng xuất"}
          </button>
        </div>
      </header>

      <main className="mx-auto grid max-w-page gap-53 px-16 pt-38 pb-119 sm:px-38">
        {loadError ? (
          <Notice>{loadError}</Notice>
        ) : !user ? (
          <div
            aria-busy="true"
            aria-label="Đang tải tài khoản"
            className="grid gap-53"
          >
            <div className="grid gap-13 motion-safe:animate-pulse">
              <div className="h-52 w-full max-w-440 bg-tint" />
              <div className="h-22 w-240 bg-tint" />
            </div>
            <FormSkeleton rows={3} />
          </div>
        ) : (
          <>
            <div className="grid gap-13">
              <h1 className="text-heading-lg font-bold break-words">
                {user.fullName}
              </h1>
              <p className="text-muted">
                {user.email}
                <span className="mx-11 text-line-strong" aria-hidden="true">
                  /
                </span>
                {ROLE_LABEL[user.role]}
              </p>
            </div>
            <Section
              title="Hồ sơ"
              desc="Họ tên và số điện thoại dùng để liên hệ về đặt chỗ của bạn."
            >
              <ProfileForm user={user} onSaved={setUser} />
            </Section>
            <Section
              title="Đổi mật khẩu"
              desc="Cần mật khẩu hiện tại để đặt mật khẩu mới."
            >
              <PasswordForm />
            </Section>
          </>
        )}
      </main>
    </div>
  );
}
