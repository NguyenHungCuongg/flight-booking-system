package vn.edu.uit.flightbooking.aftersales.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.aftersales.domain.CustomerRefunds;
import vn.edu.uit.flightbooking.aftersales.domain.RefundCreatedResponse;
import vn.edu.uit.flightbooking.aftersales.domain.RefundQuote;
import vn.edu.uit.flightbooking.common.CurrentUser;

/** FR-50, FR-51: khách xem trước và gửi yêu cầu hoàn một chiều. Quyền CUSTOMER theo đường dẫn ở SecurityConfig. */
@RestController
@RequestMapping("/api/bookings/{code}/journeys/{direction}")
class RefundController {

	private final CustomerRefunds refunds;

	RefundController(CustomerRefunds refunds) {
		this.refunds = refunds;
	}

	@GetMapping("/refund-quote")
	RefundQuote quote(@AuthenticationPrincipal CurrentUser me, @PathVariable String code,
			@PathVariable String direction) {
		return refunds.quote(me.id(), code, direction);
	}

	@PostMapping("/refund-requests")
	@ResponseStatus(HttpStatus.CREATED)
	RefundCreatedResponse request(@AuthenticationPrincipal CurrentUser me, @PathVariable String code,
			@PathVariable String direction, @Valid @RequestBody ReasonBody body) {
		return refunds.request(me.id(), code, direction, body.reason());
	}

}
