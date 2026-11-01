# Plan 10 — Báo giá booking: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khách đã đăng nhập gửi hành trình đã chọn, hành khách, liên hệ, hành lý và voucher tới `POST /api/bookings/quote` và nhận bảng giá chi tiết: giá vé theo loại khách, hành lý, mức giảm chia theo chiều, tổng tiền. Báo giá kiểm tra đúng những gì giữ chỗ sẽ kiểm tra (FR-20), không ghi gì vào DB, và cho đúng các con số của ví dụ TDD §8.8 (tổng 5.848.000 đ).

**Architecture:** Thêm `flight.FlightApi` (API công khai của module `flight`): kiểm tra hành trình đã chọn theo BR-01–06, BR-04 và trả giá người lớn từng chặng cùng quy định gói giá. Truy vấn bằng `JdbcClient` như `FlightSearch`. Thêm module Spring Modulith `booking` (phụ thuộc `common`, `catalog`, `flight`, `promotion`). Quy tắc hành khách (BR-10, 11, 13) và phép tính tiền (BR-21 từng vé, BR-33) là logic thuần trong `booking.domain`, test không cần Spring. `booking.domain.QuoteService` ghép các phần đó, gọi `CatalogApi.baggageOptions` (thêm mới) cho BR-22 và `VoucherApi.check` cho BR-30, BR-31. BR-30 điều kiện 5 được kiểm tra bằng một truy vấn trên bảng `bookings`. Plan 11 gọi lại `QuoteService.quote` rồi mới giữ ghế và lượt voucher (TDD §6.2: "`quote` và `create` dùng chung một service").

**Tech Stack:** Java 21, Spring Boot 4.1.1 (Spring MVC, Bean Validation, `JdbcClient`, Spring Data JPA chỉ ở `catalog`), Spring Modulith 2.1.1, PostgreSQL 18, Testcontainers, MockMvc, JUnit 5 + AssertJ. Frontend chỉ chạy lại `npm run gen:api` (openapi-typescript 7.13.0).

**Spec:** [PRD](../../PRD.md) FR-20, FR-21, FR-22, FR-23, BR-01–06, BR-10–14, BR-20–24, BR-30 (điều kiện 5), BR-31, BR-33 · [TDD](../../TDD.md) §3.2, §4.3, §5.1, §5.2, §6.2 (bước 1–4), §8.3, §8.8, §9 · [App Flow](../../APP_FLOW.md) C-07 · [Backend Schema](../../BACKEND_SCHEMA.md) §5 (`bookings`, `passengers`, `booking_baggage`, `uq_bookings_voucher_user`), Q-06 · [Lộ trình](2026-10-09-00-roadmap.md) §2, §5, §6

## Global Constraints

- Mọi ràng buộc ở Global Constraints của [Plan 01](2026-10-09-01-backend-foundation.md), [Plan 02](2026-10-09-02-accounts-settings-email.md), [Plan 06](2026-10-11-06-flights.md), [Plan 07](2026-10-11-07-flight-search.md) và [Plan 09](2026-10-12-09-voucher.md) vẫn giữ nguyên: phiên bản thư viện, ProblemDetail có `code`, CSRF SPA, câu lỗi tiếng Việt, DTO là `record`, dữ liệu sai theo trường trả 400 `VALIDATION_FAILED` kèm `errors[{field, message}]`.
- Phụ thuộc giữa module: `booking → common, catalog, flight, promotion`; `flight → common, catalog` (không đổi). TDD §3.2 có ghi `booking → payment`, nhưng phụ thuộc đó chỉ cần từ Plan 12. `ModularityTest` kiểm tra.
- **Không có migration mới.** Các bảng của `booking` đã có trong `V1__init.sql`. Plan này không ghi bảng nào.
- **Báo giá chỉ đọc** (FR-20): `@Transactional(readOnly = true)`, không giữ ghế, không giữ lượt voucher, không ghi `bookings`.
- **Thứ tự kiểm tra** (TDD §6.2): Bean Validation → chiều đi/về → BR-11 → hành trình (`FlightApi`: BR-01–06, BR-04) → BR-10, BR-13 → hành lý BR-22 → voucher BR-30 điều kiện 1–4, rồi điều kiện 5 → tính tiền.
- **Mã lỗi** (TDD §9): cấu trúc hành trình sai (BR-02–05, sai chiều) → 400 `ITINERARY_INVALID`; chuyến không còn bán (không tồn tại, đã huỷ, quá sát giờ, gói giá ngừng bán hoặc không còn bán trên một chặng) → 409 `FLIGHT_NOT_BOOKABLE`; thiếu ghế → 409 `SEATS_UNAVAILABLE` với câu báo dạng "Chuyến Q1200 hạng ECONOMY không còn đủ 4 ghế" (TDD §8.8); BR-10, 11, 13 → 400 `PASSENGER_RULES_VIOLATED`; voucher → 409 `VOUCHER_INVALID` kèm `reason`.
- **Tiền** (TDD §5.2): `long` VND. BR-21 làm tròn half-up **trên từng vé từng chặng** bằng `FlightSearch.percentOf` (không viết lại công thức). BR-31 (đã có ở `VoucherApi`) và BR-33 làm tròn xuống; chiều cuối nhận phần còn lại. Tổng thoả BR-23: `totalAmount = fareTotal + baggageTotal − discountTotal`.
- **Ngày dùng cho BR-10, BR-13** là ngày theo giờ địa phương của sân bay đi: tuổi tính tại ngày cất cánh chặng đầu chiều đi, hộ chiếu tính từ ngày cất cánh chặng cuối của booking. Không dùng ngày UTC.
- Ràng buộc DB của `passengers` và `bookings` (Plan 11 ghi) được Bean Validation chặn trước: tên `^[A-Z]+( [A-Z]+)*$` tối đa 50 ký tự (BR-12), quốc tịch `^[A-Z]{2}$`, số hộ chiếu `^[A-Z0-9]{6,20}$`, liên hệ tối đa 100/255/20 ký tự.
- Test tự tạo dữ liệu, không dựa vào seed: danh mục bằng `TestFlights.catalog(jdbc)` cộng SQL, chuyến bằng API Admin (`TestFlights.createOne`), voucher bằng `TestVouchers.insert`, booking cũ (cho BR-30 điều kiện 5) bằng SQL.

### Điểm cố ý khác TDD

1. **Phản hồi báo giá có thêm `passengerFares` ở mỗi chiều**, dạng `[{type, count, unitPrice, total}]`. FR-20 yêu cầu bảng giá có "giá vé theo loại khách", nhưng phản hồi mẫu ở TDD §8.8 chỉ có `fareTotal` của chiều. Không bỏ trường nào của TDD, nên phản hồi vẫn khớp ví dụ.
2. **`PASSENGER_RULES_VIOLATED` có thêm mảng `errors[{field, message}]`**, cùng dạng với `VALIDATION_FAILED`. TDD §9 không nói lỗi này có `errors`, nhưng APP_FLOW C-07 yêu cầu "đánh dấu từng trường bị lỗi". Mọi vi phạm được gom lại và trả một lần.
3. **BR-11 (số khách) kiểm tra trước hành trình**, còn TDD §6.2 đặt bước 2 (hành trình) trước bước 3 (hành khách). Lý do: bước kiểm tra ghế (BR-06) cần số khách chiếm ghế. Nếu khách gửi 10 người, họ phải nhận lỗi "tối đa 9 khách" chứ không phải `SEATS_UNAVAILABLE`. BR-10 và BR-13 vẫn chạy sau hành trình vì cần ngày bay và quốc gia của sân bay.
4. **`FlightApi` chỉ có `price` (kiểm tra và định giá)**, chưa có phần "đọc chuyến" mà TDD §3.2 ghi. Chưa có nơi nào cần đọc chuyến; plan nào cần (C-09, hậu mãi) sẽ thêm.

Những điểm TDD không nói, plan tự chọn:

- **Phân loại lỗi hành trình.** Id chuyến không tồn tại báo `FLIGHT_NOT_BOOKABLE` vì trường hợp này xảy ra khi Admin xoá chuyến (FR-94) sau lúc khách tìm, và C-07 sẽ đưa khách về C-02. Gói giá không tồn tại hoặc thuộc hãng khác báo `ITINERARY_INVALID` vì client gửi sai. Gói ngừng bán, hoặc không còn bán trên một chặng, báo `FLIGHT_NOT_BOOKABLE` (TDD §9: "gói giá ngừng bán").
- **Lỗi hành lý trả 400 `VALIDATION_FAILED` kèm `errors`** cho đúng `baggage[i].passengerIndex`, `baggage[i].direction` hoặc `baggage[i].baggageOptionId`. TDD §9 không có mã riêng cho BR-22, và đây là dữ liệu form sai.
- **`voucherCode` rỗng hoặc chỉ có khoảng trắng nghĩa là không dùng voucher**, vì ô voucher để trống trên form vẫn gửi `""`. Không báo `NOT_FOUND` trong trường hợp này.
- **Chặng nội địa không cần hộ chiếu.** Nếu khách vẫn khai thì giá trị chỉ cần đúng định dạng (Bean Validation) và không bị kiểm tra hạn.
- **BR-06 khi báo giá chỉ đọc `available_seats`**, không khoá. Giữ chỗ (Plan 11) mới giữ ghế bằng Q-01, nên báo giá xanh không bảo đảm giữ chỗ thành công.
- **BR-30 điều kiện 5 dùng `SELECT EXISTS` trên `bookings`**, cùng điều kiện trạng thái với `uq_bookings_voucher_user`. Plan 11 vẫn phải bắt vi phạm unique index khi ghi, vì hai request đồng thời đều có thể vượt qua truy vấn này.
- **`Quote` nằm ở `booking.domain`**, không ở gói gốc, vì chỉ module `booking` dùng (controller và Plan 11). Phản hồi API là record riêng `booking.web.QuoteResponse`, để các trường nội bộ mà Plan 11 cần (`itinerary`, `baggage`, `voucherId`) không lộ ra JSON.
- **`Pricing.allocate` dùng `Math.multiplyExact`**: tràn số sẽ báo lỗi 500 thay vì trả sai số tiền. Với giá vé thực tế (tạm tính vài tỉ đồng), tích chưa gần ngưỡng `long`.

## Review Focus

Những tình huống tài liệu không nói rõ nhưng dễ gây lỗi thật, xếp theo khả năng gặp. Mỗi dòng đã có test ghim lại.

