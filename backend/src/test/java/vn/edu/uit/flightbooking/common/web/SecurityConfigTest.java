package vn.edu.uit.flightbooking.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import vn.edu.uit.flightbooking.IntegrationTest;

@IntegrationTest
class SecurityConfigTest {

	@Autowired
	MockMvc mvc;

	/** Request đã qua lớp bảo mật: route chưa tồn tại thì 404, nhưng không được là 401/403. */
	static ResultMatcher passesSecurity() {
		return result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
	}

	@Test
	void healthIsPublic() throws Exception {
		mvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void searchAndCatalogReadsArePublic() throws Exception {
		mvc.perform(get("/api/flights/search")).andExpect(passesSecurity());
		mvc.perform(get("/api/airports")).andExpect(passesSecurity());
		mvc.perform(get("/api/airlines/VN/baggage-options")).andExpect(passesSecurity());
	}

	@Test
	void anonymousUserGetsUnauthenticatedProblem() throws Exception {
		mvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mvc.perform(post("/api/auth/logout").with(csrf(mvc)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(roles = "CUSTOMER")
	void customerCannotCallStaffOrAdminApis() throws Exception {
		mvc.perform(get("/api/staff/bookings"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mvc.perform(get("/api/admin/settings")).andExpect(status().isForbidden());
		mvc.perform(get("/api/bookings")).andExpect(passesSecurity());
	}

	@Test
	@WithMockUser(roles = "STAFF")
	void staffCannotBookOrCallAdminApis() throws Exception {
		mvc.perform(get("/api/staff/bookings")).andExpect(passesSecurity());
		mvc.perform(get("/api/bookings")).andExpect(status().isForbidden());
		mvc.perform(get("/api/admin/settings")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void adminInheritsStaffRights() throws Exception {
		mvc.perform(get("/api/staff/bookings")).andExpect(passesSecurity());
		mvc.perform(get("/api/admin/settings")).andExpect(passesSecurity());
	}

	@Test
	@WithMockUser(roles = "CUSTOMER")
	void writeRequestsNeedCsrfToken() throws Exception {
		mvc.perform(post("/api/bookings"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mvc.perform(post("/api/bookings").with(csrf(mvc))).andExpect(passesSecurity());
	}

	@Test
	void publicWriteEndpointsStillNeedCsrfToken() throws Exception {
		mvc.perform(post("/api/auth/login"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mvc.perform(post("/api/auth/login").with(csrf(mvc))).andExpect(passesSecurity());
	}

	@Test
	@WithMockUser(roles = "CUSTOMER")
	void trailingSlashDoesNotBypassRoleCheck() throws Exception {
		mvc.perform(get("/api/admin/settings/")).andExpect(status().isForbidden());
	}

	@Test
	void apiDocsAreHiddenOutsideDevProfile() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
	}

	@Test
	void csrfEndpointIssuesCookieReadableByJavaScript() throws Exception {
		mvc.perform(get("/api/auth/csrf"))
			.andExpect(status().isNoContent())
			.andExpect(cookie().exists("XSRF-TOKEN"))
			.andExpect(cookie().httpOnly("XSRF-TOKEN", false));
	}

}
