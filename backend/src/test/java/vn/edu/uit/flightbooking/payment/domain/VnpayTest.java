package vn.edu.uit.flightbooking.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import vn.edu.uit.flightbooking.payment.TestVnpay;

/**
 * Chữ ký VNPay 2.1.0 (TDD §6.3). {@link #HASH} được tính độc lập bằng
 * {@code printf '%s' "$QUERY" | openssl dgst -sha512 -hmac "$SECRET"}, nên test ghim đúng thuật toán của VNPay chứ
 * không chỉ so code với chính nó.
 */
class VnpayTest {

	static final Vnpay VNPAY = new Vnpay(TestVnpay.TMN_CODE, TestVnpay.SECRET,
			"https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", "http://localhost:3000/api/payments/vnpay/return");

	static final String QUERY = "vnp_Amount=302000000&vnp_Command=pay&vnp_CreateDate=20261201080000"
			+ "&vnp_CurrCode=VND&vnp_ExpireDate=20261201081500&vnp_IpAddr=127.0.0.1&vnp_Locale=vn"
			+ "&vnp_OrderInfo=Thanh+toan+dat+ve+K7Q2XM&vnp_OrderType=other"
			+ "&vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A3000%2Fapi%2Fpayments%2Fvnpay%2Freturn&vnp_TmnCode=TESTTMN1"
			+ "&vnp_TxnRef=0123456789abcdef0123456789abcdef&vnp_Version=2.1.0";

	static final String HASH = "4c4b04c7b5d5b05d14c194170cb123823948ac9f27361f44a083eb1d73a1bd4d"
			+ "a767a3b399f82ed857fc7e05c8d8df76249382a8326ce4e5264fa9648b77a7f0";

	/** 08:00 và 08:15 giờ Việt Nam; số tiền nhân 100; khoảng trắng thành {@code +}, ký tự đặc biệt thành {@code %XX}. */
	@Test
	void buildsSignedPaymentUrl() {
		String url = VNPAY.payUrl("0123456789abcdef0123456789abcdef", 3_020_000, "Thanh toan dat ve K7Q2XM",
				"127.0.0.1", Instant.parse("2026-12-01T01:00:00Z"), Instant.parse("2026-12-01T01:15:00Z"));

		assertThat(url)
			.isEqualTo("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?" + QUERY + "&vnp_SecureHash=" + HASH);
	}

	/** Chữ ký chữ hoa, {@code vnp_SecureHashType} và tham số rỗng không ảnh hưởng; sửa một tham số thì sai chữ ký. */
	@Test
	void verifiesCallbackSignature() {
		Map<String, String> params = new HashMap<>(Map.ofEntries(Map.entry("vnp_Amount", "302000000"),
				Map.entry("vnp_Command", "pay"), Map.entry("vnp_CreateDate", "20261201080000"),
				Map.entry("vnp_CurrCode", "VND"), Map.entry("vnp_ExpireDate", "20261201081500"),
				Map.entry("vnp_IpAddr", "127.0.0.1"), Map.entry("vnp_Locale", "vn"),
				Map.entry("vnp_OrderInfo", "Thanh toan dat ve K7Q2XM"), Map.entry("vnp_OrderType", "other"),
				Map.entry("vnp_ReturnUrl", "http://localhost:3000/api/payments/vnpay/return"),
				Map.entry("vnp_TmnCode", "TESTTMN1"), Map.entry("vnp_TxnRef", "0123456789abcdef0123456789abcdef"),
				Map.entry("vnp_Version", "2.1.0")));
		params.put("vnp_SecureHash", HASH);
		assertThat(VNPAY.verify(params)).isTrue();

		params.put("vnp_SecureHash", HASH.toUpperCase(Locale.ROOT));
		params.put("vnp_SecureHashType", "HmacSHA512");
		params.put("vnp_CardType", "");
		assertThat(VNPAY.verify(params)).isTrue();

		params.put("vnp_Amount", "302000100");
		assertThat(VNPAY.verify(params)).isFalse();
		params.put("vnp_Amount", "302000000");
		params.remove("vnp_SecureHash");
		assertThat(VNPAY.verify(params)).isFalse();
	}

	/** TDD §6.6: tính độc lập bằng {@code printf '%s' "$DATA" | openssl dgst -sha512 -hmac "$SECRET"}. */
	static final String REFUND_HASH = "b1c65f81a6c85dc7750f04d5c47ff1f7a03719e7265b5f3eb1915a07684ff051"
			+ "94f68f053939ef0abde4b472c9ad3dde893eb17093435286f3dc4bb32e07886d";

	/** 09:00 giờ Việt Nam; số tiền nhân 100; chữ ký nối các giá trị bằng {@code |}, không URL-encode. */
	@Test
	void buildsSignedRefundBody() {
		Map<String, String> body = VNPAY.refundBody(new Vnpay.RefundCall("fedcba9876543210fedcba9876543210", "03",
				"0123456789abcdef0123456789abcdef", 2_720_000, "14000001", "20261201081000", "staff@test.local",
				Instant.parse("2026-12-10T02:00:00Z"), "127.0.0.1", "Hoan tien yeu cau 42"));

		assertThat(body).containsExactly(entry("vnp_RequestId", "fedcba9876543210fedcba9876543210"),
				entry("vnp_Version", "2.1.0"), entry("vnp_Command", "refund"), entry("vnp_TmnCode", "TESTTMN1"),
				entry("vnp_TransactionType", "03"), entry("vnp_TxnRef", "0123456789abcdef0123456789abcdef"),
				entry("vnp_Amount", "272000000"), entry("vnp_TransactionNo", "14000001"),
				entry("vnp_TransactionDate", "20261201081000"), entry("vnp_CreateBy", "staff@test.local"),
				entry("vnp_CreateDate", "20261210090000"), entry("vnp_IpAddr", "127.0.0.1"),
				entry("vnp_OrderInfo", "Hoan tien yeu cau 42"), entry("vnp_SecureHash", REFUND_HASH));
	}

}
