package vn.edu.uit.flightbooking.booking.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.booking.UnexpectedPaymentReceived;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.payment.PaymentApi;
import vn.edu.uit.flightbooking.payment.PaymentSucceeded;

/**
 * Thanh toán booking: tạo lượt thu qua {@link PaymentApi} (FR-30, BR-41) và nhận kết quả qua {@link PaymentSucceeded}
 * (FR-31, BR-43, BR-44).
 */
@Service
public class BookingPayments {

	private record Payable(long id, long userId, BookingStatus status, long totalAmount, Instant holdExpiresAt) {
	}

	private final PaymentApi payments;

	private final BookingService bookings;

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	BookingPayments(PaymentApi payments, BookingService bookings, JdbcClient jdbc, ApplicationEventPublisher events) {
		this.payments = payments;
		this.bookings = bookings;
		this.jdbc = jdbc;
		this.events = events;
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
		return payments.charge(booking.id(), null, booking.totalAmount(), booking.holdExpiresAt(),
				"Thanh toan dat ve " + upper, clientIp);
	}

	/**
	 * TDD §6.3: chạy đồng bộ trong transaction của callback, đã khoá {@code payments}; khoá booking rồi kiểm tra lại.
	 * Booking còn chờ thanh toán (kể cả trong 5 phút ân hạn của BR-45) thì xuất vé (BR-43). Ngược lại phát
	 * {@link UnexpectedPaymentReceived} để hoàn 100% lượt thu (BR-44). Bắt buộc chạy trong transaction của callback
	 * (MANDATORY: gọi ngoài transaction thì lỗi ngay thay vì chạy sai). Lượt thu của đổi chuyến do aftersales xử lý.
	 */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	void on(PaymentSucceeded event) {
		if (event.rescheduleId() != null) {
			return;
		}
		BookingStatus status = jdbc.sql("SELECT status FROM bookings WHERE id = ? FOR UPDATE")
			.param(event.bookingId())
			.query((rs, rowNum) -> BookingStatus.valueOf(rs.getString("status")))
			.single();
		if (status == BookingStatus.PENDING_PAYMENT) {
			bookings.issue(event.bookingId());
		}
		else {
			events.publishEvent(new UnexpectedPaymentReceived(event.paymentId(), event.bookingId()));
		}
	}

}
