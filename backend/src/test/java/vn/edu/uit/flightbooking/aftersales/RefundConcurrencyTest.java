package vn.edu.uit.flightbooking.aftersales;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
import vn.edu.uit.flightbooking.aftersales.domain.CustomerRefunds;
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

/**
 * T14. Không dùng {@code @Transactional}: mỗi luồng cần transaction riêng, nên dữ liệu được commit thật. Hãng Q7,
 * sân bay QXA, QXB chỉ dùng trong test này và được xoá ở {@link #cleanUp()}.
 */
@IntegrationTest
class RefundConcurrencyTest {

	@Autowired
	BookingService bookings;

	@Autowired
	CustomerRefunds refunds;

	@Autowired
	JdbcClient jdbc;

	long userId;

	long staffId;

	long flightId;

	long bookingId;

	String code;

	/** Chuyến Q7100 QXA→QXB sau 10 ngày, 5 ghế ECONOMY, 1.000.000; một booking một chiều đã xuất vé. */
	@BeforeEach
	void setUp() {
		jdbc.sql("INSERT INTO airlines (code, name, ticket_prefix) VALUES ('Q7', 'Hãng Hoàn', '907')").update();
		jdbc.sql("""
				INSERT INTO airports (code, name, city, country_code, timezone) VALUES
				    ('QXA', 'Đi', 'Hà Nội', 'VN', 'Asia/Ho_Chi_Minh'),
				    ('QXB', 'Đến', 'Đà Nẵng', 'VN', 'Asia/Ho_Chi_Minh')
				""").update();
		long fareFamilyId = jdbc.sql("""
				INSERT INTO fare_families (airline_code, cabin_class, name, carry_on_kg, checked_baggage_kg,
				                           refundable, refund_fee, changeable)
				VALUES ('Q7', 'ECONOMY', 'Eco', 7, 0, TRUE, 300000, TRUE)
				RETURNING id
				""").query(Long.class).single();
		flightId = jdbc.sql("""
				INSERT INTO flights (airline_code, flight_number, departure_airport, arrival_airport,
				                     departure_time, arrival_time)
				VALUES ('Q7', 'Q7100', 'QXA', 'QXB', now() + interval '10 days', now() + interval '10 days 1 hour')
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
		staffId = TestUsers.create(jdbc, Role.STAFF, TestUsers.randomEmail());
		BookingRequest request = new BookingRequest(
				List.of(new JourneyRequest(Direction.OUTBOUND, List.of(flightId), fareFamilyId)),
				List.of(new PassengerRequest(PassengerType.ADULT, "NGUYEN", "VAN AN", Gender.MALE,
						LocalDate.of(1990, 5, 20), null, null, null)),
				List.of(), new ContactRequest("NGUYEN VAN AN", "an@example.com", "0901234567"), null);
		code = bookings.create(userId, request).code();
		bookingId = jdbc.sql("SELECT id FROM bookings WHERE code = ?").param(code).query(Long.class).single();
		// Không gọi issue: tránh email bất đồng bộ chạy trong lúc dọn dữ liệu.
		jdbc.sql("UPDATE bookings SET status = 'ISSUED', issued_at = now() WHERE id = ?").param(bookingId).update();
	}

	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM payments WHERE kind = 'REFUND' AND booking_id IN (SELECT id FROM bookings WHERE user_id = ?)")
			.param(userId)
			.update();
		jdbc.sql("DELETE FROM refund_requests WHERE booking_id IN (SELECT id FROM bookings WHERE user_id = ?)")
			.param(userId)
			.update();
		jdbc.sql("DELETE FROM payments WHERE booking_id IN (SELECT id FROM bookings WHERE user_id = ?)")
			.param(userId)
			.update();
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
		jdbc.sql("DELETE FROM users WHERE id IN (?, ?)").params(userId, staffId).update();
		jdbc.sql("DELETE FROM flights WHERE airline_code = 'Q7'").update();
		jdbc.sql("DELETE FROM fare_families WHERE airline_code = 'Q7'").update();
		jdbc.sql("DELETE FROM airports WHERE code IN ('QXA', 'QXB')").update();
		jdbc.sql("DELETE FROM airlines WHERE code = 'Q7'").update();
	}

	/** Chạy {@code task} trên {@code threads} luồng cùng lúc; kết quả mỗi luồng là {@code "OK"} hoặc mã lỗi. */
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

	/** T14: nhiều yêu cầu hoàn cùng lúc cho một chiều; đúng một yêu cầu được ghi. */
	@Test
	void t14ConcurrentRequestsForOneJourneyKeepOne() throws Exception {
		List<String> results = race(5, () -> refunds.request(userId, code, "OUTBOUND", "Đổi kế hoạch"));

		assertThat(results).filteredOn("OK"::equals).hasSize(1);
		assertThat(results).filteredOn("REQUEST_IN_PROGRESS"::equals).hasSize(4);
		assertThat(jdbc.sql("SELECT count(*) FROM refund_requests WHERE booking_id = ?").param(bookingId)
			.query(Long.class).single()).isEqualTo(1);
	}

}
