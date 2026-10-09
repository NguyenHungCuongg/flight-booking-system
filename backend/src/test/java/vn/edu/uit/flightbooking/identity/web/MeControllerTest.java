package vn.edu.uit.flightbooking.identity.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;

@IntegrationTest
@Transactional
class MeControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Test
	void showsCurrentAccountWithRole() throws Exception {
		var staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF);

		mvc.perform(get("/api/me").session(staff.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(staff.id()))
			.andExpect(jsonPath("$.email").value(staff.email()))
			.andExpect(jsonPath("$.role").value("STAFF"));
	}

	@Test
	void updatesNameAndPhoneButNeverEmail() throws Exception {
		var user = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);

		mvc.perform(put("/api/me").with(csrf(mvc)).session(user.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName": "Trần Thị B", "phone": "0987654321", "email": "hacker@test.local"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fullName").value("Trần Thị B"))
			.andExpect(jsonPath("$.phone").value("0987654321"))
			.andExpect(jsonPath("$.email").value(user.email()));
	}

	@Test
	void changePasswordNeedsCurrentPassword() throws Exception {
		var user = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);

		mvc.perform(put("/api/me/password").with(csrf(mvc)).session(user.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword": "SaiMatKhau1", "newPassword": "MatKhauMoi9"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("currentPassword"));

		mvc.perform(put("/api/me/password").with(csrf(mvc)).session(user.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword": "%s", "newPassword": "MatKhauMoi9"}
					""".formatted(TestUsers.PASSWORD)))
			.andExpect(status().isNoContent());
		TestUsers.login(mvc, user.email(), "MatKhauMoi9");
	}

}
