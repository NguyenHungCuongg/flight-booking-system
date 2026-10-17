package vn.edu.uit.flightbooking.aftersales.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.aftersales.RefundCompleted;
import vn.edu.uit.flightbooking.aftersales.RefundRejected;
import vn.edu.uit.flightbooking.aftersales.TestRefunds;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.booking.domain.BookingCancellation;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.payment.TestVnpay;
import vn.edu.uit.flightbooking.payment.TestVnpayApi;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;

/** FR-71, BR-62–66, T7: Staff duyệt, từ chối, thử lại và xác nhận hoàn thủ công. */
@IntegrationTest
@Transactional
@RecordApplicationEvents
class StaffRefundTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	Vnpay vnpay;

	@Autowired
	TestVnpayApi api;

	@Autowired
	BookingCancellation cancellation;

	@Autowired
	ApplicationEvents events;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	TestUsers.TestUser staff;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		TestRefunds.refundable(jdbc, trip);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF);
		api.reset();
	}

	ResultActions act(long id, String action, String json) throws Exception {
		MockHttpServletRequestBuilder request = post("/api/staff/refund-requests/{id}/{action}", id, action)
			.with(csrf(mvc))
			.session(staff.session());
		if (json != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return mvc.perform(request);
	}

	void ipn(String txnRef, long amount) throws Exception {
		mvc.perform(get("/api/payments/vnpay/ipn").params(TestVnpay.callback(vnpay, txnRef, amount, "00")))
			.andExpect(status().isOk());
	}

	ResultActions detail(long id) throws Exception {
		return mvc.perform(get("/api/staff/refund-requests/{id}", id).session(staff.session()));
	}

	/** Như T6: booking EXPIRED vẫn nhận tiền, sinh yêu cầu INVALID_PAYMENT; trả id yêu cầu. */
	long invalidPaymentRequest() throws Exception {
		String late = TestBookings.holdCode(mvc, customer.session(), TestBookings.body(trip, Map.of()));
		String txnRef = TestVnpay.start(mvc, customer.session(), late);
		long bookingId = TestBookings.bookingId(jdbc, late);
		jdbc.sql("UPDATE bookings SET hold_expires_at = now() - interval '10 minutes' WHERE id = ?")
			.param(bookingId)
			.update();
		assertThat(cancellation.expire(bookingId)).isTrue();
		ipn(txnRef, 3_020_000);
		return jdbc.sql("SELECT id FROM refund_requests WHERE source = 'INVALID_PAYMENT' AND booking_id = ?")
			.param(bookingId)
			.query(Long.class)
			.single();
	}

	/** T7, BR-62: hoàn chiều đi của vé khứ hồi; chiều về và booking giữ nguyên. */
	@Test
	void t7ApprovingOutboundOfRoundTripRefundsItAndKeepsBookingIssued() throws Exception {
		String code = TestRefunds.issued(mvc, jdbc, vnpay, customer,
				TestBookings.body(trip, Map.of("journeys", trip.roundTrip())));
		long id = TestRefunds.requestId(mvc, customer.session(), code, "OUTBOUND", "Đổi kế hoạch");

		act(id, "approve", "{\"note\": \"Đúng chính sách\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.refundedAmount").value(2_720_000))
			.andExpect(jsonPath("$.staffNote").value("Đúng chính sách"))
			.andExpect(jsonPath("$.processedBy").value(staff.email()))
			.andExpect(jsonPath("$.attempts[0].status").value("SUCCESS"));

		long bookingId = TestBookings.bookingId(jdbc, code);
		assertThat(jdbc.sql("SELECT status FROM bookings WHERE id = ?").param(bookingId).query(String.class).single())
			.isEqualTo("ISSUED");
		assertThat(jdbc.sql("SELECT direction || ':' || status FROM booking_journeys WHERE booking_id = ? ORDER BY direction")
			.param(bookingId).query(String.class).list()).containsExactly("OUTBOUND:REFUNDED", "RETURN:ACTIVE");
		assertThat(TestBookings.available(jdbc, trip.firstLeg())).isEqualTo(100);
		assertThat(TestBookings.available(jdbc, trip.secondLeg())).isEqualTo(100);
		assertThat(TestBookings.available(jdbc, trip.back())).isEqualTo(99);
		assertThat(jdbc.sql("""
				SELECT r.provider, r.status, r.amount, r.original_payment_id = c.id AS on_charge
				FROM payments r JOIN payments c ON c.booking_id = r.booking_id AND c.kind = 'CHARGE'
				WHERE r.refund_request_id = ?
				""").param(id).query().singleRow())
			.containsEntry("provider", "VNPAY").containsEntry("status", "SUCCESS")
			.containsEntry("amount", 2_720_000L).containsEntry("on_charge", true);
		assertThat(api.requests()).singleElement().satisfies(sent -> assertThat(sent)
			.containsEntry("vnp_TransactionType", "03").containsEntry("vnp_Amount", "272000000")
			.containsEntry("vnp_TransactionNo", "14000001").containsEntry("vnp_CreateBy", staff.email()));
		assertThat(events.stream(RefundCompleted.class)).containsExactly(new RefundCompleted(id));
	}

	/** Review Focus 2, BR-66: lượt thu trùng chỉ do yêu cầu INVALID_PAYMENT của nó hoàn. */
	@Test
	void journeyRefundLeavesDuplicateChargeToItsOwnRequest() throws Exception {
		String code = TestBookings.holdCode(mvc, customer.session(), TestBookings.body(trip, Map.of()));
		String first = TestVnpay.start(mvc, customer.session(), code);
		String second = TestVnpay.start(mvc, customer.session(), code);
		ipn(first, 3_020_000);
		ipn(second, 3_020_000);
		long invalid = jdbc.sql("SELECT id FROM refund_requests WHERE source = 'INVALID_PAYMENT' AND booking_id = ?")
			.param(TestBookings.bookingId(jdbc, code)).query(Long.class).single();
		long journey = TestRefunds.requestId(mvc, customer.session(), code, "OUTBOUND", "Đổi kế hoạch");

		act(journey, "approve", null).andExpect(jsonPath("$.status").value("COMPLETED"));
		act(invalid, "approve", null).andExpect(jsonPath("$.status").value("COMPLETED"));

		assertThat(api.requests()).extracting(r -> r.get("vnp_TxnRef") + ":" + r.get("vnp_TransactionType") + ":"
				+ r.get("vnp_Amount")).containsExactly(first + ":03:272000000", second + ":02:302000000");
		assertThat(jdbc.sql("SELECT status FROM bookings WHERE code = ?").param(code).query(String.class).single())
			.isEqualTo("REFUNDED");
	}

	/** BR-63, Review Focus 4: bị từ chối thì gửi lại được; đã hoàn xong thì không. */
	@Test
	void rejectedJourneyCanBeRequestedAgainButRefundedOneCannot() throws Exception {
		String code = TestRefunds.issued(mvc, jdbc, vnpay, customer,
				TestBookings.body(trip, Map.of("journeys", trip.roundTrip())));
		long id = TestRefunds.requestId(mvc, customer.session(), code, "OUTBOUND", "Đổi kế hoạch");

		act(id, "reject", null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		act(id, "reject", "{\"reason\": \"  \"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		act(id, "reject", "{\"reason\": \"Vé đã dùng\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.staffNote").value("Vé đã dùng"));
		assertThat(events.stream(RefundRejected.class)).containsExactly(new RefundRejected(id));
		long bookingId = TestBookings.bookingId(jdbc, code);
		assertThat(jdbc.sql("SELECT status FROM bookings WHERE id = ?").param(bookingId).query(String.class).single())
			.isEqualTo("ISSUED");
		assertThat(jdbc.sql("SELECT status FROM booking_journeys WHERE booking_id = ? AND direction = 'OUTBOUND'")
			.param(bookingId).query(String.class).single()).isEqualTo("ACTIVE");

		long again = TestRefunds.requestId(mvc, customer.session(), code, "OUTBOUND", "Gửi lại");
		act(again, "approve", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

		TestRefunds.request(mvc, customer.session(), code, "OUTBOUND", "Lần ba")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.detail").value("Chiều này đã được hoàn"));
		act(again, "reject", "{\"reason\": \"Muộn\"}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
	}

	/** BR-66: yêu cầu do hệ thống tạo chỉ được duyệt; booking EXPIRED giữ nguyên. */
	@Test
	void systemRequestsCannotBeRejected() throws Exception {
		long id = invalidPaymentRequest();

		act(id, "reject", "{\"reason\": \"Không hoàn\"}").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.detail").value("Yêu cầu do hệ thống tạo không thể bị từ chối"));
		act(id, "approve", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

		assertThat(api.requests()).singleElement().satisfies(sent -> assertThat(sent)
			.containsEntry("vnp_TransactionType", "02").containsEntry("vnp_Amount", "302000000"));
		assertThat(jdbc.sql("""
				SELECT b.status FROM bookings b JOIN refund_requests r ON r.booking_id = b.id WHERE r.id = ?
				""").param(id).query(String.class).single()).isEqualTo("EXPIRED");
	}

	/** Review Focus 3: phí ≥ giá trị chiều thì hoàn 0, xong ngay mà không gọi VNPay; ghế vẫn được trả. */
	@Test
	void zeroRefundCompletesWithoutCallingVnpay() throws Exception {
		String code = TestRefunds.issued(mvc, jdbc, vnpay, customer, TestBookings.body(trip, Map.of()));
		jdbc.sql("""
				UPDATE booking_journeys SET refund_fee = 5000000
				WHERE direction = 'OUTBOUND' AND booking_id = (SELECT id FROM bookings WHERE code = ?)
				""").param(code).update();
		TestRefunds.quote(mvc, customer.session(), code, "OUTBOUND").andExpect(jsonPath("$.refundAmount").value(0));
		long id = TestRefunds.requestId(mvc, customer.session(), code, "OUTBOUND", "Đổi kế hoạch");

		act(id, "approve", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

		assertThat(api.requests()).isEmpty();
		assertThat(jdbc.sql("SELECT count(*) FROM payments WHERE refund_request_id = ?").param(id)
			.query(Long.class).single()).isZero();
		assertThat(TestBookings.available(jdbc, trip.firstLeg())).isEqualTo(100);
	}

	/** BR-64: VNPay từ chối thì yêu cầu giữ APPROVED kèm lỗi; Staff xác nhận đã hoàn thủ công. */
	@Test
	void markManualCompletesWhatVnpayCouldNot() throws Exception {
		String code = TestRefunds.issued(mvc, jdbc, vnpay, customer,
				TestBookings.body(trip, Map.of("journeys", trip.roundTrip())));
		long id = TestRefunds.requestId(mvc, customer.session(), code, "OUTBOUND", "Đổi kế hoạch");
		api.respond("94");

		act(id, "approve", null).andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_ERROR"))
			.andExpect(jsonPath("$.detail").value("Hoàn tiền qua VNPay chưa xong: VNPay từ chối hoàn tiền (mã 94)"));
		detail(id).andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.lastError").value("VNPay từ chối hoàn tiền (mã 94)"));

		act(id, "mark-manual", "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("note"));
		act(id, "mark-manual", "{\"note\": \"Đã chuyển khoản ngày 01/12\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.lastError").value(nullValue()))
			.andExpect(jsonPath("$.refundedAmount").value(2_720_000))
			.andExpect(jsonPath("$.attempts[*].provider").value(contains("VNPAY", "MANUAL")))
			.andExpect(jsonPath("$.attempts[*].status").value(contains("FAILED", "SUCCESS")));

		act(id, "retry", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
	}

	/** Không còn lượt thu nào để hoàn: không gọi VNPay, giữ APPROVED kèm lỗi để Staff xử lý tay. */
	@Test
	void noChargeLeftKeepsRequestApprovedWithError() throws Exception {
		String code = TestRefunds.issued(mvc, jdbc, vnpay, customer, TestBookings.body(trip, Map.of()));
		long id = TestRefunds.requestId(mvc, customer.session(), code, "OUTBOUND", "Đổi kế hoạch");
		jdbc.sql("UPDATE payments SET status = 'FAILED' WHERE kind = 'CHARGE' AND booking_id = ?")
			.param(TestBookings.bookingId(jdbc, code))
			.update();

		act(id, "approve", null).andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.detail").value("Hoàn tiền qua VNPay chưa xong: Không còn lượt thu nào để hoàn"));
		detail(id).andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.lastError").value("Không còn lượt thu nào để hoàn"));
		assertThat(api.requests()).isEmpty();
	}

	/** FR-71, S-03, S-04: hàng đợi cũ nhất trước, lọc theo trạng thái và nguồn; chi tiết. */
	@Test
	void queueListsOldestFirstWithFiltersAndDetail() throws Exception {
		String code = TestRefunds.issued(mvc, jdbc, vnpay, customer,
				TestBookings.body(trip, Map.of("journeys", trip.roundTrip())));
		long outbound = TestRefunds.requestId(mvc, customer.session(), code, "OUTBOUND", "Đổi kế hoạch");
		TestRefunds.requestId(mvc, customer.session(), code, "RETURN", "Huỷ chiều về");

		mvc.perform(get("/api/staff/refund-requests").param("status", "PENDING").session(staff.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].direction").value(contains("OUTBOUND", "RETURN")))
			.andExpect(jsonPath("$.content[0].bookingCode").value(code));
		mvc.perform(get("/api/staff/refund-requests").param("source", "INVALID_PAYMENT").session(staff.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].source").value(everyItem(is("INVALID_PAYMENT"))));
		mvc.perform(get("/api/staff/refund-requests").param("status", "XYZ").session(staff.session()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		detail(outbound).andExpect(status().isOk())
			.andExpect(jsonPath("$.bookingStatus").value("ISSUED"))
			.andExpect(jsonPath("$.contactEmail").value("an@example.com"))
			.andExpect(jsonPath("$.refundedAmount").value(0))
			.andExpect(jsonPath("$.attempts").value(empty()));
		detail(999_999_999).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
			.andExpect(jsonPath("$.detail").value("Không tìm thấy yêu cầu hoàn"));
		mvc.perform(get("/api/staff/refund-requests").session(customer.session())).andExpect(status().isForbidden());

		act(outbound, "approve", null).andExpect(status().isOk());
		act(outbound, "approve", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
	}

}
