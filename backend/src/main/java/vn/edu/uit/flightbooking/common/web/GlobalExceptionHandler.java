package vn.edu.uit.flightbooking.common.web;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/** Chuyển lỗi thành ProblemDetail (RFC 9457) có thêm trường {@code code} (TDD §5.3, §9). */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	ProblemDetail handleBusiness(BusinessException ex) {
		ProblemDetail problem = problem(ex.code(), ex.getMessage());
		ex.properties().forEach(problem::setProperty);
		return problem;
	}

	private static ProblemDetail problem(ErrorCode code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
		problem.setProperty("code", code.name());
		return problem;
	}

}
