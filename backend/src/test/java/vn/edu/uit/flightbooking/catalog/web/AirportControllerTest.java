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

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;

/** FR-10, FR-80, BR-90, BR-91. Test tự tạo sân bay mã Q.. (seed không nạp khi test). */
@IntegrationTest
@Transactional
class AirportControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	MockHttpSession admin;

	@BeforeEach
	void logIn() throws Exception {
		admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();
	}

	ResultActions create(String json) throws Exception {
		return mvc.perform(post("/api/admin/airports").with(csrf(mvc)).session(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	ResultActions create(String code, String name, String city, boolean active) throws Exception {
		return create("""
				{"code": "%s", "name": "%s", "city": "%s", "countryCode": "VN",
				 "timezone": "Asia/Ho_Chi_Minh", "active": %s}
				""".formatted(code, name, city, active));
	}

	@Test
	void adminCreatesUpdatesAndListsAirportsIncludingInactive() throws Exception {
		create("QHA", "Nội Bài Thử", "Hà Nội", true)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("QHA"))
			.andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"))
			.andExpect(jsonPath("$.active").value(true));

		mvc.perform(put("/api/admin/airports/QHA").with(csrf(mvc)).session(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "Suvarnabhumi Thử", "city": "Bangkok", "countryCode": "TH",
					 "timezone": "Asia/Bangkok", "active": false}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.countryCode").value("TH"))
			.andExpect(jsonPath("$.active").value(false));

		mvc.perform(get("/api/admin/airports").session(admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.code == 'QHA')].active").value(false));
	}

	@Test
	void duplicateCodeIsFieldErrorNotOverwrite() throws Exception {
		create("QHB", "Bản Gốc", "Hà Nội", true).andExpect(status().isCreated());

		create("QHB", "Bản Đè", "Hà Nội", true)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("code"));
		mvc.perform(get("/api/admin/airports").session(admin))
			.andExpect(jsonPath("$[?(@.code == 'QHB')].name").value("Bản Gốc"));
	}

	@Test
	void rejectsBadCodeCountryAndTimezone() throws Exception {
		create("qhc", "Chữ Thường", "Hà Nội", true)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("code"));
		create("""
				{"code": "QHC", "name": "Sai Quốc Gia", "city": "Hà Nội", "countryCode": "XX",
				 "timezone": "Asia/Ho_Chi_Minh", "active": true}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("countryCode"));
		create("""
				{"code": "QHC", "name": "Sai Múi Giờ", "city": "Hà Nội", "countryCode": "VN",
				 "timezone": "GMT+7", "active": true}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("timezone"));
	}

	@Test
	void deletesUnusedAirport() throws Exception {
		create("QHD", "Chưa Dùng", "Hà Nội", true).andExpect(status().isCreated());

		mvc.perform(delete("/api/admin/airports/QHD").with(csrf(mvc)).session(admin))
			.andExpect(status().isNoContent());
		mvc.perform(delete("/api/admin/airports/QHD").with(csrf(mvc)).session(admin))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	/**
	 * BR-91. Dữ liệu tham chiếu tạo bằng SQL: bản ghi tạo qua API chưa flush xuống DB trong transaction của test.
	 * Lỗi khoá ngoại làm hỏng transaction đó, nên request xoá phải là lệnh SQL cuối cùng.
	 */
	@Test
	void airportUsedByFlightCannotBeDeleted() throws Exception {
		jdbc.sql("""
				INSERT INTO airports (code, name, city, country_code, timezone) VALUES
				    ('QHE', 'Đi', 'Hà Nội', 'VN', 'Asia/Ho_Chi_Minh'),
				    ('QHF', 'Đến', 'Đà Nẵng', 'VN', 'Asia/Ho_Chi_Minh')
				""").update();
		jdbc.sql("INSERT INTO airlines (code, name, ticket_prefix) VALUES ('Q1', 'Hãng Thử', '901')").update();
		jdbc.sql("""
				INSERT INTO flights (airline_code, flight_number, departure_airport, arrival_airport,
				                     departure_time, arrival_time)
				VALUES ('Q1', 'Q1100', 'QHE', 'QHF', now() + interval '1 day', now() + interval '1 day 1 hour')
				""").update();

		mvc.perform(delete("/api/admin/airports/QHE").with(csrf(mvc)).session(admin))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
	}

	@Test
	void publicSuggestionsIgnoreCaseAndDiacriticsAndHideInactive() throws Exception {
		create("QHG", "Sân Bay Thử Đông", "Hà Nội", true).andExpect(status().isCreated());
		create("QHH", "Sân Bay Thử Tây", "Hà Nội", false).andExpect(status().isCreated());

		mvc.perform(get("/api/airports").param("q", "ha noi"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].code", contains("QHG")));
		mvc.perform(get("/api/airports").param("q", "DONG"))
			.andExpect(jsonPath("$[*].code", contains("QHG")));
		mvc.perform(get("/api/airports").param("q", "qhg"))
			.andExpect(jsonPath("$[0].code").value("QHG"));
	}

	@Test
	void exactCodeMatchComesFirst() throws Exception {
		// "QHI" có trong tên của QHA nhưng sân bay mã QHI phải đứng đầu.
		create("QHA", "Cạnh QHI", "Hà Nội", true).andExpect(status().isCreated());
		create("QHI", "Đúng Mã", "Huế", true).andExpect(status().isCreated());

		mvc.perform(get("/api/airports").param("q", "qhi"))
			.andExpect(jsonPath("$[*].code", contains("QHI", "QHA")));
	}

	@Test
	void adminEndpointsRequireAdmin() throws Exception {
		var staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF).session();

		mvc.perform(get("/api/admin/airports").session(staff))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/airports")).andExpect(status().isOk());
	}

}