1. **Tuổi tính theo ngày giờ địa phương, không theo UTC.** Q1100 cất cánh 06:00 `+07:00`, tức 23:00 UTC ngày hôm trước. Một bé tròn 2 tuổi đúng ngày bay phải là trẻ em, không phải em bé (Task 3 `ageUsesLocalDepartureDate`; biên tuổi ở Task 2 `ageIsCountedInWholeYearsOnFlightDate`).
2. **Ô voucher để trống** (`""` hoặc `"  "`) không được báo `VOUCHER_INVALID`. Mã viết thường vẫn dùng được (Task 4 `voucherReasonsBlankCodeAndFullDiscount`, `voucherOncePerAccount`).
3. **Form có nhiều chỗ sai thì nhận mọi lỗi trong một lần**, mỗi lỗi gắn đúng tên trường để C-07 đánh dấu: định dạng tên, email, số điện thoại, hộ chiếu (Task 3 `rejectsMalformedFieldsPerField`), vi phạm hành khách (Task 2 `declaredTypeMustMatchAgeAndBirthCannotFollowFlight`), hành lý (Task 4 `baggageRulesReportEveryWrongItem`).
4. **Làm tròn và phần dư.** Giá trẻ em và em bé làm tròn trên từng chặng rồi mới cộng; mức giảm chia theo chiều làm tròn xuống, chiều cuối nhận phần dư. Giảm 100% thì mỗi chiều được giảm đúng bằng tạm tính của chiều và tổng bằng 0 (Task 2 `discountIsSplitByJourneySubtotalAndLastTakesTheRest`; Task 4 `matchesTddBookingExample`, `voucherReasonsBlankCodeAndFullDiscount`).
5. **Báo giá không để lại dấu vết.** Sau khi báo giá, ghế còn nguyên và `used_count` của voucher vẫn là 0 (Task 4 `matchesTddBookingExample`). Hộ chiếu hết hạn đúng ngày bay chặng cuối + 6 tháng vẫn hợp lệ, sớm hơn 1 ngày thì không (Task 2 `internationalTripNeedsPassportValidSixMonthsAfterLastDeparture`).

---

## Trước khi bắt đầu

- **Cần có:** Docker Desktop đang chạy, Git Bash, JDK 21 trở lên, Node 24. Plan 01–09 đã xong.
- **Chạy lệnh ở đâu:** mọi lệnh dùng Git Bash và chạy từ thư mục gốc repo, trừ khi bước đó ghi `cd backend` hoặc `cd frontend`. Nếu chạy ở PowerShell, đặt đối số `-D...` trong dấu nháy (`"-Dspring-boot.run.profiles=dev"`).
- **Cổng PostgreSQL:** máy dev của dự án có PostgreSQL Windows chiếm cổng 5432, nên `.env` dùng `DB_PORT=5433` và `DB_URL` khớp. Test dùng Testcontainers nên không ảnh hưởng; chỉ bước chạy profile `dev` ở Task 5 cần.
- **Nhánh:** tạo nhánh mới từ `develop`, VD `feat/10-booking-quote`.
- **Trạng thái kiểm chứng:** plan viết ngày 2026-10-13 và **chưa được chạy thử**. Mọi tên lớp, hàm và kiểu được đối chiếu với code thật (`FlightSearch`, `SeatInventory`, `CatalogApi`, `BaggageOptionRepository`, `VoucherApi`, `VoucherRejection`, `BusinessException`, `GlobalExceptionHandler`, `SecurityConfig`, `TestFlights`, `TestVouchers`, `TestUsers`, `V1__init.sql`). Nếu một bước đỏ ngoài dự kiến, sửa code cho đúng ý định của bước, đừng nới test.

### Bốn điều cần biết khi viết test cho plan này

- **Chuyến tạo qua API thấy được ngay bằng SQL.** Module `flight` ghi bằng `JdbcClient` trong transaction của test, nên khác với entity JPA, không có chuyện "chưa flush". Danh mục (hãng, sân bay, gói giá, hành lý) vẫn tạo bằng SQL như các plan trước.
- **`TestFlights.catalog(jdbc)` chỉ có sân bay QHA, QHB (Việt Nam), QHC (Singapore), QHD (ngừng).** Ví dụ TDD §8.8 cần một chiều nối chuyến nội địa, nên `BookingQuoteTest` tự thêm sân bay `QHE` (Việt Nam).
- **Ngày sinh tính theo `TestFlights.DAY`** (hôm nay + 30 ngày), không ghi cứng năm, để test không đổi kết quả theo ngày chạy.
- **Booking cũ cho BR-30 điều kiện 5** được tạo bằng `INSERT INTO bookings` trong test. Mã đặt chỗ phải khớp `^[A-HJ-NP-Z2-9]{6}$` (không có `0`, `O`, `1`, `I`), VD `QABCDE`.

## Cấu trúc file sau plan

```
backend/src/
├── main/java/vn/edu/uit/flightbooking/
│   ├── catalog/CatalogApi.java                       # Task 4: thêm baggageOptions
│   ├── flight/
│   │   ├── FlightApi.java                            # Task 1 (API công khai)
│   │   └── FlightSearch.java                         # Task 2: percentOf thành public
│   └── booking/
│       ├── domain/
│       │   ├── PassengerType.java, Gender.java, Direction.java   # Task 2
│       │   ├── BookingRequest.java                   # Task 2
│       │   ├── FieldErrors.java, PassengerRules.java, Pricing.java   # Task 2
│       │   ├── Quote.java                            # Task 3
│       │   └── QuoteService.java                     # Task 3, thay toàn bộ ở Task 4
│       └── web/
│           ├── BookingController.java                # Task 3
│           └── QuoteResponse.java                    # Task 3
└── test/java/vn/edu/uit/flightbooking/
    ├── flight/FlightApiTest.java                     # Task 1
    └── booking/
        ├── domain/PassengerRulesTest.java            # Task 2
        ├── domain/PricingTest.java                   # Task 2
        └── web/BookingQuoteTest.java                 # Task 3, thêm test ở Task 4
frontend/src/lib/api-types.ts                         # Task 5 (sinh lại bằng npm run gen:api)
docs/superpowers/plans/2026-10-09-00-roadmap.md       # Task 5
CLAUDE.md                                             # Task 5
```

## API sau plan

| Method | Đường dẫn             | Quyền    | Thành công | Lỗi                                                                                                                                                       |
| ------ | --------------------- | -------- | ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| POST   | `/api/bookings/quote` | Customer | 200        | 400 (`VALIDATION_FAILED`, `ITINERARY_INVALID`, `PASSENGER_RULES_VIOLATED`), 401, 403, 409 (`FLIGHT_NOT_BOOKABLE`, `SEATS_UNAVAILABLE`, `VOUCHER_INVALID`) |

`SecurityConfig` đã có `/api/bookings/**` → `CUSTOMER`, nên không phải sửa.

---

### Task 1: `FlightApi` kiểm tra và định giá hành trình (BR-01–06, BR-04)

**Files:**

- Create: `backend/src/main/java/vn/edu/uit/flightbooking/flight/FlightApi.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/flight/FlightApiTest.java`

**Interfaces:**

- Consumes: `SettingsApi.getInt`, `SettingKey.BOOKING_MIN_HOURS_BEFORE_DEPARTURE`, `SEARCH_MIN_CONNECTION_MINUTES`, `SEARCH_MAX_CONNECTION_MINUTES` (Plan 01, 02); `BusinessException`, `ErrorCode` (Plan 01); `CabinClass` (Plan 05); `FlightStatus` (Plan 06); test: `TestFlights.catalog`, `newFlight`, `createOne`, `DAY` (Plan 06), `TestUsers.loggedIn` (Plan 02).
- Produces:
  - `flight.FlightApi.price(List<JourneyChoice> journeys, int seats) → List<PricedJourney>`: chiều đi ở vị trí 0, chiều về (nếu có) ở vị trí 1. `seats` là số khách chiếm ghế. Chỉ đọc.
  - `FlightApi.JourneyChoice(List<Long> flightIds, long fareFamilyId)`.
  - `FlightApi.PricedJourney(String airlineCode, FareSnapshot fare, List<PricedSegment> segments)`.
  - `FlightApi.FareSnapshot(long fareFamilyId, String name, CabinClass cabinClass, int carryOnKg, int checkedBaggageKg, boolean refundable, long refundFee, boolean changeable, long changeFee)`.
  - `FlightApi.PricedSegment(long flightId, String flightNumber, String departureAirport, String arrivalAirport, OffsetDateTime departureTime, OffsetDateTime arrivalTime, boolean international, long adultPrice)`: giờ theo múi giờ sân bay tương ứng.

- [ ] **Step 1: Viết test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/flight/FlightApiTest.java`:

```java
package vn.edu.uit.flightbooking.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;
import static vn.edu.uit.flightbooking.flight.TestFlights.createOne;
import static vn.edu.uit.flightbooking.flight.TestFlights.newFlight;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.flight.FlightApi.JourneyChoice;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedJourney;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedSegment;

