import { Suspense } from "react";
import PageLoading from "@/components/page-loading";
import type { Metadata } from "next";
import RefundDetailView from "./refund-detail";

export const metadata: Metadata = {
  title: "Chi tiết yêu cầu hoàn | SkyLine",
};

/** S-04 (APP_FLOW §2.2): Staff và Admin xử lý chi tiết yêu cầu hoàn tiền. */
export default function StaffRefundDetailPage() {
  return (
    <Suspense fallback={<PageLoading />}>
      <RefundDetailView />
    </Suspense>
  );
}

