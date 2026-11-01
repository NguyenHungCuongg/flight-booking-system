package vn.edu.uit.flightbooking.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.util.Map;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestMailSender;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.aftersales.TestRefunds;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;

/**
 * FR-52, FR-130: email đã hoàn tiền và email từ chối hoàn. Gọi thẳng {@link RefundEmail#sendCompleted} và
 * {@link RefundEmail#sendRejected}, vì listener sau commit không chạy trong test {@code @Transactional}.
 */
@IntegrationTest
@Transactional
class RefundEmailTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TestMailSender mail;

	@Autowired
	Vnpay vnpay;

	@Autowired
	RefundEmail email;

	TestUsers.TestUser customer;

	TestUsers.TestUser staff;

	String contact;

	String code;

	long id;

	@BeforeEach
	void setUp() throws Exception {
		TestBookings.Trip trip = TestBookings.trip(mvc, jdbc);
		TestRefunds.refundable(jdbc, trip);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF);
		contact = TestUsers.randomEmail();
		code = TestRefunds.issued(mvc, jdbc, vnpay, customer, TestBookings.body(trip, Map.of("journeys",
				trip.roundTrip(), "contact", Map.of("name", "NGUYEN VAN AN", "email", contact, "phone", "0901234567"))));
		id = TestRefunds.requestId(mvc, customer.session(), code, "OUTBOUND", "Đổi kế hoạch");
	}

	void act(String action, String json) throws Exception {
		mvc.perform(post("/api/staff/refund-requests/{id}/{action}", id, action).with(csrf(mvc))
			.session(staff.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)).andExpect(status().isOk());
	}

	@Test
	void completedEmailShowsAmount() throws Exception {
		act("approve", "{}");

		email.sendCompleted(id);

		assertThat(mail.sentTo(contact)).hasSize(1);
		MimeMessage message = mail.sentTo(contact).getFirst();
		assertThat(message.getSubject()).isEqualTo("Đã hoàn tiền – mã đặt chỗ " + code);
		assertThat((String) message.getContent()).contains(code, "NGUYEN VAN AN", "chiều đi", "2.720.000",
				"http://localhost:3000/bookings/" + code);
	}

	@Test
	void rejectedEmailShowsReason() throws Exception {
		act("reject", "{\"reason\": \"Vé đã dùng\"}");

		email.sendRejected(id);

		assertThat(mail.sentTo(contact)).hasSize(1);
		MimeMessage message = mail.sentTo(contact).getFirst();
		assertThat(message.getSubject()).isEqualTo("Yêu cầu hoàn tiền bị từ chối – mã đặt chỗ " + code);
		assertThat((String) message.getContent()).contains("Vé đã dùng", "chiều đi");
	}

}
