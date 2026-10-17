package vn.edu.uit.flightbooking.payment;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;
import vn.edu.uit.flightbooking.payment.infra.VnpayApi;

/**
 * API công khai của payment (TDD §3.2): tạo lượt thu VNPay. Kết quả thanh toán báo về bằng {@code PaymentSucceeded}.
 * Hoàn tiền chia số tiền vào các lượt thu, mới nhất trước (BR-62): {@link #refund} gọi Refund API của VNPay,
 * {@link #refundManually} chỉ ghi sổ (BR-64). Cả hai chạy trong transaction của bên gọi.
 */
@Service
public class PaymentApi {

	private static final Logger log = LoggerFactory.getLogger(PaymentApi.class);

	/** Q-09. {@code %s}: điều kiện phạm vi, hoặc rỗng. */
	private static final String REFUNDABLE = """
			SELECT c.id, c.amount, c.txn_ref, c.provider_txn_no, c.provider_txn_date,
			       c.amount - COALESCE(SUM(r.amount) FILTER (WHERE r.status = 'SUCCESS'), 0) AS refundable
			FROM payments c
			LEFT JOIN payments r ON r.original_payment_id = c.id
			WHERE c.booking_id = :bookingId AND c.kind = 'CHARGE' AND c.status = 'SUCCESS' %s
			GROUP BY c.id
			HAVING c.amount - COALESCE(SUM(r.amount) FILTER (WHERE r.status = 'SUCCESS'), 0) > 0
			ORDER BY c.completed_at DESC, c.id DESC
			""";

	private static final String INSERT_REFUND = """
			INSERT INTO payments (booking_id, kind, provider, status, amount, refund_request_id, original_payment_id,
			                      txn_ref, response_code, raw_response, completed_at)
			VALUES (:bookingId, 'REFUND', :provider, :status, :amount, :refundRequestId, :chargeId, :txnRef,
			        :responseCode, CAST(:raw AS jsonb), now())
			""";

	private static final String UNREFUNDED = """
			SELECT c.amount - COALESCE((SELECT SUM(r.amount) FROM payments r
			                            WHERE r.original_payment_id = c.id AND r.status = 'SUCCESS'), 0)
			FROM payments c WHERE c.id = ?
			""";

	private static final String REFUNDS = """
			SELECT provider, status, amount, response_code, created_at
			FROM payments WHERE refund_request_id = ? AND kind = 'REFUND' ORDER BY id
			""";

	private static final ZoneOffset BOOKING_ZONE = ZoneOffset.ofHours(7);

	/**
	 * Một đợt hoàn tiền của {@code refundRequestId}. {@code onlyChargeId} khác {@code null}: chỉ hoàn lượt thu đó;
	 * {@code excludedChargeIds} rỗng: không loại lượt nào. {@code createdBy} là {@code vnp_CreateBy}.
	 */
	public record RefundOrder(long bookingId, long refundRequestId, long amount, Long onlyChargeId,
			List<Long> excludedChargeIds, String createdBy) {
	}

	/** {@code refunded}: số đã hoàn thành công; {@code error}: {@code null} khi mọi lượt gọi đều thành công. */
	public record RefundOutcome(long refunded, String error) {
	}

	public record RefundAttempt(String provider, String status, long amount, String responseCode,
			OffsetDateTime createdAt) {
	}

	private record Charge(long id, long amount, String txnRef, String txnNo, String txnDate, long refundable) {
	}

	private static final String INSERT_CHARGE = """
			INSERT INTO payments (booking_id, kind, provider, status, amount, txn_ref, expires_at)
			VALUES (:bookingId, 'CHARGE', 'VNPAY', 'PENDING', :amount, :txnRef, :expiresAt)
			""";

	private final JdbcClient jdbc;

	private final Vnpay vnpay;

	private final VnpayApi vnpayApi;

