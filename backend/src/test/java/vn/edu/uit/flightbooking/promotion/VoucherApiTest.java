package vn.edu.uit.flightbooking.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.OffsetDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/** BR-30 (điều kiện 1–4), BR-31 qua {@link VoucherApi#check}. Voucher tạo bằng SQL, mã QV… (seed không nạp khi test). */
@IntegrationTest
@Transactional
class VoucherApiTest {

	@Autowired
	VoucherApi vouchers;

	@Autowired
	JdbcClient jdbc;

	/** Gọi {@code check} và trả {@code reason} của VOUCHER_INVALID; test hỏng nếu không có lỗi hoặc sai loại lỗi. */
	String reasonOf(String code, long subtotal) {
		Throwable thrown = catchThrowable(() -> vouchers.check(code, subtotal));
		assertThat(thrown).isInstanceOf(BusinessException.class);
		BusinessException e = (BusinessException) thrown;
		assertThat(e.code()).isEqualTo(ErrorCode.VOUCHER_INVALID);
		return (String) e.properties().get("reason");
	}

	@Test
	void usableVoucherReturnsIdCodeAndDiscountWithoutReserving() {
		long id = TestVouchers.insert(jdbc, "QVWELCOME", Map.of("max_discount", 200_000));

		// Ví dụ TDD §8.8: 10% của 6.048.000 vượt mức tối đa nên giảm 200.000.
		assertThat(vouchers.check("QVWELCOME", 6_048_000))
			.isEqualTo(new VoucherApi.Applied(id, "QVWELCOME", 200_000));
		assertThat(TestVouchers.usedCount(jdbc, id)).isZero();
	}

	@Test
	void codeIsMatchedIgnoringCaseAndSpaces() {
		TestVouchers.insert(jdbc, "QVLOWER", Map.of());

		assertThat(vouchers.check("  qvlower ", 1_000_000).code()).isEqualTo("QVLOWER");
	}

	@Test
	void eachRejectionReportsItsReason() {
		OffsetDateTime now = OffsetDateTime.now();
		TestVouchers.insert(jdbc, "QVOFF", Map.of("active", false));
		TestVouchers.insert(jdbc, "QVFUTURE",
				Map.of("valid_from", now.plusDays(1), "valid_to", now.plusDays(2)));
		TestVouchers.insert(jdbc, "QVPAST", Map.of("valid_from", now.minusDays(2), "valid_to", now.minusDays(1)));
		TestVouchers.insert(jdbc, "QVFULL", Map.of("usage_limit", 2, "used_count", 2));
		TestVouchers.insert(jdbc, "QVMIN", Map.of("min_order_amount", 1_000_000));

		assertThat(reasonOf("QVNONE", 1_000_000)).isEqualTo("NOT_FOUND");
		assertThat(reasonOf("   ", 1_000_000)).isEqualTo("NOT_FOUND");
		assertThat(reasonOf("QVOFF", 1_000_000)).isEqualTo("INACTIVE");
		assertThat(reasonOf("QVFUTURE", 1_000_000)).isEqualTo("NOT_STARTED");
		assertThat(reasonOf("QVPAST", 1_000_000)).isEqualTo("EXPIRED");
		assertThat(reasonOf("QVFULL", 1_000_000)).isEqualTo("EXHAUSTED");
		assertThat(reasonOf("QVMIN", 999_999)).isEqualTo("MIN_ORDER_NOT_MET");
		assertThat(vouchers.check("QVMIN", 1_000_000).discount()).isEqualTo(100_000);
	}

}
