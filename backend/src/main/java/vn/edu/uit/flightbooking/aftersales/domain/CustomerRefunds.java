package vn.edu.uit.flightbooking.aftersales.domain;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.aftersales.domain.JourneyRules.Action;
import vn.edu.uit.flightbooking.booking.BookingApi;
import vn.edu.uit.flightbooking.booking.BookingApi.JourneyRef;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/** FR-50, FR-51: khách xem trước và gửi yêu cầu hoàn một chiều; kiểm tra 5 điều kiện của BR-51 ở {@link JourneyRules}. */
@Service
public class CustomerRefunds {

	private final JdbcClient jdbc;

	private final BookingApi bookings;

	private final JourneyRules rules;

	CustomerRefunds(JdbcClient jdbc, BookingApi bookings, JourneyRules rules) {
		this.jdbc = jdbc;
		this.bookings = bookings;
		this.rules = rules;
	}

	@Transactional(readOnly = true)
	public RefundQuote quote(long userId, String code, String direction) {
		JourneyRef journey = bookings.journey(userId, code, direction).orElseThrow(CustomerRefunds::notFound);
		rules.check(journey, Action.REFUND);
		return RefundQuote.of(journey.value(), journey.refundFee(), journey.seated());
	}

	/** FR-51, BR-61: chốt số tiền lúc gửi; booking và chiều chưa đổi cho tới khi Staff duyệt. */
	@Transactional
	public RefundCreatedResponse request(long userId, String code, String direction, String reason) {
		JourneyRef journey = bookings.lockJourney(userId, code, direction).orElseThrow(CustomerRefunds::notFound);
		rules.check(journey, Action.REFUND);
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
			throw JourneyRules.inProgress();
		}
	}

	private static BusinessException notFound() {
		return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy booking");
	}

}
