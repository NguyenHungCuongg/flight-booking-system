package vn.edu.uit.flightbooking.booking.domain;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.identity.UserApi;
import vn.edu.uit.flightbooking.identity.UserSummary;

/**
 * Đọc booking cho C-08, C-09 (FR-40, FR-41) và S-01, S-02 (FR-70). Chỉ đọc. Đọc thẳng bảng {@code flights},
 * {@code airports} (flight, catalog), {@code vouchers} (promotion), {@code payments} (payment), {@code refund_requests}
 * và {@code reschedules} (aftersales) bằng SQL để lấy số hiệu, múi giờ, mã voucher và lịch sử thu.
 * Thông tin gói giá và giá luôn lấy từ snapshot của booking (BR-24). Chặng hiện hành xác định ở {@link #CURRENT_SEGMENT}.
 */
@Service
public class BookingQueries {

	/** Một chiều trên C-08: điểm đi, điểm đến cuối, giờ cất cánh chặng đầu theo giờ sân bay đi. */
	public record RouteView(Direction direction, String departureAirport, String arrivalAirport,
			OffsetDateTime departureTime) {
	}

	public record BookingSummary(String code, BookingStatus status, String contactName, String contactEmail,
			String contactPhone, long totalAmount, OffsetDateTime holdExpiresAt, OffsetDateTime createdAt,
			List<RouteView> routes) {
	}

	public record Contact(String name, String email, String phone) {
	}

	/** Giờ theo múi giờ sân bay tương ứng; {@code adultPrice}: giá người lớn đã chốt (BR-24). */
	public record SegmentView(long flightId, String flightNumber, String departureAirport,
			OffsetDateTime departureTime, String arrivalAirport, OffsetDateTime arrivalTime, String status,
			long adultPrice) {
	}

	/** {@code passengerIndex}: vị trí trong {@code passengers} của chi tiết, tính từ 0. */
	public record BaggageView(int passengerIndex, int weightKg, long price) {
	}

	/** Quy định gói giá là snapshot lúc giữ chỗ (BR-24); {@code discount}: phần mức giảm của chiều (BR-33). */
	public record JourneyView(Direction direction, String status, String airlineCode, String fareFamilyName,
			CabinClass cabinClass, int carryOnKg, int checkedBaggageKg, boolean refundable, long refundFee,
			boolean changeable, long changeFee, long discount, List<SegmentView> segments,
			List<BaggageView> baggage) {
	}

	/** {@code ticketNumber} là {@code null} cho tới khi xuất vé. */
	public record TicketView(Direction direction, String flightNumber, long fareAmount, String ticketNumber) {
	}

	public record PassengerView(PassengerType type, String lastName, String firstName, Gender gender,
			LocalDate dateOfBirth, String nationality, String passportNumber, LocalDate passportExpiry,
			List<TicketView> tickets) {
	}

	/** Mọi lượt thu và lượt hoàn của booking (FR-41, S-02), cũ nhất trước; giờ theo giờ Việt Nam. */
	public record PaymentView(String kind, String status, long amount, OffsetDateTime createdAt,
			OffsetDateTime completedAt) {
	}

	/** FR-41: {@code direction} là {@code null} với nguồn INVALID_PAYMENT; giờ theo giờ Việt Nam. */
	public record RefundRequestView(long id, Direction direction, String source, String status, long baseAmount,
			long feeAmount, long refundAmount, String reason, String staffNote, OffsetDateTime createdAt,
			OffsetDateTime completedAt) {
	}

	/** FR-41: yêu cầu đổi chuyến một chiều; {@code flightNumbers}: các chặng mới theo thứ tự; giờ theo giờ Việt Nam. */
	public record RescheduleView(long id, Direction direction, String status, long oldFareTotal, long newFareTotal,
			long changeFeeTotal, long amountDue, OffsetDateTime holdExpiresAt, OffsetDateTime createdAt,
			OffsetDateTime completedAt, List<String> flightNumbers) {
	}

