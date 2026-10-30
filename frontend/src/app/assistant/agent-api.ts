/** API của service agent (agent/flight_agent/app.py) qua rewrite /agent/*. Không có trong OpenAPI của Spring nên viết tay. */

import { csrfToken } from "@/lib/api";

export type Segment = {
  flightNumber: string;
  from: string;
  to: string;
  depart: string;
  arrive: string;
};

export type PassengerType = "ADULT" | "CHILD" | "INFANT";

export type Card =
  | {
      type: "constraints";
      origin: string;
      destination: string;
      date: string;
      returnDate: string | null;
      departBefore: string | null;
      maxPrice: number | null;
      adults: number;
      children: number;
      infants: number;
    }
  | {
      type: "proposal";
      proposalId: string;
      journeys: {
        direction: "OUTBOUND" | "RETURN";
        segments: Segment[];
        fare: string;
        fares: {
          type: PassengerType;
          count: number;
          unitPrice: number;
          total: number;
        }[];
      }[];
      passengers: {
        lastName: string;
        firstName: string;
        gender: "MALE" | "FEMALE";
        dateOfBirth: string;
        type: PassengerType;
      }[];
      contact: { name: string; email: string; phone: string };
      totalAmount: number;
    }
  | { type: "held"; code: string; holdExpiresAt: string; totalAmount: number }
  | { type: "handoff"; doneSoFar: string[]; tried: string[]; question: string };

export type Turn = {
  reply: string;
  trace: { tool: string; args: Record<string, unknown>; status: string }[];
  cards: Card[];
};

export type Entry =
  | { from: "user"; text: string }
  | { from: "agent"; turn: Turn };

/** GET /agent/chats/{id}: các lượt đã hiển thị và đề xuất còn duyệt được (null khi không còn). */
export type ChatView = { entries: Entry[]; proposalId: string | null };

export class AgentError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

const DOWN = "Trợ lý đang không hoạt động. Hãy thử lại sau.";

/** POST tới service agent. Gửi X-XSRF-TOKEN như apiFetch: service chỉ chuyển tiếp header này, không tự đọc cookie. */
export async function agentPost<T>(path: string, body?: unknown): Promise<T> {
  const headers = new Headers();
  if (body !== undefined) headers.set("Content-Type", "application/json");
  const token = await csrfToken();
  if (token) headers.set("X-XSRF-TOKEN", token);
  return agentFetch("POST", path, headers, body);
}

export function agentGet<T>(path: string): Promise<T> {
  return agentFetch("GET", path, new Headers());
}

async function agentFetch<T>(
  method: string,
  path: string,
  headers: Headers,
  body?: unknown,
): Promise<T> {
  let res: Response;
  try {
    res = await fetch("/agent" + path, {
      method,
      headers,
      credentials: "same-origin",
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new AgentError(0, DOWN);
  }
  if (res.ok) return (await res.json()) as T;
  if ([500, 502, 503, 504].includes(res.status))
    throw new AgentError(res.status, DOWN);
  const problem = await res.json().catch(() => ({}));
  throw new AgentError(
    res.status,
    typeof problem.detail === "string"
      ? problem.detail
      : "Đã có lỗi xảy ra. Hãy thử lại.",
  );
}
