package vn.edu.uit.flightbooking.aftersales.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.aftersales.RefundCompleted;
import vn.edu.uit.flightbooking.aftersales.RefundRejected;
import vn.edu.uit.flightbooking.booking.BookingApi;
import vn.edu.uit.flightbooking.booking.UnexpectedPaymentReceived;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.identity.UserApi;
import vn.edu.uit.flightbooking.payment.PaymentApi;
import vn.edu.uit.flightbooking.payment.PaymentApi.RefundOrder;
import vn.edu.uit.flightbooking.payment.PaymentApi.RefundOutcome;

/**
 * Yêu cầu hoàn do hệ thống tạo, và các thao tác của Staff (FR-71). Mọi đường có hoàn tiền khoá {@code bookings} trước
 * {@code refund_requests} (TDD §5.4), vì vòng hoàn của {@link PaymentApi} không khoá lượt thu: hai yêu cầu của cùng
 * booking chạy song song sẽ hoàn lố một lượt thu.
 */
@Service
public class RefundRequests {

	private record Locked(long bookingId, Long journeyId, Long paymentId, RefundSource source, RefundStatus status,
			long refundAmount) {
	}

	private final JdbcClient jdbc;

	private final BookingApi bookings;

	private final PaymentApi payments;

	private final UserApi users;

	private final ApplicationEventPublisher events;

	RefundRequests(JdbcClient jdbc, BookingApi bookings, PaymentApi payments, UserApi users,
			ApplicationEventPublisher events) {
		this.jdbc = jdbc;
		this.bookings = bookings;
		this.payments = payments;
		this.users = users;
		this.events = events;
	}

	/**
	 * Chạy đồng bộ trong transaction của callback, sau khi listener của booking đã khoá booking (TDD §3.3, BR-44,
	 * BR-66): tạo yêu cầu hoàn {@code INVALID_PAYMENT} đúng bằng phần chưa hoàn của lượt thu.
	 */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	void on(UnexpectedPaymentReceived event) {
		long amount = payments.unrefunded(event.paymentId());
		jdbc.sql("""
				INSERT INTO refund_requests (booking_id, payment_id, source, status, base_amount, fee_amount,
				                             refund_amount)
				VALUES (?, ?, 'INVALID_PAYMENT', 'PENDING', ?, 0, ?)
				""").params(event.bookingId(), event.paymentId(), amount, amount).update();
	}

