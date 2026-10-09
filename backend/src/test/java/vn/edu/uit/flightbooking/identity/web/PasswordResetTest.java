package vn.edu.uit.flightbooking.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.identity.PasswordResetRequested;

/**
 * Lấy token từ sự kiện {@link PasswordResetRequested}. Test chạy trong transaction rollback nên email không được
 * gửi (listener chỉ chạy sau commit); luồng có email nằm ở {@code PasswordResetEmailTest}.
 */
@IntegrationTest
@Transactional
@RecordApplicationEvents
class PasswordResetTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	ResultActions forgot(String email) throws Exception {
		return mvc.perform(post("/api/auth/forgot-password").with(csrf(mvc))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s"}
					""".formatted(email)));
	}

	ResultActions reset(String token, String newPassword) throws Exception {
		return mvc.perform(post("/api/auth/reset-password").with(csrf(mvc))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"token": "%s", "newPassword": "%s"}
					""".formatted(token, newPassword)));
	}

	String requestToken(String email) throws Exception {
		forgot(email).andExpect(status().isOk());
		return events.stream(PasswordResetRequested.class).reduce((first, second) -> second).orElseThrow().rawToken();
	}

	@Test
	void unknownEmailStillGetsOkAndNoToken() throws Exception {
		forgot("nobody@test.local").andExpect(status().isOk());

		assertThat(events.stream(PasswordResetRequested.class)).isEmpty();
	}

	@Test
	void tokenIsStoredOnlyAsHashAndExpiresIn30Minutes() throws Exception {
		long id = TestUsers.create(jdbc, Role.CUSTOMER, "reset@test.local");

		String token = requestToken("reset@test.local");

		var row = jdbc.sql("""
				SELECT token_hash, extract(epoch FROM expires_at - created_at) AS ttl
				FROM password_reset_tokens WHERE user_id = ?
				""").param(id).query().singleRow();
		assertThat(row.get("token_hash")).isNotEqualTo(token).asString().hasSize(64);
		assertThat(((Number) row.get("ttl")).intValue()).isEqualTo(30 * 60);
	}

	@Test
	void resetChangesPasswordOnce() throws Exception {
		TestUsers.create(jdbc, Role.CUSTOMER, "reset@test.local");
		String token = requestToken("RESET@test.local");

		reset(token, "MatKhauMoi9").andExpect(status().isNoContent());
		TestUsers.login(mvc, "reset@test.local", "MatKhauMoi9");

		reset(token, "MatKhauKhac9")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void expiredTokenIsRejected() throws Exception {
		long id = TestUsers.create(jdbc, Role.CUSTOMER, "reset@test.local");
		String token = requestToken("reset@test.local");
		jdbc.sql("UPDATE password_reset_tokens SET expires_at = now() - interval '1 second' WHERE user_id = ?")
			.param(id)
			.update();

		reset(token, "MatKhauMoi9")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void madeUpTokenIsRejected() throws Exception {
		reset("khong-phai-token-that", "MatKhauMoi9")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void newPasswordMustFollowPasswordRule() throws Exception {
		TestUsers.create(jdbc, Role.CUSTOMER, "reset@test.local");
		String token = requestToken("reset@test.local");

		reset(token, "ngan1")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("newPassword"));
		// Token chưa bị tiêu thụ bởi request không hợp lệ.
		reset(token, "MatKhauMoi9").andExpect(status().isNoContent());
	}

}
