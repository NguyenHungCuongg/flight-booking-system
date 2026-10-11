package vn.edu.uit.flightbooking.flight.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.catalog.CatalogApi;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.flight.FlightTimeChanged;

/**
 * Tạo, sửa, xoá chuyến (FR-90, FR-92, FR-94; BR-80–82, BR-85–87). Dùng JdbcClient thay vì entity: số ghế chỉ được
 * đổi bằng UPDATE có điều kiện (TDD §5.4), và tạo hàng loạt cần {@code ON CONFLICT DO NOTHING} để bỏ qua ngày trùng.
 */
@Service
public class FlightService {

	/** BR-85: khoảng ngày tối đa 180 ngày, tính cả ngày đầu và ngày cuối. */
	static final int MAX_DAYS = 180;

	public record CabinSeats(@NotNull CabinClass cabinClass, @NotNull @Min(1) @Max(999) Integer totalSeats) {
	}

	/** {@code price}: giá người lớn của chặng (BR-20). */
	public record FarePrice(@NotNull Long fareFamilyId, @NotNull @Min(1) Long price) {
	}

	/**
	 * Body của POST /admin/flights (FR-90). {@code departureTime} là giờ địa phương của sân bay đi. Khoảng ngày chỉ
	 * gồm một ngày nghĩa là tạo chuyến lẻ.
	 */
	public record NewFlights(@NotBlank String airlineCode, @NotBlank String flightNumber,
			@NotBlank String departureAirport, @NotBlank String arrivalAirport, @NotNull LocalTime departureTime,
			@NotNull @Min(1) @Max(1440) Integer durationMinutes, @NotNull LocalDate fromDate, @NotNull LocalDate toDate,
			@NotEmpty Set<DayOfWeek> daysOfWeek, @NotBlank @Size(max = 50) String aircraftModel,
			@NotEmpty List<@Valid CabinSeats> cabins, @NotEmpty List<@Valid FarePrice> fares) {
	}

	/** Kết quả hiện ở A-05: số chuyến tạo được và số ngày bị bỏ qua vì trùng (BR-85). */
	public record CreatedFlights(int created, int skipped, List<Long> flightIds) {
	}

	/**
	 * Body của PUT /admin/flights/{id} (FR-92). Giờ là giờ địa phương của sân bay tương ứng (BR-81). Hãng, số hiệu và
	 * tuyến không đổi được.
	 */
	public record FlightChanges(@NotNull LocalDateTime departureTime, @NotNull LocalDateTime arrivalTime,
			@NotBlank @Size(max = 50) String aircraftModel, @NotEmpty List<@Valid CabinSeats> cabins,
			@NotEmpty List<@Valid FarePrice> fares) {
	}

	private record Current(String airlineCode, String departureAirport, String arrivalAirport, Instant departureTime,
			Instant arrivalTime, FlightStatus status) {
	}

	private final JdbcClient jdbc;

	private final CatalogApi catalog;

	private final ApplicationEventPublisher events;

	FlightService(JdbcClient jdbc, CatalogApi catalog, ApplicationEventPublisher events) {
		this.jdbc = jdbc;
		this.catalog = catalog;
		this.events = events;
	}

	/** FR-90, BR-85: mỗi ngày trong khoảng rơi vào các thứ đã chọn tạo một chuyến; ngày đã có chuyến trùng thì bỏ qua. */
	@Transactional
	public CreatedFlights create(NewFlights in) {
		if (!catalog.airlineActive(in.airlineCode())) {
			throw BusinessException.invalidField("airlineCode", "Hãng không tồn tại hoặc đã ngừng hoạt động");
		}
		if (!in.flightNumber().matches("[A-Z0-9]{2}[0-9]{1,4}") || !in.flightNumber().startsWith(in.airlineCode())) {
			throw BusinessException.invalidField("flightNumber",
					"Số hiệu gồm mã hãng và 1–4 chữ số, VD " + in.airlineCode() + "213");
		}
		ZoneId zone = activeAirportZone("departureAirport", in.departureAirport());
		activeAirportZone("arrivalAirport", in.arrivalAirport());
		if (in.departureAirport().equals(in.arrivalAirport())) {
			throw BusinessException.invalidField("arrivalAirport", "Sân bay đến phải khác sân bay đi");
		}
		if (in.toDate().isBefore(in.fromDate())) {
			throw BusinessException.invalidField("toDate", "Ngày kết thúc không được trước ngày bắt đầu");
		}
		if (ChronoUnit.DAYS.between(in.fromDate(), in.toDate()) >= MAX_DAYS) {
			throw BusinessException.invalidField("toDate", "Khoảng ngày tối đa " + MAX_DAYS + " ngày");
		}
		checkCabinsAndFares(in.airlineCode(), in.cabins(), in.fares());

		List<Long> ids = new ArrayList<>();
		int skipped = 0;
		for (LocalDate day = in.fromDate(); !day.isAfter(in.toDate()); day = day.plusDays(1)) {
			if (!in.daysOfWeek().contains(day.getDayOfWeek())) {
				continue;
			}
			Instant departure = ZonedDateTime.of(day, in.departureTime(), zone).toInstant();
			if (!departure.isAfter(Instant.now())) {
				throw BusinessException.invalidField("fromDate", "Không tạo được chuyến cất cánh trong quá khứ");
			}
			Optional<Long> id = jdbc.sql("""
					INSERT INTO flights (airline_code, flight_number, departure_airport, arrival_airport,
					                     departure_time, arrival_time, aircraft_model)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					ON CONFLICT (airline_code, flight_number, departure_time) DO NOTHING
					RETURNING id
					""")
				.params(in.airlineCode(), in.flightNumber(), in.departureAirport(), in.arrivalAirport(), utc(departure),
						utc(departure.plus(in.durationMinutes(), ChronoUnit.MINUTES)), in.aircraftModel())
				.query(Long.class)
				.optional();
			if (id.isEmpty()) {
				skipped++;
				continue;
			}
			for (CabinSeats cabin : in.cabins()) {
				insertCabin(id.get(), cabin);
			}
			insertFares(id.get(), in.fares());
			ids.add(id.get());
		}
		return new CreatedFlights(ids.size(), skipped, ids);
	}

