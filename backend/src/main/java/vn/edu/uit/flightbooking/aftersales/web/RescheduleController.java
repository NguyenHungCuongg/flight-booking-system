package vn.edu.uit.flightbooking.aftersales.web;

import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.aftersales.domain.CustomerReschedules;
import vn.edu.uit.flightbooking.aftersales.domain.RescheduleOptions;
import vn.edu.uit.flightbooking.aftersales.domain.RescheduleQuote;
import vn.edu.uit.flightbooking.common.CurrentUser;

/** FR-60, FR-61: khách xem lựa chọn thay thế và báo giá đổi chuyến. Quyền CUSTOMER theo đường dẫn ở SecurityConfig. */
@RestController
class RescheduleController {

	private final CustomerReschedules reschedules;

	RescheduleController(CustomerReschedules reschedules) {
		this.reschedules = reschedules;
	}

	@GetMapping("/api/bookings/{code}/journeys/{direction}/reschedule-options")
	RescheduleOptions options(@AuthenticationPrincipal CurrentUser me, @PathVariable String code,
			@PathVariable String direction, @RequestParam LocalDate date) {
		return reschedules.options(me.id(), code, direction, date);
	}

	@PostMapping("/api/bookings/{code}/journeys/{direction}/reschedule-quote")
	RescheduleQuote quote(@AuthenticationPrincipal CurrentUser me, @PathVariable String code,
			@PathVariable String direction, @Valid @RequestBody FlightIdsBody body) {
		return reschedules.quote(me.id(), code, direction, body.flightIds());
	}

}
