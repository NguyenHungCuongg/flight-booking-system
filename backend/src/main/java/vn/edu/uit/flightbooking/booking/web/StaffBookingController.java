package vn.edu.uit.flightbooking.booking.web;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.booking.domain.BookingQueries;
import vn.edu.uit.flightbooking.booking.domain.BookingQueries.BookingSummary;
import vn.edu.uit.flightbooking.booking.domain.BookingQueries.StaffBookingDetail;
import vn.edu.uit.flightbooking.booking.domain.BookingStatus;

/** Màn hình S-01, S-02: tra cứu booking (FR-70). Quyền STAFF (Admin kế thừa) theo đường dẫn ở SecurityConfig. */
@RestController
@RequestMapping("/api/staff/bookings")
class StaffBookingController {

	private final BookingQueries queries;

	StaffBookingController(BookingQueries queries) {
		this.queries = queries;
	}

	@GetMapping
	PagedModel<BookingSummary> search(@RequestParam(required = false) String q,
			@RequestParam(required = false) BookingStatus status, @PageableDefault(size = 20) Pageable pageable) {
		return new PagedModel<>(queries.search(q, status, pageable));
	}

	@GetMapping("/{code}")
	StaffBookingDetail detail(@PathVariable String code) {
		return queries.staffDetail(code);
	}

}
