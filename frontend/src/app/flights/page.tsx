import type { Metadata } from "next";
import { Suspense } from "react";
import PageLoading from "@/components/page-loading";
import FlightResults from "./flight-results";

export const metadata: Metadata = { title: "Tìm chuyến bay | SkyLine" };

/** C-02 (APP_FLOW §2.1): trang Tìm chuyến bay độc lập, tìm và sửa tìm kiếm tại chỗ. Công khai. useSearchParams đọc điều kiện tìm, nên nằm trong Suspense (Cache Components). */
export default function Page() {
  return (
    <Suspense fallback={<PageLoading />}>
      <FlightResults />
    </Suspense>
  );
}
