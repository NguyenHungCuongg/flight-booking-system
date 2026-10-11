package vn.edu.uit.flightbooking.flight.web;

import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.flight.domain.FlightQueries;
import vn.edu.uit.flightbooking.flight.domain.FlightQueries.FlightDetail;
import vn.edu.uit.flightbooking.flight.domain.FlightQueries.FlightView;
import vn.edu.uit.flightbooking.flight.domain.FlightService;
import vn.edu.uit.flightbooking.flight.domain.FlightService.CreatedFlights;
import vn.edu.uit.flightbooking.flight.domain.FlightService.NewFlights;
import vn.edu.uit.flightbooking.flight.domain.FlightStatus;

/** Quản trị chuyến bay (FR-90–92, FR-94): màn hình S-05, A-04–A-06. Quyền theo đường dẫn ở SecurityConfig. */
@RestController
@RequestMapping("/api")
class FlightController {

	private final FlightService flights;

	private final FlightQueries queries;

	FlightController(FlightService flights, FlightQueries queries) {
		this.flights = flights;
		this.queries = queries;
	}

	/** Staff và Admin dùng chung (TDD §8.6). */
	@GetMapping("/staff/flights")
	PagedModel<FlightView> list(@RequestParam(required = false) String from,
			@RequestParam(required = false) String to,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) String airline, @RequestParam(required = false) FlightStatus status,
			@PageableDefault(size = 20) Pageable pageable) {
		return new PagedModel<>(queries.list(from, to, date, airline, status, pageable));
	}

	@PostMapping("/admin/flights")
	@ResponseStatus(HttpStatus.CREATED)
	CreatedFlights create(@Valid @RequestBody NewFlights body) {
		return flights.create(body);
	}

	@GetMapping("/admin/flights/{id}")
	FlightDetail detail(@PathVariable long id) {
		return queries.detail(id);
	}

}
