import { Suspense } from "react";
import PageLoading from "@/components/page-loading";
import type { Metadata } from "next";
import RefundQueue from "./refund-queue";

export const metadata: Metadata = {
  title: "Yêu cầu hoàn | SkyLine",
};

/** S-03 (APP_FLOW §2.2): Staff và Admin xem hàng đợi yêu cầu hoàn tiền. */
export default function StaffRefundsPage() {
  return (
    <Suspense fallback={<PageLoading />}>
      <RefundQueue />
    </Suspense>
  );
}

