package vn.edu.uit.flightbooking.promotion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;
import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.promotion.TestVouchers;

/** FR-100. Voucher có sẵn tạo bằng SQL (mã QV…), vì bản ghi tạo qua API chưa flush xuống DB trong transaction của test. */
@IntegrationTest
@Transactional
class AdminVoucherControllerTest {

	static final String FROM = "2026-01-01T00:00:00+07:00";

	static final String TO = "2036-01-01T00:00:00+07:00";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	MockHttpSession admin;

	@BeforeEach
	void logIn() throws Exception {
		admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();
	}

	/** Body mặc định: QVADMIN giảm 10% tối đa 200.000, hiệu lực {@link #FROM}–{@link #TO}, 100 lượt. */
	static Map<String, Object> defaults() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("code", "QVADMIN");
		body.put("description", "Giảm 10% tối đa 200.000 đ");
		body.put("discountType", "PERCENT");
		body.put("discountValue", 10);
		body.put("maxDiscount", 200_000);
		body.put("minOrderAmount", 0);
		body.put("validFrom", FROM);
		body.put("validTo", TO);
		body.put("usageLimit", 100);
		body.put("active", true);
		return body;
	}

	/** Body mặc định, ghi đè theo cặp (tên trường, giá trị); giá trị {@code null} là gửi {@code null}. */
	static String json(Object... keysAndValues) {
		Map<String, Object> body = defaults();
		for (int i = 0; i < keysAndValues.length; i += 2) {
			body.put((String) keysAndValues[i], keysAndValues[i + 1]);
		}
		return JsonMapper.shared().writeValueAsString(body);
	}

	static String jsonWithout(String field) {
		Map<String, Object> body = defaults();
		body.remove(field);
		return JsonMapper.shared().writeValueAsString(body);
	}

	ResultActions create(String json) throws Exception {
		return mvc.perform(post("/api/admin/vouchers").with(csrf(mvc)).session(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	ResultActions update(long id, String json) throws Exception {
		return mvc.perform(put("/api/admin/vouchers/{id}", id).with(csrf(mvc)).session(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	@Test
	void adminCreatesUpdatesAndDeactivatesVouchers() throws Exception {
		String created = create(json()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("QVADMIN"))
			.andExpect(jsonPath("$.discountType").value("PERCENT"))
			.andExpect(jsonPath("$.maxDiscount").value(200_000))
			.andExpect(jsonPath("$.usedCount").value(0))
			.andExpect(jsonPath("$.validFrom").value(FROM))
			.andExpect(jsonPath("$.active").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long id = ((Number) JsonPath.read(created, "$.id")).longValue();

		update(id, json("discountType", "FIXED", "discountValue", 100_000, "maxDiscount", null, "usageLimit", null,
				"active", false))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.discountType").value("FIXED"))
			.andExpect(jsonPath("$.discountValue").value(100_000))
			.andExpect(jsonPath("$.maxDiscount").value(nullValue()))
			.andExpect(jsonPath("$.usageLimit").value(nullValue()))
			.andExpect(jsonPath("$.active").value(false));

		mvc.perform(get("/api/admin/vouchers").session(admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.code == 'QVADMIN')].active").value(false));
	}

	@Test
	void listsNewestFirstWithUsedCount() throws Exception {
		TestVouchers.insert(jdbc, "QVOLD", Map.of());
		TestVouchers.insert(jdbc, "QVNEW", Map.of("used_count", 2));

		mvc.perform(get("/api/admin/vouchers").session(admin))
			.andExpect(jsonPath("$[*].code", contains("QVNEW", "QVOLD")))
			.andExpect(jsonPath("$[?(@.code == 'QVNEW')].usedCount").value(2));
	}

	@Test
	void codeIsUniqueAndCannotChange() throws Exception {
		String created = create(json()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(created, "$.id")).longValue();

		create(json()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("code"));
		create(json("code", "QVOTHER")).andExpect(status().isCreated());

		// FR-100: không sửa được mã, kể cả đổi sang mã chưa có.
		update(id, json("code", "QVOTHER")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("code"));
		update(id, json("code", "QVRENAMED")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("code"));
		update(id, json("description", "Đổi mô tả")).andExpect(status().isOk());
	}

	/** Mỗi tổ hợp sau sẽ vi phạm một CHECK của bảng vouchers; phải báo 400 theo trường, không phải 500. */
	@Test
	void rejectsValuesThatBreakDatabaseChecks() throws Exception {
		create(json("discountValue", 150)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("discountValue"));
		// FIXED nhưng vẫn còn maxDiscount mặc định 200.000.
		create(json("discountType", "FIXED", "discountValue", 100_000)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("maxDiscount"));
		create(json("validTo", FROM)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("validTo"));
		create(json("validFrom", TO, "validTo", FROM)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("validTo"));
	}

	@Test
	void rejectsMissingAndMalformedFields() throws Exception {
		create(jsonWithout("minOrderAmount")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("minOrderAmount"));
		create(json("discountValue", 0)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("discountValue"));
		create(json("minOrderAmount", -1)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("minOrderAmount"));
		create(json("usageLimit", 0)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("usageLimit"));
		create(json("code", "qvlower")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("code"));
		create(json("discountType", "PERCENTAGE")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void usageLimitCannotDropBelowUsedCount() throws Exception {
		long id = TestVouchers.insert(jdbc, "QVUSED", Map.of("usage_limit", 10, "used_count", 5));

		update(id, json("code", "QVUSED", "usageLimit", 4)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("usageLimit"));
		update(id, json("code", "QVUSED", "usageLimit", 5)).andExpect(status().isOk())
			.andExpect(jsonPath("$.usedCount").value(5));
		update(id, json("code", "QVUSED", "usageLimit", null)).andExpect(status().isOk());
	}

	/**
	 * Admin sửa trong lúc Q-14 giữ thêm một lượt ở request khác: bản sửa không được ghi đè {@code used_count}. Hibernate
	 * chỉ gửi UPDATE khi flush, nên SQL tăng lượt chạy trước UPDATE của bản sửa; nếu {@code used_count} có trong câu
	 * UPDATE đó thì lượt vừa tăng bị ghi đè về 3.
	 */
	@Test
	void updateNeverOverwritesUsedCount() throws Exception {
		long id = TestVouchers.insert(jdbc, "QVBUMP", Map.of("usage_limit", 10, "used_count", 3));
		update(id, json("code", "QVBUMP", "description", "Đã sửa")).andExpect(status().isOk());

		jdbc.sql("UPDATE vouchers SET used_count = 4 WHERE id = ?").param(id).update();
		mvc.perform(get("/api/admin/vouchers").session(admin)).andExpect(status().isOk()); // truy vấn JPQL buộc flush bản sửa

		assertThat(TestVouchers.usedCount(jdbc, id)).isEqualTo(4);
		assertThat(jdbc.sql("SELECT description FROM vouchers WHERE id = ?")
			.param(id)
			.query(String.class)
			.single()).isEqualTo("Đã sửa");
	}

	@Test
	void unknownVoucherIsNotFound() throws Exception {
		update(999_999, json()).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void endpointsRequireAdmin() throws Exception {
		MockHttpSession staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF).session();

		mvc.perform(get("/api/admin/vouchers")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/admin/vouchers").session(staff)).andExpect(status().isForbidden());
		mvc.perform(post("/api/admin/vouchers").with(csrf(mvc)).session(staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json())).andExpect(status().isForbidden());
	}

}
