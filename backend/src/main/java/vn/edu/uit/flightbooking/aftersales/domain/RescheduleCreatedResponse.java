package vn.edu.uit.flightbooking.aftersales.domain;

import java.time.OffsetDateTime;

/** Kết quả xác nhận đổi chuyến: {@code COMPLETED} khi không phải trả thêm, ngược lại chờ thanh toán đến hạn giữ chỗ. */
public record RescheduleCreatedResponse(long id, RescheduleStatus status, long amountDue,
		OffsetDateTime holdExpiresAt) {

}
