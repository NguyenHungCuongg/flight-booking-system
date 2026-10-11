import type { Metadata } from "next";
import PlaceholderPage from "@/components/placeholder-page";

export const metadata: Metadata = { title: "Đặt vé | SkyLine" };

/** C-07 (APP_FLOW §2.1), Plan 13 làm. Lựa chọn hành trình đã nằm trong sessionStorage (src/lib/selection.ts). */
export default function Page() {
  return <PlaceholderPage code="C-07" title="Đặt vé" />;
}
