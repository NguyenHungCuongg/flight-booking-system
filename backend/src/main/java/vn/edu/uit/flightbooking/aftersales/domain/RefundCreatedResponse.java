package vn.edu.uit.flightbooking.aftersales.domain;

/** Kết quả gửi yêu cầu hoàn. Service dựng luôn vì sau khi gửi thì {@code quote} đã báo REQUEST_IN_PROGRESS. */
public record RefundCreatedResponse(long id, RefundStatus status, long refundAmount) {

}
