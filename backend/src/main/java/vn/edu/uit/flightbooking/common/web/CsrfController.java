package vn.edu.uit.flightbooking.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Frontend gọi API này khi khởi động, sau đăng nhập và sau đăng xuất (TDD §4.2).
 * Với csrf.spa(), CsrfFilter tự đặt cookie XSRF-TOKEN ở mọi request còn thiếu cookie;
 * frontend gửi lại nguyên giá trị cookie trong header X-XSRF-TOKEN.
 */
@RestController
class CsrfController {

	@GetMapping("/api/auth/csrf")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void csrf() {
	}

}
