import type { Metadata } from "next";
import PolicyPage from "../policy-page";

export const metadata: Metadata = { title: "Chính sách quyền riêng tư | SkyLine" };

export default function Page() {
  return <PolicyPage slug="quyen-rieng-tu" />;
}
