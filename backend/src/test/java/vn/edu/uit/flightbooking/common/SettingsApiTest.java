package vn.edu.uit.flightbooking.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;

@IntegrationTest
class SettingsApiTest {

	@Autowired
	SettingsApi settings;

	@Autowired
	JdbcClient jdbc;

	@Test
	void readsPrdDefaults() {
		assertThat(settings.getInt(SettingKey.BOOKING_HOLD_MINUTES)).isEqualTo(15);
		assertThat(settings.getInt(SettingKey.BOOKING_MIN_HOURS_BEFORE_DEPARTURE)).isEqualTo(3);
		assertThat(settings.getInt(SettingKey.BOOKING_MAX_SEATED_PASSENGERS)).isEqualTo(9);
		assertThat(settings.getInt(SettingKey.PRICING_CHILD_PERCENT)).isEqualTo(90);
		assertThat(settings.getInt(SettingKey.PRICING_INFANT_PERCENT)).isEqualTo(10);
		assertThat(settings.getInt(SettingKey.AFTERSALES_MIN_HOURS_BEFORE_DEPARTURE)).isEqualTo(24);
		assertThat(settings.getInt(SettingKey.SEARCH_MIN_CONNECTION_MINUTES)).isEqualTo(60);
		assertThat(settings.getInt(SettingKey.SEARCH_MAX_CONNECTION_MINUTES)).isEqualTo(720);
	}

	@Test
	void enumMatchesDatabaseKeysAndDefaultsAreInRange() {
		var dbKeys = jdbc.sql("SELECT key FROM system_settings").query(String.class).set();
		assertThat(dbKeys).containsExactlyInAnyOrderElementsOf(
				Arrays.stream(SettingKey.values()).map(SettingKey::key).toList());
		for (SettingKey key : SettingKey.values()) {
			assertThat(settings.getInt(key)).as(key.key()).isBetween(key.min(), key.max());
		}
	}

	@Test
	@Transactional
	void newValueAppliesToTheNextRead() {
		jdbc.sql("UPDATE system_settings SET value = '20' WHERE key = 'booking.hold_minutes'").update();

		assertThat(settings.getInt(SettingKey.BOOKING_HOLD_MINUTES)).isEqualTo(20);
	}

}
