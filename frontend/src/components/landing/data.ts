// Dữ liệu mẫu của landing. Sân bay thật sẽ lấy từ GET /api/airports?q= (FR-10) khi có khung frontend (Plan 03).
export interface Airport {
  code: string;
  city: string;
  name: string;
  cc: string;
  country: string;
}

export const AIRPORTS: Airport[] = [
  {
    code: "HAN",
    city: "Hà Nội",
    name: "Sân bay quốc tế Nội Bài",
    cc: "VN",
    country: "Việt Nam",
  },
  {
    code: "SGN",
    city: "TP. Hồ Chí Minh",
    name: "Sân bay quốc tế Tân Sơn Nhất",
    cc: "VN",
    country: "Việt Nam",
  },
  {
    code: "DAD",
    city: "Đà Nẵng",
    name: "Sân bay quốc tế Đà Nẵng",
    cc: "VN",
    country: "Việt Nam",
  },
  {
    code: "CXR",
    city: "Nha Trang",
    name: "Sân bay quốc tế Cam Ranh",
    cc: "VN",
    country: "Việt Nam",
  },
  {
    code: "PQC",
    city: "Phú Quốc",
    name: "Sân bay quốc tế Phú Quốc",
    cc: "VN",
    country: "Việt Nam",
  },
  {
    code: "HPH",
    city: "Hải Phòng",
    name: "Sân bay quốc tế Cát Bi",
    cc: "VN",
    country: "Việt Nam",
  },
  {
    code: "HUI",
    city: "Huế",
    name: "Sân bay quốc tế Phú Bài",
    cc: "VN",
    country: "Việt Nam",
  },
  {
    code: "VII",
    city: "Vinh",
    name: "Sân bay quốc tế Vinh",
    cc: "VN",
    country: "Việt Nam",
  },
  {
    code: "VCA",
    city: "Cần Thơ",
    name: "Sân bay quốc tế Cần Thơ",
    cc: "VN",
    country: "Việt Nam",
  },
  {
    code: "DLI",
    city: "Đà Lạt",
    name: "Sân bay quốc tế Liên Khương",
    cc: "VN",
    country: "Việt Nam",
  },
  {
    code: "UIH",
    city: "Quy Nhơn",
    name: "Sân bay Phù Cát",
    cc: "VN",
    country: "Việt Nam",
  },
  {
    code: "THD",
    city: "Thanh Hóa",
    name: "Sân bay Thọ Xuân",
    cc: "VN",
    country: "Việt Nam",
  },
  {
    code: "BMV",
    city: "Buôn Ma Thuột",
    name: "Sân bay Buôn Ma Thuột",
    cc: "VN",
    country: "Việt Nam",
  },
  {
    code: "VDO",
    city: "Quảng Ninh",
    name: "Sân bay quốc tế Vân Đồn",
    cc: "VN",
    country: "Việt Nam",
  },
  {
    code: "SIN",
    city: "Singapore",
    name: "Sân bay Changi",
    cc: "SG",
    country: "Singapore",
  },
  {
    code: "BKK",
    city: "Bangkok",
    name: "Sân bay Suvarnabhumi",
    cc: "TH",
    country: "Thái Lan",
  },
  {
    code: "ICN",
    city: "Seoul",
    name: "Sân bay quốc tế Incheon",
    cc: "KR",
    country: "Hàn Quốc",
  },
  {
    code: "NRT",
    city: "Tokyo",
    name: "Sân bay quốc tế Narita",
    cc: "JP",
    country: "Nhật Bản",
  },
  {
    code: "TPE",
    city: "Đài Bắc",
    name: "Sân bay quốc tế Đào Viên",
    cc: "TW",
    country: "Đài Loan",
  },
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
