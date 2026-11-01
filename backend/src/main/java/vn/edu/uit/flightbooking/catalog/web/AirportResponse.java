package vn.edu.uit.flightbooking.catalog.web;

import vn.edu.uit.flightbooking.catalog.domain.Airport;

/** Sân bay trả về cho ô gợi ý (FR-10) và màn hình A-01. */
record AirportResponse(String code, String name, String city, String countryCode, String timezone, boolean active) {

	static AirportResponse of(Airport airport) {
		return new AirportResponse(airport.getCode(), airport.getName(), airport.getCity(), airport.getCountryCode(),
				airport.getTimezone(), airport.isActive());
	}

}
