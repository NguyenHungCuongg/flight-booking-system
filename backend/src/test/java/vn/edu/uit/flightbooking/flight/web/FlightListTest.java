package vn.edu.uit.flightbooking.flight.web;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;
import static vn.edu.uit.flightbooking.flight.TestFlights.createOne;
import static vn.edu.uit.flightbooking.flight.TestFlights.newFlight;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.flight.TestFlights;

/**
 * FR-91: danh sách chuyến cho Staff và Admin. Mọi test lọc theo hãng Q1/Q2 hoặc sân bay Q.. tự tạo, vì DB test có thể
 * còn chuyến của test khác.
 */
@IntegrationTest
@Transactional
class FlightListTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	MockHttpSession staff;

	long cancelled;

	/**
	 * Q1100 QHA→QHB ngày DAY và DAY+1 (chuyến DAY+1 bị huỷ), Q1200 QHA→QHC ngày DAY lúc 08:00, Q2100 QHB→QHA ngày DAY
	 * lúc 07:00.
	 */
	@BeforeEach
	void setUp() throws Exception {
		MockHttpSession admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();
		staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF).session();
		TestFlights.Catalog catalog = TestFlights.catalog(jdbc);
		createOne(mvc, admin, newFlight(catalog, Map.of()));
		cancelled = createOne(mvc, admin, newFlight(catalog,
				Map.of("fromDate", DAY.plusDays(1).toString(), "toDate", DAY.plusDays(1).toString())));
		createOne(mvc, admin, newFlight(catalog,
				Map.of("flightNumber", "Q1200", "arrivalAirport", "QHC", "departureTime", "08:00")));
		createOne(mvc, admin, newFlight(catalog,
				Map.of("airlineCode", "Q2", "flightNumber", "Q2100", "departureAirport", "QHB", "arrivalAirport", "QHA",
						"departureTime", "07:00", "cabins", List.of(Map.of("cabinClass", "ECONOMY", "totalSeats", 50)),
						"fares", List.of(Map.of("fareFamilyId", catalog.otherAirlineFare(), "price", 900_000)))));
		jdbc.sql("UPDATE flights SET status = 'CANCELLED' WHERE id = ?").param(cancelled).update();
	}

	@Test
	void filtersByAirlineSortsByDepartureAndPages() throws Exception {
		mvc.perform(get("/api/staff/flights").param("airline", "Q1").param("size", "2").session(staff))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].flightNumber", contains("Q1100", "Q1200")))
			.andExpect(jsonPath("$.content[0].cabins[*].cabinClass", contains("ECONOMY", "BUSINESS")))
			.andExpect(jsonPath("$.content[0].cabins[0].availableSeats").value(100))
			.andExpect(jsonPath("$.page.totalElements").value(3))
			.andExpect(jsonPath("$.page.totalPages").value(2));
		mvc.perform(get("/api/staff/flights").param("airline", "Q1").param("size", "2").param("page", "1")
			.session(staff)).andExpect(jsonPath("$.content[*].id", contains((int) cancelled)));
	}

	@Test
	void filtersByRouteDateAndStatus() throws Exception {
		mvc.perform(get("/api/staff/flights").param("from", "QHA").param("to", "QHB").param("date", DAY.toString())
			.session(staff)).andExpect(jsonPath("$.content[*].flightNumber", contains("Q1100")));
		mvc.perform(get("/api/staff/flights").param("from", "QHA").param("status", "CANCELLED").session(staff))
			.andExpect(jsonPath("$.content[*].id", contains((int) cancelled)));
		mvc.perform(get("/api/staff/flights").param("to", "QHA").session(staff))
			.andExpect(jsonPath("$.content[*].flightNumber", contains("Q2100")));
	}

	/** TDD §5.1: ngày lọc là ngày theo giờ sân bay đi. 00:30 ở Singapore là 23:30 hôm trước ở Việt Nam. */
	@Test
	void dateFilterUsesDepartureAirportZone() throws Exception {
		jdbc.sql("""
				INSERT INTO flights (airline_code, flight_number, departure_airport, arrival_airport,
				                     departure_time, arrival_time)
				VALUES ('Q1', 'Q1300', 'QHC', 'QHA', (?::date + time '00:30') AT TIME ZONE 'Asia/Singapore',
				        (?::date + time '02:30') AT TIME ZONE 'Asia/Singapore')
				""").params(DAY.toString(), DAY.toString()).update();

		mvc.perform(get("/api/staff/flights").param("from", "QHC").param("date", DAY.toString()).session(staff))
			.andExpect(jsonPath("$.content[*].flightNumber", contains("Q1300")))
			.andExpect(jsonPath("$.content[0].departureTime").value(DAY + "T00:30:00+08:00"))
			.andExpect(jsonPath("$.content[0].arrivalTime").value(DAY + "T01:30:00+07:00"));
		mvc.perform(get("/api/staff/flights").param("from", "QHC").param("date", DAY.minusDays(1).toString())
			.session(staff)).andExpect(jsonPath("$.content.length()").value(0));
	}

	@Test
	void customerCannotListAndBadFilterIs400() throws Exception {
		MockHttpSession customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER).session();
		mvc.perform(get("/api/staff/flights").session(customer)).andExpect(status().isForbidden());
		mvc.perform(get("/api/staff/flights").param("status", "FLYING").session(staff))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/api/staff/flights").param("date", "01/12/2026").session(staff))
			.andExpect(status().isBadRequest());
	}

}
