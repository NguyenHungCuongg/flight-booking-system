import { expect, test } from "@playwright/test";
import {
  asciiUpper,
  bagField,
  bookingRequest,
  isInternational,
  passengerTypes,
  searchUrl,
} from "../src/app/checkout/booking-request";
import type { Schemas } from "../src/lib/api";
import {
  clock,
  countdown,
  directionParam,
  dmy,
  journeyActions,
} from "../src/lib/booking";
import {
  parseSelection,
  type Journey,
  type Selection,
} from "../src/lib/selection";

// Unit test cho logic của C-07, C-08, C-09: không mở trình duyệt, không cần backend.

const at = (airport: string, time: string) => ({
  airport,
  city: airport,
  time,
  timezone: "Asia/Ho_Chi_Minh",
});

const fare = {
  fareFamilyId: 7,
  name: "Economy Lite",
  cabinClass: "ECONOMY" as const,
  carryOnKg: 10,
  checkedBaggageKg: 0,
  refundable: false,
  refundFee: 0,
  changeable: true,
  changeFee: 600_000,
  adultPrice: 1_290_000,
  totalPrice: 1_290_000,
  seatsLeft: 9,
};

/** Một chiều gồm các chặng [flightId, đi, đến, giờ đi, giờ đến]. */
function journey(
  direction: Journey["direction"],
  legs: [number, string, string, string, string][],
): Journey {
  const segments = legs.map(([flightId, from, to, dep, arr]) => ({
    flightId,
    flightNumber: `VN${flightId}`,
    airline: { code: "VN", name: "Vietnam Airlines" },
    departure: at(from, dep),
    arrival: at(to, arr),
    durationMinutes: 120,
    aircraftModel: "Airbus A321",
    layoverMinutesAfter: 0,
  }));
  return {
    direction,
    flightIds: segments.map((s) => s.flightId),
    fareFamilyId: fare.fareFamilyId,
    itinerary: {
      stops: segments.length - 1,
      totalDurationMinutes: 120 * segments.length,
      segments,
      fareOptions: [fare],
    },
    fare,
  };
}

const domestic: Selection = {
  adults: 2,
  children: 1,
  infants: 1,
  journeys: [
    journey("OUTBOUND", [
      [
        213,
        "HAN",
        "SGN",
        "2026-12-01T06:00:00+07:00",
        "2026-12-01T08:10:00+07:00",
      ],
    ]),
  ],
};

const roundTrip: Selection = {
  adults: 1,
  children: 0,
  infants: 0,
  journeys: [
    journey("OUTBOUND", [
      [
        651,
        "SGN",
        "SIN",
        "2026-12-01T09:30:00+07:00",
        "2026-12-01T12:30:00+08:00",
      ],
    ]),
    journey("RETURN", [
      [
        652,
        "SIN",
        "SGN",
        "2026-12-05T14:00:00+08:00",
        "2026-12-05T15:00:00+07:00",
      ],
    ]),
  ],
};

const airports = [
  ["HAN", "VN"],
  ["SGN", "VN"],
  ["SIN", "SG"],
].map(([code, countryCode]) => ({
  code,
  countryCode,
  name: code,
  city: code,
  timezone: "Asia/Ho_Chi_Minh",
  active: true,
}));

test("asciiUpper: bỏ dấu, Đ thành D, viết hoa, gộp khoảng trắng (BR-12)", () => {
  expect(asciiUpper("  Nguyễn  văn Đức ")).toBe("NGUYEN VAN DUC");
  expect(asciiUpper("đặng thị ánh")).toBe("DANG THI ANH");
  expect(asciiUpper("Lê Hoàng Phúc")).toBe("LE HOANG PHUC");
});

test("passengerTypes: người lớn, rồi trẻ em, rồi em bé", () => {
  expect(passengerTypes(domestic)).toEqual([
    "ADULT",
    "ADULT",
    "CHILD",
    "INFANT",
  ]);
});

test("isInternational: có chặng mà hai sân bay khác quốc gia (BR-13)", () => {
  expect(isInternational(domestic, airports)).toBe(false);
  expect(isInternational(roundTrip, airports)).toBe(true);
});

