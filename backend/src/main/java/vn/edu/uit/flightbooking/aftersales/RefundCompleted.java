package vn.edu.uit.flightbooking.aftersales;

/** FR-52: yêu cầu hoàn đã hoàn xong. Phát trong transaction; email gửi sau commit (Task 6). */
public record RefundCompleted(long refundRequestId) {
}
