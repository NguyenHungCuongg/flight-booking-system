package vn.edu.uit.flightbooking.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;

@IntegrationTest
@Transactional
class PublicSettingsControllerTest {

	/** Viết tay, không lấy từ SettingKey: tham số mới thêm sau này không được tự lộ ra ngoài. */
	static final List<String> PUBLIC_KEYS = List.of("booking.hold_minutes", "booking.min_hours_before_departure",
			"booking.max_seated_passengers", "pricing.child_percent", "pricing.infant_percent",
			"aftersales.min_hours_before_departure", "search.min_connection_minutes", "search.max_connection_minutes");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	ResultActions getPublic() throws Exception {
		return mvc.perform(get("/api/settings/public"));
	}

	@Test
	void anonymousGetsExactlyThePublicKeys() throws Exception {
		var res = getPublic().andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(PUBLIC_KEYS.size()))
			.andExpect(jsonPath("$['booking.hold_minutes']").value(15))
			.andExpect(jsonPath("$['pricing.child_percent']").value(90));
		for (String key : PUBLIC_KEYS) {
			res.andExpect(jsonPath("$['%s']".formatted(key)).isNumber());
		}
	}

	@Test
	void changedSettingShowsOnNextRead() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);
		mvc.perform(put("/api/admin/settings/booking.hold_minutes").with(csrf(mvc)).session(admin.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"value": 20}
					"""))
			.andExpect(status().isNoContent());

		getPublic().andExpect(jsonPath("$['booking.hold_minutes']").value(20));
	}

}
