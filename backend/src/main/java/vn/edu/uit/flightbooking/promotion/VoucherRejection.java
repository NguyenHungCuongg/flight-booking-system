package vn.edu.uit.flightbooking.promotion;

import java.util.Map;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/**
 * Lý do voucher không dùng được (BR-30), đúng 7 giá trị của {@code reason} trong VOUCHER_INVALID (TDD §9).
 * {@link #ALREADY_USED} do booking ném (Plan 10, 11) vì cần bảng {@code bookings}, nên enum này công khai.
 */
public enum VoucherRejection {

	NOT_FOUND("Mã giảm giá không tồn tại"),

	INACTIVE("Mã giảm giá đã ngừng áp dụng"),

	NOT_STARTED("Mã giảm giá chưa đến thời gian áp dụng"),

	EXPIRED("Mã giảm giá đã hết hạn"),

	EXHAUSTED("Mã giảm giá đã hết lượt sử dụng"),

	MIN_ORDER_NOT_MET("Đơn hàng chưa đạt giá trị tối thiểu để dùng mã giảm giá này"),

	ALREADY_USED("Bạn đã dùng mã giảm giá này cho một đặt chỗ khác");

	private final String message;

	VoucherRejection(String message) {
		this.message = message;
	}

	public BusinessException toException() {
		return new BusinessException(ErrorCode.VOUCHER_INVALID, message, Map.of("reason", name()));
	}

}
