package vn.edu.uit.flightbooking.promotion.domain;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Các trường Admin nhập cho một voucher (FR-100), dùng làm body của cả POST lẫn PUT. {@code code} không đổi được
 * sau khi tạo: PUT phải gửi đúng mã hiện có. Kiểu bọc (Long, Boolean...) để thiếu trường thì báo lỗi thay vì ngầm
 * hiểu là 0 hoặc false; {@code maxDiscount} và {@code usageLimit} là {@code null} khi không giới hạn.
 */
public record VoucherFields(
		@NotNull @Pattern(regexp = "[A-Z0-9]{3,30}", message = "Mã gồm 3–30 ký tự in hoa hoặc số") String code,
		@NotBlank @Size(max = 255) String description, @NotNull DiscountType discountType,
		@NotNull @Min(value = 1, message = "Giá trị giảm phải lớn hơn 0") Long discountValue,
		@Min(value = 1, message = "Mức giảm tối đa phải lớn hơn 0") Long maxDiscount,
		@NotNull @Min(value = 0, message = "Đơn tối thiểu không được âm") Long minOrderAmount,
		@NotNull OffsetDateTime validFrom, @NotNull OffsetDateTime validTo,
		@Min(value = 1, message = "Số lượt phải lớn hơn 0") Integer usageLimit, @NotNull Boolean active) {
}
