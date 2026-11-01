package vn.edu.uit.flightbooking.booking.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.booking.domain.BookingRequest.BaggageRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest.JourneyRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest.PassengerRequest;
import vn.edu.uit.flightbooking.booking.domain.Quote.BaggageLine;
import vn.edu.uit.flightbooking.booking.domain.Quote.JourneyQuote;
import vn.edu.uit.flightbooking.booking.domain.Quote.PassengerFare;
import vn.edu.uit.flightbooking.catalog.CatalogApi;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;
import vn.edu.uit.flightbooking.flight.FlightApi;
import vn.edu.uit.flightbooking.flight.FlightApi.JourneyChoice;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedJourney;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedSegment;
import vn.edu.uit.flightbooking.promotion.VoucherApi;
import vn.edu.uit.flightbooking.promotion.VoucherRejection;

/**
 * Báo giá (FR-20–FR-23, TDD §6.2 bước 1–4). Giữ chỗ (Plan 11) gọi lại {@link #quote} rồi mới giữ ghế và lượt
 * voucher, nên lỗi nào xảy ra khi giữ chỗ cũng hiện ra khi báo giá.
 */
@Service
public class QuoteService {

	/**
	 * BR-30 điều kiện 5, cùng điều kiện với {@code uq_bookings_voucher_user}. Hai request đồng thời vẫn có thể cùng qua
	 * truy vấn này, nên giữ chỗ (Plan 11) phải bắt thêm vi phạm unique index khi ghi.
	 */
	private static final String VOUCHER_USED = """
			SELECT EXISTS (
			    SELECT 1 FROM bookings
			    WHERE voucher_id = :voucherId AND user_id = :userId
			      AND status IN ('PENDING_PAYMENT', 'ISSUED', 'REFUNDED'))
			""";

	private final FlightApi flights;

	private final CatalogApi catalog;

	private final VoucherApi vouchers;

	private final SettingsApi settings;

	private final JdbcClient jdbc;

	QuoteService(FlightApi flights, CatalogApi catalog, VoucherApi vouchers, SettingsApi settings, JdbcClient jdbc) {
		this.flights = flights;
		this.catalog = catalog;
		this.vouchers = vouchers;
		this.settings = settings;
		this.jdbc = jdbc;
	}

	/**
	 * Chỉ đọc: không giữ ghế, không giữ lượt voucher, không ghi DB (FR-20). {@code userId}: người đặt, để kiểm tra BR-30
	 * điều kiện 5. Lỗi hành khách (BR-10, BR-13) và lỗi hành lý (BR-22) trả về trong cùng một lần.
	 */
	@Transactional(readOnly = true)
	public Quote quote(long userId, BookingRequest request) {
		List<JourneyRequest> requested = request.journeys();
		checkDirections(requested);
		List<Direction> directions = requested.stream().map(JourneyRequest::direction).toList();
		List<PassengerRequest> passengers = request.passengers();
		PassengerRules.checkCounts(passengers, settings.getInt(SettingKey.BOOKING_MAX_SEATED_PASSENGERS));
		int seated = (int) passengers.stream().filter(p -> p.type() != PassengerType.INFANT).count();
		List<PricedJourney> itineraries = flights
			.price(requested.stream().map(j -> new JourneyChoice(j.flightIds(), j.fareFamilyId())).toList(), seated);
		List<PricedSegment> segments = itineraries.stream().flatMap(j -> j.segments().stream()).toList();

		FieldErrors errors = new FieldErrors();
		PassengerRules.checkDetails(passengers, segments.getFirst().departureTime().toLocalDate(),
				segments.getLast().departureTime().toLocalDate(),
				segments.stream().anyMatch(PricedSegment::international), errors);
		boolean passengerRulesBroken = !errors.isEmpty();
		List<List<BaggageLine>> baggage = baggage(request.baggage(), passengers, directions, itineraries, errors);
		if (passengerRulesBroken) {
			errors.throwIfAny(ErrorCode.PASSENGER_RULES_VIOLATED, PassengerRules.DETAIL);
		}
		errors.throwIfAny(ErrorCode.VALIDATION_FAILED, "Dữ liệu không hợp lệ");

		int childPercent = settings.getInt(SettingKey.PRICING_CHILD_PERCENT);
		int infantPercent = settings.getInt(SettingKey.PRICING_INFANT_PERCENT);
		Map<PassengerType, Long> counts = passengers.stream()
			.collect(Collectors.groupingBy(PassengerRequest::type, () -> new EnumMap<>(PassengerType.class),
					Collectors.counting()));
		List<List<PassengerFare>> fares = itineraries.stream()
			.map(itinerary -> passengerFares(itinerary, counts, childPercent, infantPercent))
			.toList();
		List<Long> fareTotals = fares.stream().map(f -> f.stream().mapToLong(PassengerFare::total).sum()).toList();
		List<Long> baggageTotals = baggage.stream().map(b -> b.stream().mapToLong(BaggageLine::price).sum()).toList();
		List<Long> subtotals = IntStream.range(0, itineraries.size())
			.mapToObj(i -> fareTotals.get(i) + baggageTotals.get(i))
			.toList();
		long fareTotal = sum(fareTotals);
		long baggageTotal = sum(baggageTotals);

		Optional<VoucherApi.Applied> voucher = voucher(userId, request.voucherCode(), fareTotal + baggageTotal);
		long discount = voucher.map(VoucherApi.Applied::discount).orElse(0L);
		List<Long> shares = Pricing.allocate(discount, subtotals);
		List<JourneyQuote> journeys = IntStream.range(0, itineraries.size())
			.mapToObj(i -> new JourneyQuote(directions.get(i), itineraries.get(i), fares.get(i), baggage.get(i),
					fareTotals.get(i), baggageTotals.get(i), shares.get(i)))
			.toList();
		return new Quote(journeys, voucher.map(VoucherApi.Applied::voucherId).orElse(null), fareTotal, baggageTotal,
				discount, fareTotal + baggageTotal - discount, childPercent, infantPercent);
	}

