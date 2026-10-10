import type { Metadata } from "next";
import { Suspense } from "react";
import { AuthHeading } from "@/components/auth-shell";
import { FormSkeleton } from "@/components/form";
import ResetForm from "./reset-form";

export const metadata: Metadata = { title: "Đặt lại mật khẩu | SkyLine" };

/** C-06 (APP_FLOW §2.1, UF-06). Mở từ link trong email: /reset-password?token=... */
export default function Page() {
  return (
    <>
      <AuthHeading title="Mật khẩu mới.">
        Đặt mật khẩu mới cho tài khoản của bạn.
      </AuthHeading>
      <Suspense fallback={<FormSkeleton rows={2} />}>
        <ResetForm />
      </Suspense>
    </>
  );
}
