package vn.edu.uit.flightbooking.flight;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** BR-21, TDD §5.2: unit test, không cần Spring. */
class TicketPriceTest {

	/** Ví dụ TDD §8.8: 2 người lớn, 1 trẻ em, 1 em bé; hai chặng 1.870.000 và 1.150.000. */
	@Test
	void matchesTddExample() {
		assertThat(FlightSearch.totalPrice(List.of(1_870_000L, 1_150_000L), 2, 1, 1, 90, 10)).isEqualTo(9_060_000);
	}

	@Test
	void roundsHalfUpToTheDong() {
		assertThat(FlightSearch.percentOf(5, 90)).isEqualTo(5); // 4,5 → 5
		assertThat(FlightSearch.percentOf(5, 10)).isEqualTo(1); // 0,5 → 1
		assertThat(FlightSearch.percentOf(14, 10)).isEqualTo(1); // 1,4 → 1
		assertThat(FlightSearch.percentOf(1_000_000, 0)).isZero();
		assertThat(FlightSearch.percentOf(1_000_000, 100)).isEqualTo(1_000_000);
	}

	/** Làm tròn trên từng chặng rồi mới cộng: 2 × round(4,5) = 10, không phải round(9) = 9. */
	@Test
	void roundsEachLegBeforeSumming() {
		assertThat(FlightSearch.totalPrice(List.of(5L, 5L), 0, 1, 0, 90, 10)).isEqualTo(10);
	}

}
