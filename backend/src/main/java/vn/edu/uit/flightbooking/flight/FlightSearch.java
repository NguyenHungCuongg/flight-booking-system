package vn.edu.uit.flightbooking.flight;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.catalog.CatalogApi;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;

/**
 * Tìm hành trình một chiều (FR-11, TDD §6.1): bay thẳng (Q-04), nối chuyến cùng hãng (Q-05), gói giá đang bán trên
 * mọi chặng (Q-06), giá theo BR-21. Khứ hồi (FR-12) gọi hai lần, lần sau với tuyến ngược lại; client lọc chiều về
 * theo BR-04, báo giá (Plan 10) kiểm tra lại.
 */
@Service
public class FlightSearch {

	/**
	 * Tham số của GET /flights/search. {@code date}: ngày cất cánh theo giờ sân bay đi. Thiếu {@code children},
	 * {@code infants} thì coi là 0.
	 */
	public record SearchQuery(@NotBlank String from, @NotBlank String to, @NotNull LocalDate date,
			@NotNull @Min(1) Integer adults, @Min(0) Integer children, @Min(0) Integer infants,
			@NotNull CabinClass cabin) {

		public SearchQuery {
			children = children == null ? 0 : children;
			infants = infants == null ? 0 : infants;
		}

	}

	public record AirlineRef(String code, String name) {
	}

	/** Giờ theo múi giờ của sân bay, kèm tên múi giờ IANA (TDD §5.1). */
	public record AirportTime(String airport, String city, OffsetDateTime time, String timezone) {
	}

	/** {@code layoverMinutesAfter}: thời gian nối tới chặng sau, {@code null} ở chặng cuối. */
	public record Segment(long flightId, String flightNumber, AirlineRef airline, AirportTime departure,
			AirportTime arrival, long durationMinutes, String aircraftModel, Long layoverMinutesAfter) {
	}

	/**
	 * {@code adultPrice}: giá người lớn của chiều (BR-20). {@code totalPrice}: cho đúng số khách đã nhập (BR-21).
	 * {@code seatsLeft}: số ghế còn nhỏ nhất qua các chặng.
	 */
	public record FareOption(long fareFamilyId, String name, CabinClass cabinClass, int carryOnKg,
			int checkedBaggageKg, boolean refundable, long refundFee, boolean changeable, long changeFee,
			long adultPrice, long totalPrice, int seatsLeft) {
	}

	/** Gói giá xếp theo {@code totalPrice} tăng dần. */
	public record Itinerary(int stops, long totalDurationMinutes, List<Segment> segments,
			List<FareOption> fareOptions) {
	}

	public record SearchResult(List<Itinerary> itineraries) {
	}

	private record Leg(Segment segment, Instant departure, Instant arrival, int seats) {
	}

	private record Family(long id, String name, int carryOnKg, int checkedBaggageKg, boolean refundable,
			long refundFee, boolean changeable, long changeFee) {
	}

	private record FareRow(long flightId, long price, Family family) {
	}

	/** Q-04, chỉ lấy id: chi tiết chặng đọc chung một lần ở {@link #LEGS}. */
	private static final String DIRECT = """
			SELECT f.id
			FROM flights f
			JOIN flight_cabins c ON c.flight_id = f.id AND c.cabin_class = :cabin
			WHERE f.departure_airport = :origin
			  AND f.arrival_airport = :destination
			  AND f.departure_time >= :dayStart AND f.departure_time < :dayEnd
			  AND f.departure_time >= :minDeparture
			  AND f.status = 'SCHEDULED'
			  AND c.available_seats >= :seats
			""";

	/** Q-05. */
	private static final String CONNECTING = """
			SELECT f1.id AS first_flight_id, f2.id AS second_flight_id
			FROM flights f1
			JOIN flight_cabins c1 ON c1.flight_id = f1.id AND c1.cabin_class = :cabin
			JOIN flights f2       ON f2.departure_airport = f1.arrival_airport
			                     AND f2.airline_code = f1.airline_code
			                     AND f2.arrival_airport = :destination
			                     AND f2.status = 'SCHEDULED'
			                     AND f2.departure_time BETWEEN f1.arrival_time + :minConnection * INTERVAL '1 minute'
			                                               AND f1.arrival_time + :maxConnection * INTERVAL '1 minute'
			JOIN flight_cabins c2 ON c2.flight_id = f2.id AND c2.cabin_class = :cabin
			WHERE f1.departure_airport = :origin
			  AND f1.arrival_airport <> :destination
			  AND f1.departure_time >= :dayStart AND f1.departure_time < :dayEnd
			  AND f1.departure_time >= :minDeparture
			  AND f1.status = 'SCHEDULED'
			  AND c1.available_seats >= :seats
			  AND c2.available_seats >= :seats
			""";

