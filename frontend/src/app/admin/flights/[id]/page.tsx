import { Suspense } from "react";
import FlightDetailView from "./flight-detail";

export const metadata = {
  title: "Chi tiết chuyến bay | SkyLine Admin",
};

/** A-06 (APP_FLOW §2.3): kho ghế, sửa, huỷ hai bước và xoá chuyến bay. */
export default function AdminFlightDetailPage() {
  return (
    <Suspense>
      <FlightDetailView />
    </Suspense>
  );
}