	/** TDD §6.6 bước 1: chiều chuyển REFUNDED và ghế được trả; tiền hoàn ở {@link #execute}. */
	@Transactional
	public void approve(long id, long staffId, String note) {
		Locked r = lockWithBooking(id);
		if (r.status() != RefundStatus.PENDING) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "Chỉ duyệt được yêu cầu đang chờ duyệt");
		}
		if (r.journeyId() != null) {
			bookings.refundJourney(r.journeyId());
		}
		jdbc.sql("""
				UPDATE refund_requests SET status = 'APPROVED', staff_note = ?, processed_by = ?, processed_at = now()
				WHERE id = ?
				""").params(note, staffId, id).update();
	}

	/** TDD §6.6 bước 2, BR-64: chỉ hoàn phần còn thiếu; trả {@code last_error} khi chưa hoàn đủ. */
	@Transactional
	public Optional<String> execute(long id, long staffId) {
		Locked r = lockWithBooking(id);
		if (r.status() != RefundStatus.APPROVED) {
			throw new BusinessException(ErrorCode.INVALID_STATE,
					"Chỉ thử lại được yêu cầu đã duyệt nhưng chưa hoàn xong");
		}
		long remaining = remaining(id, r);
		if (remaining > 0) {
			// ponytail: dòng booking và một kết nối DB bị giữ suốt các lượt gọi HTTP (tối đa ~35s mỗi lượt thu); đủ cho quy mô này, nếu tranh chấp khoá tăng thì đưa lệnh gọi VNPay ra ngoài khoá.
			RefundOutcome outcome = payments.refund(order(id, r, remaining, staffId));
			remaining -= outcome.refunded();
			if (remaining > 0) {
				String error = outcome.error() != null ? outcome.error() : "Không còn lượt thu nào để hoàn";
				jdbc.sql("UPDATE refund_requests SET last_error = ? WHERE id = ?").params(error, id).update();
				return Optional.of(error);
			}
		}
		complete(id);
		return Optional.empty();
	}

	/** BR-63, BR-66: chỉ yêu cầu của khách đang chờ duyệt mới từ chối được. */
	@Transactional
	public void reject(long id, long staffId, String reason) {
		Locked r = lock(id);
		if (r.source() != RefundSource.CUSTOMER) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "Yêu cầu do hệ thống tạo không thể bị từ chối");
		}
		if (r.status() != RefundStatus.PENDING) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "Chỉ từ chối được yêu cầu đang chờ duyệt");
		}
		jdbc.sql("""
				UPDATE refund_requests SET status = 'REJECTED', staff_note = ?, processed_by = ?, processed_at = now()
				WHERE id = ?
				""").params(reason, staffId, id).update();
		events.publishEvent(new RefundRejected(id));
	}

	/** BR-64: Staff đã chuyển tiền ngoài hệ thống; ghi sổ phần còn thiếu rồi hoàn tất. */
	@Transactional
	public void markManual(long id, long staffId, String note) {
		if (note == null || note.isBlank()) {
			throw BusinessException.invalidField("note", "Vui lòng nhập ghi chú");
		}
		Locked r = lockWithBooking(id);
		if (r.status() != RefundStatus.APPROVED) {
			throw new BusinessException(ErrorCode.INVALID_STATE,
					"Chỉ xác nhận hoàn thủ công cho yêu cầu đã duyệt nhưng chưa hoàn xong");
		}
		long remaining = remaining(id, r);
		if (remaining > 0) {
			payments.refundManually(order(id, r, remaining, staffId));
		}
		jdbc.sql("UPDATE refund_requests SET staff_note = ? WHERE id = ?").params(note, id).update();
		complete(id);
	}

	private void complete(long id) {
		jdbc.sql("UPDATE refund_requests SET status = 'COMPLETED', completed_at = now(), last_error = NULL WHERE id = ?")
			.param(id)
			.update();
		events.publishEvent(new RefundCompleted(id));
	}

	private long remaining(long id, Locked r) {
		return r.refundAmount() - payments.refunds(id)
			.stream()
			.filter(a -> "SUCCESS".equals(a.status()))
			.mapToLong(PaymentApi.RefundAttempt::amount)
			.sum();
	}

	/** BR-62, BR-66: lượt thu trùng chỉ do yêu cầu INVALID_PAYMENT của chính nó hoàn. */
	private RefundOrder order(long id, Locked r, long amount, long staffId) {
		String createdBy = users.get(staffId).email();
		if (r.source() == RefundSource.INVALID_PAYMENT) {
			return new RefundOrder(r.bookingId(), id, amount, r.paymentId(), List.of(), createdBy);
		}
		List<Long> excluded = jdbc
			.sql("SELECT payment_id FROM refund_requests WHERE booking_id = ? AND source = 'INVALID_PAYMENT'")
			.param(r.bookingId())
			.query(Long.class)
			.list();
		return new RefundOrder(r.bookingId(), id, amount, null, excluded, createdBy);
	}

	/** Thứ tự khoá bookings → refund_requests: đọc booking_id không khoá, khoá booking, rồi khoá yêu cầu. */
	private Locked lockWithBooking(long id) {
		long bookingId = jdbc.sql("SELECT booking_id FROM refund_requests WHERE id = ?")
			.param(id)
			.query(Long.class)
			.optional()
			.orElseThrow(RefundRequests::notFound);
		// ponytail: khoá bookings rồi ghi payments (FK KEY SHARE) lệch thứ tự chung payments → bookings, nhưng không thể deadlock: IPN chỉ chờ bookings khi lượt thu của nó đang PENDING, mà lượt thu PENDING không bao giờ là đích hoàn tiền.
		bookings.lock(bookingId);
		return lock(id);
	}

	private Locked lock(long id) {
		return jdbc.sql("""
				SELECT booking_id, journey_id, payment_id, source, status, refund_amount
				FROM refund_requests WHERE id = ? FOR UPDATE
				""")
			.param(id)
			.query((rs, n) -> new Locked(rs.getLong("booking_id"), rs.getObject("journey_id", Long.class),
					rs.getObject("payment_id", Long.class), RefundSource.valueOf(rs.getString("source")),
					RefundStatus.valueOf(rs.getString("status")), rs.getLong("refund_amount")))
			.optional()
			.orElseThrow(RefundRequests::notFound);
	}

	static BusinessException notFound() {
		return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu hoàn");
	}

}
