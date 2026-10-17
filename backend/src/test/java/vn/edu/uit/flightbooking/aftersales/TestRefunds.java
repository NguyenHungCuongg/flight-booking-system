package vn.edu.uit.flightbooking.aftersales;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.payment.TestVnpay;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;

/** Dữ liệu và lời gọi dùng chung cho test hoàn vé (Plan 14). */
public final class TestRefunds {

	/** Phí hoàn mỗi khách, mỗi chiều, của gói Eco trong {@link #refundable}. */
	public static final long REFUND_FEE = 300_000;

	private TestRefunds() {
	}

	/** Cho gói Eco của hãng Q1 được hoàn, phí {@link #REFUND_FEE}. Gọi trước khi giữ chỗ: quy định được chụp lúc giữ. */
	public static void refundable(JdbcClient jdbc, TestBookings.Trip trip) {
		jdbc.sql("UPDATE fare_families SET refundable = TRUE, refund_fee = ? WHERE id = ?")
			.params(REFUND_FEE, trip.catalog().economyFare())
			.update();
	}

	/** Giữ chỗ, tạo lượt thu và giả lập IPN thành công đúng số tiền; trả mã đặt chỗ đã xuất vé. */
	public static String issued(MockMvc mvc, JdbcClient jdbc, Vnpay vnpay, TestUsers.TestUser customer, String body)
			throws Exception {
		String code = TestBookings.holdCode(mvc, customer.session(), body);
		String txnRef = TestVnpay.start(mvc, customer.session(), code);
		long total = jdbc.sql("SELECT total_amount FROM bookings WHERE code = ?")
			.param(code)
			.query(Long.class)
			.single();
		mvc.perform(get("/api/payments/vnpay/ipn").params(TestVnpay.callback(vnpay, txnRef, total, "00")))
			.andExpect(status().isOk());
		return code;
	}

	public static ResultActions quote(MockMvc mvc, MockHttpSession customer, String code, String direction)
			throws Exception {
		return mvc.perform(get("/api/bookings/{code}/journeys/{direction}/refund-quote", code, direction)
			.session(customer));
	}

	public static ResultActions request(MockMvc mvc, MockHttpSession customer, String code, String direction,
			String reason) throws Exception {
		return mvc.perform(post("/api/bookings/{code}/journeys/{direction}/refund-requests", code, direction)
			.with(csrf(mvc))
			.session(customer)
			.contentType(MediaType.APPLICATION_JSON)
			.content(JsonMapper.shared().writeValueAsString(Map.of("reason", reason))));
	}

	/** Gửi yêu cầu hoàn (phải trả 201), trả id yêu cầu. */
	public static long requestId(MockMvc mvc, MockHttpSession customer, String code, String direction, String reason)
			throws Exception {
		String json = request(mvc, customer, code, direction, reason).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

}
