package vn.edu.uit.flightbooking.booking;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.booking.domain.BookingCancellation;

/**
 * API công khai của booking (TDD §3.2). Có dữ liệu cho email vé (FR-31, FR-73) và các hàm đọc, khoá, hoàn một chiều
 * cho hậu mãi (Plan 14). Plan 15 thêm các hàm đổi chuyến.
 */
@Service
public class BookingApi {

	/** Giờ theo múi giờ sân bay tương ứng (TDD §5.1). */
	public record IssuedTicket(String passengerName, String flightNumber, String departureAirport,
			OffsetDateTime departureTime, String arrivalAirport, OffsetDateTime arrivalTime, String ticketNumber) {
	}

	/** {@code tickets}: vé đã có số của các chặng ACTIVE, theo chiều, thứ tự chặng rồi thứ tự khách. */
	public record IssuedBooking(String code, String contactName, String contactEmail, long totalAmount,
			List<IssuedTicket> tickets) {
	}

	/**
	 * Một chiều của booking cho hậu mãi. {@code refundFee}: phí mỗi khách chiếm ghế (snapshot); {@code seated}: số khách
	 * không phải em bé; {@code firstDeparture}: giờ cất cánh sớm nhất của các chặng ACTIVE, {@code null} khi không còn
	 * chặng nào; {@code value}: giá trị chiều (Q-08, BR-52).
	 */
	public record JourneyRef(long bookingId, long journeyId, boolean bookingIssued, boolean journeyActive,
			boolean refundable, long refundFee, int seated, Instant firstDeparture, long value) {
	}

	private record Contact(String code, String name, String email, long totalAmount) {
	}

	private static final String TICKETS = """
			SELECT p.last_name || ' ' || p.first_name AS passenger, f.flight_number,
			       f.departure_airport, dep.timezone AS departure_timezone, f.departure_time,
			       f.arrival_airport, arr.timezone AS arrival_timezone, f.arrival_time, t.ticket_number
			FROM tickets t
			JOIN passengers p ON p.id = t.passenger_id
			JOIN booking_segments s ON s.id = t.segment_id
			JOIN booking_journeys j ON j.id = s.journey_id
			JOIN flights f ON f.id = s.flight_id
			JOIN airports dep ON dep.code = f.departure_airport
			JOIN airports arr ON arr.code = f.arrival_airport
			WHERE j.booking_id = :id AND s.status = 'ACTIVE' AND t.ticket_number IS NOT NULL
			ORDER BY j.direction, s.seq, p.id
			""";

	/** Q-08: giá trị một chiều gồm vé các chặng ACTIVE và hành lý, trừ phần giảm giá của chiều. */
	private static final String JOURNEY = """
			SELECT b.id AS booking_id, b.user_id, b.status = 'ISSUED' AS booking_issued,
			       j.id AS journey_id, j.status = 'ACTIVE' AS journey_active, j.refundable, j.refund_fee,
			       (SELECT count(*) FROM passengers p WHERE p.booking_id = b.id AND p.type <> 'INFANT') AS seated,
			       (SELECT min(f.departure_time) FROM booking_segments s JOIN flights f ON f.id = s.flight_id
			        WHERE s.journey_id = j.id AND s.status = 'ACTIVE') AS first_departure,
			       GREATEST(COALESCE((SELECT SUM(t.fare_amount) FROM tickets t JOIN booking_segments s ON s.id = t.segment_id
			                          WHERE s.journey_id = j.id AND s.status = 'ACTIVE'), 0)
			              + COALESCE((SELECT SUM(bb.price) FROM booking_baggage bb WHERE bb.journey_id = j.id), 0)
			              - j.discount_amount, 0) AS value
			FROM bookings b
			JOIN booking_journeys j ON j.booking_id = b.id
			WHERE b.code = :code AND j.direction = :direction
			""";

	private record Found(long userId, JourneyRef ref) {
	}

	private final JdbcClient jdbc;

	private final BookingCancellation cancellation;

	BookingApi(JdbcClient jdbc, BookingCancellation cancellation) {
		this.jdbc = jdbc;
		this.cancellation = cancellation;
	}

	/** BR-104: rỗng khi không có booking, booking của người khác hoặc booking không có chiều đó. */
	@Transactional(readOnly = true)
	public Optional<JourneyRef> journey(long userId, String code, String direction) {
		return read(userId, code, direction);
	}

	/** Như {@link #journey} nhưng khoá {@code bookings} (FOR UPDATE) trước khi đọc, để giá trị không đổi giữa chừng. */
	@Transactional
	public Optional<JourneyRef> lockJourney(long userId, String code, String direction) {
		jdbc.sql("SELECT id FROM bookings WHERE code = ? FOR UPDATE")
			.param(code.toUpperCase(Locale.ROOT))
			.query(Long.class)
			.list();
		return read(userId, code, direction);
	}

	@Transactional
	public void lock(long bookingId) {
		jdbc.sql("SELECT id FROM bookings WHERE id = ? FOR UPDATE").param(bookingId).query(Long.class).list();
	}

	/** Bên gọi đã khoá booking. Trả ghế, chuyển chiều sang REFUNDED; lượt voucher không được trả (BR-32). */
	@Transactional(propagation = Propagation.MANDATORY)
	public void refundJourney(long journeyId) {
		cancellation.refundJourney(journeyId);
	}

	private Optional<JourneyRef> read(long userId, String code, String direction) {
		return jdbc.sql(JOURNEY)
			.param("code", code.toUpperCase(Locale.ROOT))
			.param("direction", direction.toUpperCase(Locale.ROOT))
			.query((rs, rowNum) -> {
				OffsetDateTime first = rs.getObject("first_departure", OffsetDateTime.class);
				return new Found(rs.getLong("user_id"),
						new JourneyRef(rs.getLong("booking_id"), rs.getLong("journey_id"),
								rs.getBoolean("booking_issued"), rs.getBoolean("journey_active"),
								rs.getBoolean("refundable"), rs.getLong("refund_fee"), rs.getInt("seated"),
								first == null ? null : first.toInstant(), rs.getLong("value")));
			})
			.optional()
			.filter(found -> found.userId() == userId)
			.map(Found::ref);
	}

	@Transactional(readOnly = true)
	public IssuedBooking issued(long bookingId) {
		Contact contact = jdbc.sql("SELECT code, contact_name, contact_email, total_amount FROM bookings WHERE id = ?")
			.param(bookingId)
			.query((rs, rowNum) -> new Contact(rs.getString("code"), rs.getString("contact_name"),
					rs.getString("contact_email"), rs.getLong("total_amount")))
			.single();
		List<IssuedTicket> tickets = jdbc.sql(TICKETS)
			.param("id", bookingId)
			.query((rs, rowNum) -> new IssuedTicket(rs.getString("passenger"), rs.getString("flight_number"),
					rs.getString("departure_airport"), local(rs, "departure_time", "departure_timezone"),
					rs.getString("arrival_airport"), local(rs, "arrival_time", "arrival_timezone"),
					rs.getString("ticket_number")))
			.list();
		return new IssuedBooking(contact.code(), contact.name(), contact.email(), contact.totalAmount(), tickets);
	}

	private static OffsetDateTime local(ResultSet rs, String time, String zone) throws SQLException {
		return rs.getObject(time, OffsetDateTime.class)
			.atZoneSameInstant(ZoneId.of(rs.getString(zone)))
			.toOffsetDateTime();
	}

}
