package vn.edu.uit.flightbooking.payment;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.annotation.Primary;
import org.springframework.web.client.ResourceAccessException;

import vn.edu.uit.flightbooking.payment.infra.VnpayApi;

/**
 * Thay Refund API thật trong test: ghi lại body đã gửi và trả mã phản hồi theo kịch bản. Mỗi lần gọi lấy một mã từ
 * {@link #respond}; hết mã thì trả {@code 00}. Mã {@link #TIMEOUT} giả lập lỗi mạng.
 */
@Primary
public class TestVnpayApi extends VnpayApi {

	public static final String TIMEOUT = "TIMEOUT";

	private final Queue<String> codes = new ConcurrentLinkedQueue<>();

	private final List<Map<String, String>> requests = new CopyOnWriteArrayList<>();

	public TestVnpayApi() {
		super("http://vnpay.test/merchant_webapi/api/transaction");
	}

	public void respond(String... responseCodes) {
		codes.clear();
		codes.addAll(List.of(responseCodes));
	}

	public List<Map<String, String>> requests() {
		return requests;
	}

	public void reset() {
		codes.clear();
		requests.clear();
	}

	@Override
	public Map<String, Object> post(Map<String, String> body) {
		requests.add(body);
		String code = codes.isEmpty() ? "00" : codes.poll();
		if (TIMEOUT.equals(code)) {
			throw new ResourceAccessException("timeout");
		}
		return Map.of("vnp_ResponseCode", code, "vnp_Message", "test");
	}

}
