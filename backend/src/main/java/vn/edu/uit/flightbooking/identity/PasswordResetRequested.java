package vn.edu.uit.flightbooking.identity;

/**
 * Phát khi có yêu cầu đặt lại mật khẩu (TDD §3.3); notification gửi email chứa {@code rawToken}.
 * {@link #toString()} giấu token để token không lọt vào log (NFR-03).
 */
public record PasswordResetRequested(long userId, String rawToken) {

	@Override
	public String toString() {
		return "PasswordResetRequested[userId=" + userId + "]";
	}

}
