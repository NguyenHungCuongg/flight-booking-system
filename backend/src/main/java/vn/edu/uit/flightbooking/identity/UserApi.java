package vn.edu.uit.flightbooking.identity;

import org.springframework.stereotype.Service;

import vn.edu.uit.flightbooking.identity.domain.AccountService;
import vn.edu.uit.flightbooking.identity.domain.User;

/** API công khai của identity (TDD §3.2). */
@Service
public class UserApi {

	private final AccountService accounts;

	UserApi(AccountService accounts) {
		this.accounts = accounts;
	}

	/** Không có tài khoản thì báo RESOURCE_NOT_FOUND. */
	public UserSummary get(long id) {
		User user = accounts.get(id);
		return new UserSummary(user.getId(), user.getEmail(), user.getFullName());
	}

}
