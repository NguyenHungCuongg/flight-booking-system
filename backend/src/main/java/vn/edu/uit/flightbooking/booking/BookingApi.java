package vn.edu.uit.flightbooking.booking;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.booking.domain.BookingCancellation;
import vn.edu.uit.flightbooking.booking.domain.BookingChanges;
import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedSegment;

/**
 * API công khai của booking (TDD §3.2). Có dữ liệu cho email vé (FR-31, FR-73) và các hàm đọc, khoá, hoàn một chiều
 * cho hậu mãi (Plan 14), và các hàm đổi chuyến (Plan 15): đọc một chiều để đổi, giữ chỗ chặng mới, hoàn tất hoặc huỷ.
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
	 * chặng nào; {@code value}: giá trị chiều (Q-08, BR-52). {@code changeFee}: phí đổi mỗi khách chiếm ghế (snapshot).
	 */
	public record JourneyRef(long bookingId, long journeyId, boolean bookingIssued, boolean journeyActive,
			boolean refundable, long refundFee, boolean changeable, long changeFee, int seated, Instant firstDeparture,
			long value) {
	}

	/**
	 * Một chiều để đổi (BR-71): {@code flightIds}, {@code origin}, {@code destination} của các chặng ACTIVE theo
	 * {@code seq}; {@code fareTotal}: tổng giá vé các chặng đó (BR-72). {@code departAfter}: giờ hạ cánh của chiều đi,
	 * chỉ có khi đây là chiều về và chiều đi còn ACTIVE; {@code arriveBefore}: giờ cất cánh của chiều về, chỉ có khi đây
	 * là chiều đi và chiều về còn ACTIVE.
	 */
	public record ChangeRef(JourneyRef journey, long fareFamilyId, CabinClass cabinClass, int adults, int children,
			int infants, List<Long> flightIds, String origin, String destination, long fareTotal, Instant departAfter,
			Instant arriveBefore) {
	}

	private record Profile(long fareFamilyId, CabinClass cabinClass, int adults, int children, int infants) {
	}

	private record Leg(long flightId, String departureAirport, String arrivalAirport) {
	}

	private record Other(String direction, Instant firstDeparture, Instant lastArrival) {
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
			       j.changeable, j.change_fee,
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

	private static final String PROFILE = """
			SELECT j.fare_family_id, j.cabin_class,
			       count(*) FILTER (WHERE p.type = 'ADULT') AS adults,
			       count(*) FILTER (WHERE p.type = 'CHILD') AS children,
			       count(*) FILTER (WHERE p.type = 'INFANT') AS infants
			FROM booking_journeys j
			JOIN passengers p ON p.booking_id = j.booking_id
			WHERE j.id = :journeyId
			GROUP BY j.id
			""";

	private static final String LEGS = """
			SELECT s.flight_id, f.departure_airport, f.arrival_airport
			FROM booking_segments s
			JOIN flights f ON f.id = s.flight_id
			WHERE s.journey_id = :journeyId AND s.status = 'ACTIVE'
			ORDER BY s.seq
			""";

	private static final String FARE_TOTAL = """
			SELECT COALESCE(SUM(t.fare_amount), 0)
			FROM tickets t
			JOIN booking_segments s ON s.id = t.segment_id
			WHERE s.journey_id = :journeyId AND s.status = 'ACTIVE'
			""";

	private static final String OTHER_JOURNEY = """
			SELECT j.direction, min(f.departure_time) AS first_departure, max(f.arrival_time) AS last_arrival
			FROM booking_journeys j
			JOIN booking_segments s ON s.journey_id = j.id AND s.status = 'ACTIVE'
			JOIN flights f ON f.id = s.flight_id
			WHERE j.booking_id = :bookingId AND j.id <> :journeyId AND j.status = 'ACTIVE'
			GROUP BY j.direction
			""";

	private record Found(long userId, JourneyRef ref) {
	}

	private final JdbcClient jdbc;

	private final BookingCancellation cancellation;

	private final BookingChanges changes;

	BookingApi(JdbcClient jdbc, BookingCancellation cancellation, BookingChanges changes) {
		this.jdbc = jdbc;
		this.cancellation = cancellation;
		this.changes = changes;
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

	/** BR-104: rỗng khi không có booking, booking của người khác hoặc booking không có chiều đó. */
	@Transactional(readOnly = true)
	public Optional<ChangeRef> change(long userId, String code, String direction) {
		return read(userId, code, direction).map(this::changeOf);
	}

	/** Như {@link #change} nhưng khoá {@code bookings} (FOR UPDATE) trước khi đọc, như {@link #lockJourney}. */
	@Transactional
	public Optional<ChangeRef> lockChange(long userId, String code, String direction) {
		return lockJourney(userId, code, direction).map(this::changeOf);
	}

	/** BR-73. Bên gọi đã khoá booking. Giữ ghế các chuyến mới, thêm chặng PENDING_CHANGE kèm vé chưa có số. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void holdChange(long journeyId, long rescheduleId, List<PricedSegment> segments, int childPercent,
			int infantPercent) {
		changes.hold(journeyId, rescheduleId, segments, childPercent, infantPercent);
	}

	/** BR-74. Bên gọi đã khoá booking. Chặng cũ REPLACED và trả ghế, chặng mới ACTIVE và được cấp số vé. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void completeChange(long journeyId, long rescheduleId) {
		changes.complete(journeyId, rescheduleId);
	}

	/** BR-75. Bên gọi đã khoá booking. Chặng mới CANCELLED và trả ghế; chặng cũ giữ nguyên. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void cancelChange(long rescheduleId) {
		changes.cancel(rescheduleId);
	}

	private ChangeRef changeOf(JourneyRef ref) {
		Map<String, Object> id = Map.of("journeyId", ref.journeyId());
		Profile profile = jdbc.sql(PROFILE)
			.params(id)
			.query((rs, n) -> new Profile(rs.getLong("fare_family_id"), CabinClass.valueOf(rs.getString("cabin_class")),
					rs.getInt("adults"), rs.getInt("children"), rs.getInt("infants")))
			.single();
		List<Leg> legs = jdbc.sql(LEGS)
			.params(id)
			.query((rs, n) -> new Leg(rs.getLong("flight_id"), rs.getString("departure_airport"),
					rs.getString("arrival_airport")))
			.list();
		long fareTotal = jdbc.sql(FARE_TOTAL).params(id).query(Long.class).single();
		Other other = jdbc.sql(OTHER_JOURNEY)
			.param("bookingId", ref.bookingId())
			.param("journeyId", ref.journeyId())
			.query((rs, n) -> new Other(rs.getString("direction"),
					rs.getObject("first_departure", OffsetDateTime.class).toInstant(),
					rs.getObject("last_arrival", OffsetDateTime.class).toInstant()))
			.optional()
			.orElse(null);
		boolean otherIsOutbound = other != null && other.direction().equals("OUTBOUND");
		return new ChangeRef(ref, profile.fareFamilyId(), profile.cabinClass(), profile.adults(), profile.children(),
				profile.infants(), legs.stream().map(Leg::flightId).toList(), legs.getFirst().departureAirport(),
				legs.getLast().arrivalAirport(), fareTotal, otherIsOutbound ? other.lastArrival() : null,
				other != null && !otherIsOutbound ? other.firstDeparture() : null);
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
								rs.getBoolean("refundable"), rs.getLong("refund_fee"), rs.getBoolean("changeable"),
									rs.getLong("change_fee"), rs.getInt("seated"),
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
