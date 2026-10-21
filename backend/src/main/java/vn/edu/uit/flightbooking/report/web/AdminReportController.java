package vn.edu.uit.flightbooking.report.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.report.domain.CashFlowGroupBy;
import vn.edu.uit.flightbooking.report.domain.CashFlowReport;
import vn.edu.uit.flightbooking.report.domain.LoadFactorReport;
import vn.edu.uit.flightbooking.report.domain.ReportService;
import vn.edu.uit.flightbooking.report.domain.SalesGroupBy;
import vn.edu.uit.flightbooking.report.domain.SalesReport;

/**
 * Màn hình A-10 Báo cáo (FR-120–122, BR-110–112).
 * Phân quyền: ROLE_ADMIN qua SecurityConfig ("/api/admin/**").
 */
@RestController
@RequestMapping("/api/admin/reports")
class AdminReportController {

	private final ReportService reports;

	AdminReportController(ReportService reports) {
		this.reports = reports;
	}

	@GetMapping("/cash-flow")
	CashFlowReport cashFlow(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam CashFlowGroupBy groupBy) {
		return reports.getCashFlow(from, to, groupBy);
	}

	@GetMapping("/sales")
	SalesReport sales(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam SalesGroupBy groupBy) {
		return reports.getSales(from, to, groupBy);
	}

	@GetMapping("/load-factor")
	LoadFactorReport loadFactor(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String airline) {
		return reports.getLoadFactor(from, to, airline);
	}

}
