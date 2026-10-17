package vn.edu.uit.flightbooking.aftersales.domain;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.identity.UserApi;
import vn.edu.uit.flightbooking.payment.PaymentApi;
import vn.edu.uit.flightbooking.payment.PaymentApi.RefundAttempt;

/**
 * Hàng đợi và chi tiết yêu cầu hoàn cho Staff (FR-71, S-03, S-04). Chỉ đọc; đọc {@code bookings} và
 * {@code booking_journeys} bằng SQL. Giờ theo giờ Việt Nam.
 */
@Service
public class RefundQueries {

	/** {@code direction} là {@code null} với nguồn INVALID_PAYMENT. */
	public record RefundSummary(long id, String bookingCode, String direction, RefundSource source,
			RefundStatus status, long refundAmount, OffsetDateTime createdAt) {
	}

	/** {@code processedBy}: email Staff đã duyệt hoặc từ chối; {@code refundedAmount}: tổng các lượt hoàn SUCCESS. */
	public record RefundDetail(long id, String bookingCode, String bookingStatus, String contactName,
			String contactEmail, String contactPhone, String direction, RefundSource source, RefundStatus status,
			long baseAmount, long feeAmount, long refundAmount, long refundedAmount, String reason, String staffNote,
			String lastError, String processedBy, OffsetDateTime processedAt, OffsetDateTime createdAt,
			OffsetDateTime completedAt, List<RefundAttempt> attempts) {
	}

	private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

	private static final String FROM = """
			FROM refund_requests r
			JOIN bookings b ON b.id = r.booking_id
			LEFT JOIN booking_journeys j ON j.id = r.journey_id
			""";

	private final JdbcClient jdbc;

	private final UserApi users;

	private final PaymentApi payments;

	RefundQueries(JdbcClient jdbc, UserApi users, PaymentApi payments) {
		this.jdbc = jdbc;
		this.users = users;
		this.payments = payments;
	}

	/** Cũ nhất trước, lọc theo trạng thái và nguồn nếu có. */
	@Transactional(readOnly = true)
	public Page<RefundSummary> page(RefundStatus status, RefundSource source, Pageable pageable) {
		StringBuilder where = new StringBuilder(" WHERE TRUE");
		Map<String, Object> params = new HashMap<>();
		if (status != null) {
			where.append(" AND r.status = :status");
			params.put("status", status.name());
		}
		if (source != null) {
			where.append(" AND r.source = :source");
			params.put("source", source.name());
		}
		long total = jdbc.sql("SELECT count(*) FROM refund_requests r" + where).params(params).query(Long.class).single();
		params.put("limit", pageable.getPageSize());
		params.put("offset", pageable.getOffset());
		List<RefundSummary> rows = jdbc.sql("""
				SELECT r.id, b.code, j.direction, r.source, r.status, r.refund_amount, r.created_at
				""" + FROM + where + " ORDER BY r.created_at, r.id LIMIT :limit OFFSET :offset")
			.params(params)
			.query((rs, n) -> new RefundSummary(rs.getLong("id"), rs.getString("code"), rs.getString("direction"),
					RefundSource.valueOf(rs.getString("source")), RefundStatus.valueOf(rs.getString("status")),
					rs.getLong("refund_amount"), vietnam(rs, "created_at")))
			.list();
		return new PageImpl<>(rows, pageable, total);
	}

	@Transactional(readOnly = true)
	public RefundDetail detail(long id) {
		List<RefundAttempt> attempts = payments.refunds(id);
		long refunded = attempts.stream().filter(a -> "SUCCESS".equals(a.status())).mapToLong(RefundAttempt::amount).sum();
		return jdbc.sql("""
				SELECT r.id, b.code, b.status AS booking_status, b.contact_name, b.contact_email, b.contact_phone,
				       j.direction, r.source, r.status, r.base_amount, r.fee_amount, r.refund_amount, r.reason,
				       r.staff_note, r.last_error, r.processed_by, r.processed_at, r.created_at, r.completed_at
				""" + FROM + " WHERE r.id = ?")
			.param(id)
			.query((rs, n) -> {
				Long processedBy = rs.getObject("processed_by", Long.class);
				return new RefundDetail(rs.getLong("id"), rs.getString("code"), rs.getString("booking_status"),
						rs.getString("contact_name"), rs.getString("contact_email"), rs.getString("contact_phone"),
						rs.getString("direction"), RefundSource.valueOf(rs.getString("source")),
						RefundStatus.valueOf(rs.getString("status")), rs.getLong("base_amount"),
						rs.getLong("fee_amount"), rs.getLong("refund_amount"), refunded, rs.getString("reason"),
						rs.getString("staff_note"), rs.getString("last_error"),
						processedBy == null ? null : users.get(processedBy).email(), vietnam(rs, "processed_at"),
						vietnam(rs, "created_at"), vietnam(rs, "completed_at"), attempts);
			})
			.optional()
			.orElseThrow(RefundRequests::notFound);
	}

	private static OffsetDateTime vietnam(ResultSet rs, String column) throws SQLException {
		OffsetDateTime time = rs.getObject(column, OffsetDateTime.class);
		return time == null ? null : time.atZoneSameInstant(VIETNAM).toOffsetDateTime();
	}

}
