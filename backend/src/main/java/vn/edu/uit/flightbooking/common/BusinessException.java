package vn.edu.uit.flightbooking.common;

import java.util.List;
import java.util.Map;

/**
 * Lỗi nghiệp vụ. {@code detail} là câu tiếng Việt hiển thị được cho người dùng;
 * {@code properties} là các trường bổ sung của ProblemDetail (VD {@code reason} của VOUCHER_INVALID).
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode code;

	private final Map<String, Object> properties;

	public BusinessException(ErrorCode code, String detail) {
		this(code, detail, Map.of());
	}

	public BusinessException(ErrorCode code, String detail, Map<String, Object> properties) {
		super(detail);
		this.code = code;
		this.properties = properties;
	}

	/** Lỗi của một trường, cùng định dạng {@code errors} với lỗi Bean Validation để form hiện lỗi đúng chỗ. */
	public static BusinessException invalidField(String field, String message) {
		return new BusinessException(ErrorCode.VALIDATION_FAILED, "Dữ liệu không hợp lệ",
				Map.of("errors", List.of(Map.of("field", field, "message", message))));
	}

	public ErrorCode code() {
		return code;
	}

	public Map<String, Object> properties() {
		return properties;
	}

}