/** BR-01–BR-06 và BR-04 qua {@link FlightApi#price}. Chuyến tạo qua API của Admin như FlightSearchTest. */
@IntegrationTest
@Transactional
class FlightApiTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	FlightApi flights;

	MockHttpSession admin;

	TestFlights.Catalog catalog;

	long firstLeg;

	long secondLeg;

	long back;

	/**
	 * Chiều đi: Q1100 QHA→QHB 06:00–08:10 (Eco 1.000.000, Biz 3.000.000) nối Q1200 QHB→QHC 10:00–12:00 giờ Việt Nam,
	 * tức 13:00 giờ Singapore (chỉ bán Eco, 1.500.000). Chiều về: Q1300 QHC→QHA ngày DAY + 2 lúc 06:00 giờ Singapore
	 * (chỉ bán Eco, 2.000.000). Mỗi chuyến có ECONOMY 100 ghế và BUSINESS 10 ghế.
	 */
	@BeforeEach
	void setUp() throws Exception {
		admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();
		catalog = TestFlights.catalog(jdbc);
		firstLeg = flight(Map.of());
		secondLeg = flight(Map.of("flightNumber", "Q1200", "departureAirport", "QHB", "arrivalAirport", "QHC",
				"departureTime", "10:00", "durationMinutes", 120, "fares", eco(1_500_000)));
		String returnDay = DAY.plusDays(2).toString();
		back = flight(Map.of("flightNumber", "Q1300", "departureAirport", "QHC", "arrivalAirport", "QHA", "fromDate",
				returnDay, "toDate", returnDay, "fares", eco(2_000_000)));
	}

	long flight(Map<String, Object> changes) throws Exception {
		return createOne(mvc, admin, newFlight(catalog, changes));
	}

	List<Map<String, Object>> eco(long price) {
		return List.of(Map.of("fareFamilyId", catalog.economyFare(), "price", price));
	}

	static JourneyChoice choice(long fareFamilyId, Long... flightIds) {
		return new JourneyChoice(List.of(flightIds), fareFamilyId);
	}

	List<PricedJourney> price(int seats, JourneyChoice... journeys) {
		return flights.price(List.of(journeys), seats);
	}

	/** Lỗi nghiệp vụ mà lời gọi ném ra; test hỏng nếu không có lỗi hoặc sai loại lỗi. */
	static BusinessException error(ThrowingCallable call) {
		Throwable thrown = catchThrowable(call);
		assertThat(thrown).isInstanceOf(BusinessException.class);
		return (BusinessException) thrown;
	}

	@Test
	void pricesRoundTripWithLocalTimesAndFareSnapshot() {
		List<PricedJourney> priced = price(2, choice(catalog.economyFare(), firstLeg, secondLeg),
				choice(catalog.economyFare(), back));

		assertThat(priced).hasSize(2);
		PricedJourney outbound = priced.getFirst();
		assertThat(outbound.airlineCode()).isEqualTo("Q1");
		assertThat(outbound.fare().fareFamilyId()).isEqualTo(catalog.economyFare());
		assertThat(outbound.fare().name()).isEqualTo("Eco");
		assertThat(outbound.fare().cabinClass()).isEqualTo(CabinClass.ECONOMY);
		assertThat(outbound.fare().carryOnKg()).isEqualTo(7);
		assertThat(outbound.fare().changeable()).isTrue();
		assertThat(outbound.segments()).extracting(PricedSegment::flightNumber).containsExactly("Q1100", "Q1200");
		assertThat(outbound.segments()).extracting(PricedSegment::adultPrice).containsExactly(1_000_000L, 1_500_000L);
		assertThat(outbound.segments()).extracting(PricedSegment::international).containsExactly(false, true);
		assertThat(outbound.segments().getFirst().departureTime())
			.isEqualTo(OffsetDateTime.parse(DAY + "T06:00:00+07:00"));
		assertThat(outbound.segments().getLast().arrivalTime()).isEqualTo(OffsetDateTime.parse(DAY + "T13:00:00+08:00"));
		assertThat(priced.getLast().segments()).extracting(PricedSegment::adultPrice).containsExactly(2_000_000L);
	}

	/** BR-02, BR-03: 1 hoặc 2 chặng khác nhau; nối chuyến cùng hãng, đúng sân bay, thời gian nối theo tham số. */
	@Test
	void rejectsMalformedJourneys() throws Exception {
		long otherAirline = flight(Map.of("airlineCode", "Q2", "flightNumber", "Q2200", "departureAirport", "QHB",
				"arrivalAirport", "QHC", "departureTime", "10:00", "cabins",
				List.of(Map.of("cabinClass", "ECONOMY", "totalSeats", 50)), "fares",
				List.of(Map.of("fareFamilyId", catalog.otherAirlineFare(), "price", 1_000_000))));
		long eco = catalog.economyFare();

		assertThat(error(() -> price(1, choice(eco))).code()).isEqualTo(ErrorCode.ITINERARY_INVALID);
		assertThat(error(() -> price(1, choice(eco, firstLeg, secondLeg, back))).code())
			.isEqualTo(ErrorCode.ITINERARY_INVALID);
		assertThat(error(() -> price(1, choice(eco, firstLeg, firstLeg))).code()).isEqualTo(ErrorCode.ITINERARY_INVALID);
		assertThat(error(() -> price(1, choice(eco, secondLeg, firstLeg))).code())
			.isEqualTo(ErrorCode.ITINERARY_INVALID);
		assertThat(error(() -> price(1, choice(eco, firstLeg, otherAirline))).code())
			.isEqualTo(ErrorCode.ITINERARY_INVALID);

		// Nối 110 phút: hợp lệ với mặc định 60–720, sai khi tối thiểu là 120.
		jdbc.sql("UPDATE system_settings SET value = '120' WHERE key = 'search.min_connection_minutes'").update();
		assertThat(error(() -> price(1, choice(eco, firstLeg, secondLeg))).code())
			.isEqualTo(ErrorCode.ITINERARY_INVALID);
	}

	/** BR-05: gói của hãng khác là hành trình sai; gói ngừng bán hoặc không bán trên một chặng thì không bán được. */
	@Test
	void checksFareFamilyOnEverySegment() {
		assertThat(error(() -> price(1, choice(catalog.otherAirlineFare(), firstLeg))).code())
			.isEqualTo(ErrorCode.ITINERARY_INVALID);
		assertThat(error(() -> price(1, choice(-1L, firstLeg))).code()).isEqualTo(ErrorCode.ITINERARY_INVALID);
		// Q1200 không bán gói Biz.
		assertThat(error(() -> price(1, choice(catalog.businessFare(), firstLeg, secondLeg))).code())
			.isEqualTo(ErrorCode.FLIGHT_NOT_BOOKABLE);
		assertThat(price(1, choice(catalog.businessFare(), firstLeg)).getFirst().fare().cabinClass())
			.isEqualTo(CabinClass.BUSINESS);

		jdbc.sql("UPDATE fare_families SET active = FALSE WHERE id = ?").param(catalog.economyFare()).update();
		assertThat(error(() -> price(1, choice(catalog.economyFare(), firstLeg))).code())
			.isEqualTo(ErrorCode.FLIGHT_NOT_BOOKABLE);
	}

	/** BR-01: chuyến không còn, đã huỷ, hoặc chặng đầu cất cánh trong vòng N giờ tới (N đọc từ tham số). */
	@Test
	void rejectsFlightsNoLongerOnSale() {
		long eco = catalog.economyFare();
		assertThat(error(() -> price(1, choice(eco, Long.MAX_VALUE))).code()).isEqualTo(ErrorCode.FLIGHT_NOT_BOOKABLE);

		jdbc.sql("UPDATE flights SET status = 'CANCELLED' WHERE id = ?").param(secondLeg).update();
		assertThat(error(() -> price(1, choice(eco, firstLeg, secondLeg))).code())
			.isEqualTo(ErrorCode.FLIGHT_NOT_BOOKABLE);

		jdbc.sql("""
				UPDATE flights SET departure_time = now() + INTERVAL '2 hours', arrival_time = now() + INTERVAL '4 hours'
				WHERE id = ?
				""").param(firstLeg).update();
		BusinessException tooLate = error(() -> price(1, choice(eco, firstLeg)));
		assertThat(tooLate.code()).isEqualTo(ErrorCode.FLIGHT_NOT_BOOKABLE);
		assertThat(tooLate.getMessage()).contains("Q1100");

		jdbc.sql("UPDATE system_settings SET value = '1' WHERE key = 'booking.min_hours_before_departure'").update();
		assertThat(price(1, choice(eco, firstLeg))).hasSize(1);
	}

	/** BR-06: đủ ghế trên mọi chặng trong hạng ghế của gói; câu báo theo mẫu TDD §8.8. */
	@Test
	void needsEnoughSeatsOnEverySegment() {
		jdbc.sql("UPDATE flight_cabins SET available_seats = 3 WHERE flight_id = ? AND cabin_class = 'ECONOMY'")
			.param(secondLeg)
			.update();

		assertThat(price(3, choice(catalog.economyFare(), firstLeg, secondLeg))).hasSize(1);
		BusinessException e = error(() -> price(4, choice(catalog.economyFare(), firstLeg, secondLeg)));
		assertThat(e.code()).isEqualTo(ErrorCode.SEATS_UNAVAILABLE);
		assertThat(e.getMessage()).isEqualTo("Chuyến Q1200 hạng ECONOMY không còn đủ 4 ghế");
	}

	/** BR-04: chiều về bay ngược tuyến chiều đi và cất cánh sau khi chiều đi hạ cánh. */
	@Test
	void returnMustReverseRouteAndDepartAfterOutboundLands() throws Exception {
		long eco = catalog.economyFare();
		// Q1301 QHC→QHA cất cánh 12:30 giờ Singapore, trước khi Q1200 hạ cánh (13:00 giờ Singapore).
		long early = flight(Map.of("flightNumber", "Q1301", "departureAirport", "QHC", "arrivalAirport", "QHA",
				"departureTime", "12:30", "fares", eco(2_000_000)));

		assertThat(error(() -> price(1, choice(eco, firstLeg, secondLeg), choice(eco, early))).code())
			.isEqualTo(ErrorCode.ITINERARY_INVALID);
		assertThat(error(() -> price(1, choice(eco, firstLeg, secondLeg), choice(eco, firstLeg))).code())
			.isEqualTo(ErrorCode.ITINERARY_INVALID);
		assertThat(error(() -> price(1, choice(eco, firstLeg), choice(eco, back))).code())
			.isEqualTo(ErrorCode.ITINERARY_INVALID);
	}

}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
cd backend && ./mvnw test -Dtest=FlightApiTest
```

Expected: `COMPILATION ERROR`, không tìm thấy `FlightApi`.

- [ ] **Step 3: Viết `FlightApi`**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/flight/FlightApi.java`:

