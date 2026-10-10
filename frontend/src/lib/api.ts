/** Gọi API backend qua rewrites /api/* (next.config.ts). Mọi lời gọi ghi phải đi qua apiFetch (lộ trình §5). */

import type { components } from "./api-types";

/** Kiểu schema của backend, sinh bằng `npm run gen:api` vào api-types.ts (không sửa tay file đó). */
export type Schemas = components["schemas"];

export type User = Schemas["UserResponse"];
export type Role = User["role"];

export type FieldError = { field: string; message: string };

/** ProblemDetail (RFC 9457) của backend, có thêm code và errors (TDD §5.3, §9). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly detail: string,
    readonly errors: FieldError[] = [],
  ) {
    super(detail);
  }
}

function readCookie(name: string) {
  const prefix = name + "=";
  const hit = document.cookie.split("; ").find((c) => c.startsWith(prefix));
  return hit ? decodeURIComponent(hit.slice(prefix.length)) : undefined;
}

/** csrf.spa() của Spring Security: header phải mang giá trị trong cookie XSRF-TOKEN (TDD §4.2). */
async function csrfToken() {
  let token = readCookie("XSRF-TOKEN");
  if (!token) {
    await fetch("/api/auth/csrf", { credentials: "same-origin" });
    token = readCookie("XSRF-TOKEN");
  }
  return token;
}

export async function apiFetch<T>(
  path: string,
  init: Omit<RequestInit, "body"> & { body?: unknown } = {},
): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (init.body !== undefined) headers.set("Content-Type", "application/json");
  if (method !== "GET" && method !== "HEAD") {
    const token = await csrfToken();
    if (token) headers.set("X-XSRF-TOKEN", token);
  }

  let res: Response;
  try {
    res = await fetch("/api" + path, {
      ...init,
      method,
      headers,
      credentials: "same-origin",
      body: init.body === undefined ? undefined : JSON.stringify(init.body),
    });
  } catch {
    throw new ApiError(
      0,
      "NETWORK",
      "Không kết nối được máy chủ. Hãy thử lại.",
    );
  }

  if (res.ok) {
    // Có API trả 200 không có body (VD forgot-password), nên chỉ parse khi có nội dung.
    const body = await res.text();
    return (body ? JSON.parse(body) : undefined) as T;
  }
  const problem = await res.json().catch(() => ({}));
  throw new ApiError(
    res.status,
    problem.code ?? "INTERNAL_ERROR",
    problem.detail ?? "Đã có lỗi xảy ra. Hãy thử lại.",
    problem.errors ?? [],
  );
}

const HOME: Record<Role, string> = {
  CUSTOMER: "/bookings",
  STAFF: "/staff/bookings",
  ADMIN: "/admin/flights",
};

/** Trang mặc định sau đăng nhập (APP_FLOW §1). */
export function homeOf(role: Role) {
  return HOME[role];
}

/** Chỉ nhận đường dẫn trong site cho tham số next, chặn open redirect (//evil.com, /\evil.com, https://...). */
export function safeNext(next: string | null, role: Role) {
  if (
    next &&
    next.startsWith("/") &&
    !next.startsWith("//") &&
    !next.startsWith("/\\")
  ) {
    return next;
  }
  return homeOf(role);
}
