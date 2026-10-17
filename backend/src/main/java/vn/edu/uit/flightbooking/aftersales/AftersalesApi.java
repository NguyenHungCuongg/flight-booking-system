package vn.edu.uit.flightbooking.aftersales;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API công khai của module aftersales cho module khác (email hoàn tiền). */
@Service
public class AftersalesApi {

	/**
	 * Nội dung email về một yêu cầu hoàn. {@code direction} là {@code null} với yêu cầu của khoản thanh toán trùng hoặc
	 * trễ (không gắn chiều nào).
	 */
	public record RefundNotice(String bookingCode, String contactName, String contactEmail, String direction,
			long refundAmount, String staffNote) {
	}

	private final JdbcClient jdbc;

	AftersalesApi(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	public RefundNotice refund(long id) {
		return jdbc.sql("""
				SELECT b.code, b.contact_name, b.contact_email, j.direction, r.refund_amount, r.staff_note
				FROM refund_requests r
				JOIN bookings b ON b.id = r.booking_id
				LEFT JOIN booking_journeys j ON j.id = r.journey_id
				WHERE r.id = ?
				""")
			.param(id)
			.query((rs, rowNum) -> new RefundNotice(rs.getString("code"), rs.getString("contact_name"),
					rs.getString("contact_email"), rs.getString("direction"), rs.getLong("refund_amount"),
					rs.getString("staff_note")))
			.single();
	}

}
