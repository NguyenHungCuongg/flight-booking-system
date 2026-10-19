import Link from "next/link";
import { Brand } from "@/components/auth-shell";

/**
 * Đầu trang C-07: thay menu chính bằng các bước đặt vé, để khách đang nhập thông tin không bấm nhầm ra ngoài.
 * Bước 1 quay về đúng kết quả tìm (`backUrl`). Dưới 640px chỉ còn bước 1 và bước hiện tại.
 */
export default function CheckoutHeader({ backUrl }: { backUrl: string }) {
  const sep = (
    <li aria-hidden="true" className="text-line-strong">
      ›
    </li>
  );
  return (
    <header className="mx-auto flex max-w-page flex-wrap items-center justify-between gap-16 px-16 py-22 sm:px-38">
      <Brand />
      <nav aria-label="Các bước đặt vé">
        <ol className="flex flex-wrap items-center gap-x-11 gap-y-8 text-caption">
          <li>
            <Link
              href={backUrl}
              className="text-muted transition-colors hover:text-ink"
            >
              1. Chọn chuyến
            </Link>
          </li>
          {sep}
          <li>
            <span aria-current="step" className="font-bold">
              2. Thông tin hành khách
            </span>
          </li>
          <li aria-hidden="true" className="hidden text-line-strong sm:block">
            ›
          </li>
          <li className="hidden text-muted sm:block">3. Thanh toán</li>
        </ol>
      </nav>
    </header>
  );
}
