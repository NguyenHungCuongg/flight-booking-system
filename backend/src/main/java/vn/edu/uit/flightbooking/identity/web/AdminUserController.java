package vn.edu.uit.flightbooking.identity.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.common.CurrentUser;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.identity.domain.AccountService;
import vn.edu.uit.flightbooking.identity.domain.UserStatus;

/** Màn hình A-08 (FR-110). */
@RestController
@RequestMapping("/api/admin/users")
class AdminUserController {

	record CreateUserRequest(@NotBlank @Email @Size(max = 255) String email, @Password String password,
			@NotBlank @Size(max = 100) String fullName, @NotBlank @Size(max = 20) String phone, @NotNull Role role) {
	}

	record UpdateStatusRequest(@NotNull UserStatus status) {
	}

	private final AccountService accounts;

	AdminUserController(AccountService accounts) {
		this.accounts = accounts;
	}

	@GetMapping
	PagedModel<UserResponse> search(@RequestParam(defaultValue = "") String q,
			@RequestParam(required = false) Role role, @PageableDefault(size = 20) Pageable pageable) {
		return new PagedModel<>(accounts.search(q, role, pageable).map(UserResponse::of));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	UserResponse create(@Valid @RequestBody CreateUserRequest body) {
		return UserResponse.of(accounts.createOperator(body.email(), body.password(), body.fullName(), body.phone(),
				body.role()));
	}

	@PatchMapping("/{id}/status")
	UserResponse updateStatus(@PathVariable long id, @Valid @RequestBody UpdateStatusRequest body,
			@AuthenticationPrincipal CurrentUser me) {
		return UserResponse.of(accounts.setStatus(me.id(), id, body.status()));
	}

}
