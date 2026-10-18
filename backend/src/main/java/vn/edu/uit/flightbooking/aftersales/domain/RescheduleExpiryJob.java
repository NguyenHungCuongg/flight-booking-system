package vn.edu.uit.flightbooking.aftersales.domain;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * BR-75, T11: mỗi phút huỷ các yêu cầu đổi chuyến đã quá hạn giữ chỗ hơn 5 phút. Mỗi yêu cầu chạy trong một
 * transaction riêng (gọi qua proxy của {@link Reschedules}), nên lỗi ở một yêu cầu không chặn các yêu cầu sau.
 */
@Component
class RescheduleExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(RescheduleExpiryJob.class);

	private final Reschedules reschedules;

	RescheduleExpiryJob(Reschedules reschedules) {
		this.reschedules = reschedules;
	}

	@Scheduled(fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
	void run() {
		expireOverdue();
	}

	/** Trả số yêu cầu đã chuyển sang EXPIRED. */
	int expireOverdue() {
		int expired = 0;
		for (long id : reschedules.overdue()) {
			try {
				if (reschedules.expire(id)) {
					expired++;
				}
			}
			catch (RuntimeException e) {
				log.error("Không huỷ được yêu cầu đổi chuyến quá hạn {}", id, e);
			}
		}
		return expired;
	}

}