test("bookingRequest: chuyến nội địa, có hành lý và voucher (TDD §8.8)", () => {
  const family: Selection = { ...domestic, adults: 1, children: 0, infants: 1 };
  const form = new FormData();
  const pax = (i: number, values: Record<string, string>) =>
    Object.entries(values).forEach(([k, v]) =>
      form.set(`passengers[${i}].${k}`, v),
    );
  pax(0, {
    lastName: "Nguyễn",
    firstName: " văn  an ",
    gender: "MALE",
    dateOfBirth: "1990-05-20",
  });
  pax(1, {
    lastName: "nguyễn",
    firstName: "bé na",
    gender: "FEMALE",
    dateOfBirth: "2026-01-15",
  });
  form.set(bagField("OUTBOUND", 0), "12");
  form.set("contact.name", " Trần Minh Khoa ");
  form.set("contact.email", "khoa@example.com");
  form.set("contact.phone", "0912345678");

  expect(bookingRequest(family, form, "WELCOME10")).toEqual({
    journeys: [{ direction: "OUTBOUND", flightIds: [213], fareFamilyId: 7 }],
    passengers: [
      {
        type: "ADULT",
        lastName: "NGUYEN",
        firstName: "VAN AN",
        gender: "MALE",
        dateOfBirth: "1990-05-20",
      },
      {
        type: "INFANT",
        lastName: "NGUYEN",
        firstName: "BE NA",
        gender: "FEMALE",
        dateOfBirth: "2026-01-15",
      },
    ],
    baggage: [
      { passengerIndex: 0, direction: "OUTBOUND", baggageOptionId: 12 },
    ],
    contact: {
      name: "Trần Minh Khoa",
      email: "khoa@example.com",
      phone: "0912345678",
    },
    voucherCode: "WELCOME10",
  });
});

test("bookingRequest: khứ hồi quốc tế, hộ chiếu viết hoa, không voucher", () => {
  const form = new FormData();
  form.set("passengers[0].lastName", "tran");
  form.set("passengers[0].firstName", "thi mai");
  form.set("passengers[0].gender", "FEMALE");
  form.set("passengers[0].dateOfBirth", "1985-02-14");
  form.set("passengers[0].nationality", "vn");
  form.set("passengers[0].passportNumber", "c1234567");
  form.set("passengers[0].passportExpiry", "2030-01-01");
  form.set(bagField("OUTBOUND", 0), "");
  form.set(bagField("RETURN", 0), "31");

  const body = bookingRequest(roundTrip, form, "");
  expect(body.journeys.map((j) => j.direction)).toEqual(["OUTBOUND", "RETURN"]);
  expect(body.passengers[0]).toEqual({
    type: "ADULT",
    lastName: "TRAN",
    firstName: "THI MAI",
    gender: "FEMALE",
    dateOfBirth: "1985-02-14",
    nationality: "VN",
    passportNumber: "C1234567",
    passportExpiry: "2030-01-01",
  });
  expect(body.baggage).toEqual([
    { passengerIndex: 0, direction: "RETURN", baggageOptionId: 31 },
  ]);
  expect(body.voucherCode).toBeUndefined();
});

test("searchUrl: mở lại C-02 với đúng điều kiện tìm", () => {
  expect(searchUrl(domestic)).toBe(
    "/flights?from=HAN&to=SGN&date=2026-12-01&adults=2&children=1&infants=1&cabin=ECONOMY",
  );
  expect(searchUrl(roundTrip)).toBe(
    "/flights?from=SGN&to=SIN&date=2026-12-01&returnDate=2026-12-05&adults=1&children=0&infants=0&cabin=ECONOMY",
  );
});

test("parseSelection: thiếu hoặc hỏng thì là null", () => {
  for (const raw of [null, "", "{oops", "null", "5", "{}"]) {
    expect(parseSelection(raw)).toBeNull();
  }
  expect(
    parseSelection(JSON.stringify({ ...domestic, journeys: [] })),
  ).toBeNull();
  expect(parseSelection(JSON.stringify(domestic))).toEqual(domestic);
});

