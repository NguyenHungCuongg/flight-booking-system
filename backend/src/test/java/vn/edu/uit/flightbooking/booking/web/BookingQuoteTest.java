package vn.edu.uit.flightbooking.booking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;
import static vn.edu.uit.flightbooking.flight.TestFlights.createOne;
import static vn.edu.uit.flightbooking.flight.TestFlights.newFlight;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;
import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.flight.TestFlights;
import vn.edu.uit.flightbooking.promotion.TestVouchers;

/**
 * FR-20–FR-23 qua POST /api/bookings/quote. Dữ liệu theo ví dụ TDD §8.8: chiều đi Q1100 QHA→QHB 06:00–08:10
 * (1.870.000) nối Q1200 QHB→QHE 11:00–12:00 (1.150.000); chiều về Q1300 QHE→QHA ngày DAY + 3 lúc 06:00 (2.160.000);
 * thêm Q1400 QHA→QHC (Singapore, quốc tế, 1.000.000). Mọi chuyến có ECONOMY 100 ghế và chỉ bán gói Eco.
 */
@IntegrationTest
@Transactional
class BookingQuoteTest {

	static final LocalDate ADULT_BORN = DAY.minusYears(36);

	static final LocalDate CHILD_BORN = DAY.minusYears(5);

	static final LocalDate INFANT_BORN = DAY.minusMonths(15);

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	TestUsers.TestUser customer;

	TestFlights.Catalog catalog;

	long firstLeg;

	long secondLeg;

	long back;

	long international;

	@BeforeEach
	void setUp() throws Exception {
		MockHttpSession admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		catalog = TestFlights.catalog(jdbc);
		jdbc.sql("""
				INSERT INTO airports (code, name, city, country_code, timezone)
				VALUES ('QHE', 'Sân bay E', 'Phú Quốc', 'VN', 'Asia/Ho_Chi_Minh')
				""").update();
		firstLeg = createOne(mvc, admin, newFlight(catalog, Map.of("fares", eco(1_870_000))));
		secondLeg = createOne(mvc, admin, newFlight(catalog, Map.of("flightNumber", "Q1200", "departureAirport", "QHB",
				"arrivalAirport", "QHE", "departureTime", "11:00", "durationMinutes", 60, "fares", eco(1_150_000))));
		String returnDay = DAY.plusDays(3).toString();
		back = createOne(mvc, admin, newFlight(catalog, Map.of("flightNumber", "Q1300", "departureAirport", "QHE",
				"arrivalAirport", "QHA", "fromDate", returnDay, "toDate", returnDay, "fares", eco(2_160_000))));
		international = createOne(mvc, admin, newFlight(catalog, Map.of("flightNumber", "Q1400", "arrivalAirport",
				"QHC", "departureTime", "07:00", "durationMinutes", 180, "fares", eco(1_000_000))));
	}

	List<Map<String, Object>> eco(long price) {
		return List.of(Map.of("fareFamilyId", catalog.economyFare(), "price", price));
	}

	Map<String, Object> journey(String direction, long... flightIds) {
		return Map.of("direction", direction, "flightIds", flightIds, "fareFamilyId", catalog.economyFare());
	}

	/** Khách tên NGUYEN VAN AN, không khai hộ chiếu; trả map sửa được để thêm trường. */
	static Map<String, Object> passenger(String type, LocalDate dateOfBirth) {
		Map<String, Object> p = new LinkedHashMap<>();
		p.put("type", type);
		p.put("lastName", "NGUYEN");
		p.put("firstName", "VAN AN");
		p.put("gender", "MALE");
		p.put("dateOfBirth", dateOfBirth.toString());
		return p;
	}

