package vn.edu.uit.flightbooking.booking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.booking.TestBookings.ADULT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.CHILD_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.INFANT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.passenger;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import vn.edu.uit.flightbooking.booking.BookingIssued;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.promotion.TestVouchers;

/** FR-24, BR-24, BR-40, BR-43, BR-46, T2 qua POST /api/bookings. */
@IntegrationTest
@Transactional
@RecordApplicationEvents
class BookingHoldTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
	}

	ResultActions hold(Map<String, Object> changes) throws Exception {
		return TestBookings.hold(mvc, customer.session(), TestBookings.body(trip, changes));
	}

	int available(long flightId) {
		return TestBookings.available(jdbc, flightId);
	}

	long bookingCount() {
		return jdbc.sql("SELECT count(*) FROM bookings WHERE user_id = ?").param(customer.id()).query(Long.class).single();
	}

	/** Ví dụ giữ chỗ TDD §8.8: phản hồi đúng mẫu, mọi bảng được ghi, giá được chốt (BR-24), ghế và lượt bị trừ. */
	@Test
	void holdsTddExampleAndSnapshotsPrices() throws Exception {
		long bag20 = TestBookings.baggageOption(jdbc, "Q1", 20, 350_000);
		long welcome = TestVouchers.insert(jdbc, "QVWELCOME", Map.of("max_discount", 200_000));
		Instant before = Instant.now();

		String json = hold(Map.of("journeys", trip.roundTrip(), "passengers",
				List.of(passenger("ADULT", ADULT_BORN), passenger("INFANT", INFANT_BORN)), "baggage",
				List.of(TestBookings.bag(0, "OUTBOUND", bag20)), "voucherCode", "QVWELCOME"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.journeys[*].direction", contains("OUTBOUND", "RETURN")))
			.andExpect(jsonPath("$.journeys[*].fareTotal", contains(3_322_000, 2_376_000)))
			.andExpect(jsonPath("$.journeys[*].baggageTotal", contains(350_000, 0)))
			.andExpect(jsonPath("$.journeys[*].discount", contains(121_428, 78_572)))
			.andExpect(jsonPath("$.fareTotal").value(5_698_000))
			.andExpect(jsonPath("$.discountTotal").value(200_000))
			.andExpect(jsonPath("$.totalAmount").value(5_848_000))
			.andReturn()
			.getResponse()
			.getContentAsString();

		String code = JsonPath.read(json, "$.code");
		assertThat(code).matches("[A-HJ-NP-Z2-9]{6}");
		OffsetDateTime holdExpiresAt = OffsetDateTime.parse(JsonPath.read(json, "$.holdExpiresAt"));
		assertThat(holdExpiresAt.getOffset()).isEqualTo(ZoneOffset.ofHours(7));
		assertThat(holdExpiresAt.toInstant()).isBetween(before.plusSeconds(15 * 60 - 1),
				Instant.now().plusSeconds(15 * 60 + 1));

		long id = TestBookings.bookingId(jdbc, code);
		assertThat(jdbc.sql("""
				SELECT status, user_id, voucher_id, fare_total, baggage_total, discount_total, total_amount, contact_email
				FROM bookings WHERE id = ?
				""").param(id).query().singleRow())
			.containsEntry("status", "PENDING_PAYMENT")
			.containsEntry("user_id", customer.id())
			.containsEntry("voucher_id", welcome)
			.containsEntry("fare_total", 5_698_000L)
			.containsEntry("baggage_total", 350_000L)
			.containsEntry("discount_total", 200_000L)
			.containsEntry("total_amount", 5_848_000L)
			.containsEntry("contact_email", "an@example.com");
		assertThat(jdbc.sql("""
				SELECT direction || ':' || fare_family_name || ':' || cabin_class || ':' || discount_amount
				FROM booking_journeys WHERE booking_id = ? ORDER BY direction
				""").param(id).query(String.class).list())
			.containsExactly("OUTBOUND:Eco:ECONOMY:121428", "RETURN:Eco:ECONOMY:78572");
		assertThat(jdbc.sql("""
				SELECT s.adult_price FROM booking_segments s JOIN booking_journeys j ON j.id = s.journey_id
				WHERE j.booking_id = ? AND s.status = 'ACTIVE' ORDER BY j.direction, s.seq
				""").param(id).query(Long.class).list()).containsExactly(1_870_000L, 1_150_000L, 2_160_000L);
		assertThat(ticketFares(id, "ADULT")).containsExactly(1_870_000L, 1_150_000L, 2_160_000L);
		assertThat(ticketFares(id, "INFANT")).containsExactly(187_000L, 115_000L, 216_000L);
		assertThat(jdbc.sql("""
				SELECT bb.weight_kg || ':' || bb.price || ':' || p.type FROM booking_baggage bb
				JOIN passengers p ON p.id = bb.passenger_id WHERE p.booking_id = ?
				""").param(id).query(String.class).list()).containsExactly("20:350000:ADULT");

		// Em bé không chiếm ghế: mỗi chặng trừ đúng 1 ghế.
		assertThat(available(trip.firstLeg())).isEqualTo(99);
		assertThat(available(trip.secondLeg())).isEqualTo(99);
		assertThat(available(trip.back())).isEqualTo(99);
		assertThat(TestVouchers.usedCount(jdbc, welcome)).isEqualTo(1);
		assertThat(events.stream(BookingIssued.class)).isEmpty();
	}

	List<Long> ticketFares(long bookingId, String type) {
		return jdbc.sql("""
				SELECT t.fare_amount FROM tickets t
				JOIN passengers p ON p.id = t.passenger_id
				JOIN booking_segments s ON s.id = t.segment_id
				WHERE p.booking_id = ? AND p.type = ?
				ORDER BY s.id
				""").params(bookingId, type).query(Long.class).list();
	}

	/** T2: khứ hồi nối chuyến, chặng cuối hết ghế: không chặng nào bị trừ ghế, không giữ lượt, không có booking. */
	@Test
	void failedHoldKeepsEverySeatAndVoucherUse() throws Exception {
		long voucher = TestVouchers.insert(jdbc, "QVKEEP", Map.of());
		jdbc.sql("UPDATE flight_cabins SET available_seats = 0 WHERE flight_id = ? AND cabin_class = 'ECONOMY'")
			.param(trip.back())
			.update();

		hold(Map.of("journeys", trip.roundTrip(), "voucherCode", "QVKEEP")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SEATS_UNAVAILABLE"));

		assertThat(available(trip.firstLeg())).isEqualTo(100);
		assertThat(available(trip.secondLeg())).isEqualTo(100);
		assertThat(TestVouchers.usedCount(jdbc, voucher)).isZero();
		assertThat(bookingCount()).isZero();
	}

	/** Giữ chỗ kiểm tra giống hệt báo giá (FR-20); dữ liệu sai thì không ghi gì. */
	@Test
	void rejectedHoldWritesNothing() throws Exception {
		hold(Map.of("passengers", List.of(passenger("CHILD", CHILD_BORN)))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PASSENGER_RULES_VIOLATED"));

		assertThat(bookingCount()).isZero();
		assertThat(available(trip.firstLeg())).isEqualTo(100);
	}

	/** BR-40, BR-43: tổng thanh toán 0 thì xuất vé ngay, số vé 13 chữ số bắt đầu bằng mã hãng, phát BookingIssued. */
	@Test
	void zeroTotalIsIssuedAtOnce() throws Exception {
		TestVouchers.insert(jdbc, "QVFREE", Map.of("discount_value", 100));

		String json = hold(Map.of("voucherCode", "QVFREE")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("ISSUED"))
			.andExpect(jsonPath("$.totalAmount").value(0))
			.andReturn()
			.getResponse()
			.getContentAsString();

		long id = TestBookings.bookingId(jdbc, JsonPath.read(json, "$.code"));
		assertThat(jdbc.sql("""
				SELECT t.ticket_number FROM tickets t JOIN passengers p ON p.id = t.passenger_id
				WHERE p.booking_id = ? AND t.issued_at IS NOT NULL
				""").param(id).query(String.class).list())
			.hasSize(2)
			.doesNotHaveDuplicates()
			.allMatch(number -> number.matches("901[0-9]{10}"));
		assertThat(jdbc.sql("SELECT status = 'ISSUED' AND issued_at IS NOT NULL FROM bookings WHERE id = ?")
			.param(id)
			.query(Boolean.class)
			.single()).isTrue();
		assertThat(events.stream(BookingIssued.class).map(BookingIssued::bookingId)).containsExactly(id);
		assertThat(available(trip.firstLeg())).isEqualTo(99);
	}

}
