package vn.edu.uit.flightbooking.flight.domain;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.CabinClass;

/**
 * Danh sách và chi tiết chuyến (FR-91). Chỉ đọc. Đọc thẳng bảng {@code airports}, {@code fare_families} (catalog) và
 * {@code booking_segments}, {@code booking_journeys} (booking) bằng SQL để lấy múi giờ, tên gói và số booking, giống
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
