"use client";

import Link from "next/link";
import { pillLine, pillPrimary } from "@/components/form";
import StatusPage from "@/components/status-page";

/** Lỗi không lường trước khi dựng một trang (bên dưới layout gốc). Lỗi gọi API đã có thông báo riêng ở từng màn. */
export default function ErrorPage({
  error,
  retry,
}: {
  error: unknown;
  retry: () => void;
}) {
  const digest =
    error instanceof Error
      ? (error as Error & { digest?: string }).digest
      : undefined;

  return (
    <StatusPage
      code="500"
      title="Có lỗi xảy ra"
      actions={
        <>
          <button type="button" className={pillPrimary} onClick={() => retry()}>
            Thử lại
          </button>
          <Link href="/" className={pillLine}>
            Về trang chủ
          </Link>
        </>
      }
    >
      Trang không tải được. Hãy thử lại, nếu vẫn lỗi thì quay lại sau ít phút.
      {digest && (
        <span className="mt-8 block text-badge">Mã lỗi: {digest}</span>
      )}
    </StatusPage>
  );
}
