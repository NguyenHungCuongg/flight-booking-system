import type { Metadata } from "next";
import { AuthHeading } from "@/components/auth-shell";
import ForgotForm from "./forgot-form";

export const metadata: Metadata = { title: "Quên mật khẩu | SkyLine" };

/** C-05 (APP_FLOW §2.1, UF-06). */
export default function Page() {
  return (
    <>
      <AuthHeading title="Quên mật khẩu.">
        Nhập email đã đăng ký, chúng tôi sẽ gửi link để bạn đặt mật khẩu mới.
      </AuthHeading>
      <ForgotForm />
    </>
  );
}