```java
package vn.edu.uit.flightbooking.flight;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;
import vn.edu.uit.flightbooking.flight.domain.FlightStatus;

/**
 * Kiểm tra và định giá hành trình khách đã chọn (TDD §3.2, §6.2 bước 2): BR-01–BR-06 cho từng chiều, BR-04 cho khứ
 * hồi, giá người lớn từng chặng (BR-20). Chỉ đọc; giữ ghế là việc của {@link SeatInventory}. Đọc thẳng bảng
 * {@code airports}, {@code fare_families} của catalog bằng SQL, như {@link FlightSearch}.
 */
@Service
public class FlightApi {

	/** Một chiều khách chọn: các chuyến theo thứ tự bay và một gói giá (BR-02, BR-05). */
	public record JourneyChoice(List<Long> flightIds, long fareFamilyId) {
	}

	/** Quy định của gói giá lúc báo giá, để giữ chỗ chốt lại (BR-24). */
	public record FareSnapshot(long fareFamilyId, String name, CabinClass cabinClass, int carryOnKg,
			int checkedBaggageKg, boolean refundable, long refundFee, boolean changeable, long changeFee) {
	}

	/**
	 * Giờ theo múi giờ của sân bay tương ứng (TDD §5.1). {@code international}: hai sân bay khác quốc gia (BR-13).
	 * {@code adultPrice}: giá người lớn của chặng theo gói đã chọn (BR-20).
	 */
	public record PricedSegment(long flightId, String flightNumber, String departureAirport, String arrivalAirport,
			OffsetDateTime departureTime, OffsetDateTime arrivalTime, boolean international, long adultPrice) {
	}

	public record PricedJourney(String airlineCode, FareSnapshot fare, List<PricedSegment> segments) {
	}

	private record Flight(long id, String flightNumber, String airlineCode, String status, String departureAirport,
			String arrivalAirport, OffsetDateTime departureTime, OffsetDateTime arrivalTime, boolean international) {
	}

	private record Family(FareSnapshot snapshot, String airlineCode, boolean active) {
	}

	/** {@code availableSeats} là {@code null} khi chuyến không có hạng ghế của gói. */
	private record Fare(long flightId, long price, Integer availableSeats) {
	}

	private static final String FLIGHTS = """
			SELECT f.id, f.flight_number, f.airline_code, f.status,
			       f.departure_airport, dep.timezone AS departure_timezone, f.departure_time,
			       f.arrival_airport, arr.timezone AS arrival_timezone, f.arrival_time,
			       dep.country_code <> arr.country_code AS international
			FROM flights f
			JOIN airports dep ON dep.code = f.departure_airport
			JOIN airports arr ON arr.code = f.arrival_airport
			WHERE f.id IN (:ids)
			""";

	private static final String FAMILY = """
			SELECT id, airline_code, cabin_class, name, carry_on_kg, checked_baggage_kg,
			       refundable, refund_fee, changeable, change_fee, active
			FROM fare_families
			WHERE id = :id
			""";

	/** Q-06 cho một gói, kèm số ghế còn trong hạng ghế của gói (BR-06). */
	private static final String FARES = """
			SELECT ff.flight_id, ff.price, c.available_seats
			FROM flight_fares ff
			LEFT JOIN flight_cabins c ON c.flight_id = ff.flight_id AND c.cabin_class = :cabin
			WHERE ff.fare_family_id = :fareFamilyId AND ff.flight_id IN (:ids)
			""";

	private final JdbcClient jdbc;

	private final SettingsApi settings;

	FlightApi(JdbcClient jdbc, SettingsApi settings) {
		this.jdbc = jdbc;
		this.settings = settings;
	}

	/**
	 * {@code journeys}: chiều đi, rồi chiều về nếu là khứ hồi. {@code seats}: số khách chiếm ghế (BR-06). Cấu trúc sai
	 * báo ITINERARY_INVALID; chuyến không còn, đã huỷ, quá sát giờ, gói giá ngừng bán báo FLIGHT_NOT_BOOKABLE; thiếu
	 * ghế báo SEATS_UNAVAILABLE.
	 */
	@Transactional(readOnly = true)
	public List<PricedJourney> price(List<JourneyChoice> journeys, int seats) {
		Instant minDeparture = Instant.now()
			.plus(settings.getInt(SettingKey.BOOKING_MIN_HOURS_BEFORE_DEPARTURE), ChronoUnit.HOURS);
		int minConnection = settings.getInt(SettingKey.SEARCH_MIN_CONNECTION_MINUTES);
		int maxConnection = settings.getInt(SettingKey.SEARCH_MAX_CONNECTION_MINUTES);
		List<PricedJourney> priced = new ArrayList<>();
		for (JourneyChoice choice : journeys) {
			priced.add(journey(choice, seats, minDeparture, minConnection, maxConnection));
		}
		if (priced.size() == 2) {
			checkReturn(priced.getFirst(), priced.getLast());
		}
		return priced;
	}

	private PricedJourney journey(JourneyChoice choice, int seats, Instant minDeparture, int minConnection,
			int maxConnection) {
		List<Long> ids = choice.flightIds();
		if (ids.isEmpty() || ids.size() > 2 || ids.stream().distinct().count() < ids.size()) {
			throw invalid("Mỗi chiều gồm 1 chặng bay thẳng hoặc 2 chặng nối chuyến");
		}
		Map<Long, Flight> found = jdbc.sql(FLIGHTS)
			.param("ids", ids)
			.query(FlightApi::flight)
			.list()
			.stream()
			.collect(Collectors.toMap(Flight::id, f -> f));
		if (found.size() < ids.size()) {
			throw notBookable("Chuyến bay đã chọn không còn được bán");
		}
		List<Flight> route = ids.stream().map(found::get).toList();
		Flight first = route.getFirst();
		if (route.size() == 2) {
			checkConnection(first, route.getLast(), minConnection, maxConnection);
		}
		for (Flight f : route) {
			if (!FlightStatus.SCHEDULED.name().equals(f.status())) {
				throw notBookable("Chuyến %s đã bị huỷ".formatted(f.flightNumber()));
			}
		}
		if (first.departureTime().toInstant().isBefore(minDeparture)) {
			throw notBookable("Chuyến %s sắp cất cánh nên không còn bán".formatted(first.flightNumber()));
		}

		Family family = jdbc.sql(FAMILY)
			.param("id", choice.fareFamilyId())
			.query(FlightApi::family)
			.optional()
			.filter(f -> f.airlineCode().equals(first.airlineCode()))
			.orElseThrow(() -> invalid("Gói giá không thuộc hãng khai thác chiều này"));
		FareSnapshot fare = family.snapshot();
		if (!family.active()) {
			throw notBookable("Gói giá %s đã ngừng bán".formatted(fare.name()));
		}
		Map<Long, Fare> fares = jdbc.sql(FARES)
			.param("cabin", fare.cabinClass().name())
			.param("fareFamilyId", fare.fareFamilyId())
			.param("ids", ids)
			.query(FlightApi::fare)
			.list()
			.stream()
			.collect(Collectors.toMap(Fare::flightId, f -> f));

		List<PricedSegment> segments = new ArrayList<>();
		for (Flight f : route) {
			Fare price = fares.get(f.id());
			if (price == null) {
				throw notBookable("Gói giá %s không còn bán trên chuyến %s".formatted(fare.name(), f.flightNumber()));
			}
			if (price.availableSeats() == null || price.availableSeats() < seats) {
				throw new BusinessException(ErrorCode.SEATS_UNAVAILABLE, "Chuyến %s hạng %s không còn đủ %d ghế"
					.formatted(f.flightNumber(), fare.cabinClass(), seats));
			}
			segments.add(new PricedSegment(f.id(), f.flightNumber(), f.departureAirport(), f.arrivalAirport(),
					f.departureTime(), f.arrivalTime(), f.international(), price.price()));
		}
		return new PricedJourney(first.airlineCode(), fare, segments);
	}

	/** BR-03. */
	private static void checkConnection(Flight first, Flight second, int minConnection, int maxConnection) {
		long layover = Duration.between(first.arrivalTime(), second.departureTime()).toMinutes();
		if (!first.airlineCode().equals(second.airlineCode())
				|| !first.arrivalAirport().equals(second.departureAirport()) || layover < minConnection
				|| layover > maxConnection) {
			throw invalid("Hai chặng nối chuyến phải cùng hãng, nối tại cùng sân bay và cách nhau %d–%d phút"
				.formatted(minConnection, maxConnection));
		}
	}

	/** BR-04. */
	private static void checkReturn(PricedJourney outbound, PricedJourney inbound) {
		PricedSegment leave = outbound.segments().getFirst();
		PricedSegment land = outbound.segments().getLast();
		PricedSegment returnStart = inbound.segments().getFirst();
		PricedSegment returnEnd = inbound.segments().getLast();
		if (!returnStart.departureAirport().equals(land.arrivalAirport())
				|| !returnEnd.arrivalAirport().equals(leave.departureAirport())
				|| !returnStart.departureTime().isAfter(land.arrivalTime())) {
			throw invalid("Chiều về phải bay ngược tuyến chiều đi và cất cánh sau khi chiều đi hạ cánh");
		}
	}

	private static BusinessException invalid(String detail) {
		return new BusinessException(ErrorCode.ITINERARY_INVALID, detail);
	}

	private static BusinessException notBookable(String detail) {
		return new BusinessException(ErrorCode.FLIGHT_NOT_BOOKABLE, detail);
	}

	private static Flight flight(ResultSet rs, int rowNum) throws SQLException {
		return new Flight(rs.getLong("id"), rs.getString("flight_number"), rs.getString("airline_code"),
				rs.getString("status"), rs.getString("departure_airport"), rs.getString("arrival_airport"),
				local(rs, "departure_time", "departure_timezone"), local(rs, "arrival_time", "arrival_timezone"),
				rs.getBoolean("international"));
	}

	/** Đổi {@code timestamptz} sang giờ địa phương của sân bay. */
	private static OffsetDateTime local(ResultSet rs, String time, String zone) throws SQLException {
		return rs.getObject(time, OffsetDateTime.class)
			.atZoneSameInstant(ZoneId.of(rs.getString(zone)))
			.toOffsetDateTime();
	}

	private static Family family(ResultSet rs, int rowNum) throws SQLException {
		FareSnapshot snapshot = new FareSnapshot(rs.getLong("id"), rs.getString("name"),
				CabinClass.valueOf(rs.getString("cabin_class")), rs.getInt("carry_on_kg"),
				rs.getInt("checked_baggage_kg"), rs.getBoolean("refundable"), rs.getLong("refund_fee"),
				rs.getBoolean("changeable"), rs.getLong("change_fee"));
		return new Family(snapshot, rs.getString("airline_code"), rs.getBoolean("active"));
	}

	private static Fare fare(ResultSet rs, int rowNum) throws SQLException {
		return new Fare(rs.getLong("flight_id"), rs.getLong("price"), rs.getObject("available_seats", Integer.class));
	}

}
```

- [ ] **Step 4: Chạy test, xác nhận xanh**

```bash
cd backend && ./mvnw test -Dtest='FlightApiTest,ModularityTest'
```

Expected: `Tests run: 7, Failures: 0, Errors: 0` (6 của `FlightApiTest`, 1 của `ModularityTest`).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/flight/FlightApi.java backend/src/test/java/vn/edu/uit/flightbooking/flight/FlightApiTest.java
git commit -m "feat(flight): FlightApi kiểm tra và định giá hành trình đã chọn (BR-01–06, BR-04)"
```

---

### Task 2: Quy tắc hành khách và phép tính tiền (BR-10–13, BR-21, BR-33)

**Files:**

- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/PassengerType.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/Gender.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/Direction.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingRequest.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/FieldErrors.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/PassengerRules.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/Pricing.java`
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/flight/FlightSearch.java:250-253` (hàm `percentOf` thành `public`)
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/booking/domain/PassengerRulesTest.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/booking/domain/PricingTest.java`

**Interfaces:**

- Consumes: `BusinessException(code, detail, properties)`, `ErrorCode.PASSENGER_RULES_VIOLATED` (Plan 01); `FlightSearch.percentOf` (Plan 07).
- Produces:
  - Enum `booking.domain.PassengerType` (`ADULT`, `CHILD`, `INFANT`, có `label()` tiếng Việt), `Gender` (`MALE`, `FEMALE`), `Direction` (`OUTBOUND`, `RETURN`).
  - `booking.domain.BookingRequest(journeys, passengers, baggage, contact, voucherCode)` với các record lồng `JourneyRequest(Direction direction, List<Long> flightIds, Long fareFamilyId)`, `PassengerRequest(PassengerType type, String lastName, String firstName, Gender gender, LocalDate dateOfBirth, String nationality, String passportNumber, LocalDate passportExpiry)`, `BaggageRequest(Integer passengerIndex, Direction direction, Long baggageOptionId)`, `ContactRequest(String name, String email, String phone)`. `baggage` thiếu thì thành `List.of()`.
  - `FieldErrors`: `add(field, message)`, `throwIfAny(ErrorCode, detail)`.
  - `PassengerRules.checkCounts(List<PassengerRequest>, int maxSeated)` (BR-11), `PassengerRules.checkDetails(List<PassengerRequest>, LocalDate firstDeparture, LocalDate lastDeparture, boolean international)` (BR-10, BR-13), `PassengerRules.typeAt(LocalDate dateOfBirth, LocalDate day) → PassengerType`. Cả hai hàm `check…` ném `PASSENGER_RULES_VIOLATED` kèm `errors`.
  - `Pricing.ticketPrice(long adultPrice, PassengerType type, int childPercent, int infantPercent) → long` (BR-21, một vé một chặng), `Pricing.allocate(long discount, List<Long> subtotals) → List<Long>` (BR-33).
  - `FlightSearch.percentOf(long, int)` thành `public`.

- [ ] **Step 1: Viết test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/booking/domain/PassengerRulesTest.java`:

```java
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
```

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/booking/domain/PricingTest.java`:

