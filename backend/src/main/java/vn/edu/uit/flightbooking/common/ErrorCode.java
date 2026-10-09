package vn.edu.uit.flightbooking.common;

import org.springframework.http.HttpStatus;

/** Mã lỗi trả về trong trường {@code code} của ProblemDetail (TDD §9). */
public enum ErrorCode {

	VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
	PASSENGER_RULES_VIOLATED(HttpStatus.BAD_REQUEST),
	ITINERARY_INVALID(HttpStatus.BAD_REQUEST),
	SETTING_OUT_OF_RANGE(HttpStatus.BAD_REQUEST),
	INVALID_TOKEN(HttpStatus.BAD_REQUEST),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
	ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED),
	FORBIDDEN(HttpStatus.FORBIDDEN),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
	EMAIL_ALREADY_USED(HttpStatus.CONFLICT),
	INVALID_STATE(HttpStatus.CONFLICT),
	FLIGHT_NOT_BOOKABLE(HttpStatus.CONFLICT),
	SEATS_UNAVAILABLE(HttpStatus.CONFLICT),
	HOLD_EXPIRED(HttpStatus.CONFLICT),
	VOUCHER_INVALID(HttpStatus.CONFLICT),
	FARE_RULE_NOT_ALLOWED(HttpStatus.CONFLICT),
	DEADLINE_PASSED(HttpStatus.CONFLICT),
	REQUEST_IN_PROGRESS(HttpStatus.CONFLICT),
	FLIGHT_HAS_BOOKINGS(HttpStatus.CONFLICT),
	SEAT_COUNT_BELOW_SOLD(HttpStatus.CONFLICT),
	RESOURCE_IN_USE(HttpStatus.CONFLICT),
	PAYMENT_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}

}
