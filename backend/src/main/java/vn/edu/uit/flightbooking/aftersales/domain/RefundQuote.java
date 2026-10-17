package vn.edu.uit.flightbooking.aftersales.domain;

/** FR-50: giá trị chiều, phí hoàn và số tiền khách nhận lại. */
public record RefundQuote(long journeyValue, long fee, long refundAmount) {

	/** BR-53: phí tính theo số khách chiếm ghế; BR-61: số hoàn không âm. */
	public static RefundQuote of(long journeyValue, long refundFee, int seated) {
		long fee = refundFee * seated;
		return new RefundQuote(journeyValue, fee, Math.max(0, journeyValue - fee));
	}

}
