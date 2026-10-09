package vn.edu.uit.flightbooking.common;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đọc tham số hệ thống. Module khác gọi {@link #getInt} ngay lúc tạo giao dịch,
 * nên giá trị mới chỉ áp dụng cho giao dịch tạo sau đó (BR-25).
 */
@Service
public class SettingsApi {

	/** Một dòng của màn hình A-09; {@code updatedByName} là null khi chưa ai sửa. */
	public record Setting(String key, int value, String description, int min, int max,
			Instant updatedAt, String updatedByName) {
	}

	private final JdbcClient jdbc;

	SettingsApi(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	// ponytail: đọc DB mỗi lần (1 lần tra khoá chính, ~1 ms), không cache nên không bao giờ cũ;
	// thêm cache trong bộ nhớ (TDD §6.9) nếu đo thấy chậm.
	public int getInt(SettingKey key) {
		return jdbc.sql("SELECT value FROM system_settings WHERE key = ?")
			.param(key.key())
			.query(Integer.class)
			.single();
	}

	/** FR-111. Chỉ đọc tên người sửa từ bảng users, không ghi. */
	public List<Setting> list() {
		return jdbc.sql("""
				SELECT s.key, s.value, s.description, s.updated_at, u.full_name
				FROM system_settings s LEFT JOIN users u ON u.id = s.updated_by
				ORDER BY s.key
				""")
			.query((rs, rowNum) -> {
				SettingKey key = SettingKey.of(rs.getString("key"));
				return new Setting(key.key(), rs.getInt("value"), rs.getString("description"), key.min(), key.max(),
						rs.getTimestamp("updated_at").toInstant(), rs.getString("full_name"));
			})
			.list();
	}

	/** FR-111: kiểm tra khoảng hợp lệ (PRD §7), ghi người sửa và thời điểm sửa. */
	@Transactional
	public void update(SettingKey key, int value, long updatedBy) {
		if (value < key.min() || value > key.max()) {
			throw new BusinessException(ErrorCode.SETTING_OUT_OF_RANGE,
					"Giá trị phải nằm trong khoảng %d–%d".formatted(key.min(), key.max()));
		}
		if (key == SettingKey.SEARCH_MIN_CONNECTION_MINUTES || key == SettingKey.SEARCH_MAX_CONNECTION_MINUTES) {
			// Luôn khoá dòng min rồi tới dòng max, để hai Admin sửa cùng lúc không phá điều kiện min < max.
			int min = lockedValue(SettingKey.SEARCH_MIN_CONNECTION_MINUTES);
			int max = lockedValue(SettingKey.SEARCH_MAX_CONNECTION_MINUTES);
			if (key == SettingKey.SEARCH_MIN_CONNECTION_MINUTES) {
				min = value;
			}
			else {
				max = value;
			}
			if (min >= max) {
				throw new BusinessException(ErrorCode.SETTING_OUT_OF_RANGE,
						"Thời gian nối chuyến tối thiểu phải nhỏ hơn thời gian tối đa");
			}
		}
		jdbc.sql("UPDATE system_settings SET value = ?, updated_at = now(), updated_by = ? WHERE key = ?")
			.params(String.valueOf(value), updatedBy, key.key())
			.update();
	}

	private int lockedValue(SettingKey key) {
		return jdbc.sql("SELECT value FROM system_settings WHERE key = ? FOR UPDATE")
			.param(key.key())
			.query(Integer.class)
			.single();
	}

}
