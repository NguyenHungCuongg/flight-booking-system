import type { Metadata } from "next";
import PolicyPage from "../policy-page";

export const metadata: Metadata = { title: "Điều khoản & điều kiện | SkyLine" };

export default function Page() {
  return <PolicyPage slug="dieu-khoan" />;
}
