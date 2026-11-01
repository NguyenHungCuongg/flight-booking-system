package vn.edu.uit.flightbooking.report;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Chèn dữ liệu trực tiếp bằng SQL để test báo cáo nhanh chóng, độc lập.
 */
public final class TestReports {

	private TestReports() {
	}

	private static long ensureUser(JdbcClient jdbc) {
		Long id = jdbc.sql("SELECT id FROM users WHERE email = 'report-test@uit.edu.vn'").query(Long.class).optional().orElse(null);
		if (id != null) {
			return id;
		}
		return jdbc.sql("""
				INSERT INTO users (email, password_hash, full_name, phone, role)
				VALUES ('report-test@uit.edu.vn', 'hash', 'Khách Test', '0901234567', 'CUSTOMER')
				RETURNING id
				""")
			.query(Long.class)
			.single();
	}

	private static long dummyBookingId(JdbcClient jdbc) {
		Long id = jdbc.sql("SELECT id FROM bookings WHERE code = 'BKTEST'").query(Long.class).optional().orElse(null);
		if (id != null) {
			return id;
		}
		long userId = ensureUser(jdbc);
		return jdbc.sql("""
				INSERT INTO bookings (code, user_id, contact_name, contact_email, contact_phone, status, fare_total, baggage_total, discount_total, total_amount, hold_expires_at)
				VALUES ('BKTEST', ?, 'Khách Test', 'khach@test.local', '0901234567', 'ISSUED', 1000000, 0, 0, 1000000, now() + interval '1 day')
				RETURNING id
				""")
			.params(userId)
			.query(Long.class)
			.single();
	}

	public static void insertPayment(JdbcClient jdbc, String kind, String status, long amount, Instant completedAt) {
		long bookingId = dummyBookingId(jdbc);
		String txnRef = UUID.randomUUID().toString().replace("-", "");
		Object completedAtUtc = "PENDING".equals(status) ? null : completedAt.atOffset(ZoneOffset.UTC);

		if ("REFUND".equals(kind)) {
			// REFUND requires original_payment_id and refund_request_id.
			// Insert original charge in the distant past so it doesn't affect report period metrics.
			String origTxn = UUID.randomUUID().toString().replace("-", "");
			Instant pastTime = completedAt.minusSeconds(86400 * 30);
			long origPaymentId = jdbc.sql("""
					INSERT INTO payments (txn_ref, booking_id, kind, provider, status, amount, completed_at, created_at)
					VALUES (?, ?, 'CHARGE', 'VNPAY', 'SUCCESS', ?, ?, ?)
					RETURNING id
					""")
				.params(origTxn, bookingId, amount, pastTime.atOffset(ZoneOffset.UTC), pastTime.atOffset(ZoneOffset.UTC))
				.query(Long.class)
				.single();

			long refundReqId = jdbc.sql("""
					INSERT INTO refund_requests (booking_id, payment_id, source, status, base_amount, fee_amount, refund_amount, completed_at)
					VALUES (?, ?, 'INVALID_PAYMENT', 'COMPLETED', ?, 0, ?, ?)
					RETURNING id
					""")
				.params(bookingId, origPaymentId, amount, amount, completedAtUtc)
				.query(Long.class)
				.single();

			jdbc.sql("""
					INSERT INTO payments (txn_ref, booking_id, kind, provider, status, amount, completed_at, created_at,
					                     original_payment_id, refund_request_id)
					VALUES (?, ?, 'REFUND', 'VNPAY', ?, ?, ?, ?, ?, ?)
					""")
				.params(txnRef, bookingId, status, amount, completedAtUtc, completedAt.atOffset(ZoneOffset.UTC),
						origPaymentId, refundReqId)
				.update();
		} else {
			jdbc.sql("""
					INSERT INTO payments (txn_ref, booking_id, kind, provider, status, amount, completed_at, created_at)
					VALUES (?, ?, ?, 'VNPAY', ?, ?, ?, ?)
					""")
				.params(txnRef, bookingId, kind, status, amount, completedAtUtc, completedAt.atOffset(ZoneOffset.UTC))
				.update();
		}
	}

