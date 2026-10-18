package vn.edu.uit.flightbooking.aftersales.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;
import static vn.edu.uit.flightbooking.booking.TestBookings.ADULT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.CHILD_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.INFANT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.passenger;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;

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

import com.jayway.jsonpath.JsonPath;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.aftersales.RescheduleCompleted;
import vn.edu.uit.flightbooking.aftersales.TestRefunds;
import vn.edu.uit.flightbooking.aftersales.TestReschedules;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.payment.TestVnpay;
import vn.edu.uit.flightbooking.payment.TestVnpayApi;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;

/**
 * FR-61, FR-62, BR-73, BR-74, T9, T10. Mỗi test có một booking khứ hồi đã xuất vé (chiều về Q1300 giá 2.160.000,
 * phí đổi 200.000, phí hoàn 300.000) và hai chuyến về thay thế ngày DAY + 4.
 */
@IntegrationTest
@Transactional
@RecordApplicationEvents
class RescheduleTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	Vnpay vnpay;

	@Autowired
	TestVnpayApi api;

	@Autowired
	ApplicationEvents events;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	TestUsers.TestUser staff;

	String code;

	long q1310;

	long q1320;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		TestReschedules.changeFee(jdbc, trip);
		TestRefunds.refundable(jdbc, trip);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF);
		code = TestRefunds.issued(mvc, jdbc, vnpay, customer,
				TestBookings.body(trip, Map.of("journeys", trip.roundTrip())));
		q1310 = TestBookings.returnFlight(mvc, jdbc, trip, "Q1310", DAY.plusDays(4), "06:00", 2_500_000);
		q1320 = TestBookings.returnFlight(mvc, jdbc, trip, "Q1320", DAY.plusDays(4), "09:00", 1_900_000);
		api.reset();
	}

	/** Mỗi chặng của chiều dạng {@code status:flight_number}, theo id. */
	List<String> segments(String code, String direction) {
		return jdbc.sql("""
				SELECT s.status || ':' || f.flight_number
				FROM booking_segments s
				JOIN booking_journeys j ON j.id = s.journey_id
				JOIN bookings b ON b.id = j.booking_id
				JOIN flights f ON f.id = s.flight_id
				WHERE b.code = ? AND j.direction = ?
				ORDER BY s.id
				""").params(code, direction).query(String.class).list();
	}

	void ipn(String txnRef, long amount) throws Exception {
		mvc.perform(get("/api/payments/vnpay/ipn").params(TestVnpay.callback(vnpay, txnRef, amount, "00")))
			.andExpect(status().isOk());
	}

	/** Số vé của người lớn đầu tiên trên chặng có chuyến đó. */
	String ticketOf(String code, String flightNumber) {
		return jdbc.sql("""
				SELECT t.ticket_number FROM tickets t
				JOIN booking_segments s ON s.id = t.segment_id
				JOIN booking_journeys j ON j.id = s.journey_id
				JOIN bookings b ON b.id = j.booking_id
				JOIN flights f ON f.id = s.flight_id
				JOIN passengers p ON p.id = t.passenger_id
				WHERE b.code = ? AND f.flight_number = ? AND p.type = 'ADULT'
				ORDER BY p.id LIMIT 1
				""").params(code, flightNumber).query(String.class).single();
	}

	/** Số vé của người lớn đầu tiên trên chặng mới của yêu cầu đổi. */
	String newTicket(long rescheduleId) {
		return jdbc.sql("""
				SELECT t.ticket_number FROM tickets t
				JOIN booking_segments s ON s.id = t.segment_id
				JOIN passengers p ON p.id = t.passenger_id
				WHERE s.reschedule_id = ? AND p.type = 'ADULT'
				ORDER BY p.id LIMIT 1
				""").param(rescheduleId).query(String.class).single();
	}

	ResultActions approve(long id) throws Exception {
		return mvc
			.perform(post("/api/staff/refund-requests/{id}/approve", id).with(csrf(mvc)).session(staff.session()));
	}

	/** T10, FR-62, BR-73, BR-74: đổi chiều về sang Q1310, trả 540.000 qua VNPay, có số vé mới. */
	@Test
	void t10PaidRescheduleReplacesSegmentAndIssuesNewTicket() throws Exception {
		String oldTicket = ticketOf(code, "Q1300");
		String json = TestReschedules.confirm(mvc, customer.session(), code, "return", q1310)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.amountDue").value(540_000))
			.andExpect(jsonPath("$.holdExpiresAt").value(endsWith("+07:00")))
			.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(json, "$.id")).longValue();

		assertThat(jdbc.sql("SELECT * FROM reschedules WHERE id = ?").param(id).query().singleRow())
			.containsEntry("old_fare_total", 2_160_000L).containsEntry("new_fare_total", 2_500_000L)
			.containsEntry("change_fee_total", 200_000L).containsEntry("amount_due", 540_000L);
		assertThat(segments(code, "RETURN")).containsExactly("ACTIVE:Q1300", "PENDING_CHANGE:Q1310");
		assertThat(TestBookings.available(jdbc, trip.back())).isEqualTo(99);
		assertThat(TestBookings.available(jdbc, q1310)).isEqualTo(99);

		String ref = TestReschedules.payTxnRef(mvc, customer.session(), id);
		ipn(ref, 540_000);

		assertThat(jdbc.sql("SELECT status FROM reschedules WHERE id = ?").param(id).query(String.class).single())
			.isEqualTo("COMPLETED");
		assertThat(segments(code, "RETURN")).containsExactly("REPLACED:Q1300", "ACTIVE:Q1310");
		assertThat(TestBookings.available(jdbc, trip.back())).isEqualTo(100);
		assertThat(TestBookings.available(jdbc, q1310)).isEqualTo(99);
		assertThat(newTicket(id)).matches("901\\d{10}");
		assertThat(ticketOf(code, "Q1300")).isEqualTo(oldTicket);
		assertThat(jdbc.sql("SELECT count(*) FROM refund_requests r JOIN bookings b ON b.id = r.booking_id WHERE b.code = ?")
			.param(code).query(Long.class).single()).isZero();
		assertThat(events.stream(RescheduleCompleted.class)).containsExactly(new RescheduleCompleted(id));
		mvc.perform(get("/api/bookings/{code}", code).session(customer.session()))
			.andExpect(jsonPath("$.status").value("ISSUED"))
			.andExpect(jsonPath("$.journeys[1].segments[0].flightNumber").value("Q1310"))
			.andExpect(jsonPath("$.reschedules[0].status").value("COMPLETED"));
	}

	/**
	 * T9: hoàn chiều về sau khi đã đổi và trả thêm. Giá trị chiều 2.500.000 − phí 300.000 = 2.200.000; lượt thu đổi
	 * chuyến (mới nhất) hoàn trọn 540.000, lượt thu gốc hoàn 1.660.000; không lượt nào bị hoàn vượt số đã thu.
	 */
	@Test
	void t9RefundAfterPaidRescheduleSpreadsOverBothCharges() throws Exception {
		long id = TestReschedules.confirmId(mvc, customer.session(), code, "RETURN", q1310);
		String rescheduleRef = TestReschedules.payTxnRef(mvc, customer.session(), id);
		ipn(rescheduleRef, 540_000);
		TestRefunds.quote(mvc, customer.session(), code, "RETURN")
			.andExpect(jsonPath("$.journeyValue").value(2_500_000))
			.andExpect(jsonPath("$.refundAmount").value(2_200_000));
		long refund = TestRefunds.requestId(mvc, customer.session(), code, "RETURN", "Đổi kế hoạch");

		approve(refund).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

		String bookingRef = jdbc.sql("""
				SELECT p.txn_ref FROM payments p JOIN bookings b ON b.id = p.booking_id
				WHERE b.code = ? AND p.kind = 'CHARGE' AND p.reschedule_id IS NULL
				""").param(code).query(String.class).single();
		assertThat(api.requests()).extracting(r -> r.get("vnp_TxnRef") + ":" + r.get("vnp_TransactionType") + ":"
				+ r.get("vnp_Amount")).containsExactly(rescheduleRef + ":02:54000000", bookingRef + ":03:166000000");
		assertThat(jdbc.sql("""
				SELECT bool_and(c.amount >= (SELECT COALESCE(SUM(r.amount), 0) FROM payments r
				                             WHERE r.original_payment_id = c.id AND r.status = 'SUCCESS'))
				FROM payments c JOIN bookings b ON b.id = c.booking_id WHERE b.code = ? AND c.kind = 'CHARGE'
				""").param(code).query(Boolean.class).single()).isTrue();
	}

	/** FR-61, BR-73: không phải trả gì thì hoàn tất ngay, không tạo lượt thu. */
	@Test
	void zeroAmountCompletesImmediately() throws Exception {
		jdbc.sql("""
				UPDATE booking_journeys SET change_fee = 0 WHERE direction = 'RETURN'
				AND booking_id = (SELECT id FROM bookings WHERE code = ?)
				""").param(code).update();

		String json = TestReschedules.confirm(mvc, customer.session(), code, "RETURN", q1320)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.amountDue").value(0))
			.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(json, "$.id")).longValue();

		assertThat(segments(code, "RETURN")).containsExactly("REPLACED:Q1300", "ACTIVE:Q1320");
		assertThat(jdbc.sql("SELECT count(*) FROM payments WHERE reschedule_id = ?").param(id).query(Long.class).single())
			.isZero();
		assertThat(events.stream(RescheduleCompleted.class)).containsExactly(new RescheduleCompleted(id));
	}

	/** BR-53, BR-21: phí tính theo khách chiếm ghế, vé mới theo tỉ lệ từng loại khách. */
	@Test
	void childAndInfantPayFeeForSeatsAndGetTicketsAtTheirRates() throws Exception {
		String family = TestRefunds.issued(mvc, jdbc, vnpay, customer, TestBookings.body(trip,
				Map.of("journeys", trip.roundTrip(), "passengers", List.of(passenger("ADULT", ADULT_BORN),
						passenger("CHILD", CHILD_BORN), passenger("INFANT", INFANT_BORN)))));

		String json = TestReschedules.confirm(mvc, customer.session(), family, "RETURN", q1310)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.amountDue").value(1_080_000))
			.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(json, "$.id")).longValue();

		assertThat(jdbc.sql("SELECT * FROM reschedules WHERE id = ?").param(id).query().singleRow())
			.containsEntry("new_fare_total", 5_000_000L).containsEntry("change_fee_total", 400_000L);
		assertThat(jdbc.sql("""
				SELECT t.fare_amount FROM tickets t JOIN booking_segments s ON s.id = t.segment_id
				WHERE s.reschedule_id = ? ORDER BY t.passenger_id
				""").param(id).query(Long.class).list()).containsExactly(2_500_000L, 2_250_000L, 250_000L);
		assertThat(TestBookings.available(jdbc, q1310)).isEqualTo(98);
	}

	/** BR-51 điều kiện 5: yêu cầu đổi đang chờ chặn đổi tiếp và hoàn cùng chiều, không chặn chiều kia. */
	@Test
	void pendingRescheduleBlocksAnotherChangeAndRefund() throws Exception {
		TestReschedules.confirm(mvc, customer.session(), code, "RETURN", q1310).andExpect(status().isCreated());

		TestReschedules.confirm(mvc, customer.session(), code, "RETURN", q1320)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REQUEST_IN_PROGRESS"));
		TestRefunds.quote(mvc, customer.session(), code, "RETURN")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REQUEST_IN_PROGRESS"));
		TestReschedules.options(mvc, customer.session(), code, "OUTBOUND", DAY).andExpect(status().isOk());
	}

	/** FR-62: chỉ chủ yêu cầu, đang chờ thanh toán và còn hạn giữ chỗ mới trả được. */
	@Test
	void paymentNeedsOwnerPendingStateAndOpenHold() throws Exception {
		long id = TestReschedules.confirmId(mvc, customer.session(), code, "RETURN", q1310);

		TestUsers.TestUser other = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		TestReschedules.pay(mvc, other.session(), id)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Không tìm thấy yêu cầu đổi chuyến"));
		TestReschedules.pay(mvc, customer.session(), 999_999_999L)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Không tìm thấy yêu cầu đổi chuyến"));

		String json = TestReschedules.pay(mvc, customer.session(), id)
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		Map<String, String> query = TestVnpay.query(JsonPath.read(json, "$.paymentUrl"));
		assertThat(query).containsEntry("vnp_Amount", "54000000")
			.containsEntry("vnp_OrderInfo", "Thanh toan doi chuyen " + code);

		jdbc.sql("UPDATE reschedules SET hold_expires_at = now() - interval '1 minute'").update();
		TestReschedules.pay(mvc, customer.session(), id)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));

		jdbc.sql("UPDATE reschedules SET status = 'COMPLETED', completed_at = now()").update();
		TestReschedules.pay(mvc, customer.session(), id)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
	}

}
