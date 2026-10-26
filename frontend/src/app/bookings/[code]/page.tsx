import type { Metadata } from "next";
import { Suspense } from "react";
import PageLoading from "@/components/page-loading";
import BookingView from "./booking-view";

export const metadata: Metadata = { title: "Chi tiết đặt chỗ | SkyLine" };

/**
 * C-09 (APP_FLOW §2.1): chủ booking; proxy.ts chặn khi chưa có cookie phiên. Mã đặt chỗ chỉ biết lúc request, nên
 * useParams và useSearchParams cần Suspense (Cache Components).
 */
export default function Page() {
  return (
    <Suspense fallback={<PageLoading />}>
      <BookingView />
    </Suspense>
  );
}
