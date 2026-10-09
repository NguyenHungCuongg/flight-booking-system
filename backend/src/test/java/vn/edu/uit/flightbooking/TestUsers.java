package vn.edu.uit.flightbooking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.uit.flightbooking.common.Role;

/**
 * Tạo tài khoản và đăng nhập thật qua {@code POST /api/auth/login}, để test đi qua đúng đường của người dùng.
 * Gửi session trả về bằng {@code .session(user.session())}.
 */
public final class TestUsers {

	public static final String PASSWORD = "Password123";

	private static final String PASSWORD_HASH = new BCryptPasswordEncoder().encode(PASSWORD);

	public record TestUser(long id, String email, MockHttpSession session) {
	}

	private TestUsers() {
	}

	/** Email ngẫu nhiên để không trùng giữa các test, kể cả test đã commit dữ liệu. */
	public static String randomEmail() {
		return "user-" + UUID.randomUUID() + "@test.local";
	}

	/** Tài khoản ACTIVE với mật khẩu {@link #PASSWORD}. */
	public static long create(JdbcClient jdbc, Role role, String email) {
		return jdbc.sql("""
				INSERT INTO users (email, password_hash, full_name, phone, role)
				VALUES (?, ?, 'Người Dùng Test', '0900000000', ?)
				RETURNING id
				""")
			.params(email, PASSWORD_HASH, role.name())
			.query(Long.class)
			.single();
	}

	public static TestUser loggedIn(MockMvc mvc, JdbcClient jdbc, Role role) throws Exception {
		String email = randomEmail();
		long id = create(jdbc, role, email);
		return new TestUser(id, email, login(mvc, email, PASSWORD));
	}

	public static MockHttpSession login(MockMvc mvc, String email, String password) throws Exception {
		return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf(mvc))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s"}
						""".formatted(email, password)))
			.andExpect(status().isOk())
			.andReturn()
			.getRequest()
			.getSession(false);
	}

}
