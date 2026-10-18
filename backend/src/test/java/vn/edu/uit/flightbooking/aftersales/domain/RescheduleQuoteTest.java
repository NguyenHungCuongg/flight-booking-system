package vn.edu.uit.flightbooking.aftersales.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RescheduleQuoteTest {

	/** BR-72, BR-53: phí theo khách chiếm ghế; chuyến mới rẻ hơn thì chỉ trả phí. */
	@Test
	void amountDueIsFeePlusPositiveDifference() {
		assertThat(RescheduleQuote.of(2_160_000, 2_500_000, 200_000, 1))
			.isEqualTo(new RescheduleQuote(2_160_000, 2_500_000, 200_000, 540_000));
		assertThat(RescheduleQuote.of(2_160_000, 1_900_000, 200_000, 1))
			.isEqualTo(new RescheduleQuote(2_160_000, 1_900_000, 200_000, 200_000));
		assertThat(RescheduleQuote.of(4_320_000, 5_000_000, 200_000, 2))
			.isEqualTo(new RescheduleQuote(4_320_000, 5_000_000, 400_000, 1_080_000));
		assertThat(RescheduleQuote.of(2_160_000, 1_900_000, 0, 1).amountDue()).isZero();
	}

}
