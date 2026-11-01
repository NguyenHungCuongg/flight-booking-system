package vn.edu.uit.flightbooking.report.domain;

import java.util.List;

public record SalesReport(List<SalesItem> items, long totalTickets, long totalRevenue) {

	public static SalesReport of(List<SalesItem> items) {
		long totalTickets = items.stream().mapToLong(SalesItem::ticketCount).sum();
		long totalRevenue = items.stream().mapToLong(SalesItem::revenue).sum();
		return new SalesReport(items, totalTickets, totalRevenue);
	}

}
