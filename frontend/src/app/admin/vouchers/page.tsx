import VoucherList from "./voucher-list";

export const metadata = {
  title: "Quản lý voucher | SkyLine Admin",
};

/** A-07 (APP_FLOW §2.3): danh sách, tạo và sửa voucher. */
export default function AdminVouchersPage() {
  return <VoucherList />;
}