	PaymentApi(JdbcClient jdbc, Vnpay vnpay, VnpayApi vnpayApi) {
		this.jdbc = jdbc;
		this.vnpay = vnpay;
		this.vnpayApi = vnpayApi;
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

	/**
	 * BR-62: hoàn {@code order.amount()} qua VNPay, lượt thu mới nhất trước. Dừng ở lượt gọi lỗi đầu tiên (ghi dòng
	 * FAILED); số đã hoàn trước đó vẫn được giữ.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public RefundOutcome refund(RefundOrder order) {
		return spread(order, true);
	}

	/** BR-64: ghi dòng MANUAL/SUCCESS theo cùng thứ tự, không gọi VNPay. Vượt số đã thu thì ném lỗi, dòng đã ghi rollback. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void refundManually(RefundOrder order) {
		if (spread(order, false).refunded() < order.amount()) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "Số tiền cần hoàn vượt quá số đã thu");
		}
	}

	/** Số còn hoàn được của một lượt thu. */
	@Transactional(readOnly = true)
	public long unrefunded(long chargeId) {
		return jdbc.sql(UNREFUNDED).param(chargeId).query(Long.class).single();
	}

	/** Các lần hoàn của một yêu cầu, cũ nhất trước. */
	@Transactional(readOnly = true)
	public List<RefundAttempt> refunds(long refundRequestId) {
		return jdbc.sql(REFUNDS)
			.param(refundRequestId)
			.query((rs, i) -> new RefundAttempt(rs.getString("provider"), rs.getString("status"), rs.getLong("amount"),
					rs.getString("response_code"),
					rs.getObject("created_at", OffsetDateTime.class).withOffsetSameInstant(BOOKING_ZONE)))
			.list();
	}

	private RefundOutcome spread(RefundOrder order, boolean viaVnpay) {
		boolean only = order.onlyChargeId() != null;
		boolean excluding = !only && !order.excludedChargeIds().isEmpty();
		var query = jdbc.sql(REFUNDABLE.formatted(only ? "AND c.id = :only" : excluding ? "AND c.id NOT IN (:excluded)" : ""))
			.param("bookingId", order.bookingId());
		if (only) {
			query = query.param("only", order.onlyChargeId());
		}
		else if (excluding) {
			query = query.param("excluded", order.excludedChargeIds());
		}
		List<Charge> charges = query.query((rs, i) -> new Charge(rs.getLong("id"), rs.getLong("amount"),
				rs.getString("txn_ref"), rs.getString("provider_txn_no"), rs.getString("provider_txn_date"),
				rs.getLong("refundable"))).list();

		long refunded = 0;
		for (Charge c : charges) {
			long left = order.amount() - refunded;
			if (left <= 0) {
				break;
			}
			long amount = Math.min(left, c.refundable());
			String txnRef = UUID.randomUUID().toString().replace("-", "");
			String provider = "MANUAL";
			String status = "SUCCESS";
			String code = null;
			String error = null;
			Map<String, Object> raw = null;
			if (viaVnpay) {
				provider = "VNPAY";
				// ponytail: vnp_IpAddr cố định, sandbox không kiểm tra; đổi sang IP server khi lên production
				Vnpay.RefundCall call = new Vnpay.RefundCall(txnRef, amount == c.amount() ? "02" : "03", c.txnRef(),
						amount, c.txnNo(), c.txnDate(), order.createdBy(), Instant.now(), "127.0.0.1",
						"Hoan tien yeu cau " + order.refundRequestId());
				try {
					raw = vnpayApi.post(vnpay.refundBody(call));
					Object rc = raw.get("vnp_ResponseCode");
					if (rc == null) {
						throw new IllegalStateException("Thiếu vnp_ResponseCode");
					}
					code = rc.toString();
					if (!"00".equals(code)) {
						status = "FAILED";
						error = "VNPay từ chối hoàn tiền (mã " + code + ")";
					}
				}
				catch (RuntimeException e) {
					// Lỗi mạng, body rỗng hoặc sai dạng: ghi FAILED và dừng, không để rollback các dòng SUCCESS đã ghi.
					status = "FAILED";
					code = null;
					error = "Không kết nối được VNPay";
					log.warn("Hoàn tiền VNPay lỗi txn_ref={}: {}", txnRef, e.toString());
				}
			}
			jdbc.sql(INSERT_REFUND)
				.param("bookingId", order.bookingId())
				.param("provider", provider)
				.param("status", status)
				.param("amount", amount)
				.param("refundRequestId", order.refundRequestId())
				.param("chargeId", c.id())
				.param("txnRef", txnRef)
				.param("responseCode", code)
				.param("raw", raw == null ? null : JsonMapper.shared().writeValueAsString(raw))
				.update();
			log.info("Hoàn tiền {} txn_ref={} lượt thu={} số tiền={} vnp_ResponseCode={}", provider, txnRef,
					c.txnRef(), amount, code);
			if (error != null) {
				return new RefundOutcome(refunded, error);
			}
			refunded += amount;
		}
		return new RefundOutcome(refunded, null);
	}

}
