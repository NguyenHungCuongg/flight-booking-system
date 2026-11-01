// Dựng body của POST /bookings/quote và POST /bookings (TDD §8.8) từ lựa chọn ở C-02 và form C-07. Hàm thuần.
import type { Airport } from "@/lib/airports";
import type { Schemas } from "@/lib/api";
import type { Selection } from "@/lib/selection";

type PassengerRequest = Schemas["PassengerRequest"];
export type PassengerType = PassengerRequest["type"];
type Direction = Schemas["JourneyRequest"]["direction"];

/** TDD §7, BR-12: bỏ dấu, Đ thành D, viết hoa, các từ cách nhau một khoảng trắng. "Nguyễn  văn Đức " → "NGUYEN VAN DUC". */
export const asciiUpper = (s: string) =>
  s
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[đĐ]/g, "D")
    .toUpperCase()
    .trim()
    .replace(/\s+/g, " ");

/** Thứ tự khách trên form và trong request: người lớn, trẻ em, rồi em bé. `passengerIndex` của hành lý theo thứ tự này. */
export const passengerTypes = ({
  adults,
  children,
  infants,
}: Pick<Selection, "adults" | "children" | "infants">): PassengerType[] => [
  ...Array<PassengerType>(adults).fill("ADULT"),
  ...Array<PassengerType>(children).fill("CHILD"),
  ...Array<PassengerType>(infants).fill("INFANT"),
];

/** BR-13: có chặng mà sân bay đi và sân bay đến thuộc hai quốc gia khác nhau. */
export function isInternational(selection: Selection, airports: Airport[]) {
  const country = new Map(airports.map((a) => [a.code, a.countryCode]));
  return selection.journeys.some((j) =>
    j.itinerary.segments.some(
      (s) =>
        country.get(s.departure.airport) !== country.get(s.arrival.airport),
    ),
  );
}

/** Tên ô chọn hành lý của khách thứ `index` ở một chiều. */
export const bagField = (direction: Direction, index: number) =>
  `bag-${direction}-${index}`;

const text = (data: FormData, name: string) =>
  String(data.get(name) ?? "").trim();

/** `voucherCode` rỗng là không dùng voucher. Trường để trống thì là undefined, JSON bỏ trường đó. */
export function bookingRequest(
  selection: Selection,
  data: FormData,
  voucherCode: string,
): Schemas["BookingRequest"] {
  const types = passengerTypes(selection);
  return {
    journeys: selection.journeys.map(
      ({ direction, flightIds, fareFamilyId }) => ({
        direction,
        flightIds,
        fareFamilyId,
      }),
    ),
    passengers: types.map((type, i) => {
      const field = (k: string) => text(data, `passengers[${i}].${k}`);
      return {
        type,
        lastName: asciiUpper(field("lastName")),
        firstName: asciiUpper(field("firstName")),
        gender: field("gender") as PassengerRequest["gender"],
        dateOfBirth: field("dateOfBirth"),
        // Ba ô dưới chỉ có khi hành trình quốc tế (BR-13).
        nationality: field("nationality").toUpperCase() || undefined,
        passportNumber: field("passportNumber").toUpperCase() || undefined,
        passportExpiry: field("passportExpiry") || undefined,
      };
    }),
    baggage: selection.journeys.flatMap(({ direction }) =>
      types.flatMap((_, i) => {
        const id = text(data, bagField(direction, i));
        return id
          ? [{ passengerIndex: i, direction, baggageOptionId: Number(id) }]
          : [];
      }),
    ),
    contact: {
      name: text(data, "contact.name"),
      email: text(data, "contact.email"),
      phone: text(data, "contact.phone"),
    },
    voucherCode: voucherCode || undefined,
  };
}
