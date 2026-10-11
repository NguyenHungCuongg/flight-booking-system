package vn.edu.uit.flightbooking.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;

/** Seed V106 (BACKEND_SCHEMA §8.6) chạy trong transaction của test rồi rollback, giống SeedCatalogTest. */
@IntegrationTest
@Transactional
@Sql({ "classpath:db/seed/V100__seed_airports.sql", "classpath:db/seed/V101__seed_airlines.sql",
		"classpath:db/seed/V102__seed_fare_families.sql", "classpath:db/seed/V106__seed_flights.sql" })
class SeedFlightsTest {

	/** Ngày mai theo giờ Việt Nam: chắc chắn nằm trong 60 ngày của seed. */
	static final LocalDate TOMORROW = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1);

	static final String SEEDED = "airline_code IN ('VN', 'VJ', 'QH', 'SQ', 'TG')";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	int count(String sql) {
		return jdbc.sql(sql).query(Integer.class).single();
	}

	long flightOn(String flightNumber, LocalDate day) {
		return jdbc.sql("""
				SELECT f.id FROM flights f JOIN airports a ON a.code = f.departure_airport
				WHERE f.flight_number = ? AND (f.departure_time AT TIME ZONE a.timezone)::date = ?
				""").params(flightNumber, day).query(Long.class).single();
	}

	/** 20 mẫu × 60 ngày; mỗi chuyến 2 hạng ghế; mỗi ngày VN bán 4 gói × 12 mẫu, 4 hãng còn lại 3 gói × 2 mẫu. */
	@Test
	void seedsTwelveHundredFlightsWithCabinsAndFares() {
		assertThat(count("SELECT count(*) FROM flights WHERE " + SEEDED)).isEqualTo(1200);
		assertThat(count("SELECT count(*) FROM flight_cabins c JOIN flights f ON f.id = c.flight_id WHERE " + SEEDED))
			.isEqualTo(2400);
		assertThat(count("SELECT count(*) FROM flight_fares ff JOIN flights f ON f.id = ff.flight_id WHERE " + SEEDED))
			.isEqualTo(60 * (12 * 4 + 4 * 2 * 3));
	}

	/** Giờ địa phương, đổi múi giờ (SGN +07:00 → SIN +08:00), chuyến qua đêm, giá = giá gốc × hệ số làm tròn 10.000. */
	@Test
	void flightsMatchTemplates() throws Exception {
		MockHttpSession admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();
		mvc.perform(get("/api/admin/flights/{id}", flightOn("VN213", TOMORROW)).session(admin))
			.andExpect(jsonPath("$.flight.departureTime").value(TOMORROW + "T06:00:00+07:00"))
			.andExpect(jsonPath("$.flight.arrivalTime").value(TOMORROW + "T08:10:00+07:00"))
			.andExpect(jsonPath("$.flight.cabins[*].totalSeats", contains(168, 16)))
			.andExpect(jsonPath("$.fares[*].fareFamilyName",
					contains("Economy Lite", "Economy Classic", "Economy Flex", "Business Classic")))
			.andExpect(jsonPath("$.fares[*].price", contains(1290000, 1870000, 2450000, 4640000)));
		mvc.perform(get("/api/admin/flights/{id}", flightOn("VN651", TOMORROW)).session(admin))
			.andExpect(jsonPath("$.flight.arrivalTime").value(TOMORROW + "T12:30:00+08:00"));
		mvc.perform(get("/api/admin/flights/{id}", flightOn("VN416", TOMORROW)).session(admin))
			.andExpect(jsonPath("$.flight.arrivalTime").value(TOMORROW.plusDays(1) + "T06:15:00+09:00"));

		mvc.perform(get("/api/staff/flights").param("from", "HAN").param("to", "SGN")
			.param("date", TOMORROW.toString())
			.session(admin)).andExpect(jsonPath("$.content[*].flightNumber", contains("VN213", "VJ123", "QH201")));
	}

}
