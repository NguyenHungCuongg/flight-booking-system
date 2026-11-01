package vn.edu.uit.flightbooking.booking.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;
import static vn.edu.uit.flightbooking.booking.TestBookings.ADULT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.INFANT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.passenger;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.promotion.TestVouchers;

/** FR-40, FR-41, FR-70, BR-24, BR-104, T15 (phần 404). */
@IntegrationTest
@Transactional
class BookingQueriesTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
	}

	String hold(MockHttpSession session, Map<String, Object> changes) throws Exception {
		return TestBookings.holdCode(mvc, session, TestBookings.body(trip, changes));
	}

	ResultActions getAs(MockHttpSession session, String url, Object... vars) throws Exception {
		return mvc.perform(get(url, vars).session(session));
	}

	/** Truyền {@code q} bằng {@code param} chứ không qua URI template: dấu {@code +} trong template bị đọc thành khoảng trắng. */
	ResultActions search(MockHttpSession session, String q) throws Exception {
		return mvc.perform(get("/api/staff/bookings").param("q", q).session(session));
	}

	/** FR-40: mới nhất trước, lọc theo trạng thái, chỉ booking của mình; tuyến theo múi giờ sân bay. */
	@Test
	void listsMyBookingsNewestFirstWithStatusFilter() throws Exception {
		String oneWay = hold(customer.session(), Map.of());
		String roundTrip = hold(customer.session(), Map.of("journeys", trip.roundTrip()));
		mvc.perform(post("/api/bookings/{code}/cancel", oneWay).with(csrf(mvc)).session(customer.session()))
			.andExpect(status().isNoContent());
		hold(TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER).session(), Map.of());

		getAs(customer.session(), "/api/bookings").andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].code", contains(roundTrip, oneWay)))
			.andExpect(jsonPath("$.page.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].status").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.content[0].totalAmount").value(5_180_000))
			.andExpect(jsonPath("$.content[0].routes[*].direction", contains("OUTBOUND", "RETURN")))
			.andExpect(jsonPath("$.content[0].routes[0].departureAirport").value("QHA"))
			.andExpect(jsonPath("$.content[0].routes[0].arrivalAirport").value("QHE"))
			.andExpect(jsonPath("$.content[0].routes[0].departureTime").value(DAY + "T06:00:00+07:00"))
			.andExpect(jsonPath("$.content[0].routes[1].departureAirport").value("QHE"))
			.andExpect(jsonPath("$.content[0].routes[1].arrivalAirport").value("QHA"))
			.andExpect(jsonPath("$.content[1].routes[0].arrivalAirport").value("QHE"));
		getAs(customer.session(), "/api/bookings?status=CANCELLED")
			.andExpect(jsonPath("$.content[*].code", contains(oneWay)));
	}

	/** FR-41, BR-24: chi tiết đọc từ snapshot, Admin đổi gói giá hay giá bán sau khi giữ chỗ không ảnh hưởng. */
	@Test
	void detailShowsSnapshotPassengersTicketsAndBaggage() throws Exception {
		long bag20 = TestBookings.baggageOption(jdbc, "Q1", 20, 350_000);
		TestVouchers.insert(jdbc, "QVWELCOME", Map.of("max_discount", 200_000));
		String code = hold(customer.session(), Map.of("journeys", trip.roundTrip(), "passengers",
				List.of(passenger("ADULT", ADULT_BORN), passenger("INFANT", INFANT_BORN)), "baggage",
				List.of(TestBookings.bag(0, "OUTBOUND", bag20)), "voucherCode", "QVWELCOME"));
		jdbc.sql("UPDATE fare_families SET name = 'Eco Mới', refundable = TRUE WHERE id = ?")
			.param(trip.catalog().economyFare())
			.update();
		jdbc.sql("UPDATE flight_fares SET price = 9999000 WHERE flight_id = ?").param(trip.firstLeg()).update();

		getAs(customer.session(), "/api/bookings/{code}", code).andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.contact.name").value("NGUYEN VAN AN"))
			.andExpect(jsonPath("$.voucherCode").value("QVWELCOME"))
			.andExpect(jsonPath("$.totalAmount").value(5_848_000))
			.andExpect(jsonPath("$.journeys[*].direction", contains("OUTBOUND", "RETURN")))
			.andExpect(jsonPath("$.journeys[0].fareFamilyName").value("Eco"))
			.andExpect(jsonPath("$.journeys[0].refundable").value(false))
			.andExpect(jsonPath("$.journeys[0].discount").value(121_428))
			.andExpect(jsonPath("$.journeys[0].segments[*].flightNumber", contains("Q1100", "Q1200")))
			.andExpect(jsonPath("$.journeys[0].segments[0].adultPrice").value(1_870_000))
			.andExpect(jsonPath("$.journeys[0].segments[0].departureTime").value(DAY + "T06:00:00+07:00"))
			.andExpect(jsonPath("$.journeys[0].segments[0].status").value("ACTIVE"))
			.andExpect(jsonPath("$.journeys[0].baggage[0].passengerIndex").value(0))
			.andExpect(jsonPath("$.journeys[0].baggage[0].weightKg").value(20))
			.andExpect(jsonPath("$.journeys[1].baggage").isEmpty())
			.andExpect(jsonPath("$.passengers[*].type", contains("ADULT", "INFANT")))
			.andExpect(jsonPath("$.passengers[1].tickets[*].flightNumber", contains("Q1100", "Q1200", "Q1300")))
			.andExpect(jsonPath("$.passengers[1].tickets[*].fareAmount", contains(187_000, 115_000, 216_000)))
			.andExpect(jsonPath("$.passengers[1].tickets[0].ticketNumber").value(nullValue()));
	}

	/** T15 phần 404, BR-104: booking của người khác giống hệt mã không tồn tại; mã không phân biệt hoa thường. */
	@Test
	void otherCustomersGetNotFound() throws Exception {
		String code = hold(customer.session(), Map.of());
		MockHttpSession someoneElse = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER).session();

		getAs(someoneElse, "/api/bookings/{code}", code).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		getAs(customer.session(), "/api/bookings/{code}", "ZZZZZZ").andExpect(status().isNotFound());
		getAs(customer.session(), "/api/bookings/{code}", code.toLowerCase()).andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(code));
	}

	/** FR-70: Staff tìm theo mã, email, số điện thoại liên hệ (không phân biệt hoa thường), xem tài khoản đã đặt. */
	@Test
	void staffFindsByCodeEmailOrPhoneAndSeesAccount() throws Exception {
		String code = hold(customer.session(), Map.of("contact",
				Map.of("name", "TRAN THI BINH", "email", "Binh.Lookup@Example.com", "phone", "+84912345678")));
		MockHttpSession staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF).session();

		search(staff, code.toLowerCase()).andExpect(jsonPath("$.content[*].code", contains(code)));
		search(staff, "binh.lookup@example.com").andExpect(jsonPath("$.content[*].code", contains(code)))
			.andExpect(jsonPath("$.content[0].contactPhone").value("+84912345678"));
		search(staff, "+84912345678").andExpect(jsonPath("$.content[*].code", contains(code)));
		search(staff, "khong-co-ai").andExpect(jsonPath("$.page.totalElements").value(0));

		getAs(staff, "/api/staff/bookings/{code}", code.toLowerCase()).andExpect(status().isOk())
			.andExpect(jsonPath("$.account.email").value(customer.email()))
			.andExpect(jsonPath("$.booking.code").value(code))
			.andExpect(jsonPath("$.booking.contact.name").value("TRAN THI BINH"));
		getAs(customer.session(), "/api/staff/bookings").andExpect(status().isForbidden());
	}

}