```java
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
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
cd backend && ./mvnw test -Dtest='PassengerRulesTest,PricingTest'
```

Expected: `COMPILATION ERROR`, không tìm thấy `PassengerType`, `PassengerRules`, `Pricing`, `BookingRequest`.

- [ ] **Step 3: Enum và body request**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/PassengerType.java`:

```java
package vn.edu.uit.flightbooking.booking.domain;

/** Loại khách theo tuổi tại ngày bay (BR-10). Em bé không chiếm ghế (BR-11). */
public enum PassengerType {

	ADULT("người lớn"),

	CHILD("trẻ em"),

	INFANT("em bé");

	private final String label;

	PassengerType(String label) {
		this.label = label;
	}

	/** Tên tiếng Việt, dùng trong câu báo lỗi. */
	public String label() {
		return label;
	}

}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/Gender.java`:

```java
package vn.edu.uit.flightbooking.booking.domain;

/** Cột {@code passengers.gender}. */
public enum Gender {

	MALE,

	FEMALE

}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/Direction.java`:

```java
package vn.edu.uit.flightbooking.booking.domain;

/** Chiều của hành trình, cột {@code booking_journeys.direction}. */
public enum Direction {

	OUTBOUND,

	RETURN

}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingRequest.java`:

```java
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
 * Body chung của POST /bookings/quote (FR-20) và POST /bookings (FR-24, Plan 11), theo ví dụ TDD §8.8. Bean
 * Validation kiểm tra định dạng (BR-12, BR-14, định dạng hộ chiếu của BR-13); quy tắc cần dữ liệu chuyến bay nằm ở
 * {@link PassengerRules} và {@code QuoteService}. {@code voucherCode} rỗng là không dùng voucher.
 */
public record BookingRequest(@NotNull List<@NotNull @Valid JourneyRequest> journeys,
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
	public record JourneyRequest(@NotNull Direction direction, @NotNull List<@NotNull Long> flightIds,
			@NotNull Long fareFamilyId) {
	}

	/** Quốc tịch và hộ chiếu chỉ bắt buộc khi hành trình có chặng quốc tế (BR-13). */
	public record PassengerRequest(@NotNull PassengerType type,
			@NotNull @Size(max = 50) @Pattern(regexp = NAME, message = NAME_MESSAGE) String lastName,
			@NotNull @Size(max = 50) @Pattern(regexp = NAME, message = NAME_MESSAGE) String firstName,
			@NotNull Gender gender, @NotNull LocalDate dateOfBirth,
			@Pattern(regexp = "[A-Z]{2}", message = "Mã quốc gia gồm 2 chữ cái in hoa") String nationality,
			@Pattern(regexp = "[A-Z0-9]{6,20}",
					message = "Số hộ chiếu gồm 6–20 chữ in hoa hoặc chữ số") String passportNumber,
			LocalDate passportExpiry) {
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

}
```

- [ ] **Step 4: Gom lỗi, quy tắc hành khách, phép tính tiền**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/FieldErrors.java`:

```java
package vn.edu.uit.flightbooking.booking.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;

/**
 * Gom lỗi theo trường, cùng định dạng {@code errors} với lỗi Bean Validation (TDD §5.3), để C-07 đánh dấu mọi trường
 * sai trong một lần (APP_FLOW C-07).
 */
final class FieldErrors {

	private final List<Map<String, String>> errors = new ArrayList<>();

	void add(String field, String message) {
		errors.add(Map.of("field", field, "message", message));
	}

	void throwIfAny(ErrorCode code, String detail) {
		if (!errors.isEmpty()) {
			throw new BusinessException(code, detail, Map.of("errors", List.copyOf(errors)));
		}
	}

}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/PassengerRules.java`:

```java
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
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/Pricing.java`:

```java
package vn.edu.uit.flightbooking.booking.domain;

import java.util.ArrayList;
import java.util.List;

import vn.edu.uit.flightbooking.flight.FlightSearch;

/** BR-21 cho từng vé và BR-33, logic thuần (TDD §5.2). */
final class Pricing {

	private Pricing() {
	}

	/** BR-21: giá vé của một khách trên một chặng, làm tròn half-up như tìm kiếm. Plan 11 ghi vào {@code tickets}. */
	static long ticketPrice(long adultPrice, PassengerType type, int childPercent, int infantPercent) {
		return switch (type) {
			case ADULT -> adultPrice;
			case CHILD -> FlightSearch.percentOf(adultPrice, childPercent);
			case INFANT -> FlightSearch.percentOf(adultPrice, infantPercent);
		};
	}

	/**
	 * BR-33: chia {@code discount} theo tỉ lệ tạm tính từng chiều, làm tròn xuống; chiều cuối nhận phần còn lại.
	 * {@code subtotals} không rỗng và có tổng dương (giá vé luôn lớn hơn 0).
	 */
	static List<Long> allocate(long discount, List<Long> subtotals) {
		long total = subtotals.stream().mapToLong(Long::longValue).sum();
		List<Long> shares = new ArrayList<>();
		long given = 0;
		for (int i = 0; i < subtotals.size() - 1; i++) {
			// ponytail: multiplyExact báo lỗi thay vì tràn số âm thầm; tạm tính thực tế vài tỉ đồng còn xa ngưỡng long.
			long share = Math.multiplyExact(discount, subtotals.get(i)) / total;
			shares.add(share);
			given += share;
		}
		shares.add(discount - given);
		return shares;
	}

}
```

Trong `backend/src/main/java/vn/edu/uit/flightbooking/flight/FlightSearch.java`, thay:

```java
	/** Làm tròn half-up, đúng với số không âm (TDD §5.2). */
	static long percentOf(long amount, int percent) {
```

bằng:

```java
	/** Làm tròn half-up, đúng với số không âm (TDD §5.2). Booking dùng lại cho giá từng vé (Plan 10). */
	public static long percentOf(long amount, int percent) {
```

- [ ] **Step 5: Chạy test, xác nhận xanh**

```bash
cd backend && ./mvnw test -Dtest='PassengerRulesTest,PricingTest,TicketPriceTest,ModularityTest'
```

Expected: `Tests run: 10, Failures: 0, Errors: 0` (4 + 2 + 3 + 1). `ModularityTest` xác nhận module `booking` mới chỉ dùng API công khai của `flight`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/booking backend/src/main/java/vn/edu/uit/flightbooking/flight/FlightSearch.java backend/src/test/java/vn/edu/uit/flightbooking/booking
git commit -m "feat(booking): quy tắc hành khách BR-10–13 và tính giá BR-21, BR-33"
```

---

### Task 3: `POST /api/bookings/quote`: kiểm tra và giá vé (FR-20, FR-21)

**Files:**

- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/Quote.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/QuoteService.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/QuoteResponse.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/BookingController.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/booking/web/BookingQuoteTest.java`

**Interfaces:**

- Consumes: `FlightApi.price`, `JourneyChoice`, `PricedJourney`, `PricedSegment` (Task 1); `BookingRequest`, `PassengerRules`, `Pricing`, `PassengerType`, `Direction` (Task 2); `SettingsApi.getInt`, `SettingKey.BOOKING_MAX_SEATED_PASSENGERS`, `PRICING_CHILD_PERCENT`, `PRICING_INFANT_PERCENT` (Plan 01, 02); `CurrentUser` (Plan 02); test: `TestFlights`, `TestUsers`, `TestCsrf`.
- Produces:
  - `booking.domain.QuoteService.quote(long userId, BookingRequest request) → Quote` (`@Transactional(readOnly = true)`).
  - `booking.domain.Quote(List<JourneyQuote> journeys, Long voucherId, long fareTotal, long baggageTotal, long discountTotal, long totalAmount)`, với `Quote.JourneyQuote(Direction direction, FlightApi.PricedJourney itinerary, List<PassengerFare> passengerFares, List<BaggageLine> baggage, long fareTotal, long baggageTotal, long discount)`, `Quote.PassengerFare(PassengerType type, int count, long unitPrice, long total)`, `Quote.BaggageLine(int passengerIndex, long baggageOptionId, int weightKg, long price)`.
  - `booking.web.QuoteResponse(List<JourneyTotals> journeys, long fareTotal, long baggageTotal, long discountTotal, long totalAmount)`, `QuoteResponse.JourneyTotals(Direction direction, long fareTotal, long baggageTotal, long discount, List<Quote.PassengerFare> passengerFares)`, `QuoteResponse.of(Quote)`.
  - `POST /api/bookings/quote` (200).
  - Test: `BookingQuoteTest` với `setUp`, `journey`, `passenger`, `body`, `quote` mà Task 4 dùng lại.

- [ ] **Step 1: Viết test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/booking/web/BookingQuoteTest.java`:

```java
package vn.edu.uit.flightbooking.booking.web;

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

}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
cd backend && ./mvnw test -Dtest=BookingQuoteTest
```

Expected: `Tests run: 7`. `onlyCustomersCanQuote` xanh vì `SecurityConfig` đã chặn từ trước. Sáu test còn lại đỏ: 404 `RESOURCE_NOT_FOUND`, vì chưa có route `/api/bookings/quote`.

- [ ] **Step 3: Kết quả báo giá**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/Quote.java`:

```java
package vn.edu.uit.flightbooking.booking.domain;

import java.util.List;

import vn.edu.uit.flightbooking.flight.FlightApi;

/**
 * Kết quả báo giá (FR-20, TDD §6.2 bước 1–4). Giữ chỗ (Plan 11) dùng lại để ghi booking và chốt giá (BR-24).
 * {@code voucherId} là {@code null} khi không dùng voucher. {@code totalAmount = fareTotal + baggageTotal −
 * discountTotal} (BR-23).
 */
public record Quote(List<JourneyQuote> journeys, Long voucherId, long fareTotal, long baggageTotal,
		long discountTotal, long totalAmount) {

	/**
	 * {@code itinerary}: hành trình đã kiểm tra, kèm giá người lớn từng chặng và quy định gói giá. {@code discount}:
	 * phần mức giảm của chiều (BR-33).
	 */
	public record JourneyQuote(Direction direction, FlightApi.PricedJourney itinerary,
			List<PassengerFare> passengerFares, List<BaggageLine> baggage, long fareTotal, long baggageTotal,
			long discount) {
	}

	/** Giá vé của một loại khách trên cả chiều (BR-21): {@code unitPrice} cho một khách, {@code total} cho cả nhóm. */
	public record PassengerFare(PassengerType type, int count, long unitPrice, long total) {
	}

	/** Một mức hành lý mua thêm, kèm cân nặng và giá tại lúc báo giá (BR-22, BR-24). */
	public record BaggageLine(int passengerIndex, long baggageOptionId, int weightKg, long price) {
	}

}
```

- [ ] **Step 4: `QuoteService` (chưa có hành lý và voucher)**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/QuoteService.java`:

```java
package vn.edu.uit.flightbooking.booking.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.booking.domain.BookingRequest.JourneyRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest.PassengerRequest;
import vn.edu.uit.flightbooking.booking.domain.Quote.JourneyQuote;
import vn.edu.uit.flightbooking.booking.domain.Quote.PassengerFare;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;
import vn.edu.uit.flightbooking.flight.FlightApi;
import vn.edu.uit.flightbooking.flight.FlightApi.JourneyChoice;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedJourney;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedSegment;

