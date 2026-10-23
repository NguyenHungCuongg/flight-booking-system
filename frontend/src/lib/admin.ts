/**
 * Tiện ích và hằng số dùng chung cho toàn bộ các màn hình Quản trị Admin (A-01–A-10).
 */

import { ApiError } from "./api";

export const VOUCHER_TYPE_LABEL: Record<string, string> = {
  PERCENT: "Phần trăm (%)",
  FIXED: "Số tiền cố định",
};

export const WEEKDAY_OPTIONS = [
  { day: 1, label: "Thứ 2" },
  { day: 2, label: "Thứ 3" },
  { day: 3, label: "Thứ 4" },
  { day: 4, label: "Thứ 5" },
  { day: 5, label: "Thứ 6" },
  { day: 6, label: "Thứ 7" },
  { day: 7, label: "Chủ nhật" },
] as const;

export interface SettingDefinition {
  name: string;
  unit: string;
  min: number;
  max: number;
  description: string;
}

export const SETTING_INFO: Record<string, SettingDefinition> = {
  "booking.hold_minutes": {
    name: "Thời gian giữ chỗ",
    unit: "phút",
    min: 5,
    max: 60,
    description: "Thời gian giữ chỗ và thanh toán đổi chuyến.",
  },
  "booking.min_hours_before_departure": {
    name: "Khoảng cách đặt vé tối thiểu",
    unit: "giờ",
    min: 0,
    max: 72,
    description: "Không bán chuyến bay cất cánh trong vòng N giờ tới.",
  },
  "booking.max_seated_passengers": {
    name: "Số khách tối đa mỗi đơn",
    unit: "khách",
    min: 1,
    max: 9,
    description: "Số khách chiếm ghế tối đa trong một booking.",
  },
  "pricing.child_percent": {
    name: "Tỉ lệ giá vé trẻ em",
    unit: "%",
    min: 0,
    max: 100,
    description: "Giá vé trẻ em tính theo % giá người lớn.",
  },
  "pricing.infant_percent": {
    name: "Tỉ lệ giá vé em bé",
    unit: "%",
    min: 0,
    max: 100,
    description: "Giá vé em bé tính theo % giá người lớn.",
  },
  "aftersales.min_hours_before_departure": {
    name: "Hạn chót yêu cầu hoàn/đổi",
    unit: "giờ",
    min: 0,
    max: 168,
    description:
      "Thời hạn tối thiểu trước giờ cất cánh để yêu cầu hoàn hoặc đổi vé.",
  },
  "search.min_connection_minutes": {
    name: "Nối chuyến tối thiểu",
    unit: "phút",
    min: 30,
    max: 600,
    description: "Thời gian nối chuyến tối thiểu giữa các chặng.",
  },
  "search.max_connection_minutes": {
    name: "Nối chuyến tối đa",
    unit: "phút",
    min: 60,
    max: 1440,
    description: "Thời gian nối chuyến tối đa giữa các chặng.",
  },
};

export function validateSettingValue(
  key: string,
  value: number,
): string | null {
  const meta = SETTING_INFO[key];
  if (!meta) return null;
  if (!Number.isFinite(value) || value < meta.min || value > meta.max) {
    return `Giá trị phải từ ${meta.min} đến ${meta.max}`;
  }
  return null;
}

/** Câu báo lỗi cho Admin (lỗi khác ApiError, VD TypeError khi mất mạng, thì báo chung): lỗi theo trường (VALIDATION_FAILED chỉ có detail chung "Dữ liệu không hợp lệ") rồi mới tới detail. */
export function errorText(err: unknown): string {
  if (!(err instanceof ApiError)) return "Đã có lỗi xảy ra. Hãy thử lại.";
  return err.errors.length > 0
    ? err.errors.map((e) => e.message).join(" ")
    : err.detail;
}

const vnParts = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Asia/Ho_Chi_Minh",
  hour: "2-digit",
  minute: "2-digit",
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hourCycle: "h23",
});

/** Thời điểm UTC (VD updatedAt của tham số) theo giờ Việt Nam, cùng dạng `at()`: "20:06 09/10/2026". */
export function vnTime(iso: string): string {
  const p = Object.fromEntries(vnParts.formatToParts(new Date(iso)).map((x) => [x.type, x.value]));
  return `${p.hour}:${p.minute} ${p.day}/${p.month}/${p.year}`;
}
