// Lọc, sắp xếp và định dạng kết quả tìm chuyến ở client (FR-11, FR-12). Không gọi API, không đọc giờ hiện tại.
import type { Schemas } from "@/lib/api";

export type Itinerary = Schemas["Itinerary"];
export type FareOption = Schemas["FareOption"];
export type AirportTime = Schemas["AirportTime"];

export const first = (it: Itinerary) => it.segments[0];
export const last = (it: Itinerary) => it.segments[it.segments.length - 1];

/** Khoá ổn định của hành trình: id các chặng, VD "101,205". Dùng cho key React và tham số `out` của URL. */
export const itineraryKey = (it: Itinerary) =>
  it.segments.map((s) => s.flightId).join(",");

/** Tổng giá rẻ nhất cho đúng số khách đã nhập. */
export const cheapest = (it: Itinerary) =>
  Math.min(...it.fareOptions.map((f) => f.totalPrice));

/**
 * Giờ địa phương của sân bay, VD "08:10". API trả giờ đã theo múi giờ sân bay kèm offset
 * ("2026-12-01T08:10:00+07:00"), nên đọc thẳng từ chuỗi; không qua Date nên không dính múi giờ trình duyệt.
 */
export const hhmm = (t: AirportTime) => t.time.slice(11, 16);

/** Số ngày lệch giữa hai ngày địa phương, VD cất cánh 23:30 và hạ cánh 06:15 hôm sau → 1. */
export const dayShift = (from: AirportTime, to: AirportTime) =>
  Math.round(
    (Date.parse(to.time.slice(0, 10)) - Date.parse(from.time.slice(0, 10))) /
      86_400_000,
  );

export function duration(minutes: number) {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (!h) return `${m} phút`;
  return m ? `${h} giờ ${m} phút` : `${h} giờ`;
}

const money = new Intl.NumberFormat("vi-VN");
/** VD "990.000 đ"; khoảng trắng không ngắt để "đ" không rớt xuống dòng. */
export const vnd = (amount: number) => `${money.format(amount)}\u00a0đ`;

/** BR-04: chặng đầu chiều về phải cất cánh sau giờ hạ cánh chặng cuối chiều đi. Chuỗi có offset nên so thời điểm được. */
export const departsAfter = (outbound: Itinerary) => (ret: Itinerary) =>
  Date.parse(first(ret).departure.time) >
  Date.parse(last(outbound).arrival.time);

export const DAY_PARTS = [
  { id: "night", label: "Đêm, sáng sớm", range: "00:00 đến 06:00", from: 0 },
  { id: "morning", label: "Buổi sáng", range: "06:00 đến 12:00", from: 6 },
  { id: "afternoon", label: "Buổi chiều", range: "12:00 đến 18:00", from: 12 },
  { id: "evening", label: "Buổi tối", range: "18:00 đến 24:00", from: 18 },
] as const;
export type DayPart = (typeof DAY_PARTS)[number]["id"];

/** Khung giờ cất cánh theo giờ địa phương của sân bay đi. */
export function dayPart(t: AirportTime): DayPart {
  const hour = Number(hhmm(t).slice(0, 2));
  return DAY_PARTS.findLast((p) => hour >= p.from)!.id;
}

export interface Filters {
  airlines: string[];
  stops: number[];
  dayParts: DayPart[];
  /** Giá tối đa của gói rẻ nhất; null là không giới hạn. */
  maxPrice: number | null;
}

export const NO_FILTERS: Filters = {
  airlines: [],
  stops: [],
  dayParts: [],
  maxPrice: null,
};

/** Trong một nhóm: khớp một giá trị bất kỳ đã chọn (nhóm chưa chọn gì thì bỏ qua). Giữa các nhóm: phải khớp tất cả. */
export const applyFilters = (list: Itinerary[], f: Filters) =>
  list.filter(
    (it) =>
      (!f.airlines.length || f.airlines.includes(first(it).airline.code)) &&
      (!f.stops.length || f.stops.includes(it.stops)) &&
      (!f.dayParts.length ||
        f.dayParts.includes(dayPart(first(it).departure))) &&
      (f.maxPrice === null || cheapest(it) <= f.maxPrice),
  );

export const SORTS = [
  { id: "price", label: "Giá thấp nhất" },
  { id: "departure", label: "Cất cánh sớm nhất" },
  { id: "duration", label: "Bay nhanh nhất" },
] as const;
export type SortKey = (typeof SORTS)[number]["id"];

const SORT_BY: Record<SortKey, (it: Itinerary) => number> = {
  price: cheapest,
  departure: (it) => Date.parse(first(it).departure.time),
  duration: (it) => it.totalDurationMinutes,
};

/** Bằng nhau thì hành trình rẻ hơn đứng trước. */
export const sortItineraries = (list: Itinerary[], key: SortKey) =>
  [...list].sort(
    (a, b) => SORT_BY[key](a) - SORT_BY[key](b) || cheapest(a) - cheapest(b),
  );
