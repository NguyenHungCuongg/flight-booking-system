package vn.edu.uit.flightbooking.report.domain;

import java.util.List;

public record LoadFactorReport(List<LoadFactorItem> items, long totalSeats, long occupiedSeats,
		double averageLoadFactorPercent) {

	public static LoadFactorReport of(List<LoadFactorItem> items) {
		long totalSeats = items.stream().mapToLong(LoadFactorItem::totalSeats).sum();
		long occupiedSeats = items.stream().mapToLong(LoadFactorItem::occupiedSeats).sum();
		double avg = totalSeats > 0 ? Math.round(1000.0 * occupiedSeats / totalSeats) / 10.0 : 0.0;
		return new LoadFactorReport(items, totalSeats, occupiedSeats, avg);
	}

}
