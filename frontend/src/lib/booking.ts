import { useSyncExternalStore } from "react";
import type { Schemas } from "./api";

export type BookingStatus = Schemas["BookingDetail"]["status"];

/** APP_FLOW §4.1. */
export const STATUS_LABEL: Record<BookingStatus, string> = {
  PENDING_PAYMENT: "Chờ thanh toán",
  ISSUED: "Đã xuất vé",
  EXPIRED: "Hết hạn giữ chỗ",
  CANCELLED: "Đã huỷ",
  REFUNDED: "Đã hoàn vé",
};

export const DIRECTION_LABEL: Record<
  Schemas["JourneyRequest"]["direction"],
  string
> = { OUTBOUND: "Chiều đi", RETURN: "Chiều về" };

export const PASSENGER_LABEL: Record<
  Schemas["PassengerRequest"]["type"],
  string
> = { ADULT: "Người lớn", CHILD: "Trẻ em", INFANT: "Em bé" };

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
