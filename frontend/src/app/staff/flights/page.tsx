import { Suspense } from "react";
import type { Metadata } from "next";
import FlightList from "./flight-list";

export const metadata: Metadata = {
  title: "Chuyến bay | SkyLine",
};

/** S-05 (APP_FLOW §2.2): Staff và Admin tra cứu và xem danh sách chuyến bay. */
export default function StaffFlightsPage() {
  return (
    <Suspense>
      <FlightList />
    </Suspense>
  );
}

