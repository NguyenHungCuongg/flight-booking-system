package vn.edu.uit.flightbooking.booking.domain;

import java.util.List;

import vn.edu.uit.flightbooking.flight.FlightApi;

/**
 * Kết quả báo giá (FR-20, TDD §6.2 bước 1–4). Giữ chỗ dùng lại để ghi booking và chốt giá (BR-24).
 * {@code voucherId} là {@code null} khi không dùng voucher. {@code totalAmount = fareTotal + baggageTotal −
 * discountTotal} (BR-23). {@code childPercent}, {@code infantPercent}: tỉ lệ đã dùng để tính giá (BR-21), để giữ chỗ
 * tính giá từng vé ({@code tickets.fare_amount}) bằng đúng hai số đó.
 */
public record Quote(List<JourneyQuote> journeys, Long voucherId, long fareTotal, long baggageTotal,
		long discountTotal, long totalAmount, int childPercent, int infantPercent) {

	/**
	 * {@code itinerary}: hành trình đã kiểm tra, kèm giá người lớn từng chặng và quy định gói giá. {@code discount}:
	 * phần mức giảm của chiều (BR-33).
	 */
	public record JourneyQuote(Direction direction, FlightApi.PricedJourney itinerary,
			List<PassengerFare> passengerFares, List<BaggageLine> baggage, long fareTotal, long baggageTotal,
			long discount) {
	}

	/** Giá vé của một loại khách trên cả chiều (BR-21): {@code unitPrice} cho một khách, {@code total} cho cả nhóm. */
	public record PassengerFare(PassengerType type, int count, long unitPrice, long total) {
	}

	/** Một mức hành lý mua thêm, kèm cân nặng và giá tại lúc báo giá (BR-22, BR-24). */
	public record BaggageLine(int passengerIndex, long baggageOptionId, int weightKg, long price) {
	}

}
