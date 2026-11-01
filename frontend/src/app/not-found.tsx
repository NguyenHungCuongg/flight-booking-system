import type { Metadata } from "next";
import Link from "next/link";
import { pillLine, pillPrimary } from "@/components/form";
import StatusPage from "@/components/status-page";

export const metadata: Metadata = { title: "Không tìm thấy trang | SkyLine" };

export default function NotFound() {
  return (
    <StatusPage
      code="404"
      title="Không tìm thấy trang"
      actions={
        <>
          <Link href="/" className={pillPrimary}>
            Về trang chủ
          </Link>
          <Link href="/flights" className={pillLine}>
            Tìm chuyến bay
          </Link>
        </>
      }
    >
      Đường dẫn có thể đã sai hoặc trang này không còn nữa.
    </StatusPage>
  );
}
