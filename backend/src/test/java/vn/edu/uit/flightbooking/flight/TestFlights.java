package vn.edu.uit.flightbooking.flight;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
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

/**
 * Dữ liệu dùng chung cho test của module flight. Danh mục tạo bằng SQL trong transaction của test (bản ghi tạo qua API
 * chưa flush nên SQL không thấy), chuyến bay tạo qua API.
 */
public final class TestFlights {

	/** Ngày bay mặc định: 30 ngày sau hôm nay theo giờ Việt Nam, nên mọi chuyến test đều ở tương lai. */
	public static final LocalDate DAY = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(30);

	/** Id các gói giá: Q1 có một gói ECONOMY và một gói BUSINESS, Q2 có một gói ECONOMY. */
	public record Catalog(long economyFare, long businessFare, long otherAirlineFare) {
	}

	private TestFlights() {
	}

	/**
	 * Hãng Q1, Q2 (đang hoạt động), Q3 (ngừng); sân bay QHA, QHB (giờ Việt Nam), QHC (Singapore, +08:00), QHD (ngừng
	 * kích hoạt).
	 */
	public static Catalog catalog(JdbcClient jdbc) {
		jdbc.sql("""
				INSERT INTO airlines (code, name, ticket_prefix, active) VALUES
				    ('Q1', 'Hãng Một', '901', TRUE), ('Q2', 'Hãng Hai', '902', TRUE), ('Q3', 'Hãng Ba', '903', FALSE)
				""").update();
		jdbc.sql("""
				INSERT INTO airports (code, name, city, country_code, timezone, active) VALUES
				    ('QHA', 'Sân bay A', 'Hà Nội', 'VN', 'Asia/Ho_Chi_Minh', TRUE),
				    ('QHB', 'Sân bay B', 'Đà Nẵng', 'VN', 'Asia/Ho_Chi_Minh', TRUE),
				    ('QHC', 'Sân bay C', 'Singapore', 'SG', 'Asia/Singapore', TRUE),
				    ('QHD', 'Sân bay D', 'Huế', 'VN', 'Asia/Ho_Chi_Minh', FALSE)
				""").update();
		return new Catalog(fare(jdbc, "Q1", "ECONOMY", "Eco"), fare(jdbc, "Q1", "BUSINESS", "Biz"),
				fare(jdbc, "Q2", "ECONOMY", "Khác"));
	}

	private static long fare(JdbcClient jdbc, String airline, String cabin, String name) {
		return jdbc.sql("""
				INSERT INTO fare_families (airline_code, cabin_class, name, carry_on_kg, checked_baggage_kg,
				                           refundable, changeable)
				VALUES (?, ?, ?, 7, 0, FALSE, TRUE)
				RETURNING id
				""").params(airline, cabin, name).query(Long.class).single();
	}

	/**
	 * Body của POST /api/admin/flights: Q1100 QHA→QHB, 06:00 giờ địa phương, bay 130 phút, chỉ ngày {@link #DAY},
	 * ECONOMY 100 ghế, BUSINESS 10 ghế, giá 1.000.000 và 3.000.000. {@code changes} ghi đè từng trường.
	 */
	public static String newFlight(Catalog catalog, Map<String, Object> changes) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("airlineCode", "Q1");
		body.put("flightNumber", "Q1100");
		body.put("departureAirport", "QHA");
		body.put("arrivalAirport", "QHB");
		body.put("departureTime", "06:00");
		body.put("durationMinutes", 130);
		body.put("fromDate", DAY.toString());
		body.put("toDate", DAY.toString());
		body.put("daysOfWeek", DayOfWeek.values());
		body.put("aircraftModel", "Airbus A321");
		body.put("cabins", List.of(Map.of("cabinClass", "ECONOMY", "totalSeats", 100),
				Map.of("cabinClass", "BUSINESS", "totalSeats", 10)));
		body.put("fares", List.of(Map.of("fareFamilyId", catalog.economyFare(), "price", 1_000_000),
				Map.of("fareFamilyId", catalog.businessFare(), "price", 3_000_000)));
		body.putAll(changes);
		return JsonMapper.shared().writeValueAsString(body);
	}

	public static ResultActions create(MockMvc mvc, MockHttpSession admin, String body) throws Exception {
		return mvc.perform(post("/api/admin/flights").with(csrf(mvc))
			.session(admin)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	/** Tạo chuyến qua API, trả id của chuyến đầu tiên được tạo. */
	public static long createOne(MockMvc mvc, MockHttpSession admin, String body) throws Exception {
		String json = create(mvc, admin, body).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.flightIds[0]")).longValue();
	}

}
