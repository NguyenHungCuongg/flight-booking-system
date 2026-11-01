import type { Metadata } from "next";
import CheckoutView from "./checkout-view";

export const metadata: Metadata = { title: "Đặt vé | SkyLine" };

/** C-07 (APP_FLOW §2.1): Customer. proxy.ts chặn khi chưa có cookie phiên; lựa chọn hành trình nằm ở sessionStorage. */
export default function Page() {
  return <CheckoutView />;
}
