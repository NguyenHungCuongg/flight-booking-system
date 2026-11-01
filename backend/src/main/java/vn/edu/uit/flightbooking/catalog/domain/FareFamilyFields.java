package vn.edu.uit.flightbooking.catalog.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import vn.edu.uit.flightbooking.catalog.CabinClass;

/**
 * Các trường Admin nhập cho một gói giá (FR-82), dùng làm body của cả POST lẫn PUT. Kiểu bọc (Integer, Boolean...)
 * để thiếu trường thì báo lỗi thay vì ngầm hiểu là 0 hoặc false.
 */
public record FareFamilyFields(@NotNull CabinClass cabinClass, @NotBlank @Size(max = 50) String name,
		@NotNull @Min(0) Integer carryOnKg, @NotNull @Min(0) Integer checkedBaggageKg, @NotNull Boolean refundable,
		@NotNull @Min(0) Long refundFee, @NotNull Boolean changeable, @NotNull @Min(0) Long changeFee,
		@NotNull Boolean active) {
}