	/** Đọc thẳng bảng airlines, airports của catalog để lấy tên hãng, thành phố, múi giờ (giống FlightQueries). */
	private static final String LEGS = """
			SELECT f.id, f.flight_number, f.airline_code, al.name AS airline_name,
			       f.departure_airport, dep.city AS departure_city, dep.timezone AS departure_timezone, f.departure_time,
			       f.arrival_airport, arr.city AS arrival_city, arr.timezone AS arrival_timezone, f.arrival_time,
			       f.aircraft_model, c.available_seats
			FROM flights f
			JOIN airlines al ON al.code = f.airline_code
			JOIN airports dep ON dep.code = f.departure_airport
			JOIN airports arr ON arr.code = f.arrival_airport
			JOIN flight_cabins c ON c.flight_id = f.id AND c.cabin_class = :cabin
			WHERE f.id IN (:ids)
			""";

	/** Q-06. */
	private static final String FARES = """
			SELECT ff.flight_id, ff.price,
			       fam.id AS fare_family_id, fam.name, fam.carry_on_kg, fam.checked_baggage_kg,
			       fam.refundable, fam.refund_fee, fam.changeable, fam.change_fee
			FROM flight_fares ff
			JOIN fare_families fam ON fam.id = ff.fare_family_id
			WHERE ff.flight_id IN (:ids)
			  AND fam.cabin_class = :cabin
			  AND fam.active
			""";

	private final JdbcClient jdbc;

	private final CatalogApi catalog;

	private final SettingsApi settings;

	FlightSearch(JdbcClient jdbc, CatalogApi catalog, SettingsApi settings) {
		this.jdbc = jdbc;
		this.catalog = catalog;
		this.settings = settings;
	}

	/**
	 * Hành trình thoả BR-01–BR-06, xếp theo giá rẻ nhất rồi giờ hạ cánh. Sân bay đi không tồn tại hoặc không có
	 * chuyến thì trả danh sách rỗng.
	 */
	@Transactional(readOnly = true)
	public SearchResult search(SearchQuery q) {
		int seated = q.adults() + q.children();
		if (q.from().equals(q.to())) {
			throw BusinessException.invalidField("to", "Sân bay đến phải khác sân bay đi");
		}
		if (q.infants() > q.adults()) {
			throw BusinessException.invalidField("infants", "Số em bé không được nhiều hơn số người lớn");
		}
		int maxSeated = settings.getInt(SettingKey.BOOKING_MAX_SEATED_PASSENGERS);
		if (seated > maxSeated) {
			throw BusinessException.invalidField("adults",
					"Tối đa %d khách chiếm ghế (người lớn và trẻ em)".formatted(maxSeated));
		}
		Optional<ZoneId> zone = catalog.airport(q.from()).map(CatalogApi.AirportRef::zone);
		if (zone.isEmpty()) {
			return new SearchResult(List.of());
		}
		Instant minDeparture = Instant.now()
			.plus(settings.getInt(SettingKey.BOOKING_MIN_HOURS_BEFORE_DEPARTURE), ChronoUnit.HOURS);
		Map<String, Object> params = Map.of("origin", q.from(), "destination", q.to(), "cabin", q.cabin().name(),
				"seats", seated, "dayStart", utc(q.date().atStartOfDay(zone.get()).toInstant()), "dayEnd",
				utc(q.date().plusDays(1).atStartOfDay(zone.get()).toInstant()), "minDeparture", utc(minDeparture),
				"minConnection", settings.getInt(SettingKey.SEARCH_MIN_CONNECTION_MINUTES), "maxConnection",
				settings.getInt(SettingKey.SEARCH_MAX_CONNECTION_MINUTES));

		List<List<Long>> routes = new ArrayList<>();
		jdbc.sql(DIRECT).params(params).query(Long.class).list().forEach(id -> routes.add(List.of(id)));
		routes.addAll(jdbc.sql(CONNECTING)
			.params(params)
			.query((rs, n) -> List.of(rs.getLong("first_flight_id"), rs.getLong("second_flight_id")))
			.list());
		if (routes.isEmpty()) {
			return new SearchResult(List.of());
		}

		Set<Long> ids = routes.stream().flatMap(List::stream).collect(Collectors.toSet());
		Map<Long, Leg> legs = jdbc.sql(LEGS)
			.param("ids", ids)
			.param("cabin", q.cabin().name())
			.query(FlightSearch::leg)
			.list()
			.stream()
			.collect(Collectors.toMap(l -> l.segment().flightId(), l -> l));
		Map<Long, Map<Long, Long>> prices = new HashMap<>(); // flightId → fareFamilyId → giá người lớn của chặng
		Map<Long, Family> families = new HashMap<>();
		jdbc.sql(FARES).param("ids", ids).param("cabin", q.cabin().name()).query(FlightSearch::fare).list()
			.forEach(fare -> {
				prices.computeIfAbsent(fare.flightId(), k -> new HashMap<>()).put(fare.family().id(), fare.price());
				families.put(fare.family().id(), fare.family());
			});

		int childPercent = settings.getInt(SettingKey.PRICING_CHILD_PERCENT);
		int infantPercent = settings.getInt(SettingKey.PRICING_INFANT_PERCENT);
		List<Itinerary> itineraries = routes.stream()
			.map(route -> itinerary(route.stream().map(legs::get).toList(), prices, families, q, childPercent,
					infantPercent))
			.flatMap(Optional::stream)
			.sorted(Comparator.comparingLong((Itinerary i) -> i.fareOptions().getFirst().totalPrice())
				.thenComparing(i -> i.segments().getLast().arrival().time().toInstant()))
			.toList();
		return new SearchResult(itineraries);
	}

