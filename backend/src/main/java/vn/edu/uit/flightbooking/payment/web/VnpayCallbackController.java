package vn.edu.uit.flightbooking.payment.web;

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.payment.domain.PaymentCallbackService;
import vn.edu.uit.flightbooking.payment.domain.PaymentCallbackService.Outcome;

/**
 * Callback của VNPay (TDD §8.5), công khai và được bảo vệ bằng chữ ký. Là GET nên không cần CSRF (TDD §4.2). Log mỗi
 * callback theo TDD §12, không ghi chữ ký.
 */
@RestController
@RequestMapping("/api/payments/vnpay")
class VnpayCallbackController {

	private static final Logger log = LoggerFactory.getLogger(VnpayCallbackController.class);

	private final PaymentCallbackService callbacks;

	private final String baseUrl;

	VnpayCallbackController(PaymentCallbackService callbacks, @Value("${app.base-url}") String baseUrl) {
		this.callbacks = callbacks;
		this.baseUrl = baseUrl;
	}

	/** VNPay gọi từ server; luôn trả 200. Lỗi không lường trước trả 99 để VNPay gọi lại (TDD §6.3 bước 6). */
	@GetMapping("/ipn")
	Map<String, String> ipn(@RequestParam Map<String, String> params) {
		try {
			Outcome outcome = callbacks.handle(params);
			logged("IPN", params, outcome.rspCode());
			return Map.of("RspCode", outcome.rspCode(), "Message", outcome.message());
		}
		catch (RuntimeException e) {
			log.error("VNPay IPN lỗi: txnRef={}", params.get("vnp_TxnRef"), e);
			return Map.of("RspCode", "99", "Message", "Unknown error");
		}
	}

	/**
	 * AD-07: trình duyệt quay về; xử lý như IPN (idempotent) rồi chuyển về C-09 kèm kết quả. Lỗi không lường trước vẫn
	 * chuyển về C-08 thay vì hiện JSON lỗi cho khách; IPN sẽ xử lý lại.
	 */
	@GetMapping("/return")
	ResponseEntity<Void> back(@RequestParam Map<String, String> params) {
		Outcome outcome;
		try {
			outcome = callbacks.handle(params);
		}
		catch (RuntimeException e) {
			log.error("VNPay Return lỗi: txnRef={}", params.get("vnp_TxnRef"), e);
			outcome = new Outcome("99", "Unknown error", null, false);
		}
		logged("Return", params, outcome.rspCode());
		String target = outcome.bookingCode() == null ? baseUrl + "/bookings?payment=failed"
				: baseUrl + "/bookings/" + outcome.bookingCode() + "?payment=" + (outcome.paid() ? "success" : "failed");
		return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
	}

	private static void logged(String channel, Map<String, String> params, String rspCode) {
		log.info("VNPay {}: txnRef={}, vnp_ResponseCode={}, RspCode={}", channel, params.get("vnp_TxnRef"),
				params.get("vnp_ResponseCode"), rspCode);
	}

}
