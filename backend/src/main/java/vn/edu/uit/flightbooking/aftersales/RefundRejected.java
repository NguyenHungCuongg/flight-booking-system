package vn.edu.uit.flightbooking.aftersales;

/** FR-52: Staff từ chối yêu cầu hoàn của khách. Phát trong transaction; email gửi sau commit (Task 6). */
public record RefundRejected(long refundRequestId) {
}
