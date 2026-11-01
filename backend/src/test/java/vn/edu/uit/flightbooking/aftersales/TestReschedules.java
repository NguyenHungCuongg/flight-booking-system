package vn.edu.uit.flightbooking.aftersales;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.payment.TestVnpay;

/** Dữ liệu và lời gọi dùng chung cho test đổi chuyến (Plan 15). */
public final class TestReschedules {

	/** Phí đổi mỗi khách, mỗi chiều, của gói Eco trong {@link #changeFee}. */
	public static final long CHANGE_FEE = 200_000;

	private TestReschedules() {
	}

	/** Cho gói Eco của hãng Q1 được đổi, phí {@link #CHANGE_FEE}. Gọi trước khi giữ chỗ: quy định được chụp lúc giữ. */
	public static void changeFee(JdbcClient jdbc, TestBookings.Trip trip) {
		jdbc.sql("UPDATE fare_families SET changeable = TRUE, change_fee = ? WHERE id = ?")
			.params(CHANGE_FEE, trip.catalog().economyFare())
			.update();
	}

	public static ResultActions options(MockMvc mvc, MockHttpSession customer, String code, String direction,
			LocalDate date) throws Exception {
		return mvc.perform(get("/api/bookings/{code}/journeys/{direction}/reschedule-options", code, direction)
			.param("date", date.toString())
			.session(customer));
	}

	public static ResultActions quote(MockMvc mvc, MockHttpSession customer, String code, String direction,
			long... flightIds) throws Exception {
		return mvc.perform(post("/api/bookings/{code}/journeys/{direction}/reschedule-quote", code, direction)
			.with(csrf(mvc))
			.session(customer)
			.contentType(MediaType.APPLICATION_JSON)
			.content(JsonMapper.shared().writeValueAsString(Map.of("flightIds", Arrays.stream(flightIds).boxed().toList()))));
	}

	/** Xác nhận đổi chiều {@code direction} sang hành trình mới. */
	public static ResultActions confirm(MockMvc mvc, MockHttpSession customer, String code, String direction,
			long... flightIds) throws Exception {
		return mvc.perform(post("/api/bookings/{code}/journeys/{direction}/reschedules", code, direction)
			.with(csrf(mvc))
			.session(customer)
			.contentType(MediaType.APPLICATION_JSON)
			.content(JsonMapper.shared().writeValueAsString(Map.of("flightIds", Arrays.stream(flightIds).boxed().toList()))));
	}

	/** Xác nhận đổi (phải trả 201), trả id yêu cầu. */
	public static long confirmId(MockMvc mvc, MockHttpSession customer, String code, String direction,
			long... flightIds) throws Exception {
		String json = confirm(mvc, customer, code, direction, flightIds).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

	public static ResultActions pay(MockMvc mvc, MockHttpSession customer, long id) throws Exception {
		return mvc.perform(post("/api/reschedules/{id}/payments", id).with(csrf(mvc)).session(customer));
	}

	/** Tạo lượt thu (phải trả 201), trả {@code vnp_TxnRef} của lượt đó. */
	public static String payTxnRef(MockMvc mvc, MockHttpSession customer, long id) throws Exception {
		String json = pay(mvc, customer, id).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return TestVnpay.query(JsonPath.<String>read(json, "$.paymentUrl")).get("vnp_TxnRef");
	}

}
