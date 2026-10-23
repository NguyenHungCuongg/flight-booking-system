import ReportsView from "./reports-view";

export const metadata = {
  title: "Báo cáo thống kê | SkyLine Admin",
};

/** A-10 (APP_FLOW §2.3): báo cáo dòng tiền, doanh số vé và tỉ lệ lấp đầy. */
export default function AdminReportsPage() {
  return <ReportsView />;
}
