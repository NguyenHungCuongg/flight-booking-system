package vn.edu.uit.flightbooking.catalog.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.catalog.domain.AirportService;

/** Màn hình A-01 (FR-80). */
@RestController
@RequestMapping("/api/admin/airports")
class AdminAirportController {

	record CreateAirportRequest(
			@NotNull @Pattern(regexp = "[A-Z]{3}", message = "Mã IATA gồm 3 chữ cái in hoa") String code,
			@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 100) String city,
			@NotNull @Pattern(regexp = "[A-Z]{2}", message = "Mã quốc gia gồm 2 chữ cái in hoa") String countryCode,
			@NotBlank @Size(max = 50) String timezone, @NotNull Boolean active) {
	}

	record UpdateAirportRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 100) String city,
			@NotNull @Pattern(regexp = "[A-Z]{2}", message = "Mã quốc gia gồm 2 chữ cái in hoa") String countryCode,
			@NotBlank @Size(max = 50) String timezone, @NotNull Boolean active) {
	}

	private final AirportService airports;

	AdminAirportController(AirportService airports) {
		this.airports = airports;
	}

	@GetMapping
	List<AirportResponse> list() {
		return airports.list().stream().map(AirportResponse::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AirportResponse create(@Valid @RequestBody CreateAirportRequest body) {
		return AirportResponse.of(airports.create(body.code(), body.name(), body.city(), body.countryCode(),
				body.timezone(), body.active()));
	}

	@PutMapping("/{code}")
	AirportResponse update(@PathVariable String code, @Valid @RequestBody UpdateAirportRequest body) {
		return AirportResponse.of(airports.update(code, body.name(), body.city(), body.countryCode(),
				body.timezone(), body.active()));
	}

	@DeleteMapping("/{code}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable String code) {
		airports.delete(code);
	}

}
