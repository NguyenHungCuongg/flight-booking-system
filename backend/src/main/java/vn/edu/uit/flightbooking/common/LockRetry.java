package vn.edu.uit.flightbooking.common;

import java.util.function.Supplier;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

/** TDD §5.4: thử lại thao tác khoá nhiều dòng khi gặp deadlock. Đặt ngoài method {@code @Transactional}, nên mỗi lần thử là một transaction mới. */
@Component
public class LockRetry {

	@Retryable(includes = PessimisticLockingFailureException.class, maxRetries = 3, delay = 100)
	public <T> T run(Supplier<T> action) {
		return action.get();
	}

}

