package vn.edu.uit.flightbooking.flight.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;
import static vn.edu.uit.flightbooking.flight.TestFlights.createOne;
import static vn.edu.uit.flightbooking.flight.TestFlights.newFlight;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.flight.FlightCancelled;
import vn.edu.uit.flightbooking.flight.TestFlights;

@IntegrationTest
@Transactional
@RecordApplicationEvents
class CancelFlightTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	MockHttpSession admin;

	TestFlights.Catalog catalog;

	long flightId;

	@BeforeEach
	void setUp() throws Exception {
		admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();
		catalog = TestFlights.catalog(jdbc);
		flightId = createOne(mvc, admin, newFlight(catalog, Map.of()));
	}

	ResultActions cancel(long id, MockHttpSession session) throws Exception {
		return mvc.perform(post("/api/admin/flights/{id}/cancel", id).with(csrf(mvc)).session(session));
	}

	/** FR-93: chuyến chuyển CANCELLED, phát FlightCancelled, không còn trong kết quả tìm kiếm. */
	@Test
	void cancelMarksFlightCancelledAndPublishesEvent() throws Exception {
		cancel(flightId, admin).andExpect(status().isOk())
			.andExpect(jsonPath("$.flight.id").value(flightId))
			.andExpect(jsonPath("$.flight.status").value("CANCELLED"));

		assertThat(events.stream(FlightCancelled.class)).containsExactly(new FlightCancelled(flightId));
		mvc.perform(get("/api/flights/search").param("from", "QHA").param("to", "QHB")
				.param("date", DAY.toString()).param("adults", "1").param("cabin", "ECONOMY"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itineraries").isEmpty());
	}

	@Test
	void cancelRejectsCancelledDepartedOrMissingFlight() throws Exception {
		cancel(flightId, admin).andExpect(status().isOk());

		cancel(flightId, admin).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.detail").value("Chuyến đã bị huỷ"));

		long departed = jdbc.sql("""
				INSERT INTO flights (airline_code, flight_number, departure_airport, arrival_airport,
				                     departure_time, arrival_time)
				VALUES ('Q1', 'Q1999', 'QHA', 'QHB', now() - interval '1 hour', now() + interval '1 hour')
				RETURNING id
				""").query(Long.class).single();
		cancel(departed, admin).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DEADLINE_PASSED"))
			.andExpect(jsonPath("$.detail").value("Chuyến đã cất cánh nên không huỷ được"));

		cancel(999999999L, admin).andExpect(status().isNotFound());

		MockHttpSession staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF).session();
		cancel(flightId, staff).andExpect(status().isForbidden());

		assertThat(events.stream(FlightCancelled.class)).containsExactly(new FlightCancelled(flightId));
	}

}

