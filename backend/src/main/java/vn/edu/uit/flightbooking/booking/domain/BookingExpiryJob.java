package vn.edu.uit.flightbooking.booking.domain;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TDD §6.5, SD-02: mỗi phút huỷ các booking đã quá hạn giữ chỗ hơn 5 phút. Mỗi booking chạy trong một transaction
 * riêng (gọi qua proxy của {@link BookingCancellation}), nên lỗi ở một booking không chặn các booking sau. Backend
 * chạy một instance nên không cần khoá phân tán (TDD §6.5).
 */
@Component
class BookingExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(BookingExpiryJob.class);

	private final BookingCancellation bookings;

	BookingExpiryJob(BookingCancellation bookings) {
		this.bookings = bookings;
	}

	@Scheduled(fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
	void run() {
		expireOverdue();
	}

	/** Trả số booking đã chuyển sang EXPIRED. */
	int expireOverdue() {
		int expired = 0;
		for (long id : bookings.overdue()) {
			try {
				if (bookings.expire(id)) {
					expired++;
				}
			}
			catch (RuntimeException e) {
				log.error("Không huỷ được booking quá hạn {}", id, e);
			}
		}
		return expired;
	}

}
