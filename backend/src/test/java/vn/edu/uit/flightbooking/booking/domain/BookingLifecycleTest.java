package vn.edu.uit.flightbooking.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

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

/**
 * FR-33, BR-45, T5, BR-104. Chạy trong transaction của test, nên job nền không thấy dữ liệu; test gọi thẳng
 * {@link BookingExpiryJob#expireOverdue()}. {@code now()} của PostgreSQL là thời điểm test bắt đầu.
 */
@IntegrationTest
@Transactional
class BookingLifecycleTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	BookingExpiryJob job;

	@Autowired
	BookingCancellation cancellation;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
	}

	String hold(Map<String, Object> changes) throws Exception {
		return TestBookings.holdCode(mvc, customer.session(), TestBookings.body(trip, changes));
	}

	ResultActions cancel(MockHttpSession session, String code) throws Exception {
		return mvc.perform(post("/api/bookings/{code}/cancel", code).with(csrf(mvc)).session(session));
	}

	String bookingStatus(String code) {
		return jdbc.sql("SELECT status FROM bookings WHERE code = ?").param(code).query(String.class).single();
	}

	int available(long flightId) {
		return TestBookings.available(jdbc, flightId);
	}

	void holdEndedMinutesAgo(String code, int minutes) {
		jdbc.sql("UPDATE bookings SET hold_expires_at = now() - :minutes * INTERVAL '1 minute' WHERE code = :code")
			.param("minutes", minutes)
			.param("code", code)
			.update();
	}

	/** FR-33: khách huỷ thì mọi chặng thành CANCELLED, ghế và lượt voucher được trả (BR-32, BR-45). */
	@Test
	void customerCancelsPendingBookingAndGetsSeatsAndVoucherBack() throws Exception {
		long voucher = TestVouchers.insert(jdbc, "QVBACK", Map.of());
		String code = hold(Map.of("voucherCode", "QVBACK"));
		assertThat(available(trip.firstLeg())).isEqualTo(99);
		assertThat(TestVouchers.usedCount(jdbc, voucher)).isEqualTo(1);

		cancel(customer.session(), code).andExpect(status().isNoContent());

		assertThat(bookingStatus(code)).isEqualTo("CANCELLED");
		assertThat(jdbc.sql("""
				SELECT s.status FROM booking_segments s JOIN booking_journeys j ON j.id = s.journey_id
				JOIN bookings b ON b.id = j.booking_id WHERE b.code = ?
				""").param(code).query(String.class).list()).hasSize(2).containsOnly("CANCELLED");
		assertThat(available(trip.firstLeg())).isEqualTo(100);
		assertThat(available(trip.secondLeg())).isEqualTo(100);
		assertThat(TestVouchers.usedCount(jdbc, voucher)).isZero();
		cancel(customer.session(), code).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
	}

	/** BR-104: booking của người khác như không tồn tại. Booking đã xuất vé không huỷ được. Mã không phân biệt hoa thường. */
	@Test
	void onlyOwnersCancelAndOnlyWhilePending() throws Exception {
		String code = hold(Map.of());
		MockHttpSession someoneElse = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER).session();

		cancel(someoneElse, code).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		cancel(customer.session(), "ZZZZZZ").andExpect(status().isNotFound());
		assertThat(bookingStatus(code)).isEqualTo("PENDING_PAYMENT");

		TestVouchers.insert(jdbc, "QVFREE", Map.of("discount_value", 100));
		String issued = hold(Map.of("voucherCode", "QVFREE"));
		cancel(customer.session(), issued).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
		assertThat(bookingStatus(issued)).isEqualTo("ISSUED");

		cancel(customer.session(), code.toLowerCase()).andExpect(status().isNoContent());
		assertThat(bookingStatus(code)).isEqualTo("CANCELLED");
	}

	/** T5: quá hạn hơn 5 phút thì EXPIRED, trả ghế và lượt voucher; còn trong 5 phút ân hạn thì không bị đụng tới. */
	@Test
	void expiryJobWaitsForGracePeriod() throws Exception {
		long voucher = TestVouchers.insert(jdbc, "QVLATE", Map.of());
		String overdue = hold(Map.of("voucherCode", "QVLATE"));
		String inGrace = hold(Map.of());
		holdEndedMinutesAgo(overdue, 6);
		holdEndedMinutesAgo(inGrace, 2);
		assertThat(available(trip.firstLeg())).isEqualTo(98);

		assertThat(job.expireOverdue()).isEqualTo(1);

		assertThat(bookingStatus(overdue)).isEqualTo("EXPIRED");
		assertThat(bookingStatus(inGrace)).isEqualTo("PENDING_PAYMENT");
		assertThat(available(trip.firstLeg())).isEqualTo(99);
		assertThat(available(trip.secondLeg())).isEqualTo(99);
		assertThat(TestVouchers.usedCount(jdbc, voucher)).isZero();
	}

	/** APP_FLOW §4.3: khách huỷ rồi job chạy (hoặc ngược lại) thì ghế chỉ được trả một lần. */
	@Test
	void releasesSeatsOnlyOnce() throws Exception {
		String code = hold(Map.of());
		cancel(customer.session(), code).andExpect(status().isNoContent());
		holdEndedMinutesAgo(code, 10);

		assertThat(cancellation.expire(TestBookings.bookingId(jdbc, code))).isFalse();

		assertThat(bookingStatus(code)).isEqualTo("CANCELLED");
		assertThat(available(trip.firstLeg())).isEqualTo(100);
	}

	/** Quá hạn giữ chỗ nhưng còn trong 5 phút ân hạn: booking vẫn PENDING_PAYMENT nên khách vẫn huỷ được. */
	@Test
	void customerMayCancelDuringGrace() throws Exception {
		String code = hold(Map.of());
		holdEndedMinutesAgo(code, 2);

		cancel(customer.session(), code).andExpect(status().isNoContent());

		assertThat(bookingStatus(code)).isEqualTo("CANCELLED");
		assertThat(available(trip.firstLeg())).isEqualTo(100);
	}

}
