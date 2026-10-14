package vn.edu.uit.flightbooking.booking;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API công khai của booking (TDD §3.2). Hiện có dữ liệu cho email vé (FR-31, FR-73). Plan 14, 15 thêm các hàm khoá và
 * thao tác trên chiều/chặng mà hậu mãi cần.
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

	private final JdbcClient jdbc;

	BookingApi(JdbcClient jdbc) {
		this.jdbc = jdbc;
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
