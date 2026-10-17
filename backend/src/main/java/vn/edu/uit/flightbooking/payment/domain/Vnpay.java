package vn.edu.uit.flightbooking.payment.domain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tạo URL thanh toán và kiểm chữ ký VNPay 2.1.0 (TDD §6.3), theo code mẫu của VNPay: ký các tham số {@code vnp_*}
 * khác rỗng (trừ {@code vnp_SecureHash}, {@code vnp_SecureHashType}), sắp theo tên, nối {@code key=value} bằng
 * {@code &} với key và value URL-encode US-ASCII, rồi HMAC-SHA512 bằng hash secret.
 */
@Component
public class Vnpay {

	/** {@code vnp_CreateDate}, {@code vnp_ExpireDate}: yyyyMMddHHmmss theo GMT+7. */
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
		.withZone(ZoneOffset.ofHours(7));

	private final String tmnCode;

	private final String hashSecret;

	private final String payUrl;

	private final String returnUrl;

	public Vnpay(@Value("${app.vnpay.tmn-code}") String tmnCode, @Value("${app.vnpay.hash-secret}") String hashSecret,
			@Value("${app.vnpay.pay-url}") String payUrl, @Value("${app.vnpay.return-url}") String returnUrl) {
		this.tmnCode = tmnCode;
		this.hashSecret = hashSecret;
		this.payUrl = payUrl;
		this.returnUrl = returnUrl;
	}

	/** FR-30: {@code amount} tính bằng đồng; link hết hạn lúc {@code expiresAt} (BR-41). {@code orderInfo} không dấu. */
	public String payUrl(String txnRef, long amount, String orderInfo, String clientIp, Instant createdAt,
			Instant expiresAt) {
		Map<String, String> params = new TreeMap<>();
		params.put("vnp_Version", "2.1.0");
		params.put("vnp_Command", "pay");
		params.put("vnp_TmnCode", tmnCode);
		params.put("vnp_Amount", String.valueOf(amount * 100));
		params.put("vnp_CurrCode", "VND");
		params.put("vnp_TxnRef", txnRef);
		params.put("vnp_OrderInfo", orderInfo);
		params.put("vnp_OrderType", "other");
		params.put("vnp_Locale", "vn");
		params.put("vnp_ReturnUrl", returnUrl);
		params.put("vnp_IpAddr", clientIp);
		params.put("vnp_CreateDate", TIME.format(createdAt));
		params.put("vnp_ExpireDate", TIME.format(expiresAt));
		return payUrl + "?" + query(params) + "&vnp_SecureHash=" + hash(params);
	}

	/** Bước 1 của TDD §6.3: so chữ ký không phân biệt hoa thường, thời gian so sánh không phụ thuộc nội dung. */
	public boolean verify(Map<String, String> params) {
		String received = params.get("vnp_SecureHash");
		return received != null && MessageDigest.isEqual(hash(params).getBytes(StandardCharsets.US_ASCII),
				received.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
	}

	/** Chữ ký hex chữ thường của các tham số được ký trong {@code params}; tham số khác bị bỏ qua. */
	public String hash(Map<String, String> params) {
		Map<String, String> signed = new TreeMap<>();
		params.forEach((key, value) -> {
			if (key.startsWith("vnp_") && !key.equals("vnp_SecureHash") && !key.equals("vnp_SecureHashType")
					&& value != null && !value.isEmpty()) {
				signed.put(key, value);
			}
		});
		return hmac(query(signed));
	}

	/** Một yêu cầu Refund API (TDD §6.6). {@code amount} tính bằng đồng; {@code transactionDate} là {@code vnp_PayDate} của giao dịch gốc. */
	public record RefundCall(String requestId, String transactionType, String txnRef, long amount,
			String transactionNo, String transactionDate, String createBy, Instant createdAt, String ipAddr,
			String orderInfo) {
	}

	/** Body JSON của Refund API: 13 trường theo thứ tự ký, rồi {@code vnp_SecureHash}; chữ ký nối giá trị bằng {@code |}. */
	public Map<String, String> refundBody(RefundCall call) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("vnp_RequestId", call.requestId());
		body.put("vnp_Version", "2.1.0");
		body.put("vnp_Command", "refund");
		body.put("vnp_TmnCode", tmnCode);
		body.put("vnp_TransactionType", call.transactionType());
		body.put("vnp_TxnRef", call.txnRef());
		body.put("vnp_Amount", String.valueOf(call.amount() * 100));
		body.put("vnp_TransactionNo", call.transactionNo());
		body.put("vnp_TransactionDate", call.transactionDate());
		body.put("vnp_CreateBy", call.createBy());
		body.put("vnp_CreateDate", TIME.format(call.createdAt()));
		body.put("vnp_IpAddr", call.ipAddr());
		body.put("vnp_OrderInfo", call.orderInfo());
		body.put("vnp_SecureHash", hmac(String.join("|", body.values())));
		return body;
	}

	private String hmac(String data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA512");
			mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
			return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.US_ASCII)));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String query(Map<String, String> sorted) {
		return sorted.entrySet()
			.stream()
			.map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
			.collect(Collectors.joining("&"));
	}

	private static String encode(String text) {
		return URLEncoder.encode(text, StandardCharsets.US_ASCII);
	}

}
