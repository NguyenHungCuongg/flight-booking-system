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

export type MemoryPassenger = {
  fullName: string;
  gender: "MALE" | "FEMALE";
  dateOfBirth: string;
  bookingCode: string;
};

export type ChatSummary = {
  chatId: string;
  title: string;
  updatedAt: string;
};

export type Card =
  | {
      type: "constraints";
      origin: string;
      destination: string;
      date: string;
      returnDate: string | null;
      departBefore: string | null;
      departAfter?: string | null;
      maxPrice: number | null;
      cabin?: string;
      airline?: string | null;
      nonstop?: boolean;
      refundable?: boolean;
      changeable?: boolean;
      adults: number;
      children: number;
      infants: number;
    }
  | {
      type: "memory_used";
      source: "recent_bookings";
      passengers: MemoryPassenger[];
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
  | {
      type: "cancel_proposal";
      proposalId: string;
      code: string;
      routes: string[];
      totalAmount: number;
    }
  | { type: "cancelled"; code: string }
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
export async function agentPost<T>(
  path: string,
  body?: unknown,
  signal?: AbortSignal,
): Promise<T> {
  return agentFetch("POST", path, await postHeaders(body), body, signal);
}

export function agentGet<T>(path: string, signal?: AbortSignal): Promise<T> {
  return agentFetch("GET", path, new Headers(), undefined, signal);
}

/** DELETE tới service agent (trả 204 No Content). Gửi X-XSRF-TOKEN như các thao tác ghi khác. */
export async function agentDelete(path: string): Promise<void> {
  await agentResponse("DELETE", path, await postHeaders(undefined));
}

/** Chuyển tên tool kỹ thuật sang mô tả tiếng Việt ngắn gọn cho người dùng cuối. */
export function describeStep(step: Step): string {
  const code =
    typeof step.args.code === "string" && step.args.code.trim()
      ? step.args.code.trim().toUpperCase()
      : null;
  const airline =
    typeof step.args.airline === "string" && step.args.airline.trim()
      ? step.args.airline.trim().toUpperCase()
      : null;
  let label: string;
  switch (step.tool) {
    case "set_constraints":
      label = "Cập nhật yêu cầu tìm chuyến";
      break;
    case "search_flights":
      label = "Tìm chuyến bay phù hợp";
      break;
    case "list_recent_passengers":
      label = "Tra cứu hành khách từ các đặt chỗ gần đây";
      break;
    case "propose_hold":
      label = "Tính giá và tạo thẻ duyệt giữ chỗ";
      break;
    case "list_my_bookings":
      label = "Xem danh sách đặt chỗ của bạn";
      break;
    case "get_booking":
      label = code ? `Xem chi tiết đặt chỗ ${code}` : "Xem chi tiết đặt chỗ";
      break;
    case "refund_quote":
      label = code ? `Tính số tiền hoàn vé cho đơn ${code}` : "Tính số tiền hoàn vé";
      break;
    case "reschedule_options":
      label = code
        ? `Tìm chuyến thay thế để đổi vé cho đơn ${code}`
        : "Tìm chuyến thay thế để đổi vé";
      break;
    case "propose_cancel":
      label = code
        ? `Tạo thẻ duyệt huỷ giữ chỗ ${code}`
        : "Tạo thẻ duyệt huỷ giữ chỗ";
      break;
    case "read_policy":
      label = "Đọc quy định chính sách SkyLine";
      break;
    case "airline_rules":
      label = airline
        ? `Tra cứu quy định hãng ${airline}`
        : "Tra cứu quy định hãng bay";
      break;
    default:
      label = step.tool;
  }
  if (step.status === "invalid_param") return `${label} (điều chỉnh tham số)`;
  if (step.status === "denied") return `${label} (không thoả điều kiện)`;
  if (step.status === "backend_error") return `${label} (hệ thống báo lỗi)`;
  return label;
}

/** POST rồi đọc stream: gọi onStep cho mỗi bước tool, trả kết quả lượt. Stream đóng mà chưa có kết quả thì là lỗi. */
export async function agentStream(
  path: string,
  body: unknown,
  onStep: (step: Step) => void,
  signal?: AbortSignal,
): Promise<Turn> {
  const res = await agentResponse(
    "POST",
    path,
    await postHeaders(body),
    body,
    signal,
  );
  try {
    for await (const event of sseEvents(res.body!)) {
      if (event.type === "turn") return event.turn;
      onStep({ tool: event.tool, args: event.args, status: event.status });
    }
  } catch (e) {
    if (signal?.aborted) throw e;
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

// Đường dẫn nội bộ trong câu trả lời (nguồn trích dẫn, trang tự thao tác); dấu câu cuối câu không thuộc link.
const INTERNAL_LINK =
  /(\/(?:chinh-sach|flights|bookings)\b(?:\/[A-Za-z0-9_-]+)*(?:\?[A-Za-z0-9_=&-]+)?(?:#[A-Za-z0-9_-]+)?)/;

/** Câu trả lời là văn bản thuần: tách các đường dẫn nội bộ ra để hiện thành link. */
export function replyParts(text: string): (string | { href: string })[] {
  return text
    .split(INTERNAL_LINK)
    .map((part, i) => (i % 2 ? { href: part } : part))
    .filter((part) => part !== "");
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
  signal?: AbortSignal,
): Promise<T> {
  return (await agentResponse(method, path, headers, body, signal)).json() as T;
}

async function agentResponse(
  method: string,
  path: string,
  headers: Headers,
  body?: unknown,
  signal?: AbortSignal,
): Promise<Response> {
  let res: Response;
  try {
    res = await fetch("/agent" + path, {
      method,
      headers,
      credentials: "same-origin",
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    });
  } catch (e) {
    if (signal?.aborted) throw e;
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
