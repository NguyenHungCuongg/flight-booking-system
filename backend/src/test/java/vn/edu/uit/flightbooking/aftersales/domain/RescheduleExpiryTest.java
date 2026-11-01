package vn.edu.uit.flightbooking.aftersales.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.aftersales.TestRefunds;
import vn.edu.uit.flightbooking.aftersales.TestReschedules;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.payment.TestVnpay;
import vn.edu.uit.flightbooking.payment.TestVnpayApi;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;

/**
 * BR-75, BR-44, T11. Mỗi test có một booking khứ hồi đã xuất vé (chiều về Q1300, phí đổi 200.000) và một chuyến về
 * thay thế Q1310 ngày DAY + 4.
 */
@IntegrationTest
@Transactional
class RescheduleExpiryTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	Vnpay vnpay;

	@Autowired
	TestVnpayApi api;

	@Autowired
	RescheduleExpiryJob job;

	@Autowired
	Reschedules reschedules;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	String code;

	long q1310;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		TestReschedules.changeFee(jdbc, trip);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		code = TestRefunds.issued(mvc, jdbc, vnpay, customer,
				TestBookings.body(trip, Map.of("journeys", trip.roundTrip())));
		q1310 = TestBookings.returnFlight(mvc, jdbc, trip, "Q1310", DAY.plusDays(4), "06:00", 2_500_000);
		api.reset();
	}

	String status(long id) {
		return jdbc.sql("SELECT status FROM reschedules WHERE id = ?").param(id).query(String.class).single();
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
			.andExpect(MockMvcResultMatchers.status().isOk());
	}

	/** T11, BR-75: quá hạn thêm 5 phút thì EXPIRED, trả ghế chuyến mới, chiều về giữ nguyên; đổi lại được. */
	@Test
	void t11ExpiredRescheduleReleasesNewSeatsAndKeepsJourney() throws Exception {
		long id = TestReschedules.confirmId(mvc, customer.session(), code, "RETURN", q1310);
		String ref = TestReschedules.payTxnRef(mvc, customer.session(), id);
		jdbc.sql("UPDATE reschedules SET hold_expires_at = now() - interval '10 minutes' WHERE id = ?").param(id).update();

		assertThat(job.expireOverdue()).isEqualTo(1);

		assertThat(status(id)).isEqualTo("EXPIRED");
		assertThat(segments(code, "RETURN")).containsExactly("ACTIVE:Q1300", "CANCELLED:Q1310");
		assertThat(TestBookings.available(jdbc, q1310)).isEqualTo(100);
		assertThat(TestBookings.available(jdbc, trip.back())).isEqualTo(99);
		mvc.perform(get("/api/bookings/{code}", code).session(customer.session()))
			.andExpect(jsonPath("$.journeys[1].segments[*].flightNumber").value(contains("Q1300")))
			.andExpect(jsonPath("$.reschedules[0].status").value("EXPIRED"));
		assertThat(reschedules.expire(id)).isFalse();

		ipn(ref, 540_000);

		assertThat(jdbc.sql("SELECT * FROM refund_requests WHERE payment_id = (SELECT id FROM payments WHERE txn_ref = ?)")
			.param(ref).query().singleRow())
			.containsEntry("source", "INVALID_PAYMENT").containsEntry("status", "PENDING")
			.containsEntry("refund_amount", 540_000L).containsEntry("journey_id", null);
		assertThat(status(id)).isEqualTo("EXPIRED");
		assertThat(segments(code, "RETURN")).containsExactly("ACTIVE:Q1300", "CANCELLED:Q1310");
		TestReschedules.confirm(mvc, customer.session(), code, "RETURN", q1310).andExpect(MockMvcResultMatchers.status().isCreated());
	}

	/** BR-75: trong 5 phút ân hạn thì chưa huỷ; thanh toán đến lúc đó vẫn hoàn tất đổi chuyến. */
	@Test
	void paymentWithinGraceStillCompletes() throws Exception {
		long id = TestReschedules.confirmId(mvc, customer.session(), code, "RETURN", q1310);
		String ref = TestReschedules.payTxnRef(mvc, customer.session(), id);
		jdbc.sql("UPDATE reschedules SET hold_expires_at = now() - interval '2 minutes' WHERE id = ?").param(id).update();

		assertThat(job.expireOverdue()).isZero();
		assertThat(status(id)).isEqualTo("PENDING_PAYMENT");

		ipn(ref, 540_000);

		assertThat(status(id)).isEqualTo("COMPLETED");
		assertThat(segments(code, "RETURN")).containsExactly("REPLACED:Q1300", "ACTIVE:Q1310");
	}

}
