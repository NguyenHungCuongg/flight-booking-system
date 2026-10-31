import type { Metadata } from "next";
import AssistantChat from "./assistant-chat";

export const metadata: Metadata = { title: "Trợ lý đặt vé | SkyLine" };

/** Trợ lý đặt vé (thử nghiệm, nhánh feature/flight-agent): chat với service agent, khách tự duyệt giữ chỗ. */
export default function Page() {
  return <AssistantChat />;
}
