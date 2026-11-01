package vn.edu.uit.flightbooking.aftersales.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.aftersales.RescheduleCompleted;
import vn.edu.uit.flightbooking.booking.BookingApi;
import vn.edu.uit.flightbooking.payment.PaymentSucceeded;

/** Hoàn tất yêu cầu đổi chuyến: khi thanh toán thành công (FR-62) hoặc ngay khi không phải trả thêm (BR-73). */
@Service
public class Reschedules {

	/** BR-75: chờ thêm 5 phút sau hạn giữ chỗ để kịp nhận kết quả thanh toán đến trễ (hằng số, PRD §7). */
	static final Duration GRACE = Duration.ofMinutes(5);

	private record Locked(RescheduleStatus status, Instant holdExpiresAt) {
	}

	private final JdbcClient jdbc;

	private final BookingApi bookings;

	private final RefundRequests refunds;

	private final ApplicationEventPublisher events;

	Reschedules(JdbcClient jdbc, BookingApi bookings, RefundRequests refunds, ApplicationEventPublisher events) {
		this.jdbc = jdbc;
		this.bookings = bookings;
		this.refunds = refunds;
		this.events = events;
	}

	/** BR-74. Bên gọi đã khoá booking và yêu cầu, và yêu cầu đang {@code PENDING_PAYMENT} (hoặc vừa được chèn). */
	@Transactional(propagation = Propagation.MANDATORY)
	public void complete(long id) {
		long journeyId = jdbc.sql("SELECT journey_id FROM reschedules WHERE id = ?")
			.param(id)
			.query(Long.class)
			.single();
		bookings.completeChange(journeyId, id);
		jdbc.sql("UPDATE reschedules SET status = 'COMPLETED', completed_at = now() WHERE id = ?").param(id).update();
		events.publishEvent(new RescheduleCompleted(id));
	}

	/**
	 * TDD §6.3: chạy đồng bộ trong transaction của callback, sau khi đã khoá {@code payments}; khoá booking rồi yêu cầu
	 * và kiểm tra lại. Yêu cầu còn chờ thanh toán (kể cả trong 5 phút ân hạn, như BR-45 của booking) thì hoàn tất
	 * (BR-74). Ngược lại (đã hết hạn hoặc bị huỷ) tạo yêu cầu hoàn 100% lượt thu (BR-44). Lượt thu của booking do
	 * module booking xử lý.
	 */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	void on(PaymentSucceeded event) {
		if (event.rescheduleId() == null) {
			return;
		}
		if (lock(event.rescheduleId()).status() == RescheduleStatus.PENDING_PAYMENT) {
			complete(event.rescheduleId());
		}
		else {
			refunds.createInvalidPayment(event.bookingId(), event.paymentId());
		}
	}

	/** Q-07 cho bảng {@code reschedules}: tối đa 100 yêu cầu chờ thanh toán đã quá hạn hơn {@link #GRACE}, quá hạn lâu nhất trước. */
	@Transactional(readOnly = true)
	public List<Long> overdue() {
		return jdbc.sql("""
				SELECT id FROM reschedules
				WHERE status = 'PENDING_PAYMENT' AND hold_expires_at < :cutoff
				ORDER BY hold_expires_at
				LIMIT 100
				""").param("cutoff", Instant.now().minus(GRACE).atOffset(ZoneOffset.UTC)).query(Long.class).list();
	}

	/**
	 * BR-75: khoá rồi kiểm tra lại. Đã thanh toán, đã huỷ hoặc chưa quá 5 phút ân hạn thì bỏ qua và trả {@code false}.
	 * Ngược lại chặng mới thành CANCELLED (trả ghế chuyến mới), chiều về giữ nguyên chặng cũ.
	 */
	@Transactional
	public boolean expire(long id) {
		Locked request = lock(id);
		if (request.status() != RescheduleStatus.PENDING_PAYMENT
				|| !request.holdExpiresAt().plus(GRACE).isBefore(Instant.now())) {
			return false;
		}
		bookings.cancelChange(id);
		jdbc.sql("UPDATE reschedules SET status = 'EXPIRED' WHERE id = ?").param(id).update();
		return true;
	}

	/** Thứ tự khoá TDD §5.4: đọc {@code booking_id} (không khoá), khoá booking, rồi khoá yêu cầu. */
	private Locked lock(long id) {
		long bookingId = jdbc.sql("SELECT booking_id FROM reschedules WHERE id = ?")
			.param(id)
			.query(Long.class)
			.single();
		bookings.lock(bookingId);
		return jdbc.sql("SELECT status, hold_expires_at FROM reschedules WHERE id = ? FOR UPDATE")
			.param(id)
			.query((rs, n) -> new Locked(RescheduleStatus.valueOf(rs.getString("status")),
					rs.getObject("hold_expires_at", OffsetDateTime.class).toInstant()))
			.single();
	}

}
