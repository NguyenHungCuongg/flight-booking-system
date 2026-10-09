package vn.edu.uit.flightbooking.common;

/**
 * Người đang gọi API, là principal lưu trong session sau khi đăng nhập.
 * Controller nhận bằng {@code @AuthenticationPrincipal CurrentUser me}.
 */
public record CurrentUser(long id, Role role) {
}