test("dmy, clock: đọc thẳng từ chuỗi có offset", () => {
  expect(dmy("2026-12-01T23:30:00+07:00")).toBe("01/12/2026");
  expect(clock("2026-12-01T23:30:00+07:00")).toBe("23:30");
  expect(dmy("1990-05-20")).toBe("20/05/1990");
});

test("countdown: mm:ss, hết giờ là 00:00", () => {
  expect(countdown(15 * 60_000)).toBe("15:00");
  expect(countdown(61_999)).toBe("01:01");
  expect(countdown(-5_000)).toBe("00:00");
});

// journeyActions: dựng BookingDetail tối thiểu; chỉ các trường hàm đọc.
const NOW = Date.parse("2026-11-01T00:00:00+07:00");
type Changes = Record<string, unknown>;
const leg = (direction: "OUTBOUND" | "RETURN", changes: Changes = {}) => ({
  direction,
  status: "ACTIVE",
  refundable: true,
  changeable: true,
  segments: [{ departureTime: "2026-11-10T10:00:00+07:00" }],
  ...changes,
});
const detail = (changes: Changes = {}) =>
  ({
    status: "ISSUED",
    journeys: [leg("OUTBOUND"), leg("RETURN")],
    refundRequests: [],
    reschedules: [],
    ...changes,
  }) as unknown as Schemas["BookingDetail"];
const actions = (
  b: Schemas["BookingDetail"],
  i: number,
  now: number | null = NOW,
) => journeyActions(b, b.journeys[i], now);

test("journeyActions: đã xuất vé, chiều ACTIVE, chưa bay thì theo cờ của gói (BR-60, BR-70)", () => {
  expect(actions(detail(), 0)).toEqual({ refund: true, change: true });
  const lite = detail({ journeys: [leg("OUTBOUND", { refundable: false })] });
  expect(actions(lite, 0)).toEqual({ refund: false, change: true });
});

test("journeyActions: không hiện khi chưa xuất vé, chiều đã hoàn, đã cất cánh hoặc chưa hydrate (BR-51)", () => {
  const none = { refund: false, change: false };
  expect(actions(detail({ status: "PENDING_PAYMENT" }), 0)).toEqual(none);
  expect(
    actions(detail({ journeys: [leg("OUTBOUND", { status: "REFUNDED" })] }), 0),
  ).toEqual(none);
  expect(actions(detail(), 0, Date.parse("2026-11-10T10:00:00+07:00"))).toEqual(
    none,
  );
  expect(actions(detail(), 0, null)).toEqual(none);
});

test("journeyActions: yêu cầu hoàn đang mở hoặc đổi chờ thanh toán chỉ chặn đúng chiều đó (BR-51)", () => {
  const refund = (direction: string | null, status: string) => ({
    direction,
    status,
  });
  const open = detail({ refundRequests: [refund("OUTBOUND", "APPROVED")] });
  expect(actions(open, 0).refund).toBe(false);
  expect(actions(open, 1).refund).toBe(true);
  const closed = detail({
    refundRequests: [
      refund("OUTBOUND", "REJECTED"),
      refund("OUTBOUND", "COMPLETED"),
      refund(null, "PENDING"),
    ],
  });
  expect(actions(closed, 0)).toEqual({ refund: true, change: true });
  const changing = detail({
    reschedules: [{ direction: "RETURN", status: "PENDING_PAYMENT" }],
  });
  expect(actions(changing, 1)).toEqual({ refund: false, change: false });
  expect(actions(changing, 0)).toEqual({ refund: true, change: true });
  const expired = detail({
    reschedules: [{ direction: "RETURN", status: "EXPIRED" }],
  });
  expect(actions(expired, 1).change).toBe(true);
});

test("directionParam: chỉ nhận outbound, return", () => {
  expect(directionParam("outbound")).toBe("OUTBOUND");
  expect(directionParam("return")).toBe("RETURN");
  expect(directionParam("RETURN")).toBeNull();
  expect(directionParam("abc")).toBeNull();
});
