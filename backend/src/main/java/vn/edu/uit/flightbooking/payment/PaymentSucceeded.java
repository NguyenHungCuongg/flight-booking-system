package vn.edu.uit.flightbooking.payment;

/**
 * Lượt thu vừa thành công (TDD §3.3, BR-42). Phát trong transaction của callback; listener đồng bộ của booking xuất vé
 * hoặc báo thanh toán ngoài dự kiến (BR-43, BR-44). Plan 15 thêm {@code rescheduleId} cho lượt thu của đổi chuyến.
 */
public record PaymentSucceeded(long paymentId, long bookingId, long amount) {
}