	/**
	 * BR-21, TDD §5.2: giá vé của từng khách trên từng chặng làm tròn half-up đến đồng, rồi mới cộng. Báo giá (Plan 10)
	 * dùng lại hàm này.
	 */
	public static long totalPrice(List<Long> adultLegPrices, int adults, int children, int infants, int childPercent,
			int infantPercent) {
		return adultLegPrices.stream()
			.mapToLong(price -> adults * price + children * percentOf(price, childPercent)
					+ infants * percentOf(price, infantPercent))
			.sum();
	}

	/** Làm tròn half-up, đúng với số không âm (TDD §5.2). */
	static long percentOf(long amount, int percent) {
		return (amount * percent + 50) / 100;
	}

	/** Chỉ giữ gói giá có trên mọi chặng (BR-05); không còn gói nào thì bỏ hành trình. */
	private static Optional<Itinerary> itinerary(List<Leg> route, Map<Long, Map<Long, Long>> prices,
			Map<Long, Family> families, SearchQuery q, int childPercent, int infantPercent) {
		Set<Long> common = new LinkedHashSet<>(prices.getOrDefault(route.getFirst().segment().flightId(), Map.of())
			.keySet());
		route.forEach(leg -> common.retainAll(prices.getOrDefault(leg.segment().flightId(), Map.of()).keySet()));
		if (common.isEmpty()) {
			return Optional.empty();
		}
		int seatsLeft = route.stream().mapToInt(Leg::seats).min().orElseThrow();
		List<FareOption> options = common.stream().map(id -> {
			Family f = families.get(id);
			List<Long> legPrices = route.stream().map(leg -> prices.get(leg.segment().flightId()).get(id)).toList();
			return new FareOption(id, f.name(), q.cabin(), f.carryOnKg(), f.checkedBaggageKg(), f.refundable(),
					f.refundFee(), f.changeable(), f.changeFee(), legPrices.stream().mapToLong(Long::longValue).sum(),
					totalPrice(legPrices, q.adults(), q.children(), q.infants(), childPercent, infantPercent),
					seatsLeft);
		}).sorted(Comparator.comparingLong(FareOption::totalPrice)).toList();

		List<Segment> segments = new ArrayList<>();
		for (int i = 0; i < route.size(); i++) {
			Segment s = route.get(i).segment();
			Long layover = i + 1 < route.size() ? minutes(route.get(i).arrival(), route.get(i + 1).departure()) : null;
			segments.add(new Segment(s.flightId(), s.flightNumber(), s.airline(), s.departure(), s.arrival(),
					s.durationMinutes(), s.aircraftModel(), layover));
		}
		return Optional.of(new Itinerary(route.size() - 1,
				minutes(route.getFirst().departure(), route.getLast().arrival()), segments, options));
	}

	private static Leg leg(ResultSet rs, int rowNum) throws SQLException {
		Instant departure = rs.getObject("departure_time", OffsetDateTime.class).toInstant();
		Instant arrival = rs.getObject("arrival_time", OffsetDateTime.class).toInstant();
		Segment segment = new Segment(rs.getLong("id"), rs.getString("flight_number"),
				new AirlineRef(rs.getString("airline_code"), rs.getString("airline_name")),
				airportTime(rs.getString("departure_airport"), rs.getString("departure_city"), departure,
						rs.getString("departure_timezone")),
				airportTime(rs.getString("arrival_airport"), rs.getString("arrival_city"), arrival,
						rs.getString("arrival_timezone")),
				minutes(departure, arrival), rs.getString("aircraft_model"), null);
		return new Leg(segment, departure, arrival, rs.getInt("available_seats"));
	}

	private static AirportTime airportTime(String airport, String city, Instant time, String zone) {
		return new AirportTime(airport, city, time.atZone(ZoneId.of(zone)).toOffsetDateTime(), zone);
	}

	private static long minutes(Instant from, Instant to) {
		return Duration.between(from, to).toMinutes();
	}

	private static FareRow fare(ResultSet rs, int rowNum) throws SQLException {
		return new FareRow(rs.getLong("flight_id"), rs.getLong("price"),
				new Family(rs.getLong("fare_family_id"), rs.getString("name"), rs.getInt("carry_on_kg"),
						rs.getInt("checked_baggage_kg"), rs.getBoolean("refundable"), rs.getLong("refund_fee"),
						rs.getBoolean("changeable"), rs.getLong("change_fee")));
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

}
