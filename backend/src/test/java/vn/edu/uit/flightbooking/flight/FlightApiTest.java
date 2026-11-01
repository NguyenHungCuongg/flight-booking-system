package vn.edu.uit.flightbooking.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;
import static vn.edu.uit.flightbooking.flight.TestFlights.createOne;
import static vn.edu.uit.flightbooking.flight.TestFlights.newFlight;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.flight.FlightApi.JourneyChoice;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedJourney;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedSegment;

/** BR-01–BR-06 và BR-04 qua {@link FlightApi#price}. Chuyến tạo qua API của Admin như FlightSearchTest. */
@IntegrationTest
@Transactional
class FlightApiTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	FlightApi flights;

	MockHttpSession admin;

	TestFlights.Catalog catalog;

	long firstLeg;

	long secondLeg;

	long back;

	/**
	 * Chiều đi: Q1100 QHA→QHB 06:00–08:10 (Eco 1.000.000, Biz 3.000.000) nối Q1200 QHB→QHC 10:00–12:00 giờ Việt Nam,
	 * tức 13:00 giờ Singapore (chỉ bán Eco, 1.500.000). Chiều về: Q1300 QHC→QHA ngày DAY + 2 lúc 06:00 giờ Singapore
	 * (chỉ bán Eco, 2.000.000). Mỗi chuyến có ECONOMY 100 ghế và BUSINESS 10 ghế.
	 */
	@BeforeEach
	void setUp() throws Exception {
		admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();
		catalog = TestFlights.catalog(jdbc);
		firstLeg = flight(Map.of());
		secondLeg = flight(Map.of("flightNumber", "Q1200", "departureAirport", "QHB", "arrivalAirport", "QHC",
				"departureTime", "10:00", "durationMinutes", 120, "fares", eco(1_500_000)));
		String returnDay = DAY.plusDays(2).toString();
		back = flight(Map.of("flightNumber", "Q1300", "departureAirport", "QHC", "arrivalAirport", "QHA", "fromDate",
				returnDay, "toDate", returnDay, "fares", eco(2_000_000)));
	}

	long flight(Map<String, Object> changes) throws Exception {
		return createOne(mvc, admin, newFlight(catalog, changes));
	}

	List<Map<String, Object>> eco(long price) {
		return List.of(Map.of("fareFamilyId", catalog.economyFare(), "price", price));
	}

	static JourneyChoice choice(long fareFamilyId, Long... flightIds) {
		return new JourneyChoice(List.of(flightIds), fareFamilyId);
	}

	List<PricedJourney> price(int seats, JourneyChoice... journeys) {
		return flights.price(List.of(journeys), seats);
	}

	/** Lỗi nghiệp vụ mà lời gọi ném ra; test hỏng nếu không có lỗi hoặc sai loại lỗi. */
	static BusinessException error(ThrowingCallable call) {
		Throwable thrown = catchThrowable(call);
		assertThat(thrown).isInstanceOf(BusinessException.class);
		return (BusinessException) thrown;
	}

	@Test
	void pricesRoundTripWithLocalTimesAndFareSnapshot() {
		List<PricedJourney> priced = price(2, choice(catalog.economyFare(), firstLeg, secondLeg),
				choice(catalog.economyFare(), back));

		assertThat(priced).hasSize(2);
		PricedJourney outbound = priced.getFirst();
		assertThat(outbound.airlineCode()).isEqualTo("Q1");
		assertThat(outbound.fare().fareFamilyId()).isEqualTo(catalog.economyFare());
		assertThat(outbound.fare().name()).isEqualTo("Eco");
		assertThat(outbound.fare().cabinClass()).isEqualTo(CabinClass.ECONOMY);
		assertThat(outbound.fare().carryOnKg()).isEqualTo(7);
		assertThat(outbound.fare().changeable()).isTrue();
		assertThat(outbound.segments()).extracting(PricedSegment::flightNumber).containsExactly("Q1100", "Q1200");
		assertThat(outbound.segments()).extracting(PricedSegment::adultPrice).containsExactly(1_000_000L, 1_500_000L);
		assertThat(outbound.segments()).extracting(PricedSegment::international).containsExactly(false, true);
		assertThat(outbound.segments().getFirst().departureTime())
			.isEqualTo(OffsetDateTime.parse(DAY + "T06:00:00+07:00"));
		assertThat(outbound.segments().getLast().arrivalTime()).isEqualTo(OffsetDateTime.parse(DAY + "T13:00:00+08:00"));
		assertThat(priced.getLast().segments()).extracting(PricedSegment::adultPrice).containsExactly(2_000_000L);
	}

	/** BR-02, BR-03: 1 hoặc 2 chặng khác nhau; nối chuyến cùng hãng, đúng sân bay, thời gian nối theo tham số. */
	@Test
	void rejectsMalformedJourneys() throws Exception {
		long otherAirline = flight(Map.of("airlineCode", "Q2", "flightNumber", "Q2200", "departureAirport", "QHB",
				"arrivalAirport", "QHC", "departureTime", "10:00", "cabins",
				List.of(Map.of("cabinClass", "ECONOMY", "totalSeats", 50)), "fares",
				List.of(Map.of("fareFamilyId", catalog.otherAirlineFare(), "price", 1_000_000))));
		long eco = catalog.economyFare();

		assertThat(error(() -> price(1, choice(eco))).code()).isEqualTo(ErrorCode.ITINERARY_INVALID);
		assertThat(error(() -> price(1, choice(eco, firstLeg, secondLeg, back))).code())
			.isEqualTo(ErrorCode.ITINERARY_INVALID);
		assertThat(error(() -> price(1, choice(eco, firstLeg, firstLeg))).code()).isEqualTo(ErrorCode.ITINERARY_INVALID);
		assertThat(error(() -> price(1, choice(eco, secondLeg, firstLeg))).code())
			.isEqualTo(ErrorCode.ITINERARY_INVALID);
		assertThat(error(() -> price(1, choice(eco, firstLeg, otherAirline))).code())
			.isEqualTo(ErrorCode.ITINERARY_INVALID);

		// Nối 110 phút: hợp lệ với mặc định 60–720, sai khi tối thiểu là 120.
		jdbc.sql("UPDATE system_settings SET value = '120' WHERE key = 'search.min_connection_minutes'").update();
		assertThat(error(() -> price(1, choice(eco, firstLeg, secondLeg))).code())
			.isEqualTo(ErrorCode.ITINERARY_INVALID);
	}

	/** BR-05: gói của hãng khác là hành trình sai; gói ngừng bán hoặc không bán trên một chặng thì không bán được. */
	@Test
	void checksFareFamilyOnEverySegment() {
		assertThat(error(() -> price(1, choice(catalog.otherAirlineFare(), firstLeg))).code())
			.isEqualTo(ErrorCode.ITINERARY_INVALID);
		assertThat(error(() -> price(1, choice(-1L, firstLeg))).code()).isEqualTo(ErrorCode.ITINERARY_INVALID);
		// Q1200 không bán gói Biz.
		assertThat(error(() -> price(1, choice(catalog.businessFare(), firstLeg, secondLeg))).code())
			.isEqualTo(ErrorCode.FLIGHT_NOT_BOOKABLE);
		assertThat(price(1, choice(catalog.businessFare(), firstLeg)).getFirst().fare().cabinClass())
			.isEqualTo(CabinClass.BUSINESS);

		jdbc.sql("UPDATE fare_families SET active = FALSE WHERE id = ?").param(catalog.economyFare()).update();
		assertThat(error(() -> price(1, choice(catalog.economyFare(), firstLeg))).code())
			.isEqualTo(ErrorCode.FLIGHT_NOT_BOOKABLE);
	}

	/** BR-01: chuyến không còn, đã huỷ, hoặc chặng đầu cất cánh trong vòng N giờ tới (N đọc từ tham số). */
	@Test
	void rejectsFlightsNoLongerOnSale() {
		long eco = catalog.economyFare();
		assertThat(error(() -> price(1, choice(eco, Long.MAX_VALUE))).code()).isEqualTo(ErrorCode.FLIGHT_NOT_BOOKABLE);

		jdbc.sql("UPDATE flights SET status = 'CANCELLED' WHERE id = ?").param(secondLeg).update();
		assertThat(error(() -> price(1, choice(eco, firstLeg, secondLeg))).code())
			.isEqualTo(ErrorCode.FLIGHT_NOT_BOOKABLE);

		jdbc.sql("""
				UPDATE flights SET departure_time = now() + INTERVAL '2 hours', arrival_time = now() + INTERVAL '4 hours'
				WHERE id = ?
				""").param(firstLeg).update();
		BusinessException tooLate = error(() -> price(1, choice(eco, firstLeg)));
		assertThat(tooLate.code()).isEqualTo(ErrorCode.FLIGHT_NOT_BOOKABLE);
		assertThat(tooLate.getMessage()).contains("Q1100");

		jdbc.sql("UPDATE system_settings SET value = '1' WHERE key = 'booking.min_hours_before_departure'").update();
		assertThat(price(1, choice(eco, firstLeg))).hasSize(1);
	}

	/** BR-06: đủ ghế trên mọi chặng trong hạng ghế của gói; câu báo theo mẫu TDD §8.8. */
	@Test
	void needsEnoughSeatsOnEverySegment() {
		jdbc.sql("UPDATE flight_cabins SET available_seats = 3 WHERE flight_id = ? AND cabin_class = 'ECONOMY'")
			.param(secondLeg)
			.update();

		assertThat(price(3, choice(catalog.economyFare(), firstLeg, secondLeg))).hasSize(1);
		BusinessException e = error(() -> price(4, choice(catalog.economyFare(), firstLeg, secondLeg)));
		assertThat(e.code()).isEqualTo(ErrorCode.SEATS_UNAVAILABLE);
		assertThat(e.getMessage()).isEqualTo("Chuyến Q1200 hạng ECONOMY không còn đủ 4 ghế");
	}

	/** BR-04: chiều về bay ngược tuyến chiều đi và cất cánh sau khi chiều đi hạ cánh. */
	@Test
	void returnMustReverseRouteAndDepartAfterOutboundLands() throws Exception {
		long eco = catalog.economyFare();
		// Q1301 QHC→QHA cất cánh 12:30 giờ Singapore, trước khi Q1200 hạ cánh (13:00 giờ Singapore).
		long early = flight(Map.of("flightNumber", "Q1301", "departureAirport", "QHC", "arrivalAirport", "QHA",
				"departureTime", "12:30", "fares", eco(2_000_000)));

		assertThat(error(() -> price(1, choice(eco, firstLeg, secondLeg), choice(eco, early))).code())
			.isEqualTo(ErrorCode.ITINERARY_INVALID);
		assertThat(error(() -> price(1, choice(eco, firstLeg, secondLeg), choice(eco, firstLeg))).code())
			.isEqualTo(ErrorCode.ITINERARY_INVALID);
		assertThat(error(() -> price(1, choice(eco, firstLeg), choice(eco, back))).code())
			.isEqualTo(ErrorCode.ITINERARY_INVALID);
	}

}
