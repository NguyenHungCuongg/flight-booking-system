package vn.edu.uit.flightbooking.booking.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.payment.PaymentApi;

/** Thanh toán booking (FR-30, BR-41): tạo lượt thu qua {@link PaymentApi}. */
@Service
public class BookingPayments {

	private record Payable(long id, long userId, BookingStatus status, long totalAmount, Instant holdExpiresAt) {
	}

	private final PaymentApi payments;

	private final JdbcClient jdbc;

	BookingPayments(PaymentApi payments, JdbcClient jdbc) {
		this.payments = payments;
		this.jdbc = jdbc;
	}

	/**
	 * FR-30, BR-41: chỉ booking PENDING_PAYMENT còn trong hạn giữ chỗ; khách thử lại được nhiều lần. Không khoá booking:
	 * nếu booking bị huỷ ngay sau đó, khoản tiền trả về sau rơi vào BR-44.
	 */
	@Transactional
	public String start(long userId, String code, String clientIp) {
		String upper = code.toUpperCase(Locale.ROOT);
		Payable booking = jdbc.sql("""
				SELECT id, user_id, status, total_amount, hold_expires_at FROM bookings WHERE code = ?
				""")
			.param(upper)
			.query((rs, rowNum) -> new Payable(rs.getLong("id"), rs.getLong("user_id"),
					BookingStatus.valueOf(rs.getString("status")), rs.getLong("total_amount"),
					rs.getObject("hold_expires_at", OffsetDateTime.class).toInstant()))
			.optional()
			.filter(b -> b.userId() == userId)
			.orElseThrow(BookingCancellation::notFound);
		boolean pending = booking.status() == BookingStatus.PENDING_PAYMENT;
		if (booking.status() == BookingStatus.EXPIRED || pending && !Instant.now().isBefore(booking.holdExpiresAt())) {
			throw new BusinessException(ErrorCode.HOLD_EXPIRED, "Booking đã quá hạn giữ chỗ, vui lòng đặt lại");
		}
		if (!pending) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "Chỉ thanh toán được booking đang chờ thanh toán");
		}
		return payments.charge(booking.id(), booking.totalAmount(), booking.holdExpiresAt(),
				"Thanh toan dat ve " + upper, clientIp);
	}

}
