package vn.edu.uit.flightbooking.report.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import vn.edu.uit.flightbooking.common.BusinessException;

public record ReportPeriod(Instant from, Instant to) {

	public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

	public static ReportPeriod of(LocalDate fromDate, LocalDate toDate) {
		if (fromDate == null) {
			throw BusinessException.invalidField("from", "Ngày bắt đầu không được để trống");
		}
		if (toDate == null) {
			throw BusinessException.invalidField("to", "Ngày kết thúc không được để trống");
		}
		if (toDate.isBefore(fromDate)) {
			throw BusinessException.invalidField("to", "Ngày kết thúc không được trước ngày bắt đầu");
		}
		Instant start = fromDate.atStartOfDay(VIETNAM).toInstant();
		Instant end = toDate.plusDays(1).atStartOfDay(VIETNAM).toInstant();
		return new ReportPeriod(start, end);
	}

}
