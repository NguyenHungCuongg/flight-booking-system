package vn.edu.uit.flightbooking.booking.web;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.booking.domain.BookingRequest;
import vn.edu.uit.flightbooking.booking.domain.QuoteService;
import vn.edu.uit.flightbooking.common.CurrentUser;

/** Màn hình C-07: báo giá (FR-20). Giữ chỗ (FR-24) thêm ở Plan 11. Quyền CUSTOMER theo đường dẫn ở SecurityConfig. */
@RestController
@RequestMapping("/api/bookings")
class BookingController {

	private final QuoteService quotes;

	BookingController(QuoteService quotes) {
		this.quotes = quotes;
	}

	@PostMapping("/quote")
	QuoteResponse quote(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody BookingRequest body) {
		return QuoteResponse.of(quotes.quote(me.id(), body));
	}

}
