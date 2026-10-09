package vn.edu.uit.flightbooking.common;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Đọc tham số hệ thống. Module khác gọi {@link #getInt} ngay lúc tạo giao dịch,
 * nên giá trị mới chỉ áp dụng cho giao dịch tạo sau đó (BR-25).
 */
@Service
public class SettingsApi {

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

}
