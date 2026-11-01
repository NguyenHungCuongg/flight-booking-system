package vn.edu.uit.flightbooking.report.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;

@IntegrationTest
@Transactional
class AdminReportControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Test
	void adminCanQueryCashFlowReport() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		mvc.perform(get("/api/admin/reports/cash-flow")
				.session(admin.session())
				.param("from", "2026-10-01")
				.param("to", "2026-10-31")
				.param("groupBy", "DAY"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isArray())
			.andExpect(jsonPath("$.totalCharged").isNumber())
			.andExpect(jsonPath("$.totalRefunded").isNumber())
			.andExpect(jsonPath("$.netRevenue").isNumber());
	}

	@Test
	void adminCanQuerySalesReport() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		mvc.perform(get("/api/admin/reports/sales")
				.session(admin.session())
				.param("from", "2026-10-01")
				.param("to", "2026-10-31")
				.param("groupBy", "AIRLINE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isArray())
			.andExpect(jsonPath("$.totalTickets").isNumber())
			.andExpect(jsonPath("$.totalRevenue").isNumber());
	}

	@Test
	void adminCanQueryLoadFactorReport() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		mvc.perform(get("/api/admin/reports/load-factor")
				.session(admin.session())
				.param("from", "2026-10-01")
				.param("to", "2026-10-31"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isArray())
			.andExpect(jsonPath("$.totalSeats").isNumber())
			.andExpect(jsonPath("$.occupiedSeats").isNumber())
			.andExpect(jsonPath("$.averageLoadFactorPercent").isNumber());
	}

	@Test
	void rejectsEndDateBeforeStartDate() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN);

		mvc.perform(get("/api/admin/reports/cash-flow")
				.session(admin.session())
				.param("from", "2026-10-31")
				.param("to", "2026-10-01")
				.param("groupBy", "DAY"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("to"));
	}

	@Test
	void unauthenticatedUserCannotAccessReports() throws Exception {
		mvc.perform(get("/api/admin/reports/cash-flow")
				.param("from", "2026-10-01")
				.param("to", "2026-10-31")
				.param("groupBy", "DAY"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void customerOrStaffCannotAccessReports() throws Exception {
		var customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		mvc.perform(get("/api/admin/reports/cash-flow")
				.session(customer.session())
				.param("from", "2026-10-01")
				.param("to", "2026-10-31")
				.param("groupBy", "DAY"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		var staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF);
		mvc.perform(get("/api/admin/reports/cash-flow")
				.session(staff.session())
				.param("from", "2026-10-01")
				.param("to", "2026-10-31")
				.param("groupBy", "DAY"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

}
