package vn.edu.uit.flightbooking.flight;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;
import vn.edu.uit.flightbooking.flight.domain.FlightStatus;

/**
 * Kiểm tra và định giá hành trình khách đã chọn (TDD §3.2, §6.2 bước 2): BR-01–BR-06 cho từng chiều, BR-04 cho khứ
 * hồi, giá người lớn từng chặng (BR-20). Chỉ đọc; giữ ghế là việc của {@link SeatInventory}. Đọc thẳng bảng
 * {@code airports}, {@code fare_families} của catalog bằng SQL, như {@link FlightSearch}.
 */
@Service
public class FlightApi {

	/** Một chiều khách chọn: các chuyến theo thứ tự bay và một gói giá (BR-02, BR-05). */
	public record JourneyChoice(List<Long> flightIds, long fareFamilyId) {
	}

	/** Quy định của gói giá lúc báo giá, để giữ chỗ chốt lại (BR-24). */
	public record FareSnapshot(long fareFamilyId, String name, CabinClass cabinClass, int carryOnKg,
			int checkedBaggageKg, boolean refundable, long refundFee, boolean changeable, long changeFee) {
	}

	/**
	 * Giờ theo múi giờ của sân bay tương ứng (TDD §5.1). {@code international}: hai sân bay khác quốc gia (BR-13).
	 * {@code adultPrice}: giá người lớn của chặng theo gói đã chọn (BR-20).
	 */
	public record PricedSegment(long flightId, String flightNumber, String departureAirport, String arrivalAirport,
			OffsetDateTime departureTime, OffsetDateTime arrivalTime, boolean international, long adultPrice) {
	}

	public record PricedJourney(String airlineCode, FareSnapshot fare, List<PricedSegment> segments) {
	}

	/** Thông tin tóm tắt chuyến bay cho thông báo đổi giờ hoặc huỷ chuyến (Plan 16). */
	public record FlightRef(long id, String flightNumber, String departureAirport, String arrivalAirport,
			OffsetDateTime departureTime, OffsetDateTime arrivalTime) {
	}

	private record Flight(long id, String flightNumber, String airlineCode, String status, String departureAirport,
			String arrivalAirport, OffsetDateTime departureTime, OffsetDateTime arrivalTime, boolean international) {
	}

	private record Family(FareSnapshot snapshot, String airlineCode, boolean active) {
	}

	/** {@code availableSeats} là {@code null} khi chuyến không có hạng ghế của gói. */
	private record Fare(long flightId, long price, Integer availableSeats) {
	}

	private static final String FLIGHTS = """
			SELECT f.id, f.flight_number, f.airline_code, f.status,
			       f.departure_airport, dep.timezone AS departure_timezone, f.departure_time,
			       f.arrival_airport, arr.timezone AS arrival_timezone, f.arrival_time,
			       dep.country_code <> arr.country_code AS international
			FROM flights f
			JOIN airports dep ON dep.code = f.departure_airport
			JOIN airports arr ON arr.code = f.arrival_airport
			WHERE f.id IN (:ids)
			""";

	private static final String FAMILY = """
			SELECT id, airline_code, cabin_class, name, carry_on_kg, checked_baggage_kg,
			       refundable, refund_fee, changeable, change_fee, active
			FROM fare_families
			WHERE id = :id
			""";

	/** Q-06 cho một gói, kèm số ghế còn trong hạng ghế của gói (BR-06). */
	private static final String FARES = """
			SELECT ff.flight_id, ff.price, c.available_seats
			FROM flight_fares ff
			LEFT JOIN flight_cabins c ON c.flight_id = ff.flight_id AND c.cabin_class = :cabin
			WHERE ff.fare_family_id = :fareFamilyId AND ff.flight_id IN (:ids)
			""";

	private final JdbcClient jdbc;

	private final SettingsApi settings;

	FlightApi(JdbcClient jdbc, SettingsApi settings) {
		this.jdbc = jdbc;
		this.settings = settings;
	}

