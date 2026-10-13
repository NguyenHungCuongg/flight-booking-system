package vn.edu.uit.flightbooking.booking.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/**
 * Gom lỗi theo trường, cùng định dạng {@code errors} với lỗi Bean Validation (TDD §5.3), để C-07 đánh dấu mọi trường
 * sai trong một lần (APP_FLOW C-07).
 */
final class FieldErrors {

	private final List<Map<String, String>> errors = new ArrayList<>();

	void add(String field, String message) {
		errors.add(Map.of("field", field, "message", message));
	}

	void throwIfAny(ErrorCode code, String detail) {
		if (!errors.isEmpty()) {
			throw new BusinessException(code, detail, Map.of("errors", List.copyOf(errors)));
		}
	}

}
