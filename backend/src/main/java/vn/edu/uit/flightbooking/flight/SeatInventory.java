package vn.edu.uit.flightbooking.flight;

import java.util.Collection;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/**
 * Kho ghế: giữ và trả ghế chỉ bằng {@code UPDATE} nguyên tử (Q-01, Q-02, TDD §5.4). Bên gọi chạy trong transaction
 * của mình, giữ ghế nhiều chặng theo thứ tự {@code flightId} tăng dần, và khoá chuyến bằng {@link #lockFlights} trước khi
 * giữ ghế.
 */
@Service
public class SeatInventory {

	private final JdbcClient jdbc;

	SeatInventory(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * TDD §5.4: khoá chia sẻ các chuyến theo id tăng dần trước khi giữ ghế. Nhiều lượt giữ chỗ khoá cùng lúc được,
	 * còn huỷ chuyến (khoá FOR UPDATE, Plan 16) phải chờ các lượt đó xong.
	 */
	@Transactional
	public void lockFlights(Collection<Long> flightIds) {
		jdbc.sql("SELECT id FROM flights WHERE id IN (:ids) ORDER BY id FOR SHARE")
			.param("ids", flightIds)
			.query(Long.class)
			.list();
	}

	/** Q-01. Không đủ ghế (hoặc chuyến không có hạng này) thì báo SEATS_UNAVAILABLE. */
	@Transactional
	public void hold(long flightId, CabinClass cabin, int seats) {
		Assert.isTrue(seats > 0, "seats phải lớn hơn 0");
		int updated = jdbc.sql("""
				UPDATE flight_cabins
				SET available_seats = available_seats - :seats
				WHERE flight_id = :flightId AND cabin_class = :cabin AND available_seats >= :seats
				""")
			.param("seats", seats)
			.param("flightId", flightId)
			.param("cabin", cabin.name())
			.update();
		if (updated == 0) {
			throw new BusinessException(ErrorCode.SEATS_UNAVAILABLE, "Chuyến bay không còn đủ ghế cho hạng đã chọn");
		}
	}

	/** Q-02. Mỗi chặng rời trạng thái giữ ghế thì gọi đúng một lần (APP_FLOW §4.3). */
	@Transactional
	public void release(long flightId, CabinClass cabin, int seats) {
		Assert.isTrue(seats > 0, "seats phải lớn hơn 0");
		jdbc.sql("""
				UPDATE flight_cabins
				SET available_seats = available_seats + :seats
				WHERE flight_id = :flightId AND cabin_class = :cabin
				""")
			.param("seats", seats)
			.param("flightId", flightId)
			.param("cabin", cabin.name())
			.update();
	}

}
