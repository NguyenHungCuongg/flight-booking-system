package vn.edu.uit.flightbooking.catalog.web;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;

/** FR-82, BR-91, BR-92. Mỗi test dùng hãng Q1/Q2 tự tạo bằng SQL. */
@IntegrationTest
@Transactional
class FareFamilyControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	MockHttpSession admin;

	@BeforeEach
	void setUp() throws Exception {
		admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();
		jdbc.sql("INSERT INTO airlines (code, name, ticket_prefix) VALUES ('Q1', 'Hãng Một', '901'), ('Q2', 'Hãng Hai', '902')")
			.update();
	}

	static String fare(String cabin, String name, int carryOnKg) {
		return """
				{"cabinClass": "%s", "name": "%s", "carryOnKg": %d, "checkedBaggageKg": 23,
				 "refundable": true, "refundFee": 600000, "changeable": true, "changeFee": 300000, "active": true}
				""".formatted(cabin, name, carryOnKg);
	}

	ResultActions create(String airline, String json) throws Exception {
		return mvc.perform(post("/api/admin/airlines/{code}/fare-families", airline).with(csrf(mvc)).session(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	ResultActions update(long id, String json) throws Exception {
		return mvc.perform(put("/api/admin/fare-families/{id}", id).with(csrf(mvc)).session(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	@Test
	void adminCreatesUpdatesAndListsFareFamiliesOfAnAirline() throws Exception {
		long id = idOf(create("Q1", fare("ECONOMY", "Economy Classic", 10))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.airlineCode").value("Q1"))
			.andExpect(jsonPath("$.refundFee").value(600000)));
		create("Q1", fare("BUSINESS", "Business Classic", 18)).andExpect(status().isCreated());
		create("Q2", fare("ECONOMY", "Của Hãng Khác", 7)).andExpect(status().isCreated());

		update(id, """
				{"cabinClass": "ECONOMY", "name": "Economy Lite", "carryOnKg": 7, "checkedBaggageKg": 0,
				 "refundable": false, "refundFee": 0, "changeable": true, "changeFee": 600000, "active": false}
				""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Economy Lite"))
			.andExpect(jsonPath("$.refundable").value(false))
			.andExpect(jsonPath("$.active").value(false));

		mvc.perform(get("/api/admin/airlines/Q1/fare-families").session(admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name", contains("Economy Lite", "Business Classic")));
	}

	@Test
	void nameIsUniqueWithinAirlineOnly() throws Exception {
		create("Q1", fare("ECONOMY", "Eco", 7)).andExpect(status().isCreated());
		long other = idOf(create("Q1", fare("ECONOMY", "Deluxe", 7)));

		create("Q1", fare("BUSINESS", "Eco", 7))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("name"));
		update(other, fare("ECONOMY", "Eco", 7))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("name"));
		update(other, fare("ECONOMY", "Deluxe", 10)).andExpect(status().isOk());
		create("Q2", fare("ECONOMY", "Eco", 7)).andExpect(status().isCreated());
	}

	/** Giá bán của chuyến đặt theo gói giá trong một hạng ghế, nên đổi hạng ghế sẽ làm sai các chuyến đã có. */
	@Test
	void cabinClassCannotChange() throws Exception {
		long id = idOf(create("Q1", fare("ECONOMY", "Eco", 7)));

		update(id, fare("BUSINESS", "Eco", 7))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("cabinClass"));
	}

	@Test
	void rejectsNegativeNumbersAndMissingFields() throws Exception {
		create("Q1", fare("ECONOMY", "Âm", -1))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("carryOnKg"));
		create("Q1", """
				{"cabinClass": "ECONOMY", "name": "Thiếu Phí", "carryOnKg": 7, "checkedBaggageKg": 0,
				 "refundable": false, "changeable": false, "changeFee": 0, "active": true}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("refundFee"));
	}

	@Test
	void unknownAirlineOrFareFamilyIsNotFound() throws Exception {
		create("ZZ", fare("ECONOMY", "Eco", 7))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		mvc.perform(get("/api/admin/airlines/ZZ/fare-families").session(admin)).andExpect(status().isNotFound());
		update(Long.MAX_VALUE, fare("ECONOMY", "Eco", 7)).andExpect(status().isNotFound());
	}

	@Test
	void deletesUnusedFareFamily() throws Exception {
		long id = idOf(create("Q1", fare("ECONOMY", "Eco", 7)));

		mvc.perform(delete("/api/admin/fare-families/{id}", id).with(csrf(mvc)).session(admin))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/admin/airlines/Q1/fare-families").session(admin))
			.andExpect(jsonPath("$.length()").value(0));
	}

	/**
	 * BR-91. Dữ liệu tham chiếu tạo bằng SQL: bản ghi tạo qua API chưa flush xuống DB trong transaction của test.
	 * Lỗi khoá ngoại làm hỏng transaction đó, nên request xoá phải là lệnh SQL cuối cùng.
	 */
	@Test
	void fareFamilySoldOnAFlightCannotBeDeleted() throws Exception {
		jdbc.sql("""
				INSERT INTO airports (code, name, city, country_code, timezone) VALUES
				    ('QHE', 'Đi', 'Hà Nội', 'VN', 'Asia/Ho_Chi_Minh'),
				    ('QHF', 'Đến', 'Đà Nẵng', 'VN', 'Asia/Ho_Chi_Minh')
				""").update();
		long fareId = jdbc.sql("""
				INSERT INTO fare_families (airline_code, cabin_class, name, carry_on_kg, checked_baggage_kg,
				                           refundable, changeable)
				VALUES ('Q1', 'ECONOMY', 'Đang Bán', 7, 0, false, false)
				RETURNING id
				""").query(Long.class).single();
		long flightId = jdbc.sql("""
				INSERT INTO flights (airline_code, flight_number, departure_airport, arrival_airport,
				                     departure_time, arrival_time)
				VALUES ('Q1', 'Q1100', 'QHE', 'QHF', now() + interval '1 day', now() + interval '1 day 1 hour')
				RETURNING id
				""").query(Long.class).single();
		jdbc.sql("INSERT INTO flight_fares (flight_id, fare_family_id, price) VALUES (?, ?, 1000000)")
			.params(flightId, fareId)
			.update();

		mvc.perform(delete("/api/admin/fare-families/{id}", fareId).with(csrf(mvc)).session(admin))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
	}

}
