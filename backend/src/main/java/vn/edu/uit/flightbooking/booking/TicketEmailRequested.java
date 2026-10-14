package vn.edu.uit.flightbooking.booking;

/**
 * FR-73: Staff yêu cầu gửi lại email vé. Tách khỏi {@link BookingIssued} để listener của "đã xuất vé" không chạy lại.
 * Module notification gửi email sau khi commit.
 */
public record TicketEmailRequested(long bookingId) {
}
