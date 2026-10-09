package vn.edu.uit.flightbooking.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestMailSender;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;

/**
 * Không dùng {@code @Transactional}: email chỉ gửi sau khi transaction commit, nên test này commit thật
 * và tự dọn dữ liệu ở {@link #cleanUp()}.
 */
@IntegrationTest
class PasswordResetEmailTest {

	private static final Pattern TOKEN = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TestMailSender mail;

	final String email = TestUsers.randomEmail();

	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM password_reset_tokens WHERE user_id IN (SELECT id FROM users WHERE email = ?)")
			.param(email)
			.update();
		jdbc.sql("DELETE FROM users WHERE email = ?").param(email).update();
	}

	@Test
	void emailLinkResetsPassword() throws Exception {
		TestUsers.create(jdbc, Role.CUSTOMER, email);

		mvc.perform(post("/api/auth/forgot-password").with(csrf(mvc))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"email": "%s"}
					""".formatted(email)))
			.andExpect(status().isOk());

		await().atMost(Duration.ofSeconds(10)).until(() -> !mail.sentTo(email).isEmpty());
		MimeMessage message = mail.sentTo(email).getFirst();
		String html = (String) message.getContent();
		assertThat(message.getSubject()).isEqualTo("Đặt lại mật khẩu SkyLine");
		assertThat(html).contains("http://localhost:3000/reset-password?token=").contains("30 phút");

		Matcher link = TOKEN.matcher(html);
		assertThat(link.find()).isTrue();
		mvc.perform(post("/api/auth/reset-password").with(csrf(mvc))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"token": "%s", "newPassword": "QuaEmail123"}
					""".formatted(link.group(1))))
			.andExpect(status().isNoContent());
		TestUsers.login(mvc, email, "QuaEmail123");
	}

}
