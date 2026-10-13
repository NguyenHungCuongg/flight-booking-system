package vn.edu.uit.flightbooking.booking.domain;

import java.util.ArrayList;
import java.util.List;

import vn.edu.uit.flightbooking.flight.FlightSearch;

/** BR-21 cho từng vé và BR-33, logic thuần (TDD §5.2). */
final class Pricing {

	private Pricing() {
	}

	/** BR-21: giá vé của một khách trên một chặng, làm tròn half-up như tìm kiếm. Plan 11 ghi vào {@code tickets}. */
	static long ticketPrice(long adultPrice, PassengerType type, int childPercent, int infantPercent) {
		return switch (type) {
			case ADULT -> adultPrice;
			case CHILD -> FlightSearch.percentOf(adultPrice, childPercent);
			case INFANT -> FlightSearch.percentOf(adultPrice, infantPercent);
		};
	}

	/**
	 * BR-33: chia {@code discount} theo tỉ lệ tạm tính từng chiều, làm tròn xuống; chiều cuối nhận phần còn lại.
	 * {@code subtotals} không rỗng và có tổng dương (giá vé luôn lớn hơn 0).
	 */
	static List<Long> allocate(long discount, List<Long> subtotals) {
		long total = subtotals.stream().mapToLong(Long::longValue).sum();
		List<Long> shares = new ArrayList<>();
		long given = 0;
		for (int i = 0; i < subtotals.size() - 1; i++) {
			// ponytail: multiplyExact báo lỗi thay vì tràn số âm thầm; tạm tính thực tế vài tỉ đồng còn xa ngưỡng long.
			long share = Math.multiplyExact(discount, subtotals.get(i)) / total;
			shares.add(share);
			given += share;
		}
		shares.add(discount - given);
		return shares;
	}

}
