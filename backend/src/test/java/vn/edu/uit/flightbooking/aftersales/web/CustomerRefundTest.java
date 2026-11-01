package vn.edu.uit.flightbooking.aftersales.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.aftersales.TestRefunds;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.booking.domain.BookingCancellation;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.payment.TestVnpay;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;

/** FR-50, FR-51, BR-51, BR-60, BR-104, T6. Mỗi test có một booking khứ hồi đã xuất vé (3.020.000 + 2.160.000). */
@IntegrationTest
@Transactional
class CustomerRefundTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	Vnpay vnpay;

	@Autowired
	BookingCancellation cancellation;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	String code;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		TestRefunds.refundable(jdbc, trip);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		code = TestRefunds.issued(mvc, jdbc, vnpay, customer, TestBookings.body(trip, Map.of("journeys", trip.roundTrip())));
	}

	/** FR-50: chiều đi 3.020.000, phí 300.000 × 1 khách. */
	@Test
	void quoteShowsJourneyValueFeeAndRefund() throws Exception {
		TestRefunds.quote(mvc, customer.session(), code, "outbound")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.journeyValue").value(3_020_000))
			.andExpect(jsonPath("$.fee").value(300_000))
			.andExpect(jsonPath("$.refundAmount").value(2_720_000));
	}

	/** FR-51, BR-61: số tiền chốt lúc gửi; booking và chiều chưa đổi cho tới khi Staff duyệt. */
	@Test
	void requestLocksAmountsAndLeavesBookingUntouched() throws Exception {
		String json = TestRefunds.request(mvc, customer.session(), code, "OUTBOUND", "Đổi kế hoạch")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.refundAmount").value(2_720_000))
			.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(json, "$.id")).longValue();

		assertThat(jdbc.sql("SELECT * FROM refund_requests WHERE id = ?").param(id).query().singleRow())
			.containsEntry("source", "CUSTOMER").containsEntry("status", "PENDING")
			.containsEntry("base_amount", 3_020_000L).containsEntry("fee_amount", 300_000L)
			.containsEntry("refund_amount", 2_720_000L).containsEntry("reason", "Đổi kế hoạch")
			.containsEntry("requested_by", customer.id()).containsEntry("payment_id", null);
		mvc.perform(get("/api/bookings/{code}", code).session(customer.session()))
			.andExpect(jsonPath("$.status").value("ISSUED"))
			.andExpect(jsonPath("$.journeys[0].status").value("ACTIVE"))
			.andExpect(jsonPath("$.refundRequests[0].status").value("PENDING"));
	}

	/** BR-51 điều kiện 5: một yêu cầu mở mỗi chiều; yêu cầu đổi đang chờ thanh toán cũng chặn. Chiều kia vẫn gửi được. */
	@Test
	void openRequestOrPendingRescheduleBlocksTheJourney() throws Exception {
		TestRefunds.requestId(mvc, customer.session(), code, "OUTBOUND", "Đổi kế hoạch");
		TestRefunds.request(mvc, customer.session(), code, "OUTBOUND", "Lần hai")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REQUEST_IN_PROGRESS"));

		long rescheduleId = jdbc.sql("""
				INSERT INTO reschedules (booking_id, journey_id, status, old_fare_total, new_fare_total,
				                         change_fee_total, amount_due, hold_expires_at)
				SELECT b.id, j.id, 'PENDING_PAYMENT', 0, 0, 0, 0, now() + interval '15 minutes'
				FROM bookings b JOIN booking_journeys j ON j.booking_id = b.id
				WHERE b.code = ? AND j.direction = 'RETURN'
				RETURNING id
				""").param(code).query(Long.class).single();
		TestRefunds.quote(mvc, customer.session(), code, "RETURN")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REQUEST_IN_PROGRESS"));

		jdbc.sql("DELETE FROM reschedules WHERE id = ?").param(rescheduleId).update();
		TestRefunds.request(mvc, customer.session(), code, "RETURN", "Huỷ chiều về").andExpect(status().isCreated());
	}

	/** BR-60, BR-51 điều kiện 4: snapshot không cho hoàn; còn dưới 24 giờ là quá hạn (cả quote lẫn gửi). */
	@Test
	void fareRuleAndDeadlineAreChecked() throws Exception {
		jdbc.sql("""
				UPDATE booking_journeys SET refundable = FALSE
				WHERE direction = 'RETURN' AND booking_id = (SELECT id FROM bookings WHERE code = ?)
				""").param(code).update();
		TestRefunds.quote(mvc, customer.session(), code, "RETURN")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FARE_RULE_NOT_ALLOWED"));
		TestRefunds.request(mvc, customer.session(), code, "RETURN", "Huỷ")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FARE_RULE_NOT_ALLOWED"));

		jdbc.sql("""
				UPDATE flights SET departure_time = now() + interval '23 hours', arrival_time = now() + interval '25 hours'
				WHERE id = ?
				""").param(trip.firstLeg()).update();
		TestRefunds.quote(mvc, customer.session(), code, "OUTBOUND")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DEADLINE_PASSED"))
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("24 giờ")));
		TestRefunds.request(mvc, customer.session(), code, "OUTBOUND", "Huỷ")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DEADLINE_PASSED"));

		jdbc.sql("""
				UPDATE flights SET departure_time = now() + interval '25 hours', arrival_time = now() + interval '27 hours'
				WHERE id = ?
				""").param(trip.firstLeg()).update();
		TestRefunds.quote(mvc, customer.session(), code, "OUTBOUND").andExpect(status().isOk());
	}

	/** BR-51 điều kiện 1: booking chưa xuất vé; thiếu lý do là 400 theo trường. */
	@Test
	void unpaidBookingAndMissingReasonAreRejected() throws Exception {
		String unpaid = TestBookings.holdCode(mvc, customer.session(), TestBookings.body(trip, Map.of()));
		TestRefunds.quote(mvc, customer.session(), unpaid, "OUTBOUND")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));

		TestRefunds.request(mvc, customer.session(), code, "OUTBOUND", "  ")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("reason"));
	}

	/** BR-104: booking người khác, chiều không có hoặc sai tên đều 404. Staff gọi API của khách nhận 403. */
	@Test
	void othersBookingsAndUnknownJourneysAreNotFound() throws Exception {
		TestUsers.TestUser other = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		TestRefunds.quote(mvc, other.session(), code, "OUTBOUND")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		TestRefunds.quote(mvc, customer.session(), code, "SIDEWAYS")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

		String oneWay = TestRefunds.issued(mvc, jdbc, vnpay, customer, TestBookings.body(trip, Map.of()));
		TestRefunds.quote(mvc, customer.session(), oneWay, "RETURN")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

		TestUsers.TestUser staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF);
		TestRefunds.quote(mvc, staff.session(), code, "OUTBOUND").andExpect(status().isForbidden());
	}

	/** T6, BR-44: booking đã EXPIRED vẫn nhận tiền thì có yêu cầu hoàn INVALID_PAYMENT đúng bằng lượt thu. */
	@Test
	void t6PaymentForExpiredBookingCreatesInvalidPaymentRequest() throws Exception {
		String late = TestBookings.holdCode(mvc, customer.session(), TestBookings.body(trip, Map.of()));
		String txnRef = TestVnpay.start(mvc, customer.session(), late);
		long bookingId = TestBookings.bookingId(jdbc, late);
		jdbc.sql("UPDATE bookings SET hold_expires_at = now() - interval '10 minutes' WHERE id = ?")
			.param(bookingId)
			.update();
		assertThat(cancellation.expire(bookingId)).isTrue();

		mvc.perform(get("/api/payments/vnpay/ipn").params(TestVnpay.callback(vnpay, txnRef, 3_020_000, "00")))
			.andExpect(status().isOk());

		long paymentId = jdbc.sql("SELECT id FROM payments WHERE booking_id = ? AND kind = 'CHARGE'")
			.param(bookingId)
			.query(Long.class)
			.single();
		assertThat(jdbc.sql("SELECT * FROM refund_requests WHERE booking_id = ?").param(bookingId).query().singleRow())
			.containsEntry("source", "INVALID_PAYMENT").containsEntry("status", "PENDING")
			.containsEntry("journey_id", null).containsEntry("payment_id", paymentId)
			.containsEntry("base_amount", 3_020_000L).containsEntry("refund_amount", 3_020_000L)
			.containsEntry("fee_amount", 0L).containsEntry("requested_by", null);
		assertThat(jdbc.sql("SELECT status FROM bookings WHERE id = ?").param(bookingId).query(String.class).single())
			.isEqualTo("EXPIRED");
	}

}
