package vn.edu.uit.flightbooking.aftersales.domain;

/** FR-61: tiền vé cũ, vé mới, phí đổi và số tiền khách phải trả thêm. */
public record RescheduleQuote(long oldFareTotal, long newFareTotal, long changeFeeTotal, long amountDue) {

	/** BR-53: phí tính theo số khách chiếm ghế; BR-72: chuyến mới rẻ hơn thì chỉ trả phí, không hoàn chênh lệch. */
	public static RescheduleQuote of(long oldFareTotal, long newFareTotal, long changeFee, int seated) {
		long feeTotal = changeFee * seated;
		return new RescheduleQuote(oldFareTotal, newFareTotal, feeTotal, feeTotal + Math.max(0, newFareTotal - oldFareTotal));
	}

}
