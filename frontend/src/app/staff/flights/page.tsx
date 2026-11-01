import { Suspense } from "react";
import PageLoading from "@/components/page-loading";
import type { Metadata } from "next";
import FlightList from "./flight-list";

export const metadata: Metadata = {
  title: "Chuyến bay | SkyLine",
};

/** S-05 (APP_FLOW §2.2): Staff và Admin tra cứu và xem danh sách chuyến bay. */
export default function StaffFlightsPage() {
  return (
    <Suspense fallback={<PageLoading />}>
      <FlightList />
    </Suspense>
  );
}

