package vn.edu.uit.flightbooking.report.domain;

public enum CashFlowGroupBy {
	DAY, MONTH, YEAR;

	public String unit() {
		return switch (this) {
			case DAY -> "day";
			case MONTH -> "month";
			case YEAR -> "year";
		};
	}
}
