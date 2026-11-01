import type { Metadata } from "next";
import { Suspense } from "react";
import { AuthHeading } from "@/components/auth-shell";
import { FormSkeleton } from "@/components/form";
import RegisterForm from "./register-form";

export const metadata: Metadata = { title: "Đăng ký | SkyLine" };

/** C-04 (APP_FLOW §2.1). */
export default function Page() {
  return (
    <>
      <AuthHeading title="Tạo tài khoản.">
        Một tài khoản cho mọi chuyến bay, vé và hoàn đổi.
      </AuthHeading>
      <Suspense fallback={<FormSkeleton rows={4} />}>
        <RegisterForm />
      </Suspense>
    </>
  );
}
