package vn.edu.uit.flightbooking.flight.domain;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.CabinClass;

/**
 * Danh sách và chi tiết chuyến (FR-91), manifest (FR-72). Chỉ đọc. Đọc thẳng bảng {@code airports},
 * {@code fare_families} (catalog) và {@code booking_segments}, {@code booking_journeys}, {@code bookings},
 * {@code tickets}, {@code passengers} (booking) bằng SQL để lấy múi giờ, tên gói, số booking và danh sách khách, giống
 * cách module report đọc dữ liệu.
 */
@Service
public class FlightQueries {

	public record CabinView(CabinClass cabinClass, int totalSeats, int availableSeats) {
	}

	/** Giờ theo múi giờ của sân bay tương ứng, kèm tên múi giờ IANA (TDD §5.1). Hạng ghế theo thứ tự của enum. */
	public record FlightView(long id, String airlineCode, String flightNumber, String departureAirport,
			String arrivalAirport, OffsetDateTime departureTime, String departureTimezone, OffsetDateTime arrivalTime,
			String arrivalTimezone, String aircraftModel, FlightStatus status, List<CabinView> cabins) {
	}

	public record FareView(long fareFamilyId, String fareFamilyName, CabinClass cabinClass, long price) {
	}

	/** Màn hình A-06. {@code bookingCount}: số booking từng có chặng trên chuyến, ở mọi trạng thái (BR-82). */
	public record FlightDetail(FlightView flight, List<FareView> fares, long bookingCount) {
	}

	public record ManifestPassenger(String bookingCode, String lastName, String firstName, String type,
			String ticketNumber, CabinClass cabinClass, String contactPhone, String contactEmail) {
	}

	public record Manifest(FlightView flight, List<ManifestPassenger> passengers) {
	}

	private static final String SELECT_FLIGHT = """
			SELECT f.id, f.airline_code, f.flight_number, f.departure_airport, f.arrival_airport,
			       f.departure_time, dep.timezone AS departure_timezone,
			       f.arrival_time, arr.timezone AS arrival_timezone, f.aircraft_model, f.status
			FROM flights f
			JOIN airports dep ON dep.code = f.departure_airport
			JOIN airports arr ON arr.code = f.arrival_airport
			""";

	private final JdbcClient jdbc;

	FlightQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Màn hình S-05, A-04. Mọi bộ lọc đều không bắt buộc; {@code date} là ngày cất cánh theo giờ sân bay đi. Sắp theo
	 * giờ cất cánh.
	 */
	@Transactional(readOnly = true)
	public Page<FlightView> list(String from, String to, LocalDate date, String airline, FlightStatus status,
			Pageable pageable) {
		StringBuilder where = new StringBuilder(" WHERE TRUE");
		Map<String, Object> params = new HashMap<>();
		if (from != null) {
			where.append(" AND f.departure_airport = :from");
			params.put("from", from);
		}
		if (to != null) {
			where.append(" AND f.arrival_airport = :to");
			params.put("to", to);
		}
		if (date != null) {
			where.append(" AND (f.departure_time AT TIME ZONE dep.timezone)::date = :date");
			params.put("date", date);
		}
		if (airline != null) {
			where.append(" AND f.airline_code = :airline");
			params.put("airline", airline);
		}
		if (status != null) {
			where.append(" AND f.status = :status");
			params.put("status", status.name());
		}
		long total = jdbc.sql("SELECT count(*) FROM flights f JOIN airports dep ON dep.code = f.departure_airport" + where)
			.params(params)
			.query(Long.class)
			.single();
		params.put("limit", pageable.getPageSize());
		params.put("offset", pageable.getOffset());
		List<FlightView> content = views(
				SELECT_FLIGHT + where + " ORDER BY f.departure_time, f.id LIMIT :limit OFFSET :offset", params);
		return new PageImpl<>(content, pageable, total);
	}

