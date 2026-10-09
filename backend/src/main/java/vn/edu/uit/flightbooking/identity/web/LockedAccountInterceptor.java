package vn.edu.uit.flightbooking.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.CurrentUser;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.identity.domain.AccountService;

/**
 * BR-102: phiên của tài khoản bị khoá mất hiệu lực ngay ở request kế tiếp (TDD §4.1). Mỗi request đã đăng nhập
 * đọc trạng thái user theo khoá chính. Dùng interceptor của Spring MVC thay cho filter của Spring Security vì
 * SecurityConfig nằm ở common và common không được phụ thuộc identity; lỗi ném ra ở đây đi qua
 * GlobalExceptionHandler nên vẫn là ProblemDetail.
 */
@Component
class LockedAccountInterceptor implements HandlerInterceptor, WebMvcConfigurer {

	private final AccountService accounts;

	LockedAccountInterceptor(AccountService accounts) {
		this.accounts = accounts;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this).addPathPatterns("/api/**");
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user
				&& !accounts.isActive(user.id())) {
			HttpSession session = request.getSession(false);
			if (session != null) {
				session.invalidate();
			}
			SecurityContextHolder.clearContext();
			throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "Tài khoản đã bị khoá");
		}
		return true;
	}

}
