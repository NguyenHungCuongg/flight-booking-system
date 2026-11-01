package vn.edu.uit.flightbooking.booking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.payment.TestVnpay;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;
import vn.edu.uit.flightbooking.promotion.TestVouchers;

/** FR-30, BR-41, FR-41 (lịch sử thu): POST /api/bookings/{code}/payments. Một người lớn, một chiều: 3.020.000. */
@IntegrationTest
@Transactional
class BookingPaymentTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	Vnpay vnpay;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
	}

	String hold(Map<String, Object> changes) throws Exception {
		return TestBookings.holdCode(mvc, customer.session(), TestBookings.body(trip, changes));
	}

	ResultActions pay(MockHttpSession session, String code) throws Exception {
		return mvc.perform(post("/api/bookings/{code}/payments", code).with(csrf(mvc)).session(session));
	}

	/** Số lượt thu của mọi booking thuộc {@link #customer}. */
	int payments() {
		return jdbc.sql("SELECT count(*) FROM payments p JOIN bookings b ON b.id = p.booking_id WHERE b.user_id = ?")
			.param(customer.id())
			.query(Integer.class)
			.single();
	}

	/**
	 * TDD §6.3: lượt thu PENDING đúng số tiền, URL ký đúng, IP lấy từ X-Forwarded-For, link hết hạn cùng hạn giữ
	 * chỗ. BR-41: tạo lại được (thử lại), mỗi lần một {@code txn_ref} mới; mã chữ thường vẫn tìm thấy.
	 */
	@Test
	void createsSignedPaymentUrlThatExpiresWithTheHold() throws Exception {
		String code = hold(Map.of());

		String json = mvc
			.perform(post("/api/bookings/{code}/payments", code).with(csrf(mvc))
				.session(customer.session())
				.header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();

		String url = JsonPath.read(json, "$.paymentUrl");
		assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
		Map<String, String> params = TestVnpay.query(url);
		assertThat(vnpay.verify(params)).isTrue();
		assertThat(params).containsEntry("vnp_Version", "2.1.0")
			.containsEntry("vnp_Command", "pay")
			.containsEntry("vnp_TmnCode", TestVnpay.TMN_CODE)
			.containsEntry("vnp_Amount", "302000000")
			.containsEntry("vnp_CurrCode", "VND")
			.containsEntry("vnp_OrderInfo", "Thanh toan dat ve " + code)
			.containsEntry("vnp_OrderType", "other")
			.containsEntry("vnp_Locale", "vn")
			.containsEntry("vnp_ReturnUrl", "http://localhost:3000/api/payments/vnpay/return")
			.containsEntry("vnp_IpAddr", "203.0.113.7");
		assertThat(params.get("vnp_TxnRef")).matches("[0-9a-f]{32}");
		assertThat(params.get("vnp_ExpireDate")).isEqualTo(jdbc.sql("""
				SELECT to_char(hold_expires_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYYMMDDHH24MISS')
				FROM bookings WHERE code = ?
				""").param(code).query(String.class).single());
		assertThat(jdbc.sql("""
				SELECT p.kind, p.provider, p.status, p.amount, p.expires_at = b.hold_expires_at AS same_expiry,
				       p.completed_at
				FROM payments p JOIN bookings b ON b.id = p.booking_id
				WHERE p.txn_ref = ?
				""").param(params.get("vnp_TxnRef")).query().singleRow())
			.containsEntry("kind", "CHARGE")
			.containsEntry("provider", "VNPAY")
			.containsEntry("status", "PENDING")
			.containsEntry("amount", 3_020_000L)
			.containsEntry("same_expiry", true)
			.containsEntry("completed_at", null);

		String retry = TestVnpay.start(mvc, customer.session(), code.toLowerCase());
		assertThat(retry).isNotEqualTo(params.get("vnp_TxnRef"));
		assertThat(payments()).isEqualTo(2);
	}

	/** FR-30, BR-41: quá hạn giữ chỗ là HOLD_EXPIRED; đã huỷ hoặc đã xuất vé là INVALID_STATE; không ghi lượt thu. */
	@Test
	void refusesWhenHoldIsOverOrBookingIsNotPending() throws Exception {
		String overdue = hold(Map.of());
		jdbc.sql("UPDATE bookings SET hold_expires_at = now() - INTERVAL '1 minute' WHERE code = ?")
			.param(overdue)
			.update();
		String expired = hold(Map.of());
		jdbc.sql("UPDATE bookings SET status = 'EXPIRED' WHERE code = ?").param(expired).update();
		String cancelled = hold(Map.of());
		mvc.perform(post("/api/bookings/{code}/cancel", cancelled).with(csrf(mvc)).session(customer.session()))
			.andExpect(status().isNoContent());
		TestVouchers.insert(jdbc, "QVFREE", Map.of("discount_value", 100));
		String issued = hold(Map.of("voucherCode", "QVFREE"));

		pay(customer.session(), overdue).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
		pay(customer.session(), expired).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
		pay(customer.session(), cancelled).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
		pay(customer.session(), issued).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
		assertThat(payments()).isZero();
	}

	/** BR-104: booking của người khác như không tồn tại. */
	@Test
	void otherCustomersGetNotFound() throws Exception {
		String code = hold(Map.of());

		pay(TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER).session(), code).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		pay(customer.session(), "ZZZZZZ").andExpect(status().isNotFound());
		assertThat(payments()).isZero();
	}

	/** FR-41, S-02: chi tiết booking liệt kê lượt thu, kể cả lượt chưa có kết quả. */
	@Test
	void detailListsPayments() throws Exception {
		String code = hold(Map.of());
		pay(customer.session(), code).andExpect(status().isCreated());

		mvc.perform(get("/api/bookings/{code}", code).session(customer.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.payments.length()").value(1))
			.andExpect(jsonPath("$.payments[0].kind").value("CHARGE"))
			.andExpect(jsonPath("$.payments[0].status").value("PENDING"))
			.andExpect(jsonPath("$.payments[0].amount").value(3_020_000))
			.andExpect(jsonPath("$.payments[0].createdAt").isNotEmpty())
			.andExpect(jsonPath("$.payments[0].completedAt").value(nullValue()));
	}

}
