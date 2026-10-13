package vn.edu.uit.flightbooking.booking.web;

import java.util.List;

import vn.edu.uit.flightbooking.booking.domain.Direction;
import vn.edu.uit.flightbooking.booking.domain.Quote;

/**
 * Phản hồi của POST /bookings/quote, đúng các trường của ví dụ TDD §8.8, thêm {@code passengerFares} để C-07 hiện
 * giá vé theo loại khách (FR-20). Không lộ các trường nội bộ mà giữ chỗ cần.
 */
public record QuoteResponse(List<JourneyTotals> journeys, long fareTotal, long baggageTotal, long discountTotal,
		long totalAmount) {

	public record JourneyTotals(Direction direction, long fareTotal, long baggageTotal, long discount,
			List<Quote.PassengerFare> passengerFares) {
	}

	static QuoteResponse of(Quote quote) {
		return new QuoteResponse(quote.journeys()
			.stream()
			.map(j -> new JourneyTotals(j.direction(), j.fareTotal(), j.baggageTotal(), j.discount(),
					j.passengerFares()))
			.toList(), quote.fareTotal(), quote.baggageTotal(), quote.discountTotal(), quote.totalAmount());
	}

}
