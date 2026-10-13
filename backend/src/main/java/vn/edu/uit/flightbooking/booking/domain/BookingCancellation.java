package vn.edu.uit.flightbooking.booking.domain;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.flight.SeatInventory;
import vn.edu.uit.flightbooking.promotion.VoucherApi;

/**
 * Huỷ booking chưa thanh toán (FR-33, BR-45, TDD §6.5): khách tự huỷ thì CANCELLED, quá hạn giữ chỗ thì EXPIRED. Cả
 * hai dùng chung {@link #release}: thứ tự khoá TDD §5.4 là {@code bookings} → {@code flight_cabins} →
 * {@code vouchers}.
 */
@Service
public class BookingCancellation {

	/** BR-45: chờ thêm 5 phút sau hạn giữ chỗ để kịp nhận kết quả thanh toán đến trễ (hằng số, PRD §7). */
	static final Duration GRACE = Duration.ofMinutes(5);

	private record Locked(long id, long userId, BookingStatus status, Long voucherId, Instant holdExpiresAt) {
	}

	private record Seat(long flightId, CabinClass cabin) {
	}

	/** Q-07. */
	private static final String OVERDUE = """
			SELECT id
			FROM bookings
			WHERE status = 'PENDING_PAYMENT'
			  AND hold_expires_at < :cutoff
			ORDER BY hold_expires_at
			LIMIT 100
			""";

	/** APP_FLOW §4.3: chặng ACTIVE thành CANCELLED; trả về chuyến và hạng ghế để trả ghế. */
	private static final String CANCEL_SEGMENTS = """
			UPDATE booking_segments s
			SET status = 'CANCELLED'
			FROM booking_journeys j
			WHERE j.id = s.journey_id AND j.booking_id = :bookingId AND s.status = 'ACTIVE'
			RETURNING s.flight_id, j.cabin_class
			""";

	private final SeatInventory seats;

	private final VoucherApi vouchers;

	private final JdbcClient jdbc;

	BookingCancellation(SeatInventory seats, VoucherApi vouchers, JdbcClient jdbc) {
		this.seats = seats;
		this.vouchers = vouchers;
		this.jdbc = jdbc;
	}

	/** Booking của người khác cũng báo không tìm thấy, để không lộ việc booking đó có tồn tại (BR-104). */
	static BusinessException notFound() {
		return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy booking");
	}

	/** Q-07: tối đa 100 booking chờ thanh toán đã quá hạn giữ chỗ hơn {@link #GRACE} (cùng đồng hồ JVM với {@code expire}), quá hạn lâu nhất trước. */
	@Transactional(readOnly = true)
	public List<Long> overdue() {
		return jdbc.sql(OVERDUE).param("cutoff", Instant.now().minus(GRACE).atOffset(ZoneOffset.UTC)).query(Long.class).list();
	}

	/** FR-33: khách huỷ booking chưa thanh toán của mình, kể cả trong 5 phút ân hạn sau hạn giữ chỗ. */
	@Transactional
	public void cancel(long userId, String code) {
		Locked booking = lock("code = :key", code.toUpperCase(Locale.ROOT)).filter(b -> b.userId() == userId)
			.orElseThrow(BookingCancellation::notFound);
		if (booking.status() != BookingStatus.PENDING_PAYMENT) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "Chỉ huỷ được booking đang chờ thanh toán");
		}
		release(booking, BookingStatus.CANCELLED);
	}

	/**
	 * TDD §6.5: khoá rồi kiểm tra lại. Booking đã được thanh toán, đã bị huỷ, hoặc chưa quá 5 phút ân hạn thì bỏ qua và
	 * trả {@code false}, nên job và khách huỷ cùng lúc không trả ghế hai lần.
	 */
	@Transactional
	public boolean expire(long bookingId) {
		Optional<Locked> booking = lock("id = :key", bookingId);
		if (booking.isEmpty() || booking.get().status() != BookingStatus.PENDING_PAYMENT
				|| !booking.get().holdExpiresAt().plus(GRACE).isBefore(Instant.now())) {
			return false;
		}
		release(booking.get(), BookingStatus.EXPIRED);
		return true;
	}

	private Optional<Locked> lock(String where, Object key) {
		return jdbc.sql("SELECT id, user_id, status, voucher_id, hold_expires_at FROM bookings WHERE " + where
				+ " FOR UPDATE")
			.param("key", key)
			.query(BookingCancellation::locked)
			.optional();
	}

	/**
	 * Mỗi chặng ACTIVE trả đúng (người lớn + trẻ em) ghế một lần, theo {@code flightId} tăng dần (Q-02). Trả lượt
	 * voucher (Q-15, BR-32). Chuyển booking sang {@code status}.
	 */
	private void release(Locked booking, BookingStatus status) {
		int seated = jdbc.sql("SELECT count(*) FROM passengers WHERE booking_id = ? AND type <> 'INFANT'")
			.param(booking.id())
			.query(Integer.class)
			.single();
		jdbc.sql(CANCEL_SEGMENTS)
			.param("bookingId", booking.id())
			.query((rs, rowNum) -> new Seat(rs.getLong("flight_id"), CabinClass.valueOf(rs.getString("cabin_class"))))
			.list()
			.stream()
			.sorted(Comparator.comparingLong(Seat::flightId))
			.forEach(seat -> seats.release(seat.flightId(), seat.cabin(), seated));
		if (booking.voucherId() != null) {
			vouchers.release(booking.voucherId());
		}
		jdbc.sql("UPDATE bookings SET status = :status, updated_at = now() WHERE id = :id")
			.param("status", status.name())
			.param("id", booking.id())
			.update();
	}

	private static Locked locked(ResultSet rs, int rowNum) throws SQLException {
		return new Locked(rs.getLong("id"), rs.getLong("user_id"), BookingStatus.valueOf(rs.getString("status")),
				rs.getObject("voucher_id", Long.class),
				rs.getObject("hold_expires_at", OffsetDateTime.class).toInstant());
	}

}
