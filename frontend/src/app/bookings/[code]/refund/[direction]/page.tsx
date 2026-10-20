import type { Metadata } from "next";
import { Suspense } from "react";
import RefundView from "./refund-view";

export const metadata: Metadata = { title: "Hoàn vé | SkyLine" };

/**
 * C-10 (APP_FLOW §2.1): chủ booking xem số tiền hoàn và gửi yêu cầu hoàn một chiều (FR-50, FR-51).
 * useParams cần Suspense (Cache Components).
 */
export default function Page() {
  return (
    <Suspense>
      <RefundView />
    </Suspense>
  );
}

