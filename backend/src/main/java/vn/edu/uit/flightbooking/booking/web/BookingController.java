package vn.edu.uit.flightbooking.booking.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.booking.domain.BookingCancellation;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingService;
import vn.edu.uit.flightbooking.booking.domain.QuoteService;
import vn.edu.uit.flightbooking.common.CurrentUser;

/**
 * Màn hình C-07, C-09: báo giá (FR-20), giữ chỗ (FR-24), huỷ giữ chỗ (FR-33). Quyền CUSTOMER theo đường dẫn ở
 * SecurityConfig; quyền chủ booking kiểm tra trong service (BR-104).
 */
@RestController
@RequestMapping("/api/bookings")
class BookingController {

	private final QuoteService quotes;

	private final BookingService bookings;

	private final BookingCancellation cancellation;

	BookingController(QuoteService quotes, BookingService bookings, BookingCancellation cancellation) {
		this.quotes = quotes;
		this.bookings = bookings;
		this.cancellation = cancellation;
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

	@PostMapping("/{code}/cancel")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void cancel(@AuthenticationPrincipal CurrentUser me, @PathVariable String code) {
		cancellation.cancel(me.id(), code);
	}

}
