package vn.edu.uit.flightbooking.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.promotion.domain.DiscountType;
import vn.edu.uit.flightbooking.promotion.domain.VoucherFields;
import vn.edu.uit.flightbooking.promotion.domain.VoucherService;

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
	VoucherService voucherService;

	@Autowired
	TransactionTemplate tx;

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

	/**
	 * Review Focus 4: Admin hạ {@code usageLimit} xuống đúng số lượt đã dùng trong lúc một booking đã giữ thêm lượt nhưng
	 * chưa commit. {@code findForUpdate} chờ booking commit rồi mới so, nên Admin nhận 400 theo trường; không có khoá thì
	 * phép so thấy số cũ, UPDATE chờ rồi vi phạm CHECK (used_count <= usage_limit) và Admin nhận 500.
	 */
	@Test
	void adminEditWaitsForUncommittedReserveInsteadOfBreakingCheck() throws Exception {
		long id = TestVouchers.insert(jdbc, "QVLOCK", Map.of("usage_limit", 10, "used_count", 5));
		VoucherFields lowerLimit = new VoucherFields("QVLOCK", "Voucher thử", DiscountType.PERCENT, 10L, null, 0L,
				OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(30), 5, true);
		CountDownLatch reserved = new CountDownLatch(1);
		CountDownLatch commit = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		Future<?> booking = pool.submit(() -> tx.execute(status -> {
			vouchers.reserve(id); // used_count = 6, dòng bị khoá, chưa commit
			reserved.countDown();
			try {
				commit.await();
			}
			catch (InterruptedException e) {
				throw new IllegalStateException(e);
			}
			return null;
		}));
		assertThat(reserved.await(10, TimeUnit.SECONDS)).isTrue();

		Future<Throwable> admin = pool.submit(() -> catchThrowable(() -> voucherService.update(id, lowerLimit)));
		Thread.sleep(500); // để Admin kịp chạm vào dòng bị khoá; ngủ ngắn chỉ làm test dễ qua nhầm, không làm nó đỏ nhầm
		commit.countDown();
		booking.get(10, TimeUnit.SECONDS);
		pool.shutdown();

		assertThat(admin.get(10, TimeUnit.SECONDS)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.properties().toString()).contains("usageLimit"));
		assertThat(TestVouchers.usedCount(jdbc, id)).isEqualTo(6);
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
