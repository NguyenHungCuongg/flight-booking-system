package vn.edu.uit.flightbooking.aftersales.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

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

/** FR-60, FR-61: khách xem hành trình thay thế và báo giá đổi một chiều (BR-70–72). Chỉ đọc. */
@Service
public class CustomerReschedules {

	private final BookingApi bookings;

	private final FlightSearch search;

	private final FlightApi flights;

	private final SettingsApi settings;

	private final JourneyRules rules;

	CustomerReschedules(BookingApi bookings, FlightSearch search, FlightApi flights, SettingsApi settings,
			JourneyRules rules) {
		this.bookings = bookings;
		this.search = search;
		this.flights = flights;
		this.settings = settings;
		this.rules = rules;
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
		return RescheduleQuote.of(ref.fareTotal(), newFareTotal(ref, priced), ref.journey().changeFee(),
				ref.journey().seated());
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

	private long newFareTotal(ChangeRef ref, PricedJourney priced) {
		return FlightSearch.totalPrice(priced.segments().stream().map(PricedSegment::adultPrice).toList(),
				ref.adults(), ref.children(), ref.infants(), settings.getInt(SettingKey.PRICING_CHILD_PERCENT),
				settings.getInt(SettingKey.PRICING_INFANT_PERCENT));
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
