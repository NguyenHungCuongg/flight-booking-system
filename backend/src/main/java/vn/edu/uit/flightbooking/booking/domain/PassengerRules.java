package vn.edu.uit.flightbooking.booking.domain;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

import vn.edu.uit.flightbooking.booking.domain.BookingRequest.PassengerRequest;
import vn.edu.uit.flightbooking.common.ErrorCode;

/**
 * BR-10, BR-11, BR-13 (TDD §6.2 bước 3). Định dạng tên và hộ chiếu (BR-12, BR-13) do Bean Validation ở
 * {@link BookingRequest} kiểm tra. Mọi vi phạm báo một lần: PASSENGER_RULES_VIOLATED kèm {@code errors}.
 */
final class PassengerRules {

	static final String DETAIL = "Thông tin hành khách chưa hợp lệ";

	private PassengerRules() {
	}

	/** BR-11. Chạy trước khi kiểm tra hành trình, vì số ghế cần kiểm tra (BR-06) phụ thuộc số khách. */
	static void checkCounts(List<PassengerRequest> passengers, int maxSeated) {
		long adults = count(passengers, PassengerType.ADULT);
		long infants = count(passengers, PassengerType.INFANT);
		long seated = passengers.size() - infants;
		FieldErrors errors = new FieldErrors();
		if (adults == 0) {
			errors.add("passengers", "Cần ít nhất 1 người lớn");
		}
		if (infants > adults) {
			errors.add("passengers", "Số em bé không được nhiều hơn số người lớn");
		}
		if (seated > maxSeated) {
			errors.add("passengers", "Tối đa %d khách chiếm ghế (người lớn và trẻ em)".formatted(maxSeated));
		}
		errors.throwIfAny(ErrorCode.PASSENGER_RULES_VIOLATED, DETAIL);
	}

	/**
	 * BR-10 tại {@code firstDeparture} (ngày cất cánh chặng đầu chiều đi, giờ địa phương). BR-13 khi có chặng quốc tế:
	 * hộ chiếu còn hạn tới ít nhất {@code lastDeparture} (ngày cất cánh chặng cuối của booking) cộng 6 tháng.
	 */
	static void checkDetails(List<PassengerRequest> passengers, LocalDate firstDeparture, LocalDate lastDeparture,
			boolean international) {
		FieldErrors errors = new FieldErrors();
		for (int i = 0; i < passengers.size(); i++) {
			PassengerRequest p = passengers.get(i);
			String field = "passengers[%d].".formatted(i);
			if (p.dateOfBirth().isAfter(firstDeparture)) {
				errors.add(field + "dateOfBirth", "Ngày sinh không được sau ngày bay");
			}
			else if (typeAt(p.dateOfBirth(), firstDeparture) != p.type()) {
				errors.add(field + "dateOfBirth", "Theo ngày sinh, hành khách là %s tại ngày bay"
					.formatted(typeAt(p.dateOfBirth(), firstDeparture).label()));
			}
			if (international) {
				if (p.nationality() == null) {
					errors.add(field + "nationality", "Hành trình quốc tế cần khai quốc tịch");
				}
				if (p.passportNumber() == null) {
					errors.add(field + "passportNumber", "Hành trình quốc tế cần khai số hộ chiếu");
				}
				if (p.passportExpiry() == null) {
					errors.add(field + "passportExpiry", "Hành trình quốc tế cần khai ngày hết hạn hộ chiếu");
				}
				else if (p.passportExpiry().isBefore(lastDeparture.plusMonths(6))) {
					errors.add(field + "passportExpiry",
							"Hộ chiếu phải còn hạn ít nhất 6 tháng tính từ ngày bay của chặng cuối");
				}
			}
		}
		errors.throwIfAny(ErrorCode.PASSENGER_RULES_VIOLATED, DETAIL);
	}

	/** BR-10: loại khách theo tuổi tròn năm tại {@code day}. */
	static PassengerType typeAt(LocalDate dateOfBirth, LocalDate day) {
		int age = Period.between(dateOfBirth, day).getYears();
		if (age >= 12) {
			return PassengerType.ADULT;
		}
		return age >= 2 ? PassengerType.CHILD : PassengerType.INFANT;
	}

	private static long count(List<PassengerRequest> passengers, PassengerType type) {
		return passengers.stream().filter(p -> p.type() == type).count();
	}

}
