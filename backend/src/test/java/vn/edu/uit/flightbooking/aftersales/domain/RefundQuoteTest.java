package vn.edu.uit.flightbooking.aftersales.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefundQuoteTest {

	/** BR-53: phí theo khách chiếm ghế; BR-61: không âm. */
	@Test
	void feeCountsSeatedPassengersAndRefundIsNeverNegative() {
		assertThat(RefundQuote.of(3_020_000, 300_000, 1)).isEqualTo(new RefundQuote(3_020_000, 300_000, 2_720_000));
		assertThat(RefundQuote.of(6_140_000, 300_000, 2)).isEqualTo(new RefundQuote(6_140_000, 600_000, 5_540_000));
		assertThat(RefundQuote.of(200_000, 300_000, 1)).isEqualTo(new RefundQuote(200_000, 300_000, 0));
	}

}
