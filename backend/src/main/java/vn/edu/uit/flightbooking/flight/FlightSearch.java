package vn.edu.uit.flightbooking.flight;

import java.util.List;

/** Tìm hành trình một chiều (FR-11). Task 2 thêm phần tìm kiếm. */
public class FlightSearch {

	/**
	 * BR-21, TDD §5.2: giá vé của từng khách trên từng chặng làm tròn half-up đến đồng, rồi mới cộng. Báo giá (Plan 10)
	 * dùng lại hàm này.
	 */
	public static long totalPrice(List<Long> adultLegPrices, int adults, int children, int infants, int childPercent,
			int infantPercent) {
		return adultLegPrices.stream()
			.mapToLong(price -> adults * price + children * percentOf(price, childPercent)
					+ infants * percentOf(price, infantPercent))
			.sum();
	}

	/** Làm tròn half-up, đúng với số không âm (TDD §5.2). */
	static long percentOf(long amount, int percent) {
		return (amount * percent + 50) / 100;
	}

}
