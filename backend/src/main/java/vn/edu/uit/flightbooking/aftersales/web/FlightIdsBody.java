package vn.edu.uit.flightbooking.aftersales.web;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Các chuyến của hành trình mới, theo thứ tự bay (BR-02). */
public record FlightIdsBody(@NotEmpty @Size(max = 2) List<@NotNull Long> flightIds) {

}
