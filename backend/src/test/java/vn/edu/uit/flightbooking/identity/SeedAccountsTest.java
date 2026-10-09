package vn.edu.uit.flightbooking.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;

/** Seed chỉ nạp ở profile dev; test chạy file seed trong transaction rồi rollback để kiểm tra hash và vai trò. */
@IntegrationTest
@Transactional
@Sql("classpath:db/seed/V105__seed_accounts.sql")
class SeedAccountsTest {

	@Autowired
	MockMvc mvc;

	@Test
	void demoAccountsLogInWithDocumentedPassword() throws Exception {
		for (String role : new String[] { "admin", "staff", "customer" }) {
			var session = TestUsers.login(mvc, role + "@demo.local", "Demo@1234");
			mvc.perform(get("/api/me").session(session)).andExpect(jsonPath("$.role").value(role.toUpperCase()));
		}
	}

}
