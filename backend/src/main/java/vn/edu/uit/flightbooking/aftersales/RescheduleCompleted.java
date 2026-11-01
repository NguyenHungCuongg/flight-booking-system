package vn.edu.uit.flightbooking.aftersales;

/** FR-62: yêu cầu đổi chuyến đã hoàn tất, chặng mới đã có số vé. Phát trong transaction; email gửi sau commit. */
public record RescheduleCompleted(long rescheduleId) {
}
