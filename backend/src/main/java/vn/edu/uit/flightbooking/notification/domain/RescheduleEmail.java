package vn.edu.uit.flightbooking.notification.domain;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import vn.edu.uit.flightbooking.aftersales.AftersalesApi;
import vn.edu.uit.flightbooking.aftersales.AftersalesApi.RescheduleNotice;
import vn.edu.uit.flightbooking.aftersales.RescheduleCompleted;
import vn.edu.uit.flightbooking.booking.BookingApi;
import vn.edu.uit.flightbooking.notification.infra.Mailer;

/**
 * Email đổi chuyến thành công kèm vé mới (FR-62, FR-130) gửi tới email liên hệ của booking. Chạy bất đồng bộ, sau khi
 * transaction hoàn tất đổi chuyến commit; gửi lỗi chỉ ghi log.
 */
@Component
class RescheduleEmail {

	private final AftersalesApi aftersales;

	private final BookingApi bookings;

	private final Mailer mailer;

	private final String baseUrl;

	RescheduleEmail(AftersalesApi aftersales, BookingApi bookings, Mailer mailer,
			@Value("${app.base-url}") String baseUrl) {
		this.aftersales = aftersales;
		this.bookings = bookings;
		this.mailer = mailer;
		this.baseUrl = baseUrl;
	}

	@ApplicationModuleListener
	void on(RescheduleCompleted event) {
		send(event.rescheduleId());
	}

	void send(long id) {
		RescheduleNotice notice = aftersales.reschedule(id);
		mailer.send(notice.contactEmail(), "Đổi chuyến thành công – mã đặt chỗ " + notice.bookingCode(),
				"reschedule-completed",
				Map.of("contactName", notice.contactName(), "code", notice.bookingCode(), "direction",
						notice.direction(), "amountDue", notice.amountDue(), "tickets",
						bookings.issued(notice.bookingId()).tickets(), "link",
						baseUrl + "/bookings/" + notice.bookingCode()));
	}

}
