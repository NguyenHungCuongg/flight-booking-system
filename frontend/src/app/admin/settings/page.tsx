import SettingsForm from "./settings-form";

export const metadata = {
  title: "Tham số hệ thống | SkyLine Admin",
};

/** A-09 (APP_FLOW §2.3): xem và sửa 8 tham số nghiệp vụ trong khoảng hợp lệ. */
export default function AdminSettingsPage() {
  return <SettingsForm />;
}
