import type { Metadata } from "next";
import AccountView from "./account-view";

export const metadata: Metadata = { title: "Tài khoản | SkyLine" };

/** C-12 (APP_FLOW §2.1): mọi vai trò đã đăng nhập. proxy.ts chặn khi chưa có cookie phiên. */
export default function Page() {
  return <AccountView />;
}
