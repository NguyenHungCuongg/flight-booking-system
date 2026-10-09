package vn.edu.uit.flightbooking.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import vn.edu.uit.flightbooking.IntegrationTest;

/**
 * Mặc định của Jakarta Mail là chờ vô hạn. SMTP treo thì listener giữ thread async và connection DB mãi mãi,
 * nên mọi lần gọi SMTP phải có timeout. STARTTLS bật kiểu "dùng nếu server hỗ trợ" để SMTP thật (cổng 587)
 * chấp nhận đăng nhập, còn Mailpit không có TLS vẫn chạy. Đọc từ Environment vì trong test JavaMailSender
 * là TestMailSender, Spring Boot không tạo MailProperties.
 */
@IntegrationTest
class MailSettingsTest {

	@Autowired
	Environment env;

	@Test
	void smtpCallsTimeOutAndUseStartTlsWhenOffered() {
		assertThat(env.getProperty("spring.mail.properties.mail.smtp.connectiontimeout")).isEqualTo("5000");
		assertThat(env.getProperty("spring.mail.properties.mail.smtp.timeout")).isEqualTo("10000");
		assertThat(env.getProperty("spring.mail.properties.mail.smtp.writetimeout")).isEqualTo("10000");
		assertThat(env.getProperty("spring.mail.properties.mail.smtp.starttls.enable")).isEqualTo("true");
	}

}
