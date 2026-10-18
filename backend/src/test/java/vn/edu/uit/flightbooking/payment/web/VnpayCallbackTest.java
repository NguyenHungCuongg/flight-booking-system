package vn.edu.uit.flightbooking.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.booking.BookingIssued;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.booking.UnexpectedPaymentReceived;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.payment.PaymentApi;
import vn.edu.uit.flightbooking.payment.PaymentSucceeded;
import vn.edu.uit.flightbooking.payment.TestVnpay;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;

/**
 * FR-31, FR-32, BR-42–44, T3, T4. Mỗi test giữ chỗ một người lớn một chiều (3.020.000) và tạo một lượt thu. Listener
 * của booking chạy đồng bộ trong transaction của test, nên gọi IPN xong là thấy kết quả ngay.
 */
@IntegrationTest
@Transactional
@RecordApplicationEvents
class VnpayCallbackTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	Vnpay vnpay;

	@Autowired
	PaymentApi payments;

	@Autowired
	ApplicationEvents events;

	TestUsers.TestUser customer;

	String code;

	String txnRef;

	@BeforeEach
	void setUp() throws Exception {
		TestBookings.Trip trip = TestBookings.trip(mvc, jdbc);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		code = TestBookings.holdCode(mvc, customer.session(), TestBookings.body(trip, Map.of()));
		txnRef = TestVnpay.start(mvc, customer.session(), code);
	}

	ResultActions ipn(MultiValueMap<String, String> params) throws Exception {
		return mvc.perform(get("/api/payments/vnpay/ipn").params(params)).andExpect(status().isOk());
	}

	ResultActions back(MultiValueMap<String, String> params) throws Exception {
		return mvc.perform(get("/api/payments/vnpay/return").params(params)).andExpect(status().isFound());
	}

	/** Kết quả thành công cho lượt thu {@code ref}, đúng số tiền. */
	LinkedMultiValueMap<String, String> paid(String ref) {
		return TestVnpay.callback(vnpay, ref, 3_020_000, "00");
	}

	String bookingStatus() {
		return jdbc.sql("SELECT status FROM bookings WHERE code = ?").param(code).query(String.class).single();
	}

	Map<String, Object> payment(String ref) {
		return jdbc.sql("""
				SELECT id, status, provider_txn_no, provider_txn_date, response_code,
				       raw_response ->> 'vnp_BankCode' AS bank_code, completed_at
				FROM payments WHERE txn_ref = ?
				""").param(ref).query().singleRow();
	}

	long paymentId(String ref) {
		return ((Number) payment(ref).get("id")).longValue();
	}

	List<String> tickets() {
		return jdbc.sql("""
				SELECT t.ticket_number FROM tickets t JOIN passengers p ON p.id = t.passenger_id
				JOIN bookings b ON b.id = p.booking_id WHERE b.code = ? ORDER BY t.id
				""").param(code).query(String.class).list();
	}

	/**
	 * T3, BR-42, BR-43: IPN thành công thì xuất vé; VNPay gửi lại IPN thì trả 02; Return URL đến sau vẫn báo thành
	 * công. Số vé không đổi, PaymentSucceeded và BookingIssued (email vé) chỉ phát một lần.
	 */
	@Test
	void issuesTicketsExactlyOnceAcrossIpnRetryAndReturn() throws Exception {
		ipn(paid(txnRef)).andExpect(jsonPath("$.RspCode").value("00"))
			.andExpect(jsonPath("$.Message").value("Confirm Success"));

		assertThat(bookingStatus()).isEqualTo("ISSUED");
		List<String> numbers = tickets();
		assertThat(numbers).hasSize(2).allMatch(number -> number.matches("901[0-9]{10}"));
		assertThat(payment(txnRef)).containsEntry("status", "SUCCESS")
			.containsEntry("provider_txn_no", "14000001")
			.containsEntry("provider_txn_date", "20261201081000")
			.containsEntry("response_code", "00")
			.containsEntry("bank_code", "NCB");
		assertThat(payment(txnRef).get("completed_at")).isNotNull();

		ipn(paid(txnRef)).andExpect(jsonPath("$.RspCode").value("02"));
		back(paid(txnRef)).andExpect(redirectedUrl("http://localhost:3000/bookings/" + code + "?payment=success"));

		assertThat(tickets()).isEqualTo(numbers);
		assertThat(events.stream(PaymentSucceeded.class)).hasSize(1);
		assertThat(events.stream(BookingIssued.class))
			.containsExactly(new BookingIssued(TestBookings.bookingId(jdbc, code)));
		mvc.perform(get("/api/bookings/{code}", code).session(customer.session()))
			.andExpect(jsonPath("$.status").value("ISSUED"))
			.andExpect(jsonPath("$.payments[0].status").value("SUCCESS"))
			.andExpect(jsonPath("$.payments[0].completedAt").isNotEmpty());
	}

	/** T4: sai chữ ký (97), thiếu chữ ký (97), không có lượt thu (01), sai số tiền (04); dữ liệu không đổi. */
	@Test
	void rejectsForgedUnknownAndWrongAmountCallbacks() throws Exception {
		LinkedMultiValueMap<String, String> forged = paid(txnRef);
		forged.set("vnp_Amount", "100");
		LinkedMultiValueMap<String, String> unsigned = paid(txnRef);
		unsigned.remove("vnp_SecureHash");

		ipn(forged).andExpect(jsonPath("$.RspCode").value("97"));
		ipn(unsigned).andExpect(jsonPath("$.RspCode").value("97"));
		ipn(paid("f".repeat(32))).andExpect(jsonPath("$.RspCode").value("01"));
		ipn(TestVnpay.callback(vnpay, txnRef, 3_019_000, "00")).andExpect(jsonPath("$.RspCode").value("04"));
		back(forged).andExpect(redirectedUrl("http://localhost:3000/bookings?payment=failed"));

		assertThat(payment(txnRef)).containsEntry("status", "PENDING").containsEntry("completed_at", null);
		assertThat(bookingStatus()).isEqualTo("PENDING_PAYMENT");
		assertThat(tickets()).containsOnlyNulls();
		assertThat(events.stream(PaymentSucceeded.class)).isEmpty();
	}

	/** FR-30: khách huỷ trên VNPay (mã 24) thì lượt thu FAILED, booking vẫn chờ; thử lại bằng lượt thu mới thì được. */
	@Test
	void failedPaymentKeepsBookingPendingAndAllowsRetry() throws Exception {
		LinkedMultiValueMap<String, String> cancelledOnVnpay = TestVnpay.callback(vnpay, txnRef, 3_020_000, "24");
		ipn(cancelledOnVnpay).andExpect(jsonPath("$.RspCode").value("00"));
		back(cancelledOnVnpay)
			.andExpect(redirectedUrl("http://localhost:3000/bookings/" + code + "?payment=failed"));

		assertThat(payment(txnRef)).containsEntry("status", "FAILED").containsEntry("response_code", "24");
		assertThat(payment(txnRef).get("completed_at")).isNotNull();
		assertThat(bookingStatus()).isEqualTo("PENDING_PAYMENT");
		assertThat(events.stream(PaymentSucceeded.class)).isEmpty();

		String retry = TestVnpay.start(mvc, customer.session(), code);
		ipn(paid(retry)).andExpect(jsonPath("$.RspCode").value("00"));
		assertThat(bookingStatus()).isEqualTo("ISSUED");
	}

	/** BR-45: hết hạn giữ chỗ nhưng còn trong 5 phút ân hạn thì booking vẫn chờ, nên thanh toán đến vẫn xuất vé. */
	@Test
	void paymentArrivingDuringGracePeriodStillIssues() throws Exception {
		jdbc.sql("UPDATE bookings SET hold_expires_at = now() - INTERVAL '2 minutes' WHERE code = ?")
			.param(code)
			.update();

		ipn(paid(txnRef)).andExpect(jsonPath("$.RspCode").value("00"));

		assertThat(bookingStatus()).isEqualTo("ISSUED");
	}

	/** BR-44: khách đã huỷ booking rồi vẫn trả tiền. Lượt thu ghi nhận, booking giữ nguyên, phát sự kiện để hoàn. */
	@Test
	void paymentForCancelledBookingIsReportedInsteadOfIssued() throws Exception {
		mvc.perform(post("/api/bookings/{code}/cancel", code).with(csrf(mvc)).session(customer.session()))
			.andExpect(status().isNoContent());

		ipn(paid(txnRef)).andExpect(jsonPath("$.RspCode").value("00"));

		assertThat(payment(txnRef)).containsEntry("status", "SUCCESS");
		assertThat(bookingStatus()).isEqualTo("CANCELLED");
		assertThat(tickets()).containsOnlyNulls();
		assertThat(events.stream(BookingIssued.class)).isEmpty();
		assertThat(events.stream(UnexpectedPaymentReceived.class)).containsExactly(
				new UnexpectedPaymentReceived(paymentId(txnRef), TestBookings.bookingId(jdbc, code)));
	}

	/** BR-44: khách trả cả hai link của cùng một booking. Lần hai không cấp lại số vé mà phát sự kiện để hoàn. */
	@Test
	void secondPaymentForIssuedBookingIsReported() throws Exception {
		String second = TestVnpay.start(mvc, customer.session(), code);
		ipn(paid(txnRef)).andExpect(jsonPath("$.RspCode").value("00"));
		List<String> numbers = tickets();

		ipn(paid(second)).andExpect(jsonPath("$.RspCode").value("00"));

		assertThat(tickets()).isEqualTo(numbers);
		assertThat(events.stream(BookingIssued.class)).hasSize(1);
		assertThat(events.stream(UnexpectedPaymentReceived.class)).containsExactly(
				new UnexpectedPaymentReceived(paymentId(second), TestBookings.bookingId(jdbc, code)));
	}

	/** BR-42: mã phản hồi 00 nhưng vnp_TransactionStatus khác 00 thì lượt thu FAILED, booking vẫn chờ thanh toán. */
	@Test
	void responseCodeOkButTransactionStatusFailedIsNotSuccess() throws Exception {
		LinkedMultiValueMap<String, String> params = paid(txnRef);
		params.set("vnp_TransactionStatus", "02");
		params.set("vnp_SecureHash", vnpay.hash(params.toSingleValueMap()));

		ipn(params).andExpect(jsonPath("$.RspCode").value("00"));

		assertThat(payment(txnRef)).containsEntry("status", "FAILED");
		assertThat(bookingStatus()).isEqualTo("PENDING_PAYMENT");
		assertThat(events.stream(PaymentSucceeded.class)).isEmpty();
	}

	/** Review Focus 3: lượt thu của đổi chuyến mang reschedule_id; listener của booking bỏ qua (aftersales xử lý). */
	@Test
	void reschedulePaymentCarriesRescheduleIdAndLeavesBookingAlone() throws Exception {
		ipn(paid(txnRef));
		long bookingId = TestBookings.bookingId(jdbc, code);
		long rescheduleId = jdbc.sql("""
				INSERT INTO reschedules (booking_id, journey_id, status, old_fare_total, new_fare_total,
				                         change_fee_total, amount_due, hold_expires_at)
				SELECT b.id, j.id, 'PENDING_PAYMENT', 0, 0, 500000, 500000, now() + interval '15 minutes'
				FROM bookings b JOIN booking_journeys j ON j.booking_id = b.id WHERE b.id = ?
				RETURNING id
				""").param(bookingId).query(Long.class).single();
		String ref = TestVnpay.query(payments.charge(bookingId, rescheduleId, 500_000, Instant.now().plusSeconds(900),
				"Thanh toan doi chuyen " + code, "127.0.0.1")).get("vnp_TxnRef");

		ipn(TestVnpay.callback(vnpay, ref, 500_000, "00")).andExpect(jsonPath("$.RspCode").value("00"));

		assertThat(jdbc.sql("SELECT reschedule_id FROM payments WHERE txn_ref = ?").param(ref).query(Long.class).single())
			.isEqualTo(rescheduleId);
		assertThat(events.stream(PaymentSucceeded.class))
			.containsExactly(new PaymentSucceeded(paymentId(txnRef), bookingId, null, 3_020_000),
					new PaymentSucceeded(paymentId(ref), bookingId, rescheduleId, 500_000));
		assertThat(events.stream(UnexpectedPaymentReceived.class)).isEmpty();
		assertThat(events.stream(BookingIssued.class)).hasSize(1);
		assertThat(bookingStatus()).isEqualTo("ISSUED");
	}

}
