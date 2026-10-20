import { Suspense } from "react";
import type { Metadata } from "next";
import ManifestView from "./manifest-view";

export const metadata: Metadata = {
  title: "Hành khách theo chuyến | SkyLine",
};

/** S-06 (APP_FLOW §2.2): Staff và Admin xem danh sách hành khách theo chuyến bay (manifest). */
export default function FlightPassengersPage() {
  return (
    <Suspense>
      <ManifestView />
    </Suspense>
  );
}

