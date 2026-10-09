package vn.edu.uit.flightbooking.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;

@IntegrationTest
class MigrationTest {

	@Autowired
	JdbcClient jdbc;

	@Test
	void createsAllTablesFromSchemaDoc() {
		var tables = jdbc.sql("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
				""").query(String.class).set();

		assertThat(tables).containsExactlyInAnyOrder(
				"users", "password_reset_tokens", "system_settings",
				"airports", "airlines", "fare_families", "baggage_options",
				"flights", "flight_cabins", "flight_fares", "vouchers",
				"bookings", "booking_journeys", "booking_segments", "passengers", "tickets", "booking_baggage",
				"reschedules", "refund_requests", "payments");
	}

	@Test
	void seedsEightSystemSettings() {
		assertThat(jdbc.sql("SELECT count(*) FROM system_settings").query(Long.class).single()).isEqualTo(8);
	}

	@Test
	@Transactional
	void databaseRejectsOverbooking() {
		jdbc.sql("INSERT INTO airports (code, name, city, country_code, timezone) VALUES ('AAA', 'A', 'A', 'VN', 'Asia/Ho_Chi_Minh'), ('BBB', 'B', 'B', 'VN', 'Asia/Ho_Chi_Minh')").update();
		jdbc.sql("INSERT INTO airlines (code, name, ticket_prefix) VALUES ('ZZ', 'Test Air', '999')").update();
		long flightId = jdbc.sql("""
				INSERT INTO flights (airline_code, flight_number, departure_airport, arrival_airport, departure_time, arrival_time)
				VALUES ('ZZ', 'ZZ1', 'AAA', 'BBB', now() + interval '1 day', now() + interval '1 day 2 hours')
				RETURNING id
				""").query(Long.class).single();

		assertThatThrownBy(() -> jdbc.sql("INSERT INTO flight_cabins (flight_id, cabin_class, total_seats, available_seats) VALUES (?, 'ECONOMY', 10, 11)")
				.param(flightId).update())
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
