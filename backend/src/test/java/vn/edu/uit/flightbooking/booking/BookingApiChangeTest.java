package vn.edu.uit.flightbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.booking.TestBookings.ADULT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.CHILD_BORN;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.booking.BookingApi.ChangeRef;
import vn.edu.uit.flightbooking.booking.domain.BookingService;
import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.flight.FlightApi;
import vn.edu.uit.flightbooking.flight.FlightApi.JourneyChoice;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedSegment;

/** Các hàm BookingApi cho đổi chuyến (Plan 15) và yêu cầu đổi trong chi tiết booking. */
@IntegrationTest
@Transactional
class BookingApiChangeTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	BookingApi api;

	@Autowired
	BookingService service;

	@Autowired
	FlightApi flights;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	String code;

	long bookingId;

	long q1310;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		code = TestBookings.holdCode(mvc, customer.session(),
				TestBookings.body(trip, Map.of("journeys", trip.roundTrip())));
		bookingId = TestBookings.bookingId(jdbc, code);
		service.issue(bookingId);
		q1310 = TestBookings.returnFlight(mvc, jdbc, trip, "Q1310", DAY.plusDays(4), "06:00", 2_500_000);
	}

	/** BR-71, BR-72, BR-04: tuyến, tổng giá vé, số khách và giờ của chiều còn lại. */
	@Test
	void changeRefCarriesRouteFareTotalAndOtherJourneyTimes() {
		ChangeRef back = api.change(customer.id(), code.toLowerCase(), "return").orElseThrow();

		assertThat(back.flightIds()).containsExactly(trip.back());
		assertThat(back.origin()).isEqualTo("QHE");
		assertThat(back.destination()).isEqualTo("QHA");
		assertThat(back.fareFamilyId()).isEqualTo(trip.catalog().economyFare());
		assertThat(back.cabinClass()).isEqualTo(CabinClass.ECONOMY);
		assertThat(List.of(back.adults(), back.children(), back.infants())).containsExactly(1, 0, 0);
		assertThat(back.fareTotal()).isEqualTo(2_160_000);
		assertThat(back.departAfter()).isEqualTo(arrival(trip.secondLeg()));
		assertThat(back.arriveBefore()).isNull();
		assertThat(back.journey().changeable()).isTrue();
		assertThat(back.journey().changeFee()).isZero();

		ChangeRef out = api.change(customer.id(), code, "OUTBOUND").orElseThrow();

		assertThat(out.flightIds()).containsExactly(trip.firstLeg(), trip.secondLeg());
		assertThat(out.origin() + "→" + out.destination()).isEqualTo("QHA→QHE");
		assertThat(out.fareTotal()).isEqualTo(3_020_000);
		assertThat(out.departAfter()).isNull();
		assertThat(out.arriveBefore()).isEqualTo(departure(trip.back()));
		assertThat(api.lockChange(customer.id(), code, "RETURN")).contains(back);
		assertThat(api.change(TestUsers.create(jdbc, Role.CUSTOMER, TestUsers.randomEmail()), code, "RETURN")).isEmpty();
	}

	/** Chiều đã hoàn không còn chặng ACTIVE: vẫn đọc được (để bên gọi báo INVALID_STATE), không ném lỗi. */
	@Test
	void changeRefOfRefundedJourneyHasNoLegs() {
		api.refundJourney(journeyId(bookingId, "RETURN"));

		ChangeRef back = api.change(customer.id(), code, "RETURN").orElseThrow();

		assertThat(back.journey().journeyActive()).isFalse();
		assertThat(back.flightIds()).isEmpty();
		assertThat(back.origin()).isNull();
		assertThat(back.destination()).isNull();
		assertThat(back.fareTotal()).isZero();
	}

	/** BR-73, BR-21: chặng mới PENDING_CHANGE gắn reschedule_id; vé theo tỉ lệ truyền vào, chưa có số; giữ ghế chuyến mới. */
	@Test
	void holdChangeAddsPendingSegmentWithTicketsAndHoldsSeats() throws Exception {
		String two = TestBookings.holdCode(mvc, customer.session(), TestBookings.body(trip, Map.of("journeys",
				trip.roundTrip(), "passengers", List.of(TestBookings.passenger("ADULT", ADULT_BORN),
						TestBookings.passenger("CHILD", CHILD_BORN)))));
		long twoId = TestBookings.bookingId(jdbc, two);
		long journeyId = journeyId(twoId, "RETURN");
		long rid = reschedule(journeyId);

		api.holdChange(journeyId, rid, priced(2, q1310), 90, 10);

		assertThat(segments(twoId, "RETURN")).containsExactly("ACTIVE:Q1300", "PENDING_CHANGE:Q1310");
		assertThat(jdbc.sql("""
				SELECT t.fare_amount FROM tickets t JOIN booking_segments s ON s.id = t.segment_id
				WHERE s.reschedule_id = ? AND t.ticket_number IS NULL ORDER BY t.passenger_id
				""").param(rid).query(Long.class).list()).containsExactly(2_500_000L, 2_250_000L);
		assertThat(TestBookings.available(jdbc, q1310)).isEqualTo(98);
		assertThat(api.journey(customer.id(), two, "RETURN").orElseThrow().value()).isEqualTo(2_160_000 + 1_944_000);
	}

	/** BR-74, Q-08: chặng cũ REPLACED và trả ghế; chặng mới ACTIVE có số vé; giá trị chiều theo vé mới. */
	@Test
	void completeChangeReplacesOldSegmentAndIssuesNewTicket() {
		long journeyId = journeyId(bookingId, "RETURN");
		String oldNumber = ticketNumber(trip.back());
		long rid = reschedule(journeyId);
		api.holdChange(journeyId, rid, priced(1, q1310), 90, 10);

		api.completeChange(journeyId, rid);

		assertThat(segments(bookingId, "RETURN")).containsExactly("REPLACED:Q1300", "ACTIVE:Q1310");
		assertThat(TestBookings.available(jdbc, trip.back())).isEqualTo(100);
		assertThat(TestBookings.available(jdbc, q1310)).isEqualTo(99);
		assertThat(ticketNumber(q1310)).matches("^901\\d{10}$");
		assertThat(ticketNumber(trip.back())).isEqualTo(oldNumber);
		assertThat(api.journey(customer.id(), code, "RETURN").orElseThrow().value()).isEqualTo(2_500_000);
		assertThat(api.issued(bookingId).tickets()).extracting(BookingApi.IssuedTicket::flightNumber)
			.contains("Q1310")
			.doesNotContain("Q1300");
	}

	/** BR-75: chặng mới CANCELLED và trả ghế; chặng cũ giữ nguyên. */
	@Test
	void cancelChangeReleasesOnlyNewSeats() {
		long journeyId = journeyId(bookingId, "RETURN");
		long rid = reschedule(journeyId);
		api.holdChange(journeyId, rid, priced(1, q1310), 90, 10);

		api.cancelChange(rid);

		assertThat(segments(bookingId, "RETURN")).containsExactly("ACTIVE:Q1300", "CANCELLED:Q1310");
		assertThat(TestBookings.available(jdbc, q1310)).isEqualTo(100);
		assertThat(TestBookings.available(jdbc, trip.back())).isEqualTo(99);
	}

	/** Review Focus 5, FR-41: chặng hiện hành và danh sách yêu cầu đổi. */
	@Test
	void detailShowsOnlyCurrentSegmentsAndListsReschedules() throws Exception {
		long journeyId = journeyId(bookingId, "RETURN");
		long rid1 = reschedule(journeyId);
		api.holdChange(journeyId, rid1, priced(1, q1310), 90, 10);

		mvc.perform(get("/api/bookings/{code}", code).session(customer.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.journeys[1].segments[*].flightNumber", contains("Q1300")))
			.andExpect(jsonPath("$.reschedules[0].direction").value("RETURN"))
			.andExpect(jsonPath("$.reschedules[0].status").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.reschedules[0].amountDue").value(340_000))
			.andExpect(jsonPath("$.reschedules[0].flightNumbers", contains("Q1310")))
			.andExpect(jsonPath("$.reschedules[0].holdExpiresAt").value(endsWith("+07:00")));

		api.cancelChange(rid1);
		jdbc.sql("UPDATE reschedules SET status = 'EXPIRED' WHERE id = ?").param(rid1).update();

		mvc.perform(get("/api/bookings/{code}", code).session(customer.session()))
			.andExpect(jsonPath("$.journeys[1].segments[*].flightNumber", contains("Q1300")));

		long rid2 = reschedule(journeyId);
		api.holdChange(journeyId, rid2, priced(1, q1310), 90, 10);
		api.completeChange(journeyId, rid2);
		jdbc.sql("UPDATE reschedules SET status = 'COMPLETED', completed_at = now() WHERE id = ?")
			.param(rid2)
			.update();

		mvc.perform(get("/api/bookings/{code}", code).session(customer.session()))
			.andExpect(jsonPath("$.journeys[1].segments[*].flightNumber", contains("Q1310")))
			.andExpect(jsonPath("$.passengers[0].tickets[*].flightNumber", hasItem("Q1310")))
			.andExpect(jsonPath("$.passengers[0].tickets[*].flightNumber", not(hasItem("Q1300"))))
			.andExpect(jsonPath("$.reschedules.length()").value(2));

		api.refundJourney(journeyId);

		mvc.perform(get("/api/bookings/{code}", code).session(customer.session()))
			.andExpect(jsonPath("$.journeys[1].segments.length()").value(1))
			.andExpect(jsonPath("$.journeys[1].segments[0].flightNumber").value("Q1310"))
			.andExpect(jsonPath("$.journeys[1].segments[0].status").value("CANCELLED"));
	}

	/** Chèn yêu cầu đổi PENDING_PAYMENT cho chiều, trả id. */
	private long reschedule(long journeyId) {
		return jdbc.sql("""
				INSERT INTO reschedules (booking_id, journey_id, status, old_fare_total, new_fare_total,
				                         change_fee_total, amount_due, hold_expires_at)
				SELECT booking_id, id, 'PENDING_PAYMENT', 2160000, 2500000, 0, 340000, now() + interval '15 minutes'
				FROM booking_journeys WHERE id = ?
				RETURNING id
				""").param(journeyId).query(Long.class).single();
	}

	private List<PricedSegment> priced(int seats, long... flightIds) {
		return flights
			.price(List.of(new JourneyChoice(LongStream.of(flightIds).boxed().toList(), trip.catalog().economyFare())),
					seats)
			.getFirst()
			.segments();
	}

	/** Mỗi chặng của chiều dạng {@code status:flight_number}, theo id. */
	private List<String> segments(long bookingId, String direction) {
		return jdbc.sql("""
				SELECT s.status || ':' || f.flight_number
				FROM booking_segments s
				JOIN booking_journeys j ON j.id = s.journey_id
				JOIN flights f ON f.id = s.flight_id
				WHERE j.booking_id = ? AND j.direction = ?
				ORDER BY s.id
				""").params(bookingId, direction).query(String.class).list();
	}

	private String ticketNumber(long flightId) {
		return jdbc.sql("""
				SELECT t.ticket_number FROM tickets t JOIN booking_segments s ON s.id = t.segment_id
				WHERE s.flight_id = ? AND s.journey_id = ?
				""").params(flightId, journeyId(bookingId, "RETURN")).query(String.class).single();
	}

	private Instant departure(long flightId) {
		return time("departure_time", flightId);
	}

	private Instant arrival(long flightId) {
		return time("arrival_time", flightId);
	}

	private Instant time(String column, long flightId) {
		return jdbc.sql("SELECT " + column + " FROM flights WHERE id = ?")
			.param(flightId)
			.query(OffsetDateTime.class)
			.single()
			.toInstant();
	}

	private long journeyId(long bookingId, String direction) {
		return jdbc.sql("SELECT id FROM booking_journeys WHERE booking_id = ? AND direction = ?")
			.params(bookingId, direction)
			.query(Long.class)
			.single();
	}

}
