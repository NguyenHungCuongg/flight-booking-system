import type { Metadata } from "next";
import { Suspense } from "react";
import { AuthHeading } from "@/components/auth-shell";
import { FormSkeleton } from "@/components/form";
import LoginForm from "./login-form";

export const metadata: Metadata = { title: "Đăng nhập | SkyLine" };

/** C-03 (APP_FLOW §2.1). */
export default function Page() {
  return (
    <>
      <AuthHeading title="Đăng nhập.">
        Xem đặt chỗ, thanh toán và quản lý vé của bạn.
      </AuthHeading>
      {/* useSearchParams đọc next và reset, nên form nằm trong Suspense (Cache Components). */}
      <Suspense fallback={<FormSkeleton rows={2} />}>
        <LoginForm />
      </Suspense>
    </>
  );
}
