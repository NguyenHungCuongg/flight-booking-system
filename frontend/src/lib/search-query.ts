import { first, last } from "@/app/flights/itineraries";
import { CABINS } from "@/components/landing/data";
import type { SearchForm } from "@/components/landing/search-context";
import type { Selection } from "./selection";

const count = (v: string | null) => {
  const n = v === null || v === "" ? NaN : Number(v);
  return Number.isInteger(n) && n >= 0 ? n : undefined;
};

/** Giá trị ban đầu của ô tìm trên C-02, đọc từ query string. Tham số thiếu hoặc sai thì bỏ, để form dùng mặc định. */
export function formFromParams(params: URLSearchParams): Partial<SearchForm> {
  const ret = params.get("returnDate");
  const form: Partial<SearchForm> = { trip: ret ? "round" : "oneway" };
  const from = params.get("from");
  const to = params.get("to");
  const dep = params.get("date");
  if (from) form.from = from;
  if (to) form.to = to;
  if (dep) form.dep = dep;
  if (ret) form.ret = ret;
  for (const key of ["adults", "children", "infants"] as const) {
    const n = count(params.get(key));
    if (n !== undefined) form[key] = n;
  }
  const cabin = CABINS.find((c) => c.id === params.get("cabin"));
  if (cabin) form.cabin = cabin.id;
  return form;
}

/**
 * URL C-02 dựng lại từ lựa chọn đã lưu, để C-07 quay về đúng kết quả tìm. Ngày lấy thẳng từ chuỗi giờ cất cánh:
 * OffsetDateTime của API đã ở múi giờ sân bay (TDD §5).
 */
export function flightsUrl(selection: Selection): string {
  const [out, back] = selection.journeys;
  const q = new URLSearchParams({
    from: first(out.itinerary).departure.airport,
    to: last(out.itinerary).arrival.airport,
    date: first(out.itinerary).departure.time.slice(0, 10),
    adults: String(selection.adults),
    children: String(selection.children),
    infants: String(selection.infants),
    cabin: out.fare.cabinClass,
  });
  if (back)
    q.set("returnDate", first(back.itinerary).departure.time.slice(0, 10));
  return `/flights?${q}`;
}
