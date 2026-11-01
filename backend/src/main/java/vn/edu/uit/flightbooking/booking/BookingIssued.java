package vn.edu.uit.flightbooking.booking;

/**
 * Booking vừa được xuất vé (TDD §3.3): khi giữ chỗ với tổng tiền 0 (BR-40) hoặc khi thanh toán thành công (Plan 12).
 * Module notification gửi email vé sau khi commit (Plan 12).
 */
public record BookingIssued(long bookingId) {
}
