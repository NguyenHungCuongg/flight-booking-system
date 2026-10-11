package vn.edu.uit.flightbooking.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;

/** Tìm kiếm trên seed V106 (1.200 chuyến): ví dụ TDD §8.8 và NFR-01. Seed chạy trong transaction của test rồi rollback. */
@IntegrationTest
@Transactional
@Sql({ "classpath:db/seed/V100__seed_airports.sql", "classpath:db/seed/V101__seed_airlines.sql",
		"classpath:db/seed/V102__seed_fare_families.sql", "classpath:db/seed/V106__seed_flights.sql" })
class SearchSeedTest {

	/** Ngày mai theo giờ Việt Nam: chuyến sớm nhất (06:00) luôn cách hiện tại hơn 3 giờ (BR-01). */
	static final LocalDate TOMORROW = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1);

	@Autowired
	MockMvc mvc;

	ResultActions search(String from, String to, LocalDate date) throws Exception {
		return mvc.perform(get("/api/flights/search").param("from", from)
			.param("to", to)
			.param("date", date.toString())
			.param("adults", "2")
			.param("children", "1")
			.param("infants", "1")
			.param("cabin", "ECONOMY"));
	}

	/** HAN→PQC: bay thẳng VN1233 rẻ hơn nên đứng trước; nối chuyến VN213 + VN1825 cho đúng các con số của TDD §8.8. */
	@Test
	void hanoiToPhuQuocMatchesTddExample() throws Exception {
		search("HAN", "PQC", TOMORROW).andExpect(status().isOk())
			.andExpect(jsonPath("$.itineraries[0].segments[*].flightNumber", contains("VN1233")))
			.andExpect(jsonPath("$.itineraries[1].segments[*].flightNumber", contains("VN213", "VN1825")))
			.andExpect(jsonPath("$.itineraries[1].totalDurationMinutes").value(360))
			.andExpect(jsonPath("$.itineraries[1].segments[0].layoverMinutesAfter").value(170))
			.andExpect(jsonPath("$.itineraries[1].segments[1].arrival.city").value("Phú Quốc"))
			.andExpect(jsonPath("$.itineraries[1].fareOptions[*].name",
					contains("Economy Lite", "Economy Classic", "Economy Flex")))
			.andExpect(jsonPath("$.itineraries[1].fareOptions[1].adultPrice").value(3_020_000))
			.andExpect(jsonPath("$.itineraries[1].fareOptions[1].totalPrice").value(9_060_000))
			.andExpect(jsonPath("$.itineraries[1].fareOptions[1].seatsLeft").value(168));
	}

	/** NFR-01: p95 dưới 1 giây. Đo 100 lần tìm trên 5 tuyến × 20 ngày, sau 10 lần chạy làm nóng. */
	@Test
	void searchP95IsUnderOneSecond() throws Exception {
		String[][] routes = { { "HAN", "PQC" }, { "HAN", "SGN" }, { "SGN", "HAN" }, { "SGN", "SIN" },
				{ "HAN", "ICN" } };
		for (int i = 0; i < 10; i++) {
			search("HAN", "PQC", TOMORROW).andExpect(status().isOk());
		}
		List<Long> millis = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			String[] route = routes[i % routes.length];
			long start = System.nanoTime();
			search(route[0], route[1], TOMORROW.plusDays(i / routes.length)).andExpect(status().isOk());
			millis.add((System.nanoTime() - start) / 1_000_000);
		}
		millis.sort(null);
		assertThat(millis.get(94)).as("p95 (ms)").isLessThan(1000);
	}

}
