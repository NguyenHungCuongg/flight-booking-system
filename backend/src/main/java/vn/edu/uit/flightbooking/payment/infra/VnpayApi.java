package vn.edu.uit.flightbooking.payment.infra;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Gửi yêu cầu tới Refund API của VNPay (TDD §6.6). Lỗi mạng, timeout hay HTTP khác 2xx thì ném {@code RestClientException}. */
@Component
public class VnpayApi {

	private final String apiUrl;

	private final RestClient client;

	public VnpayApi(@Value("${app.vnpay.api-url}") String apiUrl) {
		this.apiUrl = apiUrl;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(5));
		factory.setReadTimeout(Duration.ofSeconds(30));
		this.client = RestClient.builder().requestFactory(factory).build();
	}

	public Map<String, Object> post(Map<String, String> body) {
		return client.post()
			.uri(apiUrl)
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.body(new ParameterizedTypeReference<Map<String, Object>>() {
			});
	}

}
