// Dữ liệu mẫu cho các khối minh hoạ của landing (tuyến gợi ý). Ô tìm chuyến lấy sân bay thật từ GET /api/airports
// (src/lib/airports.ts).
interface RouteAirport {
  code: string;
  city: string;
  cc: string;
}

const AIRPORTS: RouteAirport[] = [
  { code: "HAN", city: "Hà Nội", cc: "VN" },
  { code: "SGN", city: "TP. Hồ Chí Minh", cc: "VN" },
  { code: "DAD", city: "Đà Nẵng", cc: "VN" },
  { code: "PQC", city: "Phú Quốc", cc: "VN" },
  { code: "HPH", city: "Hải Phòng", cc: "VN" },
  { code: "VII", city: "Vinh", cc: "VN" },
  { code: "SIN", city: "Singapore", cc: "SG" },
];

export const CABINS = [
  { id: "ECONOMY", label: "Phổ thông" },
  { id: "PREMIUM_ECONOMY", label: "Phổ thông đặc biệt" },
  { id: "BUSINESS", label: "Thương gia" },
  { id: "FIRST", label: "Hạng nhất" },
] as const;

export type CabinId = (typeof CABINS)[number]["id"];

export const ROUTES: { from: string; to: string }[] = [
  { from: "HAN", to: "SGN" },
  { from: "SGN", to: "DAD" },
  { from: "HAN", to: "PQC" },
  { from: "SGN", to: "HPH" },
  { from: "SGN", to: "VII" },
  { from: "SGN", to: "SIN" },
];

export const airportByCode = (code: string) =>
  AIRPORTS.find((a) => a.code === code) ?? null;

// Thời hạn giữ chỗ (phút). Giá trị thật là tham số hệ thống của PRD §7, đọc qua SettingsApi ở backend.
export const HOLD_MINUTES = 15;
