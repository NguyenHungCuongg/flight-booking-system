package vn.edu.uit.flightbooking.promotion;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Dữ liệu dùng chung cho test của module promotion. Voucher tạo bằng SQL: bản ghi tạo qua API chưa flush xuống DB
 * trong transaction của test nên SQL không thấy, và entity đã nạp cũng không thấy SQL cập nhật (Plan 09).
 */
public final class TestVouchers {

	private TestVouchers() {
	}

	/**
	 * Voucher {@code code}: giảm 10%, không tối đa, không đơn tối thiểu, không giới hạn lượt, đang kích hoạt, hiệu
	 * lực từ hôm qua đến 30 ngày sau. {@code changes} ghi đè hoặc thêm cột (VD {@code "used_count"}).
	 */
	public static long insert(JdbcClient jdbc, String code, Map<String, Object> changes) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("code", code);
		row.put("description", "Voucher thử");
		row.put("discount_type", "PERCENT");
		row.put("discount_value", 10);
		row.put("min_order_amount", 0);
		row.put("valid_from", OffsetDateTime.now().minusDays(1));
		row.put("valid_to", OffsetDateTime.now().plusDays(30));
		row.putAll(changes);
		String marks = String.join(", ", Collections.nCopies(row.size(), "?"));
		return jdbc.sql("INSERT INTO vouchers (" + String.join(", ", row.keySet()) + ") VALUES (" + marks
				+ ") RETURNING id")
			.params(new ArrayList<>(row.values()))
			.query(Long.class)
			.single();
	}

	public static int usedCount(JdbcClient jdbc, long id) {
		return jdbc.sql("SELECT used_count FROM vouchers WHERE id = ?").param(id).query(Integer.class).single();
	}

}
