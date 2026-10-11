import type { Schemas } from "./api";

/**
 * Lựa chọn hành trình ở C-02, lưu ở sessionStorage (TDD §7) để C-07 (Plan 13) đọc lại. `direction`, `flightIds`,
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
