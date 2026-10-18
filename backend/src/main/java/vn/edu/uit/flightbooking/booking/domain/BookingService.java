package vn.edu.uit.flightbooking.booking.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.booking.BookingIssued;
import vn.edu.uit.flightbooking.booking.TicketEmailRequested;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest.ContactRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest.PassengerRequest;
import vn.edu.uit.flightbooking.booking.domain.Quote.BaggageLine;
import vn.edu.uit.flightbooking.booking.domain.Quote.JourneyQuote;
import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;
import vn.edu.uit.flightbooking.flight.FlightApi.FareSnapshot;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedSegment;
import vn.edu.uit.flightbooking.flight.SeatInventory;
import vn.edu.uit.flightbooking.promotion.VoucherApi;
import vn.edu.uit.flightbooking.promotion.VoucherRejection;

/**
 * Giữ chỗ (FR-24, TDD §6.2 bước 5) và xuất vé (TDD §6.4). Ghi bằng JdbcClient như module flight: mọi chuyển trạng
 * thái đều khoá dòng rồi {@code UPDATE} có điều kiện, nên không cần entity JPA.
 */
@Service
public class BookingService {

	/** Giờ của booking (hạn giữ chỗ, thời điểm tạo, xuất vé) trả theo giờ Việt Nam: booking không gắn với một sân bay. */
	static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

	/** TDD §6.2 bước 5.4: sinh lại mã khi trùng, tối đa 5 lần. */
	private static final int CODE_ATTEMPTS = 5;

	public record Created(String code, BookingStatus status, OffsetDateTime holdExpiresAt, Quote quote) {
	}

	private record Hold(long flightId, CabinClass cabin) {
	}

	/** Mã trùng thì không thêm dòng nào (sinh mã khác); vi phạm {@code uq_bookings_voucher_user} vẫn báo lỗi. */
	private static final String INSERT_BOOKING = """
			INSERT INTO bookings (code, user_id, status, contact_name, contact_email, contact_phone, voucher_id,
			                      fare_total, baggage_total, discount_total, total_amount, hold_expires_at)
			VALUES (:code, :userId, 'PENDING_PAYMENT', :name, :email, :phone, :voucherId,
			        :fareTotal, :baggageTotal, :discountTotal, :totalAmount, :holdExpiresAt)
			ON CONFLICT (code) DO NOTHING
			RETURNING id
			""";

	private static final String INSERT_JOURNEY = """
			INSERT INTO booking_journeys (booking_id, direction, airline_code, fare_family_id, fare_family_name,
			                              cabin_class, carry_on_kg, checked_baggage_kg, refundable, refund_fee,
			                              changeable, change_fee, discount_amount)
			VALUES (:bookingId, :direction, :airlineCode, :fareFamilyId, :name, :cabin, :carryOnKg, :checkedBaggageKg,
			        :refundable, :refundFee, :changeable, :changeFee, :discount)
			RETURNING id
			""";

	private static final String INSERT_SEGMENT = """
			INSERT INTO booking_segments (journey_id, flight_id, seq, status, adult_price)
			VALUES (:journeyId, :flightId, :seq, 'ACTIVE', :adultPrice)
			RETURNING id
			""";

	private static final String INSERT_PASSENGER = """
			INSERT INTO passengers (booking_id, type, last_name, first_name, gender, date_of_birth, nationality,
			                        passport_number, passport_expiry)
			VALUES (:bookingId, :type, :lastName, :firstName, :gender, :dateOfBirth, :nationality, :passportNumber,
			        :passportExpiry)
			RETURNING id
			""";

	static final String INSERT_TICKET = """
			INSERT INTO tickets (segment_id, passenger_id, fare_amount) VALUES (:segmentId, :passengerId, :fareAmount)
			""";

	private static final String INSERT_BAGGAGE = """
			INSERT INTO booking_baggage (journey_id, passenger_id, baggage_option_id, weight_kg, price)
			VALUES (:journeyId, :passengerId, :optionId, :weightKg, :price)
			""";

	/** Q-13 cho mọi vé chưa có số của các chặng ACTIVE; {@code nextval} chạy riêng cho từng dòng. */
	static final String ISSUE_TICKETS = """
			UPDATE tickets t
			SET ticket_number = a.ticket_prefix || lpad(nextval('ticket_number_seq')::text, 10, '0'),
			    issued_at = now()
			FROM booking_segments s
			JOIN booking_journeys j ON j.id = s.journey_id
			JOIN airlines a ON a.code = j.airline_code
			WHERE t.segment_id = s.id AND j.booking_id = :bookingId AND s.status = 'ACTIVE'
			  AND t.ticket_number IS NULL
			""";