	/** {@code reschedules}: các yêu cầu đổi chuyến, cũ nhất trước. */
	public record BookingDetail(String code, BookingStatus status, OffsetDateTime holdExpiresAt,
			OffsetDateTime issuedAt, OffsetDateTime createdAt, Contact contact, String voucherCode, long fareTotal,
			long baggageTotal, long discountTotal, long totalAmount, List<JourneyView> journeys,
			List<PassengerView> passengers, List<PaymentView> payments, List<RefundRequestView> refundRequests,
			List<RescheduleView> reschedules) {
	}

	/** S-02: chi tiết kèm tài khoản đã đặt. */
	public record StaffBookingDetail(UserSummary account, BookingDetail booking) {
	}

	private record Row(long id, long userId, String code, BookingStatus status, Contact contact, String voucherCode,
			long fareTotal, long baggageTotal, long discountTotal, long totalAmount, OffsetDateTime holdExpiresAt,
			OffsetDateTime issuedAt, OffsetDateTime createdAt) {
	}

	private record Loaded(long userId, BookingDetail detail) {
	}

	private record SummaryRow(long id, String code, BookingStatus status, String contactName, String contactEmail,
			String contactPhone, long totalAmount, OffsetDateTime holdExpiresAt, OffsetDateTime createdAt) {

		BookingSummary view(List<RouteView> routes) {
			return new BookingSummary(code, status, contactName, contactEmail, contactPhone, totalAmount,
					holdExpiresAt, createdAt, routes);
		}

	}

	private record Leg(long bookingId, Direction direction, String departureAirport, OffsetDateTime departureTime,
			String arrivalAirport) {
	}

	private record JourneyRow(long id, Direction direction, String status, String airlineCode, String fareFamilyName,
			CabinClass cabinClass, int carryOnKg, int checkedBaggageKg, boolean refundable, long refundFee,
			boolean changeable, long changeFee, long discount) {

		JourneyView view(List<SegmentView> segments, List<BaggageView> baggage) {
			return new JourneyView(direction, status, airlineCode, fareFamilyName, cabinClass, carryOnKg,
					checkedBaggageKg, refundable, refundFee, changeable, changeFee, discount, segments, baggage);
		}

	}

	private record SegmentRow(long id, long journeyId, SegmentView view) {
	}

	private record PassengerRow(long id, PassengerType type, String lastName, String firstName, Gender gender,
			LocalDate dateOfBirth, String nationality, String passportNumber, LocalDate passportExpiry) {

		PassengerView view(List<TicketView> tickets) {
			return new PassengerView(type, lastName, firstName, gender, dateOfBirth, nationality, passportNumber,
					passportExpiry, tickets);
		}

	}

	private record TicketRow(long passengerId, long segmentId, long fareAmount, String ticketNumber) {
	}

	private record BaggageRow(long journeyId, long passengerId, int weightKg, long price) {
	}

	/**
	 * Chặng hiện hành: ACTIVE, hoặc CANCELLED mà từng ACTIVE (của booking gốc, hoặc của lần đổi đã hoàn tất). Bỏ chặng
	 * REPLACED, PENDING_CHANGE và chặng mới của lần đổi quá hạn hoặc bị huỷ.
	 */
	private static final String CURRENT_SEGMENT = """
			(s.status = 'ACTIVE' OR s.status = 'CANCELLED' AND (s.reschedule_id IS NULL
			    OR EXISTS (SELECT 1 FROM reschedules r WHERE r.id = s.reschedule_id AND r.status = 'COMPLETED')))""";

	private static final String SUMMARY = """
			SELECT b.id, b.code, b.status, b.contact_name, b.contact_email, b.contact_phone, b.total_amount,
			       b.hold_expires_at, b.created_at
			FROM bookings b
			""";

	private static final String ROUTES = """
			SELECT j.booking_id, j.direction, f.departure_airport, dep.timezone AS departure_timezone,
			       f.departure_time, f.arrival_airport
			FROM booking_journeys j
			JOIN booking_segments s ON s.journey_id = j.id
			JOIN flights f ON f.id = s.flight_id
			JOIN airports dep ON dep.code = f.departure_airport
			WHERE j.booking_id IN (:ids) AND %s
			ORDER BY j.booking_id, j.direction, s.seq
			""".formatted(CURRENT_SEGMENT);

