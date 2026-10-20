package vn.edu.uit.flightbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.aftersales.TestRefunds;
import vn.edu.uit.flightbooking.booking.BookingApi.JourneyRef;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;
import vn.edu.uit.flightbooking.promotion.TestVouchers;

@IntegrationTest
@Transactional
class BookingFlightCancelTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	Vnpay vnpay;

	@Autowired
	BookingApi api;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	MockHttpSession admin;

	long voucherId;

	String pending;

	String issued;

	String other;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();

		voucherId = TestVouchers.insert(jdbc, "QVFLIGHT", Map.of());

		pending = TestBookings.holdCode(mvc, customer.session(),
				TestBookings.body(trip, Map.of("journeys", trip.roundTrip(), "voucherCode", "QVFLIGHT")));

		issued = TestRefunds.issued(mvc, jdbc, vnpay, customer,
				TestBookings.body(trip, Map.of("journeys", trip.roundTrip())));

		other = TestBookings.holdCode(mvc, customer.session(), TestBookings.body(trip,
				Map.of("journeys", List.of(trip.journey("OUTBOUND", trip.firstLeg(), trip.secondLeg())))));
	}

	ResultActions cancel(long flightId) throws Exception {
		return mvc.perform(post("/api/admin/flights/{id}/cancel", flightId).with(csrf(mvc)).session(admin));
	}

	String bookingStatus(String code) {
		return jdbc.sql("SELECT status FROM bookings WHERE code = ?").param(code).query(String.class).single();
	}

	long journeyId(long bookingId, String direction) {
		return jdbc.sql("SELECT id FROM booking_journeys WHERE booking_id = ? AND direction = ?")
			.params(bookingId, direction)
			.query(Long.class)
			.single();
	}

	/** BR-84 (a): booking chờ thanh toán có chặng trên chuyến bị huỷ cả booking, trả ghế mọi chặng và trả lượt voucher. */
	@Test
	void cancellingFlightCancelsPendingBookingsOnIt() throws Exception {
		cancel(trip.back()).andExpect(status().isOk());

		assertThat(bookingStatus(pending)).isEqualTo("CANCELLED");
		assertThat(jdbc.sql("""
				SELECT DISTINCT s.status FROM booking_segments s JOIN booking_journeys j ON j.id = s.journey_id
				WHERE j.booking_id = ?
				""").param(TestBookings.bookingId(jdbc, pending)).query(String.class).list()).containsExactly("CANCELLED");
		assertThat(TestVouchers.usedCount(jdbc, voucherId)).isZero();
		assertThat(TestBookings.available(jdbc, trip.firstLeg())).isEqualTo(98);
		assertThat(TestBookings.available(jdbc, trip.back())).isEqualTo(99);
		assertThat(bookingStatus(issued)).isEqualTo("ISSUED");
		assertThat(bookingStatus(other)).isEqualTo("PENDING_PAYMENT");
	}

	/** BR-84 (c) đầu vào: chiều ACTIVE của booking ISSUED có chặng ACTIVE trên chuyến, kèm giá trị Q-08. */
	@Test
	void issuedJourneysOnListsOnlyIssuedActiveJourneysOnFlight() {
		long bookingId = TestBookings.bookingId(jdbc, issued);

		assertThat(api.issuedJourneysOn(trip.back())).singleElement().satisfies(j -> {
			assertThat(j.bookingId()).isEqualTo(bookingId);
			assertThat(j.journeyId()).isEqualTo(journeyId(bookingId, "RETURN"));
			assertThat(j.value()).isEqualTo(2_160_000);
		});
		assertThat(api.issuedJourneysOn(trip.secondLeg())).singleElement()
			.satisfies(j -> assertThat(j.value()).isEqualTo(3_020_000));
		api.refundJourney(journeyId(bookingId, "RETURN"));
		assertThat(api.issuedJourneysOn(trip.back())).isEmpty();
	}

}
