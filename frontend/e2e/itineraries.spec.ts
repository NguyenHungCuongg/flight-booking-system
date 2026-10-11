import { expect, test } from "@playwright/test";
import {
  NO_FILTERS,
  applyFilters,
  dayPart,
  dayShift,
  departsAfter,
  duration,
  sortItineraries,
  type Itinerary,
} from "../src/app/flights/itineraries";

// Unit test cho logic lọc, sắp xếp của C-02: không mở trình duyệt, không cần backend.

const at = (time: string, airport = "HAN") => ({
  airport,
  city: airport,
  time,
  timezone: "Asia/Ho_Chi_Minh",
});

/** Hành trình một chặng, giá rẻ nhất `price`. */
function trip(
  dep: string,
  arr: string,
  price: number,
  minutes = 120,
  airline = "VN",
): Itinerary {
  return {
    stops: 0,
    totalDurationMinutes: minutes,
    segments: [
      {
        flightId: price,
        flightNumber: `${airline}${price}`,
        airline: { code: airline, name: airline },
        departure: at(dep),
        arrival: at(arr, "SGN"),
        durationMinutes: minutes,
        aircraftModel: "A321",
        layoverMinutesAfter: 0,
      },
    ],
    fareOptions: [price + 500, price].map((totalPrice) => ({
      fareFamilyId: totalPrice,
      name: "Eco",
      cabinClass: "ECONOMY",
      carryOnKg: 7,
      checkedBaggageKg: 0,
      refundable: false,
      refundFee: 0,
      changeable: true,
      changeFee: 0,
      adultPrice: totalPrice,
      totalPrice,
      seatsLeft: 9,
    })),
  };
}

test("BR-04: chiều về cất cánh đúng giờ hạ cánh của chiều đi thì không được chọn", () => {
  const out = trip("2026-12-01T10:00:00+07:00", "2026-12-01T12:10:00+07:00", 1);
  const ok = departsAfter(out);
  expect(
    ok(trip("2026-12-01T12:11:00+07:00", "2026-12-01T14:00:00+07:00", 2)),
  ).toBe(true);
  expect(
    ok(trip("2026-12-01T12:10:00+07:00", "2026-12-01T14:00:00+07:00", 3)),
  ).toBe(false);
  // Khác múi giờ: 13:05 giờ Singapore là 12:05 giờ Việt Nam, vẫn trước 12:10.
  expect(
    ok(trip("2026-12-01T13:05:00+08:00", "2026-12-01T15:00:00+08:00", 4)),
  ).toBe(false);
});

test("khung giờ cất cánh theo giờ địa phương, 06:00 thuộc buổi sáng", () => {
  expect(dayPart(at("2026-12-01T05:59:00+07:00"))).toBe("night");
  expect(dayPart(at("2026-12-01T06:00:00+07:00"))).toBe("morning");
  expect(dayPart(at("2026-12-01T12:00:00+08:00"))).toBe("afternoon");
  expect(dayPart(at("2026-12-01T23:30:00+07:00"))).toBe("evening");
});

test("hạ cánh hôm sau thì lệch 1 ngày, kể cả qua cuối tháng", () => {
  expect(
    dayShift(at("2026-11-30T23:30:00+07:00"), at("2026-12-01T06:15:00+09:00")),
  ).toBe(1);
  expect(
    dayShift(at("2026-12-01T09:30:00+07:00"), at("2026-12-01T12:30:00+08:00")),
  ).toBe(0);
  expect(duration(45)).toBe("45 phút");
  expect(duration(120)).toBe("2 giờ");
  expect(duration(330)).toBe("5 giờ 30 phút");
});

test("lọc kết hợp các nhóm, sắp xếp bằng nhau thì rẻ hơn đứng trước", () => {
  const a = trip(
    "2026-12-01T06:00:00+07:00",
    "2026-12-01T08:10:00+07:00",
    1_290_000,
    130,
  );
  const b = trip(
    "2026-12-01T08:00:00+07:00",
    "2026-12-01T10:10:00+07:00",
    990_000,
    130,
    "VJ",
  );
  const c = trip(
    "2026-12-01T19:00:00+07:00",
    "2026-12-01T21:10:00+07:00",
    1_190_000,
    130,
    "QH",
  );
  const all = [a, b, c];

  expect(sortItineraries(all, "price")).toEqual([b, c, a]);
  expect(sortItineraries(all, "departure")).toEqual([a, b, c]);
  expect(sortItineraries(all, "duration")).toEqual([b, c, a]);

  expect(applyFilters(all, NO_FILTERS)).toEqual(all);
  expect(applyFilters(all, { ...NO_FILTERS, airlines: ["VN", "QH"] })).toEqual([
    a,
    c,
  ]);
  expect(
    applyFilters(all, {
      ...NO_FILTERS,
      airlines: ["VN", "QH"],
      dayParts: ["evening"],
    }),
  ).toEqual([c]);
  // Giá tối đa so với gói rẻ nhất, tính cả biên.
  expect(applyFilters(all, { ...NO_FILTERS, maxPrice: 1_190_000 })).toEqual([
    b,
    c,
  ]);
});