	private static final String BOOKING = """
			SELECT b.id, b.user_id, b.code, b.status, b.contact_name, b.contact_email, b.contact_phone,
			       v.code AS voucher_code, b.fare_total, b.baggage_total, b.discount_total, b.total_amount,
			       b.hold_expires_at, b.issued_at, b.created_at
			FROM bookings b
			LEFT JOIN vouchers v ON v.id = b.voucher_id
			WHERE b.code = :code
			""";

	private static final String JOURNEYS = """
			SELECT id, direction, status, airline_code, fare_family_name, cabin_class, carry_on_kg, checked_baggage_kg,
			       refundable, refund_fee, changeable, change_fee, discount_amount
			FROM booking_journeys
			WHERE booking_id = :id
			ORDER BY direction
			""";

	private static final String SEGMENTS = """
			SELECT s.id, s.journey_id, s.status, s.adult_price, f.id AS flight_id, f.flight_number,
			       f.departure_airport, dep.timezone AS departure_timezone, f.departure_time,
			       f.arrival_airport, arr.timezone AS arrival_timezone, f.arrival_time
			FROM booking_segments s
			JOIN booking_journeys j ON j.id = s.journey_id
			JOIN flights f ON f.id = s.flight_id
			JOIN airports dep ON dep.code = f.departure_airport
			JOIN airports arr ON arr.code = f.arrival_airport
			WHERE j.booking_id = :id AND %s
			ORDER BY s.journey_id, s.seq
			""".formatted(CURRENT_SEGMENT);

	private static final String PASSENGERS = """
			SELECT id, type, last_name, first_name, gender, date_of_birth, nationality, passport_number,
			       passport_expiry
			FROM passengers
			WHERE booking_id = :id
			ORDER BY id
			""";

	private static final String TICKETS = """
			SELECT t.passenger_id, t.segment_id, t.fare_amount, t.ticket_number
			FROM tickets t
			JOIN passengers p ON p.id = t.passenger_id
			WHERE p.booking_id = :id
			ORDER BY t.segment_id
			""";

	private static final String BAGGAGE = """
			SELECT bb.journey_id, bb.passenger_id, bb.weight_kg, bb.price
			FROM booking_baggage bb
			JOIN passengers p ON p.id = bb.passenger_id
			WHERE p.booking_id = :id
			ORDER BY bb.id
			""";

	private static final String PAYMENTS = """
			SELECT kind, status, amount, created_at, completed_at
			FROM payments
			WHERE booking_id = :id
			ORDER BY created_at, id
			""";

	private static final String REFUND_REQUESTS = """
			SELECT r.id, j.direction, r.source, r.status, r.base_amount, r.fee_amount, r.refund_amount, r.reason,
			       r.staff_note, r.created_at, r.completed_at
			FROM refund_requests r
			LEFT JOIN booking_journeys j ON j.id = r.journey_id
			WHERE r.booking_id = :id
			ORDER BY r.created_at, r.id
			""";

	private static final String RESCHEDULES = """
			SELECT r.id, j.direction, r.status, r.old_fare_total, r.new_fare_total, r.change_fee_total, r.amount_due,
			       r.hold_expires_at, r.created_at, r.completed_at,
			       (SELECT string_agg(f.flight_number, ',' ORDER BY s.seq)
			        FROM booking_segments s JOIN flights f ON f.id = s.flight_id
			        WHERE s.reschedule_id = r.id) AS flight_numbers
			FROM reschedules r
			JOIN booking_journeys j ON j.id = r.journey_id
			WHERE r.booking_id = :id
			ORDER BY r.created_at, r.id
			""";

	private final JdbcClient jdbc;

	private final UserApi users;

	BookingQueries(JdbcClient jdbc, UserApi users) {
		this.jdbc = jdbc;
		this.users = users;
	}

