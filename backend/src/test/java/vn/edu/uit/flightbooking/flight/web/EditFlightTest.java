package vn.edu.uit.flightbooking.flight.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;
import static vn.edu.uit.flightbooking.flight.TestFlights.createOne;
import static vn.edu.uit.flightbooking.flight.TestFlights.newFlight;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;
import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.flight.FlightTimeChanged;
import vn.edu.uit.flightbooking.flight.SeatInventory;
import vn.edu.uit.flightbooking.flight.TestFlights;

/**
 * FR-92, FR-94; BR-82, BR-83 (phát sự kiện), BR-86, BR-87. Ghế "đã bán hoặc đang giữ" được tạo bằng
 * {@link SeatInventory#hold}, chạy chung transaction với test.
 */
@IntegrationTest
@Transactional
@RecordApplicationEvents
class EditFlightTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	SeatInventory seats;

	@Autowired
	ApplicationEvents events;

	MockHttpSession admin;

	TestFlights.Catalog catalog;

	/** Q1100 QHA→QHC (Singapore): 06:00 +07:00 ngày DAY, bay 130 phút; ECONOMY 100, BUSINESS 10 ghế. */
	long flightId;

	@BeforeEach
	void setUp() throws Exception {
		admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();
		catalog = TestFlights.catalog(jdbc);
		flightId = createOne(mvc, admin, newFlight(catalog, Map.of("arrivalAirport", "QHC")));
	}

	/** Body của PUT: giữ nguyên giờ, máy bay, ghế và giá của chuyến trong setUp; {@code changes} ghi đè từng trường. */
	String changes(Map<String, Object> changes) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("departureTime", DAY + "T06:00");
		body.put("arrivalTime", DAY + "T09:10");
		body.put("aircraftModel", "Airbus A321");
		body.put("cabins", List.of(Map.of("cabinClass", "ECONOMY", "totalSeats", 100),
				Map.of("cabinClass", "BUSINESS", "totalSeats", 10)));
		body.put("fares", List.of(Map.of("fareFamilyId", catalog.economyFare(), "price", 1_000_000),
				Map.of("fareFamilyId", catalog.businessFare(), "price", 3_000_000)));
		body.putAll(changes);
		return JsonMapper.shared().writeValueAsString(body);
	}

	ResultActions update(long id, String body) throws Exception {
		return mvc.perform(put("/api/admin/flights/{id}", id).with(csrf(mvc))
			.session(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	ResultActions remove(long id) throws Exception {
		return mvc.perform(delete("/api/admin/flights/{id}", id).with(csrf(mvc)).session(admin));
	}

	/** BR-81: giờ hạ cánh nhập theo giờ sân bay đến. BR-83: chỉ phát sự kiện khi giờ thật sự đổi. */
	@Test
	void changesTimesInLocalZonesAndPublishesEventOnlyWhenTimeChanges() throws Exception {
		update(flightId, changes(Map.of("aircraftModel", "Airbus A320neo"))).andExpect(status().isOk());
		assertThat(events.stream(FlightTimeChanged.class)).isEmpty();

		update(flightId, changes(Map.of("departureTime", DAY + "T10:00", "arrivalTime", DAY + "T13:30")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.flight.departureTime").value(DAY + "T10:00:00+07:00"))
			.andExpect(jsonPath("$.flight.arrivalTime").value(DAY + "T13:30:00+08:00"))
			.andExpect(jsonPath("$.flight.aircraftModel").value("Airbus A321"));
		assertThat(events.stream(FlightTimeChanged.class)).containsExactly(new FlightTimeChanged(flightId));
	}

	/** BR-82, Q-03: tổng ghế không được thấp hơn số ghế đã bán hoặc đang giữ. */
	@Test
	void seatCountCannotDropBelowHeldSeats() throws Exception {
		seats.hold(flightId, CabinClass.ECONOMY, 10);

		update(flightId, changes(Map.of("cabins", List.of(Map.of("cabinClass", "ECONOMY", "totalSeats", 10),
				Map.of("cabinClass", "BUSINESS", "totalSeats", 10)))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.flight.cabins[0].totalSeats").value(10))
			.andExpect(jsonPath("$.flight.cabins[0].availableSeats").value(0));
		update(flightId, changes(Map.of("cabins", List.of(Map.of("cabinClass", "ECONOMY", "totalSeats", 9),
				Map.of("cabinClass", "BUSINESS", "totalSeats", 10)))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SEAT_COUNT_BELOW_SOLD"));
		update(flightId, changes(Map.of()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.flight.cabins[0].totalSeats").value(100))
			.andExpect(jsonPath("$.flight.cabins[0].availableSeats").value(90));
	}

	/** BR-82: bỏ được hạng ghế chưa có ghế bán ra; BR-86: danh sách giá bán được thay toàn bộ. */
	@Test
	void removesEmptyCabinAddsNewOneAndReplacesFares() throws Exception {
		long firstFare = jdbc.sql("""
				INSERT INTO fare_families (airline_code, cabin_class, name, carry_on_kg, checked_baggage_kg,
				                           refundable, changeable)
				VALUES ('Q1', 'FIRST', 'First', 14, 40, TRUE, TRUE)
				RETURNING id
				""").query(Long.class).single();

		update(flightId, changes(Map.of(
				"cabins", List.of(Map.of("cabinClass", "FIRST", "totalSeats", 8),
						Map.of("cabinClass", "ECONOMY", "totalSeats", 100)),
				"fares", List.of(Map.of("fareFamilyId", catalog.economyFare(), "price", 1_200_000),
						Map.of("fareFamilyId", firstFare, "price", 9_000_000)))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.flight.cabins[*].cabinClass", contains("ECONOMY", "FIRST")))
			.andExpect(jsonPath("$.flight.cabins[1].availableSeats").value(8))
			.andExpect(jsonPath("$.fares[*].price", contains(1_200_000, 9_000_000)));
	}

	@Test
	void cabinWithHeldSeatsCannotBeRemoved() throws Exception {
		seats.hold(flightId, CabinClass.BUSINESS, 1);

		update(flightId, changes(Map.of("cabins", List.of(Map.of("cabinClass", "ECONOMY", "totalSeats", 100)),
				"fares", List.of(Map.of("fareFamilyId", catalog.economyFare(), "price", 1_000_000)))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SEAT_COUNT_BELOW_SOLD"));
	}

	@Test
	void rejectsInvalidTimesAndFares() throws Exception {
		update(flightId, changes(Map.of("arrivalTime", DAY + "T06:30")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("arrivalTime"));
		update(flightId, changes(Map.of("departureTime", DAY.minusDays(31) + "T06:00")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("departureTime"));
		update(flightId, changes(Map.of("cabins", List.of(Map.of("cabinClass", "ECONOMY", "totalSeats", 100)))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("fares[1].fareFamilyId"));
	}

	/** BR-81: giờ trong PUT là giờ địa phương. Chuỗi có "Z" hay offset bị từ chối, không bị bỏ offset âm thầm làm lệch giờ. */
	@Test
	void timesWithOffsetAreRejectedInsteadOfShifted() throws Exception {
		update(flightId, changes(Map.of("departureTime", DAY + "T10:00:00.000Z", "arrivalTime", DAY + "T13:30"))).andExpect(status().isBadRequest());
		update(flightId, changes(Map.of("arrivalTime", DAY + "T13:30:00+08:00"))).andExpect(status().isBadRequest());

		mvc.perform(get("/api/admin/flights/{id}", flightId).session(admin))
			.andExpect(jsonPath("$.flight.departureTime").value(DAY + "T06:00:00+07:00"));
		assertThat(events.stream(FlightTimeChanged.class)).isEmpty();
	}

	/** BR-87: chỉ sửa được chuyến chưa cất cánh; chuyến đã huỷ cũng không sửa được. */
	@Test
	void departedOrCancelledFlightCannotBeEdited() throws Exception {
		long departed = jdbc.sql("""
				INSERT INTO flights (airline_code, flight_number, departure_airport, arrival_airport,
				                     departure_time, arrival_time)
				VALUES ('Q1', 'Q1999', 'QHA', 'QHB', now() - interval '1 hour', now() + interval '1 hour')
				RETURNING id
				""").query(Long.class).single();
		update(departed, changes(Map.of()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DEADLINE_PASSED"));

		jdbc.sql("UPDATE flights SET status = 'CANCELLED' WHERE id = ?").param(flightId).update();
		update(flightId, changes(Map.of()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
		update(Long.MAX_VALUE, changes(Map.of())).andExpect(status().isNotFound());
	}

	/** BR-80: đổi giờ trùng một chuyến khác cùng số hiệu thì báo lỗi trường, không phải 500. Lỗi UNIQUE làm hỏng
	 * transaction của test nên request này phải là lệnh SQL cuối cùng. */
	@Test
	void newTimeClashingWithSameFlightNumberIsFieldError() throws Exception {
		long nextDay = createOne(mvc, admin, newFlight(catalog,
				Map.of("arrivalAirport", "QHC", "fromDate", DAY.plusDays(1).toString(), "toDate",
						DAY.plusDays(1).toString())));

		update(nextDay, changes(Map.of()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("departureTime"));
	}

	@Test
	void deletesFlightWithoutBookings() throws Exception {
		remove(flightId).andExpect(status().isNoContent());

		mvc.perform(get("/api/admin/flights/{id}", flightId).session(admin)).andExpect(status().isNotFound());
		remove(flightId).andExpect(status().isNotFound());
	}

	/** BR-82: chuyến đã từng có booking (kể cả booking đã hết hạn) không xoá được. Lỗi khoá ngoại làm hỏng transaction
	 * của test nên request xoá phải là lệnh SQL cuối cùng. */
	@Test
	void flightWithBookingCannotBeDeleted() throws Exception {
		long userId = TestUsers.create(jdbc, Role.CUSTOMER, TestUsers.randomEmail());
		long bookingId = jdbc.sql("""
				INSERT INTO bookings (code, user_id, status, contact_name, contact_email, contact_phone,
				                      fare_total, baggage_total, discount_total, total_amount, hold_expires_at)
				VALUES ('QQQQQQ', ?, 'EXPIRED', 'KHACH TEST', 'khach@test.local', '0900000000',
				        1000000, 0, 0, 1000000, now())
				RETURNING id
				""").param(userId).query(Long.class).single();
		long journeyId = jdbc.sql("""
				INSERT INTO booking_journeys (booking_id, direction, airline_code, fare_family_id, fare_family_name,
				                              cabin_class, carry_on_kg, checked_baggage_kg, refundable, refund_fee,
				                              changeable, change_fee)
				VALUES (?, 'OUTBOUND', 'Q1', ?, 'Eco', 'ECONOMY', 7, 0, FALSE, 0, TRUE, 0)
				RETURNING id
				""").params(bookingId, catalog.economyFare()).query(Long.class).single();
		jdbc.sql("""
				INSERT INTO booking_segments (journey_id, flight_id, seq, status, adult_price)
				VALUES (?, ?, 1, 'CANCELLED', 1000000)
				""").params(journeyId, flightId).update();

		mvc.perform(get("/api/admin/flights/{id}", flightId).session(admin))
			.andExpect(jsonPath("$.bookingCount").value(1));
		remove(flightId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FLIGHT_HAS_BOOKINGS"));
	}

}
