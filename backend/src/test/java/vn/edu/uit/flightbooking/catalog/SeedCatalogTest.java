package vn.edu.uit.flightbooking.catalog;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;

/** Seed chỉ nạp ở profile dev; test chạy các file seed trong transaction rồi rollback để kiểm tra nội dung. */
@IntegrationTest
@Transactional
@Sql({ "classpath:db/seed/V100__seed_airports.sql", "classpath:db/seed/V101__seed_airlines.sql",
		"classpath:db/seed/V102__seed_fare_families.sql", "classpath:db/seed/V103__seed_baggage_options.sql" })
class SeedCatalogTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Test
	void seedMatchesBackendSchemaSection8() throws Exception {
		mvc.perform(get("/api/airports")).andExpect(jsonPath("$.length()").value(29));
		mvc.perform(get("/api/airports").param("q", "ho chi minh")).andExpect(jsonPath("$[*].code", contains("SGN")));
		mvc.perform(get("/api/airports").param("q", "seoul"))
			.andExpect(jsonPath("$[0].timezone").value("Asia/Seoul"));
		mvc.perform(get("/api/airlines")).andExpect(jsonPath("$.length()").value(5));
		mvc.perform(get("/api/airlines/TG/baggage-options"))
			.andExpect(jsonPath("$[*].weightKg", contains(15, 20, 25, 30)))
			.andExpect(jsonPath("$[*].price", contains(500000, 700000, 900000, 1100000)));
		mvc.perform(get("/api/airlines/VJ/baggage-options")).andExpect(jsonPath("$[0].price").value(250000));

		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();
		mvc.perform(get("/api/admin/airlines/VN/fare-families").session(admin))
			.andExpect(jsonPath("$[*].name",
					contains("Economy Lite", "Economy Classic", "Economy Flex", "Business Classic")))
			.andExpect(jsonPath("$[0].refundable").value(false))
			.andExpect(jsonPath("$[0].changeFee").value(600000));
		mvc.perform(get("/api/admin/airlines/SQ/fare-families").session(admin))
			.andExpect(jsonPath("$[*].name", contains("Economy Lite", "Economy Standard", "Business")))
			.andExpect(jsonPath("$[1].refundFee").value(1000000));
	}

}
