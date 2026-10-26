import type { Metadata } from "next";
import { Suspense } from "react";
import PageLoading from "@/components/page-loading";
import RescheduleView from "./reschedule-view";

export const metadata: Metadata = { title: "Đổi chuyến | SkyLine" };

/**
 * C-11 (APP_FLOW §2.1): chủ booking chọn ngày, chọn chuyến thay thế, xem báo giá và xác nhận đổi một chiều (FR-60–FR-62).
 * useParams cần Suspense (Cache Components).
 */
export default function Page() {
  return (
    <Suspense fallback={<PageLoading />}>
      <RescheduleView />
    </Suspense>
  );
}