	/** FR-40: mới nhất trước, lọc theo trạng thái nếu có. */
	@Transactional(readOnly = true)
	public Page<BookingSummary> mine(long userId, BookingStatus status, Pageable pageable) {
		StringBuilder where = new StringBuilder(" WHERE b.user_id = :userId");
		Map<String, Object> params = new HashMap<>(Map.of("userId", userId));
		filterStatus(where, params, status);
		return page(where, params, pageable);
	}

	/**
	 * FR-70: {@code q} khớp nguyên văn với mã đặt chỗ hoặc email liên hệ (không phân biệt hoa thường), hoặc số điện
	 * thoại liên hệ. Không có {@code q} thì liệt kê mọi booking.
	 */
	@Transactional(readOnly = true)
	public Page<BookingSummary> search(String q, BookingStatus status, Pageable pageable) {
		StringBuilder where = new StringBuilder(" WHERE TRUE");
		Map<String, Object> params = new HashMap<>();
		if (q != null && !q.isBlank()) {
			where.append(" AND (b.code = upper(:q) OR lower(b.contact_email) = lower(:q) OR b.contact_phone = :q)");
			params.put("q", q.strip());
		}
		filterStatus(where, params, status);
		return page(where, params, pageable);
	}

	/** FR-41, BR-104: booking của người khác cũng báo không tìm thấy. */
	@Transactional(readOnly = true)
	public BookingDetail detail(long userId, String code) {
		return load(code).filter(loaded -> loaded.userId() == userId)
			.map(Loaded::detail)
			.orElseThrow(BookingCancellation::notFound);
	}

	/** FR-70, S-02. */
	@Transactional(readOnly = true)
	public StaffBookingDetail staffDetail(String code) {
		Loaded loaded = load(code).orElseThrow(BookingCancellation::notFound);
		return new StaffBookingDetail(users.get(loaded.userId()), loaded.detail());
	}

	private static void filterStatus(StringBuilder where, Map<String, Object> params, BookingStatus status) {
		if (status != null) {
			where.append(" AND b.status = :status");
			params.put("status", status.name());
		}
	}

	private Page<BookingSummary> page(StringBuilder where, Map<String, Object> params, Pageable pageable) {
		long total = jdbc.sql("SELECT count(*) FROM bookings b" + where).params(params).query(Long.class).single();
		params.put("limit", pageable.getPageSize());
		params.put("offset", pageable.getOffset());
		List<SummaryRow> rows = jdbc
			.sql(SUMMARY + where + " ORDER BY b.created_at DESC, b.id DESC LIMIT :limit OFFSET :offset")
			.params(params)
			.query(BookingQueries::summary)
			.list();
		Map<Long, List<RouteView>> routes = routes(rows.stream().map(SummaryRow::id).toList());
		return new PageImpl<>(rows.stream().map(r -> r.view(routes.getOrDefault(r.id(), List.of()))).toList(),
				pageable, total);
	}

	/** Gộp các chặng theo booking và chiều: điểm đi và giờ cất cánh của chặng đầu, điểm đến của chặng cuối. */
	private Map<Long, List<RouteView>> routes(List<Long> bookingIds) {
		if (bookingIds.isEmpty()) {
			return Map.of();
		}
		List<Leg> legs = jdbc.sql(ROUTES)
			.param("ids", bookingIds)
			.query((rs, rowNum) -> new Leg(rs.getLong("booking_id"), Direction.valueOf(rs.getString("direction")),
					rs.getString("departure_airport"), local(rs, "departure_time", "departure_timezone"),
					rs.getString("arrival_airport")))
			.list();
		Map<Long, List<RouteView>> routes = new HashMap<>();
		Leg first = null;
		for (int i = 0; i < legs.size(); i++) {
			Leg leg = legs.get(i);
			if (first == null) {
				first = leg;
			}
			Leg next = i + 1 < legs.size() ? legs.get(i + 1) : null;
			if (next == null || next.bookingId() != leg.bookingId() || next.direction() != leg.direction()) {
				routes.computeIfAbsent(leg.bookingId(), k -> new ArrayList<>())
					.add(new RouteView(leg.direction(), first.departureAirport(), leg.arrivalAirport(),
							first.departureTime()));
				first = null;
			}
		}
		return routes;
	}

