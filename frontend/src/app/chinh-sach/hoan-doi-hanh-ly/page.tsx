import type { Metadata } from "next";
import PolicyPage from "../policy-page";

export const metadata: Metadata = {
  title: "Chính sách hoàn, đổi vé và hành lý | SkyLine",
};

export default function Page() {
  return <PolicyPage slug="hoan-doi-hanh-ly" />;
}
