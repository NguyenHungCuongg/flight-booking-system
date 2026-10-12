package vn.edu.uit.flightbooking.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/**
 * Q-14, Q-15. Không dùng {@code @Transactional}: mỗi luồng cần transaction và connection riêng, và mỗi lời gọi
 * {@code check} cần persistence context mới để thấy {@code used_count} vừa đổi. Voucher được commit thật và xoá ở
 * {@link #cleanUp()}.
 */
@IntegrationTest
class VoucherReservationTest {

	@Autowired
	VoucherApi vouchers;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM vouchers WHERE code LIKE 'QV%'").update();
	}

	boolean isExhausted(BusinessException e) {
		return e.code() == ErrorCode.VOUCHER_INVALID && "EXHAUSTED".equals(e.properties().get("reason"));
	}

	/** Tiền đề của T16 ở mức bộ đếm: 20 luồng cùng giữ lượt trên voucher còn 5 lượt. */
	@Test
	void twentyConcurrentReservesNeverExceedLimit() throws Exception {
		long id = TestVouchers.insert(jdbc, "QVRACE", Map.of("usage_limit", 5));
		int threads = 20;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger reserved = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();
		for (int i = 0; i < threads; i++) {
			pool.submit(() -> {
				start.await();
				try {
					vouchers.reserve(id);
					reserved.incrementAndGet();
				}
				catch (BusinessException e) {
					if (isExhausted(e)) {
						rejected.incrementAndGet();
					}
				}
				return null;
			});
		}
		start.countDown();
		pool.shutdown();
		assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

		assertThat(reserved).hasValue(5);
		assertThat(rejected).hasValue(15);
		assertThat(TestVouchers.usedCount(jdbc, id)).isEqualTo(5);
	}

	@Test
	void releaseReopensAnExhaustedVoucher() {
		long id = TestVouchers.insert(jdbc, "QVLAST", Map.of("usage_limit", 1));
		vouchers.reserve(id);

		assertThatThrownBy(() -> vouchers.check("QVLAST", 1_000_000)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(isExhausted(e)).isTrue());
		vouchers.release(id);

		assertThat(vouchers.check("QVLAST", 1_000_000).voucherId()).isEqualTo(id);
	}

	@Test
	void releaseNeverGoesBelowZero() {
		long id = TestVouchers.insert(jdbc, "QVZERO", Map.of());

		vouchers.release(id);

		assertThat(TestVouchers.usedCount(jdbc, id)).isZero();
	}

	@Test
	void unlimitedVoucherAlwaysReserves() {
		long id = TestVouchers.insert(jdbc, "QVFREE", Map.of("used_count", 1000));

		vouchers.reserve(id);

		assertThat(TestVouchers.usedCount(jdbc, id)).isEqualTo(1001);
	}

}
