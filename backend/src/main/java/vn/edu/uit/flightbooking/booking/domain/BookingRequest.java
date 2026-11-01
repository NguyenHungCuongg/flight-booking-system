package vn.edu.uit.flightbooking.booking.domain;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body chung của POST /bookings/quote (FR-20) và POST /bookings (FR-24), theo ví dụ TDD §8.8. Bean Validation kiểm
 * tra định dạng (BR-12, BR-14, định dạng hộ chiếu của BR-13); quy tắc cần dữ liệu chuyến bay nằm ở
 * {@link PassengerRules} và {@code QuoteService}. {@code voucherCode} rỗng là không dùng voucher.
 */
public record BookingRequest(@NotNull @Size(max = 10) List<@NotNull @Valid JourneyRequest> journeys,
		@NotEmpty List<@NotNull @Valid PassengerRequest> passengers, List<@NotNull @Valid BaggageRequest> baggage,
		@NotNull @Valid ContactRequest contact, String voucherCode) {

	/** BR-12: chữ in hoa không dấu, các từ cách nhau đúng một khoảng trắng. */
	static final String NAME = "[A-Z]+( [A-Z]+)*";

	static final String NAME_MESSAGE = "Chỉ gồm chữ in hoa không dấu, các từ cách nhau một khoảng trắng";

	/** Thiếu {@code baggage} thì coi là không mua thêm hành lý. */
	public BookingRequest {
		baggage = baggage == null ? List.of() : baggage;
	}

	/** {@code flightIds} theo thứ tự bay. Số chặng và các quy tắc khác do {@code FlightApi} kiểm tra (BR-02–BR-05). */
	public record JourneyRequest(@NotNull Direction direction, @NotNull @Size(max = 10) List<@NotNull Long> flightIds,
			@NotNull Long fareFamilyId) {
	}

	/**
	 * Quốc tịch và hộ chiếu chỉ bắt buộc khi hành trình có chặng quốc tế (BR-13). Ô để trống trên form gửi chuỗi rỗng,
	 * nên chuỗi trống được coi là không khai.
	 */
	public record PassengerRequest(@NotNull PassengerType type,
			@NotNull @Size(max = 50) @Pattern(regexp = NAME, message = NAME_MESSAGE) String lastName,
			@NotNull @Size(max = 50) @Pattern(regexp = NAME, message = NAME_MESSAGE) String firstName,
			@NotNull Gender gender, @NotNull LocalDate dateOfBirth,
			@Pattern(regexp = "[A-Z]{2}", message = "Mã quốc gia gồm 2 chữ cái in hoa") String nationality,
			@Pattern(regexp = "[A-Z0-9]{6,20}",
					message = "Số hộ chiếu gồm 6–20 chữ in hoa hoặc chữ số") String passportNumber,
			LocalDate passportExpiry) {

		public PassengerRequest {
			nationality = blankToNull(nationality);
			passportNumber = blankToNull(passportNumber);
		}

	}

	/** {@code passengerIndex}: vị trí trong {@code passengers}, tính từ 0 (TDD §8.8). */
	public record BaggageRequest(@NotNull @Min(0) Integer passengerIndex, @NotNull Direction direction,
			@NotNull Long baggageOptionId) {
	}

	/** BR-14. */
	public record ContactRequest(@NotBlank @Size(max = 100) String name,
			@NotBlank @Email @Size(max = 255) String email,
			@NotNull @Pattern(regexp = "\\+?[0-9]{9,15}",
					message = "Số điện thoại gồm 9–15 chữ số, có thể có dấu + ở đầu") String phone) {
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

}