/**
 * Báo giá (FR-20, FR-21, TDD §6.2 bước 1–4). Giữ chỗ (Plan 11) gọi lại {@link #quote} rồi mới giữ ghế và lượt
 * voucher, nên lỗi nào xảy ra khi giữ chỗ cũng hiện ra khi báo giá.
 */
@Service
public class QuoteService {

	private final FlightApi flights;

	private final SettingsApi settings;

	QuoteService(FlightApi flights, SettingsApi settings) {
		this.flights = flights;
		this.settings = settings;
	}

	/** Chỉ đọc: không giữ ghế, không ghi DB (FR-20). {@code userId}: người đặt. */
	@Transactional(readOnly = true)
	public Quote quote(long userId, BookingRequest request) {
		List<JourneyRequest> requested = request.journeys();
		checkDirections(requested);
		List<PassengerRequest> passengers = request.passengers();
		PassengerRules.checkCounts(passengers, settings.getInt(SettingKey.BOOKING_MAX_SEATED_PASSENGERS));
		int seated = (int) passengers.stream().filter(p -> p.type() != PassengerType.INFANT).count();
		List<PricedJourney> itineraries = flights
			.price(requested.stream().map(j -> new JourneyChoice(j.flightIds(), j.fareFamilyId())).toList(), seated);
		List<PricedSegment> segments = itineraries.stream().flatMap(j -> j.segments().stream()).toList();
		PassengerRules.checkDetails(passengers, segments.getFirst().departureTime().toLocalDate(),
				segments.getLast().departureTime().toLocalDate(),
				segments.stream().anyMatch(PricedSegment::international));

		int childPercent = settings.getInt(SettingKey.PRICING_CHILD_PERCENT);
		int infantPercent = settings.getInt(SettingKey.PRICING_INFANT_PERCENT);
		Map<PassengerType, Long> counts = passengers.stream()
			.collect(Collectors.groupingBy(PassengerRequest::type, () -> new EnumMap<>(PassengerType.class),
					Collectors.counting()));
		List<JourneyQuote> journeys = IntStream.range(0, itineraries.size()).mapToObj(i -> {
			List<PassengerFare> fares = passengerFares(itineraries.get(i), counts, childPercent, infantPercent);
			long fareTotal = fares.stream().mapToLong(PassengerFare::total).sum();
			return new JourneyQuote(requested.get(i).direction(), itineraries.get(i), fares, List.of(), fareTotal, 0,
					0);
		}).toList();
		long fareTotal = journeys.stream().mapToLong(JourneyQuote::fareTotal).sum();
		return new Quote(journeys, null, fareTotal, 0, 0, fareTotal);
	}

	/** Chiều đi, rồi chiều về nếu là khứ hồi (FR-12), đúng thứ tự đó. */
	private static void checkDirections(List<JourneyRequest> journeys) {
		List<Direction> directions = journeys.stream().map(JourneyRequest::direction).toList();
		if (!directions.equals(List.of(Direction.OUTBOUND))
				&& !directions.equals(List.of(Direction.OUTBOUND, Direction.RETURN))) {
			throw new BusinessException(ErrorCode.ITINERARY_INVALID,
					"Hành trình gồm chiều đi, và chiều về nếu là khứ hồi");
		}
	}

	/** BR-21 trên từng chặng rồi cộng lại; loại khách theo thứ tự ADULT, CHILD, INFANT. */
	private static List<PassengerFare> passengerFares(PricedJourney itinerary, Map<PassengerType, Long> counts,
			int childPercent, int infantPercent) {
		return counts.entrySet().stream().map(e -> {
			long unitPrice = itinerary.segments()
				.stream()
				.mapToLong(s -> Pricing.ticketPrice(s.adultPrice(), e.getKey(), childPercent, infantPercent))
				.sum();
			return new PassengerFare(e.getKey(), e.getValue().intValue(), unitPrice, unitPrice * e.getValue());
		}).toList();
	}

}
```

- [ ] **Step 5: Phản hồi và controller**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/QuoteResponse.java`:

```java
package vn.edu.uit.flightbooking.booking.web;

import java.util.List;

import vn.edu.uit.flightbooking.booking.domain.Direction;
import vn.edu.uit.flightbooking.booking.domain.Quote;

/**
 * Phản hồi của POST /bookings/quote, đúng các trường của ví dụ TDD §8.8, thêm {@code passengerFares} để C-07 hiện
 * giá vé theo loại khách (FR-20). Không lộ các trường nội bộ mà giữ chỗ cần.
 */
public record QuoteResponse(List<JourneyTotals> journeys, long fareTotal, long baggageTotal, long discountTotal,
		long totalAmount) {

	public record JourneyTotals(Direction direction, long fareTotal, long baggageTotal, long discount,
			List<Quote.PassengerFare> passengerFares) {
	}

	static QuoteResponse of(Quote quote) {
		return new QuoteResponse(quote.journeys()
			.stream()
			.map(j -> new JourneyTotals(j.direction(), j.fareTotal(), j.baggageTotal(), j.discount(),
					j.passengerFares()))
			.toList(), quote.fareTotal(), quote.baggageTotal(), quote.discountTotal(), quote.totalAmount());
	}

}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/BookingController.java`:

```java
package vn.edu.uit.flightbooking.booking.web;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.booking.domain.BookingRequest;
import vn.edu.uit.flightbooking.booking.domain.QuoteService;
import vn.edu.uit.flightbooking.common.CurrentUser;

/** Màn hình C-07: báo giá (FR-20). Giữ chỗ (FR-24) thêm ở Plan 11. Quyền CUSTOMER theo đường dẫn ở SecurityConfig. */
@RestController
@RequestMapping("/api/bookings")
class BookingController {

	private final QuoteService quotes;

	BookingController(QuoteService quotes) {
		this.quotes = quotes;
	}

	@PostMapping("/quote")
	QuoteResponse quote(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody BookingRequest body) {
		return QuoteResponse.of(quotes.quote(me.id(), body));
	}

}
```

- [ ] **Step 6: Chạy test, xác nhận xanh**

```bash
cd backend && ./mvnw test -Dtest='BookingQuoteTest,ModularityTest'
```

Expected: `Tests run: 8, Failures: 0, Errors: 0` (7 + 1).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/booking backend/src/test/java/vn/edu/uit/flightbooking/booking
git commit -m "feat(booking): API báo giá POST /bookings/quote (FR-20, FR-21)"
```

---

### Task 4: Hành lý và voucher trong báo giá (FR-22, FR-23, BR-30 điều kiện 5, BR-33)

**Files:**

- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/catalog/CatalogApi.java`
- Modify (thay toàn bộ): `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/QuoteService.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/booking/web/BookingQuoteTest.java` (thêm import, hàm phụ, 4 test)

**Interfaces:**

- Consumes: `BaggageOptionRepository` (Plan 05); `VoucherApi.check`, `VoucherApi.Applied`, `VoucherRejection.ALREADY_USED` (Plan 09); `FieldErrors`, `Pricing.allocate` (Task 2); `Quote.BaggageLine` (Task 3); test: `TestVouchers.insert`, `TestVouchers.usedCount` (Plan 09), `TestUsers.create`, `TestUsers.randomEmail` (Plan 02).
- Produces:
  - `catalog.CatalogApi.baggageOptions(Collection<Long> ids) → Map<Long, CatalogApi.BaggageRef>`; `CatalogApi.BaggageRef(String airlineCode, int weightKg, long price, boolean active)`. Id không tồn tại thì không có trong map.
  - `QuoteService` đủ bước 4 của TDD §6.2: `Quote.voucherId`, `JourneyQuote.baggage`, `baggageTotal`, `discount` có giá trị thật.

- [ ] **Step 1: Viết test**

Trong `backend/src/test/java/vn/edu/uit/flightbooking/booking/web/BookingQuoteTest.java`, thêm vào nhóm import:

```java
import static org.assertj.core.api.Assertions.assertThat;

import vn.edu.uit.flightbooking.promotion.TestVouchers;
```

rồi thêm vào cuối lớp (trước dấu `}` cuối cùng):

```java
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
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
cd backend && ./mvnw test -Dtest=BookingQuoteTest
```

Expected: `Tests run: 11, Failures: 4`. Bảy test của Task 3 vẫn xanh. Bốn test mới đỏ vì `QuoteService` của Task 3 bỏ qua `baggage` và `voucherCode`; VD `matchesTddBookingExample` báo `$.journeys[*].baggageTotal` là `[0, 0]`.

- [ ] **Step 3: `CatalogApi.baggageOptions`**

Thay toàn bộ `backend/src/main/java/vn/edu/uit/flightbooking/catalog/CatalogApi.java` bằng:

```java
package vn.edu.uit.flightbooking.catalog;

import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.domain.Airline;
import vn.edu.uit.flightbooking.catalog.domain.BaggageOption;
import vn.edu.uit.flightbooking.catalog.domain.FareFamily;
import vn.edu.uit.flightbooking.catalog.infra.AirlineRepository;
import vn.edu.uit.flightbooking.catalog.infra.AirportRepository;
import vn.edu.uit.flightbooking.catalog.infra.BaggageOptionRepository;
import vn.edu.uit.flightbooking.catalog.infra.FareFamilyRepository;

/**
 * Những gì module khác cần đọc từ danh mục (TDD §3.2): flight dùng khi tạo và sửa chuyến, booking dùng khi báo giá
 * hành lý.
 */
@Service
public class CatalogApi {

	/** {@code zone} lấy từ tên múi giờ IANA của sân bay (BR-90). */
	public record AirportRef(ZoneId zone, boolean active) {
	}

	/** Một mức hành lý, kể cả đã ngừng bán; giá cho mỗi hành khách, mỗi chiều (BR-22). */
	public record BaggageRef(String airlineCode, int weightKg, long price, boolean active) {
	}

	private final AirportRepository airports;

	private final AirlineRepository airlines;

	private final FareFamilyRepository fareFamilies;

	private final BaggageOptionRepository baggage;

	CatalogApi(AirportRepository airports, AirlineRepository airlines, FareFamilyRepository fareFamilies,
			BaggageOptionRepository baggage) {
		this.airports = airports;
		this.airlines = airlines;
		this.fareFamilies = fareFamilies;
		this.baggage = baggage;
	}

	@Transactional(readOnly = true)
	public Optional<AirportRef> airport(String code) {
		return airports.findById(code).map(a -> new AirportRef(ZoneId.of(a.getTimezone()), a.isActive()));
	}

	/** Hãng không tồn tại cũng trả {@code false}. */
	@Transactional(readOnly = true)
	public boolean airlineActive(String code) {
		return airlines.findById(code).map(Airline::isActive).orElse(false);
	}

	/** Mọi gói giá của hãng, kể cả đã ngừng bán: id gói → hạng ghế. */
	@Transactional(readOnly = true)
	public Map<Long, CabinClass> fareFamilyCabins(String airlineCode) {
		return fareFamilies.findByAirlineCodeOrderById(airlineCode)
			.stream()
			.collect(Collectors.toMap(FareFamily::getId, FareFamily::getCabinClass));
	}

	/** Mức hành lý theo id; id không tồn tại thì không có trong kết quả. */
	@Transactional(readOnly = true)
	public Map<Long, BaggageRef> baggageOptions(Collection<Long> ids) {
		return baggage.findAllById(ids)
			.stream()
			.collect(Collectors.toMap(BaggageOption::getId,
					b -> new BaggageRef(b.getAirlineCode(), b.getWeightKg(), b.getPrice(), b.isActive())));
	}

}
```

