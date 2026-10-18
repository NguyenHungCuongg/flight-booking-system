package vn.edu.uit.flightbooking.aftersales.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;
import static vn.edu.uit.flightbooking.flight.TestFlights.createOne;
import static vn.edu.uit.flightbooking.flight.TestFlights.newFlight;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.aftersales.TestRefunds;
import vn.edu.uit.flightbooking.aftersales.TestReschedules;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;

/**
 * FR-60, FR-61, BR-70–72. Mỗi test có một booking khứ hồi đã xuất vé (chiều đi 3.020.000, chiều về Q1300 ngày DAY + 3
 * giá 2.160.000, phí đổi 200.000) và hai chuyến về thay thế ngày DAY + 4.
 */
@IntegrationTest
@Transactional
class RescheduleOptionsTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	Vnpay vnpay;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	String code;

	long q1310;

	long q1320;

	long q2310;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		TestReschedules.changeFee(jdbc, trip);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		code = TestRefunds.issued(mvc, jdbc, vnpay, customer,
				TestBookings.body(trip, Map.of("journeys", trip.roundTrip())));
		q1310 = TestBookings.returnFlight(mvc, jdbc, trip, "Q1310", DAY.plusDays(4), "06:00", 2_500_000);
		q1320 = TestBookings.returnFlight(mvc, jdbc, trip, "Q1320", DAY.plusDays(4), "09:00", 1_900_000);
		q2310 = createOne(mvc, TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session(),
				newFlight(trip.catalog(), Map.of("airlineCode", "Q2", "flightNumber", "Q2310", "departureAirport", "QHE",
						"arrivalAirport", "QHA", "departureTime", "07:00", "fromDate", DAY.plusDays(4).toString(),
						"toDate", DAY.plusDays(4).toString(), "fares",
						List.of(Map.of("fareFamilyId", trip.catalog().otherAirlineFare(), "price", 1_000_000)))));
	}

	/** FR-60, BR-71, BR-72: cùng hãng, cùng gói, bỏ hành trình hiện tại; xếp theo số tiền phải trả. */
	@Test
	void optionsKeepSameAirlineAndFareAndShowAmountDue() throws Exception {
		TestReschedules.options(mvc, customer.session(), code, "return", DAY.plusDays(4))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.options.length()").value(2))
			.andExpect(jsonPath("$.options[0].itinerary.segments[0].flightNumber").value("Q1320"))
			.andExpect(jsonPath("$.options[0].itinerary.fareOptions.length()").value(1))
			.andExpect(jsonPath("$.options[0].itinerary.fareOptions[0].fareFamilyId").value(trip.catalog().economyFare()))
			.andExpect(jsonPath("$.options[0].quote.amountDue").value(200_000))
			.andExpect(jsonPath("$.options[1].itinerary.segments[0].flightNumber").value("Q1310"))
			.andExpect(jsonPath("$.options[1].quote.oldFareTotal").value(2_160_000))
			.andExpect(jsonPath("$.options[1].quote.newFareTotal").value(2_500_000))
			.andExpect(jsonPath("$.options[1].quote.changeFeeTotal").value(200_000))
			.andExpect(jsonPath("$.options[1].quote.amountDue").value(540_000));
		TestReschedules.options(mvc, customer.session(), code, "RETURN", DAY.plusDays(3))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.options").isEmpty());
	}

	/** FR-61: báo giá khớp lựa chọn và không ghi gì. */
	@Test
	void quoteMatchesOptionAndWritesNothing() throws Exception {
		TestReschedules.quote(mvc, customer.session(), code, "RETURN", q1310)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amountDue").value(540_000));
		assertThat(jdbc.sql("SELECT count(*) FROM reschedules").query(Long.class).single()).isZero();
		assertThat(TestBookings.available(jdbc, q1310)).isEqualTo(100);
	}

	/** Review Focus 1: đổi chiều về khi chiều đi đã bay; chỉ chiều mới phải thoả BR-01, BR-06. */
	@Test
	void returnCanBeChangedAfterOutboundHasFlown() throws Exception {
		jdbc.sql("""
				UPDATE flights SET departure_time = now() - interval '2 days',
				                   arrival_time = now() - interval '2 days' + interval '2 hours'
				WHERE id IN (?, ?)
				""").params(trip.firstLeg(), trip.secondLeg()).update();

		TestReschedules.quote(mvc, customer.session(), code, "RETURN", q1310)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amountDue").value(540_000));
	}

	/** BR-71: trùng, khác tuyến, khác hãng, danh sách rỗng. */
	@Test
	void quoteRejectsItinerariesOutsideBr71() throws Exception {
		TestReschedules.quote(mvc, customer.session(), code, "RETURN", trip.back())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ITINERARY_INVALID"))
			.andExpect(jsonPath("$.detail").value("Hành trình mới trùng hành trình hiện tại"));
		TestReschedules.quote(mvc, customer.session(), code, "RETURN", trip.firstLeg())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ITINERARY_INVALID"))
			.andExpect(jsonPath("$.detail")
				.value("Hành trình mới phải cùng sân bay đi và sân bay đến với chiều hiện tại"));
		TestReschedules.quote(mvc, customer.session(), code, "RETURN", q2310)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ITINERARY_INVALID"))
			.andExpect(jsonPath("$.detail").value("Gói giá không thuộc hãng khai thác chiều này"));
		mvc.perform(post("/api/bookings/{code}/journeys/{direction}/reschedule-quote", code, "RETURN")
			.with(csrf(mvc))
			.session(customer.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"flightIds\": []}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("flightIds"));
	}

	/** Review Focus 2, BR-04 theo cả hai hướng. */
	@Test
	void newItineraryMustKeepOrderWithOtherJourney() throws Exception {
		String wording = "Chiều về phải cất cánh sau khi chiều đi hạ cánh";
		long q1330 = TestBookings.returnFlight(mvc, jdbc, trip, "Q1330", DAY, "10:00", 2_000_000);
		TestReschedules.options(mvc, customer.session(), code, "RETURN", DAY)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.options").isEmpty());
		TestReschedules.quote(mvc, customer.session(), code, "RETURN", q1330)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ITINERARY_INVALID"))
			.andExpect(jsonPath("$.detail").value(wording));

		long q1110 = createOne(mvc, TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session(),
				newFlight(trip.catalog(), Map.of("flightNumber", "Q1110", "arrivalAirport", "QHE", "departureTime",
						"07:00", "fromDate", DAY.plusDays(5).toString(), "toDate", DAY.plusDays(5).toString(), "fares",
						List.of(Map.of("fareFamilyId", trip.catalog().economyFare(), "price", 1_000_000)))));
		TestReschedules.options(mvc, customer.session(), code, "OUTBOUND", DAY.plusDays(5))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.options").isEmpty());
		TestReschedules.quote(mvc, customer.session(), code, "OUTBOUND", q1110)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ITINERARY_INVALID"))
			.andExpect(jsonPath("$.detail").value(wording));
	}

	/** BR-51, BR-70: câu báo của đổi chuyến; quyền chủ booking. */
	@Test
	void journeyRulesUseChangeWording() throws Exception {
		String unpaid = TestBookings.holdCode(mvc, customer.session(),
				TestBookings.body(trip, Map.of("journeys", trip.roundTrip())));
		TestReschedules.options(mvc, customer.session(), unpaid, "RETURN", DAY.plusDays(4))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.detail").value("Chỉ đổi được booking đã xuất vé"));

		String changeable = "UPDATE booking_journeys SET changeable = ? WHERE direction = 'RETURN' "
				+ "AND booking_id = (SELECT id FROM bookings WHERE code = ?)";
		jdbc.sql(changeable).params(false, code).update();
		TestReschedules.quote(mvc, customer.session(), code, "RETURN", q1310)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FARE_RULE_NOT_ALLOWED"))
			.andExpect(jsonPath("$.detail").value("Gói giá của chiều này không cho phép đổi chuyến"));
		jdbc.sql(changeable).params(true, code).update();

		jdbc.sql("""
				UPDATE flights SET departure_time = now() + interval '23 hours', arrival_time = now() + interval '25 hours'
				WHERE id = ?
				""").param(trip.back()).update();
		TestReschedules.options(mvc, customer.session(), code, "RETURN", DAY.plusDays(4))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DEADLINE_PASSED"))
			.andExpect(jsonPath("$.detail").value(containsString("đổi chuyến")))
			.andExpect(jsonPath("$.detail").value(containsString("24 giờ")));

		TestUsers.TestUser other = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		TestReschedules.options(mvc, other.session(), code, "RETURN", DAY.plusDays(4))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		TestReschedules.options(mvc, customer.session(), code, "SIDEWAYS", DAY.plusDays(4))
			.andExpect(status().isNotFound());
		TestUsers.TestUser staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF);
		TestReschedules.options(mvc, staff.session(), code, "RETURN", DAY.plusDays(4))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/bookings/{code}/journeys/RETURN/reschedule-options", code).session(customer.session()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

}