	public static void insertAirline(JdbcClient jdbc, String code, String name) {
		String prefix = String.format("%03d", Math.abs(code.hashCode() % 900) + 100);
		jdbc.sql("""
				INSERT INTO airlines (code, name, ticket_prefix, active)
				VALUES (?, ?, ?, TRUE)
				ON CONFLICT (code) DO NOTHING
				""")
			.params(code, name, prefix)
			.update();
	}

	public static void insertAirport(JdbcClient jdbc, String code, String name, String city) {
		jdbc.sql("""
				INSERT INTO airports (code, name, city, country_code, timezone, active)
				VALUES (?, ?, ?, 'VN', 'Asia/Ho_Chi_Minh', TRUE)
				ON CONFLICT (code) DO NOTHING
				""")
			.params(code, name, city)
			.update();
	}

	public static long insertFlight(JdbcClient jdbc, String flightNumber, String airlineCode,
			String departureAirport, String arrivalAirport, Instant departureTime, String status) {
		return jdbc.sql("""
				INSERT INTO flights (flight_number, airline_code, departure_airport, arrival_airport,
				                     departure_time, arrival_time, aircraft_model, status)
				VALUES (?, ?, ?, ?, ?, ?, 'A321', ?)
				RETURNING id
				""")
			.params(flightNumber, airlineCode, departureAirport, arrivalAirport,
					departureTime.atOffset(ZoneOffset.UTC),
					departureTime.plusSeconds(7200).atOffset(ZoneOffset.UTC),
					status)
			.query(Long.class)
			.single();
	}

	public static void insertCabin(JdbcClient jdbc, long flightId, String cabinClass, int totalSeats, int availableSeats) {
		jdbc.sql("""
				INSERT INTO flight_cabins (flight_id, cabin_class, total_seats, available_seats)
				VALUES (?, ?, ?, ?)
				""")
			.params(flightId, cabinClass, totalSeats, availableSeats)
			.update();
	}

	public static long insertBooking(JdbcClient jdbc, String code, long totalAmount) {
		long userId = ensureUser(jdbc);
		String validCode = "BK" + UUID.randomUUID().toString().replaceAll("[^A-HJ-NP-Z2-9]", "").substring(0, 4);
		return jdbc.sql("""
				INSERT INTO bookings (code, user_id, contact_name, contact_email, contact_phone, status,
				                     fare_total, baggage_total, discount_total, total_amount, hold_expires_at)
				VALUES (?, ?, 'Khách Test', 'khach@test.local', '0901234567', 'ISSUED', ?, 0, 0, ?, now() + interval '1 day')
				RETURNING id
				""")
			.params(validCode, userId, totalAmount, totalAmount)
			.query(Long.class)
			.single();
	}

	public static long insertPassenger(JdbcClient jdbc, long bookingId, String lastName, String firstName) {
		return jdbc.sql("""
				INSERT INTO passengers (booking_id, type, last_name, first_name, gender, date_of_birth)
				VALUES (?, 'ADULT', ?, ?, 'MALE', '1990-01-01')
				RETURNING id
				""")
			.params(bookingId, lastName, firstName)
			.query(Long.class)
			.single();
	}

