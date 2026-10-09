package vn.edu.uit.flightbooking.common.web;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/**
 * Endpoint chỉ có trong test để kiểm tra định dạng lỗi. Là lớp top-level trong src/test
 * nên được component scan ở mọi integration test, không tạo thêm Spring context.
 */
@RestController
class TestProbeController {

	@GetMapping("/api/test/business-error")
	void businessError() {
		throw new BusinessException(ErrorCode.SEATS_UNAVAILABLE, "Chuyến VN1825 hạng ECONOMY không còn đủ 3 ghế");
	}

	@GetMapping("/api/test/voucher-error")
	void voucherError() {
		throw new BusinessException(ErrorCode.VOUCHER_INVALID, "Voucher đã hết hạn", Map.of("reason", "EXPIRED"));
	}

	@PostMapping("/api/test/validate")
	void validate(@Valid @RequestBody PassengerName body) {
	}

	@GetMapping("/api/test/unexpected-error")
	void unexpectedError() {
		throw new IllegalStateException("chi tiết nội bộ không được lộ ra");
	}

	@GetMapping("/api/test/typed")
	int typed(@RequestParam int page) {
		return page;
	}

	record PassengerName(@NotBlank String lastName) {
	}

}
