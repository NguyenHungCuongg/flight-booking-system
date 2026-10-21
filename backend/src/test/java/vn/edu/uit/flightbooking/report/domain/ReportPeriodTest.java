package vn.edu.uit.flightbooking.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

class ReportPeriodTest {

	private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

	@Test
	void convertsDateRangeToInstantHalfOpenIntervalInVietnamTime() {
		LocalDate from = LocalDate.of(2026, 10, 1);
		LocalDate to = LocalDate.of(2026, 10, 31);

		ReportPeriod period = ReportPeriod.of(from, to);

		Instant expectedStart = from.atStartOfDay(VIETNAM).toInstant();
		Instant expectedEnd = to.plusDays(1).atStartOfDay(VIETNAM).toInstant();

		assertThat(period.from()).isEqualTo(expectedStart);
		assertThat(period.to()).isEqualTo(expectedEnd);
	}

	@Test
	void singleDayRangeSpansExactly24HoursInVietnamTime() {
		LocalDate day = LocalDate.of(2026, 10, 15);

		ReportPeriod period = ReportPeriod.of(day, day);

		assertThat(period.to()).isEqualTo(period.from().plusSeconds(86400));
	}

	@Test
	void rejectsEndDateBeforeStartDate() {
		LocalDate from = LocalDate.of(2026, 10, 20);
		LocalDate to = LocalDate.of(2026, 10, 10);

		assertThatThrownBy(() -> ReportPeriod.of(from, to))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				BusinessException be = (BusinessException) e;
				assertThat(be.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
				assertThat(be.getMessage()).contains("Dữ liệu không hợp lệ");
				assertThat(be.properties()).containsKey("errors");
			});
	}

	@Test
	void rejectsNullDates() {
		assertThatThrownBy(() -> ReportPeriod.of(null, LocalDate.now()))
			.isInstanceOf(BusinessException.class);

		assertThatThrownBy(() -> ReportPeriod.of(LocalDate.now(), null))
			.isInstanceOf(BusinessException.class);
	}

}
