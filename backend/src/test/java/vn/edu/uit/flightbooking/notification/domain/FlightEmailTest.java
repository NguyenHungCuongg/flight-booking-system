package vn.edu.uit.flightbooking.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.edu.uit.flightbooking.booking.TestBookings.ADULT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.CHILD_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.passenger;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;

import java.time.format.DateTimeFormatter;
import java.util.List;
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
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;

@IntegrationTest
@Transactional
class FlightEmailTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TestMailSender mail;

	@Autowired
	Vnpay vnpay;

	@Autowired
	FlightEmail email;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	String issuedContact;

	String pendingContact;

	String code;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		issuedContact = TestUsers.randomEmail();
		pendingContact = TestUsers.randomEmail();

		code = TestRefunds.issued(mvc, jdbc, vnpay, customer,
				TestBookings.body(trip, Map.of("journeys", trip.roundTrip(), "passengers",
						List.of(passenger("ADULT", ADULT_BORN), passenger("CHILD", CHILD_BORN)), "contact",
						Map.of("name", "NGUYEN VAN AN", "email", issuedContact, "phone", "0901234567"))));

		TestBookings.holdCode(mvc, customer.session(), TestBookings.body(trip, Map.of("journeys", trip.roundTrip(),
				"contact", Map.of("name", "TRAN VAN B", "email", pendingContact, "phone", "0901234568"))));
	}

	/** BR-83, Review Focus 5: một email cho booking đã xuất vé, kèm giờ mới; booking chờ thanh toán không nhận. */
	@Test
	void timeChangedEmailGoesOncePerIssuedBooking() throws Exception {
		jdbc.sql("""
				UPDATE flights SET departure_time = departure_time + interval '3 hours',
				                   arrival_time = arrival_time + interval '3 hours'
				WHERE id = ?
				""").param(trip.back()).update();

		email.sendTimeChanged(trip.back());

		assertThat(mail.sentTo(pendingContact)).isEmpty();
		assertThat(mail.sentTo(issuedContact)).hasSize(1);
		MimeMessage message = mail.sentTo(issuedContact).getFirst();
		assertThat(message.getSubject()).isEqualTo("Chuyến bay Q1300 đổi giờ – mã đặt chỗ " + code);
		String newDeparture = "09:00 " + DAY.plusDays(3).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
		assertThat((String) message.getContent()).contains(code, "NGUYEN VAN AN", "Q1300", "QHE → QHA", newDeparture,
				"http://localhost:3000/bookings/" + code);
	}

	/** BR-84 (d): booking đã xuất vé được báo chuyến bị huỷ và yêu cầu hoàn 100%. */
	@Test
	void cancelledEmailMentionsFullRefund() throws Exception {
		email.sendCancelled(trip.back());

		assertThat(mail.sentTo(pendingContact)).isEmpty();
		assertThat(mail.sentTo(issuedContact)).singleElement().satisfies(m -> {
			assertThat(m.getSubject()).isEqualTo("Chuyến bay Q1300 bị huỷ – mã đặt chỗ " + code);
			assertThat((String) m.getContent()).contains(code, "Q1300", "hoàn 100%",
					"http://localhost:3000/bookings/" + code);
		});
	}

}

