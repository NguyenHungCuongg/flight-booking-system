package vn.edu.uit.flightbooking.booking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.booking.TicketEmailRequested;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.promotion.TestVouchers;

/** FR-73: Staff gửi lại email vé, chỉ cho booking ISSUED. */
@IntegrationTest
@Transactional
@RecordApplicationEvents
class ResendTicketEmailTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	ResultActions resend(MockHttpSession session, String code) throws Exception {
		return mvc.perform(post("/api/staff/bookings/{code}/resend-ticket-email", code).with(csrf(mvc)).session(session));
	}

	@Test
	void staffResendsTicketEmailOnlyForIssuedBookings() throws Exception {
		TestBookings.Trip trip = TestBookings.trip(mvc, jdbc);
		MockHttpSession customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER).session();
		MockHttpSession staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF).session();
		TestVouchers.insert(jdbc, "QVFREE", Map.of("discount_value", 100));
		String issued = TestBookings.holdCode(mvc, customer,
				TestBookings.body(trip, Map.of("voucherCode", "QVFREE")));
		String pending = TestBookings.holdCode(mvc, customer, TestBookings.body(trip, Map.of()));

		resend(staff, issued.toLowerCase()).andExpect(status().isNoContent());
		resend(staff, pending).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
		resend(staff, "ZZZZZZ").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		resend(customer, issued).andExpect(status().isForbidden());

		assertThat(events.stream(TicketEmailRequested.class))
			.containsExactly(new TicketEmailRequested(TestBookings.bookingId(jdbc, issued)));
	}

}
