package vn.edu.uit.flightbooking.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import com.jayway.jsonpath.JsonPath;

import vn.edu.uit.flightbooking.payment.domain.Vnpay;

/** Cấu hình VNPay của test (đặt ở {@code @IntegrationTest}) và các hàm giả lập phía VNPay. */
public final class TestVnpay {

	public static final String TMN_CODE = "TESTTMN1";

	public static final String SECRET = "TESTSECRETTESTSECRETTESTSECRET12";

	private TestVnpay() {
	}

	/** Tham số trên URL thanh toán, đã giải mã ({@code +} thành khoảng trắng). */
	public static Map<String, String> query(String url) {
		Map<String, String> params = new TreeMap<>();
		UriComponentsBuilder.fromUriString(url)
			.build()
			.getQueryParams()
			.forEach((key, values) -> params.put(key, URLDecoder.decode(values.getFirst(), StandardCharsets.UTF_8)));
		return params;
	}

	/** POST /api/bookings/{code}/payments (phải trả 201), trả URL thanh toán. */
	public static String paymentUrl(MockMvc mvc, MockHttpSession customer, String code) throws Exception {
		String json = mvc.perform(post("/api/bookings/{code}/payments", code).with(csrf(mvc)).session(customer))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(json, "$.paymentUrl");
	}

	/** Tạo một lượt thu, trả {@code vnp_TxnRef} của lượt đó. */
	public static String start(MockMvc mvc, MockHttpSession customer, String code) throws Exception {
		return query(paymentUrl(mvc, customer, code)).get("vnp_TxnRef");
	}

	/**
	 * Tham số VNPay gửi về IPN và Return URL, ký bằng secret của test. {@code responseCode} {@code "00"} đi kèm
	 * {@code vnp_TransactionStatus = 00} (thành công); mã khác (VD {@code "24"}: khách huỷ) kèm {@code "02"}.
	 * {@code amount} tính bằng đồng, hàm tự nhân 100.
	 */
	public static LinkedMultiValueMap<String, String> callback(Vnpay vnpay, String txnRef, long amount,
			String responseCode) {
		Map<String, String> params = new TreeMap<>();
		params.put("vnp_Amount", String.valueOf(amount * 100));
		params.put("vnp_BankCode", "NCB");
		params.put("vnp_CardType", "ATM");
		params.put("vnp_OrderInfo", "Thanh toan dat ve");
		params.put("vnp_PayDate", "20261201081000");
		params.put("vnp_ResponseCode", responseCode);
		params.put("vnp_TmnCode", TMN_CODE);
		params.put("vnp_TransactionNo", "14000001");
		params.put("vnp_TransactionStatus", "00".equals(responseCode) ? "00" : "02");
		params.put("vnp_TxnRef", txnRef);
		params.put("vnp_SecureHashType", "HmacSHA512");
		params.put("vnp_SecureHash", vnpay.hash(params));
		LinkedMultiValueMap<String, String> result = new LinkedMultiValueMap<>();
		params.forEach(result::add);
		return result;
	}

}
