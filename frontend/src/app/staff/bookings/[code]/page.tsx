import { Suspense } from "react";
import type { Metadata } from "next";
import StaffBookingView from "./staff-booking-view";

export const metadata: Metadata = {
  title: "Chi tiết đặt chỗ | SkyLine",
};

/** S-02 (APP_FLOW §2.2): Staff và Admin xem chi tiết đặt chỗ, tài khoản đặt và gửi lại email vé. */
export default function StaffBookingDetailPage() {
  return (
    <Suspense>
      <StaffBookingView />
    </Suspense>
  );
}

