package vn.edu.uit.flightbooking.aftersales.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.aftersales.TestRefunds;
import vn.edu.uit.flightbooking.aftersales.TestReschedules;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.flight.FlightCancelled;
import vn.edu.uit.flightbooking.payment.TestVnpay;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;
import vn.edu.uit.flightbooking.promotion.TestVouchers;

@IntegrationTest
@Transactional
@RecordApplicationEvents
class FlightCancelledTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	Vnpay vnpay;

	@Autowired
	ApplicationEvents events;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	TestUsers.TestUser staff;

	TestUsers.TestUser admin;

	long voucherId;

	long q1310;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		TestReschedules.changeFee(jdbc, trip);
		TestRefunds.refundable(jdbc, trip);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF);
		admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);
		voucherId = TestVouchers.insert(jdbc, "QVCANCEL", Map.of());
		q1310 = TestBookings.returnFlight(mvc, jdbc, trip, "Q1310", DAY.plusDays(4), "06:00", 2_500_000);
	}

	String issued(List<Map<String, Object>> journeys) throws Exception {
		return TestRefunds.issued(mvc, jdbc, vnpay, customer,
				TestBookings.body(trip, Map.of("journeys", journeys)));
	}

	ResultActions cancel(long flightId) throws Exception {
		return mvc.perform(post("/api/admin/flights/{id}/cancel", flightId).with(csrf(mvc)).session(admin.session()));
	}

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

	String bookingStatus(String code) {
		return jdbc.sql("SELECT status FROM bookings WHERE code = ?").param(code).query(String.class).single();
	}

	String rescheduleStatus(long id) {
		return jdbc.sql("SELECT status FROM reschedules WHERE id = ?").param(id).query(String.class).single();
	}

	void ipn(String txnRef, long amount) throws Exception {
		mvc.perform(get("/api/payments/vnpay/ipn").params(TestVnpay.callback(vnpay, txnRef, amount, "00")))
			.andExpect(status().isOk());
	}

	List<Map<String, Object>> refunds(String code) {
		return jdbc.sql("""
				SELECT r.id, j.direction, r.source, r.status, r.base_amount, r.fee_amount, r.refund_amount, r.requested_by,
				       r.payment_id
				FROM refund_requests r
				JOIN bookings b ON b.id = r.booking_id
				LEFT JOIN booking_journeys j ON j.id = r.journey_id
				WHERE b.code = ?
				ORDER BY r.id
				""").param(code).query().listOfRows();
	}

	/**
	 * T12, BR-84 (a)–(c). Trên Q1300 cùng lúc có: booking chờ thanh toán dùng voucher; yêu cầu đổi có chặng cũ trên
	 * Q1300 (changing); yêu cầu đổi có chặng mới trên Q1300 (incoming); yêu cầu hoàn đang chờ của khách (refunding).
	 */
	@Test
	void t12CancellingFlightAppliesAllConsequences() throws Exception {
		String pending = TestBookings.holdCode(mvc, customer.session(),
				TestBookings.body(trip, Map.of("journeys", trip.roundTrip(), "voucherCode", "QVCANCEL")));
		String changing = issued(trip.roundTrip());
		long r1 = TestReschedules.confirmId(mvc, customer.session(), changing, "RETURN", q1310);
		String incoming = issued(List.of(trip.journey("OUTBOUND", trip.firstLeg(), trip.secondLeg()),
				trip.journey("RETURN", q1310)));
		long r2 = TestReschedules.confirmId(mvc, customer.session(), incoming, "RETURN", trip.back());
		String refunding = issued(trip.roundTrip());
		long customerRequest = TestRefunds.requestId(mvc, customer.session(), refunding, "RETURN", "Đổi kế hoạch");
		assertThat(TestBookings.available(jdbc, trip.back())).isEqualTo(96);
		assertThat(TestBookings.available(jdbc, q1310)).isEqualTo(98);

		cancel(trip.back()).andExpect(status().isOk()).andExpect(jsonPath("$.flight.status").value("CANCELLED"));

		// (a) booking chờ thanh toán
		assertThat(bookingStatus(pending)).isEqualTo("CANCELLED");
		assertThat(TestVouchers.usedCount(jdbc, voucherId)).isZero();
		assertThat(refunds(pending)).isEmpty();
		// (b) yêu cầu đổi đang dở, chặng cũ hoặc chặng mới trên chuyến
		assertThat(rescheduleStatus(r1)).isEqualTo("CANCELLED");
		assertThat(segments(changing, "RETURN")).containsExactly("ACTIVE:Q1300", "CANCELLED:Q1310");
		assertThat(rescheduleStatus(r2)).isEqualTo("CANCELLED");
		assertThat(segments(incoming, "RETURN")).containsExactly("ACTIVE:Q1310", "CANCELLED:Q1300");
		assertThat(TestBookings.available(jdbc, trip.back())).isEqualTo(98);
		assertThat(TestBookings.available(jdbc, q1310)).isEqualTo(99);
		// (c) yêu cầu hoàn 100%: tạo mới, hoặc chuyển nguồn yêu cầu đang chờ của khách
		assertThat(refunds(changing)).singleElement().satisfies(r -> assertThat(r)
			.containsEntry("direction", "RETURN").containsEntry("source", "FLIGHT_CANCELLED")
			.containsEntry("status", "PENDING").containsEntry("base_amount", 2_160_000L)
			.containsEntry("fee_amount", 0L).containsEntry("refund_amount", 2_160_000L)
			.containsEntry("requested_by", null));
		assertThat(refunds(refunding)).singleElement().satisfies(r -> assertThat(r)
			.containsEntry("id", customerRequest).containsEntry("source", "FLIGHT_CANCELLED")
			.containsEntry("status", "PENDING").containsEntry("base_amount", 2_160_000L)
			.containsEntry("fee_amount", 0L).containsEntry("refund_amount", 2_160_000L)
			.containsEntry("requested_by", customer.id()));
		assertThat(refunds(incoming)).isEmpty();
		assertThat(List.of(bookingStatus(changing), bookingStatus(incoming), bookingStatus(refunding)))
			.containsOnly("ISSUED");
		assertThat(events.stream(FlightCancelled.class)).containsExactly(new FlightCancelled(trip.back()));
		// BR-66: yêu cầu do hệ thống tạo (kể cả yêu cầu vừa chuyển nguồn) không từ chối được
		mvc.perform(post("/api/staff/refund-requests/{id}/reject", customerRequest).with(csrf(mvc))
				.session(staff.session()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Không\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
	}

	/** Review Focus 1, BR-44: thanh toán đến sau khi chuyến bị huỷ thành yêu cầu hoàn INVALID_PAYMENT. */
	@Test
	void latePaymentsAfterCancellationBecomeInvalidPaymentRefunds() throws Exception {
		String pending = TestBookings.holdCode(mvc, customer.session(),
				TestBookings.body(trip, Map.of("journeys", trip.roundTrip())));
		String ref1 = TestVnpay.start(mvc, customer.session(), pending);
		long total = jdbc.sql("SELECT total_amount FROM bookings WHERE code = ?").param(pending).query(Long.class).single();

		String changing = issued(trip.roundTrip());
		long r1 = TestReschedules.confirmId(mvc, customer.session(), changing, "RETURN", q1310);
		String ref2 = TestReschedules.payTxnRef(mvc, customer.session(), r1);

		cancel(trip.back()).andExpect(status().isOk());

		ipn(ref1, total);
		ipn(ref2, 540_000);

		assertThat(bookingStatus(pending)).isEqualTo("CANCELLED");
		assertThat(jdbc.sql("SELECT count(*) FROM tickets t JOIN passengers p ON p.id = t.passenger_id JOIN bookings b ON b.id = p.booking_id WHERE b.code = ? AND t.ticket_number IS NOT NULL")
				.param(pending).query(Long.class).single()).isZero();

		assertThat(refunds(pending)).singleElement().satisfies(r -> assertThat(r)
			.containsEntry("source", "INVALID_PAYMENT")
			.containsEntry("refund_amount", total));

		assertThat(rescheduleStatus(r1)).isEqualTo("CANCELLED");
		assertThat(segments(changing, "RETURN")).containsExactly("ACTIVE:Q1300", "CANCELLED:Q1310");

		List<Map<String, Object>> changingRefunds = refunds(changing);
		assertThat(changingRefunds).hasSize(2);
		assertThat(changingRefunds.get(0))
			.containsEntry("source", "FLIGHT_CANCELLED")
			.containsEntry("refund_amount", 2_160_000L);
		assertThat(changingRefunds.get(1))
			.containsEntry("source", "INVALID_PAYMENT")
			.containsEntry("refund_amount", 540_000L);
		assertThat(changingRefunds.get(1).get("payment_id")).isNotNull();
	}

	/** Review Focus 3: Chiều nối chuyến bị huỷ một chặng thì hoàn trọn giá trị chiều. */
	@Test
	void connectingJourneyIsRefundedWhole() throws Exception {
		String code = issued(trip.roundTrip());
		cancel(trip.secondLeg()).andExpect(status().isOk());

		assertThat(refunds(code)).singleElement().satisfies(r -> assertThat(r)
			.containsEntry("direction", "OUTBOUND")
			.containsEntry("source", "FLIGHT_CANCELLED")
			.containsEntry("refund_amount", 3_020_000L));

		assertThat(segments(code, "RETURN")).containsExactly("ACTIVE:Q1300");
	}

	/** Review Focus 4: Chuyến đã huỷ không còn giữ chỗ, đổi sang được, hoặc hiện trong options. */
	@Test
	void cancelledFlightCannotBeBookedOrChangedInto() throws Exception {
		String incoming = issued(List.of(trip.journey("OUTBOUND", trip.firstLeg(), trip.secondLeg()),
				trip.journey("RETURN", q1310)));
		cancel(trip.back()).andExpect(status().isOk());

		TestBookings.hold(mvc, customer.session(), TestBookings.body(trip, Map.of("journeys", trip.roundTrip())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FLIGHT_NOT_BOOKABLE"))
			.andExpect(jsonPath("$.detail").value("Chuyến Q1300 đã bị huỷ"));

		TestReschedules.confirm(mvc, customer.session(), incoming, "RETURN", trip.back())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FLIGHT_NOT_BOOKABLE"));

		TestReschedules.options(mvc, customer.session(), incoming, "RETURN", DAY.plusDays(3))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.options").isEmpty());
	}

}

