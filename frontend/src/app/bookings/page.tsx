import type { Metadata } from "next";
import { Suspense } from "react";
import BookingList from "./booking-list";

export const metadata: Metadata = { title: "Đặt chỗ của tôi | SkyLine" };

/** C-08 (APP_FLOW §2.1): Customer, trang mặc định sau đăng nhập. useSearchParams cần Suspense (Cache Components). */
export default function Page() {
  return (
    <Suspense>
      <BookingList />
    </Suspense>
  );
}
