import { expect, test } from "@playwright/test";
import { flightsUrl, formFromParams } from "../src/lib/search-query";
import type { Journey, Selection } from "../src/lib/selection";

// Unit test cho việc chuyển đổi URL tìm chuyến: không mở trình duyệt, không cần backend.

const seg = (from: string, dep: string, to: string) => ({
  departure: { airport: from, time: dep },
  arrival: { airport: to },
});

const journey = (
  direction: Journey["direction"],
  segments: ReturnType<typeof seg>[],
) =>
  ({
    direction,
    itinerary: { segments },
    fare: { cabinClass: "ECONOMY" },
  }) as unknown as Journey;

const outbound = journey("OUTBOUND", [
  seg("HAN", "2026-11-02T08:00:00+07:00", "DAD"),
  seg("DAD", "2026-11-02T11:00:00+07:00", "SGN"),
]);
const pax = { adults: 1, children: 0, infants: 0 };

test("formFromParams đọc đủ điều kiện khứ hồi", () => {
  const p = new URLSearchParams(
    "from=HAN&to=SGN&date=2026-11-02&returnDate=2026-11-05&adults=2&children=1&infants=0&cabin=BUSINESS",
  );
  expect(formFromParams(p)).toEqual({
    trip: "round",
    from: "HAN",
    to: "SGN",
    dep: "2026-11-02",
    ret: "2026-11-05",
    adults: 2,
    children: 1,
    infants: 0,
    cabin: "BUSINESS",
  });
});

test("formFromParams không có tham số thì chỉ là một chiều", () => {
  expect(formFromParams(new URLSearchParams())).toEqual({ trip: "oneway" });
});

test("formFromParams bỏ qua hạng ghế và số khách sai", () => {
  expect(formFromParams(new URLSearchParams("cabin=XYZ&adults=abc"))).toEqual({
    trip: "oneway",
  });
});

test("flightsUrl một chiều nối chuyến lấy sân bay đến của chặng cuối", () => {
  const sel: Selection = { ...pax, journeys: [outbound] };
  expect(flightsUrl(sel)).toBe(
    "/flights?from=HAN&to=SGN&date=2026-11-02&adults=1&children=0&infants=0&cabin=ECONOMY",
  );
});

test("flightsUrl khứ hồi thêm returnDate theo chặng đầu của chiều về", () => {
  const back = journey("RETURN", [
    seg("SGN", "2026-11-05T17:00:00+07:00", "HAN"),
  ]);
  const sel: Selection = { ...pax, journeys: [outbound, back] };
  expect(flightsUrl(sel)).toBe(
    "/flights?from=HAN&to=SGN&date=2026-11-02&adults=1&children=0&infants=0&cabin=ECONOMY&returnDate=2026-11-05",
  );
});