	private Optional<Loaded> load(String code) {
		return jdbc.sql(BOOKING)
			.param("code", code.toUpperCase(Locale.ROOT))
			.query(BookingQueries::row)
			.optional()
			.map(row -> new Loaded(row.userId(), detail(row)));
	}

	private BookingDetail detail(Row booking) {
		Map<String, Object> id = Map.of("id", booking.id());
		List<JourneyRow> journeys = jdbc.sql(JOURNEYS).params(id).query(BookingQueries::journey).list();
		List<SegmentRow> segments = jdbc.sql(SEGMENTS).params(id).query(BookingQueries::segment).list();
		List<PassengerRow> passengers = jdbc.sql(PASSENGERS).params(id).query(BookingQueries::passenger).list();
		List<TicketRow> tickets = jdbc.sql(TICKETS)
			.params(id)
			.query((rs, n) -> new TicketRow(rs.getLong("passenger_id"), rs.getLong("segment_id"),
					rs.getLong("fare_amount"), rs.getString("ticket_number")))
			.list();
		List<BaggageRow> baggage = jdbc.sql(BAGGAGE)
			.params(id)
			.query((rs, n) -> new BaggageRow(rs.getLong("journey_id"), rs.getLong("passenger_id"),
					rs.getInt("weight_kg"), rs.getLong("price")))
			.list();

		List<PaymentView> payments = jdbc.sql(PAYMENTS)
			.params(id)
			.query((rs, n) -> new PaymentView(rs.getString("kind"), rs.getString("status"), rs.getLong("amount"),
					vietnam(rs, "created_at"), vietnam(rs, "completed_at")))
			.list();
		List<RefundRequestView> refundRequests = jdbc.sql(REFUND_REQUESTS)
			.params(id)
			.query((rs, n) -> new RefundRequestView(rs.getLong("id"),
					rs.getString("direction") == null ? null : Direction.valueOf(rs.getString("direction")),
					rs.getString("source"), rs.getString("status"), rs.getLong("base_amount"),
					rs.getLong("fee_amount"), rs.getLong("refund_amount"), rs.getString("reason"),
					rs.getString("staff_note"), vietnam(rs, "created_at"), vietnam(rs, "completed_at")))
			.list();
		List<RescheduleView> reschedules = jdbc.sql(RESCHEDULES)
			.params(id)
			.query((rs, n) -> new RescheduleView(rs.getLong("id"), Direction.valueOf(rs.getString("direction")),
					rs.getString("status"), rs.getLong("old_fare_total"), rs.getLong("new_fare_total"),
					rs.getLong("change_fee_total"), rs.getLong("amount_due"), vietnam(rs, "hold_expires_at"),
					vietnam(rs, "created_at"), vietnam(rs, "completed_at"),
					rs.getString("flight_numbers") == null ? List.of() : List.of(rs.getString("flight_numbers").split(","))))
			.list();

		Map<Long, Integer> passengerIndex = new HashMap<>();
		for (int i = 0; i < passengers.size(); i++) {
			passengerIndex.put(passengers.get(i).id(), i);
		}
		Map<Long, Direction> directionOf = journeys.stream()
			.collect(Collectors.toMap(JourneyRow::id, JourneyRow::direction));
		Map<Long, SegmentRow> segmentById = segments.stream()
			.collect(Collectors.toMap(SegmentRow::id, Function.identity()));

		List<JourneyView> journeyViews = journeys.stream()
			.map(j -> j.view(
					segments.stream().filter(s -> s.journeyId() == j.id()).map(SegmentRow::view).toList(),
					baggage.stream()
						.filter(b -> b.journeyId() == j.id())
						.map(b -> new BaggageView(passengerIndex.get(b.passengerId()), b.weightKg(), b.price()))
						.toList()))
			.toList();
		List<PassengerView> passengerViews = passengers.stream()
			.map(p -> p.view(tickets.stream()
				.filter(t -> t.passengerId() == p.id() && segmentById.containsKey(t.segmentId()))
				.map(t -> {
					SegmentRow s = segmentById.get(t.segmentId());
					return new TicketView(directionOf.get(s.journeyId()), s.view().flightNumber(), t.fareAmount(),
							t.ticketNumber());
				})
				.toList()))
			.toList();
		return new BookingDetail(booking.code(), booking.status(), booking.holdExpiresAt(), booking.issuedAt(),
				booking.createdAt(), booking.contact(), booking.voucherCode(), booking.fareTotal(),
				booking.baggageTotal(), booking.discountTotal(), booking.totalAmount(), journeyViews, passengerViews,
				payments, refundRequests, reschedules);
	}

