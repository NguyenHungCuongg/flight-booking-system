package vn.edu.uit.flightbooking.aftersales.domain;

import java.time.Duration;
import java.time.Instant;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import vn.edu.uit.flightbooking.booking.BookingApi.JourneyRef;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;

/** BR-51, BR-70: 5 điều kiện để hoàn hoặc đổi một chiều, dùng chung cho hoàn vé và đổi chuyến. */
@Component
class JourneyRules {

	enum Action {

		REFUND("hoàn", "hoàn vé"), CHANGE("đổi", "đổi chuyến");

		private final String verb;

		private final String noun;

		Action(String verb, String noun) {
			this.verb = verb;
			this.noun = noun;
		}

	}

	private static final String OPEN = """
			SELECT EXISTS (SELECT 1 FROM refund_requests WHERE journey_id = :id AND status IN ('PENDING', 'APPROVED'))
			    OR EXISTS (SELECT 1 FROM reschedules WHERE journey_id = :id AND status = 'PENDING_PAYMENT')
			""";

	private final JdbcClient jdbc;

	private final SettingsApi settings;

	JourneyRules(JdbcClient jdbc, SettingsApi settings) {
		this.jdbc = jdbc;
		this.settings = settings;
	}

	/** Theo đúng thứ tự điều kiện; điều kiện 3 đọc {@code refundable} khi hoàn, {@code changeable} khi đổi. */
	void check(JourneyRef j, Action action) {
		if (!j.bookingIssued()) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "Chỉ " + action.verb + " được booking đã xuất vé");
		}
		if (!j.journeyActive()) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "Chiều này đã được hoàn");
		}
		if (!(action == Action.REFUND ? j.refundable() : j.changeable())) {
			throw new BusinessException(ErrorCode.FARE_RULE_NOT_ALLOWED,
					"Gói giá của chiều này không cho phép " + action.noun);
		}
		int hours = settings.getInt(SettingKey.AFTERSALES_MIN_HOURS_BEFORE_DEPARTURE);
		if (j.firstDeparture() == null || j.firstDeparture().isBefore(Instant.now().plus(Duration.ofHours(hours)))) {
			throw new BusinessException(ErrorCode.DEADLINE_PASSED,
					"Đã quá hạn " + action.noun + ": phải yêu cầu trước giờ cất cánh ít nhất " + hours + " giờ");
		}
		if (jdbc.sql(OPEN).param("id", j.journeyId()).query(Boolean.class).single()) {
			throw inProgress();
		}
	}

	static BusinessException inProgress() {
		return new BusinessException(ErrorCode.REQUEST_IN_PROGRESS,
				"Chiều này đang có yêu cầu hoàn hoặc đổi chuyến chưa xong");
	}

}
