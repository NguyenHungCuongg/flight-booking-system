package vn.edu.uit.flightbooking.payment;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.payment.domain.Vnpay;

/**
 * API công khai của payment (TDD §3.2): tạo lượt thu VNPay. Kết quả thanh toán báo về bằng {@code PaymentSucceeded}.
 * Plan 14 thêm hoàn tiền và liệt kê lượt thu còn hoàn được.
 */
@Service
public class PaymentApi {

	private static final String INSERT_CHARGE = """
			INSERT INTO payments (booking_id, kind, provider, status, amount, txn_ref, expires_at)
			VALUES (:bookingId, 'CHARGE', 'VNPAY', 'PENDING', :amount, :txnRef, :expiresAt)
			""";

	private final JdbcClient jdbc;

	private final Vnpay vnpay;

	PaymentApi(JdbcClient jdbc, Vnpay vnpay) {
		this.jdbc = jdbc;
		this.vnpay = vnpay;
	}

	/**
	 * FR-30, TDD §6.3: ghi lượt thu PENDING và trả URL thanh toán VNPay hết hạn lúc {@code expiresAt} (BR-41). Bên gọi
	 * tự kiểm tra trạng thái và hạn giữ chỗ. {@code orderInfo} phải không dấu.
	 */
	@Transactional
	public String charge(long bookingId, long amount, Instant expiresAt, String orderInfo, String clientIp) {
		String txnRef = UUID.randomUUID().toString().replace("-", "");
		jdbc.sql(INSERT_CHARGE)
			.param("bookingId", bookingId)
			.param("amount", amount)
			.param("txnRef", txnRef)
			.param("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))
			.update();
		return vnpay.payUrl(txnRef, amount, orderInfo, clientIp, Instant.now(), expiresAt);
	}

}