	/** Một người lớn đi một chiều Q1100 + Q1200, không hành lý, không voucher; {@code changes} ghi đè từng trường. */
	String body(Map<String, Object> changes) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("journeys", List.of(journey("OUTBOUND", firstLeg, secondLeg)));
		body.put("passengers", List.of(passenger("ADULT", ADULT_BORN)));
		body.put("contact", Map.of("name", "NGUYEN VAN AN", "email", "an@example.com", "phone", "0901234567"));
		body.putAll(changes);
		return JsonMapper.shared().writeValueAsString(body);
	}

	ResultActions quote(Map<String, Object> changes) throws Exception {
		return mvc.perform(post("/api/bookings/quote").with(csrf(mvc))
			.session(customer.session())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(changes)));
	}

	/** BR-20, BR-21: đúng bảng tính giá của ví dụ tìm kiếm TDD §8.8 (2 người lớn, 1 trẻ em, 1 em bé). */
	@Test
	void pricesEachPassengerTypeOnEveryLeg() throws Exception {
		quote(Map.of("passengers", List.of(passenger("ADULT", ADULT_BORN), passenger("ADULT", ADULT_BORN),
				passenger("CHILD", CHILD_BORN), passenger("INFANT", INFANT_BORN))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.journeys.length()").value(1))
			.andExpect(jsonPath("$.journeys[0].direction").value("OUTBOUND"))
			.andExpect(jsonPath("$.journeys[0].passengerFares[*].type", contains("ADULT", "CHILD", "INFANT")))
			.andExpect(jsonPath("$.journeys[0].passengerFares[*].count", contains(2, 1, 1)))
			.andExpect(jsonPath("$.journeys[0].passengerFares[*].unitPrice", contains(3_020_000, 2_718_000, 302_000)))
			.andExpect(jsonPath("$.journeys[0].passengerFares[*].total", contains(6_040_000, 2_718_000, 302_000)))
			.andExpect(jsonPath("$.journeys[0].fareTotal").value(9_060_000))
			.andExpect(jsonPath("$.journeys[0].baggageTotal").value(0))
			.andExpect(jsonPath("$.journeys[0].discount").value(0))
			.andExpect(jsonPath("$.fareTotal").value(9_060_000))
			.andExpect(jsonPath("$.baggageTotal").value(0))
			.andExpect(jsonPath("$.discountTotal").value(0))
			.andExpect(jsonPath("$.totalAmount").value(9_060_000));
	}

	/**
	 * BR-10 theo ngày giờ địa phương: Q1100 cất cánh 06:00 (+07:00), tức 23:00 UTC ngày hôm trước. Bé tròn 2 tuổi
	 * đúng ngày bay là trẻ em.
	 */
	@Test
	void ageUsesLocalDepartureDate() throws Exception {
		quote(Map.of("passengers", List.of(passenger("ADULT", ADULT_BORN), passenger("CHILD", DAY.minusYears(2)))))
			.andExpect(status().isOk());
		quote(Map.of("passengers", List.of(passenger("ADULT", ADULT_BORN), passenger("INFANT", DAY.minusYears(2)))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PASSENGER_RULES_VIOLATED"))
			.andExpect(jsonPath("$.errors[*].field", contains("passengers[1].dateOfBirth")));
	}

	/** BR-11: báo theo trường {@code passengers}. */
	@Test
	void needsAnAdult() throws Exception {
		quote(Map.of("passengers", List.of(passenger("CHILD", CHILD_BORN)))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PASSENGER_RULES_VIOLATED"))
			.andExpect(jsonPath("$.errors[*].field", contains("passengers")));
	}

	/** BR-13: chặng quốc tế cần quốc tịch, số hộ chiếu, hộ chiếu còn hạn 6 tháng từ ngày bay chặng cuối. */
	@Test
	void internationalTripNeedsPassports() throws Exception {
		List<Map<String, Object>> toSingapore = List.of(journey("OUTBOUND", international));

		quote(Map.of("journeys", toSingapore)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PASSENGER_RULES_VIOLATED"))
			.andExpect(jsonPath("$.errors[*].field", contains("passengers[0].nationality",
					"passengers[0].passportNumber", "passengers[0].passportExpiry")));

		Map<String, Object> traveller = passenger("ADULT", ADULT_BORN);
		traveller.put("nationality", "VN");
		traveller.put("passportNumber", "C1234567");
		traveller.put("passportExpiry", DAY.plusMonths(6).toString());
		quote(Map.of("journeys", toSingapore, "passengers", List.of(traveller))).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalAmount").value(1_000_000));
	}

	/** BR-12, BR-14 và định dạng hộ chiếu: lỗi theo trường như ví dụ TDD §8.8, mọi trường sai trong một lần. */
	@Test
	void rejectsMalformedFieldsPerField() throws Exception {
		Map<String, Object> sloppy = passenger("ADULT", ADULT_BORN);
		sloppy.put("lastName", "Nguyễn");
		sloppy.put("firstName", "VAN  AN");
		sloppy.put("passportNumber", "c12");

		quote(Map.of("passengers", List.of(sloppy), "contact",
				Map.of("name", "NGUYEN VAN AN", "email", "khong-phai-email", "phone", "09-01")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[*].field",
					containsInAnyOrder("passengers[0].lastName", "passengers[0].firstName",
							"passengers[0].passportNumber", "contact.email", "contact.phone")));
	}

	/** Chiều sai thứ tự, chuyến đã huỷ: lỗi của hành trình đi thẳng ra API (chi tiết ở FlightApiTest). */
	@Test
	void itineraryErrorsReachTheClient() throws Exception {
		List<Map<String, Object>> roundTrip = List.of(journey("OUTBOUND", firstLeg, secondLeg),
				journey("RETURN", back));

		quote(Map.of("journeys", List.of(journey("RETURN", back)))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ITINERARY_INVALID"));
		quote(Map.of("journeys", roundTrip)).andExpect(status().isOk())
			.andExpect(jsonPath("$.journeys[*].direction", contains("OUTBOUND", "RETURN")));

		jdbc.sql("UPDATE flights SET status = 'CANCELLED' WHERE id = ?").param(back).update();
		quote(Map.of("journeys", roundTrip)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("FLIGHT_NOT_BOOKABLE"));
	}

	/** TDD §4.3: chỉ Customer gọi được. */
	@Test
	void onlyCustomersCanQuote() throws Exception {
		mvc.perform(post("/api/bookings/quote").with(csrf(mvc))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(Map.of()))).andExpect(status().isUnauthorized());

		MockHttpSession staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF).session();
		mvc.perform(post("/api/bookings/quote").with(csrf(mvc))
			.session(staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(Map.of()))).andExpect(status().isForbidden());
	}

	long baggageOption(String airline, int weightKg, long price, boolean active) {
		return jdbc.sql("""
				INSERT INTO baggage_options (airline_code, weight_kg, price, active)
				VALUES (?, ?, ?, ?)
				RETURNING id
				""").params(airline, weightKg, price, active).query(Long.class).single();
	}

	static Map<String, Object> bag(int passengerIndex, String direction, long baggageOptionId) {
		return Map.of("passengerIndex", passengerIndex, "direction", direction, "baggageOptionId", baggageOptionId);
	}

	/** Booking cũ dùng voucher; chỉ các cột bắt buộc, số tiền thoả CHECK của bảng. */
	void booking(String code, long userId, long voucherId, String status) {
		jdbc.sql("""
				INSERT INTO bookings (code, user_id, status, contact_name, contact_email, contact_phone, voucher_id,
				                      fare_total, baggage_total, discount_total, total_amount, hold_expires_at)
				VALUES (?, ?, ?, 'KHACH TEST', 'khach@test.local', '0900000000', ?, 1000000, 0, 100000, 900000, now())
				""").params(code, userId, status, voucherId).update();
	}

	/** Kết quả chính của Plan 10: đúng từng con số của ví dụ giữ chỗ TDD §8.8; báo giá không giữ ghế, không giữ lượt. */
	@Test
	void matchesTddBookingExample() throws Exception {
		long bag20 = baggageOption("Q1", 20, 350_000, true);
		long welcome = TestVouchers.insert(jdbc, "QVWELCOME", Map.of("max_discount", 200_000));

		quote(Map.of("journeys", List.of(journey("OUTBOUND", firstLeg, secondLeg), journey("RETURN", back)),
				"passengers", List.of(passenger("ADULT", ADULT_BORN), passenger("INFANT", INFANT_BORN)), "baggage",
				List.of(bag(0, "OUTBOUND", bag20)), "voucherCode", "QVWELCOME"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.journeys[*].direction", contains("OUTBOUND", "RETURN")))
			.andExpect(jsonPath("$.journeys[*].fareTotal", contains(3_322_000, 2_376_000)))
			.andExpect(jsonPath("$.journeys[*].baggageTotal", contains(350_000, 0)))
			.andExpect(jsonPath("$.journeys[*].discount", contains(121_428, 78_572)))
			.andExpect(jsonPath("$.fareTotal").value(5_698_000))
			.andExpect(jsonPath("$.baggageTotal").value(350_000))
			.andExpect(jsonPath("$.discountTotal").value(200_000))
			.andExpect(jsonPath("$.totalAmount").value(5_848_000));

		assertThat(TestVouchers.usedCount(jdbc, welcome)).isZero();
		assertThat(jdbc.sql("""
				SELECT min(available_seats) FROM flight_cabins
				WHERE flight_id IN (?, ?, ?) AND cabin_class = 'ECONOMY'
				""").params(firstLeg, secondLeg, back).query(Integer.class).single()).isEqualTo(100);
	}

	/** BR-22: một mức mỗi khách mỗi chiều, không cho em bé, chỉ trong bảng giá đang bán của hãng khai thác chiều đó. */
	@Test
	void baggageRulesReportEveryWrongItem() throws Exception {
		long bag20 = baggageOption("Q1", 20, 350_000, true);
		long retired = baggageOption("Q1", 30, 550_000, false);
		long otherAirline = baggageOption("Q2", 20, 350_000, true);

		quote(Map.of("journeys", List.of(journey("OUTBOUND", firstLeg, secondLeg), journey("RETURN", back)),
				"passengers",
				List.of(passenger("ADULT", ADULT_BORN), passenger("ADULT", ADULT_BORN),
						passenger("INFANT", INFANT_BORN)),
				"baggage",
				List.of(bag(0, "OUTBOUND", bag20), bag(0, "OUTBOUND", bag20), bag(2, "OUTBOUND", bag20),
						bag(3, "OUTBOUND", bag20), bag(1, "OUTBOUND", retired), bag(1, "RETURN", otherAirline))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[*].field",
					contains("baggage[1].direction", "baggage[2].passengerIndex", "baggage[3].passengerIndex",
							"baggage[4].baggageOptionId", "baggage[5].baggageOptionId")));

		// Một chiều thì không mua được hành lý cho chiều về.
		quote(Map.of("baggage", List.of(bag(0, "RETURN", bag20)))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field", contains("baggage[0].direction")));
	}

	/** BR-30 điều kiện 5: tài khoản đã có booking giữ voucher (chờ thanh toán, đã xuất, đã hoàn) thì không dùng lại. */
	@Test
	void voucherOncePerAccount() throws Exception {
		long voucher = TestVouchers.insert(jdbc, "QVONCE", Map.of());
		long someoneElse = TestUsers.create(jdbc, Role.CUSTOMER, TestUsers.randomEmail());
		booking("QABCDE", someoneElse, voucher, "ISSUED");
		booking("QABCDF", customer.id(), voucher, "EXPIRED");
		booking("QABCDG", customer.id(), voucher, "CANCELLED");

		// 10% của 3.020.000; mã viết thường vẫn dùng được.
		quote(Map.of("voucherCode", "qvonce")).andExpect(status().isOk())
			.andExpect(jsonPath("$.discountTotal").value(302_000))
			.andExpect(jsonPath("$.totalAmount").value(2_718_000));

		booking("QABCDH", customer.id(), voucher, "REFUNDED");
		quote(Map.of("voucherCode", "QVONCE")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VOUCHER_INVALID"))
			.andExpect(jsonPath("$.reason").value("ALREADY_USED"));
	}

	/** BR-30, BR-31: lý do từ chối đi ra API; ô voucher để trống là không dùng voucher; giảm 100% thì tổng bằng 0. */
	@Test
	void voucherReasonsBlankCodeAndFullDiscount() throws Exception {
		TestVouchers.insert(jdbc, "QVMIN", Map.of("min_order_amount", 3_020_001));
		TestVouchers.insert(jdbc, "QVFREE", Map.of("discount_value", 100));

		quote(Map.of("voucherCode", "QVMIN")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.reason").value("MIN_ORDER_NOT_MET"));
		quote(Map.of("voucherCode", "QVNONE")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.reason").value("NOT_FOUND"));
		quote(Map.of("voucherCode", "")).andExpect(status().isOk()).andExpect(jsonPath("$.discountTotal").value(0));
		quote(Map.of("voucherCode", "  ")).andExpect(status().isOk()).andExpect(jsonPath("$.discountTotal").value(0));
		quote(Map.of("journeys", List.of(journey("OUTBOUND", firstLeg, secondLeg), journey("RETURN", back)),
				"voucherCode", "QVFREE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.journeys[*].discount", contains(3_020_000, 2_160_000)))
			.andExpect(jsonPath("$.discountTotal").value(5_180_000))
			.andExpect(jsonPath("$.totalAmount").value(0));
	}

}
