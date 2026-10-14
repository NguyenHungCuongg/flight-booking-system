package vn.edu.uit.flightbooking.notification.domain;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import vn.edu.uit.flightbooking.booking.BookingApi;
import vn.edu.uit.flightbooking.booking.BookingApi.IssuedBooking;
import vn.edu.uit.flightbooking.booking.BookingIssued;
import vn.edu.uit.flightbooking.booking.TicketEmailRequested;
import vn.edu.uit.flightbooking.notification.infra.Mailer;

/**
 * Email vé điện tử (FR-31, FR-73, FR-130) gửi tới email liên hệ của booking. Chạy bất đồng bộ, sau khi transaction xuất
 * vé hoặc yêu cầu gửi lại commit; gửi lỗi chỉ ghi log.
 */
@Component
class TicketEmail {

	private final BookingApi bookings;

	private final Mailer mailer;

	private final String baseUrl;

	TicketEmail(BookingApi bookings, Mailer mailer, @Value("${app.base-url}") String baseUrl) {
		this.bookings = bookings;
		this.mailer = mailer;
		this.baseUrl = baseUrl;
	}

	@ApplicationModuleListener
	void on(BookingIssued event) {
		send(event.bookingId());
	}

	@ApplicationModuleListener
	void on(TicketEmailRequested event) {
		send(event.bookingId());
	}

	void send(long bookingId) {
		IssuedBooking booking = bookings.issued(bookingId);
		mailer.send(booking.contactEmail(), "Vé điện tử SkyLine – mã đặt chỗ " + booking.code(), "ticket",
				Map.of("contactName", booking.contactName(), "code", booking.code(), "totalAmount",
						booking.totalAmount(), "tickets", booking.tickets(), "link",
						baseUrl + "/bookings/" + booking.code()));
	}

}
