package vn.edu.uit.flightbooking.report.domain;

import java.util.List;

public record CashFlowReport(List<CashFlowItem> items, long totalCharged, long totalRefunded, long netRevenue) {

	public static CashFlowReport of(List<CashFlowItem> items) {
		long totalCharged = items.stream().mapToLong(CashFlowItem::charged).sum();
		long totalRefunded = items.stream().mapToLong(CashFlowItem::refunded).sum();
		long netRevenue = totalCharged - totalRefunded;
		return new CashFlowReport(items, totalCharged, totalRefunded, netRevenue);
	}

}
