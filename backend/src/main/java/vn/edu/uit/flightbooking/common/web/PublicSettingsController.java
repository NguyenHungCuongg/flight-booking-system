package vn.edu.uit.flightbooking.common.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;

/** Tham số khách cần biết, để trang chính sách và trợ lý đặt vé ghi đúng số đang áp dụng. Công khai, chỉ đọc. */
@RestController
@RequestMapping("/api/settings")
class PublicSettingsController {

	/**
	 * Viết tay thay vì lấy {@code SettingKey.values()}: tham số nội bộ thêm sau này không tự lộ ra ngoài.
	 * Hiện cả 8 tham số của PRD §7 đều là quy tắc khách gặp phải.
	 */
	private static final List<SettingKey> PUBLIC = List.of(SettingKey.BOOKING_HOLD_MINUTES,
			SettingKey.BOOKING_MIN_HOURS_BEFORE_DEPARTURE, SettingKey.BOOKING_MAX_SEATED_PASSENGERS,
			SettingKey.PRICING_CHILD_PERCENT, SettingKey.PRICING_INFANT_PERCENT,
			SettingKey.AFTERSALES_MIN_HOURS_BEFORE_DEPARTURE, SettingKey.SEARCH_MIN_CONNECTION_MINUTES,
			SettingKey.SEARCH_MAX_CONNECTION_MINUTES);

	private final SettingsApi settings;

	PublicSettingsController(SettingsApi settings) {
		this.settings = settings;
	}

	@GetMapping("/public")
	Map<String, Integer> publicSettings() {
		Map<String, Integer> values = new LinkedHashMap<>();
		PUBLIC.forEach(key -> values.put(key.key(), settings.getInt(key)));
		return values;
	}

}
