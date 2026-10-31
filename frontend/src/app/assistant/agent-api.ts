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
        baggage: { weightKg: number; count: number; total: number } | null;
      }[];
      passengers: {
        lastName: string;
        firstName: string;
        gender: "MALE" | "FEMALE";
        dateOfBirth: string;
        type: PassengerType;
      }[];
      contact: { name: string; email: string; phone: string };
      discount: { code: string; amount: number } | null;
      totalAmount: number;
    }
  | { type: "held"; code: string; holdExpiresAt: string; totalAmount: number }
  | { type: "handoff"; doneSoFar: string[]; tried: string[]; question: string };

export type Turn = {
  reply: string;
  trace: { tool: string; args: Record<string, unknown>; status: string }[];
  cards: Card[];
};

export type Step = Turn["trace"][number];

/** Sự kiện của endpoint gửi tin (text/event-stream): mỗi bước tool xong, cuối cùng là kết quả lượt. */
export type StreamEvent =
  | ({ type: "step" } & Step)
  | { type: "turn"; turn: Turn };

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
  return agentFetch("POST", path, await postHeaders(body), body);
}

export function agentGet<T>(path: string): Promise<T> {
  return agentFetch("GET", path, new Headers());
}

/** POST rồi đọc stream: gọi onStep cho mỗi bước tool, trả kết quả lượt. Stream đóng mà chưa có kết quả thì là lỗi. */
export async function agentStream(
  path: string,
  body: unknown,
  onStep: (step: Step) => void,
): Promise<Turn> {
  const res = await agentResponse("POST", path, await postHeaders(body), body);
  try {
    for await (const event of sseEvents(res.body!)) {
      if (event.type === "turn") return event.turn;
      onStep({ tool: event.tool, args: event.args, status: event.status });
    }
  } catch {
    // mất kết nối giữa chừng: báo như stream đóng sớm
  }
  throw new AgentError(0, DOWN);
}

/** Tách stream thành từng sự kiện `data: <JSON>`; một sự kiện có thể bị cắt giữa hai chunk. */
export async function* sseEvents(
  body: ReadableStream<Uint8Array>,
): AsyncGenerator<StreamEvent> {
  // Đọc bằng reader: Safari chưa cho duyệt ReadableStream bằng for await.
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let rest = "";
  for (;;) {
    const { done, value } = await reader.read();
    if (done) return;
    // stream: true giữ lại byte dở của một chữ có dấu cho chunk sau
    const blocks = (rest + decoder.decode(value, { stream: true })).split(
      "\n\n",
    );
    rest = blocks.pop()!;
    for (const block of blocks)
      yield JSON.parse(block.replace(/^data: /, "")) as StreamEvent;
  }
}

async function postHeaders(body: unknown) {
  const headers = new Headers();
  if (body !== undefined) headers.set("Content-Type", "application/json");
  const token = await csrfToken();
  if (token) headers.set("X-XSRF-TOKEN", token);
  return headers;
}

async function agentFetch<T>(
  method: string,
  path: string,
  headers: Headers,
  body?: unknown,
): Promise<T> {
  return (await agentResponse(method, path, headers, body)).json() as T;
}

async function agentResponse(
  method: string,
  path: string,
  headers: Headers,
  body?: unknown,
): Promise<Response> {
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
  if (res.ok) return res;
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
