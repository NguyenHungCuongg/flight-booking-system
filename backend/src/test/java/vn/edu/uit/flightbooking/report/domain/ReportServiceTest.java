package vn.edu.uit.flightbooking.report.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.report.TestReports;

@IntegrationTest
@Transactional
class ReportServiceTest {

	private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

	@Autowired
	ReportService reportService;

	@Autowired
	JdbcClient jdbc;

	@Test
	void calculatesCashFlowAccuratelyGroupedByDay() {
		LocalDate day1 = LocalDate.of(2026, 10, 1);
		LocalDate day2 = LocalDate.of(2026, 10, 2);

		Instant t1 = day1.atTime(10, 0).atZone(VIETNAM).toInstant();
		Instant t2 = day1.atTime(15, 30).atZone(VIETNAM).toInstant();
		Instant t3 = day2.atTime(9, 0).atZone(VIETNAM).toInstant();

		TestReports.insertPayment(jdbc, "CHARGE", "SUCCESS", 2_000_000, t1);
		TestReports.insertPayment(jdbc, "REFUND", "SUCCESS", 500_000, t2);
		TestReports.insertPayment(jdbc, "CHARGE", "SUCCESS", 3_000_000, t3);
		TestReports.insertPayment(jdbc, "CHARGE", "PENDING", 10_000_000, t1); // Không tính PENDING
		TestReports.insertPayment(jdbc, "CHARGE", "FAILED", 5_000_000, t1); // Không tính FAILED

		CashFlowReport report = reportService.getCashFlow(day1, day2, CashFlowGroupBy.DAY);

		assertThat(report.totalCharged()).isEqualTo(5_000_000);
		assertThat(report.totalRefunded()).isEqualTo(500_000);
		assertThat(report.netRevenue()).isEqualTo(4_500_000);

		assertThat(report.items()).hasSize(2);
		CashFlowItem item1 = report.items().get(0);
		assertThat(item1.period()).isEqualTo(day1);
		assertThat(item1.charged()).isEqualTo(2_000_000);
		assertThat(item1.refunded()).isEqualTo(500_000);
		assertThat(item1.net()).isEqualTo(1_500_000);

		CashFlowItem item2 = report.items().get(1);
		assertThat(item2.period()).isEqualTo(day2);
		assertThat(item2.charged()).isEqualTo(3_000_000);
		assertThat(item2.refunded()).isZero();
		assertThat(item2.net()).isEqualTo(3_000_000);
	}

	@Test
	void calculatesSalesGroupedByAirlineAndRoute() {
		LocalDate from = LocalDate.of(2026, 10, 1);
		LocalDate to = LocalDate.of(2026, 10, 5);
		Instant issueTime = from.atTime(8, 0).atZone(VIETNAM).toInstant();

		TestReports.insertAirline(jdbc, "QA", "Hãng Hàng Không A");
		TestReports.insertAirline(jdbc, "QB", "Hãng Hàng Không B");
		TestReports.insertAirport(jdbc, "QAA", "Sân bay AA", "Hà Nội");
		TestReports.insertAirport(jdbc, "QBB", "Sân bay BB", "TP. Hồ Chí Minh");

		long flight1 = TestReports.insertFlight(jdbc, "QA101", "QA", "QAA", "QBB", issueTime.plusSeconds(86400), "SCHEDULED");
		long flight2 = TestReports.insertFlight(jdbc, "QB201", "QB", "QBB", "QAA", issueTime.plusSeconds(86400), "SCHEDULED");

		long booking = TestReports.insertBooking(jdbc, "BKREPSAL01", 3_000_000);
		long p1 = TestReports.insertPassenger(jdbc, booking, "NGUYEN", "VAN A");
		long p2 = TestReports.insertPassenger(jdbc, booking, "TRAN", "THI B");

		long seg1 = TestReports.insertSegment(jdbc, booking, flight1, "ACTIVE");
		long seg2 = TestReports.insertSegment(jdbc, booking, flight2, "ACTIVE");
		long segCancelled = TestReports.insertSegment(jdbc, booking, flight1, "CANCELLED"); // Không tính

		TestReports.insertTicket(jdbc, booking, seg1, p1, "9990000000001", 1_200_000, issueTime);
		TestReports.insertTicket(jdbc, booking, seg2, p2, "9990000000002", 1_800_000, issueTime);
		TestReports.insertTicket(jdbc, booking, segCancelled, p1, "9990000000003", 500_000, issueTime);

		// 1. Nhóm theo hãng
		SalesReport byAirline = reportService.getSales(from, to, SalesGroupBy.AIRLINE);
		assertThat(byAirline.totalTickets()).isEqualTo(2);
		assertThat(byAirline.totalRevenue()).isEqualTo(3_000_000);
		assertThat(byAirline.items()).hasSize(2);
		assertThat(byAirline.items().get(0).groupKey()).isEqualTo("QB");
		assertThat(byAirline.items().get(0).revenue()).isEqualTo(1_800_000);
		assertThat(byAirline.items().get(1).groupKey()).isEqualTo("QA");
		assertThat(byAirline.items().get(1).revenue()).isEqualTo(1_200_000);

		// 2. Nhóm theo tuyến
		SalesReport byRoute = reportService.getSales(from, to, SalesGroupBy.ROUTE);
		assertThat(byRoute.totalTickets()).isEqualTo(2);
		assertThat(byRoute.totalRevenue()).isEqualTo(3_000_000);
		assertThat(byRoute.items().get(0).groupKey()).isEqualTo("QBB-QAA");
		assertThat(byRoute.items().get(1).groupKey()).isEqualTo("QAA-QBB");
	}

