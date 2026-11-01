package vn.edu.uit.flightbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.booking.TestBookings.ADULT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.CHILD_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.INFANT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.passenger;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.booking.BookingApi.JourneyRef;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.promotion.TestVouchers;

/** Các hàm BookingApi cho hậu mãi (Plan 14) và yêu cầu hoàn trong chi tiết booking. */
@IntegrationTest
@Transactional
class BookingApiAftersalesTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	BookingApi bookings;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
	}

	/**
	 * BR-52, BR-53: một chiều, người lớn + trẻ em + em bé = 3.020.000 + 2.718.000 + 302.000; hành lý người lớn
	 * 200.000; voucher giảm 100.000. Giá trị chiều 6.140.000, 2 khách chiếm ghế.
	 */
	@Test
	void journeyCarriesValueAndRulesForAftersales() throws Exception {
		long bag = TestBookings.baggageOption(jdbc, "Q1", 20, 200_000);
		TestVouchers.insert(jdbc, "QREFUND1", Map.of("discount_type", "FIXED", "discount_value", 100_000));
		jdbc.sql("UPDATE fare_families SET refundable = TRUE, refund_fee = 300000 WHERE id = ?")
			.param(trip.catalog().economyFare())
			.update();
		String code = TestBookings.holdCode(mvc, customer.session(), TestBookings.body(trip, Map.of("passengers",
				List.of(passenger("ADULT", ADULT_BORN), passenger("CHILD", CHILD_BORN),
						passenger("INFANT", INFANT_BORN)),
				"baggage", List.of(TestBookings.bag(0, "OUTBOUND", bag)), "voucherCode", "QREFUND1")));

		JourneyRef ref = bookings.journey(customer.id(), code.toLowerCase(), "outbound").orElseThrow();

		assertThat(ref.bookingId()).isEqualTo(TestBookings.bookingId(jdbc, code));
		assertThat(ref.bookingIssued()).isFalse();
		assertThat(ref.journeyActive()).isTrue();
		assertThat(ref.refundable()).isTrue();
		assertThat(ref.refundFee()).isEqualTo(300_000);
		assertThat(ref.seated()).isEqualTo(2);
		assertThat(ref.value()).isEqualTo(6_140_000);
		assertThat(ref.firstDeparture()).isEqualTo(jdbc.sql("SELECT departure_time FROM flights WHERE id = ?")
			.param(trip.firstLeg())
			.query(OffsetDateTime.class)
			.single()
			.toInstant());
		assertThat(bookings.lockJourney(customer.id(), code, "OUTBOUND")).contains(ref);
	}

	/** BR-104: booking người khác, chiều không có trong booking, chiều sai tên đều rỗng. */
	@Test
	void journeyIsEmptyForOtherUsersAndMissingDirections() throws Exception {
		String code = TestBookings.holdCode(mvc, customer.session(), TestBookings.body(trip, Map.of()));
		long other = TestUsers.create(jdbc, Role.CUSTOMER, TestUsers.randomEmail());

		assertThat(bookings.journey(other, code, "OUTBOUND")).isEmpty();
		assertThat(bookings.journey(customer.id(), code, "RETURN")).isEmpty();
		assertThat(bookings.journey(customer.id(), code, "SIDEWAYS")).isEmpty();
		assertThat(bookings.journey(customer.id(), "ZZZZZZ", "OUTBOUND")).isEmpty();
	}

	/** BR-62, BR-65: hoàn chiều đi giữ booking ISSUED; hoàn nốt chiều về thì booking REFUNDED. Ghế trả đúng một lần. */
	@Test
	void refundJourneyReleasesSeatsAndRefundsBookingAfterLastJourney() throws Exception {
		String code = TestBookings.holdCode(mvc, customer.session(),
				TestBookings.body(trip, Map.of("journeys", trip.roundTrip())));
		long bookingId = TestBookings.bookingId(jdbc, code);
		jdbc.sql("UPDATE bookings SET status = 'ISSUED', issued_at = now() WHERE id = ?").param(bookingId).update();

		bookings.refundJourney(journeyId(bookingId, "OUTBOUND"));

		assertThat(TestBookings.available(jdbc, trip.firstLeg())).isEqualTo(100);
		assertThat(TestBookings.available(jdbc, trip.secondLeg())).isEqualTo(100);
		assertThat(TestBookings.available(jdbc, trip.back())).isEqualTo(99);
		assertThat(statuses(bookingId)).containsExactly("ISSUED", "OUTBOUND:REFUNDED:CANCELLED:CANCELLED",
				"RETURN:ACTIVE:ACTIVE");

		bookings.refundJourney(journeyId(bookingId, "RETURN"));

		assertThat(TestBookings.available(jdbc, trip.back())).isEqualTo(100);
		assertThat(statuses(bookingId)).containsExactly("REFUNDED", "OUTBOUND:REFUNDED:CANCELLED:CANCELLED",
				"RETURN:REFUNDED:CANCELLED");
	}

	/** FR-41, FR-52: chi tiết booking liệt kê yêu cầu hoàn, kể cả yêu cầu INVALID_PAYMENT (không có chiều). */
	@Test
	void detailListsRefundRequests() throws Exception {
		String code = TestBookings.holdCode(mvc, customer.session(), TestBookings.body(trip, Map.of()));
		long bookingId = TestBookings.bookingId(jdbc, code);
		jdbc.sql("""
				INSERT INTO refund_requests (booking_id, journey_id, source, status, base_amount, fee_amount,
				                             refund_amount, reason, created_at)
				VALUES (:bookingId, :journeyId, 'CUSTOMER', 'PENDING', 3020000, 300000, 2720000, 'Đổi kế hoạch',
				        now() - interval '1 minute')
				""").param("bookingId", bookingId).param("journeyId", journeyId(bookingId, "OUTBOUND")).update();
		long paymentId = jdbc.sql("""
				INSERT INTO payments (booking_id, kind, provider, status, amount, txn_ref, completed_at)
				VALUES (:bookingId, 'CHARGE', 'VNPAY', 'SUCCESS', 3020000, 'aftersalesdetailtxn0001', now())
				RETURNING id
				""").param("bookingId", bookingId).query(Long.class).single();
		jdbc.sql("""
				INSERT INTO refund_requests (booking_id, payment_id, source, status, base_amount, fee_amount,
				                             refund_amount, completed_at)
				VALUES (:bookingId, :paymentId, 'INVALID_PAYMENT', 'COMPLETED', 3020000, 0, 3020000, now())
				""").param("bookingId", bookingId).param("paymentId", paymentId).update();

		mvc.perform(get("/api/bookings/{code}", code).session(customer.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.refundRequests.length()").value(2))
			.andExpect(jsonPath("$.refundRequests[0].direction").value("OUTBOUND"))
			.andExpect(jsonPath("$.refundRequests[0].status").value("PENDING"))
			.andExpect(jsonPath("$.refundRequests[0].refundAmount").value(2_720_000))
			.andExpect(jsonPath("$.refundRequests[0].reason").value("Đổi kế hoạch"))
			.andExpect(jsonPath("$.refundRequests[1].direction").value(nullValue()))
			.andExpect(jsonPath("$.refundRequests[1].source").value("INVALID_PAYMENT"))
			.andExpect(jsonPath("$.refundRequests[1].completedAt").value(endsWith("+07:00")));
	}

	private long journeyId(long bookingId, String direction) {
		return jdbc.sql("SELECT id FROM booking_journeys WHERE booking_id = ? AND direction = ?")
			.params(bookingId, direction)
			.query(Long.class)
			.single();
	}

	/** Trạng thái booking, rồi mỗi chiều: {@code DIRECTION:status_chiều:status_chặng...} (chặng theo seq). */
	private List<String> statuses(long bookingId) {
		List<String> result = new ArrayList<>();
		result.add(jdbc.sql("SELECT status FROM bookings WHERE id = ?").param(bookingId).query(String.class).single());
		var journeys = jdbc
			.sql("SELECT id, direction, status FROM booking_journeys WHERE booking_id = ? ORDER BY direction")
			.param(bookingId)
			.query((rs, n) -> Map.entry(rs.getLong("id"), rs.getString("direction") + ":" + rs.getString("status")))
			.list();
		for (var j : journeys) {
			String segments = String.join(":",
					jdbc.sql("SELECT status FROM booking_segments WHERE journey_id = ? ORDER BY seq")
						.param(j.getKey())
						.query(String.class)
						.list());
			result.add(j.getValue() + ":" + segments);
		}
		return result;
	}

}
