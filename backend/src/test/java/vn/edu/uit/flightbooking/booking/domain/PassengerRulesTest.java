package vn.edu.uit.flightbooking.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static vn.edu.uit.flightbooking.booking.domain.PassengerType.ADULT;
import static vn.edu.uit.flightbooking.booking.domain.PassengerType.CHILD;
import static vn.edu.uit.flightbooking.booking.domain.PassengerType.INFANT;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import vn.edu.uit.flightbooking.booking.domain.BookingRequest.PassengerRequest;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/** BR-10, BR-11, BR-13: logic thuần, không cần Spring. */
class PassengerRulesTest {

	static final LocalDate FLIGHT = LocalDate.of(2026, 12, 1);

	static final LocalDate ADULT_BORN = LocalDate.of(1990, 5, 20);

	static PassengerRequest passenger(PassengerType type, LocalDate dateOfBirth) {
		return new PassengerRequest(type, "NGUYEN", "VAN AN", Gender.MALE, dateOfBirth, null, null, null);
	}

	static PassengerRequest withPassport(LocalDate expiry) {
		return new PassengerRequest(ADULT, "NGUYEN", "VAN AN", Gender.MALE, ADULT_BORN, "VN", "C1234567", expiry);
	}

	/** Các trường trong {@code errors} của PASSENGER_RULES_VIOLATED; test hỏng nếu không có lỗi hoặc sai mã. */
	@SuppressWarnings("unchecked")
	static List<String> fieldsOf(ThrowingCallable call) {
		Throwable thrown = catchThrowable(call);
		assertThat(thrown).isInstanceOf(BusinessException.class);
		BusinessException e = (BusinessException) thrown;
		assertThat(e.code()).isEqualTo(ErrorCode.PASSENGER_RULES_VIOLATED);
		return ((List<Map<String, String>>) e.properties().get("errors")).stream().map(m -> m.get("field")).toList();
	}

	/** Tuổi tròn năm: đúng sinh nhật thứ 12 là người lớn, thứ 2 là trẻ em; sinh đúng ngày bay là em bé. */
	@Test
	void ageIsCountedInWholeYearsOnFlightDate() {
		assertThat(PassengerRules.typeAt(LocalDate.of(2014, 12, 1), FLIGHT)).isEqualTo(ADULT);
		assertThat(PassengerRules.typeAt(LocalDate.of(2014, 12, 2), FLIGHT)).isEqualTo(CHILD);
		assertThat(PassengerRules.typeAt(LocalDate.of(2024, 12, 1), FLIGHT)).isEqualTo(CHILD);
		assertThat(PassengerRules.typeAt(LocalDate.of(2024, 12, 2), FLIGHT)).isEqualTo(INFANT);
		assertThat(PassengerRules.typeAt(FLIGHT, FLIGHT)).isEqualTo(INFANT);
	}

	/** BR-10: loại khách khớp tuổi, ngày sinh không sau ngày bay; mọi lỗi trả một lần, đúng vị trí khách. */
	@Test
	void declaredTypeMustMatchAgeAndBirthCannotFollowFlight() {
		PassengerRules.checkDetails(List.of(passenger(ADULT, ADULT_BORN), passenger(INFANT, FLIGHT)), FLIGHT, FLIGHT,
				false);

		assertThat(fieldsOf(() -> PassengerRules.checkDetails(List.of(passenger(ADULT, ADULT_BORN),
				passenger(CHILD, LocalDate.of(2024, 12, 2)), passenger(INFANT, FLIGHT.plusDays(1))), FLIGHT, FLIGHT,
				false)))
			.containsExactly("passengers[1].dateOfBirth", "passengers[2].dateOfBirth");
	}

	/** BR-11: ít nhất 1 người lớn, em bé không nhiều hơn người lớn, em bé không chiếm ghế. */
	@Test
	void countsNeedAnAdultFewerInfantsAndSeatLimit() {
		PassengerRequest adult = passenger(ADULT, ADULT_BORN);
		PassengerRequest child = passenger(CHILD, LocalDate.of(2020, 1, 1));
		PassengerRequest infant = passenger(INFANT, LocalDate.of(2026, 1, 1));

		PassengerRules.checkCounts(List.of(adult, adult, infant, infant), 2);
		assertThat(fieldsOf(() -> PassengerRules.checkCounts(List.of(child), 9))).containsExactly("passengers");
		assertThat(fieldsOf(() -> PassengerRules.checkCounts(List.of(adult, infant, infant), 9)))
			.containsExactly("passengers");
		assertThat(fieldsOf(() -> PassengerRules.checkCounts(List.of(adult, adult, child), 2)))
			.containsExactly("passengers");
	}

	/** BR-13: hộ chiếu còn hạn tới ít nhất ngày bay chặng cuối + 6 tháng (tính cả biên); nội địa thì không cần. */
	@Test
	void internationalTripNeedsPassportValidSixMonthsAfterLastDeparture() {
		LocalDate last = FLIGHT.plusDays(5);

		PassengerRules.checkDetails(List.of(withPassport(last.plusMonths(6))), FLIGHT, last, true);
		assertThat(fieldsOf(() -> PassengerRules.checkDetails(List.of(withPassport(last.plusMonths(6).minusDays(1))),
				FLIGHT, last, true)))
			.containsExactly("passengers[0].passportExpiry");
		assertThat(fieldsOf(
				() -> PassengerRules.checkDetails(List.of(passenger(ADULT, ADULT_BORN)), FLIGHT, last, true)))
			.containsExactly("passengers[0].nationality", "passengers[0].passportNumber",
					"passengers[0].passportExpiry");
		PassengerRules.checkDetails(List.of(passenger(ADULT, ADULT_BORN)), FLIGHT, last, false);
	}

}