	@Test
	void calculatesLoadFactorRespectingOccupiedSeatsAndAirlineFilter() {
		LocalDate from = LocalDate.of(2026, 11, 1);
		LocalDate to = LocalDate.of(2026, 11, 2);
		Instant dep1 = from.atTime(6, 0).atZone(VIETNAM).toInstant();
		Instant dep2 = from.atTime(12, 0).atZone(VIETNAM).toInstant();

		TestReports.insertAirline(jdbc, "QC", "Hãng Hàng Không C");
		TestReports.insertAirline(jdbc, "QD", "Hãng Hàng Không D");
		TestReports.insertAirport(jdbc, "QCC", "Sân bay CC", "Đà Nẵng");
		TestReports.insertAirport(jdbc, "QDD", "Sân bay DD", "Hải Phòng");

		long f1 = TestReports.insertFlight(jdbc, "QC101", "QC", "QCC", "QDD", dep1, "SCHEDULED");
		TestReports.insertCabin(jdbc, f1, "ECONOMY", 100, 20); // Đã lấp 80/100 = 80.0%

		long f2 = TestReports.insertFlight(jdbc, "QD201", "QD", "QDD", "QCC", dep2, "SCHEDULED");
		TestReports.insertCabin(jdbc, f2, "BUSINESS", 20, 10); // Đã lấp 10/20 = 50.0%

		// Lọc toàn bộ
		LoadFactorReport all = reportService.getLoadFactor(from, to, null);
		assertThat(all.items()).hasSize(2);
		assertThat(all.totalSeats()).isEqualTo(120);
		assertThat(all.occupiedSeats()).isEqualTo(90);
		assertThat(all.averageLoadFactorPercent()).isEqualTo(75.0); // 90 / 120 = 75.0%

		assertThat(all.items().get(0).flightNumber()).isEqualTo("QC101");
		assertThat(all.items().get(0).loadFactorPercent()).isEqualTo(80.0);

		// Lọc theo hãng QC
		LoadFactorReport qcOnly = reportService.getLoadFactor(from, to, "QC");
		assertThat(qcOnly.items()).hasSize(1);
		assertThat(qcOnly.items().get(0).flightNumber()).isEqualTo("QC101");
		assertThat(qcOnly.totalSeats()).isEqualTo(100);
		assertThat(qcOnly.occupiedSeats()).isEqualTo(80);
		assertThat(qcOnly.averageLoadFactorPercent()).isEqualTo(80.0);
	}

}
