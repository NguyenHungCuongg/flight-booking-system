package vn.edu.uit.flightbooking.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** BR-46: logic thuần, không cần Spring. */
class BookingCodesTest {

	@Test
	void codesUseSixUnambiguousCharacters() {
		Set<String> codes = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			codes.add(BookingCodes.next());
		}

		assertThat(codes).allMatch(code -> code.matches("[A-HJ-NP-Z2-9]{6}"));
		// 32^6 ≈ 1 tỉ mã: 1.000 mã ngẫu nhiên gần như không trùng nhau.
		assertThat(codes).hasSizeGreaterThan(990);
		assertThat(BookingCodes.ALPHABET).hasSize(32).doesNotContain("0", "O", "1", "I");
	}

}
