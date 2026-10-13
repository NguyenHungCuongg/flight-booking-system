package vn.edu.uit.flightbooking.booking.web;

import java.time.OffsetDateTime;
import java.util.List;

import vn.edu.uit.flightbooking.booking.domain.BookingService;
import vn.edu.uit.flightbooking.booking.domain.BookingStatus;

/** Phản hồi 201 của POST /bookings, đúng ví dụ TDD §8.8: phần bảng giá giống hệt POST /bookings/quote. */
public record BookingCreatedResponse(String code, BookingStatus status, OffsetDateTime holdExpiresAt,
		List<QuoteResponse.JourneyTotals> journeys, long fareTotal, long baggageTotal, long discountTotal,
		long totalAmount) {

	static BookingCreatedResponse of(BookingService.Created created) {
		QuoteResponse totals = QuoteResponse.of(created.quote());
		return new BookingCreatedResponse(created.code(), created.status(), created.holdExpiresAt(), totals.journeys(),
				totals.fareTotal(), totals.baggageTotal(), totals.discountTotal(), totals.totalAmount());
	}

}