	private static SummaryRow summary(ResultSet rs, int rowNum) throws SQLException {
		return new SummaryRow(rs.getLong("id"), rs.getString("code"), BookingStatus.valueOf(rs.getString("status")),
				rs.getString("contact_name"), rs.getString("contact_email"), rs.getString("contact_phone"),
				rs.getLong("total_amount"), vietnam(rs, "hold_expires_at"), vietnam(rs, "created_at"));
	}

	private static Row row(ResultSet rs, int rowNum) throws SQLException {
		return new Row(rs.getLong("id"), rs.getLong("user_id"), rs.getString("code"),
				BookingStatus.valueOf(rs.getString("status")),
				new Contact(rs.getString("contact_name"), rs.getString("contact_email"), rs.getString("contact_phone")),
				rs.getString("voucher_code"), rs.getLong("fare_total"), rs.getLong("baggage_total"),
				rs.getLong("discount_total"), rs.getLong("total_amount"), vietnam(rs, "hold_expires_at"),
				vietnam(rs, "issued_at"), vietnam(rs, "created_at"));
	}

	private static JourneyRow journey(ResultSet rs, int rowNum) throws SQLException {
		return new JourneyRow(rs.getLong("id"), Direction.valueOf(rs.getString("direction")), rs.getString("status"),
				rs.getString("airline_code"), rs.getString("fare_family_name"),
				CabinClass.valueOf(rs.getString("cabin_class")), rs.getInt("carry_on_kg"),
				rs.getInt("checked_baggage_kg"), rs.getBoolean("refundable"), rs.getLong("refund_fee"),
				rs.getBoolean("changeable"), rs.getLong("change_fee"), rs.getLong("discount_amount"));
	}

	private static SegmentRow segment(ResultSet rs, int rowNum) throws SQLException {
		return new SegmentRow(rs.getLong("id"), rs.getLong("journey_id"),
				new SegmentView(rs.getLong("flight_id"), rs.getString("flight_number"),
						rs.getString("departure_airport"), local(rs, "departure_time", "departure_timezone"),
						rs.getString("arrival_airport"), local(rs, "arrival_time", "arrival_timezone"),
						rs.getString("status"), rs.getLong("adult_price")));
	}

	private static PassengerRow passenger(ResultSet rs, int rowNum) throws SQLException {
		return new PassengerRow(rs.getLong("id"), PassengerType.valueOf(rs.getString("type")),
				rs.getString("last_name"), rs.getString("first_name"), Gender.valueOf(rs.getString("gender")),
				rs.getObject("date_of_birth", LocalDate.class), rs.getString("nationality"),
				rs.getString("passport_number"), rs.getObject("passport_expiry", LocalDate.class));
	}

	/** Đổi {@code timestamptz} sang giờ địa phương của sân bay (TDD §5.1). */
	private static OffsetDateTime local(ResultSet rs, String time, String zone) throws SQLException {
		return rs.getObject(time, OffsetDateTime.class)
			.atZoneSameInstant(ZoneId.of(rs.getString(zone)))
			.toOffsetDateTime();
	}

	/** Giờ của booking theo giờ Việt Nam; {@code null} giữ nguyên (VD {@code issued_at} chưa xuất vé). */
	private static OffsetDateTime vietnam(ResultSet rs, String column) throws SQLException {
		OffsetDateTime time = rs.getObject(column, OffsetDateTime.class);
		return time == null ? null : time.atZoneSameInstant(BookingService.VIETNAM).toOffsetDateTime();
	}

}
