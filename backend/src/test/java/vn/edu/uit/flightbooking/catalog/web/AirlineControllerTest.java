package vn.edu.uit.flightbooking.catalog.web;

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

/** FR-81, BR-90, BR-91. Test tự tạo hãng mã Q.. và mã số vé 9.. (seed không nạp khi test). */
@IntegrationTest
@Transactional
class AirlineControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	MockHttpSession admin;

	@BeforeEach
	void logIn() throws Exception {
		admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();
	}

	ResultActions create(String code, String name, String ticketPrefix, boolean active) throws Exception {
		return mvc.perform(post("/api/admin/airlines").with(csrf(mvc)).session(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"code": "%s", "name": "%s", "ticketPrefix": "%s", "active": %s}
					""".formatted(code, name, ticketPrefix, active)));
	}

	ResultActions update(String code, String name, String ticketPrefix, boolean active) throws Exception {
		return mvc.perform(put("/api/admin/airlines/{code}", code).with(csrf(mvc)).session(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "%s", "ticketPrefix": "%s", "active": %s}
					""".formatted(name, ticketPrefix, active)));
	}

	@Test
	void adminCreatesUpdatesAndListsAirlinesIncludingInactive() throws Exception {
		create("Q2", "Hãng Hai", "902", true)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("Q2"))
			.andExpect(jsonPath("$.ticketPrefix").value("902"));

		update("Q2", "Hãng Hai Mới", "912", false)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Hãng Hai Mới"))
			.andExpect(jsonPath("$.active").value(false));

		mvc.perform(get("/api/admin/airlines").session(admin))
			.andExpect(jsonPath("$[?(@.code == 'Q2')].ticketPrefix").value("912"));
	}

	@Test
	void codeAndTicketPrefixAreUnique() throws Exception {
		create("Q3", "Hãng Ba", "903", true).andExpect(status().isCreated());
		create("Q4", "Hãng Bốn", "904", true).andExpect(status().isCreated());

		create("Q3", "Trùng Mã", "913", true)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("code"));
		create("Q5", "Trùng Mã Số Vé", "903", true)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("ticketPrefix"));
		update("Q4", "Hãng Bốn", "903", true)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("ticketPrefix"));
		// Giữ nguyên mã số vé của chính mình không phải là trùng.
		update("Q4", "Hãng Bốn Đổi Tên", "904", true).andExpect(status().isOk());
	}

	@Test
	void rejectsBadCodeAndTicketPrefix() throws Exception {
		create("q6", "Chữ Thường", "906", true)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("code"));
		create("Q6", "Mã Số Vé Ngắn", "96", true)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("ticketPrefix"));
	}

	@Test
	void deletesUnusedAirline() throws Exception {
		create("Q7", "Chưa Dùng", "907", true).andExpect(status().isCreated());

		mvc.perform(delete("/api/admin/airlines/Q7").with(csrf(mvc)).session(admin))
			.andExpect(status().isNoContent());
		mvc.perform(delete("/api/admin/airlines/Q7").with(csrf(mvc)).session(admin))
			.andExpect(status().isNotFound());
	}

	/** BR-91. Lỗi khoá ngoại làm hỏng transaction của test, nên request xoá phải là lệnh SQL cuối cùng. */
	@Test
	void airlineWithFareFamilyCannotBeDeleted() throws Exception {
		jdbc.sql("INSERT INTO airlines (code, name, ticket_prefix) VALUES ('Q8', 'Có Gói Giá', '908')").update();
		jdbc.sql("""
				INSERT INTO fare_families (airline_code, cabin_class, name, carry_on_kg, checked_baggage_kg,
				                           refundable, changeable)
				VALUES ('Q8', 'ECONOMY', 'Gói Thử', 7, 0, false, false)
				""").update();

		mvc.perform(delete("/api/admin/airlines/Q8").with(csrf(mvc)).session(admin))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
	}

	@Test
	void publicListShowsOnlyActiveAirlines() throws Exception {
		create("Q9", "Đang Bay", "909", true).andExpect(status().isCreated());
		create("QA", "Ngừng Bay", "910", false).andExpect(status().isCreated());

		mvc.perform(get("/api/airlines"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.code == 'Q9')]").isNotEmpty())
			.andExpect(jsonPath("$[?(@.code == 'QA')]").isEmpty());
	}

}
