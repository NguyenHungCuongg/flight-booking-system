package vn.edu.uit.flightbooking.flight.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.booking.TestBookings.ADULT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.CHILD_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.INFANT_BORN;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.aftersales.TestRefunds;
import vn.edu.uit.flightbooking.booking.BookingApi;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;

@IntegrationTest
@Transactional
class ManifestTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	Vnpay vnpay;

	@Autowired
	BookingApi api;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	TestUsers.TestUser staff;

	String code;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF);

		Map<String, Object> p1 = TestBookings.passenger("ADULT", ADULT_BORN);
		p1.put("firstName", "VAN AN");
		Map<String, Object> p2 = TestBookings.passenger("CHILD", CHILD_BORN);
		p2.put("firstName", "THI BE");
		Map<String, Object> p3 = TestBookings.passenger("INFANT", INFANT_BORN);
		p3.put("firstName", "MINH");

		code = TestRefunds.issued(mvc, jdbc, vnpay, customer,
				TestBookings.body(trip, Map.of("journeys", trip.roundTrip(), "passengers", List.of(p1, p2, p3))));

		TestBookings.holdCode(mvc, customer.session(), TestBookings.body(trip, Map.of("journeys", trip.roundTrip())));
	}

	/** FR-72, Q-17: vé hiệu lực của booking đã xuất vé trên chuyến, xếp theo họ tên; booking chờ thanh toán bị bỏ. */
	@Test
	void manifestListsTicketsOfIssuedBookings() throws Exception {
		mvc.perform(get("/api/staff/flights/{id}/passengers", trip.back()).session(staff.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.flight.flightNumber").value("Q1300"))
			.andExpect(jsonPath("$.passengers.length()").value(3))
			.andExpect(jsonPath("$.passengers[*].firstName").value(contains("MINH", "THI BE", "VAN AN")))
			.andExpect(jsonPath("$.passengers[*].type").value(contains("INFANT", "CHILD", "ADULT")))
			.andExpect(jsonPath("$.passengers[0].bookingCode").value(code))
			.andExpect(jsonPath("$.passengers[0].lastName").value("NGUYEN"))
			.andExpect(jsonPath("$.passengers[0].ticketNumber").value(matchesPattern("901\\d{10}")))
			.andExpect(jsonPath("$.passengers[0].cabinClass").value("ECONOMY"))
			.andExpect(jsonPath("$.passengers[0].contactPhone").value("0901234567"))
			.andExpect(jsonPath("$.passengers[0].contactEmail").value("an@example.com"));
	}

	@Test
	void manifestNeedsStaffAndExistingFlight() throws Exception {
		mvc.perform(get("/api/staff/flights/{id}/passengers", trip.back()).session(customer.session()))
			.andExpect(status().isForbidden());

		mvc.perform(get("/api/staff/flights/{id}/passengers", 999999999L).session(staff.session()))
			.andExpect(status().isNotFound());

		long bookingId = TestBookings.bookingId(jdbc, code);
		long returnJourneyId = jdbc.sql("SELECT id FROM booking_journeys WHERE booking_id = ? AND direction = 'RETURN'")
			.param(bookingId)
			.query(Long.class)
			.single();
		api.refundJourney(returnJourneyId);

		mvc.perform(get("/api/staff/flights/{id}/passengers", trip.back()).session(staff.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passengers").isEmpty());
	}

}

