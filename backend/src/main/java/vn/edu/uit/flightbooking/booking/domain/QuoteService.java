package vn.edu.uit.flightbooking.booking.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.booking.domain.BookingRequest.JourneyRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest.PassengerRequest;
import vn.edu.uit.flightbooking.booking.domain.Quote.JourneyQuote;
import vn.edu.uit.flightbooking.booking.domain.Quote.PassengerFare;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;
import vn.edu.uit.flightbooking.flight.FlightApi;
import vn.edu.uit.flightbooking.flight.FlightApi.JourneyChoice;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedJourney;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedSegment;

/**
 * Báo giá (FR-20, FR-21, TDD §6.2 bước 1–4). Giữ chỗ (Plan 11) gọi lại {@link #quote} rồi mới giữ ghế và lượt
 * voucher, nên lỗi nào xảy ra khi giữ chỗ cũng hiện ra khi báo giá.
 */
@Service
public class QuoteService {

	private final FlightApi flights;

	private final SettingsApi settings;

	QuoteService(FlightApi flights, SettingsApi settings) {
		this.flights = flights;
		this.settings = settings;
	}

	/** Chỉ đọc: không giữ ghế, không ghi DB (FR-20). {@code userId}: người đặt. */
	@Transactional(readOnly = true)
	public Quote quote(long userId, BookingRequest request) {
		List<JourneyRequest> requested = request.journeys();
		checkDirections(requested);
		List<PassengerRequest> passengers = request.passengers();
		PassengerRules.checkCounts(passengers, settings.getInt(SettingKey.BOOKING_MAX_SEATED_PASSENGERS));
		int seated = (int) passengers.stream().filter(p -> p.type() != PassengerType.INFANT).count();
		List<PricedJourney> itineraries = flights
			.price(requested.stream().map(j -> new JourneyChoice(j.flightIds(), j.fareFamilyId())).toList(), seated);
		List<PricedSegment> segments = itineraries.stream().flatMap(j -> j.segments().stream()).toList();
		PassengerRules.checkDetails(passengers, segments.getFirst().departureTime().toLocalDate(),
				segments.getLast().departureTime().toLocalDate(),
				segments.stream().anyMatch(PricedSegment::international));

		int childPercent = settings.getInt(SettingKey.PRICING_CHILD_PERCENT);
		int infantPercent = settings.getInt(SettingKey.PRICING_INFANT_PERCENT);
		Map<PassengerType, Long> counts = passengers.stream()
			.collect(Collectors.groupingBy(PassengerRequest::type, () -> new EnumMap<>(PassengerType.class),
					Collectors.counting()));
		List<JourneyQuote> journeys = IntStream.range(0, itineraries.size()).mapToObj(i -> {
			List<PassengerFare> fares = passengerFares(itineraries.get(i), counts, childPercent, infantPercent);
			long fareTotal = fares.stream().mapToLong(PassengerFare::total).sum();
			return new JourneyQuote(requested.get(i).direction(), itineraries.get(i), fares, List.of(), fareTotal, 0,
					0);
		}).toList();
		long fareTotal = journeys.stream().mapToLong(JourneyQuote::fareTotal).sum();
		return new Quote(journeys, null, fareTotal, 0, 0, fareTotal);
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

}
