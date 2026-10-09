package vn.edu.uit.flightbooking.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class AuthControllerTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	ResultActions register(String email, String password) throws Exception {
		return mvc.perform(post("/api/auth/register").with(csrf(mvc))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "password": "%s", "fullName": "Nguyễn Văn A", "phone": "0901234567"}
					""".formatted(email, password)));
	}

	ResultActions login(String email, String password) throws Exception {
		return mvc.perform(post("/api/auth/login").with(csrf(mvc))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s", "password": "%s"}
					""".formatted(email, password)));
	}

	@Test
	void registerCreatesActiveCustomerAndSignsIn() throws Exception {
		var session = (MockHttpSession) register("new.customer@test.local", "Matkhau123")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("new.customer@test.local"))
			.andExpect(jsonPath("$.role").value("CUSTOMER"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andReturn().getRequest().getSession(false);

		mvc.perform(get("/api/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"));
	}

	@Test
	void emailIsCaseInsensitive() throws Exception {
		register("Mixed.Case@Test.Local", "Matkhau123")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("mixed.case@test.local"));

		register("MIXED.CASE@test.local", "Matkhau123")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
	}

	@Test
	void loginAcceptsEmailInAnyCase() throws Exception {
		TestUsers.create(jdbc, Role.CUSTOMER, "case@test.local");

		login("  CASE@Test.Local ", TestUsers.PASSWORD).andExpect(status().isOk());
	}

	@Test
	void weakPasswordIsRejectedPerField() throws Exception {
		register("weak@test.local", "chicochu")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("password"));
		register("weak@test.local", "12345678").andExpect(status().isBadRequest());
		register("weak@test.local", "abc123").andExpect(status().isBadRequest());
	}

	@Test
	void passwordOverBcryptLimitIsValidationErrorNotServerError() throws Exception {
		// 25 chữ "ệ" (3 byte mỗi chữ) + số: 26 ký tự nhưng 76 byte, BCrypt sẽ ném lỗi nếu không chặn trước.
		register("long@test.local", "ệ".repeat(25) + "1")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("password"));
	}

	@Test
	void wrongPasswordAndUnknownEmailGiveTheSameError() throws Exception {
		TestUsers.create(jdbc, Role.CUSTOMER, "known@test.local");

		login("known@test.local", "SaiMatKhau1")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
			.andExpect(jsonPath("$.detail").value("Email hoặc mật khẩu không đúng"));
		login("unknown@test.local", "SaiMatKhau1")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
			.andExpect(jsonPath("$.detail").value("Email hoặc mật khẩu không đúng"));
	}

	@Test
	void veryLongPasswordAtLoginIsInvalidCredentials() throws Exception {
		TestUsers.create(jdbc, Role.CUSTOMER, "known@test.local");

		login("known@test.local", "a1".repeat(100))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void lockedAccountCannotLogIn() throws Exception {
		long id = TestUsers.create(jdbc, Role.CUSTOMER, "locked@test.local");
		jdbc.sql("UPDATE users SET status = 'LOCKED' WHERE id = ?").param(id).update();

		login("locked@test.local", TestUsers.PASSWORD)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
		// Sai mật khẩu thì vẫn chỉ báo INVALID_CREDENTIALS, không lộ là tài khoản đang bị khoá.
		login("locked@test.local", "SaiMatKhau1").andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void loginChangesSessionId() throws Exception {
		TestUsers.create(jdbc, Role.CUSTOMER, "fixation@test.local");
		var session = new MockHttpSession();
		String before = session.getId();

		mvc.perform(post("/api/auth/login").with(csrf(mvc)).session(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "fixation@test.local", "password": "%s"}
					""".formatted(TestUsers.PASSWORD)))
			.andExpect(status().isOk());

		assertThat(session.getId()).isNotEqualTo(before);
	}

	@Test
	void logoutEndsTheSession() throws Exception {
		var user = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);

		mvc.perform(post("/api/auth/logout").with(csrf(mvc)).session(user.session()))
			.andExpect(status().isNoContent());

		mvc.perform(get("/api/me").session(user.session()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

}
