import { useSyncExternalStore } from "react";
import type { Schemas } from "./api";

export type BookingStatus = Schemas["BookingDetail"]["status"];
export type Direction = Schemas["JourneyRequest"]["direction"];

/** APP_FLOW §4.1. */
export const STATUS_LABEL: Record<BookingStatus, string> = {
  PENDING_PAYMENT: "Chờ thanh toán",
  ISSUED: "Đã xuất vé",
  EXPIRED: "Hết hạn giữ chỗ",
  CANCELLED: "Đã huỷ",
  REFUNDED: "Đã hoàn vé",
};

export const DIRECTION_LABEL: Record<Direction, string> = {
  OUTBOUND: "Chiều đi",
  RETURN: "Chiều về",
};

export const PASSENGER_LABEL: Record<
  Schemas["PassengerRequest"]["type"],
  string
> = { ADULT: "Người lớn", CHILD: "Trẻ em", INFANT: "Em bé" };

/** APP_FLOW §4.5. */
export const REFUND_STATUS: Record<string, string> = {
  PENDING: "Chờ duyệt",
  APPROVED: "Đã duyệt, đang hoàn tiền",
  REJECTED: "Bị từ chối",
  COMPLETED: "Đã hoàn tiền",
};

export const REFUND_SOURCE: Record<string, string> = {
  CUSTOMER: "Khách yêu cầu",
  FLIGHT_CANCELLED: "Chuyến bay bị huỷ",
  INVALID_PAYMENT: "Thanh toán trễ hoặc trùng",
};

/** APP_FLOW §4.6. */
export const RESCHEDULE_STATUS: Record<string, string> = {
  PENDING_PAYMENT: "Chờ thanh toán",
  COMPLETED: "Hoàn tất",
  EXPIRED: "Hết hạn thanh toán",
  CANCELLED: "Đã huỷ",
};

export const PAYMENT_STATUS: Record<string, string> = {
  PENDING: "đang xử lý",
  SUCCESS: "thành công",
  FAILED: "thất bại",
};

/** "08:00 16/10/2026", theo giờ trong chuỗi. */
export const at = (iso: string) => `${clock(iso)} ${dmy(iso)}`;

/** Tham số `[direction]` trên URL của C-10, C-11 (luôn viết thường) sang enum của API. */
export function directionParam(param: string): Direction | null {
  if (param === "outbound") return "OUTBOUND";
  if (param === "return") return "RETURN";
  return null;
}

/**
 * Quy tắc hiện nút "Hoàn chiều này", "Đổi chiều này" trên C-09 (BR-51, BR-60, BR-70). Không kiểm tra hạn chót
 * `aftersales.min_hours_before_departure` vì tham số đó không có trong `BookingDetail`; khi quá hạn, C-10 và C-11 hiện
 * câu `DEADLINE_PASSED` của backend.
 */
export function journeyActions(
  booking: Schemas["BookingDetail"],
  journey: Schemas["JourneyView"],
  now: number | null,
): { refund: boolean; change: boolean } {
  const openRefund = booking.refundRequests.some(
    (r) =>
      r.direction === journey.direction &&
      (r.status === "PENDING" || r.status === "APPROVED"),
  );
  const openReschedule = booking.reschedules.some(
    (r) => r.direction === journey.direction && r.status === "PENDING_PAYMENT",
  );
  const eligible =
    now !== null &&
    booking.status === "ISSUED" &&
    journey.status === "ACTIVE" &&
    Date.parse(journey.segments[0].departureTime) > now &&
    !openRefund &&
    !openReschedule;
  return {
    refund: eligible && journey.refundable,
    change: eligible && journey.changeable,
  };
}

/**
 * Ngày và giờ đọc thẳng từ chuỗi có offset của API (giờ sân bay, hoặc giờ Việt Nam cho các mốc của booking), không qua
 * Date nên không dính múi giờ trình duyệt. "2026-10-16T08:00:00+07:00" → "16/10/2026" và "08:00". `dmy` nhận cả chuỗi
 * chỉ có ngày ("1990-05-20").
 */
export const dmy = (iso: string) =>
  `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}`;
export const clock = (iso: string) => iso.slice(11, 16);

const two = (n: number) => String(n).padStart(2, "0");

/** Thời gian còn lại dạng "mm:ss"; hết giờ là "00:00". */
export function countdown(ms: number) {
  const s = Math.max(0, Math.floor(ms / 1000));
  return `${two(Math.floor(s / 60))}:${two(s % 60)}`;
}

const everySecond = (tick: () => void) => {
  const id = setInterval(tick, 1000);
  return () => clearInterval(id);
};

/**
 * Giờ hiện tại (ms, tròn giây), cập nhật mỗi giây sau khi hydrate. Ở server trả null: Cache Components không cho đọc
 * giờ khi prerender. Snapshot tròn giây nên giữ nguyên giữa hai lần React đọc trong cùng một giây.
 */
export function useNow() {
  return useSyncExternalStore<number | null>(
    everySecond,
    () => Math.floor(Date.now() / 1000) * 1000,
    () => null,
  );
}