	private final QuoteService quotes;

	private final SeatInventory seats;

	private final VoucherApi vouchers;

	private final SettingsApi settings;

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	BookingService(QuoteService quotes, SeatInventory seats, VoucherApi vouchers, SettingsApi settings,
			JdbcClient jdbc, ApplicationEventPublisher events) {
		this.quotes = quotes;
		this.seats = seats;
		this.vouchers = vouchers;
		this.settings = settings;
		this.jdbc = jdbc;
		this.events = events;
	}

	/**
	 * FR-24. Thứ tự khoá TDD §5.4: khoá chia sẻ các chuyến, báo giá lại trên dữ liệu đã khoá (mọi lỗi của FR-20), giữ
	 * ghế theo {@code flightId} tăng dần (Q-01), giữ lượt voucher (Q-14), rồi ghi booking. Lỗi ở bất kỳ bước nào làm
	 * rollback toàn bộ. Tổng thanh toán 0 thì xuất vé ngay (BR-40).
	 */
	@Transactional
	public Created create(long userId, BookingRequest request) {
		List<Long> flightIds = request.journeys()
			.stream()
			.flatMap(j -> j.flightIds().stream())
			.distinct()
			.sorted()
			.toList();
		if (!flightIds.isEmpty()) {
			seats.lockFlights(flightIds);
		}
		Quote quote = quotes.quote(userId, request);
		List<PassengerRequest> passengers = request.passengers();
		int seated = (int) passengers.stream().filter(p -> p.type() != PassengerType.INFANT).count();
		quote.journeys()
			.stream()
			.flatMap(j -> j.itinerary()
				.segments()
				.stream()
				.map(s -> new Hold(s.flightId(), j.itinerary().fare().cabinClass())))
			.sorted(Comparator.comparingLong(Hold::flightId))
			.forEach(h -> seats.hold(h.flightId(), h.cabin(), seated));
		if (quote.voucherId() != null) {
			vouchers.reserve(quote.voucherId());
		}

		Instant holdExpiresAt = Instant.now()
			.plus(settings.getInt(SettingKey.BOOKING_HOLD_MINUTES), ChronoUnit.MINUTES);
		String code = null;
		Long bookingId = null;
		for (int attempt = 0; attempt < CODE_ATTEMPTS && bookingId == null; attempt++) {
			code = BookingCodes.next();
			bookingId = insertBooking(code, userId, request.contact(), quote, holdExpiresAt).orElse(null);
		}
		if (bookingId == null) {
			throw new IllegalStateException("Không sinh được mã đặt chỗ sau %d lần thử".formatted(CODE_ATTEMPTS));
		}

		List<Long> passengerIds = new ArrayList<>();
		for (PassengerRequest p : passengers) {
			passengerIds.add(insertPassenger(bookingId, p));
		}
		for (JourneyQuote journey : quote.journeys()) {
			long journeyId = insertJourney(bookingId, journey);
			List<PricedSegment> segments = journey.itinerary().segments();
			for (int seq = 1; seq <= segments.size(); seq++) {
				PricedSegment segment = segments.get(seq - 1);
				long segmentId = insertSegment(journeyId, segment, seq);
				for (int i = 0; i < passengers.size(); i++) {
					long fare = Pricing.ticketPrice(segment.adultPrice(), passengers.get(i).type(),
							quote.childPercent(), quote.infantPercent());
					jdbc.sql(INSERT_TICKET)
						.param("segmentId", segmentId)
						.param("passengerId", passengerIds.get(i))
						.param("fareAmount", fare)
						.update();
				}
			}
			for (BaggageLine line : journey.baggage()) {
				jdbc.sql(INSERT_BAGGAGE)
					.param("journeyId", journeyId)
					.param("passengerId", passengerIds.get(line.passengerIndex()))
					.param("optionId", line.baggageOptionId())
					.param("weightKg", line.weightKg())
					.param("price", line.price())
					.update();
			}
		}

		BookingStatus status = BookingStatus.PENDING_PAYMENT;
		if (quote.totalAmount() == 0) {
			issue(bookingId);
			status = BookingStatus.ISSUED;
		}
		return new Created(code, status, holdExpiresAt.atZone(VIETNAM).toOffsetDateTime(), quote);
	}

