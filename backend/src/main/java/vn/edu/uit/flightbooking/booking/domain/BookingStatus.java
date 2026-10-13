package vn.edu.uit.flightbooking.booking.domain;

/** Cột {@code bookings.status} (APP_FLOW §4.1). */
public enum BookingStatus {

	PENDING_PAYMENT,

	ISSUED,

	EXPIRED,

	CANCELLED,

	REFUNDED

}
