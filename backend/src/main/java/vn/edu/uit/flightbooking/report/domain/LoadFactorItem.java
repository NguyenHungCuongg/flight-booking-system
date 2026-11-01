package vn.edu.uit.flightbooking.report.domain;

import java.time.OffsetDateTime;

public record LoadFactorItem(long flightId, String flightNumber, String airlineCode,
		String departureAirport, String arrivalAirport, OffsetDateTime departureTime,
		int totalSeats, int occupiedSeats, double loadFactorPercent) {
}
