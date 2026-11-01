package vn.edu.uit.flightbooking.booking;

/**
 * BR-44: lượt thu thành công nhưng booking không còn chờ thanh toán (đã hết hạn, đã huỷ, hoặc đã trả bằng lượt thu
 * khác). Phát trong transaction của callback; Plan 14 thêm listener tạo yêu cầu hoàn {@code INVALID_PAYMENT} (TDD §3.3).
 */
public record UnexpectedPaymentReceived(long paymentId, long bookingId) {
}