	/** Chiều đi, rồi chiều về nếu là khứ hồi (FR-12), đúng thứ tự đó. */
	private static void checkDirections(List<JourneyRequest> journeys) {
		List<Direction> directions = journeys.stream().map(JourneyRequest::direction).toList();
		if (!directions.equals(List.of(Direction.OUTBOUND))
				&& !directions.equals(List.of(Direction.OUTBOUND, Direction.RETURN))) {
			throw new BusinessException(ErrorCode.ITINERARY_INVALID,
					"Hành trình gồm chiều đi, và chiều về nếu là khứ hồi");
		}
	}

	/** BR-21 trên từng chặng rồi cộng lại; loại khách theo thứ tự ADULT, CHILD, INFANT. */
	private static List<PassengerFare> passengerFares(PricedJourney itinerary, Map<PassengerType, Long> counts,
			int childPercent, int infantPercent) {
		return counts.entrySet().stream().map(e -> {
			long unitPrice = itinerary.segments()
				.stream()
				.mapToLong(s -> Pricing.ticketPrice(s.adultPrice(), e.getKey(), childPercent, infantPercent))
				.sum();
			return new PassengerFare(e.getKey(), e.getValue().intValue(), unitPrice, unitPrice * e.getValue());
		}).toList();
	}

	/**
	 * BR-22: mỗi khách (trừ em bé) chọn tối đa một mức mỗi chiều, trong bảng giá đang bán của hãng khai thác chiều đó.
	 * Mỗi dòng kiểm tra độc lập ba nhóm (khách, chiều, mức hành lý) và thêm mọi lỗi vào {@code errors}. Trả các mức
	 * hợp lệ của từng chiều, cùng thứ tự với {@code directions}.
	 */
	private List<List<BaggageLine>> baggage(List<BaggageRequest> requests, List<PassengerRequest> passengers,
			List<Direction> directions, List<PricedJourney> itineraries, FieldErrors errors) {
		Map<Long, CatalogApi.BaggageRef> options = catalog
			.baggageOptions(requests.stream().map(BaggageRequest::baggageOptionId).toList());
		List<List<BaggageLine>> lines = new ArrayList<>();
		directions.forEach(d -> lines.add(new ArrayList<>()));
		Set<String> chosen = new HashSet<>();
		for (int i = 0; i < requests.size(); i++) {
			BaggageRequest b = requests.get(i);
			String field = "baggage[%d].".formatted(i);
			int journey = directions.indexOf(b.direction());
			CatalogApi.BaggageRef option = options.get(b.baggageOptionId());
			boolean valid = true;
			if (b.passengerIndex() >= passengers.size()) {
				errors.add(field + "passengerIndex", "Không có hành khách này");
				valid = false;
			}
			else if (passengers.get(b.passengerIndex()).type() == PassengerType.INFANT) {
				errors.add(field + "passengerIndex", "Em bé không được mua thêm hành lý");
				valid = false;
			}
			if (journey < 0) {
				errors.add(field + "direction", "Hành trình không có chiều này");
				valid = false;
			}
			else if (!chosen.add(b.passengerIndex() + ":" + b.direction())) {
				errors.add(field + "direction", "Mỗi hành khách chỉ chọn một mức hành lý cho mỗi chiều");
				valid = false;
			}
			if (option == null || !option.active()
					|| (journey >= 0 && !option.airlineCode().equals(itineraries.get(journey).airlineCode()))) {
				errors.add(field + "baggageOptionId", "Mức hành lý không có trong bảng giá đang bán của hãng");
				valid = false;
			}
			if (valid) {
				lines.get(journey)
					.add(new BaggageLine(b.passengerIndex(), b.baggageOptionId(), option.weightKg(), option.price()));
			}
		}
		return lines;
	}

	/** BR-30, BR-31. Mã trống là không dùng voucher. Điều kiện 5 kiểm tra sau điều kiện 1–4 (thứ tự ở TDD §9). */
	private Optional<VoucherApi.Applied> voucher(long userId, String code, long subtotal) {
		if (code == null || code.isBlank()) {
			return Optional.empty();
		}
		VoucherApi.Applied applied = vouchers.check(code, subtotal);
		boolean used = jdbc.sql(VOUCHER_USED)
			.param("voucherId", applied.voucherId())
			.param("userId", userId)
			.query(Boolean.class)
			.single();
		if (used) {
			throw VoucherRejection.ALREADY_USED.toException();
		}
		return Optional.of(applied);
	}

	private static long sum(List<Long> amounts) {
		return amounts.stream().mapToLong(Long::longValue).sum();
	}

}