- [ ] **Step 4: `QuoteService` đầy đủ**

Thay toàn bộ `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/QuoteService.java` bằng:

```java
package vn.edu.uit.flightbooking.booking.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.booking.domain.BookingRequest.BaggageRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest.JourneyRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest.PassengerRequest;
import vn.edu.uit.flightbooking.booking.domain.Quote.BaggageLine;
import vn.edu.uit.flightbooking.booking.domain.Quote.JourneyQuote;
import vn.edu.uit.flightbooking.booking.domain.Quote.PassengerFare;
import vn.edu.uit.flightbooking.catalog.CatalogApi;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;
import vn.edu.uit.flightbooking.flight.FlightApi;
import vn.edu.uit.flightbooking.flight.FlightApi.JourneyChoice;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedJourney;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedSegment;
import vn.edu.uit.flightbooking.promotion.VoucherApi;
import vn.edu.uit.flightbooking.promotion.VoucherRejection;

/**
 * Báo giá (FR-20–FR-23, TDD §6.2 bước 1–4). Giữ chỗ (Plan 11) gọi lại {@link #quote} rồi mới giữ ghế và lượt
 * voucher, nên lỗi nào xảy ra khi giữ chỗ cũng hiện ra khi báo giá.
 */
@Service
public class QuoteService {

	/**
	 * BR-30 điều kiện 5, cùng điều kiện với {@code uq_bookings_voucher_user}. Hai request đồng thời vẫn có thể cùng qua
	 * truy vấn này, nên giữ chỗ (Plan 11) phải bắt thêm vi phạm unique index khi ghi.
	 */
	private static final String VOUCHER_USED = """
			SELECT EXISTS (
			    SELECT 1 FROM bookings
			    WHERE voucher_id = :voucherId AND user_id = :userId
			      AND status IN ('PENDING_PAYMENT', 'ISSUED', 'REFUNDED'))
			""";

	private final FlightApi flights;

	private final CatalogApi catalog;

	private final VoucherApi vouchers;

	private final SettingsApi settings;

	private final JdbcClient jdbc;

	QuoteService(FlightApi flights, CatalogApi catalog, VoucherApi vouchers, SettingsApi settings, JdbcClient jdbc) {
		this.flights = flights;
		this.catalog = catalog;
		this.vouchers = vouchers;
		this.settings = settings;
		this.jdbc = jdbc;
	}

	/**
	 * Chỉ đọc: không giữ ghế, không giữ lượt voucher, không ghi DB (FR-20). {@code userId}: người đặt, để kiểm tra BR-30
	 * điều kiện 5.
	 */
	@Transactional(readOnly = true)
	public Quote quote(long userId, BookingRequest request) {
		List<JourneyRequest> requested = request.journeys();
		checkDirections(requested);
		List<Direction> directions = requested.stream().map(JourneyRequest::direction).toList();
		List<PassengerRequest> passengers = request.passengers();
		PassengerRules.checkCounts(passengers, settings.getInt(SettingKey.BOOKING_MAX_SEATED_PASSENGERS));
		int seated = (int) passengers.stream().filter(p -> p.type() != PassengerType.INFANT).count();
		List<PricedJourney> itineraries = flights
			.price(requested.stream().map(j -> new JourneyChoice(j.flightIds(), j.fareFamilyId())).toList(), seated);
		List<PricedSegment> segments = itineraries.stream().flatMap(j -> j.segments().stream()).toList();
		PassengerRules.checkDetails(passengers, segments.getFirst().departureTime().toLocalDate(),
				segments.getLast().departureTime().toLocalDate(),
				segments.stream().anyMatch(PricedSegment::international));
		List<List<BaggageLine>> baggage = baggage(request.baggage(), passengers, directions, itineraries);

		int childPercent = settings.getInt(SettingKey.PRICING_CHILD_PERCENT);
		int infantPercent = settings.getInt(SettingKey.PRICING_INFANT_PERCENT);
		Map<PassengerType, Long> counts = passengers.stream()
			.collect(Collectors.groupingBy(PassengerRequest::type, () -> new EnumMap<>(PassengerType.class),
					Collectors.counting()));
		List<List<PassengerFare>> fares = itineraries.stream()
			.map(itinerary -> passengerFares(itinerary, counts, childPercent, infantPercent))
			.toList();
		List<Long> fareTotals = fares.stream().map(f -> f.stream().mapToLong(PassengerFare::total).sum()).toList();
		List<Long> baggageTotals = baggage.stream().map(b -> b.stream().mapToLong(BaggageLine::price).sum()).toList();
		List<Long> subtotals = IntStream.range(0, itineraries.size())
			.mapToObj(i -> fareTotals.get(i) + baggageTotals.get(i))
			.toList();
		long fareTotal = sum(fareTotals);
		long baggageTotal = sum(baggageTotals);

		Optional<VoucherApi.Applied> voucher = voucher(userId, request.voucherCode(), fareTotal + baggageTotal);
		long discount = voucher.map(VoucherApi.Applied::discount).orElse(0L);
		List<Long> shares = Pricing.allocate(discount, subtotals);
		List<JourneyQuote> journeys = IntStream.range(0, itineraries.size())
			.mapToObj(i -> new JourneyQuote(directions.get(i), itineraries.get(i), fares.get(i), baggage.get(i),
					fareTotals.get(i), baggageTotals.get(i), shares.get(i)))
			.toList();
		return new Quote(journeys, voucher.map(VoucherApi.Applied::voucherId).orElse(null), fareTotal, baggageTotal,
				discount, fareTotal + baggageTotal - discount);
	}

	/** Chiều đi, rồi chiều về nếu là khứ hồi (FR-12), đúng thứ tự đó. */
	private static void checkDirections(List<JourneyRequest> journeys) {
		List<Direction> directions = journeys.stream().map(JourneyRequest::direction).toList();
		if (!directions.equals(List.of(Direction.OUTBOUND))
				&& !directions.equals(List.of(Direction.OUTBOUND, Direction.RETURN))) {
			throw new BusinessException(ErrorCode.ITINERARY_INVALID,
					"Hành trình gồm chiều đi, và chiều về nếu là khứ hồi");
		}
	}

	/** BR-21 trên từng chặng rồi cộng lại; loại khách theo thứ tự ADULT, CHILD, INFANT. */
	private static List<PassengerFare> passengerFares(PricedJourney itinerary, Map<PassengerType, Long> counts,
			int childPercent, int infantPercent) {
		return counts.entrySet().stream().map(e -> {
			long unitPrice = itinerary.segments()
				.stream()
				.mapToLong(s -> Pricing.ticketPrice(s.adultPrice(), e.getKey(), childPercent, infantPercent))
				.sum();
			return new PassengerFare(e.getKey(), e.getValue().intValue(), unitPrice, unitPrice * e.getValue());
		}).toList();
	}

	/**
	 * BR-22: mỗi khách (trừ em bé) chọn tối đa một mức mỗi chiều, trong bảng giá đang bán của hãng khai thác chiều đó.
	 * Trả các mức đã chọn của từng chiều, cùng thứ tự với {@code directions}. Mọi mục sai báo một lần.
	 */
	private List<List<BaggageLine>> baggage(List<BaggageRequest> requests, List<PassengerRequest> passengers,
			List<Direction> directions, List<PricedJourney> itineraries) {
		Map<Long, CatalogApi.BaggageRef> options = catalog
			.baggageOptions(requests.stream().map(BaggageRequest::baggageOptionId).toList());
		List<List<BaggageLine>> lines = new ArrayList<>();
		directions.forEach(d -> lines.add(new ArrayList<>()));
		Set<String> chosen = new HashSet<>();
		FieldErrors errors = new FieldErrors();
		for (int i = 0; i < requests.size(); i++) {
			BaggageRequest b = requests.get(i);
			String field = "baggage[%d].".formatted(i);
			int journey = directions.indexOf(b.direction());
			CatalogApi.BaggageRef option = options.get(b.baggageOptionId());
			if (b.passengerIndex() >= passengers.size()) {
				errors.add(field + "passengerIndex", "Không có hành khách này");
			}
			else if (passengers.get(b.passengerIndex()).type() == PassengerType.INFANT) {
				errors.add(field + "passengerIndex", "Em bé không được mua thêm hành lý");
			}
			else if (journey < 0) {
				errors.add(field + "direction", "Hành trình không có chiều này");
			}
			else if (!chosen.add(b.passengerIndex() + ":" + b.direction())) {
				errors.add(field + "direction", "Mỗi hành khách chỉ chọn một mức hành lý cho mỗi chiều");
			}
			else if (option == null || !option.active()
					|| !option.airlineCode().equals(itineraries.get(journey).airlineCode())) {
				errors.add(field + "baggageOptionId", "Mức hành lý không có trong bảng giá đang bán của hãng");
			}
			else {
				lines.get(journey)
					.add(new BaggageLine(b.passengerIndex(), b.baggageOptionId(), option.weightKg(), option.price()));
			}
		}
		errors.throwIfAny(ErrorCode.VALIDATION_FAILED, "Dữ liệu không hợp lệ");
		return lines;
	}

	/** BR-30, BR-31. Mã trống là không dùng voucher. Điều kiện 5 kiểm tra sau điều kiện 1–4 (thứ tự ở TDD §9). */
	private Optional<VoucherApi.Applied> voucher(long userId, String code, long subtotal) {
		if (code == null || code.isBlank()) {
			return Optional.empty();
		}
		VoucherApi.Applied applied = vouchers.check(code, subtotal);
		boolean used = jdbc.sql(VOUCHER_USED)
			.param("voucherId", applied.voucherId())
			.param("userId", userId)
			.query(Boolean.class)
			.single();
		if (used) {
			throw VoucherRejection.ALREADY_USED.toException();
		}
		return Optional.of(applied);
	}

	private static long sum(List<Long> amounts) {
		return amounts.stream().mapToLong(Long::longValue).sum();
	}

}
```

- [ ] **Step 5: Chạy test, xác nhận xanh**

```bash
cd backend && ./mvnw test -Dtest='BookingQuoteTest,FlightApiTest,CreateFlightTest,EditFlightTest,ModularityTest'
```

