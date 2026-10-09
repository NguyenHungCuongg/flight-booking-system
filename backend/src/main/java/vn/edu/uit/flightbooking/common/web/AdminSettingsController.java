package vn.edu.uit.flightbooking.common.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.common.CurrentUser;
import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;

/** Màn hình A-09 (FR-111, TDD §6.9). */
@RestController
@RequestMapping("/api/admin/settings")
class AdminSettingsController {

	/** Thiếu {@code value} thì Jackson 3 báo lỗi (FAIL_ON_NULL_FOR_PRIMITIVES bật sẵn), không hiểu thành 0. */
	record UpdateSettingRequest(int value) {
	}

	private final SettingsApi settings;

	AdminSettingsController(SettingsApi settings) {
		this.settings = settings;
	}

	@GetMapping
	List<SettingsApi.Setting> list() {
		return settings.list();
	}

	@PutMapping("/{key}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void update(@PathVariable String key, @RequestBody UpdateSettingRequest body,
			@AuthenticationPrincipal CurrentUser me) {
		settings.update(SettingKey.of(key), body.value(), me.id());
	}

}
