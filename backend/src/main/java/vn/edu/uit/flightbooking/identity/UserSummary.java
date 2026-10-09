package vn.edu.uit.flightbooking.identity;

/** Thông tin tóm tắt của một tài khoản cho module khác (VD notification lấy email người nhận). */
public record UserSummary(long id, String email, String fullName) {
}
