import type { Metadata } from "next";
import { Suspense } from "react";
import FlightResults from "./flight-results";

export const metadata: Metadata = { title: "Kết quả tìm kiếm | SkyLine" };

/** C-02 (APP_FLOW §2.1). Công khai. useSearchParams đọc điều kiện tìm, nên nằm trong Suspense (Cache Components). */
export default function Page() {
  return (
    <Suspense>
      <FlightResults />
    </Suspense>
  );
}
