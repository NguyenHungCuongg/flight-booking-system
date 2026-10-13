package vn.edu.uit.flightbooking.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;
import static vn.edu.uit.flightbooking.flight.TestFlights.createOne;
import static vn.edu.uit.flightbooking.flight.TestFlights.newFlight;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.flight.TestFlights;

/**
 * Dữ liệu dùng chung cho test giữ chỗ, theo ví dụ TDD §8.8 (giống BookingQuoteTest): chiều đi Q1100 QHA→QHB 06:00
 * (1.870.000) nối Q1200 QHB→QHE 11:00 (1.150.000), chiều về Q1300 QHE→QHA ngày DAY + 3 lúc 06:00 (2.160.000). Mọi
 * chuyến có ECONOMY 100 ghế, chỉ bán gói Eco của hãng Q1 (mã số vé 901). Dùng trong test {@code @Transactional}.
 */
public final class TestBookings {

	public static final LocalDate ADULT_BORN = DAY.minusYears(36);

	public static final LocalDate CHILD_BORN = DAY.minusYears(5);

	public static final LocalDate INFANT_BORN = DAY.minusMonths(15);

	public record Trip(TestFlights.Catalog catalog, long firstLeg, long secondLeg, long back) {

		public Map<String, Object> journey(String direction, long... flightIds) {
			return Map.of("direction", direction, "flightIds", flightIds, "fareFamilyId", catalog.economyFare());
		}

		public List<Map<String, Object>> oneWay() {
			return List.of(journey("OUTBOUND", firstLeg, secondLeg));
		}

		public List<Map<String, Object>> roundTrip() {
			return List.of(journey("OUTBOUND", firstLeg, secondLeg), journey("RETURN", back));
		}

	}

	private TestBookings() {
	}

	/** Danh mục của {@link TestFlights#catalog}, thêm sân bay QHE (Việt Nam), và ba chuyến tạo qua API Admin. */
	public static Trip trip(MockMvc mvc, JdbcClient jdbc) throws Exception {
		MockHttpSession admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();
		TestFlights.Catalog catalog = TestFlights.catalog(jdbc);
		jdbc.sql("""
				INSERT INTO airports (code, name, city, country_code, timezone)
				VALUES ('QHE', 'Sân bay E', 'Phú Quốc', 'VN', 'Asia/Ho_Chi_Minh')
				""").update();
		long firstLeg = createOne(mvc, admin, newFlight(catalog, Map.of("fares", eco(catalog, 1_870_000))));
		long secondLeg = createOne(mvc, admin, newFlight(catalog, Map.of("flightNumber", "Q1200", "departureAirport",
				"QHB", "arrivalAirport", "QHE", "departureTime", "11:00", "durationMinutes", 60, "fares",
				eco(catalog, 1_150_000))));
		String returnDay = DAY.plusDays(3).toString();
		long back = createOne(mvc, admin, newFlight(catalog, Map.of("flightNumber", "Q1300", "departureAirport", "QHE",
				"arrivalAirport", "QHA", "fromDate", returnDay, "toDate", returnDay, "fares", eco(catalog, 2_160_000))));
		return new Trip(catalog, firstLeg, secondLeg, back);
	}

	private static List<Map<String, Object>> eco(TestFlights.Catalog catalog, long price) {
		return List.of(Map.of("fareFamilyId", catalog.economyFare(), "price", price));
	}

	/** Khách tên NGUYEN VAN AN, không khai hộ chiếu; trả map sửa được để thêm trường. */
	public static Map<String, Object> passenger(String type, LocalDate dateOfBirth) {
		Map<String, Object> p = new LinkedHashMap<>();
		p.put("type", type);
		p.put("lastName", "NGUYEN");
		p.put("firstName", "VAN AN");
		p.put("gender", "MALE");
		p.put("dateOfBirth", dateOfBirth.toString());
		return p;
	}

	/** Một người lớn đi một chiều Q1100 + Q1200, không hành lý, không voucher; {@code changes} ghi đè từng trường. */
	public static String body(Trip trip, Map<String, Object> changes) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("journeys", trip.oneWay());
		body.put("passengers", List.of(passenger("ADULT", ADULT_BORN)));
		body.put("contact", Map.of("name", "NGUYEN VAN AN", "email", "an@example.com", "phone", "0901234567"));
		body.putAll(changes);
		return JsonMapper.shared().writeValueAsString(body);
	}

	public static ResultActions hold(MockMvc mvc, MockHttpSession customer, String body) throws Exception {
		return mvc.perform(post("/api/bookings").with(csrf(mvc))
			.session(customer)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	/** Giữ chỗ qua API (phải trả 201), trả mã đặt chỗ. */
	public static String holdCode(MockMvc mvc, MockHttpSession customer, String body) throws Exception {
		String json = hold(mvc, customer, body).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(json, "$.code");
	}

	/** Mức hành lý đang bán của {@code airline}. */
	public static long baggageOption(JdbcClient jdbc, String airline, int weightKg, long price) {
		return jdbc.sql("""
				INSERT INTO baggage_options (airline_code, weight_kg, price) VALUES (?, ?, ?) RETURNING id
				""").params(airline, weightKg, price).query(Long.class).single();
	}

	public static Map<String, Object> bag(int passengerIndex, String direction, long baggageOptionId) {
		return Map.of("passengerIndex", passengerIndex, "direction", direction, "baggageOptionId", baggageOptionId);
	}

	public static long bookingId(JdbcClient jdbc, String code) {
		return jdbc.sql("SELECT id FROM bookings WHERE code = ?").param(code).query(Long.class).single();
	}

	/** Số ghế còn của hạng ECONOMY. */
	public static int available(JdbcClient jdbc, long flightId) {
		return jdbc.sql("SELECT available_seats FROM flight_cabins WHERE flight_id = ? AND cabin_class = 'ECONOMY'")
			.param(flightId)
			.query(Integer.class)
			.single();
	}

}
