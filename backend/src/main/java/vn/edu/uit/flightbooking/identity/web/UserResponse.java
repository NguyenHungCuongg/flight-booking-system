package vn.edu.uit.flightbooking.identity.web;

import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.identity.domain.User;
import vn.edu.uit.flightbooking.identity.domain.UserStatus;

/** Tài khoản trả về cho đăng nhập, {@code /me} và màn hình A-08. Không bao giờ chứa hash mật khẩu. */
record UserResponse(long id, String email, String fullName, String phone, Role role, UserStatus status) {

	static UserResponse of(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), user.getRole(),
				user.getStatus());
	}

}
