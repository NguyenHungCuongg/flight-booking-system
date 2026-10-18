package vn.edu.uit.flightbooking.aftersales.domain;

import vn.edu.uit.flightbooking.flight.FlightSearch.Itinerary;

/** FR-60: một hành trình thay thế, chỉ còn gói giá của chiều, kèm báo giá. */
public record RescheduleOption(Itinerary itinerary, RescheduleQuote quote) {

}
