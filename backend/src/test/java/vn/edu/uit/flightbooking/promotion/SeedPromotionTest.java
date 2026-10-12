package vn.edu.uit.flightbooking.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.Role;

/** Seed chỉ nạp ở profile dev; test chạy file seed trong transaction rồi rollback để kiểm tra nội dung. */
@IntegrationTest
@Transactional
@Sql("classpath:db/seed/V104__seed_vouchers.sql")
class SeedPromotionTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	VoucherApi vouchers;

	boolean sql(String condition) {
		return jdbc.sql("SELECT " + condition).query(Boolean.class).single();
	}

	@Test
	void seedMatchesBackendSchemaSection84() throws Exception {
		var admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();
		mvc.perform(get("/api/admin/vouchers").session(admin))
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[?(@.code == 'WELCOME10')].discountType").value("PERCENT"))
			.andExpect(jsonPath("$[?(@.code == 'WELCOME10')].discountValue").value(10))
			.andExpect(jsonPath("$[?(@.code == 'WELCOME10')].maxDiscount").value(200_000))
			.andExpect(jsonPath("$[?(@.code == 'SALE100K')].discountType").value("FIXED"))
			.andExpect(jsonPath("$[?(@.code == 'SALE100K')].minOrderAmount").value(1_000_000))
			.andExpect(jsonPath("$[?(@.code == 'SALE100K')].usageLimit").value(100));

		// WELCOME10: không giới hạn lượt, hiệu lực 1 năm từ ngày seed. SALE100K: 3 tháng.
		assertThat(sql("usage_limit IS NULL AND valid_from <= now() AND valid_to >= now() + interval '364 days' "
				+ "FROM vouchers WHERE code = 'WELCOME10'")).isTrue();
		assertThat(sql("valid_from <= now() AND valid_to BETWEEN now() + interval '88 days' "
				+ "AND now() + interval '93 days' FROM vouchers WHERE code = 'SALE100K'")).isTrue();
	}

	/** Số liệu của ví dụ TDD §8.8, tiền đề cho Plan 10. */
	@Test
	void seededVouchersApplyAsInTddExample() {
		assertThat(vouchers.check("WELCOME10", 6_048_000).discount()).isEqualTo(200_000);
		assertThat(vouchers.check("SALE100K", 1_000_000).discount()).isEqualTo(100_000);
		assertThatThrownBy(() -> vouchers.check("SALE100K", 999_999)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.properties()).containsEntry("reason", "MIN_ORDER_NOT_MET"));
	}

}
