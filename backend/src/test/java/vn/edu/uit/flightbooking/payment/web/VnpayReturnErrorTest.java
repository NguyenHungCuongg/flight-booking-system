package vn.edu.uit.flightbooking.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import vn.edu.uit.flightbooking.payment.domain.PaymentCallbackService;

/** Return URL gặp lỗi không lường trước vẫn chuyển trình duyệt về C-08, không hiện JSON lỗi. */
class VnpayReturnErrorTest {

	@Test
	void unexpectedErrorRedirectsToBookingList() {
		PaymentCallbackService callbacks = mock(PaymentCallbackService.class);
		when(callbacks.handle(any())).thenThrow(new IllegalStateException("DB down"));
		var controller = new VnpayCallbackController(callbacks, "http://localhost:3000");

		ResponseEntity<Void> response = controller.back(Map.of("vnp_TxnRef", "abc"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		assertThat(response.getHeaders().getLocation()).hasToString("http://localhost:3000/bookings?payment=failed");
	}

}