Expected: `BUILD SUCCESS`, 0 failures. `BookingQuoteTest` chạy 11 test, `FlightApiTest` 6. `CreateFlightTest` và `EditFlightTest` xác nhận module `flight` vẫn chạy với constructor mới của `CatalogApi`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/catalog/CatalogApi.java backend/src/main/java/vn/edu/uit/flightbooking/booking backend/src/test/java/vn/edu/uit/flightbooking/booking
git commit -m "feat(booking): hành lý và voucher trong báo giá (FR-22, FR-23, BR-30 điều kiện 5, BR-33)"
```

---

### Task 5: Kiểu frontend, lộ trình và kiểm tra toàn bộ

**Files:**

- Modify (sinh lại): `frontend/src/lib/api-types.ts`
- Modify: `docs/superpowers/plans/2026-10-09-00-roadmap.md` (4 chỗ: dòng "Plan chi tiết đã có", cột "Xong" của Plan 10 ở §2, thêm dòng Plan 10 ở §6, thêm mục 11 ở §7)
- Modify: `CLAUDE.md` (dòng Status)

**Interfaces:**

- Consumes: API của Task 3, 4.
- Produces: `Schemas["BookingRequest"]`, `["JourneyRequest"]`, `["PassengerRequest"]`, `["BaggageRequest"]`, `["ContactRequest"]`, `["QuoteResponse"]`, `["JourneyTotals"]`, `["PassengerFare"]` cho Plan 13 (màn C-07); hợp đồng `FlightApi`, `QuoteService`, `CatalogApi.baggageOptions` trong lộ trình §6 cho Plan 11.

- [ ] **Step 1: Chạy backend profile `dev` và sinh lại kiểu TypeScript**

Terminal 1:

```bash
docker compose up -d postgres mailpit
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

(PowerShell: `./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"`.) Khi log báo `Started FlightBookingApplication`, chạy ở terminal 2:

```bash
cd frontend && npm run gen:api
git diff --stat src/lib/api-types.ts
grep -n "/api/bookings/quote\|BookingRequest\|QuoteResponse\|JourneyTotals\|PassengerFare" src/lib/api-types.ts
```

Expected: `api-types.ts` có đường dẫn `/api/bookings/quote` và các schema `BookingRequest`, `JourneyRequest`, `PassengerRequest`, `BaggageRequest`, `ContactRequest`, `QuoteResponse`, `JourneyTotals`, `PassengerFare`. Dừng backend (Ctrl+C ở terminal 1).

Lưu ý cho Plan 13: với cờ `--properties-required-by-default`, kiểu sinh ra coi mọi trường là bắt buộc. Thực tế `baggage`, `voucherCode`, `nationality`, `passportNumber`, `passportExpiry` được để `null` hoặc bỏ qua, nên form C-07 phải gửi `null` cho các trường hộ chiếu khi chuyến là nội địa.

- [ ] **Step 2: Cập nhật lộ trình**

Trong `docs/superpowers/plans/2026-10-09-00-roadmap.md`:

1. Dòng bảng đầu tài liệu: ngay sau `[Plan 09 — Voucher](2026-10-12-09-voucher.md)` thêm ` · [Plan 10 — Báo giá booking](2026-10-13-10-booking-quote.md)`.
2. Bảng §2, dòng Plan 10: đổi `| P0 (FR-22, 23: P1) | ☐ |` thành `| P0 (FR-22, 23: P1) | ☑ |`.
3. Bảng §6: ngay sau dòng bắt đầu bằng `| 09 | Module \`promotion\``, thêm dòng:

```
| 10 | Module `booking` (phụ thuộc `common`, `catalog`, `flight`, `promotion`), chưa có entity JPA và chưa ghi bảng nào. `flight.FlightApi.price(List<JourneyChoice(flightIds, fareFamilyId)>, seats) → List<PricedJourney(airlineCode, fare, segments)>`: chiều đi ở vị trí 0, chiều về ở vị trí 1; `seats` là số khách chiếm ghế; chỉ đọc, không khoá. Kiểm tra BR-01–06 và BR-04: cấu trúc sai báo `ITINERARY_INVALID`; chuyến không còn, đã huỷ, quá sát giờ, gói ngừng bán hoặc không bán trên một chặng báo `FLIGHT_NOT_BOOKABLE`; thiếu ghế báo `SEATS_UNAVAILABLE` ("Chuyến X hạng Y không còn đủ N ghế"). `FareSnapshot(fareFamilyId, name, cabinClass, carryOnKg, checkedBaggageKg, refundable, refundFee, changeable, changeFee)` để chốt quy định gói (BR-24); `PricedSegment(flightId, flightNumber, departureAirport, arrivalAirport, departureTime, arrivalTime, international, adultPrice)`, giờ theo múi giờ sân bay. `FlightSearch.percentOf` thành công khai. `catalog.CatalogApi.baggageOptions(ids) → Map<id, BaggageRef(airlineCode, weightKg, price, active)>`. `booking.domain.QuoteService.quote(userId, BookingRequest) → Quote(journeys, voucherId, fareTotal, baggageTotal, discountTotal, totalAmount)`, mỗi chiều là `JourneyQuote(direction, itinerary, passengerFares, baggage, fareTotal, baggageTotal, discount)`, kèm `PassengerFare(type, count, unitPrice, total)` và `BaggageLine(passengerIndex, baggageOptionId, weightKg, price)`; chỉ đọc. Plan 11 gọi lại hàm này trong transaction ghi, rồi khoá chuyến, giữ ghế và giữ lượt voucher. `BookingRequest` là body chung của quote và giữ chỗ: `journeys[{direction, flightIds, fareFamilyId}]`, `passengers[{type, lastName, firstName, gender, dateOfBirth, nationality, passportNumber, passportExpiry}]`, `baggage[{passengerIndex, direction, baggageOptionId}]` (thiếu thì là rỗng), `contact{name, email, phone}`, `voucherCode` (rỗng là không dùng). Enum `booking.domain.PassengerType` (có `label()`), `Gender`, `Direction`. `Pricing.ticketPrice(adultPrice, type, childPercent, infantPercent)` là giá một vé một chặng (BR-21; Plan 11 ghi `tickets.fare_amount`); `Pricing.allocate(discount, subtotals)` cho BR-33. BR-10, BR-11, BR-13 trả 400 `PASSENGER_RULES_VIOLATED` kèm `errors[{field, message}]`; hành lý sai trả 400 `VALIDATION_FAILED` kèm `errors`. BR-30 điều kiện 5 kiểm tra bằng `SELECT EXISTS` trên `bookings`; Plan 11 vẫn phải bắt vi phạm `uq_bookings_voucher_user` khi ghi. API: `POST /api/bookings/quote` (Customer, 200) trả `{journeys[{direction, fareTotal, baggageTotal, discount, passengerFares[{type, count, unitPrice, total}]}], fareTotal, baggageTotal, discountTotal, totalAmount}`. Test: `BookingQuoteTest` có dữ liệu theo ví dụ TDD §8.8 (sân bay `QHE`, chuyến Q1100, Q1200, Q1300, Q1400). Kiểu frontend: `Schemas["BookingRequest"]`, `["JourneyRequest"]`, `["PassengerRequest"]`, `["BaggageRequest"]`, `["ContactRequest"]`, `["QuoteResponse"]`, `["JourneyTotals"]`, `["PassengerFare"]` |
```

4. Mục §7: sau mục 10 (Plan 08), thêm:

```
11. **Plan 10 có 4 điểm cố ý khác TDD** (chi tiết ở [Plan 10](2026-10-13-10-booking-quote.md#điểm-cố-ý-khác-tdd)): phản hồi báo giá có thêm `passengerFares` theo loại khách (FR-20); `PASSENGER_RULES_VIOLATED` kèm `errors` theo trường để C-07 đánh dấu từng trường; BR-11 kiểm tra trước hành trình vì số ghế cần kiểm tra phụ thuộc số khách; `FlightApi` mới có hàm kiểm tra và định giá, chưa có hàm đọc chuyến.
```

Trong `CLAUDE.md`, mục Status: đổi ` and \`promotion\` (Plan 09: voucher admin API, \`VoucherApi\`, seed \`V104\`) are done.`thành`, \`promotion\` (Plan 09: voucher admin API, \`VoucherApi\`, seed \`V104\`) and \`booking\` (Plan 10: \`POST /api/bookings/quote\`, with \`FlightApi\` checking and pricing the chosen itinerary) are done.`

- [ ] **Step 3: Kiểm tra toàn bộ**

```bash
cd backend && ./mvnw -B verify
cd ../frontend && npm run lint && npm run build
```

Expected: `BUILD SUCCESS` với 0 failures (thêm 6 + 4 + 2 + 11 = 23 test mới của Plan 10 vào bộ test cũ); `npm run lint` không lỗi; `npm run build` thành công (build kiểm tra kiểu, xác nhận `api-types.ts` mới biên dịch được). E2E không đổi vì plan này không thêm route frontend.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/api-types.ts docs/superpowers/plans/2026-10-09-00-roadmap.md docs/superpowers/plans/2026-10-13-10-booking-quote.md CLAUDE.md
git commit -m "docs: hoàn tất Plan 10, cập nhật hợp đồng FlightApi, QuoteService và kiểu API frontend"
```

---

## Tự kiểm tra plan

- **Độ phủ spec:**
  - FR-20 (bảng giá chi tiết, không giữ ghế, kiểm tra như giữ chỗ) → Task 3, 4.
  - FR-21 (hành khách, liên hệ, BR-10–14) → Task 2 (BR-10, 11, 13), Task 3 (BR-12, BR-14 bằng Bean Validation, ngày bay theo giờ địa phương).
  - FR-22, BR-22 → Task 4. FR-23, BR-30 điều kiện 1–5, BR-31 → Task 4 (điều kiện 1–4 và BR-31 qua `VoucherApi` của Plan 09). BR-33 → Task 2, 4.
  - BR-01–06, BR-04 → Task 1. BR-20, BR-21, BR-23 → Task 2, 3, 4.
  - BR-24 (chốt giá): plan này gom đủ dữ liệu cần chốt (`FareSnapshot`, `adultPrice` từng chặng, `Pricing.ticketPrice`, `BaggageLine`). Việc ghi xuống DB thuộc Plan 11.
  - Kết quả kiểm chứng của lộ trình ("`POST /bookings/quote` cho đúng các con số của ví dụ TDD §8.8") → Task 4 `matchesTddBookingExample`.
- **Nhất quán tên:** `FlightApi.price / JourneyChoice / PricedJourney / PricedSegment / FareSnapshot`, `CatalogApi.baggageOptions / BaggageRef`, `BookingRequest.JourneyRequest / PassengerRequest / BaggageRequest / ContactRequest`, `PassengerRules.checkCounts / checkDetails / typeAt`, `Pricing.ticketPrice / allocate`, `FieldErrors.add / throwIfAny`, `Quote.JourneyQuote / PassengerFare / BaggageLine`, `QuoteService.quote`, `QuoteResponse.JourneyTotals / of`. Mỗi tên được dùng giống nhau ở mọi task và ở dòng §6 của lộ trình.
- **Review Focus:** mỗi dòng trong mục Review Focus có test nêu tên ở cuối dòng.