	/**
	 * {@code journeys}: chiều đi, rồi chiều về nếu là khứ hồi. {@code seats}: số khách chiếm ghế (BR-06). Cấu trúc sai
	 * báo ITINERARY_INVALID; chuyến không còn, đã huỷ, quá sát giờ, gói giá ngừng bán báo FLIGHT_NOT_BOOKABLE; thiếu
	 * ghế báo SEATS_UNAVAILABLE.
	 */
	@Transactional(readOnly = true)
	public List<PricedJourney> price(List<JourneyChoice> journeys, int seats) {
		Instant minDeparture = Instant.now()
			.plus(settings.getInt(SettingKey.BOOKING_MIN_HOURS_BEFORE_DEPARTURE), ChronoUnit.HOURS);
		int minConnection = settings.getInt(SettingKey.SEARCH_MIN_CONNECTION_MINUTES);
		int maxConnection = settings.getInt(SettingKey.SEARCH_MAX_CONNECTION_MINUTES);
		List<PricedJourney> priced = new ArrayList<>();
		for (JourneyChoice choice : journeys) {
			priced.add(journey(choice, seats, minDeparture, minConnection, maxConnection));
		}
		if (priced.size() == 2) {
			checkReturn(priced.getFirst(), priced.getLast());
		}
		return priced;
	}

	/** Đọc thông tin chuyến bay theo id kèm giờ địa phương. Chuyến luôn tồn tại. */
	@Transactional(readOnly = true)
	public FlightRef flight(long id) {
		Flight f = jdbc.sql(FLIGHTS)
			.param("ids", List.of(id))
			.query(FlightApi::flight)
			.single();
		return new FlightRef(f.id(), f.flightNumber(), f.departureAirport(), f.arrivalAirport(), f.departureTime(),
				f.arrivalTime());
	}

	private PricedJourney journey(JourneyChoice choice, int seats, Instant minDeparture, int minConnection,
			int maxConnection) {
		List<Long> ids = choice.flightIds();
		if (ids.isEmpty() || ids.size() > 2 || ids.stream().distinct().count() < ids.size()) {
			throw invalid("Mỗi chiều gồm 1 chặng bay thẳng hoặc 2 chặng nối chuyến");
		}
		Map<Long, Flight> found = jdbc.sql(FLIGHTS)
			.param("ids", ids)
			.query(FlightApi::flight)
			.list()
			.stream()
			.collect(Collectors.toMap(Flight::id, f -> f));
		if (found.size() < ids.size()) {
			throw notBookable("Chuyến bay đã chọn không còn được bán");
		}
		List<Flight> route = ids.stream().map(found::get).toList();
		Flight first = route.getFirst();
		if (route.size() == 2) {
			checkConnection(first, route.getLast(), minConnection, maxConnection);
		}
		for (Flight f : route) {
			if (!FlightStatus.SCHEDULED.name().equals(f.status())) {
				throw notBookable("Chuyến %s đã bị huỷ".formatted(f.flightNumber()));
			}
		}
		if (first.departureTime().toInstant().isBefore(minDeparture)) {
			throw notBookable("Chuyến %s sắp cất cánh nên không còn bán".formatted(first.flightNumber()));
		}

		Family family = jdbc.sql(FAMILY)
			.param("id", choice.fareFamilyId())
			.query(FlightApi::family)
			.optional()
			.filter(f -> f.airlineCode().equals(first.airlineCode()))
			.orElseThrow(() -> invalid("Gói giá không thuộc hãng khai thác chiều này"));
		FareSnapshot fare = family.snapshot();
		if (!family.active()) {
			throw notBookable("Gói giá %s đã ngừng bán".formatted(fare.name()));
		}
		Map<Long, Fare> fares = jdbc.sql(FARES)
			.param("cabin", fare.cabinClass().name())
			.param("fareFamilyId", fare.fareFamilyId())
			.param("ids", ids)
			.query(FlightApi::fare)
			.list()
			.stream()
			.collect(Collectors.toMap(Fare::flightId, f -> f));

		List<PricedSegment> segments = new ArrayList<>();
		for (Flight f : route) {
			Fare price = fares.get(f.id());
			if (price == null) {
				throw notBookable("Gói giá %s không còn bán trên chuyến %s".formatted(fare.name(), f.flightNumber()));
			}
			if (price.availableSeats() == null || price.availableSeats() < seats) {
				throw new BusinessException(ErrorCode.SEATS_UNAVAILABLE, "Chuyến %s hạng %s không còn đủ %d ghế"
					.formatted(f.flightNumber(), fare.cabinClass(), seats));
			}
			segments.add(new PricedSegment(f.id(), f.flightNumber(), f.departureAirport(), f.arrivalAirport(),
					f.departureTime(), f.arrivalTime(), f.international(), price.price()));
		}
		return new PricedJourney(first.airlineCode(), fare, segments);
	}

