import { Suspense } from "react";
import PageLoading from "@/components/page-loading";
import AdminFlightList from "./flight-list";

export const metadata = {
  title: "Quản lý chuyến bay | SkyLine Admin",
};

/** A-04 (APP_FLOW §2.3): danh sách chuyến bay của Admin, trang mặc định sau đăng nhập. */
export default function AdminFlightsPage() {
  return (
    <Suspense fallback={<PageLoading />}>
      <AdminFlightList />
    </Suspense>
  );
}
