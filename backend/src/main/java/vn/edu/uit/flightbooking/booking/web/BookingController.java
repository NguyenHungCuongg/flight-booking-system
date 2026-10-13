package vn.edu.uit.flightbooking.booking.web;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.booking.domain.BookingCancellation;
import vn.edu.uit.flightbooking.booking.domain.BookingQueries;
import vn.edu.uit.flightbooking.booking.domain.BookingQueries.BookingDetail;
import vn.edu.uit.flightbooking.booking.domain.BookingQueries.BookingSummary;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingService;
import vn.edu.uit.flightbooking.booking.domain.BookingStatus;
import vn.edu.uit.flightbooking.booking.domain.QuoteService;
import vn.edu.uit.flightbooking.common.CurrentUser;

/**
 * Màn hình C-07, C-08, C-09: báo giá (FR-20), giữ chỗ (FR-24), huỷ giữ chỗ (FR-33), danh sách (FR-40) và chi tiết
 * (FR-41). Quyền CUSTOMER theo đường dẫn ở SecurityConfig; quyền chủ booking kiểm tra trong service (BR-104).
 */
@RestController
@RequestMapping("/api/bookings")
class BookingController {

	private final QuoteService quotes;

	private final BookingService bookings;

	private final BookingCancellation cancellation;

	private final BookingQueries queries;

	BookingController(QuoteService quotes, BookingService bookings, BookingCancellation cancellation,
			BookingQueries queries) {
		this.quotes = quotes;
		this.bookings = bookings;
		this.cancellation = cancellation;
		this.queries = queries;
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

	@GetMapping
	PagedModel<BookingSummary> mine(@AuthenticationPrincipal CurrentUser me,
			@RequestParam(required = false) BookingStatus status, @PageableDefault(size = 20) Pageable pageable) {
		return new PagedModel<>(queries.mine(me.id(), status, pageable));
	}

	@GetMapping("/{code}")
	BookingDetail detail(@AuthenticationPrincipal CurrentUser me, @PathVariable String code) {
		return queries.detail(me.id(), code);
	}

	@PostMapping("/{code}/cancel")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void cancel(@AuthenticationPrincipal CurrentUser me, @PathVariable String code) {
		cancellation.cancel(me.id(), code);
	}

}
