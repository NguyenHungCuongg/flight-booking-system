package vn.edu.uit.flightbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest.ContactRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest.JourneyRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest.PassengerRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingService;
import vn.edu.uit.flightbooking.booking.domain.Direction;
import vn.edu.uit.flightbooking.booking.domain.Gender;
import vn.edu.uit.flightbooking.booking.domain.PassengerType;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.promotion.TestVouchers;

/**
 * T1, T16. Không dùng {@code @Transactional}: mỗi luồng cần transaction và connection riêng, nên dữ liệu được commit
 * thật. Hãng Q8, sân bay QYA, QYB, voucher QVRACE11 chỉ dùng trong test này và được xoá ở {@link #cleanUp()}.
 */
@IntegrationTest
class BookingConcurrencyTest {

	@Autowired
	BookingService bookings;

	@Autowired
	JdbcClient jdbc;

	long userId;

	long fareFamilyId;

	long flightId;

	/** Chuyến Q8100 QYA→QYB sau 10 ngày, ECONOMY còn 5 ghế, gói Eco 1.000.000. */
	@BeforeEach
	void setUp() {
		jdbc.sql("INSERT INTO airlines (code, name, ticket_prefix) VALUES ('Q8', 'Hãng Đua', '908')").update();
		jdbc.sql("""
				INSERT INTO airports (code, name, city, country_code, timezone) VALUES
				    ('QYA', 'Đi', 'Hà Nội', 'VN', 'Asia/Ho_Chi_Minh'),
				    ('QYB', 'Đến', 'Đà Nẵng', 'VN', 'Asia/Ho_Chi_Minh')
				""").update();
		fareFamilyId = jdbc.sql("""
				INSERT INTO fare_families (airline_code, cabin_class, name, carry_on_kg, checked_baggage_kg,
				                           refundable, changeable)
				VALUES ('Q8', 'ECONOMY', 'Eco', 7, 0, FALSE, TRUE)
				RETURNING id
				""").query(Long.class).single();
		flightId = jdbc.sql("""
				INSERT INTO flights (airline_code, flight_number, departure_airport, arrival_airport,
				                     departure_time, arrival_time)
				VALUES ('Q8', 'Q8100', 'QYA', 'QYB', now() + interval '10 days', now() + interval '10 days 1 hour')
				RETURNING id
				""").query(Long.class).single();
		jdbc.sql("""
				INSERT INTO flight_cabins (flight_id, cabin_class, total_seats, available_seats)
				VALUES (?, 'ECONOMY', 5, 5)
				""").param(flightId).update();
		jdbc.sql("INSERT INTO flight_fares (flight_id, fare_family_id, price) VALUES (?, ?, 1000000)")
			.params(flightId, fareFamilyId)
			.update();
		userId = TestUsers.create(jdbc, Role.CUSTOMER, TestUsers.randomEmail());
	}

	/** Xoá theo thứ tự khoá ngoại: vé, chặng, chiều, hành khách, booking, rồi tới danh mục. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("""
				DELETE FROM tickets WHERE passenger_id IN
				    (SELECT p.id FROM passengers p JOIN bookings b ON b.id = p.booking_id WHERE b.user_id = ?)
				""").param(userId).update();
		jdbc.sql("""
				DELETE FROM booking_segments WHERE journey_id IN
				    (SELECT j.id FROM booking_journeys j JOIN bookings b ON b.id = j.booking_id WHERE b.user_id = ?)
				""").param(userId).update();
		jdbc.sql("DELETE FROM booking_journeys WHERE booking_id IN (SELECT id FROM bookings WHERE user_id = ?)")
			.param(userId)
			.update();
		jdbc.sql("DELETE FROM passengers WHERE booking_id IN (SELECT id FROM bookings WHERE user_id = ?)")
			.param(userId)
			.update();
		jdbc.sql("DELETE FROM bookings WHERE user_id = ?").param(userId).update();
		jdbc.sql("DELETE FROM users WHERE id = ?").param(userId).update();
		jdbc.sql("DELETE FROM vouchers WHERE code = 'QVRACE11'").update();
		jdbc.sql("DELETE FROM flights WHERE airline_code = 'Q8'").update();
		jdbc.sql("DELETE FROM fare_families WHERE airline_code = 'Q8'").update();
		jdbc.sql("DELETE FROM airports WHERE code IN ('QYA', 'QYB')").update();
		jdbc.sql("DELETE FROM airlines WHERE code = 'Q8'").update();
	}

	BookingRequest request(String voucherCode) {
		return new BookingRequest(List.of(new JourneyRequest(Direction.OUTBOUND, List.of(flightId), fareFamilyId)),
				List.of(new PassengerRequest(PassengerType.ADULT, "NGUYEN", "VAN AN", Gender.MALE,
						LocalDate.of(1990, 5, 20), null, null, null)),
				List.of(), new ContactRequest("NGUYEN VAN AN", "an@example.com", "0901234567"), voucherCode);
	}

	int available() {
		return jdbc.sql("SELECT available_seats FROM flight_cabins WHERE flight_id = ?")
			.param(flightId)
			.query(Integer.class)
			.single();
	}

	long bookingCount() {
		return jdbc.sql("SELECT count(*) FROM bookings WHERE user_id = ?").param(userId).query(Long.class).single();
	}

	/**
	 * Chạy {@code task} trên {@code threads} luồng cùng lúc. Kết quả mỗi luồng là {@code "OK"}, mã lỗi (VD
	 * {@code "SEATS_UNAVAILABLE"}), hoặc mã lỗi kèm lý do (VD {@code "VOUCHER_INVALID:ALREADY_USED"}).
	 */
	List<String> race(int threads, Runnable task) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<String>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			futures.add(pool.submit(() -> {
				start.await();
				try {
					task.run();
					return "OK";
				}
				catch (BusinessException e) {
					Object reason = e.properties().get("reason");
					return reason == null ? e.code().name() : e.code().name() + ":" + reason;
				}
			}));
		}
		start.countDown();
		pool.shutdown();
		assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
		List<String> results = new ArrayList<>();
		for (Future<String> future : futures) {
			results.add(future.get());
		}
		return results;
	}

	/** T1: 20 luồng cùng giữ chỗ trên chuyến còn 5 ghế, mỗi luồng 1 khách. */
	@Test
	void twentyConcurrentHoldsSellExactlyFiveSeats() throws Exception {
		List<String> results = race(20, () -> bookings.create(userId, request(null)));

		assertThat(results).filteredOn(r -> r.equals("OK")).hasSize(5);
		assertThat(results).filteredOn(r -> r.equals("SEATS_UNAVAILABLE")).hasSize(15);
		assertThat(available()).isZero();
		assertThat(bookingCount()).isEqualTo(5);
	}

	/** T16: một tài khoản dùng cùng một voucher cho nhiều booking cùng lúc thì chỉ một booking thành công. */
	@Test
	void sameVoucherAtOnceKeepsOneBooking() throws Exception {
		long voucher = TestVouchers.insert(jdbc, "QVRACE11", Map.of());

		List<String> results = race(5, () -> bookings.create(userId, request("QVRACE11")));

		assertThat(results).filteredOn(r -> r.equals("OK")).hasSize(1);
		assertThat(results).filteredOn(r -> r.equals("VOUCHER_INVALID:ALREADY_USED")).hasSize(4);
		assertThat(TestVouchers.usedCount(jdbc, voucher)).isEqualTo(1);
		assertThat(available()).isEqualTo(4);
		assertThat(bookingCount()).isEqualTo(1);
	}

}
