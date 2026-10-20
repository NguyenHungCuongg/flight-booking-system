/**
 * Hàm thuần và hằng số cho danh sách và thao tác nghiệp vụ của màn Staff (S-01–S-06).
 */

import { CABINS } from "../components/landing/data";
import type { Schemas } from "./api";

/**
 * Trạng thái chuyến bay hiển thị trên giao diện Staff (S-05, S-06).
 */
export const FLIGHT_STATUS: Record<Schemas["FlightView"]["status"], string> = {
  SCHEDULED: "Theo lịch",
  CANCELLED: "Đã huỷ",
};

/**
 * Tên phương thức / nhà cung cấp dịch vụ hoàn tiền (S-04).
 */
export const REFUND_PROVIDER: Record<string, string> = {
  VNPAY: "VNPay",
  MANUAL: "Thủ công",
};

/**
 * Lấy nhãn tiếng Việt của hạng ghế từ CABINS, không tìm thấy thì giữ nguyên mã id.
 */
export function cabinLabel(id: string): string {
  return CABINS.find((c) => c.id === id)?.label ?? id;
}

/**
 * Đọc số trang từ URL params (đếm từ 1 như C-08).
 * Nếu thiếu, không hợp lệ hoặc nhỏ hơn 1 thì trả về 1.
 */
export function pageOf(params: URLSearchParams): number {
  const raw = params.get("page");
  if (!raw) return 1;
  const n = Math.floor(Number(raw));
  return Number.isFinite(n) && n >= 1 ? n : 1;
}

/**
 * Chuyển bộ lọc trên URL thành chuỗi query gửi lên API backend:
 * - Bỏ mọi ô rỗng hoặc chỉ có khoảng trắng (vì `<Form>` của `next/form` gửi ô trống thành `key=`,
 *   trong khi backend so khớp nguyên văn như `f.departure_airport = :from` nên gửi rỗng sẽ làm bảng rỗng).
 * - Giữ đúng thứ tự các khoá trong `keys`.
 * - Đổi trang URL (đếm từ 1) sang trang API backend (đếm từ 0): `page = pageOf - 1`.
 */
export function listQuery(
  params: URLSearchParams,
  keys: readonly string[],
): string {
  const out = new URLSearchParams();
  for (const key of keys) {
    const val = params.get(key)?.trim();
    if (val) {
      out.set(key, val);
    }
  }
  out.set("page", String(pageOf(params) - 1));
  return out.toString();
}

/**
 * Tạo URL dẫn tới trang `page` mới, giữ nguyên các bộ lọc khác rỗng và loại bỏ `page` cũ.
 * Nếu `page === 1`, không ghi tham số `page` lên URL.
 * Nếu không còn tham số nào thì chỉ trả về `path`.
 */
export function pageHref(
  path: string,
  params: URLSearchParams,
  page: number,
): string {
  const out = new URLSearchParams();
  for (const [key, val] of params.entries()) {
    if (key === "page") continue;
    const trimmed = val.trim();
    if (trimmed) {
      out.append(key, trimmed);
    }
  }
  if (page > 1) {
    out.set("page", String(page));
  }
  const qs = out.toString();
  return qs ? `${path}?${qs}` : path;
}

/**
 * Xác định các thao tác khả dụng trên màn S-04 theo bảng "S-04 thao tác" (BR-62, BR-63, BR-64, BR-66):
 * - `approve`: Yêu cầu đang `PENDING`.
 * - `reject`: Yêu cầu đang `PENDING` và nguồn hoàn do khách gửi `CUSTOMER` (BR-66).
 * - `settle`: ("Thử lại" và "Xác nhận hoàn thủ công"): Yêu cầu đang `APPROVED` (BR-64).
 */
export function refundActions(
  status: string,
  source: string,
): { approve: boolean; reject: boolean; settle: boolean } {
  return {
    approve: status === "PENDING",
    reject: status === "PENDING" && source === "CUSTOMER",
    settle: status === "APPROVED",
  };
}

