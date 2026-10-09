package vn.edu.uit.flightbooking.identity.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.common.CurrentUser;
import vn.edu.uit.flightbooking.identity.domain.AccountService;

/** Màn hình C-12 (FR-04). */
@RestController
@RequestMapping("/api/me")
class MeController {

	private final AccountService accounts;

	MeController(AccountService accounts) {
		this.accounts = accounts;
	}

	@GetMapping
	UserResponse me(@AuthenticationPrincipal CurrentUser me) {
		return UserResponse.of(accounts.get(me.id()));
	}

}