	public static long insertSegment(JdbcClient jdbc, long bookingId, long flightId, String status) {
		String airlineCode = jdbc.sql("SELECT airline_code FROM flights WHERE id = ?")
			.params(flightId)
			.query(String.class)
			.single();

		Long fareFamilyId = jdbc.sql("SELECT id FROM fare_families WHERE airline_code = ? AND name = 'Eco Test'")
			.params(airlineCode)
			.query(Long.class)
			.optional()
			.orElse(null);
		if (fareFamilyId == null) {
			fareFamilyId = jdbc.sql("""
					INSERT INTO fare_families (airline_code, cabin_class, name, carry_on_kg, checked_baggage_kg,
					                          refundable, refund_fee, changeable, change_fee)
					VALUES (?, 'ECONOMY', 'Eco Test', 7, 20, TRUE, 0, TRUE, 0)
					RETURNING id
					""")
				.params(airlineCode)
				.query(Long.class)
				.single();
		}

		Long outboundJourneyId = jdbc.sql("SELECT id FROM booking_journeys WHERE booking_id = ? AND direction = 'OUTBOUND'")
			.params(bookingId)
			.query(Long.class)
			.optional()
			.orElse(null);

		long journeyId;
		short seq;
		if (outboundJourneyId == null) {
			journeyId = jdbc.sql("""
					INSERT INTO booking_journeys (booking_id, direction, airline_code, fare_family_id, fare_family_name,
					                             cabin_class, carry_on_kg, checked_baggage_kg, refundable, refund_fee,
					                             changeable, change_fee)
					VALUES (?, 'OUTBOUND', ?, ?, 'Eco Test', 'ECONOMY', 7, 20, TRUE, 0, TRUE, 0)
					RETURNING id
					""")
				.params(bookingId, airlineCode, fareFamilyId)
				.query(Long.class)
				.single();
			seq = 1;
		} else {
			int activeCount = jdbc.sql("SELECT COUNT(*) FROM booking_segments WHERE journey_id = ? AND status = 'ACTIVE'")
				.params(outboundJourneyId)
				.query(Integer.class)
				.single();
			if ("ACTIVE".equals(status) && activeCount >= 1) {
				Long returnJourneyId = jdbc.sql("SELECT id FROM booking_journeys WHERE booking_id = ? AND direction = 'RETURN'")
					.params(bookingId)
					.query(Long.class)
					.optional()
					.orElse(null);
				if (returnJourneyId == null) {
					returnJourneyId = jdbc.sql("""
							INSERT INTO booking_journeys (booking_id, direction, airline_code, fare_family_id, fare_family_name,
							                             cabin_class, carry_on_kg, checked_baggage_kg, refundable, refund_fee,
							                             changeable, change_fee)
							VALUES (?, 'RETURN', ?, ?, 'Eco Test', 'ECONOMY', 7, 20, TRUE, 0, TRUE, 0)
							RETURNING id
							""")
						.params(bookingId, airlineCode, fareFamilyId)
						.query(Long.class)
						.single();
				}
				journeyId = returnJourneyId;
				int returnCount = jdbc.sql("SELECT COUNT(*) FROM booking_segments WHERE journey_id = ?")
					.params(journeyId)
					.query(Integer.class)
					.single();
				seq = (short) (returnCount + 1);
			} else {
				journeyId = outboundJourneyId;
				int outboundCount = jdbc.sql("SELECT COUNT(*) FROM booking_segments WHERE journey_id = ?")
					.params(journeyId)
					.query(Integer.class)
					.single();
				seq = (short) (outboundCount + 1);
			}
		}

		return jdbc.sql("""
				INSERT INTO booking_segments (journey_id, flight_id, seq, adult_price, status)
				VALUES (?, ?, ?, 1000000, ?)
				RETURNING id
				""")
			.params(journeyId, flightId, seq, status)
			.query(Long.class)
			.single();
	}

	public static void insertTicket(JdbcClient jdbc, long bookingId, long segmentId, long passengerId,
			String ticketNumber, long fareAmount, Instant issuedAt) {
		jdbc.sql("""
				INSERT INTO tickets (segment_id, passenger_id, ticket_number, fare_amount, issued_at)
				VALUES (?, ?, ?, ?, ?)
				""")
			.params(segmentId, passengerId, ticketNumber, fareAmount,
					issuedAt == null ? null : issuedAt.atOffset(ZoneOffset.UTC))
			.update();
	}

}
