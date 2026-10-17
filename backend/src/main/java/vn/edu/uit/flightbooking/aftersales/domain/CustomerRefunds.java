package vn.edu.uit.flightbooking.aftersales.domain;

import java.time.Duration;
import java.time.Instant;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.booking.BookingApi;
import vn.edu.uit.flightbooking.booking.BookingApi.JourneyRef;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;

/** FR-50, FR-51: khách xem trước và gửi yêu cầu hoàn một chiều; kiểm tra 5 điều kiện của BR-51. */
@Service
public class CustomerRefunds {

	private static final String OPEN = """
			SELECT EXISTS (SELECT 1 FROM refund_requests WHERE journey_id = :id AND status IN ('PENDING', 'APPROVED'))
			    OR EXISTS (SELECT 1 FROM reschedules WHERE journey_id = :id AND status = 'PENDING_PAYMENT')
			""";

	private final JdbcClient jdbc;

	private final BookingApi bookings;

	private final SettingsApi settings;

	CustomerRefunds(JdbcClient jdbc, BookingApi bookings, SettingsApi settings) {
		this.jdbc = jdbc;
		this.bookings = bookings;
		this.settings = settings;
	}

	@Transactional(readOnly = true)
	public RefundQuote quote(long userId, String code, String direction) {
		JourneyRef journey = bookings.journey(userId, code, direction).orElseThrow(CustomerRefunds::notFound);
		check(journey);
		return RefundQuote.of(journey.value(), journey.refundFee(), journey.seated());
	}

	/** FR-51, BR-61: chốt số tiền lúc gửi; booking và chiều chưa đổi cho tới khi Staff duyệt. */
	@Transactional
	public RefundCreatedResponse request(long userId, String code, String direction, String reason) {
		JourneyRef journey = bookings.lockJourney(userId, code, direction).orElseThrow(CustomerRefunds::notFound);
		check(journey);
		RefundQuote quote = RefundQuote.of(journey.value(), journey.refundFee(), journey.seated());
		try {
			long id = jdbc.sql("""
					INSERT INTO refund_requests (booking_id, journey_id, source, status, base_amount, fee_amount,
					                             refund_amount, reason, requested_by)
					VALUES (:bookingId, :journeyId, 'CUSTOMER', 'PENDING', :base, :fee, :refund, :reason, :userId)
					RETURNING id
					""")
				.param("bookingId", journey.bookingId())
				.param("journeyId", journey.journeyId())
				.param("base", quote.journeyValue())
				.param("fee", quote.fee())
				.param("refund", quote.refundAmount())
				.param("reason", reason.strip())
				.param("userId", userId)
				.query(Long.class)
				.single();
			return new RefundCreatedResponse(id, RefundStatus.PENDING, quote.refundAmount());
		}
		catch (DuplicateKeyException e) {
			throw inProgress();
		}
	}

	/** BR-51, theo đúng thứ tự điều kiện. */
	private void check(JourneyRef j) {
		if (!j.bookingIssued()) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "Chỉ hoàn được booking đã xuất vé");
		}
		if (!j.journeyActive()) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "Chiều này đã được hoàn");
		}
		if (!j.refundable()) {
			throw new BusinessException(ErrorCode.FARE_RULE_NOT_ALLOWED,
					"Gói giá của chiều này không cho phép hoàn vé");
		}
		int hours = settings.getInt(SettingKey.AFTERSALES_MIN_HOURS_BEFORE_DEPARTURE);
		if (j.firstDeparture() == null || j.firstDeparture().isBefore(Instant.now().plus(Duration.ofHours(hours)))) {
			throw new BusinessException(ErrorCode.DEADLINE_PASSED,
					"Đã quá hạn hoàn vé: phải yêu cầu trước giờ cất cánh ít nhất " + hours + " giờ");
		}
		if (jdbc.sql(OPEN).param("id", j.journeyId()).query(Boolean.class).single()) {
			throw inProgress();
		}
	}

	private static BusinessException inProgress() {
		return new BusinessException(ErrorCode.REQUEST_IN_PROGRESS,
				"Chiều này đang có yêu cầu hoàn hoặc đổi chuyến chưa xong");
	}

	private static BusinessException notFound() {
		return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy booking");
	}

}
