package vn.edu.uit.flightbooking.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** BR-21 (từng vé) và BR-33: logic thuần, không cần Spring. */
class PricingTest {

	/** Ví dụ TDD §8.8: chặng 1.870.000, trẻ em 90%, em bé 10%. */
	@Test
	void ticketPriceFollowsPassengerType() {
		assertThat(Pricing.ticketPrice(1_870_000, PassengerType.ADULT, 90, 10)).isEqualTo(1_870_000);
		assertThat(Pricing.ticketPrice(1_870_000, PassengerType.CHILD, 90, 10)).isEqualTo(1_683_000);
		assertThat(Pricing.ticketPrice(1_870_000, PassengerType.INFANT, 90, 10)).isEqualTo(187_000);
		// 4,5 → 5: half-up như tìm kiếm.
		assertThat(Pricing.ticketPrice(5, PassengerType.CHILD, 90, 10)).isEqualTo(5);
	}

	/** Ví dụ TDD §8.8: ⌊200.000 × 3.672.000 / 6.048.000⌋ = 121.428, chiều cuối nhận 78.572. */
	@Test
	void discountIsSplitByJourneySubtotalAndLastTakesTheRest() {
		assertThat(Pricing.allocate(200_000, List.of(3_672_000L, 2_376_000L))).containsExactly(121_428L, 78_572L);
		assertThat(Pricing.allocate(200_000, List.of(6_048_000L))).containsExactly(200_000L);
		assertThat(Pricing.allocate(0, List.of(3_672_000L, 2_376_000L))).containsExactly(0L, 0L);
		assertThat(Pricing.allocate(100, List.of(1L, 1L, 1L))).containsExactly(33L, 33L, 34L);
		// Giảm 100%: mỗi chiều được giảm đúng bằng tạm tính của chiều.
		assertThat(Pricing.allocate(6_048_000, List.of(3_672_000L, 2_376_000L)))
			.containsExactly(3_672_000L, 2_376_000L);
	}

}
