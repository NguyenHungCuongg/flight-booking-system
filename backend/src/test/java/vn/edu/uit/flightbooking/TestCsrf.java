package vn.edu.uit.flightbooking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Gửi CSRF token giống trình duyệt thật: lấy cookie XSRF-TOKEN rồi gửi lại trong header X-XSRF-TOKEN.
 * <p>
 * Không dùng {@code SecurityMockMvcRequestPostProcessors.csrf()}: nó thay CsrfTokenRepository
 * của CsrfFilter dùng chung, làm hỏng CSRF của mọi test chạy sau trong cùng Spring context.
 */
public final class TestCsrf {

	private TestCsrf() {
	}

	public static RequestPostProcessor csrf(MockMvc mvc) throws Exception {
		Cookie cookie = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		return request -> {
			request.setCookies(cookie);
			request.addHeader("X-XSRF-TOKEN", cookie.getValue());
			return request;
		};
	}

}
