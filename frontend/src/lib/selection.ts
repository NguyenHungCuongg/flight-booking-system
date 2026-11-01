import { useMemo, useSyncExternalStore } from "react";
import type { Schemas } from "./api";

/**
 * Lựa chọn hành trình ở C-02, lưu ở sessionStorage (TDD §7) để C-07 đọc lại (useSelection). `direction`, `flightIds`,
 * `fareFamilyId` đúng dạng `journeys` của POST /bookings/quote và /bookings (TDD §8.8); `itinerary`, `fare` để hiển thị.
 */
export interface Journey {
  direction: "OUTBOUND" | "RETURN";
  flightIds: number[];
  fareFamilyId: number;
  itinerary: Schemas["Itinerary"];
  fare: Schemas["FareOption"];
}

export interface Selection {
  adults: number;
  children: number;
  infants: number;
  journeys: Journey[];
}

export const SELECTION_KEY = "skyline.selection";

export function saveSelection(selection: Selection) {
  sessionStorage.setItem(SELECTION_KEY, JSON.stringify(selection));
}

/** Lựa chọn đã lưu; null khi không có hoặc hỏng (VD ai đó sửa tay sessionStorage). */
export function parseSelection(raw: string | null): Selection | null {
  if (!raw) return null;
  try {
    const s = JSON.parse(raw) as Selection | null;
    return s && Array.isArray(s.journeys) && s.journeys.length > 0 ? s : null;
  } catch {
    return null;
  }
}

const subscribeNone = () => () => {};

/** C-07 đọc lựa chọn sau khi hydrate: undefined khi prerender (server không có sessionStorage), null khi không có. */
export function useSelection() {
  const raw = useSyncExternalStore<string | null | undefined>(
    subscribeNone,
    () => sessionStorage.getItem(SELECTION_KEY),
    () => undefined,
  );
  return useMemo(
    () => (raw === undefined ? undefined : parseSelection(raw)),
    [raw],
  );
}

/** Giữ chỗ xong thì xoá, để mở lại /checkout không giữ chỗ lần nữa được. */
export function clearSelection() {
  sessionStorage.removeItem(SELECTION_KEY);
}
