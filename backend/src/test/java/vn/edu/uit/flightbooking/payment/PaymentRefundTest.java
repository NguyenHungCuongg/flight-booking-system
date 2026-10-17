package vn.edu.uit.flightbooking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.payment.PaymentApi.RefundAttempt;
import vn.edu.uit.flightbooking.payment.PaymentApi.RefundOrder;
import vn.edu.uit.flightbooking.payment.PaymentApi.RefundOutcome;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;

/**
 * BR-62, BR-64, Q-09. Mỗi test có hai lượt thu của một booking: A (3.020.000, cũ) và B (500.000, mới nhất), cùng
 * một yêu cầu hoàn đã duyệt.
 */
@IntegrationTest
@Transactional
class PaymentRefundTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	Vnpay vnpay;

	@Autowired
	PaymentApi payments;

	@Autowired
	TestVnpayApi api;

	long bookingId;

	long requestId;

	String txnA;

	String txnB;

	long chargeA;

	long chargeB;

	@BeforeEach
	void setUp() throws Exception {
		TestBookings.Trip trip = TestBookings.trip(mvc, jdbc);
		TestUsers.TestUser customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		String code = TestBookings.holdCode(mvc, customer.session(), TestBookings.body(trip, Map.of()));
		bookingId = TestBookings.bookingId(jdbc, code);
		txnA = TestVnpay.start(mvc, customer.session(), code);
		mvc.perform(get("/api/payments/vnpay/ipn").params(TestVnpay.callback(vnpay, txnA, 3_020_000, "00")))
			.andExpect(status().isOk());
		chargeA = jdbc.sql("SELECT id FROM payments WHERE txn_ref = ?").param(txnA).query(Long.class).single();
		txnB = UUID.randomUUID().toString().replace("-", "");
		chargeB = jdbc.sql("""
				INSERT INTO payments (booking_id, kind, provider, status, amount, txn_ref, provider_txn_no,
				                      provider_txn_date, completed_at)
				VALUES (:bookingId, 'CHARGE', 'VNPAY', 'SUCCESS', 500000, :txnRef, '14000002', '20261201090000',
				        now() + interval '1 second')
				RETURNING id
				""").param("bookingId", bookingId).param("txnRef", txnB).query(Long.class).single();
		requestId = jdbc.sql("""
				INSERT INTO refund_requests (booking_id, journey_id, source, status, base_amount, fee_amount,
				                             refund_amount)
				VALUES (:bookingId, (SELECT id FROM booking_journeys WHERE booking_id = :bookingId
				                     AND direction = 'OUTBOUND'), 'CUSTOMER', 'APPROVED', 3000000, 0, 3000000)
				RETURNING id
				""").param("bookingId", bookingId).query(Long.class).single();
		api.reset();
	}

	RefundOrder order(long amount, Long only, List<Long> excluded) {
		return new RefundOrder(bookingId, requestId, amount, only, excluded, "staff@test.local");
	}

	/** BR-62: mới nhất trước, B hoàn trọn (02) rồi A hoàn một phần (03); A lỗi thì dừng, ghi FAILED. */
	@Test
	void refundsNewestChargeFirstAndStopsAtFirstFailure() {
		api.respond("00", "94");

		RefundOutcome first = payments.refund(order(3_000_000, null, List.of()));

		assertThat(first).isEqualTo(new RefundOutcome(500_000, "VNPay từ chối hoàn tiền (mã 94)"));
		assertThat(api.requests()).extracting(r -> r.get("vnp_TxnRef") + ":" + r.get("vnp_TransactionType") + ":"
				+ r.get("vnp_Amount")).containsExactly(txnB + ":02:50000000", txnA + ":03:250000000");
		assertThat(api.requests().get(1)).containsEntry("vnp_TransactionNo", "14000001")
			.containsEntry("vnp_TransactionDate", "20261201081000")
			.containsEntry("vnp_CreateBy", "staff@test.local")
			.containsEntry("vnp_OrderInfo", "Hoan tien yeu cau " + requestId);
		assertThat(payments.refunds(requestId)).extracting(RefundAttempt::status, RefundAttempt::amount,
				RefundAttempt::responseCode)
			.containsExactly(tuple("SUCCESS", 500_000L, "00"), tuple("FAILED", 2_500_000L, "94"));

		RefundOutcome retry = payments.refund(order(2_500_000, null, List.of()));

		assertThat(retry).isEqualTo(new RefundOutcome(2_500_000, null));
		assertThat(api.requests().getLast()).containsEntry("vnp_TxnRef", txnA)
			.containsEntry("vnp_TransactionType", "03");
		assertThat(payments.unrefunded(chargeA)).isEqualTo(520_000);
		assertThat(payments.unrefunded(chargeB)).isZero();
	}

	/** BR-66 và điểm khác TDD số 4: chỉ một lượt thu, hoặc loại một lượt thu. */
	@Test
	void onlyAndExcludedChargesLimitTheScope() {
		assertThat(payments.refund(order(100_000, chargeA, List.of())).refunded()).isEqualTo(100_000);
		assertThat(payments.refund(order(100_000, null, List.of(chargeB))).refunded()).isEqualTo(100_000);
		assertThat(api.requests()).extracting(r -> r.get("vnp_TxnRef")).containsExactly(txnA, txnA);
		assertThat(payments.unrefunded(chargeA)).isEqualTo(2_820_000);
	}

	/** Lỗi mạng: dòng FAILED không có mã phản hồi. */
	@Test
	void networkErrorIsRecordedAsFailed() {
		api.respond(TestVnpayApi.TIMEOUT);

		assertThat(payments.refund(order(100_000, null, List.of())))
			.isEqualTo(new RefundOutcome(0, "Không kết nối được VNPay"));
		assertThat(payments.refunds(requestId)).singleElement()
			.extracting(RefundAttempt::status, RefundAttempt::responseCode)
			.containsExactly("FAILED", null);
	}

	/** Body rỗng từ VNPay: không ném lỗi, dòng SUCCESS đã ghi trước đó được giữ, lần hỏng ghi FAILED. */
	@Test
	void nullBodyIsRecordedAsFailedAndKeepsEarlierRows() {
		api.respond("00", TestVnpayApi.NULL_BODY);

		RefundOutcome outcome = payments.refund(order(3_000_000, null, List.of()));

		assertThat(outcome).isEqualTo(new RefundOutcome(500_000, "Không kết nối được VNPay"));
		assertThat(payments.refunds(requestId)).extracting(RefundAttempt::status, RefundAttempt::amount,
				RefundAttempt::responseCode)
			.containsExactly(tuple("SUCCESS", 500_000L, "00"), tuple("FAILED", 2_500_000L, null));
	}

	/** BR-64: hoàn thủ công ghi dòng MANUAL SUCCESS, không gọi VNPay; vượt số đã thu thì báo lỗi, không ghi gì. */
	@Test
	void manualRefundWritesManualRowsWithoutCallingVnpay() {
		payments.refundManually(order(600_000, null, List.of()));

		assertThat(api.requests()).isEmpty();
		assertThat(payments.refunds(requestId)).extracting(RefundAttempt::provider, RefundAttempt::status,
				RefundAttempt::amount)
			.containsExactly(tuple("MANUAL", "SUCCESS", 500_000L), tuple("MANUAL", "SUCCESS", 100_000L));
		assertThatThrownBy(() -> payments.refundManually(order(9_000_000, null, List.of())))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE));
	}

}