	@Transactional(readOnly = true)
	public FlightDetail detail(long id) {
		FlightView flight = views(SELECT_FLIGHT + " WHERE f.id = :id", Map.of("id", id)).stream()
			.findFirst()
			.orElseThrow(FlightService::notFound);
		List<FareView> fares = jdbc.sql("""
				SELECT ff.fare_family_id, fam.name, fam.cabin_class, ff.price
				FROM flight_fares ff JOIN fare_families fam ON fam.id = ff.fare_family_id
				WHERE ff.flight_id = ?
				""")
			.param(id)
			.query((rs, n) -> new FareView(rs.getLong("fare_family_id"), rs.getString("name"),
					CabinClass.valueOf(rs.getString("cabin_class")), rs.getLong("price")))
			.list()
			.stream()
			.sorted(Comparator.comparing(FareView::cabinClass).thenComparing(FareView::price))
			.toList();
		long bookingCount = jdbc.sql("""
				SELECT count(DISTINCT j.booking_id)
				FROM booking_segments s JOIN booking_journeys j ON j.id = s.journey_id
				WHERE s.flight_id = ?
				""").param(id).query(Long.class).single();
		return new FlightDetail(flight, fares, bookingCount);
	}

	/**
	 * FR-72, S-06, Q-17: Danh sách hành khách (manifest) của chuyến bay cho Staff. Chuyến không tồn tại thì 404.
	 */
	@Transactional(readOnly = true)
	public Manifest manifest(long id) {
		FlightView flight = views(SELECT_FLIGHT + " WHERE f.id = :id", Map.of("id", id)).stream()
			.findFirst()
			.orElseThrow(FlightService::notFound);
		List<ManifestPassenger> passengers = jdbc.sql("""
				SELECT b.code AS booking_code, p.last_name, p.first_name, p.type,
				       t.ticket_number, j.cabin_class, b.contact_phone, b.contact_email
				FROM booking_segments s
				JOIN booking_journeys j ON j.id = s.journey_id
				JOIN bookings b         ON b.id = j.booking_id AND b.status = 'ISSUED'
				JOIN tickets t          ON t.segment_id = s.id
				JOIN passengers p       ON p.id = t.passenger_id
				WHERE s.flight_id = :flightId
				  AND s.status = 'ACTIVE'
				ORDER BY p.last_name, p.first_name
				""")
			.param("flightId", id)
			.query((rs, n) -> new ManifestPassenger(rs.getString("booking_code"), rs.getString("last_name"),
					rs.getString("first_name"), rs.getString("type"), rs.getString("ticket_number"),
					CabinClass.valueOf(rs.getString("cabin_class")), rs.getString("contact_phone"),
					rs.getString("contact_email")))
			.list();
		return new Manifest(flight, passengers);
	}

	/** Chạy truy vấn chuyến rồi điền hạng ghế của các chuyến đó bằng một truy vấn nữa. */
	private List<FlightView> views(String sql, Map<String, ?> params) {
		List<FlightView> flights = jdbc.sql(sql).params(params).query(FlightQueries::flight).list();
		if (flights.isEmpty()) {
			return flights;
		}
		Map<Long, FlightView> byId = flights.stream().collect(Collectors.toMap(FlightView::id, f -> f));
		jdbc.sql("SELECT flight_id, cabin_class, total_seats, available_seats FROM flight_cabins WHERE flight_id IN (:ids)")
			.param("ids", byId.keySet())
			.query(rs -> {
				byId.get(rs.getLong("flight_id"))
					.cabins()
					.add(new CabinView(CabinClass.valueOf(rs.getString("cabin_class")), rs.getInt("total_seats"),
							rs.getInt("available_seats")));
			});
		flights.forEach(f -> f.cabins().sort(Comparator.comparing(CabinView::cabinClass)));
		return flights;
	}

	private static FlightView flight(ResultSet rs, int rowNum) throws SQLException {
		String departureZone = rs.getString("departure_timezone");
		String arrivalZone = rs.getString("arrival_timezone");
		return new FlightView(rs.getLong("id"), rs.getString("airline_code"), rs.getString("flight_number"),
				rs.getString("departure_airport"), rs.getString("arrival_airport"),
				local(rs.getObject("departure_time", OffsetDateTime.class), departureZone), departureZone,
				local(rs.getObject("arrival_time", OffsetDateTime.class), arrivalZone), arrivalZone,
				rs.getString("aircraft_model"), FlightStatus.valueOf(rs.getString("status")), new ArrayList<>());
	}

	private static OffsetDateTime local(OffsetDateTime time, String zone) {
		return time.atZoneSameInstant(ZoneId.of(zone)).toOffsetDateTime();
	}

}
