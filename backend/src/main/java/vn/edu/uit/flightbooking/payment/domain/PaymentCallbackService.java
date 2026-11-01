package vn.edu.uit.flightbooking.payment.domain;

import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;
import vn.edu.uit.flightbooking.payment.PaymentSucceeded;

/**
 * Xử lý kết quả VNPay cho cả IPN và Return URL (TDD §6.3, AD-07). Khoá lượt thu trước khi đọc trạng thái, nên cùng một
 * kết quả đến nhiều lần chỉ được xử lý một lần (BR-42). Listener của booking chạy trong cùng transaction và khoá
 * {@code bookings} sau {@code payments} (TDD §5.4).
 */
@Service
public class PaymentCallbackService {

	/**
	 * {@code rspCode}, {@code message}: phản hồi IPN theo VNPay. {@code bookingCode}: {@code null} khi chữ ký sai hoặc
	 * không có lượt thu. {@code paid}: lượt thu đang SUCCESS, kể cả khi đã được xử lý từ trước.
	 */
	public record Outcome(String rspCode, String message, String bookingCode, boolean paid) {
	}

	private record Locked(long id, long bookingId, Long rescheduleId, long amount, String status,
			String bookingCode) {
	}

	/**
	 * Đọc {@code bookings.code} để Return URL về đúng trang booking: payment không phụ thuộc module booking (TDD
	 * §3.2), nên đọc thẳng bảng như {@code BookingQueries} đọc {@code flights}.
	 */
	private static final String LOCK = """
			SELECT p.id, p.booking_id, p.reschedule_id, p.amount, p.status, b.code
			FROM payments p
			JOIN bookings b ON b.id = p.booking_id
			WHERE p.txn_ref = :txnRef AND p.kind = 'CHARGE'
			FOR UPDATE OF p
			""";

	private static final String COMPLETE = """
			UPDATE payments
			SET status = :status, provider_txn_no = :txnNo, provider_txn_date = :payDate,
			    response_code = :responseCode, raw_response = CAST(:raw AS jsonb), completed_at = now()
			WHERE id = :id
			""";

	private final Vnpay vnpay;

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	PaymentCallbackService(Vnpay vnpay, JdbcClient jdbc, ApplicationEventPublisher events) {
		this.vnpay = vnpay;
		this.jdbc = jdbc;
		this.events = events;
	}

	/** Đúng 6 bước của TDD §6.3; lỗi ở listener làm rollback toàn bộ, IPN trả 99 và VNPay gọi lại. */
	@Transactional
	public Outcome handle(Map<String, String> params) {
		if (!vnpay.verify(params)) {
			return new Outcome("97", "Invalid signature", null, false);
		}
		Optional<Locked> found = jdbc.sql(LOCK)
			.param("txnRef", params.get("vnp_TxnRef"))
			.query((rs, rowNum) -> new Locked(rs.getLong("id"), rs.getLong("booking_id"),
					rs.getObject("reschedule_id", Long.class), rs.getLong("amount"),
					rs.getString("status"), rs.getString("code")))
			.optional();
		if (found.isEmpty()) {
			return new Outcome("01", "Order not found", null, false);
		}
		Locked payment = found.get();
		if (!String.valueOf(payment.amount() * 100).equals(params.get("vnp_Amount"))) {
			return new Outcome("04", "Invalid amount", payment.bookingCode(), false);
		}
		if (!"PENDING".equals(payment.status())) {
			return new Outcome("02", "Order already confirmed", payment.bookingCode(),
					"SUCCESS".equals(payment.status()));
		}
		boolean paid = "00".equals(params.get("vnp_ResponseCode"))
				&& "00".equals(params.get("vnp_TransactionStatus"));
		jdbc.sql(COMPLETE)
			.param("status", paid ? "SUCCESS" : "FAILED")
			.param("txnNo", params.get("vnp_TransactionNo"))
			.param("payDate", params.get("vnp_PayDate"))
			.param("responseCode", params.get("vnp_ResponseCode"))
			.param("raw", JsonMapper.shared().writeValueAsString(params))
			.param("id", payment.id())
			.update();
		if (paid) {
			events.publishEvent(new PaymentSucceeded(payment.id(), payment.bookingId(), payment.rescheduleId(),
					payment.amount()));
		}
		return new Outcome("00", "Confirm Success", payment.bookingCode(), paid);
	}

}
