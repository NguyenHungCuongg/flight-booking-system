package vn.edu.uit.flightbooking.notification.domain;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import vn.edu.uit.flightbooking.booking.BookingApi;
import vn.edu.uit.flightbooking.booking.BookingApi.FlightContact;
import vn.edu.uit.flightbooking.flight.FlightApi;
import vn.edu.uit.flightbooking.flight.FlightApi.FlightRef;
import vn.edu.uit.flightbooking.flight.FlightCancelled;
import vn.edu.uit.flightbooking.flight.FlightTimeChanged;
import vn.edu.uit.flightbooking.notification.infra.Mailer;

/**
 * Email chuyến bay đổi giờ (BR-83) và bị huỷ (BR-84 d), gửi tới email liên hệ của các booking đã xuất vé có chặng
 * trên chuyến. Chạy bất đồng bộ sau commit; gửi lỗi chỉ ghi log.
 */
@Component
class FlightEmail {

	private final FlightApi flights;

	private final BookingApi bookings;

	private final Mailer mailer;

	private final String baseUrl;

	FlightEmail(FlightApi flights, BookingApi bookings, Mailer mailer, @Value("${app.base-url}") String baseUrl) {
		this.flights = flights;
		this.bookings = bookings;
		this.mailer = mailer;
		this.baseUrl = baseUrl;
	}

	@ApplicationModuleListener
	void on(FlightTimeChanged e) {
		sendTimeChanged(e.flightId());
	}

	@ApplicationModuleListener
	void on(FlightCancelled e) {
		sendCancelled(e.flightId());
	}

	/** Đọc người nhận trước: chuyến chưa có booking có thể đã bị xoá khi listener chạy. */
	void sendTimeChanged(long flightId) {
		List<FlightContact> contacts = bookings.contactsOn(flightId);
		if (contacts.isEmpty()) {
			return;
		}
		FlightRef flight = flights.flight(flightId);
		for (FlightContact c : contacts) {
			String subject = "Chuyến bay " + flight.flightNumber() + " đổi giờ – mã đặt chỗ " + c.code();
			mailer.send(c.contactEmail(), subject, "flight-time-changed",
					Map.of("contactName", c.contactName(), "code", c.code(), "flight", flight, "link",
							baseUrl + "/bookings/" + c.code()));
		}
	}

	void sendCancelled(long flightId) {
		List<FlightContact> contacts = bookings.contactsOn(flightId);
		if (contacts.isEmpty()) {
			return;
		}
		FlightRef flight = flights.flight(flightId);
		for (FlightContact c : contacts) {
			String subject = "Chuyến bay " + flight.flightNumber() + " bị huỷ – mã đặt chỗ " + c.code();
			mailer.send(c.contactEmail(), subject, "flight-cancelled",
					Map.of("contactName", c.contactName(), "code", c.code(), "flight", flight, "link",
							baseUrl + "/bookings/" + c.code()));
		}
	}

}
