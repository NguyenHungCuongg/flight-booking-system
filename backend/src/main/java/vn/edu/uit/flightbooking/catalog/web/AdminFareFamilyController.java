package vn.edu.uit.flightbooking.catalog.web;

import java.util.List;

import jakarta.validation.Valid;

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

import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.catalog.domain.FareFamily;
import vn.edu.uit.flightbooking.catalog.domain.FareFamilyFields;
import vn.edu.uit.flightbooking.catalog.domain.FareFamilyService;

/** Tab "Gói giá" của màn hình A-03 (FR-82). */
@RestController
@RequestMapping("/api/admin")
class AdminFareFamilyController {

	record FareFamilyResponse(long id, String airlineCode, CabinClass cabinClass, String name, int carryOnKg,
			int checkedBaggageKg, boolean refundable, long refundFee, boolean changeable, long changeFee,
			boolean active) {

		static FareFamilyResponse of(FareFamily f) {
			return new FareFamilyResponse(f.getId(), f.getAirlineCode(), f.getCabinClass(), f.getName(),
					f.getCarryOnKg(), f.getCheckedBaggageKg(), f.isRefundable(), f.getRefundFee(), f.isChangeable(),
					f.getChangeFee(), f.isActive());
		}

	}

	private final FareFamilyService fareFamilies;

	AdminFareFamilyController(FareFamilyService fareFamilies) {
		this.fareFamilies = fareFamilies;
	}

	@GetMapping("/airlines/{code}/fare-families")
	List<FareFamilyResponse> list(@PathVariable String code) {
		return fareFamilies.list(code).stream().map(FareFamilyResponse::of).toList();
	}

	@PostMapping("/airlines/{code}/fare-families")
	@ResponseStatus(HttpStatus.CREATED)
	FareFamilyResponse create(@PathVariable String code, @Valid @RequestBody FareFamilyFields body) {
		return FareFamilyResponse.of(fareFamilies.create(code, body));
	}

	@PutMapping("/fare-families/{id}")
	FareFamilyResponse update(@PathVariable long id, @Valid @RequestBody FareFamilyFields body) {
		return FareFamilyResponse.of(fareFamilies.update(id, body));
	}

	@DeleteMapping("/fare-families/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable long id) {
		fareFamilies.delete(id);
	}

}
