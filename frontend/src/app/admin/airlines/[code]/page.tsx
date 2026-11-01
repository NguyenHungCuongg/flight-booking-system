import { Suspense } from "react";
import PageLoading from "@/components/page-loading";
import AirlineDetail from "./airline-detail";

export const metadata = {
  title: "Chi tiết hãng bay | SkyLine Admin",
};

/** A-03 (APP_FLOW §2.3): gói giá và bảng giá hành lý của một hãng. */
export default function AdminAirlineDetailPage() {
  return (
    <Suspense fallback={<PageLoading />}>
      <AirlineDetail />
    </Suspense>
  );
}
