/** API của service agent (agent/flight_agent/app.py) qua rewrite /agent/*. Không có trong OpenAPI của Spring nên viết tay. */

import { csrfToken } from "@/lib/api";

export type Segment = {
  flightNumber: string;
  from: string;
  to: string;
  depart: string;
  arrive: string;
};

export type Card =
  | {
      type: "constraints";
      origin: string;
      destination: string;
      date: string;
      departBefore: string | null;
      maxPrice: number | null;
    }
  | {
      type: "proposal";
      proposalId: string;
      segments: Segment[];
      fare: string;
      passenger: {
        lastName: string;
        firstName: string;
        gender: "MALE" | "FEMALE";
        dateOfBirth: string;
      };
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

  let res: Response;
  try {
    res = await fetch("/agent" + path, {
      method: "POST",
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
