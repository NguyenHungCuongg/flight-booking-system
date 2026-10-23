type Tone = "ok" | "warn" | "danger" | "info" | "neutral";

/**
 * Tông màu theo mã trạng thái. Một bảng cho mọi enum (đặt chỗ, yêu cầu hoàn, đổi chuyến, thanh toán, chuyến bay) vì
 * các mã trùng tên đều cùng nghĩa: xong là xanh lá, đang chờ là vàng, huỷ hoặc lỗi là đỏ.
 */
const TONE: Record<string, Tone> = {
  ISSUED: "ok",
  COMPLETED: "ok",
  SUCCESS: "ok",
  PENDING_PAYMENT: "warn",
  PENDING: "warn",
  APPROVED: "info",
  SCHEDULED: "info",
  REFUNDED: "info",
  CANCELLED: "danger",
  REJECTED: "danger",
  FAILED: "danger",
  EXPIRED: "neutral",
};

const CLASS: Record<Tone, string> = {
  ok: "bg-ok-soft text-ok",
  warn: "bg-warn-soft text-warn",
  danger: "bg-danger-soft text-danger",
  info: "bg-info-soft text-info",
  neutral: "bg-tint text-muted",
};

/** Nhãn trạng thái có màu: khối bo 8px, chữ đậm cùng tông với nền nhạt. `large` cho dòng trạng thái đầu màn chi tiết. */
export default function StatusBadge({
  status,
  label,
  large = false,
}: {
  status: string;
  label: string;
  large?: boolean;
}) {
  return (
    <span
      className={`inline-flex w-fit items-center whitespace-nowrap ${
        large
          ? "rounded-inner px-10 py-3 text-subheading font-bold"
          : "rounded-badge px-8 py-2 text-badge font-semibold"
      } ${CLASS[TONE[status] ?? "neutral"]}`}
    >
      {label}
    </span>
  );
}
