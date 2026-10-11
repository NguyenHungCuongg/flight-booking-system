package vn.edu.uit.flightbooking.flight.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;
import static vn.edu.uit.flightbooking.flight.TestFlights.createOne;
import static vn.edu.uit.flightbooking.flight.TestFlights.newFlight;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
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
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.flight.TestFlights;

/** FR-11, BR-01–06, T13: tìm QHA→QHC ngày DAY, không đăng nhập. */
@IntegrationTest
@Transactional
class FlightSearchTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	MockHttpSession admin;

	TestFlights.Catalog catalog;

	long direct;

	long firstLeg;

	long secondLeg;

	/**
	 * Bay thẳng Q1300 QHA→QHC 07:00 (180 phút; bán Eco, Biz và gói ECONOMY "Cũ" đã ngừng bán) và Q1301 (đã huỷ).
	 * Chặng 1 Q1100 QHA→QHB 06:00–08:10. Chặng 2 QHB→QHC (120 phút) cất cánh lúc: Q1201 09:09 (nối 59 phút), Q1203
	 * 09:10 (60), Q1200 10:00 (110, ECONOMY 40 ghế), Q1204 12:00 (chỉ bán Biz), Q1205 13:00 (chỉ có ECONOMY 2 ghế),
	 * Q1206 20:10 (720), Q1202 20:11 (721), Q2100 11:00 của hãng Q2. Mọi giá Eco 1.000.000, Biz 3.000.000.
	 */
	@BeforeEach
	void setUp() throws Exception {
		admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();
		catalog = TestFlights.catalog(jdbc);
		long retired = jdbc.sql("""
				INSERT INTO fare_families (airline_code, cabin_class, name, carry_on_kg, checked_baggage_kg,
				                           refundable, changeable, active)
				VALUES ('Q1', 'ECONOMY', 'Cũ', 7, 0, FALSE, FALSE, FALSE)
				RETURNING id
				""").query(Long.class).single();

		direct = flight(Map.of("flightNumber", "Q1300", "arrivalAirport", "QHC", "departureTime", "07:00",
				"durationMinutes", 180, "fares",
				List.of(eco(), Map.of("fareFamilyId", retired, "price", 500_000), biz())));
		long cancelled = flight(Map.of("flightNumber", "Q1301", "arrivalAirport", "QHC", "departureTime", "08:00"));
		jdbc.sql("UPDATE flights SET status = 'CANCELLED' WHERE id = ?").param(cancelled).update();

		firstLeg = flight(Map.of());
		second("Q1201", "09:09", Map.of());
		secondLeg = second("Q1203", "09:10", Map.of());
		second("Q1200", "10:00", Map.of("cabins", List.of(cabin("ECONOMY", 40), cabin("BUSINESS", 10))));
		second("Q1204", "12:00", Map.of("fares", List.of(biz())));
		second("Q1205", "13:00", Map.of("cabins", List.of(cabin("ECONOMY", 2)), "fares", List.of(eco())));
		second("Q1206", "20:10", Map.of());
		second("Q1202", "20:11", Map.of());
		second("Q2100", "11:00", Map.of("airlineCode", "Q2", "cabins", List.of(cabin("ECONOMY", 50)), "fares",
				List.of(Map.of("fareFamilyId", catalog.otherAirlineFare(), "price", 1_000_000))));
	}

	long flight(Map<String, Object> changes) throws Exception {
		return createOne(mvc, admin, newFlight(catalog, changes));
	}

	long second(String flightNumber, String departureTime, Map<String, Object> changes) throws Exception {
		Map<String, Object> body = new HashMap<>(Map.of("flightNumber", flightNumber, "departureAirport", "QHB",
				"arrivalAirport", "QHC", "departureTime", departureTime, "durationMinutes", 120));
		body.putAll(changes);
		return flight(body);
	}

	Map<String, Object> eco() {
		return Map.of("fareFamilyId", catalog.economyFare(), "price", 1_000_000);
	}

	Map<String, Object> biz() {
		return Map.of("fareFamilyId", catalog.businessFare(), "price", 3_000_000);
	}

	static Map<String, Object> cabin(String cabinClass, int seats) {
		return Map.of("cabinClass", cabinClass, "totalSeats", seats);
	}

	ResultActions search(LocalDate date, String cabin, int adults, int children, int infants) throws Exception {
		return mvc.perform(get("/api/flights/search").param("from", "QHA")
			.param("to", "QHC")
			.param("date", date.toString())
			.param("adults", String.valueOf(adults))
			.param("children", String.valueOf(children))
			.param("infants", String.valueOf(infants))
			.param("cabin", cabin));
	}

	/** T13: cùng hãng, nối 60–720 phút (tính cả hai biên), bỏ chuyến hết ghế, không bán gói giá, đã huỷ. */
	@Test
	void connectsSameAirlineWithinLayoverWindowOnly() throws Exception {
		search(DAY, "ECONOMY", 2, 1, 1).andExpect(status().isOk())
			.andExpect(jsonPath("$.itineraries.length()").value(4))
			.andExpect(jsonPath("$.itineraries[*].stops", contains(0, 1, 1, 1)))
			.andExpect(jsonPath("$.itineraries[0].segments[*].flightNumber", contains("Q1300")))
			.andExpect(jsonPath("$.itineraries[1].segments[*].flightNumber", contains("Q1100", "Q1203")))
			.andExpect(jsonPath("$.itineraries[2].segments[*].flightNumber", contains("Q1100", "Q1200")))
			.andExpect(jsonPath("$.itineraries[3].segments[*].flightNumber", contains("Q1100", "Q1206")))
			.andExpect(jsonPath("$.itineraries[1].segments[0].layoverMinutesAfter").value(60))
			.andExpect(jsonPath("$.itineraries[3].segments[0].layoverMinutesAfter").value(720))
			.andExpect(jsonPath("$.itineraries[3].segments[1].layoverMinutesAfter").value(nullValue()));
	}

	/** Giờ theo múi giờ từng sân bay; chỉ gói đang bán của đúng hạng; giá theo BR-21; ghế còn nhỏ nhất qua các chặng. */
	@Test
	void showsLocalTimesActiveFaresAndPricesForRequestedPassengers() throws Exception {
		search(DAY, "ECONOMY", 2, 1, 1).andExpect(jsonPath("$.itineraries[0].totalDurationMinutes").value(180))
			.andExpect(jsonPath("$.itineraries[0].segments[0].flightId").value(direct))
			.andExpect(jsonPath("$.itineraries[0].segments[0].airline.name").value("Hãng Một"))
			.andExpect(jsonPath("$.itineraries[0].segments[0].aircraftModel").value("Airbus A321"))
			.andExpect(jsonPath("$.itineraries[0].segments[0].durationMinutes").value(180))
			.andExpect(jsonPath("$.itineraries[0].segments[0].departure.time").value(DAY + "T07:00:00+07:00"))
			.andExpect(jsonPath("$.itineraries[0].segments[0].departure.city").value("Hà Nội"))
			.andExpect(jsonPath("$.itineraries[0].segments[0].arrival.time").value(DAY + "T11:00:00+08:00"))
			.andExpect(jsonPath("$.itineraries[0].segments[0].arrival.timezone").value("Asia/Singapore"))
			.andExpect(jsonPath("$.itineraries[0].fareOptions[*].name", contains("Eco")))
			.andExpect(jsonPath("$.itineraries[0].fareOptions[0].cabinClass").value("ECONOMY"))
			.andExpect(jsonPath("$.itineraries[0].fareOptions[0].changeable").value(true))
			.andExpect(jsonPath("$.itineraries[0].fareOptions[0].adultPrice").value(1_000_000))
			.andExpect(jsonPath("$.itineraries[0].fareOptions[0].totalPrice").value(3_000_000))
			.andExpect(jsonPath("$.itineraries[0].fareOptions[0].seatsLeft").value(100))
			.andExpect(jsonPath("$.itineraries[2].totalDurationMinutes").value(360))
			.andExpect(jsonPath("$.itineraries[2].fareOptions[0].adultPrice").value(2_000_000))
			.andExpect(jsonPath("$.itineraries[2].fareOptions[0].totalPrice").value(6_000_000))
			.andExpect(jsonPath("$.itineraries[2].fareOptions[0].seatsLeft").value(40));
	}

	/** BR-05: hạng ghế lấy theo gói giá. Q1204 chỉ bán Biz nên có ở BUSINESS; Q1205 không có hạng BUSINESS. */
	@Test
	void businessSearchUsesBusinessFaresOnly() throws Exception {
		search(DAY, "BUSINESS", 1, 0, 0).andExpect(jsonPath("$.itineraries.length()").value(5))
			.andExpect(jsonPath("$.itineraries[*].segments[1].flightNumber", hasItem("Q1204")))
			.andExpect(jsonPath("$.itineraries[*].segments[1].flightNumber", not(hasItem("Q1205"))))
			.andExpect(jsonPath("$.itineraries[*].fareOptions[*].name", everyItem(is("Biz"))));
	}

	/** BR-06: đủ ghế cho người lớn + trẻ em (em bé không chiếm ghế). Q1205 còn đúng 2 ghế. */
	@Test
	void needsSeatsForAdultsAndChildrenOnly() throws Exception {
		search(DAY, "ECONOMY", 2, 0, 2)
			.andExpect(jsonPath("$.itineraries[*].segments[1].flightNumber", hasItem("Q1205")));
		search(DAY, "ECONOMY", 2, 1, 0)
			.andExpect(jsonPath("$.itineraries[*].segments[1].flightNumber", not(hasItem("Q1205"))));
	}

	/**
	 * BR-01, BR-25: không bán hành trình có chặng đầu cất cánh trong vòng N giờ tới; N đọc từ tham số ở mỗi lần tìm.
	 * Dời Q1300 và cặp Q1100 + Q1203 (nối 60 phút) về 2 giờ nữa.
	 */
	@Test
	void hidesItinerariesDepartingWithinMinimumHours() throws Exception {
		move(direct, 120, 300);
		move(firstLeg, 120, 250);
		move(secondLeg, 310, 430);
		LocalDate date = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusHours(2).toLocalDate();

		search(date, "ECONOMY", 1, 0, 0).andExpect(jsonPath("$.itineraries").isEmpty());
		jdbc.sql("UPDATE system_settings SET value = '1' WHERE key = 'booking.min_hours_before_departure'").update();
		search(date, "ECONOMY", 1, 0, 0)
			.andExpect(jsonPath("$.itineraries[*].segments[0].flightNumber", contains("Q1300", "Q1100")));
	}

	/** Dời giờ cất cánh và hạ cánh về {@code now() + n phút}. */
	void move(long flightId, int departureMinutes, int arrivalMinutes) {
		jdbc.sql("""
				UPDATE flights SET departure_time = now() + :dep * INTERVAL '1 minute',
				                   arrival_time = now() + :arr * INTERVAL '1 minute'
				WHERE id = :id
				""").param("dep", departureMinutes).param("arr", arrivalMinutes).param("id", flightId).update();
	}

	/** BR-03, BR-25: khoảng thời gian nối đọc từ tham số. */
	@Test
	void connectionWindowFollowsSettings() throws Exception {
		jdbc.sql("UPDATE system_settings SET value = '120' WHERE key = 'search.max_connection_minutes'").update();

		search(DAY, "ECONOMY", 1, 0, 0)
			.andExpect(jsonPath("$.itineraries[*].segments[1].flightNumber", contains("Q1203", "Q1200")));
	}

	/** FR-11: không có kết quả thì trả danh sách rỗng, không phải lỗi. */
	@Test
	void noResultIsEmptyList() throws Exception {
		search(DAY.minusDays(60), "ECONOMY", 1, 0, 0).andExpect(status().isOk())
			.andExpect(jsonPath("$.itineraries").isEmpty());
		mvc.perform(get("/api/flights/search").param("from", "ZZZ")
			.param("to", "QHC")
			.param("date", DAY.toString())
			.param("adults", "1")
			.param("cabin", "ECONOMY"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itineraries").isEmpty());
	}

	/** BR-11 và dữ liệu thiếu: lỗi theo trường để form hiện đúng chỗ. */
	@Test
	void rejectsInvalidQueryPerField() throws Exception {
		mvc.perform(get("/api/flights/search").param("from", "QHA")
			.param("to", "QHA")
			.param("date", DAY.toString())
			.param("adults", "1")
			.param("cabin", "ECONOMY"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("to"));
		search(DAY, "ECONOMY", 1, 0, 2).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("infants"));
		search(DAY, "ECONOMY", 5, 5, 0).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("adults"));
		search(DAY, "ECONOMY", 0, 1, 0).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("adults"));
		mvc.perform(get("/api/flights/search").param("from", "QHA")
			.param("to", "QHC")
			.param("date", DAY.toString())
			.param("adults", "1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("cabin"));
	}

}
