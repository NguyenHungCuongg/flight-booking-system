package vn.edu.uit.flightbooking.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

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
import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;

@IntegrationTest
@Transactional
class AdminSettingsControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	SettingsApi settings;

	ResultActions update(MockHttpSession admin, String key, String body) throws Exception {
		return mvc.perform(put("/api/admin/settings/{key}", key).with(csrf(mvc)).session(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	@Test
	void listsAllSettingsWithValidRange() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		mvc.perform(get("/api/admin/settings").session(admin.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(8))
			.andExpect(jsonPath("$[?(@.key == 'booking.hold_minutes')].value").value(15))
			.andExpect(jsonPath("$[?(@.key == 'booking.hold_minutes')].min").value(5))
			.andExpect(jsonPath("$[?(@.key == 'booking.hold_minutes')].max").value(60));
	}

	@Test
	void updateRecordsWhoChangedItAndAppliesToNextRead() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		update(admin.session(), "booking.hold_minutes", """
				{"value": 20}
				""").andExpect(status().isNoContent());

		assertThat(settings.getInt(SettingKey.BOOKING_HOLD_MINUTES)).isEqualTo(20);
		mvc.perform(get("/api/admin/settings").session(admin.session()))
			.andExpect(jsonPath("$[?(@.key == 'booking.hold_minutes')].updatedByName").value("Người Dùng Test"));
	}

	@Test
	void valueOutsideRangeIsRejected() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		update(admin.session(), "booking.hold_minutes", """
				{"value": 61}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SETTING_OUT_OF_RANGE"));
		assertThat(settings.getInt(SettingKey.BOOKING_HOLD_MINUTES)).isEqualTo(15);
	}

	@Test
	void minConnectionMustStayBelowMax() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		// 600 nằm trong khoảng của min (30–600) nhưng không nhỏ hơn max hiện tại sau khi max = 600.
		update(admin.session(), "search.max_connection_minutes", """
				{"value": 600}
				""").andExpect(status().isNoContent());
		update(admin.session(), "search.min_connection_minutes", """
				{"value": 600}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SETTING_OUT_OF_RANGE"));
		update(admin.session(), "search.max_connection_minutes", """
				{"value": 60}
				""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SETTING_OUT_OF_RANGE"));
	}

	@Test
	void missingValueIsValidationErrorNotZero() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		update(admin.session(), "pricing.child_percent", "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		assertThat(settings.getInt(SettingKey.PRICING_CHILD_PERCENT)).isEqualTo(90);
	}

	@Test
	void unknownKeyIsNotFound() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		update(admin.session(), "khong.ton_tai", """
				{"value": 1}
				""")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

}
