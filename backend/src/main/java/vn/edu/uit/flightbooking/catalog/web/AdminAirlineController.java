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

import vn.edu.uit.flightbooking.catalog.domain.AirlineService;

/** Màn hình A-02 (FR-81). */
@RestController
@RequestMapping("/api/admin/airlines")
class AdminAirlineController {

	record CreateAirlineRequest(
			@NotNull @Pattern(regexp = "[A-Z0-9]{2}", message = "Mã IATA gồm 2 ký tự in hoa hoặc số") String code,
			@NotBlank @Size(max = 100) String name,
			@NotNull @Pattern(regexp = "[0-9]{3}", message = "Mã số vé gồm 3 chữ số") String ticketPrefix,
			@NotNull Boolean active) {
	}

	record UpdateAirlineRequest(@NotBlank @Size(max = 100) String name,
			@NotNull @Pattern(regexp = "[0-9]{3}", message = "Mã số vé gồm 3 chữ số") String ticketPrefix,
			@NotNull Boolean active) {
	}

	private final AirlineService airlines;

	AdminAirlineController(AirlineService airlines) {
		this.airlines = airlines;
	}

	@GetMapping
	List<AirlineResponse> list() {
		return airlines.list().stream().map(AirlineResponse::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AirlineResponse create(@Valid @RequestBody CreateAirlineRequest body) {
		return AirlineResponse.of(airlines.create(body.code(), body.name(), body.ticketPrefix(), body.active()));
	}

	@PutMapping("/{code}")
	AirlineResponse update(@PathVariable String code, @Valid @RequestBody UpdateAirlineRequest body) {
		return AirlineResponse.of(airlines.update(code, body.name(), body.ticketPrefix(), body.active()));
	}

	@DeleteMapping("/{code}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable String code) {
		airlines.delete(code);
	}

}
