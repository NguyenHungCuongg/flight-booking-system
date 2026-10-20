package vn.edu.uit.flightbooking.flight.web;

import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.common.LockRetry;
import vn.edu.uit.flightbooking.flight.FlightSearch;
import vn.edu.uit.flightbooking.flight.FlightSearch.SearchQuery;
import vn.edu.uit.flightbooking.flight.FlightSearch.SearchResult;
import vn.edu.uit.flightbooking.flight.domain.FlightQueries;
import vn.edu.uit.flightbooking.flight.domain.FlightQueries.FlightDetail;
import vn.edu.uit.flightbooking.flight.domain.FlightQueries.FlightView;
import vn.edu.uit.flightbooking.flight.domain.FlightQueries.Manifest;
import vn.edu.uit.flightbooking.flight.domain.FlightService;
import vn.edu.uit.flightbooking.flight.domain.FlightService.CreatedFlights;
import vn.edu.uit.flightbooking.flight.domain.FlightService.FlightChanges;
import vn.edu.uit.flightbooking.flight.domain.FlightService.NewFlights;
import vn.edu.uit.flightbooking.flight.domain.FlightStatus;

/**
 * Tìm chuyến (FR-11, FR-12: màn hình C-02) và quản trị chuyến bay (FR-72, FR-90–93, FR-94: S-05, S-06, A-04–A-06). Quyền
 * theo đường dẫn ở SecurityConfig.
 */
@RestController
@RequestMapping("/api")
class FlightController {

	private final FlightService flights;

	private final FlightQueries queries;

	private final FlightSearch search;

	private final LockRetry retry;

	FlightController(FlightService flights, FlightQueries queries, FlightSearch search, LockRetry retry) {
		this.flights = flights;
		this.queries = queries;
		this.search = search;
		this.retry = retry;
	}

	/** Công khai, không phân trang (TDD §6.1). */
	@GetMapping("/flights/search")
	SearchResult searchFlights(@Valid @ParameterObject SearchQuery query) {
		return search.search(query);
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

	/** Danh sách hành khách của chuyến cho Staff (FR-72, S-06). */
	@GetMapping("/staff/flights/{id}/passengers")
	Manifest passengers(@PathVariable long id) {
		return queries.manifest(id);
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

	@PutMapping("/admin/flights/{id}")
	FlightDetail update(@PathVariable long id, @Valid @RequestBody FlightChanges body) {
		flights.update(id, body);
		return queries.detail(id);
	}

	@PostMapping("/admin/flights/{id}/cancel")
	FlightDetail cancel(@PathVariable long id) {
		return retry.run(() -> {
			flights.cancel(id);
			return queries.detail(id);
		});
	}

	@DeleteMapping("/admin/flights/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long id) {
		flights.delete(id);
	}

}
