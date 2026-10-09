package vn.edu.uit.flightbooking.identity.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.common.CurrentUser;
import vn.edu.uit.flightbooking.identity.domain.AccountService;
import vn.edu.uit.flightbooking.identity.domain.User;

/** Đăng ký, đăng nhập, đăng xuất, quên mật khẩu (FR-01–03, TDD §4.1, §4.4). */
@RestController
@RequestMapping("/api/auth")
class AuthController {

	record RegisterRequest(@NotBlank @Email @Size(max = 255) String email, @Password String password,
			@NotBlank @Size(max = 100) String fullName, @NotBlank @Size(max = 20) String phone) {
	}

	record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

	private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

	private final AccountService accounts;

	AuthController(AccountService accounts) {
		this.accounts = accounts;
	}

	/** FR-01: đăng ký xong thì đăng nhập luôn. */
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	UserResponse register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		User user = accounts.register(body.email(), body.password(), body.fullName(), body.phone());
		signIn(user, request, response);
		return UserResponse.of(user);
	}

	@PostMapping("/login")
	UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		User user = accounts.authenticate(body.email(), body.password());
		signIn(user, request, response);
		return UserResponse.of(user);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		new SecurityContextLogoutHandler().logout(request, response, authentication);
	}

	/** TDD §4.1: đổi session id (chống session fixation) rồi lưu SecurityContext vào HttpSession. */
	private void signIn(User user, HttpServletRequest request, HttpServletResponse response) {
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		var principal = new CurrentUser(user.getId(), user.getRole());
		var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
		SecurityContextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
	}

}
