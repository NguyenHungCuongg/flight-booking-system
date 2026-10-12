package vn.edu.uit.flightbooking.promotion.web;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.promotion.domain.DiscountType;
import vn.edu.uit.flightbooking.promotion.domain.Voucher;
import vn.edu.uit.flightbooking.promotion.domain.VoucherFields;
import vn.edu.uit.flightbooking.promotion.domain.VoucherService;

/** Màn hình A-07 (FR-100). Không có DELETE: voucher chỉ ngừng kích hoạt. */
@RestController
@RequestMapping("/api/admin/vouchers")
class AdminVoucherController {

	private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

	/** {@code maxDiscount} và {@code usageLimit} là {@code null} khi không giới hạn. */
	record VoucherResponse(long id, String code, String description, DiscountType discountType, long discountValue,
			Long maxDiscount, long minOrderAmount, OffsetDateTime validFrom, OffsetDateTime validTo,
			Integer usageLimit, int usedCount, boolean active) {

		static VoucherResponse of(Voucher v) {
			return new VoucherResponse(v.getId(), v.getCode(), v.getDescription(), v.getDiscountType(),
					v.getDiscountValue(), v.getMaxDiscount(), v.getMinOrderAmount(),
					v.getValidFrom().atZone(VIETNAM).toOffsetDateTime(), v.getValidTo().atZone(VIETNAM).toOffsetDateTime(),
					v.getUsageLimit(), v.getUsedCount(), v.isActive());
		}

	}

	private final VoucherService vouchers;

	AdminVoucherController(VoucherService vouchers) {
		this.vouchers = vouchers;
	}

	@GetMapping
	List<VoucherResponse> listVouchers() {
		return vouchers.list().stream().map(VoucherResponse::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	VoucherResponse createVoucher(@Valid @RequestBody VoucherFields body) {
		return VoucherResponse.of(vouchers.create(body));
	}

	@PutMapping("/{id}")
	VoucherResponse updateVoucher(@PathVariable long id, @Valid @RequestBody VoucherFields body) {
		return VoucherResponse.of(vouchers.update(id, body));
	}

}
