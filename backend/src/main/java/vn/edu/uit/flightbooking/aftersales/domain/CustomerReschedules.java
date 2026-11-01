package vn.edu.uit.flightbooking.aftersales.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.aftersales.domain.JourneyRules.Action;
import vn.edu.uit.flightbooking.booking.BookingApi;
import vn.edu.uit.flightbooking.booking.BookingApi.ChangeRef;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;
import vn.edu.uit.flightbooking.flight.FlightApi;
import vn.edu.uit.flightbooking.flight.FlightApi.JourneyChoice;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedJourney;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedSegment;
import vn.edu.uit.flightbooking.flight.FlightSearch;
import vn.edu.uit.flightbooking.flight.FlightSearch.Itinerary;
import vn.edu.uit.flightbooking.flight.FlightSearch.SearchQuery;
import vn.edu.uit.flightbooking.flight.SeatInventory;
import vn.edu.uit.flightbooking.payment.PaymentApi;

/** FR-60–62: khách xem hành trình thay thế, báo giá, xác nhận và thanh toán đổi một chiều (BR-70–74). */
@Service
public class CustomerReschedules {

	private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

	private final BookingApi bookings;

	private final FlightSearch search;

	private final FlightApi flights;

	private final SettingsApi settings;

	private final JourneyRules rules;

	private final JdbcClient jdbc;

	private final SeatInventory seats;

	private final PaymentApi payments;

	private final Reschedules reschedules;

	CustomerReschedules(BookingApi bookings, FlightSearch search, FlightApi flights, SettingsApi settings,
			JourneyRules rules, JdbcClient jdbc, SeatInventory seats, PaymentApi payments, Reschedules reschedules) {
		this.bookings = bookings;
		this.search = search;
		this.flights = flights;
		this.settings = settings;
		this.rules = rules;
		this.jdbc = jdbc;
		this.seats = seats;
		this.payments = payments;
		this.reschedules = reschedules;
	}

	/** Cùng hãng, cùng gói giá, khác hành trình hiện tại, đúng thứ tự với chiều còn lại (BR-04, BR-71). */
	@Transactional(readOnly = true)
	public RescheduleOptions options(long userId, String code, String direction, LocalDate date) {
		ChangeRef ref = bookings.change(userId, code, direction).orElseThrow(CustomerReschedules::notFound);
		rules.check(ref.journey(), Action.CHANGE);
		var result = search.search(new SearchQuery(ref.origin(), ref.destination(), date, ref.adults(),
				ref.children(), ref.infants(), ref.cabinClass()));
		List<RescheduleOption> options = result.itineraries()
			.stream()
			.filter(i -> !flightIds(i).equals(ref.flightIds()))
			.filter(i -> keepsOrder(ref, i.segments().getFirst().departure().time().toInstant(),
					i.segments().getLast().arrival().time().toInstant()))
			.flatMap(i -> i.fareOptions()
				.stream()
				.filter(f -> f.fareFamilyId() == ref.fareFamilyId())
				.map(f -> new RescheduleOption(
						new Itinerary(i.stops(), i.totalDurationMinutes(), i.segments(), List.of(f)),
						RescheduleQuote.of(ref.fareTotal(), f.totalPrice(), ref.journey().changeFee(),
								ref.journey().seated()))))
			.sorted(Comparator.comparingLong((RescheduleOption o) -> o.quote().amountDue())
				.thenComparing(o -> o.itinerary().segments().getLast().arrival().time().toInstant()))
			.toList();
		return new RescheduleOptions(options);
	}

	@Transactional(readOnly = true)
	public RescheduleQuote quote(long userId, String code, String direction, List<Long> flightIds) {
		ChangeRef ref = bookings.change(userId, code, direction).orElseThrow(CustomerReschedules::notFound);
		rules.check(ref.journey(), Action.CHANGE);
		PricedJourney priced = validate(ref, flightIds);
		return quoteOf(ref, priced, settings.getInt(SettingKey.PRICING_CHILD_PERCENT),
				settings.getInt(SettingKey.PRICING_INFANT_PERCENT));
	}

	/**
	 * FR-61, BR-73: khoá chuyến mới, rồi booking, chèn yêu cầu, giữ ghế (TDD §5.4). Không phải trả gì thì hoàn tất
	 * ngay, nếu không chờ thanh toán đến hết hạn giữ chỗ.
	 */
	@Transactional
	public RescheduleCreatedResponse confirm(long userId, String code, String direction, List<Long> flightIds) {
		seats.lockFlights(flightIds.stream().distinct().sorted().toList());
		ChangeRef ref = bookings.lockChange(userId, code, direction).orElseThrow(CustomerReschedules::notFound);
		rules.check(ref.journey(), Action.CHANGE);
		PricedJourney priced = validate(ref, flightIds);
		int childPercent = settings.getInt(SettingKey.PRICING_CHILD_PERCENT);
		int infantPercent = settings.getInt(SettingKey.PRICING_INFANT_PERCENT);
		RescheduleQuote quote = quoteOf(ref, priced, childPercent, infantPercent);
		Instant holdExpiresAt = Instant.now().plus(settings.getInt(SettingKey.BOOKING_HOLD_MINUTES), ChronoUnit.MINUTES);
		long id;
		try {
			id = jdbc.sql("""
					INSERT INTO reschedules (booking_id, journey_id, status, old_fare_total, new_fare_total,
					                         change_fee_total, amount_due, hold_expires_at)
					VALUES (:bookingId, :journeyId, 'PENDING_PAYMENT', :old, :new, :fee, :due, :holdExpiresAt)
					RETURNING id
					""")
				.param("bookingId", ref.journey().bookingId())
				.param("journeyId", ref.journey().journeyId())
				.param("old", quote.oldFareTotal())
				.param("new", quote.newFareTotal())
				.param("fee", quote.changeFeeTotal())
				.param("due", quote.amountDue())
				.param("holdExpiresAt", holdExpiresAt.atOffset(ZoneOffset.UTC))
				.query(Long.class)
				.single();
		}
		catch (DuplicateKeyException e) {
			throw JourneyRules.inProgress();
		}
		bookings.holdChange(ref.journey().journeyId(), id, priced.segments(), childPercent, infantPercent);
		RescheduleStatus status = RescheduleStatus.PENDING_PAYMENT;
		if (quote.amountDue() == 0) {
			reschedules.complete(id);
			status = RescheduleStatus.COMPLETED;
		}
		return new RescheduleCreatedResponse(id, status, quote.amountDue(),
				holdExpiresAt.atZone(VIETNAM).toOffsetDateTime());
	}

