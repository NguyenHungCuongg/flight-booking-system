package vn.edu.uit.flightbooking.report.domain;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportService {

	private final JdbcClient jdbc;

	public ReportService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public CashFlowReport getCashFlow(LocalDate from, LocalDate to, CashFlowGroupBy groupBy) {
		ReportPeriod period = ReportPeriod.of(from, to);
		String sql = """
				SELECT date_trunc(cast(:unit as text), completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS period,
				       COALESCE(SUM(amount) FILTER (WHERE kind = 'CHARGE'), 0)                             AS charged,
				       COALESCE(SUM(amount) FILTER (WHERE kind = 'REFUND'), 0)                             AS refunded,
				       COALESCE(SUM(CASE WHEN kind = 'CHARGE' THEN amount ELSE -amount END), 0)          AS net
				FROM payments
				WHERE status = 'SUCCESS'
				  AND completed_at >= :from AND completed_at < :to
				GROUP BY period
				ORDER BY period ASC
				""";
		List<CashFlowItem> items = jdbc.sql(sql)
			.param("unit", groupBy.unit())
			.param("from", period.from().atOffset(ZoneOffset.UTC))
			.param("to", period.to().atOffset(ZoneOffset.UTC))
			.query(ReportService::mapCashFlow)
			.list();
		return CashFlowReport.of(items);
	}

	public SalesReport getSales(LocalDate from, LocalDate to, SalesGroupBy groupBy) {
		ReportPeriod period = ReportPeriod.of(from, to);
		String sql = switch (groupBy) {
			case AIRLINE -> """
					SELECT f.airline_code AS group_key,
					       a.name         AS group_name,
					       COUNT(*)       AS tickets,
					       COALESCE(SUM(t.fare_amount), 0) AS revenue
					FROM tickets t
					JOIN booking_segments s ON s.id = t.segment_id AND s.status = 'ACTIVE'
					JOIN flights f          ON f.id = s.flight_id
					JOIN airlines a         ON a.code = f.airline_code
					WHERE t.issued_at >= :from AND t.issued_at < :to
					GROUP BY f.airline_code, a.name
					ORDER BY revenue DESC, tickets DESC
					""";
			case ROUTE -> """
					SELECT f.departure_airport || '-' || f.arrival_airport AS group_key,
					       f.departure_airport || ' → ' || f.arrival_airport AS group_name,
					       COUNT(*)           AS tickets,
					       COALESCE(SUM(t.fare_amount), 0) AS revenue
					FROM tickets t
					JOIN booking_segments s ON s.id = t.segment_id AND s.status = 'ACTIVE'
					JOIN flights f          ON f.id = s.flight_id
					WHERE t.issued_at >= :from AND t.issued_at < :to
					GROUP BY f.departure_airport, f.arrival_airport
					ORDER BY revenue DESC, tickets DESC
					""";
		};
		List<SalesItem> items = jdbc.sql(sql)
			.param("from", period.from().atOffset(ZoneOffset.UTC))
			.param("to", period.to().atOffset(ZoneOffset.UTC))
			.query(ReportService::mapSales)
			.list();
		return SalesReport.of(items);
	}

	public LoadFactorReport getLoadFactor(LocalDate from, LocalDate to, String airline) {
		ReportPeriod period = ReportPeriod.of(from, to);
		String normalizedAirline = (airline != null && !airline.isBlank()) ? airline.strip().toUpperCase() : null;
		String sql = """
				SELECT f.id                                           AS flight_id,
				       f.flight_number                                AS flight_number,
				       f.airline_code                                 AS airline_code,
				       f.departure_airport                            AS departure_airport,
				       f.arrival_airport                              AS arrival_airport,
				       f.departure_time                               AS departure_time,
				       COALESCE(SUM(c.total_seats), 0)                AS total_seats,
				       COALESCE(SUM(c.total_seats - c.available_seats), 0) AS occupied_seats,
				       CASE
				           WHEN COALESCE(SUM(c.total_seats), 0) = 0 THEN 0.0
				           ELSE ROUND(100.0 * SUM(c.total_seats - c.available_seats) / SUM(c.total_seats), 1)
				       END AS load_factor_percent
				FROM flights f
				LEFT JOIN flight_cabins c ON c.flight_id = f.id
				WHERE f.status = 'SCHEDULED'
				  AND f.departure_time >= :from AND f.departure_time < :to
				  AND (cast(:airline as text) IS NULL OR f.airline_code = :airline)
				GROUP BY f.id, f.flight_number, f.airline_code, f.departure_airport, f.arrival_airport, f.departure_time
				ORDER BY f.departure_time ASC, f.id ASC
				""";
		List<LoadFactorItem> items = jdbc.sql(sql)
			.param("from", period.from().atOffset(ZoneOffset.UTC))
			.param("to", period.to().atOffset(ZoneOffset.UTC))
			.param("airline", normalizedAirline)
			.query(ReportService::mapLoadFactor)
			.list();
		return LoadFactorReport.of(items);
	}

	private static CashFlowItem mapCashFlow(ResultSet rs, int rowNum) throws SQLException {
		LocalDate period = rs.getObject("period", LocalDate.class);
		long charged = rs.getLong("charged");
		long refunded = rs.getLong("refunded");
		long net = rs.getLong("net");
		return new CashFlowItem(period, charged, refunded, net);
	}

	private static SalesItem mapSales(ResultSet rs, int rowNum) throws SQLException {
		String groupKey = rs.getString("group_key");
		String groupName = rs.getString("group_name");
		long ticketCount = rs.getLong("tickets");
		long revenue = rs.getLong("revenue");
		return new SalesItem(groupKey, groupName, ticketCount, revenue);
	}

	private static LoadFactorItem mapLoadFactor(ResultSet rs, int rowNum) throws SQLException {
		long flightId = rs.getLong("flight_id");
		String flightNumber = rs.getString("flight_number");
		String airlineCode = rs.getString("airline_code");
		String departureAirport = rs.getString("departure_airport");
		String arrivalAirport = rs.getString("arrival_airport");
		OffsetDateTime departureTime = rs.getTimestamp("departure_time")
			.toInstant()
			.atZone(ReportPeriod.VIETNAM)
			.toOffsetDateTime();
		int totalSeats = rs.getInt("total_seats");
		int occupiedSeats = rs.getInt("occupied_seats");
		double loadFactorPercent = rs.getDouble("load_factor_percent");
		return new LoadFactorItem(flightId, flightNumber, airlineCode, departureAirport, arrivalAirport,
				departureTime, totalSeats, occupiedSeats, loadFactorPercent);
	}

}
