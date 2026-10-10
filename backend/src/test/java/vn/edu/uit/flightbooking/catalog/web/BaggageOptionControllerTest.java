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

/** FR-83, BR-91, BR-92. Mỗi test dùng hãng Q1/Q2 tự tạo bằng SQL. */
@IntegrationTest
@Transactional
class BaggageOptionControllerTest {

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

	static String option(int weightKg, long price, boolean active) {
		return """
				{"weightKg": %d, "price": %d, "active": %s}
				""".formatted(weightKg, price, active);
	}

	ResultActions create(String airline, String json) throws Exception {
		return mvc.perform(post("/api/admin/airlines/{code}/baggage-options", airline).with(csrf(mvc)).session(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	ResultActions update(long id, String json) throws Exception {
		return mvc.perform(put("/api/admin/baggage-options/{id}", id).with(csrf(mvc)).session(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	@Test
	void adminManagesOptionsAndPublicSeesOnlyActiveOnesByWeight() throws Exception {
		create("Q1", option(25, 450000, true)).andExpect(status().isCreated());
		long id = idOf(create("Q1", option(15, 250000, true))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.airlineCode").value("Q1")));
		create("Q1", option(30, 550000, false)).andExpect(status().isCreated());
		create("Q2", option(20, 700000, true)).andExpect(status().isCreated());

		update(id, option(20, 350000, true))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.weightKg").value(20))
			.andExpect(jsonPath("$.price").value(350000));

		mvc.perform(get("/api/admin/airlines/Q1/baggage-options").session(admin))
			.andExpect(jsonPath("$[*].weightKg", contains(20, 25, 30)));
		mvc.perform(get("/api/airlines/Q1/baggage-options"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].weightKg", contains(20, 25)))
			.andExpect(jsonPath("$[0].price").value(350000));
	}

	@Test
	void weightIsUniqueWithinAirlineOnly() throws Exception {
		create("Q1", option(20, 350000, true)).andExpect(status().isCreated());
		long other = idOf(create("Q1", option(25, 450000, true)));

		create("Q1", option(20, 999000, true))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("weightKg"));
		update(other, option(20, 450000, true))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("weightKg"));
		update(other, option(25, 500000, true)).andExpect(status().isOk());
		create("Q2", option(20, 700000, true)).andExpect(status().isCreated());
	}

	@Test
	void weightAndPriceMustBePositive() throws Exception {
		create("Q1", option(0, 350000, true))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("weightKg"));
		create("Q1", option(20, 0, true))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("price"));
	}

	@Test
	void unknownAirlineIsNotFoundEvenOnPublicEndpoint() throws Exception {
		create("ZZ", option(20, 350000, true)).andExpect(status().isNotFound());
		mvc.perform(get("/api/airlines/ZZ/baggage-options"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void deletesUnusedOption() throws Exception {
		long id = idOf(create("Q1", option(20, 350000, true)));

		mvc.perform(delete("/api/admin/baggage-options/{id}", id).with(csrf(mvc)).session(admin))
			.andExpect(status().isNoContent());
		mvc.perform(delete("/api/admin/baggage-options/{id}", id).with(csrf(mvc)).session(admin))
			.andExpect(status().isNotFound());
	}

	/**
	 * BR-91. Dữ liệu tham chiếu tạo bằng SQL: bản ghi tạo qua API chưa flush xuống DB trong transaction của test.
	 * Lỗi khoá ngoại làm hỏng transaction đó, nên request xoá phải là lệnh SQL cuối cùng.
	 */
	@Test
	void optionBoughtInABookingCannotBeDeleted() throws Exception {
		long userId = TestUsers.create(jdbc, Role.CUSTOMER, TestUsers.randomEmail());
		long optionId = jdbc.sql("""
				INSERT INTO baggage_options (airline_code, weight_kg, price) VALUES ('Q1', 20, 350000) RETURNING id
				""").query(Long.class).single();
		long fareId = jdbc.sql("""
				INSERT INTO fare_families (airline_code, cabin_class, name, carry_on_kg, checked_baggage_kg,
				                           refundable, changeable)
				VALUES ('Q1', 'ECONOMY', 'Eco', 7, 0, false, false)
				RETURNING id
				""").query(Long.class).single();
		long bookingId = jdbc.sql("""
				INSERT INTO bookings (code, user_id, status, contact_name, contact_email, contact_phone,
				                      fare_total, baggage_total, discount_total, total_amount, hold_expires_at)
				VALUES ('QQTEST', ?, 'PENDING_PAYMENT', 'Khách', 'k@test.local', '0900000000',
				        1000000, 350000, 0, 1350000, now() + interval '15 minutes')
				RETURNING id
				""").param(userId).query(Long.class).single();
		long journeyId = jdbc.sql("""
				INSERT INTO booking_journeys (booking_id, direction, airline_code, fare_family_id, fare_family_name,
				                              cabin_class, carry_on_kg, checked_baggage_kg, refundable, refund_fee,
				                              changeable, change_fee)
				VALUES (?, 'OUTBOUND', 'Q1', ?, 'Eco', 'ECONOMY', 7, 0, false, 0, false, 0)
				RETURNING id
				""").params(bookingId, fareId).query(Long.class).single();
		long passengerId = jdbc.sql("""
				INSERT INTO passengers (booking_id, type, last_name, first_name, gender, date_of_birth)
				VALUES (?, 'ADULT', 'NGUYEN', 'AN', 'MALE', DATE '1990-01-01')
				RETURNING id
				""").param(bookingId).query(Long.class).single();
		jdbc.sql("""
				INSERT INTO booking_baggage (journey_id, passenger_id, baggage_option_id, weight_kg, price)
				VALUES (?, ?, ?, 20, 350000)
				""").params(journeyId, passengerId, optionId).update();

		mvc.perform(delete("/api/admin/baggage-options/{id}", optionId).with(csrf(mvc)).session(admin))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
	}

}
