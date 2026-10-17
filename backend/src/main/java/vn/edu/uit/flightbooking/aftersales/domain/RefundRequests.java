package vn.edu.uit.flightbooking.aftersales.domain;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.booking.UnexpectedPaymentReceived;
import vn.edu.uit.flightbooking.payment.PaymentApi;

/** Yêu cầu hoàn do hệ thống tạo (Task 5 thêm các thao tác của Staff). */
@Service
public class RefundRequests {

	private final JdbcClient jdbc;

	private final PaymentApi payments;

	RefundRequests(JdbcClient jdbc, PaymentApi payments) {
		this.jdbc = jdbc;
		this.payments = payments;
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

}