	/** BR-03. */
	private static void checkConnection(Flight first, Flight second, int minConnection, int maxConnection) {
		long layover = Duration.between(first.arrivalTime(), second.departureTime()).toMinutes();
		if (!first.airlineCode().equals(second.airlineCode())
				|| !first.arrivalAirport().equals(second.departureAirport()) || layover < minConnection
				|| layover > maxConnection) {
			throw invalid("Hai chặng nối chuyến phải cùng hãng, nối tại cùng sân bay và cách nhau %d–%d phút"
				.formatted(minConnection, maxConnection));
		}
	}

	/** BR-04. */
	private static void checkReturn(PricedJourney outbound, PricedJourney inbound) {
		PricedSegment leave = outbound.segments().getFirst();
		PricedSegment land = outbound.segments().getLast();
		PricedSegment returnStart = inbound.segments().getFirst();
		PricedSegment returnEnd = inbound.segments().getLast();
		if (!returnStart.departureAirport().equals(land.arrivalAirport())
				|| !returnEnd.arrivalAirport().equals(leave.departureAirport())
				|| !returnStart.departureTime().isAfter(land.arrivalTime())) {
			throw invalid("Chiều về phải bay ngược tuyến chiều đi và cất cánh sau khi chiều đi hạ cánh");
		}
	}

	private static BusinessException invalid(String detail) {
		return new BusinessException(ErrorCode.ITINERARY_INVALID, detail);
	}

	private static BusinessException notBookable(String detail) {
		return new BusinessException(ErrorCode.FLIGHT_NOT_BOOKABLE, detail);
	}

	private static Flight flight(ResultSet rs, int rowNum) throws SQLException {
		return new Flight(rs.getLong("id"), rs.getString("flight_number"), rs.getString("airline_code"),
				rs.getString("status"), rs.getString("departure_airport"), rs.getString("arrival_airport"),
				local(rs, "departure_time", "departure_timezone"), local(rs, "arrival_time", "arrival_timezone"),
				rs.getBoolean("international"));
	}

	/** Đổi {@code timestamptz} sang giờ địa phương của sân bay. */
	private static OffsetDateTime local(ResultSet rs, String time, String zone) throws SQLException {
		return rs.getObject(time, OffsetDateTime.class)
			.atZoneSameInstant(ZoneId.of(rs.getString(zone)))
			.toOffsetDateTime();
	}

	private static Family family(ResultSet rs, int rowNum) throws SQLException {
		FareSnapshot snapshot = new FareSnapshot(rs.getLong("id"), rs.getString("name"),
				CabinClass.valueOf(rs.getString("cabin_class")), rs.getInt("carry_on_kg"),
				rs.getInt("checked_baggage_kg"), rs.getBoolean("refundable"), rs.getLong("refund_fee"),
				rs.getBoolean("changeable"), rs.getLong("change_fee"));
		return new Family(snapshot, rs.getString("airline_code"), rs.getBoolean("active"));
	}

	private static Fare fare(ResultSet rs, int rowNum) throws SQLException {
		return new Fare(rs.getLong("flight_id"), rs.getLong("price"), rs.getObject("available_seats", Integer.class));
	}

}
