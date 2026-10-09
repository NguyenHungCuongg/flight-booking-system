package vn.edu.uit.flightbooking.notification.domain;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import vn.edu.uit.flightbooking.identity.PasswordResetRequested;
import vn.edu.uit.flightbooking.identity.UserApi;
import vn.edu.uit.flightbooking.identity.UserSummary;
import vn.edu.uit.flightbooking.notification.infra.Mailer;

/** Email đặt lại mật khẩu (FR-03, FR-130). Chạy bất đồng bộ, sau khi transaction của identity commit. */
@Component
class PasswordResetEmail {

	private final UserApi users;

	private final Mailer mailer;

	private final String baseUrl;

	PasswordResetEmail(UserApi users, Mailer mailer, @Value("${app.base-url}") String baseUrl) {
		this.users = users;
		this.mailer = mailer;
		this.baseUrl = baseUrl;
	}

	@ApplicationModuleListener
	void on(PasswordResetRequested event) {
		UserSummary user = users.get(event.userId());
		mailer.send(user.email(), "Đặt lại mật khẩu SkyLine", "password-reset", Map.of(
				"fullName", user.fullName(),
				"link", baseUrl + "/reset-password?token=" + event.rawToken()));
	}

}
