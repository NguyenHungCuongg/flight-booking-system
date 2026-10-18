package vn.edu.uit.flightbooking.booking.domain;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedSegment;
import vn.edu.uit.flightbooking.flight.SeatInventory;

/**
 * Phần ghi của đổi chuyến (Plan 15): giữ chỗ chặng mới, hoàn tất (thay chặng cũ) và huỷ. Bên gọi (aftersales) đã
 * khoá booking. Trả ghế dùng {@link BookingCancellation#releaseSeats} dạng static, không đi qua proxy giao dịch.
 */
@Service
public class BookingChanges {

	private record Passenger(long id, PassengerType type) {
	}

	/** Chặng ACTIVE của chiều thành REPLACED (trước khi kích hoạt chặng mới, vì {@code uq_segments_active_seq}); trả về chuyến và hạng ghế để trả ghế. */
	private static final String REPLACE_SEGMENTS = """
			UPDATE booking_segments s
			SET status = 'REPLACED'
			FROM booking_journeys j
			WHERE j.id = s.journey_id AND j.id = :journeyId AND s.status = 'ACTIVE'
			RETURNING s.flight_id, j.cabin_class
			""";

	private static final String CANCEL_PENDING_SEGMENTS = """
			UPDATE booking_segments s
			SET status = 'CANCELLED'
			FROM booking_journeys j
			WHERE j.id = s.journey_id AND s.reschedule_id = :rescheduleId AND s.status = 'PENDING_CHANGE'
			RETURNING s.flight_id, j.cabin_class
			""";

	private static final String INSERT_SEGMENT = """
			INSERT INTO booking_segments (journey_id, flight_id, seq, status, adult_price, reschedule_id)
			VALUES (:journeyId, :flightId, :seq, 'PENDING_CHANGE', :adultPrice, :rescheduleId)
			RETURNING id
			""";

	private final SeatInventory seats;

	private final JdbcClient jdbc;

	BookingChanges(SeatInventory seats, JdbcClient jdbc) {
		this.seats = seats;
		this.jdbc = jdbc;
	}

	/**
	 * BR-73: giữ ghế các chuyến mới (theo {@code flightId} tăng dần, Q-01), chèn chặng PENDING_CHANGE và vé chưa có số
	 * cho mọi khách, giá theo tỉ lệ khách (BR-21).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void hold(long journeyId, long rescheduleId, List<PricedSegment> segments, int childPercent,
			int infantPercent) {
		record Journey(long bookingId, CabinClass cabin) {
		}
		Journey journey = jdbc.sql("SELECT booking_id, cabin_class FROM booking_journeys WHERE id = ?")
			.param(journeyId)
			.query((rs, n) -> new Journey(rs.getLong("booking_id"), CabinClass.valueOf(rs.getString("cabin_class"))))
			.single();
		List<Passenger> passengers = jdbc.sql("SELECT id, type FROM passengers WHERE booking_id = ? ORDER BY id")
			.param(journey.bookingId())
			.query((rs, n) -> new Passenger(rs.getLong("id"), PassengerType.valueOf(rs.getString("type"))))
			.list();
		int seated = (int) passengers.stream().filter(p -> p.type() != PassengerType.INFANT).count();
		segments.stream()
			.map(PricedSegment::flightId)
			.sorted()
			.forEach(flightId -> seats.hold(flightId, journey.cabin(), seated));
		for (int seq = 1; seq <= segments.size(); seq++) {
			PricedSegment segment = segments.get(seq - 1);
			long segmentId = jdbc.sql(INSERT_SEGMENT)
				.param("journeyId", journeyId)
				.param("flightId", segment.flightId())
				.param("seq", seq)
				.param("adultPrice", segment.adultPrice())
				.param("rescheduleId", rescheduleId)
				.query(Long.class)
				.single();
			for (Passenger p : passengers) {
				jdbc.sql(BookingService.INSERT_TICKET)
					.param("segmentId", segmentId)
					.param("passengerId", p.id())
					.param("fareAmount", Pricing.ticketPrice(segment.adultPrice(), p.type(), childPercent, infantPercent))
					.update();
			}
		}
	}

	/** BR-74: chặng cũ REPLACED và trả ghế, chặng mới ACTIVE, cấp số cho vé mới (vé cũ giữ số). */
	@Transactional(propagation = Propagation.MANDATORY)
	public void complete(long journeyId, long rescheduleId) {
		long bookingId = jdbc.sql("SELECT booking_id FROM booking_journeys WHERE id = ?")
			.param(journeyId)
			.query(Long.class)
			.single();
		BookingCancellation.releaseSeats(jdbc, seats, bookingId, jdbc.sql(REPLACE_SEGMENTS)
			.param("journeyId", journeyId)
			.query(BookingCancellation::seat)
			.list());
		jdbc.sql("UPDATE booking_segments SET status = 'ACTIVE' WHERE reschedule_id = :rescheduleId AND status = 'PENDING_CHANGE'")
			.param("rescheduleId", rescheduleId)
			.update();
		jdbc.sql(BookingService.ISSUE_TICKETS).param("bookingId", bookingId).update();
	}

	/** BR-75: chặng mới CANCELLED và trả ghế; chặng cũ giữ nguyên. Không có chặng chờ thì không làm gì. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void cancel(long rescheduleId) {
		List<BookingCancellation.Seat> released = jdbc.sql(CANCEL_PENDING_SEGMENTS)
			.param("rescheduleId", rescheduleId)
			.query(BookingCancellation::seat)
			.list();
		if (!released.isEmpty()) {
			long bookingId = jdbc.sql("SELECT booking_id FROM reschedules WHERE id = ?")
				.param(rescheduleId)
				.query(Long.class)
				.single();
			BookingCancellation.releaseSeats(jdbc, seats, bookingId, released);
		}
	}

}
