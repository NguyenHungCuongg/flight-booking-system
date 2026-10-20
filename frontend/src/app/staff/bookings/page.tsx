import { Suspense } from "react";
import type { Metadata } from "next";
import BookingSearch from "./booking-search";

export const metadata: Metadata = {
  title: "Tra cứu đặt chỗ | SkyLine",
};

/** S-01 (APP_FLOW §2.2): Staff và Admin tra cứu đặt chỗ theo mã, email hoặc số điện thoại. */
export default function StaffBookingsPage() {
  return (
    <Suspense>
      <BookingSearch />
    </Suspense>
  );
}

