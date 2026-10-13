package vn.edu.uit.flightbooking.booking.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.booking.domain.BookingRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingService;
import vn.edu.uit.flightbooking.booking.domain.QuoteService;
import vn.edu.uit.flightbooking.common.CurrentUser;

/** Màn hình C-07: báo giá (FR-20) và giữ chỗ (FR-24). Quyền CUSTOMER theo đường dẫn ở SecurityConfig. */
@RestController
@RequestMapping("/api/bookings")
class BookingController {

	private final QuoteService quotes;

	private final BookingService bookings;

	BookingController(QuoteService quotes, BookingService bookings) {
		this.quotes = quotes;
		this.bookings = bookings;
	}

	@PostMapping("/quote")
	QuoteResponse quote(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody BookingRequest body) {
		return QuoteResponse.of(quotes.quote(me.id(), body));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	BookingCreatedResponse create(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody BookingRequest body) {
		return BookingCreatedResponse.of(bookings.create(me.id(), body));
	}

}
