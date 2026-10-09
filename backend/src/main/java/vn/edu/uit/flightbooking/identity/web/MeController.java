package vn.edu.uit.flightbooking.identity.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.common.CurrentUser;
import vn.edu.uit.flightbooking.identity.domain.AccountService;

/** Màn hình C-12 (FR-04). */
@RestController
@RequestMapping("/api/me")
class MeController {

	record UpdateProfileRequest(@NotBlank @Size(max = 100) String fullName, @NotBlank @Size(max = 20) String phone) {
	}

	record ChangePasswordRequest(@NotBlank String currentPassword, @Password String newPassword) {
	}

	private final AccountService accounts;

	MeController(AccountService accounts) {
		this.accounts = accounts;
	}

	@GetMapping
	UserResponse me(@AuthenticationPrincipal CurrentUser me) {
		return UserResponse.of(accounts.get(me.id()));
	}

	@PutMapping
	UserResponse updateProfile(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody UpdateProfileRequest body) {
		return UserResponse.of(accounts.updateProfile(me.id(), body.fullName(), body.phone()));
	}

	@PutMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changePassword(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody ChangePasswordRequest body) {
		accounts.changePassword(me.id(), body.currentPassword(), body.newPassword());
	}

}
