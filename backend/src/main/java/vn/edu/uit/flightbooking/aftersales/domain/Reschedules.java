package vn.edu.uit.flightbooking.aftersales.domain;

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
		bookings.lock(event.bookingId());
		String status = jdbc.sql("SELECT status FROM reschedules WHERE id = ? FOR UPDATE")
			.param(event.rescheduleId())
			.query(String.class)
			.single();
		if (RescheduleStatus.valueOf(status) == RescheduleStatus.PENDING_PAYMENT) {
			complete(event.rescheduleId());
		}
		else {
			refunds.createInvalidPayment(event.bookingId(), event.paymentId());
		}
	}

}