	/**
	 * TDD §6.4, BR-43: cấp số vé cho mọi vé của chặng ACTIVE (Q-13), chuyển booking sang ISSUED và phát
	 * {@link BookingIssued}. Bên gọi đã khoá booking và kiểm tra trạng thái: giữ chỗ với tổng tiền 0 (BR-40), hoặc
	 * thanh toán thành công (Plan 12).
	 */
	@Transactional
	public void issue(long bookingId) {
		jdbc.sql(ISSUE_TICKETS).param("bookingId", bookingId).update();
		jdbc.sql("UPDATE bookings SET status = 'ISSUED', issued_at = now(), updated_at = now() WHERE id = ?")
			.param(bookingId)
			.update();
		events.publishEvent(new BookingIssued(bookingId));
	}

	/** FR-73: Staff gửi lại email vé, chỉ cho booking đã xuất vé. Email gửi sau khi commit, như lúc xuất vé. */
	@Transactional
	public void resendTicketEmail(String code) {
		record Found(long id, BookingStatus status) {
		}
		Found booking = jdbc.sql("SELECT id, status FROM bookings WHERE code = ?")
			.param(code.toUpperCase(Locale.ROOT))
			.query((rs, rowNum) -> new Found(rs.getLong("id"), BookingStatus.valueOf(rs.getString("status"))))
			.optional()
			.orElseThrow(BookingCancellation::notFound);
		if (booking.status() != BookingStatus.ISSUED) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "Chỉ gửi lại email vé cho booking đã xuất vé");
		}
		events.publishEvent(new TicketEmailRequested(booking.id()));
	}

	/**
	 * Rỗng khi mã đã có người dùng. Vi phạm unique còn lại chỉ có thể là {@code uq_bookings_voucher_user} (BR-30 điều
	 * kiện 5): request khác của cùng tài khoản vừa giữ chỗ bằng voucher này (T16).
	 */
	private Optional<Long> insertBooking(String code, long userId, ContactRequest contact, Quote quote,
			Instant holdExpiresAt) {
		try {
			return jdbc.sql(INSERT_BOOKING)
				.param("code", code)
				.param("userId", userId)
				.param("name", contact.name())
				.param("email", contact.email())
				.param("phone", contact.phone())
				.param("voucherId", quote.voucherId())
				.param("fareTotal", quote.fareTotal())
				.param("baggageTotal", quote.baggageTotal())
				.param("discountTotal", quote.discountTotal())
				.param("totalAmount", quote.totalAmount())
				.param("holdExpiresAt", holdExpiresAt.atOffset(ZoneOffset.UTC))
				.query(Long.class)
				.optional();
		}
		catch (DuplicateKeyException e) {
			throw VoucherRejection.ALREADY_USED.toException();
		}
	}

	private long insertPassenger(long bookingId, PassengerRequest p) {
		return jdbc.sql(INSERT_PASSENGER)
			.param("bookingId", bookingId)
			.param("type", p.type().name())
			.param("lastName", p.lastName())
			.param("firstName", p.firstName())
			.param("gender", p.gender().name())
			.param("dateOfBirth", p.dateOfBirth())
			.param("nationality", p.nationality())
			.param("passportNumber", p.passportNumber())
			.param("passportExpiry", p.passportExpiry())
			.query(Long.class)
			.single();
	}

	/** Chốt quy định gói giá và mức giảm của chiều (BR-24, BR-33). */
	private long insertJourney(long bookingId, JourneyQuote journey) {
		FareSnapshot fare = journey.itinerary().fare();
		return jdbc.sql(INSERT_JOURNEY)
			.param("bookingId", bookingId)
			.param("direction", journey.direction().name())
			.param("airlineCode", journey.itinerary().airlineCode())
			.param("fareFamilyId", fare.fareFamilyId())
			.param("name", fare.name())
			.param("cabin", fare.cabinClass().name())
			.param("carryOnKg", fare.carryOnKg())
			.param("checkedBaggageKg", fare.checkedBaggageKg())
			.param("refundable", fare.refundable())
			.param("refundFee", fare.refundFee())
			.param("changeable", fare.changeable())
			.param("changeFee", fare.changeFee())
			.param("discount", journey.discount())
			.query(Long.class)
			.single();
	}

	/** Chốt giá người lớn của chặng (BR-24). */
	private long insertSegment(long journeyId, PricedSegment segment, int seq) {
		return jdbc.sql(INSERT_SEGMENT)
			.param("journeyId", journeyId)
			.param("flightId", segment.flightId())
			.param("seq", seq)
			.param("adultPrice", segment.adultPrice())
			.query(Long.class)
			.single();
	}

}
