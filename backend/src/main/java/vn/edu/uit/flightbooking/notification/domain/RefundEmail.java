package vn.edu.uit.flightbooking.notification.domain;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import vn.edu.uit.flightbooking.aftersales.AftersalesApi;
import vn.edu.uit.flightbooking.aftersales.AftersalesApi.RefundNotice;
import vn.edu.uit.flightbooking.aftersales.RefundCompleted;
import vn.edu.uit.flightbooking.aftersales.RefundRejected;
import vn.edu.uit.flightbooking.notification.infra.Mailer;

/**
 * Email đã hoàn tiền và email từ chối hoàn (FR-52, FR-130) gửi tới email liên hệ của booking. Chạy bất đồng bộ, sau
 * khi transaction của Staff commit; gửi lỗi chỉ ghi log.
 */
@Component
class RefundEmail {

	private final AftersalesApi aftersales;

	private final Mailer mailer;

	private final String baseUrl;

	RefundEmail(AftersalesApi aftersales, Mailer mailer, @Value("${app.base-url}") String baseUrl) {
		this.aftersales = aftersales;
		this.mailer = mailer;
		this.baseUrl = baseUrl;
	}

	@ApplicationModuleListener
	void on(RefundCompleted event) {
		sendCompleted(event.refundRequestId());
	}

	@ApplicationModuleListener
	void on(RefundRejected event) {
		sendRejected(event.refundRequestId());
	}

	void sendCompleted(long id) {
		RefundNotice notice = aftersales.refund(id);
		mailer.send(notice.contactEmail(), "Đã hoàn tiền – mã đặt chỗ " + notice.bookingCode(), "refund-completed",
				variables(notice));
	}

	void sendRejected(long id) {
		RefundNotice notice = aftersales.refund(id);
		mailer.send(notice.contactEmail(), "Yêu cầu hoàn tiền bị từ chối – mã đặt chỗ " + notice.bookingCode(),
				"refund-rejected", variables(notice));
	}

	// HashMap vì direction (khoản thanh toán trùng/trễ) và staffNote có thể null, Map.of không nhận null.
	private Map<String, Object> variables(RefundNotice notice) {
		Map<String, Object> variables = new HashMap<>();
		variables.put("contactName", notice.contactName());
		variables.put("code", notice.bookingCode());
		variables.put("direction", notice.direction());
		variables.put("refundAmount", notice.refundAmount());
		variables.put("staffNote", notice.staffNote());
		variables.put("link", baseUrl + "/bookings/" + notice.bookingCode());
		return variables;
	}

}
