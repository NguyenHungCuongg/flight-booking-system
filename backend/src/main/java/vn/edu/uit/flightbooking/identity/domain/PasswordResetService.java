package vn.edu.uit.flightbooking.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.identity.PasswordResetRequested;

/** FR-03, BR-103, TDD §4.4. DB chỉ lưu SHA-256 của token. */
@Service
public class PasswordResetService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final AccountService accounts;

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	PasswordResetService(AccountService accounts, JdbcClient jdbc, ApplicationEventPublisher events) {
		this.accounts = accounts;
		this.jdbc = jdbc;
		this.events = events;
	}

	/** Email không tồn tại thì im lặng bỏ qua: API luôn trả 200 để không lộ email nào đã đăng ký. */
	@Transactional
	public void request(String email) {
		accounts.findByEmail(email).ifPresent(user -> {
			byte[] bytes = new byte[32];
			RANDOM.nextBytes(bytes);
			String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
			jdbc.sql("""
					INSERT INTO password_reset_tokens (user_id, token_hash, expires_at)
					VALUES (?, ?, now() + interval '30 minutes')
					""")
				.params(user.getId(), sha256(token))
				.update();
			events.publishEvent(new PasswordResetRequested(user.getId(), token));
		});
	}

	@Transactional
	public void reset(String token, String newPassword) {
		// Đánh dấu đã dùng bằng một câu UPDATE nguyên tử: hai request cùng token thì chỉ một request thành công.
		long userId = jdbc.sql("""
				UPDATE password_reset_tokens SET used_at = now()
				WHERE token_hash = ? AND used_at IS NULL AND expires_at > now()
				RETURNING user_id
				""")
			.param(sha256(token))
			.query(Long.class)
			.optional()
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN,
					"Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"));
		accounts.setPassword(userId, newPassword);
	}

	private static String sha256(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
