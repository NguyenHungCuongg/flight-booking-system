package vn.edu.uit.flightbooking.catalog.web;

import vn.edu.uit.flightbooking.catalog.domain.Airline;

/** Hãng bay trả về cho danh sách công khai và màn hình A-02. */
record AirlineResponse(String code, String name, String ticketPrefix, boolean active) {

	static AirlineResponse of(Airline airline) {
		return new AirlineResponse(airline.getCode(), airline.getName(), airline.getTicketPrefix(), airline.isActive());
	}

}
