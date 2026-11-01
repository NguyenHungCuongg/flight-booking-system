package vn.edu.uit.flightbooking.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.edu.uit.flightbooking.booking.TestBookings.ADULT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.INFANT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.passenger;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestMailSender;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.booking.domain.BookingService;
import vn.edu.uit.flightbooking.common.Role;

/**
 * FR-31, FR-130: email vé gửi tới email liên hệ và có đủ số vé của mọi khách trên mọi chặng. Gọi thẳng
 * {@link TicketEmail#send}, vì listener sau commit không chạy trong test {@code @Transactional}. Email liên hệ ngẫu
 * nhiên, để không lẫn với email của test khác trong {@link TestMailSender}.
 */
@IntegrationTest
@Transactional
class TicketEmailTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TestMailSender mail;

	@Autowired
	BookingService bookings;

	@Autowired
	TicketEmail email;

	/** Một người lớn và một em bé, Q1100 QHA→QHB rồi Q1200 QHB→QHE: 4 vé, tổng 3.020.000 + 302.000. */
	@Test
	void ticketEmailListsEveryTicket() throws Exception {
		TestBookings.Trip trip = TestBookings.trip(mvc, jdbc);
		String contact = TestUsers.randomEmail();
		String code = TestBookings.holdCode(mvc, TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER).session(),
				TestBookings.body(trip, Map.of("passengers",
						List.of(passenger("ADULT", ADULT_BORN), passenger("INFANT", INFANT_BORN)), "contact",
						Map.of("name", "NGUYEN VAN AN", "email", contact, "phone", "0901234567"))));
		long bookingId = TestBookings.bookingId(jdbc, code);
		bookings.issue(bookingId);

		email.send(bookingId);

		List<String> numbers = jdbc.sql("""
				SELECT t.ticket_number FROM tickets t JOIN passengers p ON p.id = t.passenger_id
				WHERE p.booking_id = ?
				""").param(bookingId).query(String.class).list();
		assertThat(numbers).hasSize(4).doesNotContainNull();
		assertThat(mail.sentTo(contact)).hasSize(1);
		MimeMessage message = mail.sentTo(contact).getFirst();
		String html = (String) message.getContent();
		assertThat(message.getSubject()).isEqualTo("Vé điện tử SkyLine – mã đặt chỗ " + code);
		assertThat(html).contains(code, "NGUYEN VAN AN", "Q1100", "Q1200", "QHA", "QHB", "QHE", "3.322.000",
				"06:00 " + DAY.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
				"http://localhost:3000/bookings/" + code);
		assertThat(html).contains(numbers);
	}

}
