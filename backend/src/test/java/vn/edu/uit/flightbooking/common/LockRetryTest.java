package vn.edu.uit.flightbooking.common;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;

import vn.edu.uit.flightbooking.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class LockRetryTest {

	@Autowired
	LockRetry retry;

	/** TDD §5.4: lỗi khoá (deadlock) được thử lại, lần thứ ba thành công. */
	@Test
	void retriesLockFailures() {
		AtomicInteger calls = new AtomicInteger();
		String result = retry.run(() -> {
			if (calls.incrementAndGet() < 3) {
				throw new CannotAcquireLockException("deadlock");
			}
			return "ok";
		});
		assertThat(result).isEqualTo("ok");
		assertThat(calls).hasValue(3);
	}

	/** Tối đa 3 lần thử lại: tổng cộng 4 lần gọi, rồi ném lỗi gốc. */
	@Test
	void givesUpAfterThreeRetries() {
		AtomicInteger calls = new AtomicInteger();
		assertThatThrownBy(() -> retry.run(() -> {
			calls.incrementAndGet();
			throw new CannotAcquireLockException("deadlock");
		})).isInstanceOf(CannotAcquireLockException.class);
		assertThat(calls).hasValue(4);
	}

	/** Lỗi nghiệp vụ không được thử lại. */
	@Test
	void doesNotRetryBusinessErrors() {
		AtomicInteger calls = new AtomicInteger();
		assertThatThrownBy(() -> retry.run(() -> {
			calls.incrementAndGet();
			throw new BusinessException(ErrorCode.INVALID_STATE, "x");
		})).isInstanceOf(BusinessException.class);
		assertThat(calls).hasValue(1);
	}

}