	/**
	 * FR-62: chỉ yêu cầu của chính khách, đang chờ thanh toán và còn trong hạn giữ chỗ; thử lại được nhiều lần. Không
	 * khoá: nếu yêu cầu hết hạn ngay sau đó, khoản tiền trả về sau rơi vào BR-44.
	 */
	@Transactional
	public String pay(long userId, long rescheduleId, String clientIp) {
		record Payable(long bookingId, RescheduleStatus status, long amountDue, Instant holdExpiresAt, long userId,
				String code) {
		}
		Payable r = jdbc.sql("""
				SELECT r.booking_id, r.status, r.amount_due, r.hold_expires_at, b.user_id, b.code
				FROM reschedules r JOIN bookings b ON b.id = r.booking_id WHERE r.id = ?
				""")
			.param(rescheduleId)
			.query((rs, n) -> new Payable(rs.getLong("booking_id"), RescheduleStatus.valueOf(rs.getString("status")),
					rs.getLong("amount_due"), rs.getObject("hold_expires_at", OffsetDateTime.class).toInstant(),
					rs.getLong("user_id"), rs.getString("code")))
			.optional()
			.filter(p -> p.userId() == userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy yêu cầu đổi chuyến"));
		boolean pending = r.status() == RescheduleStatus.PENDING_PAYMENT;
		if (r.status() == RescheduleStatus.EXPIRED || pending && !Instant.now().isBefore(r.holdExpiresAt())) {
			throw new BusinessException(ErrorCode.HOLD_EXPIRED,
					"Yêu cầu đổi chuyến đã quá hạn thanh toán, vui lòng đổi lại");
		}
		if (!pending) {
			throw new BusinessException(ErrorCode.INVALID_STATE,
					"Chỉ thanh toán được yêu cầu đổi chuyến đang chờ thanh toán");
		}
		return payments.charge(r.bookingId(), rescheduleId, r.amountDue(), r.holdExpiresAt(),
				"Thanh toan doi chuyen " + r.code(), clientIp);
	}

	private static RescheduleQuote quoteOf(ChangeRef ref, PricedJourney priced, int childPercent, int infantPercent) {
		long newFareTotal = FlightSearch.totalPrice(priced.segments().stream().map(PricedSegment::adultPrice).toList(),
				ref.adults(), ref.children(), ref.infants(), childPercent, infantPercent);
		return RescheduleQuote.of(ref.fareTotal(), newFareTotal, ref.journey().changeFee(), ref.journey().seated());
	}

	/** BR-71, theo thứ tự: khác hiện tại, {@link FlightApi#price}, cùng tuyến, đúng thứ tự với chiều còn lại. */
	private PricedJourney validate(ChangeRef ref, List<Long> flightIds) {
		if (flightIds.equals(ref.flightIds())) {
			throw invalid("Hành trình mới trùng hành trình hiện tại");
		}
		PricedJourney priced = flights
			.price(List.of(new JourneyChoice(flightIds, ref.fareFamilyId())), ref.journey().seated())
			.getFirst();
		List<PricedSegment> segments = priced.segments();
		if (!segments.getFirst().departureAirport().equals(ref.origin())
				|| !segments.getLast().arrivalAirport().equals(ref.destination())) {
			throw invalid("Hành trình mới phải cùng sân bay đi và sân bay đến với chiều hiện tại");
		}
		if (!keepsOrder(ref, segments.getFirst().departureTime().toInstant(),
				segments.getLast().arrivalTime().toInstant())) {
			throw invalid("Chiều về phải cất cánh sau khi chiều đi hạ cánh");
		}
		return priced;
	}

	/** BR-04 với chiều còn lại (nếu còn ACTIVE): chiều về cất cánh sau khi chiều đi hạ cánh. */
	private static boolean keepsOrder(ChangeRef ref, Instant departure, Instant arrival) {
		return (ref.departAfter() == null || departure.isAfter(ref.departAfter()))
				&& (ref.arriveBefore() == null || ref.arriveBefore().isAfter(arrival));
	}

	private static List<Long> flightIds(Itinerary itinerary) {
		return itinerary.segments().stream().map(FlightSearch.Segment::flightId).toList();
	}

	private static BusinessException invalid(String detail) {
		return new BusinessException(ErrorCode.ITINERARY_INVALID, detail);
	}

	private static BusinessException notFound() {
		return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy booking");
	}

}
