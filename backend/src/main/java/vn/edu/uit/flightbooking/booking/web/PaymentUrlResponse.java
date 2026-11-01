package vn.edu.uit.flightbooking.booking.web;

/** Phản hồi 201 của POST /bookings/{code}/payments: frontend chuyển hướng người dùng tới URL này (TDD §6.3). */
public record PaymentUrlResponse(String paymentUrl) {
}
