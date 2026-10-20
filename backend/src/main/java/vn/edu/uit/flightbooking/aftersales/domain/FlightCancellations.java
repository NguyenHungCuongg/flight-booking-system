package vn.edu.uit.flightbooking.aftersales.domain;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.booking.BookingApi;
import vn.edu.uit.flightbooking.booking.BookingApi.JourneyRef;
import vn.edu.uit.flightbooking.flight.FlightCancelled;

/**
 * BR-84 (b), (c), TDD §6.8: chạy đồng bộ trong transaction huỷ chuyến, sau listener của booking ({@code @Order(2)}),
 * nên mọi booking bị ảnh hưởng đã bị khoá. Huỷ yêu cầu đổi trước, rồi mới tạo yêu cầu hoàn cho chiều.
 */
@Component
class FlightCancellations {

	private final JdbcClient jdbc;

	private final BookingApi bookings;

	FlightCancellations(JdbcClient jdbc, BookingApi bookings) {
		this.jdbc = jdbc;
		this.bookings = bookings;
	}

	@EventListener
	@Order(2)
	@Transactional(propagation = Propagation.MANDATORY)
	void on(FlightCancelled e) {
		List<Long> rescheduleIds = jdbc.sql("""
				SELECT r.id FROM reschedules r
				WHERE r.status = 'PENDING_PAYMENT'
				  AND EXISTS (SELECT 1 FROM booking_segments s
				              WHERE s.flight_id = :flightId
				                AND (s.reschedule_id = r.id AND s.status = 'PENDING_CHANGE'
				                     OR s.journey_id = r.journey_id AND s.status = 'ACTIVE'))
				ORDER BY r.id
				FOR UPDATE
				""")
			.param("flightId", e.flightId())
			.query(Long.class)
			.list();

		for (Long id : rescheduleIds) {
			bookings.cancelChange(id);
			jdbc.sql("UPDATE reschedules SET status = 'CANCELLED' WHERE id = ?").param(id).update();
		}

		for (JourneyRef j : bookings.issuedJourneysOn(e.flightId())) {
			int updated = jdbc.sql("""
					UPDATE refund_requests
					SET source = 'FLIGHT_CANCELLED', base_amount = :value, fee_amount = 0, refund_amount = :value
					WHERE journey_id = :journeyId AND status = 'PENDING'
					""")
				.param("value", j.value())
				.param("journeyId", j.journeyId())
				.update();

			if (updated == 0) {
				jdbc.sql("""
						INSERT INTO refund_requests (booking_id, journey_id, source, status, base_amount, fee_amount, refund_amount)
						VALUES (:bookingId, :journeyId, 'FLIGHT_CANCELLED', 'PENDING', :value, 0, :value)
						""")
					.param("bookingId", j.bookingId())
					.param("journeyId", j.journeyId())
					.param("value", j.value())
					.update();
			}
		}
	}

}

