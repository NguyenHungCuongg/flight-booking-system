package vn.edu.uit.flightbooking.catalog.web;

import vn.edu.uit.flightbooking.catalog.domain.BaggageOption;

/** Mức hành lý trả về cho bước chọn hành lý (FR-22) và màn hình A-03. */
record BaggageOptionResponse(long id, String airlineCode, int weightKg, long price, boolean active) {

	static BaggageOptionResponse of(BaggageOption option) {
		return new BaggageOptionResponse(option.getId(), option.getAirlineCode(), option.getWeightKg(),
				option.getPrice(), option.isActive());
	}

}
