package vn.edu.uit.flightbooking.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestMailSender;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.aftersales.TestRefunds;
import vn.edu.uit.flightbooking.aftersales.TestReschedules;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.flight.TestFlights;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;

/**
 * FR-62, FR-130: email đổi chuyến thành công kèm vé mới. Gọi thẳng {@link RescheduleEmail#send}, vì listener sau
 * commit không chạy trong test {@code @Transactional}.
 */
@IntegrationTest
@Transactional
class RescheduleEmailTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TestMailSender mail;

	@Autowired
	Vnpay vnpay;

	@Autowired
	RescheduleEmail email;

	String contact;

	String code;

	long id;

	@BeforeEach
	void setUp() throws Exception {
		TestBookings.Trip trip = TestBookings.trip(mvc, jdbc);
		TestUsers.TestUser customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		contact = TestUsers.randomEmail();
		code = TestRefunds.issued(mvc, jdbc, vnpay, customer, TestBookings.body(trip, Map.of("journeys",
				trip.roundTrip(), "contact", Map.of("name", "NGUYEN VAN AN", "email", contact, "phone", "0901234567"))));
		long q1320 = TestBookings.returnFlight(mvc, jdbc, trip, "Q1320", TestFlights.DAY.plusDays(4), "09:00",
				1_900_000);
		// Gói Eco mặc định có phí đổi 0 và Q1320 rẻ hơn Q1300: không phải trả gì, hoàn tất ngay.
		id = TestReschedules.confirmId(mvc, customer.session(), code, "RETURN", q1320);
	}

	@Test
	void completedEmailListsNewTickets() throws Exception {
		email.send(id);

		assertThat(mail.sentTo(contact)).hasSize(1);
		MimeMessage message = mail.sentTo(contact).getFirst();
		assertThat(message.getSubject()).isEqualTo("Đổi chuyến thành công – mã đặt chỗ " + code);
		String ticketNumber = jdbc.sql("""
				SELECT t.ticket_number FROM tickets t
				JOIN booking_segments s ON s.id = t.segment_id
				JOIN flights f ON f.id = s.flight_id
				JOIN booking_journeys j ON j.id = s.journey_id
				JOIN bookings b ON b.id = j.booking_id
				WHERE b.code = ? AND f.flight_number = 'Q1320'
				""").param(code).query(String.class).single();
		String html = (String) message.getContent();
		assertThat(html).contains(code, "NGUYEN VAN AN", "chiều về", "Q1320", ticketNumber,
				"http://localhost:3000/bookings/" + code);
		assertThat(html).doesNotContain("Q1300");
	}

}
