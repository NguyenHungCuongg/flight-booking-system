package vn.edu.uit.flightbooking.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/**
 * Q-01, Q-02 và tiền đề của T1. Không dùng {@code @Transactional}: mỗi luồng cần transaction và connection riêng,
 * nên dữ liệu được commit thật và dọn ở {@link #cleanUp()}. Mã Q9, QZA, QZB chỉ dùng trong test này.
 */
@IntegrationTest
class SeatInventoryTest {

	@Autowired
	SeatInventory seats;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PlatformTransactionManager transactions;

	long flightId;

	@BeforeEach
	void setUp() {
		jdbc.sql("INSERT INTO airlines (code, name, ticket_prefix) VALUES ('Q9', 'Hãng Ghế', '909')").update();
		jdbc.sql("""
				INSERT INTO airports (code, name, city, country_code, timezone) VALUES
				    ('QZA', 'Đi', 'Hà Nội', 'VN', 'Asia/Ho_Chi_Minh'),
				    ('QZB', 'Đến', 'Đà Nẵng', 'VN', 'Asia/Ho_Chi_Minh')
				""").update();
		flightId = jdbc.sql("""
				INSERT INTO flights (airline_code, flight_number, departure_airport, arrival_airport,
				                     departure_time, arrival_time)
				VALUES ('Q9', 'Q9100', 'QZA', 'QZB', now() + interval '2 days', now() + interval '2 days 1 hour')
				RETURNING id
				""").query(Long.class).single();
		jdbc.sql("""
				INSERT INTO flight_cabins (flight_id, cabin_class, total_seats, available_seats)
				VALUES (?, 'ECONOMY', 5, 5), (?, 'BUSINESS', 2, 2)
				""").params(flightId, flightId).update();
	}

	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM flights WHERE airline_code = 'Q9'").update();
		jdbc.sql("DELETE FROM airports WHERE code IN ('QZA', 'QZB')").update();
		jdbc.sql("DELETE FROM airlines WHERE code = 'Q9'").update();
	}

	int available(CabinClass cabin) {
		return jdbc.sql("SELECT available_seats FROM flight_cabins WHERE flight_id = ? AND cabin_class = ?")
			.params(flightId, cabin.name())
			.query(Integer.class)
			.single();
	}

	/** T1 ở mức kho ghế: 20 luồng cùng giữ 1 ghế trên hạng còn 5 ghế. */
	@Test
	void twentyConcurrentHoldsNeverOversell() throws Exception {
		int threads = 20;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger held = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();
		for (int i = 0; i < threads; i++) {
			pool.submit(() -> {
				start.await();
				try {
					seats.hold(flightId, CabinClass.ECONOMY, 1);
					held.incrementAndGet();
				}
				catch (BusinessException e) {
					if (e.code() == ErrorCode.SEATS_UNAVAILABLE) {
						rejected.incrementAndGet();
					}
				}
				return null;
			});
		}
		start.countDown();
		pool.shutdown();
		assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

		assertThat(held).hasValue(5);
		assertThat(rejected).hasValue(15);
		assertThat(available(CabinClass.ECONOMY)).isZero();
	}

	@Test
	void holdTakesAllRequestedSeatsOrNone() {
		seats.hold(flightId, CabinClass.BUSINESS, 2);

		assertThatThrownBy(() -> seats.hold(flightId, CabinClass.BUSINESS, 1))
			.isInstanceOf(BusinessException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.SEATS_UNAVAILABLE);
		assertThatThrownBy(() -> seats.hold(flightId, CabinClass.ECONOMY, 6)).isInstanceOf(BusinessException.class);
		assertThat(available(CabinClass.ECONOMY)).isEqualTo(5);
	}

	@Test
	void cabinNotOnFlightIsUnavailable() {
		assertThatThrownBy(() -> seats.hold(flightId, CabinClass.FIRST, 1))
			.isInstanceOf(BusinessException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.SEATS_UNAVAILABLE);
	}

	@Test
	void releaseReturnsSeats() {
		seats.hold(flightId, CabinClass.ECONOMY, 3);
		seats.release(flightId, CabinClass.ECONOMY, 3);

		assertThat(available(CabinClass.ECONOMY)).isEqualTo(5);
	}

	/** TDD §5.4: khoá chia sẻ chặn khoá ghi (huỷ chuyến, Plan 16) tới khi transaction giữ chỗ kết thúc. */
	@Test
	void lockedFlightsCannotBeLockedForUpdate() throws Exception {
		TransactionTemplate tx = new TransactionTemplate(transactions);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(1);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
			seats.lockFlights(List.of(flightId));
			locked.countDown();
			try {
				done.await(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}));
		try {
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> tx.executeWithoutResult(status -> jdbc
				.sql("SELECT id FROM flights WHERE id = ? FOR UPDATE NOWAIT")
				.param(flightId)
				.query(Long.class)
				.list())).isInstanceOf(DataAccessException.class);
			// Khoá chia sẻ không chặn nhau: một lượt giữ chỗ khác vẫn khoá được.
			tx.executeWithoutResult(status -> seats.lockFlights(List.of(flightId)));
		}
		finally {
			done.countDown();
			holder.get(10, TimeUnit.SECONDS);
			pool.shutdown();
		}
	}

}
