package vn.edu.uit.flightbooking.promotion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.promotion.VoucherRejection;

/** BR-30 (điều kiện 1–4) và BR-31: logic thuần, không cần Spring (TDD §11.1). */
class VoucherRulesTest {

	static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");

	/** Đang hiệu lực từ 1 ngày trước đến 30 ngày sau, đang kích hoạt, không giới hạn lượt, không có đơn tối thiểu. */
	static Voucher voucher(DiscountType type, long value) {
		Voucher v = new Voucher();
		v.setCode("QTEST");
		v.setDiscountType(type);
		v.setDiscountValue(value);
		v.setValidFrom(NOW.minus(Duration.ofDays(1)));
		v.setValidTo(NOW.plus(Duration.ofDays(30)));
		return v;
	}

	@Test
	void percentDiscountRoundsDownAndRespectsCap() {
		Voucher welcome = voucher(DiscountType.PERCENT, 10);
		welcome.setMaxDiscount(200_000L);

		// Ví dụ TDD §8.8: 10% của 6.048.000 là 604.800, vượt mức tối đa nên lấy 200.000.
		assertThat(welcome.discountFor(6_048_000)).isEqualTo(200_000);
		assertThat(welcome.discountFor(1_500_000)).isEqualTo(150_000);
		// 10% của 1.009 là 100,9: làm tròn xuống.
		assertThat(welcome.discountFor(1_009)).isEqualTo(100);
		assertThat(voucher(DiscountType.PERCENT, 10).discountFor(6_048_000)).isEqualTo(604_800);
	}

	@Test
	void discountNeverExceedsSubtotal() {
		Voucher fixed = voucher(DiscountType.FIXED, 100_000);

		assertThat(fixed.discountFor(1_000_000)).isEqualTo(100_000);
		assertThat(fixed.discountFor(60_000)).isEqualTo(60_000);
		assertThat(voucher(DiscountType.PERCENT, 100).discountFor(777_000)).isEqualTo(777_000);
	}

	@Test
	void validityWindowIncludesStartAndExcludesEnd() {
		Voucher v = voucher(DiscountType.FIXED, 1);
		v.setValidFrom(NOW);
		v.setValidTo(NOW.plusSeconds(60));

		assertThat(v.rejection(1, NOW.minusMillis(1))).contains(VoucherRejection.NOT_STARTED);
		assertThat(v.rejection(1, NOW)).isEmpty();
		assertThat(v.rejection(1, NOW.plusSeconds(60).minusMillis(1))).isEmpty();
		assertThat(v.rejection(1, NOW.plusSeconds(60))).contains(VoucherRejection.EXPIRED);
	}

	@Test
	void usageLimitAndMinimumOrderAreInclusiveBoundaries() {
		Voucher v = voucher(DiscountType.FIXED, 100_000);
		v.setUsageLimit(100);
		v.setMinOrderAmount(1_000_000);

		v.setUsedCount(99);
		assertThat(v.rejection(1_000_000, NOW)).isEmpty();
		assertThat(v.rejection(999_999, NOW)).contains(VoucherRejection.MIN_ORDER_NOT_MET);
		v.setUsedCount(100);
		assertThat(v.rejection(1_000_000, NOW)).contains(VoucherRejection.EXHAUSTED);

		// Không giới hạn lượt: used_count lớn đến đâu cũng còn lượt.
		v.setUsageLimit(null);
		v.setUsedCount(1_000_000);
		assertThat(v.rejection(1_000_000, NOW)).isEmpty();
	}

	/** Thứ tự theo TDD §9: INACTIVE, NOT_STARTED, EXPIRED, EXHAUSTED, MIN_ORDER_NOT_MET. */
	@Test
	void firstFailingConditionWins() {
		Voucher v = voucher(DiscountType.FIXED, 100_000);
		v.setActive(false);
		v.setValidTo(NOW.minusSeconds(1));
		v.setUsageLimit(1);
		v.setUsedCount(1);
		v.setMinOrderAmount(1_000_000);

		assertThat(v.rejection(1, NOW)).contains(VoucherRejection.INACTIVE);
		v.setActive(true);
		assertThat(v.rejection(1, NOW)).contains(VoucherRejection.EXPIRED);
		v.setValidTo(NOW.plusSeconds(1));
		assertThat(v.rejection(1, NOW)).contains(VoucherRejection.EXHAUSTED);
		v.setUsedCount(0);
		assertThat(v.rejection(1, NOW)).contains(VoucherRejection.MIN_ORDER_NOT_MET);
	}

	@Test
	void rejectionBecomesVoucherInvalidWithReason() {
		BusinessException e = VoucherRejection.ALREADY_USED.toException();

		assertThat(e.code()).isEqualTo(ErrorCode.VOUCHER_INVALID);
		assertThat(e.properties()).containsEntry("reason", "ALREADY_USED");
		assertThat(e.getMessage()).isNotBlank();
	}

}
