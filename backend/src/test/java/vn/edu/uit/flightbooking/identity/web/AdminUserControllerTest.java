package vn.edu.uit.flightbooking.identity.web;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@IntegrationTest
@Transactional
class AdminUserControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	ResultActions setStatus(MockHttpSession admin, long userId, String status) throws Exception {
		return mvc.perform(patch("/api/admin/users/{id}/status", userId).with(csrf(mvc)).session(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"status": "%s"}
					""".formatted(status)));
	}

	@Test
	void searchesByEmailOrNameAndFiltersByRole() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);
		long staffId = TestUsers.create(jdbc, Role.STAFF, "ops.search@test.local");
		TestUsers.create(jdbc, Role.CUSTOMER, "customer.search@test.local");

		mvc.perform(get("/api/admin/users").param("q", "SEARCH@test").session(admin.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page.totalElements").value(2));
		mvc.perform(get("/api/admin/users").param("q", "search@").param("role", "STAFF").session(admin.session()))
			.andExpect(jsonPath("$.content[*].id", contains((int) staffId)))
			.andExpect(jsonPath("$.page.totalElements").value(1));
		mvc.perform(get("/api/admin/users").param("q", "search@").param("size", "1").param("sort", "khong_co")
			.session(admin.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.page.totalPages").value(2));
	}

	@Test
	void createsStaffWhoCanLogIn() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		mvc.perform(post("/api/admin/users").with(csrf(mvc)).session(admin.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "New.Staff@test.local", "password": "BanDau123", "fullName": "Nhân Viên Mới",
					 "phone": "0911111111", "role": "STAFF"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("new.staff@test.local"))
			.andExpect(jsonPath("$.role").value("STAFF"));

		TestUsers.login(mvc, "new.staff@test.local", "BanDau123");
	}

	@Test
	void cannotCreateCustomerAccounts() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		mvc.perform(post("/api/admin/users").with(csrf(mvc)).session(admin.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "c@test.local", "password": "BanDau123", "fullName": "Khách",
					 "phone": "0911111111", "role": "CUSTOMER"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("role"));
	}

	@Test
	void lockingEndsTheUsersOpenSessionOnNextRequest() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);
		var customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);

		setStatus(admin.session(), customer.id(), "LOCKED")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("LOCKED"));

		mvc.perform(get("/api/me").session(customer.session()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
		mvc.perform(get("/api/me").session(customer.session()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

		setStatus(admin.session(), customer.id(), "ACTIVE").andExpect(status().isOk());
		TestUsers.login(mvc, customer.email(), TestUsers.PASSWORD);
	}

	@Test
	void adminCannotLockThemselves() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		setStatus(admin.session(), admin.id(), "LOCKED")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
	}

	@Test
	void unknownUserIsNotFound() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		setStatus(admin.session(), Long.MAX_VALUE, "LOCKED")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

}
