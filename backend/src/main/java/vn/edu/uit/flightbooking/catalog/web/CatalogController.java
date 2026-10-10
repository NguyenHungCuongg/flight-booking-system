package vn.edu.uit.flightbooking.catalog.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.catalog.domain.AirlineService;
import vn.edu.uit.flightbooking.catalog.domain.AirportService;

/** API danh mục công khai (TDD §8.2), không cần đăng nhập. */
@RestController
@RequestMapping("/api")
class CatalogController {

	private final AirportService airports;

	private final AirlineService airlines;

	CatalogController(AirportService airports, AirlineService airlines) {
		this.airports = airports;
		this.airlines = airlines;
	}

	/** FR-10. Không có {@code q} thì trả mọi sân bay đang hoạt động. */
	@GetMapping("/airports")
	List<AirportResponse> airports(@RequestParam(defaultValue = "") String q) {
		return airports.suggest(q).stream().map(AirportResponse::of).toList();
	}

	@GetMapping("/airlines")
	List<AirlineResponse> airlines() {
		return airlines.listActive().stream().map(AirlineResponse::of).toList();
	}

}
