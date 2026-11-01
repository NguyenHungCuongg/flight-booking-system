package vn.edu.uit.flightbooking.flight.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;
import static vn.edu.uit.flightbooking.flight.TestFlights.create;
import static vn.edu.uit.flightbooking.flight.TestFlights.createOne;
import static vn.edu.uit.flightbooking.flight.TestFlights.newFlight;

import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
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

/** FR-90, FR-91 (chi tiết), BR-80, BR-81, BR-85, BR-91. */
@IntegrationTest
@Transactional
class CreateFlightTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	MockHttpSession admin;

	TestFlights.Catalog catalog;

	@BeforeEach
	void setUp() throws Exception {
		admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();
		catalog = TestFlights.catalog(jdbc);
	}

	String flight(Map<String, Object> changes) {
		return newFlight(catalog, changes);
	}

	/** BR-81, TDD §5.1: giờ nhập theo giờ sân bay đi, trả theo giờ của từng sân bay kèm offset. */
	@Test
	void createsSingleFlightAndShowsTimesInEachAirportZone() throws Exception {
		long id = createOne(mvc, admin,
				flight(Map.of("arrivalAirport", "QHC", "departureTime", "09:30", "durationMinutes", 120)));

		mvc.perform(get("/api/admin/flights/{id}", id).session(admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.flight.flightNumber").value("Q1100"))
			.andExpect(jsonPath("$.flight.departureTime").value(DAY + "T09:30:00+07:00"))
			.andExpect(jsonPath("$.flight.departureTimezone").value("Asia/Ho_Chi_Minh"))
			.andExpect(jsonPath("$.flight.arrivalTime").value(DAY + "T12:30:00+08:00"))
			.andExpect(jsonPath("$.flight.arrivalTimezone").value("Asia/Singapore"))
			.andExpect(jsonPath("$.flight.aircraftModel").value("Airbus A321"))
			.andExpect(jsonPath("$.flight.status").value("SCHEDULED"))
			.andExpect(jsonPath("$.flight.cabins[*].cabinClass", contains("ECONOMY", "BUSINESS")))
			.andExpect(jsonPath("$.flight.cabins[0].totalSeats").value(100))
			.andExpect(jsonPath("$.flight.cabins[0].availableSeats").value(100))
			.andExpect(jsonPath("$.fares[*].fareFamilyName", contains("Eco", "Biz")))
			.andExpect(jsonPath("$.fares[*].price", contains(1_000_000, 3_000_000)))
			.andExpect(jsonPath("$.bookingCount").value(0));
	}

	/** BR-85: 14 ngày có đúng 2 thứ Hai và 2 thứ Sáu; thứ Hai đã có chuyến trùng thì bỏ qua. */
	@Test
	void bulkCreatesChosenWeekdaysAndSkipsDuplicates() throws Exception {
		String monday = DAY.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).toString();
		create(mvc, admin, flight(Map.of("fromDate", monday, "toDate", monday))).andExpect(status().isCreated());

		create(mvc, admin, flight(Map.of("toDate", DAY.plusDays(13).toString(), "daysOfWeek",
				List.of("MONDAY", "FRIDAY"))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.created").value(3))
			.andExpect(jsonPath("$.skipped").value(1))
			.andExpect(jsonPath("$.flightIds.length()").value(3));

		int flights = jdbc.sql("SELECT count(*) FROM flights WHERE flight_number = 'Q1100'").query(Integer.class).single();
		int cabins = jdbc.sql("""
				SELECT count(*) FROM flight_cabins c JOIN flights f ON f.id = c.flight_id WHERE f.flight_number = 'Q1100'
				""").query(Integer.class).single();
		assertThat(flights).isEqualTo(4);
		assertThat(cabins).isEqualTo(8);
	}

	/** BR-85: tối đa 180 ngày, tính cả ngày đầu và ngày cuối. */
	@Test
	void dateRangeIsAtMost180Days() throws Exception {
		create(mvc, admin, flight(Map.of("toDate", DAY.plusDays(179).toString(), "daysOfWeek", List.of("MONDAY"))))
			.andExpect(status().isCreated());
		create(mvc, admin, flight(Map.of("flightNumber", "Q1200", "toDate", DAY.plusDays(180).toString())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("toDate"));
		create(mvc, admin, flight(Map.of("flightNumber", "Q1200", "toDate", DAY.minusDays(1).toString())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("toDate"));
	}

	@Test
	void rejectsDeparturesInThePast() throws Exception {
		String yesterday = DAY.minusDays(31).toString();
		create(mvc, admin, flight(Map.of("fromDate", yesterday, "toDate", yesterday)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("fromDate"));
	}

	/** BR-80, BR-81, BR-91: mỗi lỗi chỉ đúng trường để form hiện lỗi đúng chỗ. */
	@Test
	void rejectsInvalidFieldsOneByOne() {
		List<Object[]> cases = List.of(
				new Object[] { Map.of("airlineCode", "Q3", "flightNumber", "Q3100"), "airlineCode" },
				new Object[] { Map.of("airlineCode", "ZZ", "flightNumber", "ZZ100"), "airlineCode" },
				new Object[] { Map.of("flightNumber", "Q2100"), "flightNumber" },
				new Object[] { Map.of("flightNumber", "Q112345"), "flightNumber" },
				new Object[] { Map.of("departureAirport", "QHD"), "departureAirport" },
				new Object[] { Map.of("arrivalAirport", "XXX"), "arrivalAirport" },
				new Object[] { Map.of("arrivalAirport", "QHA"), "arrivalAirport" },
				new Object[] { Map.of("cabins", List.of()), "cabins" },
				new Object[] { Map.of("cabins", List.of(Map.of("cabinClass", "ECONOMY", "totalSeats", 0))),
						"cabins[0].totalSeats" },
				new Object[] { Map.of("cabins", List.of(Map.of("cabinClass", "ECONOMY", "totalSeats", 100),
						Map.of("cabinClass", "ECONOMY", "totalSeats", 10))), "cabins[1].cabinClass" },
				new Object[] { Map.of("fares", List.of(Map.of("fareFamilyId", catalog.otherAirlineFare(), "price", 1))),
						"fares[0].fareFamilyId" },
				new Object[] { Map.of("cabins", List.of(Map.of("cabinClass", "ECONOMY", "totalSeats", 100))),
						"fares[1].fareFamilyId" },
				new Object[] { Map.of("fares", List.of(Map.of("fareFamilyId", catalog.economyFare(), "price", 0))),
						"fares[0].price" },
				new Object[] { Map.of("durationMinutes", 0), "durationMinutes" },
				new Object[] { Map.of("daysOfWeek", List.of()), "daysOfWeek" },
				new Object[] { Map.of("aircraftModel", " "), "aircraftModel" });
		cases.forEach(c -> {
			@SuppressWarnings("unchecked")
			Map<String, Object> changes = (Map<String, Object>) c[0];
			try {
				create(mvc, admin, flight(changes))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
					.andExpect(jsonPath("$.errors[0].field").value(c[1]));
			}
			catch (Throwable e) {
				throw new AssertionError("Trường hợp " + changes + ": " + e.getMessage(), e);
			}
		});
	}

	@Test
	void onlyAdminCreatesAndUnknownFlightIsNotFound() throws Exception {
		MockHttpSession staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF).session();
		create(mvc, staff, flight(Map.of())).andExpect(status().isForbidden());

		mvc.perform(get("/api/admin/flights/{id}", Long.MAX_VALUE).session(admin))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

}
