import { Suspense } from "react";
import UserList from "./user-list";

export const metadata = {
  title: "Quản lý tài khoản | SkyLine Admin",
};

/** A-08 (APP_FLOW §2.3): tìm tài khoản, tạo Staff/Admin, khoá và mở khoá. */
export default function AdminUsersPage() {
  return (
    <Suspense>
      <UserList />
    </Suspense>
  );
}