	/**
	 * FR-92. Khoá chuyến {@code FOR UPDATE} trước (thứ tự khoá TDD §5.4), nên không booking nào giữ ghế chen vào
	 * giữa lúc kiểm tra và lúc ghi. Đổi giờ thì phát {@link FlightTimeChanged} (BR-83).
	 */
	@Transactional
	public void update(long id, FlightChanges in) {
		Current current = jdbc.sql("""
				SELECT airline_code, departure_airport, arrival_airport, departure_time, arrival_time, status
				FROM flights WHERE id = ? FOR UPDATE
				""")
			.param(id)
			.query((rs, n) -> new Current(rs.getString("airline_code"), rs.getString("departure_airport"),
					rs.getString("arrival_airport"), rs.getObject("departure_time", OffsetDateTime.class).toInstant(),
					rs.getObject("arrival_time", OffsetDateTime.class).toInstant(),
					FlightStatus.valueOf(rs.getString("status"))))
			.optional()
			.orElseThrow(FlightService::notFound);
		if (current.status() == FlightStatus.CANCELLED) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "Chuyến đã huỷ nên không sửa được");
		}
		if (!current.departureTime().isAfter(Instant.now())) {
			throw new BusinessException(ErrorCode.DEADLINE_PASSED, "Chuyến đã cất cánh nên không sửa được");
		}
		Instant departure = in.departureTime().atZone(zone(current.departureAirport())).toInstant();
		Instant arrival = in.arrivalTime().atZone(zone(current.arrivalAirport())).toInstant();
		if (!departure.isAfter(Instant.now())) {
			throw BusinessException.invalidField("departureTime", "Giờ cất cánh phải sau thời điểm hiện tại");
		}
		if (!arrival.isAfter(departure)) {
			throw BusinessException.invalidField("arrivalTime", "Giờ hạ cánh phải sau giờ cất cánh");
		}
		checkCabinsAndFares(current.airlineCode(), in.cabins(), in.fares());

		try {
			jdbc.sql("""
					UPDATE flights SET departure_time = ?, arrival_time = ?, aircraft_model = ?, updated_at = now()
					WHERE id = ?
					""").params(utc(departure), utc(arrival), in.aircraftModel(), id).update();
		}
		catch (DuplicateKeyException e) {
			throw BusinessException.invalidField("departureTime", "Đã có chuyến cùng số hiệu cất cánh đúng giờ này");
		}
		updateCabins(id, in.cabins());
		jdbc.sql("DELETE FROM flight_fares WHERE flight_id = ?").param(id).update();
		insertFares(id, in.fares());
		if (!departure.equals(current.departureTime()) || !arrival.equals(current.arrivalTime())) {
			events.publishEvent(new FlightTimeChanged(id));
		}
	}

	/** FR-94, BR-82: chuyến đã từng có booking thì khoá ngoại của booking_segments chặn xoá. */
	@Transactional
	public void delete(long id) {
		try {
			if (jdbc.sql("DELETE FROM flights WHERE id = ?").param(id).update() == 0) {
				throw notFound();
			}
		}
		catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.FLIGHT_HAS_BOOKINGS, "Chuyến đã có booking nên không xoá được");
		}
	}

	/**
	 * BR-82: đổi tổng ghế bằng Q-03, bỏ hạng ghế chỉ khi chưa có ghế nào bán ra hoặc đang giữ. Cả hai là câu lệnh có
	 * điều kiện, nên đúng cả khi có booking đang giữ ghế cùng lúc.
	 */
	private void updateCabins(long id, List<CabinSeats> cabins) {
		Map<CabinClass, Integer> current = new HashMap<>();
		jdbc.sql("SELECT cabin_class, total_seats FROM flight_cabins WHERE flight_id = ?")
			.param(id)
			.query(rs -> {
				current.put(CabinClass.valueOf(rs.getString("cabin_class")), rs.getInt("total_seats"));
			});
		for (CabinSeats cabin : cabins) {
			Integer total = current.remove(cabin.cabinClass());
			if (total == null) {
				insertCabin(id, cabin);
			}
			else if (!total.equals(cabin.totalSeats()) && jdbc.sql("""
					UPDATE flight_cabins
					SET available_seats = available_seats + (:newTotal - total_seats), total_seats = :newTotal
					WHERE flight_id = :flightId AND cabin_class = :cabin
					  AND available_seats + (:newTotal - total_seats) >= 0
					""")
				.param("newTotal", cabin.totalSeats())
				.param("flightId", id)
				.param("cabin", cabin.cabinClass().name())
				.update() == 0) {
				throw new BusinessException(ErrorCode.SEAT_COUNT_BELOW_SOLD,
						"Hạng " + cabin.cabinClass() + " đã bán hoặc đang giữ nhiều hơn " + cabin.totalSeats() + " ghế");
			}
		}
		for (CabinClass removed : current.keySet()) {
			if (jdbc.sql("""
					DELETE FROM flight_cabins
					WHERE flight_id = ? AND cabin_class = ? AND available_seats = total_seats
					""").params(id, removed.name()).update() == 0) {
				throw new BusinessException(ErrorCode.SEAT_COUNT_BELOW_SOLD,
						"Không bỏ được hạng " + removed + " vì đã có ghế bán ra hoặc đang giữ");
			}
		}
	}

	/** BR-81: hạng ghế không lặp; mỗi giá bán thuộc một gói giá của đúng hãng, và hạng ghế của gói có trên chuyến. */
	private void checkCabinsAndFares(String airlineCode, List<CabinSeats> cabins, List<FarePrice> fares) {
		Set<CabinClass> classes = new HashSet<>();
		for (int i = 0; i < cabins.size(); i++) {
			if (!classes.add(cabins.get(i).cabinClass())) {
				throw BusinessException.invalidField("cabins[" + i + "].cabinClass", "Hạng ghế bị lặp");
			}
		}
		Map<Long, CabinClass> fareCabins = catalog.fareFamilyCabins(airlineCode);
		Set<Long> seen = new HashSet<>();
		for (int i = 0; i < fares.size(); i++) {
			String field = "fares[" + i + "].fareFamilyId";
			Long fareFamilyId = fares.get(i).fareFamilyId();
			CabinClass cabin = fareCabins.get(fareFamilyId);
			if (cabin == null) {
				throw BusinessException.invalidField(field, "Gói giá không thuộc hãng " + airlineCode);
			}
			if (!classes.contains(cabin)) {
				throw BusinessException.invalidField(field, "Chuyến không có hạng " + cabin + " của gói giá này");
			}
			if (!seen.add(fareFamilyId)) {
				throw BusinessException.invalidField(field, "Gói giá bị lặp");
			}
		}
	}

	/** BR-91: sân bay ngừng kích hoạt không chọn được cho chuyến mới. */
	private ZoneId activeAirportZone(String field, String code) {
		return catalog.airport(code)
			.filter(CatalogApi.AirportRef::active)
			.orElseThrow(() -> BusinessException.invalidField(field, "Sân bay không tồn tại hoặc đã ngừng hoạt động"))
			.zone();
	}

	/** Sân bay của chuyến đã có luôn tồn tại (khoá ngoại), kể cả khi đã ngừng kích hoạt. */
	private ZoneId zone(String airportCode) {
		return catalog.airport(airportCode).orElseThrow().zone();
	}

	private void insertCabin(long flightId, CabinSeats cabin) {
		jdbc.sql("""
				INSERT INTO flight_cabins (flight_id, cabin_class, total_seats, available_seats) VALUES (?, ?, ?, ?)
				""").params(flightId, cabin.cabinClass().name(), cabin.totalSeats(), cabin.totalSeats()).update();
	}

	private void insertFares(long flightId, List<FarePrice> fares) {
		for (FarePrice fare : fares) {
			jdbc.sql("INSERT INTO flight_fares (flight_id, fare_family_id, price) VALUES (?, ?, ?)")
				.params(flightId, fare.fareFamilyId(), fare.price())
				.update();
		}
	}

	/** PostgreSQL JDBC nhận OffsetDateTime cho cột timestamptz. */
	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	static BusinessException notFound() {
		return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chuyến bay");
	}

}
