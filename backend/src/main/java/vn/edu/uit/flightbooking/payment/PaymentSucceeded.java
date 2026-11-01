package vn.edu.uit.flightbooking.payment;

/**
 * Lượt thu vừa thành công (TDD §3.3, BR-42). Phát trong transaction của callback; listener đồng bộ của booking xuất vé
 * hoặc báo thanh toán ngoài dự kiến (BR-43, BR-44). {@code rescheduleId} khác null: lượt thu của yêu cầu đổi chuyến,
 * do aftersales xử lý.
 */
public record PaymentSucceeded(long paymentId, long bookingId, Long rescheduleId, long amount) {
}
