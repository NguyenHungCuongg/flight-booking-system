package vn.edu.uit.flightbooking.catalog.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.catalog.domain.AirlineService;
import vn.edu.uit.flightbooking.catalog.domain.AirportService;
import vn.edu.uit.flightbooking.catalog.domain.BaggageOptionService;
import vn.edu.uit.flightbooking.catalog.domain.FareFamilyService;

/** API danh mục công khai (TDD §8.2), không cần đăng nhập. */
@RestController
@RequestMapping("/api")
class CatalogController {

	private final AirportService airports;

	private final AirlineService airlines;

	private final BaggageOptionService baggageOptions;

	private final FareFamilyService fareFamilies;

	CatalogController(AirportService airports, AirlineService airlines, BaggageOptionService baggageOptions,
			FareFamilyService fareFamilies) {
		this.airports = airports;
		this.airlines = airlines;
		this.baggageOptions = baggageOptions;
		this.fareFamilies = fareFamilies;
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

	/** FR-22: mức hành lý đang bán của hãng. */
	@GetMapping("/airlines/{code}/baggage-options")
	List<BaggageOptionResponse> baggageOptions(@PathVariable String code) {
		return baggageOptions.listActive(code).stream().map(BaggageOptionResponse::of).toList();
	}

	/** FR-82: điều kiện gói giá đang bán của hãng. */
	@GetMapping("/airlines/{code}/fare-families")
	List<FareFamilyResponse> fareFamilies(@PathVariable String code) {
		return fareFamilies.listActive(code).stream().map(FareFamilyResponse::of).toList();
	}

}
