package vn.edu.uit.flightbooking.common.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/** Phân quyền theo đường dẫn (TDD §4.3), CSRF cho SPA (TDD §4.2), lỗi 401/403 dạng ProblemDetail. */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/api/flights/search", "/api/airports", "/api/airlines/**").permitAll()
				.requestMatchers("/api/auth/logout").authenticated()
				.requestMatchers("/api/auth/**", "/api/payments/vnpay/**").permitAll()
				.requestMatchers("/api/me/**").authenticated()
				.requestMatchers("/api/bookings/**", "/api/reschedules/**").hasRole("CUSTOMER")
				.requestMatchers("/api/staff/**").hasRole("STAFF")
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.requestMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
				.anyRequest().authenticated())
			.csrf(csrf -> csrf.spa())
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint((request, response, e) -> resolver.resolveException(request, response, null,
						new BusinessException(ErrorCode.UNAUTHENTICATED, "Bạn chưa đăng nhập hoặc phiên đã hết hạn")))
				.accessDeniedHandler((request, response, e) -> resolver.resolveException(request, response, null,
						new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"))));
		return http.build();
	}

	/** ADMIN có mọi quyền của STAFF. */
	@Bean
	static RoleHierarchy roleHierarchy() {
		return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_STAFF");
	}

}
