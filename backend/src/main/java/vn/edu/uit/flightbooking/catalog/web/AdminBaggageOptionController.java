package vn.edu.uit.flightbooking.catalog.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

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

import vn.edu.uit.flightbooking.catalog.domain.BaggageOptionService;

/** Tab "Bảng giá hành lý" của màn hình A-03 (FR-83). */
@RestController
@RequestMapping("/api/admin")
class AdminBaggageOptionController {

	record BaggageOptionRequest(@NotNull @Min(1) Integer weightKg, @NotNull @Min(1) Long price,
			@NotNull Boolean active) {
	}

	private final BaggageOptionService options;

	AdminBaggageOptionController(BaggageOptionService options) {
		this.options = options;
	}

	@GetMapping("/airlines/{code}/baggage-options")
	List<BaggageOptionResponse> list(@PathVariable String code) {
		return options.list(code).stream().map(BaggageOptionResponse::of).toList();
	}

	@PostMapping("/airlines/{code}/baggage-options")
	@ResponseStatus(HttpStatus.CREATED)
	BaggageOptionResponse create(@PathVariable String code, @Valid @RequestBody BaggageOptionRequest body) {
		return BaggageOptionResponse.of(options.create(code, body.weightKg(), body.price(), body.active()));
	}

	@PutMapping("/baggage-options/{id}")
	BaggageOptionResponse update(@PathVariable long id, @Valid @RequestBody BaggageOptionRequest body) {
		return BaggageOptionResponse.of(options.update(id, body.weightKg(), body.price(), body.active()));
	}

	@DeleteMapping("/baggage-options/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long id) {
		options.delete(id);
	}

}
