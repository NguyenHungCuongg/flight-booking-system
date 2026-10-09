package vn.edu.uit.flightbooking.common;

/** Tham số nghiệp vụ trong bảng system_settings, kèm khoảng hợp lệ (PRD §7). */
public enum SettingKey {

	BOOKING_HOLD_MINUTES("booking.hold_minutes", 5, 60),
	BOOKING_MIN_HOURS_BEFORE_DEPARTURE("booking.min_hours_before_departure", 0, 72),
	BOOKING_MAX_SEATED_PASSENGERS("booking.max_seated_passengers", 1, 9),
	PRICING_CHILD_PERCENT("pricing.child_percent", 0, 100),
	PRICING_INFANT_PERCENT("pricing.infant_percent", 0, 100),
	AFTERSALES_MIN_HOURS_BEFORE_DEPARTURE("aftersales.min_hours_before_departure", 0, 168),
	SEARCH_MIN_CONNECTION_MINUTES("search.min_connection_minutes", 30, 600),
	SEARCH_MAX_CONNECTION_MINUTES("search.max_connection_minutes", 60, 1440);

	private final String key;

	private final int min;

	private final int max;

	SettingKey(String key, int min, int max) {
		this.key = key;
		this.min = min;
		this.max = max;
	}

	public String key() {
		return key;
	}

	public int min() {
		return min;
	}

	public int max() {
		return max;
	}

}
