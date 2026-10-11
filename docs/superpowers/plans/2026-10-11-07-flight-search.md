# Plan 07 — Tìm chuyến: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khách (không cần đăng nhập) tìm hành trình một chiều từ A đến B vào một ngày: nhận cả chuyến bay thẳng lẫn nối chuyến cùng hãng, mỗi hành trình kèm các gói giá đang bán trên mọi chặng, giá người lớn, tổng giá cho đúng số khách và số ghế còn. Khứ hồi (FR-12) gọi API hai lần. Trên seed `V106`, HAN→PQC trả VN1233 và VN213 + VN1825 với p95 dưới 1 giây (NFR-01).

**Architecture:** Thêm `flight.FlightSearch` ở gói gốc của module `flight` (API công khai theo TDD §3.2; Plan 15 đổi chuyến gọi lại). Lớp này chạy Q-04, Q-05 (chỉ lấy id), một truy vấn đọc chi tiết các chặng, rồi Q-06, sau đó ghép hành trình và tính giá trong Java theo TDD §6.1. Endpoint `GET /api/flights/search` thêm vào `FlightController` sẵn có; quyền công khai đã có trong `SecurityConfig` nên không phải sửa. Không có migration mới.

**Tech Stack:** Java 21, Spring Boot 4.1.1 (`JdbcClient`, Bean Validation), springdoc 3.1.1 (`@ParameterObject`), PostgreSQL 18, Testcontainers, MockMvc, JUnit 5 + AssertJ. Frontend chỉ chạy lại `npm run gen:api`.

**Spec:** [PRD](../../PRD.md) FR-11, FR-12, BR-01–06, BR-11 (kiểm tra số khách), BR-20, BR-21, BR-25, NFR-01, PRD §7 (tham số) · [TDD](../../TDD.md) §3.2, §5.1, §5.2, §6.1, §8.4 (`/flights/search`), §8.8 (ví dụ tìm kiếm), §11.2 T13 · [App Flow](../../APP_FLOW.md) C-02 · [Backend Schema](../../BACKEND_SCHEMA.md) Q-04, Q-05, Q-06, §6.2 · [Lộ trình](2026-10-09-00-roadmap.md) §2, §5, §6 · [Plan 06](2026-10-11-06-flights.md) (module `flight`, `TestFlights`, seed `V106`)

## Global Constraints

- Mọi ràng buộc ở Global Constraints của [Plan 01](2026-10-09-01-backend-foundation.md), [Plan 02](2026-10-09-02-accounts-settings-email.md), [Plan 05](2026-10-10-05-catalog.md) và [Plan 06](2026-10-11-06-flights.md) vẫn giữ nguyên: phiên bản thư viện, ProblemDetail có `code`, câu lỗi tiếng Việt, DTO là `record`, dữ liệu sai theo trường trả 400 `VALIDATION_FAILED` kèm `errors[{field, message}]`, module `flight` không có entity JPA.
- Phụ thuộc giữa module không đổi: `flight → common, catalog`. `flight` chỉ dùng lớp ở gói gốc của `catalog` (`CatalogApi`, `CabinClass`); đọc bảng `airports`, `airlines`, `fare_families` bằng SQL chỉ đọc, như `FlightQueries` đã làm. `ModularityTest` kiểm tra.
- Không có migration schema hay seed mới. Index `ix_flights_route_time`, `ix_flights_origin_time` đã có trong `V1__init.sql`.
- **Ngày tìm kiếm (TDD §5.1):** ngày `D` đổi thành `[D 00:00, D+1 00:00)` theo múi giờ của sân bay đi. Giờ trả về theo múi giờ sân bay tương ứng kèm offset và tên múi giờ IANA, VD `2026-12-01T08:10:00+07:00` và `Asia/Ho_Chi_Minh`.
- **BR-01:** chặng đầu của mỗi hành trình phải ở trạng thái `SCHEDULED` và cất cánh sau `now + booking.min_hours_before_departure` giờ.
- **BR-03:** nối chuyến cùng hãng, sân bay đến của chặng 1 là sân bay đi của chặng 2, thời gian nối nằm trong `[search.min_connection_minutes, search.max_connection_minutes]`, tính cả hai biên (Q-05 dùng `BETWEEN`).
- **BR-05:** chỉ giữ gói giá đang kích hoạt, đúng hạng ghế được tìm, có trên **mọi** chặng. Hành trình không còn gói nào thì bỏ.
- **BR-06:** mỗi chặng còn ít nhất `adults + children` ghế trong hạng được tìm. Em bé không chiếm ghế.
- **BR-21, TDD §5.2:** giá vé = giá người lớn của chặng × tỉ lệ, làm tròn half-up bằng `(amount * percent + 50) / 100`, tính **trên từng chặng** rồi mới cộng. Tỉ lệ đọc từ `pricing.child_percent`, `pricing.infant_percent`.
- **BR-25:** mọi tham số đọc bằng `SettingsApi.getInt` ở mỗi lần tìm, không cache.
- Tiền là `long` VND.
- Test tự tạo dữ liệu (hãng `Q1`, `Q2`; sân bay `QHA`, `QHB`, `QHC` qua `TestFlights.catalog(jdbc)`), không dựa vào seed. Riêng `SearchSeedTest` nạp seed `V100`–`V102`, `V106` bằng `@Sql` trong transaction rồi rollback, giống `SeedFlightsTest`.

### Điểm cố ý khác TDD và lộ trình

1. **Chưa tạo `FlightApi`** (lộ trình §2 và §6 giao cho Plan 07 "`FlightApi` kiểm tra và định giá hành trình"). Nơi dùng duy nhất là báo giá và giữ chỗ (Plan 10, 11), nên Plan 10 tạo `FlightApi` cùng lúc với nơi dùng, giống cách Plan 05 và Plan 06 hoãn `CatalogApi` và `FlightApi`. Phần định giá dùng chung đã có ở plan này: `FlightSearch.totalPrice(...)` (BR-21) là hàm `public static`, Plan 10 gọi lại.
2. **BR-04 khi tìm chiều về: API không thêm tham số.** API giữ đúng tham số của TDD §8.4. Màn hình C-02 (Plan 08) tìm chiều về bằng tuyến ngược lại vào ngày về, rồi chỉ cho chọn hành trình có chặng đầu cất cánh sau giờ hạ cánh của chặng cuối chiều đi đã chọn (so `segments[0].departure.time` với `segments[last].arrival.time`, đều là thời điểm có offset nên so trực tiếp được). Báo giá (Plan 10) kiểm tra lại BR-04 ở server.

Những điểm TDD không nói, plan tự chọn:

- **Tham số bind vào record `FlightSearch.SearchQuery`** bằng `@Valid @ParameterObject`. Thiếu hoặc sai kiểu trả 400 `VALIDATION_FAILED` theo trường, như mọi form khác. `@ParameterObject` giúp OpenAPI liệt kê từng tham số query, nên kiểu TypeScript sinh ra có đủ `from`, `to`, `date`, `adults`, `children?`, `infants?`, `cabin`.
- **Kiểm tra số khách ngay khi tìm (BR-11)**, cũng trả `VALIDATION_FAILED` theo trường để form tìm kiếm hiện lỗi đúng ô: `adults ≥ 1`; `infants ≤ adults` (trường `infants`); `adults + children ≤ booking.max_seated_passengers` (trường `adults`); `from = to` (trường `to`). Không dùng `PASSENGER_RULES_VIOLATED`: mã đó dành cho khai báo hành khách khi đặt vé (BR-10–13).
- **`children`, `infants` mặc định 0** khi không gửi. `adults`, `from`, `to`, `date`, `cabin` bắt buộc.
- **Không có kết quả thì trả 200 `{ "itineraries": [] }`** (FR-11), gồm cả khi sân bay đi không tồn tại hoặc ngày đã qua.
- **Không lọc sân bay hay hãng đã ngừng hoạt động.** BR-91 chỉ cấm chọn chúng cho chuyến mới; chuyến đã có vẫn bán.
- **Thứ tự:** hành trình xếp theo `totalPrice` của gói rẻ nhất tăng dần (TDD §6.1), bằng giá thì theo giờ hạ cánh của chặng cuối. Gói giá trong một hành trình xếp theo `totalPrice` tăng dần.
- **Q-04, Q-05 chỉ lấy id chuyến.** Chi tiết chặng (tên hãng, thành phố, múi giờ, số ghế còn) đọc bằng một truy vấn `WHERE f.id IN (:ids)`, nên mỗi lần tìm chạy đúng 4 câu SQL dù có bao nhiêu hành trình. `seatsLeft` tính trong Java từ số ghế còn của từng chặng.
- **Cùng hãng được bảo đảm ở hai lớp:** điều kiện `f2.airline_code = f1.airline_code` của Q-05, và phép giao gói giá (gói giá thuộc đúng một hãng, Plan 06 chỉ cho gắn gói của hãng khai thác). Test T13 có chuyến Q2100 của hãng Q2 để ghim hành vi.
- **`layoverMinutesAfter`** là thời gian nối tới chặng sau, `null` ở chặng cuối. Theo quy ước của `gen:api` (cờ `--properties-required-by-default`), kiểu TypeScript sinh ra là `number`; Plan 08 phải tự coi chặng cuối là `null`.
- **Tên endpoint trong controller là `searchFlights`**, không phải `search`: `AdminUserController` đã có method `search`, springdoc sẽ đặt operationId `search_1` (không ổn định). Tên khác nhau cho operationId `searchFlights`.
- **NFR-01 đo bằng integration test** trên seed `V106` (`SearchSeedTest`): 100 lần tìm trên 5 tuyến, qua MockMvc (gồm cả chuyển JSON, không gồm mạng). Thực đo trên máy dev với profile `dev`: khoảng 20 ms mỗi lần tìm HAN→PQC sau khi đã chạy nóng.

## Review Focus

Những tình huống tài liệu không nói rõ nhưng dễ gây lỗi thật, xếp theo khả năng gặp. Mỗi dòng đã có test ghim lại.

1. **Biên của thời gian nối.** Nối 59 và 721 phút bị loại, 60 và 720 phút được giữ. Sửa tham số `search.max_connection_minutes` thì kết quả đổi ngay ở lần tìm sau (Task 2 `connectsSameAirlineWithinLayoverWindowOnly`, `connectionWindowFollowsSettings`).
2. **Múi giờ.** Chuyến QHA (+07:00) → QHC (Singapore, +08:00) phải hiện giờ hạ cánh theo giờ Singapore; ngày tìm kiếm tính theo giờ sân bay đi (Task 2 `showsLocalTimesActiveFaresAndPricesForRequestedPassengers`).
3. **Ghế còn đúng bằng số khách chiếm ghế.** Còn 2 ghế, tìm 2 người lớn + 2 em bé thì vẫn thấy chuyến; 2 người lớn + 1 trẻ em thì không (Task 2 `needsSeatsForAdultsAndChildrenOnly`).
4. **BR-01 áp cho chặng đầu của cả chuyến thẳng lẫn chuyến nối**, và giờ tối thiểu đọc lại từ tham số ở mỗi lần tìm (Task 2 `hidesItinerariesDepartingWithinMinimumHours`).
5. **Gói giá không được hiện:** gói đã ngừng bán (kể cả rẻ hơn), gói của hạng khác, gói chỉ có trên một chặng của hành trình nối (Task 2 `showsLocalTimesActiveFaresAndPricesForRequestedPassengers`, `businessSearchUsesBusinessFaresOnly`, `connectsSameAirlineWithinLayoverWindowOnly`).

---

## Trước khi bắt đầu

- **Cần có:** Docker Desktop đang chạy, Git Bash, JDK 21 trở lên, Node 24. Plan 01–06 đã xong.
- **Chạy lệnh ở đâu:** mọi lệnh dùng Git Bash và chạy từ thư mục gốc repo, trừ khi bước đó ghi `cd backend` hoặc `cd frontend`.
- **Nhánh:** tạo nhánh mới từ `develop`, VD `feat/07-flight-search`.
- **Đã kiểm chứng:** toàn bộ code trong plan đã chạy thử ngày 2026-10-11 trên Windows 11 với JDK 25 và Docker 29. Cuối plan có 127 test xanh (`./mvnw -B verify`). Đã thử cố ý phá từng điều kiện lọc của Q-04, Q-05 (ghế chặng 2, biên nối tối thiểu, trạng thái, BR-01 của chặng đầu bay thẳng và nối chuyến, gói ngừng bán): mỗi lần đều có test đỏ. Đã chạy profile `dev`: `GET /api/flights/search?from=HAN&to=PQC&date=<ngày mai>&adults=2&children=1&infants=1&cabin=ECONOMY` trả VN1233, rồi VN213 + VN1825; `npm run gen:api`, `npm run lint`, `npm run build` xanh.
- **Cảnh báo vô hại trong log khi chạy test:** chỉ có các cảnh báo đã có từ Plan 02, 05, 06 (`HHH000247`, `violates foreign key constraint`, `users_email_key`).

### Hai bẫy khi viết test cho plan này

- **Test sửa `system_settings` và giờ chuyến bằng SQL.** Được, vì test gắn `@Transactional` nên mọi thay đổi bị rollback; `SettingsApi` và `FlightSearch` đọc bằng `JdbcClient` trong cùng transaction nên thấy giá trị mới ngay.
- **`now()` của PostgreSQL là thời điểm bắt đầu transaction**, còn `FlightSearch` dùng `Instant.now()` của Java. Test BR-01 dời chuyến về `now() + 2 giờ` và đặt giờ tối thiểu là 3 rồi 1, nên chênh vài giây giữa hai đồng hồ không ảnh hưởng.

## Cấu trúc file sau plan

```
backend/src/
├── main/java/vn/edu/uit/flightbooking/flight/
│   ├── FlightSearch.java                        # Task 1 (giá BR-21), Task 2 (tìm kiếm)
│   └── web/FlightController.java                # Task 2 (thêm GET /flights/search)
└── test/java/vn/edu/uit/flightbooking/flight/
    ├── TicketPriceTest.java                     # Task 1 (unit test, không cần Spring)
    ├── SearchSeedTest.java                      # Task 3 (seed V106: ví dụ TDD §8.8, NFR-01)
    └── web/FlightSearchTest.java                # Task 2 (FR-11, BR-01–06, T13)
frontend/src/lib/api-types.ts                    # Task 3 (sinh lại bằng npm run gen:api)
```

## API sau plan

| Method | Đường dẫn                                                               | Quyền     | Thành công                   | Lỗi                                  |
| ------ | ----------------------------------------------------------------------- | --------- | ---------------------------- | ------------------------------------ |
| GET    | `/api/flights/search?from=&to=&date=&adults=&children=&infants=&cabin=` | Công khai | 200 `{ itineraries: [...] }` | 400 `VALIDATION_FAILED` kèm `errors` |

Dạng JSON giống ví dụ TDD §8.8. `cabin` là `ECONOMY`, `PREMIUM_ECONOMY`, `BUSINESS` hoặc `FIRST`; `date` dạng `yyyy-MM-dd`.

---

### Task 1: Tính giá vé theo BR-21

**Files:**

- Create: `backend/src/main/java/vn/edu/uit/flightbooking/flight/FlightSearch.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/flight/TicketPriceTest.java`

**Interfaces:**

- Consumes: không có.
- Produces (Task 2 giữ nguyên hai hàm này, Plan 10 gọi lại):
  - `public static long FlightSearch.totalPrice(List<Long> adultLegPrices, int adults, int children, int infants, int childPercent, int infantPercent)`: tổng giá vé cho đúng số khách trên một chiều; làm tròn từng khách trên từng chặng rồi mới cộng.
  - `static long FlightSearch.percentOf(long amount, int percent)`: `(amount * percent + 50) / 100`.

- [ ] **Step 1: Viết test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/flight/TicketPriceTest.java`:

```java
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
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
cd backend && ./mvnw test -Dtest=TicketPriceTest
```

Expected: `COMPILATION ERROR`, `cannot find symbol ... FlightSearch`.

- [ ] **Step 3: Viết hàm tính giá**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/flight/FlightSearch.java`. Task 2 thay toàn bộ file này bằng bản có phần tìm kiếm và giữ nguyên hai hàm dưới đây.

```java
package vn.edu.uit.flightbooking.flight;

import java.util.List;

/** Tìm hành trình một chiều (FR-11). Task 2 thêm phần tìm kiếm. */
public class FlightSearch {

	/**
	 * BR-21, TDD §5.2: giá vé của từng khách trên từng chặng làm tròn half-up đến đồng, rồi mới cộng. Báo giá (Plan 10)
	 * dùng lại hàm này.
	 */
	public static long totalPrice(List<Long> adultLegPrices, int adults, int children, int infants, int childPercent,
			int infantPercent) {
		return adultLegPrices.stream()
			.mapToLong(price -> adults * price + children * percentOf(price, childPercent)
					+ infants * percentOf(price, infantPercent))
			.sum();
	}

	/** Làm tròn half-up, đúng với số không âm (TDD §5.2). */
	static long percentOf(long amount, int percent) {
		return (amount * percent + 50) / 100;
	}

}
```

- [ ] **Step 4: Chạy test, xác nhận xanh**

```bash
cd backend && ./mvnw test -Dtest=TicketPriceTest
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/flight/FlightSearch.java backend/src/test/java/vn/edu/uit/flightbooking/flight/TicketPriceTest.java
git commit -m "feat(flight): tính giá vé theo loại khách (BR-21)"
```

---

### Task 2: API tìm chuyến bay thẳng và nối chuyến (FR-11, T13)

**Files:**

- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/flight/FlightSearch.java` (thay toàn bộ)
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/flight/web/FlightController.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/flight/web/FlightSearchTest.java`

**Interfaces:**

- Consumes:
  - `FlightSearch.totalPrice`, `FlightSearch.percentOf` (Task 1).
  - `catalog.CatalogApi.airport(String code) → Optional<AirportRef(ZoneId zone, boolean active)>`, `catalog.CabinClass` (Plan 05, 06).
  - `common.SettingsApi.getInt(SettingKey)` với `BOOKING_MIN_HOURS_BEFORE_DEPARTURE`, `BOOKING_MAX_SEATED_PASSENGERS`, `PRICING_CHILD_PERCENT`, `PRICING_INFANT_PERCENT`, `SEARCH_MIN_CONNECTION_MINUTES`, `SEARCH_MAX_CONNECTION_MINUTES` (Plan 01).
  - `BusinessException.invalidField(field, message)` (Plan 02).
  - Test: `flight.TestFlights` (`DAY`, `catalog(jdbc)` → `Catalog(economyFare, businessFare, otherAirlineFare)`, `newFlight(catalog, changes)`, `createOne(mvc, admin, body)`), `TestUsers.loggedIn` (Plan 02, 06).
- Produces (API công khai của `flight`; Plan 08 dùng qua HTTP, Plan 15 gọi Java):
  - `flight.FlightSearch.search(SearchQuery) → SearchResult`, `@Transactional(readOnly = true)`.
  - `record SearchQuery(String from, String to, LocalDate date, Integer adults, Integer children, Integer infants, CabinClass cabin)`; `children`, `infants` null thì thành 0.
  - `record SearchResult(List<Itinerary> itineraries)`.
  - `record Itinerary(int stops, long totalDurationMinutes, List<Segment> segments, List<FareOption> fareOptions)`.
  - `record Segment(long flightId, String flightNumber, AirlineRef airline, AirportTime departure, AirportTime arrival, long durationMinutes, String aircraftModel, Long layoverMinutesAfter)`.
  - `record AirlineRef(String code, String name)`, `record AirportTime(String airport, String city, OffsetDateTime time, String timezone)`.
  - `record FareOption(long fareFamilyId, String name, CabinClass cabinClass, int carryOnKg, int checkedBaggageKg, boolean refundable, long refundFee, boolean changeable, long changeFee, long adultPrice, long totalPrice, int seatsLeft)`.
  - `GET /api/flights/search` (method `FlightController.searchFlights`, operationId `searchFlights`).

- [ ] **Step 1: Viết test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/flight/web/FlightSearchTest.java`. Dữ liệu: một chuyến thẳng QHA→QHC, một chặng 1 QHA→QHB và nhiều chặng 2 QHB→QHC đặt sát các biên của BR-03, BR-05, BR-06.

```java
package vn.edu.uit.flightbooking.flight.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;
import static vn.edu.uit.flightbooking.flight.TestFlights.createOne;
import static vn.edu.uit.flightbooking.flight.TestFlights.newFlight;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.flight.TestFlights;

/** FR-11, BR-01–06, T13: tìm QHA→QHC ngày DAY, không đăng nhập. */
@IntegrationTest
@Transactional
class FlightSearchTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	MockHttpSession admin;

	TestFlights.Catalog catalog;

	long direct;

	long firstLeg;

	long secondLeg;

	/**
	 * Bay thẳng Q1300 QHA→QHC 07:00 (180 phút; bán Eco, Biz và gói ECONOMY "Cũ" đã ngừng bán) và Q1301 (đã huỷ).
	 * Chặng 1 Q1100 QHA→QHB 06:00–08:10. Chặng 2 QHB→QHC (120 phút) cất cánh lúc: Q1201 09:09 (nối 59 phút), Q1203
	 * 09:10 (60), Q1200 10:00 (110, ECONOMY 40 ghế), Q1204 12:00 (chỉ bán Biz), Q1205 13:00 (chỉ có ECONOMY 2 ghế),
	 * Q1206 20:10 (720), Q1202 20:11 (721), Q2100 11:00 của hãng Q2. Mọi giá Eco 1.000.000, Biz 3.000.000.
	 */
	@BeforeEach
	void setUp() throws Exception {
		admin = TestUsers.loggedIn(mvc, jdbc, Role.ADMIN).session();
		catalog = TestFlights.catalog(jdbc);
		long retired = jdbc.sql("""
				INSERT INTO fare_families (airline_code, cabin_class, name, carry_on_kg, checked_baggage_kg,
				                           refundable, changeable, active)
				VALUES ('Q1', 'ECONOMY', 'Cũ', 7, 0, FALSE, FALSE, FALSE)
				RETURNING id
				""").query(Long.class).single();

		direct = flight(Map.of("flightNumber", "Q1300", "arrivalAirport", "QHC", "departureTime", "07:00",
				"durationMinutes", 180, "fares",
				List.of(eco(), Map.of("fareFamilyId", retired, "price", 500_000), biz())));
		long cancelled = flight(Map.of("flightNumber", "Q1301", "arrivalAirport", "QHC", "departureTime", "08:00"));
		jdbc.sql("UPDATE flights SET status = 'CANCELLED' WHERE id = ?").param(cancelled).update();

		firstLeg = flight(Map.of());
		second("Q1201", "09:09", Map.of());
		secondLeg = second("Q1203", "09:10", Map.of());
		second("Q1200", "10:00", Map.of("cabins", List.of(cabin("ECONOMY", 40), cabin("BUSINESS", 10))));
		second("Q1204", "12:00", Map.of("fares", List.of(biz())));
		second("Q1205", "13:00", Map.of("cabins", List.of(cabin("ECONOMY", 2)), "fares", List.of(eco())));
		second("Q1206", "20:10", Map.of());
		second("Q1202", "20:11", Map.of());
		second("Q2100", "11:00", Map.of("airlineCode", "Q2", "cabins", List.of(cabin("ECONOMY", 50)), "fares",
				List.of(Map.of("fareFamilyId", catalog.otherAirlineFare(), "price", 1_000_000))));
	}

	long flight(Map<String, Object> changes) throws Exception {
		return createOne(mvc, admin, newFlight(catalog, changes));
	}

	long second(String flightNumber, String departureTime, Map<String, Object> changes) throws Exception {
		Map<String, Object> body = new HashMap<>(Map.of("flightNumber", flightNumber, "departureAirport", "QHB",
				"arrivalAirport", "QHC", "departureTime", departureTime, "durationMinutes", 120));
		body.putAll(changes);
		return flight(body);
	}

	Map<String, Object> eco() {
		return Map.of("fareFamilyId", catalog.economyFare(), "price", 1_000_000);
	}

	Map<String, Object> biz() {
		return Map.of("fareFamilyId", catalog.businessFare(), "price", 3_000_000);
	}

	static Map<String, Object> cabin(String cabinClass, int seats) {
		return Map.of("cabinClass", cabinClass, "totalSeats", seats);
	}

	ResultActions search(LocalDate date, String cabin, int adults, int children, int infants) throws Exception {
		return mvc.perform(get("/api/flights/search").param("from", "QHA")
			.param("to", "QHC")
			.param("date", date.toString())
			.param("adults", String.valueOf(adults))
			.param("children", String.valueOf(children))
			.param("infants", String.valueOf(infants))
			.param("cabin", cabin));
	}

	/** T13: cùng hãng, nối 60–720 phút (tính cả hai biên), bỏ chuyến hết ghế, không bán gói giá, đã huỷ. */
	@Test
	void connectsSameAirlineWithinLayoverWindowOnly() throws Exception {
		search(DAY, "ECONOMY", 2, 1, 1).andExpect(status().isOk())
			.andExpect(jsonPath("$.itineraries.length()").value(4))
			.andExpect(jsonPath("$.itineraries[*].stops", contains(0, 1, 1, 1)))
			.andExpect(jsonPath("$.itineraries[0].segments[*].flightNumber", contains("Q1300")))
			.andExpect(jsonPath("$.itineraries[1].segments[*].flightNumber", contains("Q1100", "Q1203")))
			.andExpect(jsonPath("$.itineraries[2].segments[*].flightNumber", contains("Q1100", "Q1200")))
			.andExpect(jsonPath("$.itineraries[3].segments[*].flightNumber", contains("Q1100", "Q1206")))
			.andExpect(jsonPath("$.itineraries[1].segments[0].layoverMinutesAfter").value(60))
			.andExpect(jsonPath("$.itineraries[3].segments[0].layoverMinutesAfter").value(720))
			.andExpect(jsonPath("$.itineraries[3].segments[1].layoverMinutesAfter").value(nullValue()));
	}

	/** Giờ theo múi giờ từng sân bay; chỉ gói đang bán của đúng hạng; giá theo BR-21; ghế còn nhỏ nhất qua các chặng. */
	@Test
	void showsLocalTimesActiveFaresAndPricesForRequestedPassengers() throws Exception {
		search(DAY, "ECONOMY", 2, 1, 1).andExpect(jsonPath("$.itineraries[0].totalDurationMinutes").value(180))
			.andExpect(jsonPath("$.itineraries[0].segments[0].flightId").value(direct))
			.andExpect(jsonPath("$.itineraries[0].segments[0].airline.name").value("Hãng Một"))
			.andExpect(jsonPath("$.itineraries[0].segments[0].aircraftModel").value("Airbus A321"))
			.andExpect(jsonPath("$.itineraries[0].segments[0].durationMinutes").value(180))
			.andExpect(jsonPath("$.itineraries[0].segments[0].departure.time").value(DAY + "T07:00:00+07:00"))
			.andExpect(jsonPath("$.itineraries[0].segments[0].departure.city").value("Hà Nội"))
			.andExpect(jsonPath("$.itineraries[0].segments[0].arrival.time").value(DAY + "T11:00:00+08:00"))
			.andExpect(jsonPath("$.itineraries[0].segments[0].arrival.timezone").value("Asia/Singapore"))
			.andExpect(jsonPath("$.itineraries[0].fareOptions[*].name", contains("Eco")))
			.andExpect(jsonPath("$.itineraries[0].fareOptions[0].cabinClass").value("ECONOMY"))
			.andExpect(jsonPath("$.itineraries[0].fareOptions[0].changeable").value(true))
			.andExpect(jsonPath("$.itineraries[0].fareOptions[0].adultPrice").value(1_000_000))
			.andExpect(jsonPath("$.itineraries[0].fareOptions[0].totalPrice").value(3_000_000))
			.andExpect(jsonPath("$.itineraries[0].fareOptions[0].seatsLeft").value(100))
			.andExpect(jsonPath("$.itineraries[2].totalDurationMinutes").value(360))
			.andExpect(jsonPath("$.itineraries[2].fareOptions[0].adultPrice").value(2_000_000))
			.andExpect(jsonPath("$.itineraries[2].fareOptions[0].totalPrice").value(6_000_000))
			.andExpect(jsonPath("$.itineraries[2].fareOptions[0].seatsLeft").value(40));
	}

	/** BR-05: hạng ghế lấy theo gói giá. Q1204 chỉ bán Biz nên có ở BUSINESS; Q1205 không có hạng BUSINESS. */
	@Test
	void businessSearchUsesBusinessFaresOnly() throws Exception {
		search(DAY, "BUSINESS", 1, 0, 0).andExpect(jsonPath("$.itineraries.length()").value(5))
			.andExpect(jsonPath("$.itineraries[*].segments[1].flightNumber", hasItem("Q1204")))
			.andExpect(jsonPath("$.itineraries[*].segments[1].flightNumber", not(hasItem("Q1205"))))
			.andExpect(jsonPath("$.itineraries[*].fareOptions[*].name", everyItem(is("Biz"))));
	}

	/** BR-06: đủ ghế cho người lớn + trẻ em (em bé không chiếm ghế). Q1205 còn đúng 2 ghế. */
	@Test
	void needsSeatsForAdultsAndChildrenOnly() throws Exception {
		search(DAY, "ECONOMY", 2, 0, 2)
			.andExpect(jsonPath("$.itineraries[*].segments[1].flightNumber", hasItem("Q1205")));
		search(DAY, "ECONOMY", 2, 1, 0)
			.andExpect(jsonPath("$.itineraries[*].segments[1].flightNumber", not(hasItem("Q1205"))));
	}

	/**
	 * BR-01, BR-25: không bán hành trình có chặng đầu cất cánh trong vòng N giờ tới; N đọc từ tham số ở mỗi lần tìm.
	 * Dời Q1300 và cặp Q1100 + Q1203 (nối 60 phút) về 2 giờ nữa.
	 */
	@Test
	void hidesItinerariesDepartingWithinMinimumHours() throws Exception {
		move(direct, 120, 300);
		move(firstLeg, 120, 250);
		move(secondLeg, 310, 430);
		LocalDate date = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusHours(2).toLocalDate();

		search(date, "ECONOMY", 1, 0, 0).andExpect(jsonPath("$.itineraries").isEmpty());
		jdbc.sql("UPDATE system_settings SET value = '1' WHERE key = 'booking.min_hours_before_departure'").update();
		search(date, "ECONOMY", 1, 0, 0)
			.andExpect(jsonPath("$.itineraries[*].segments[0].flightNumber", contains("Q1300", "Q1100")));
	}

	/** Dời giờ cất cánh và hạ cánh về {@code now() + n phút}. */
	void move(long flightId, int departureMinutes, int arrivalMinutes) {
		jdbc.sql("""
				UPDATE flights SET departure_time = now() + :dep * INTERVAL '1 minute',
				                   arrival_time = now() + :arr * INTERVAL '1 minute'
				WHERE id = :id
				""").param("dep", departureMinutes).param("arr", arrivalMinutes).param("id", flightId).update();
	}

	/** BR-03, BR-25: khoảng thời gian nối đọc từ tham số. */
	@Test
	void connectionWindowFollowsSettings() throws Exception {
		jdbc.sql("UPDATE system_settings SET value = '120' WHERE key = 'search.max_connection_minutes'").update();

		search(DAY, "ECONOMY", 1, 0, 0)
			.andExpect(jsonPath("$.itineraries[*].segments[1].flightNumber", contains("Q1203", "Q1200")));
	}

	/** FR-11: không có kết quả thì trả danh sách rỗng, không phải lỗi. */
	@Test
	void noResultIsEmptyList() throws Exception {
		search(DAY.minusDays(60), "ECONOMY", 1, 0, 0).andExpect(status().isOk())
			.andExpect(jsonPath("$.itineraries").isEmpty());
		mvc.perform(get("/api/flights/search").param("from", "ZZZ")
			.param("to", "QHC")
			.param("date", DAY.toString())
			.param("adults", "1")
			.param("cabin", "ECONOMY"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itineraries").isEmpty());
	}

	/** BR-11 và dữ liệu thiếu: lỗi theo trường để form hiện đúng chỗ. */
	@Test
	void rejectsInvalidQueryPerField() throws Exception {
		mvc.perform(get("/api/flights/search").param("from", "QHA")
			.param("to", "QHA")
			.param("date", DAY.toString())
			.param("adults", "1")
			.param("cabin", "ECONOMY"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("to"));
		search(DAY, "ECONOMY", 1, 0, 2).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("infants"));
		search(DAY, "ECONOMY", 5, 5, 0).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("adults"));
		search(DAY, "ECONOMY", 0, 1, 0).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("adults"));
		mvc.perform(get("/api/flights/search").param("from", "QHA")
			.param("to", "QHC")
			.param("date", DAY.toString())
			.param("adults", "1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("cabin"));
	}

}
```

Ghi chú về số liệu:

- Kết quả ECONOMY, 2 người lớn + 1 trẻ em + 1 em bé: Q1300 (bay thẳng, 3.000.000 = 2 × 1.000.000 + 900.000 + 100.000), rồi ba hành trình nối cùng giá 6.000.000 xếp theo giờ hạ cánh: Q1203 (11:10), Q1200 (12:00), Q1206 (22:10). Bị loại: Q1201 (nối 59 phút), Q1202 (721 phút), Q1204 (không bán gói ECONOMY nào), Q1205 (2 ghế, cần 3), Q2100 (khác hãng), Q1301 (đã huỷ).
- 07:00 ở QHA (+07:00) cộng 180 phút là 10:00 giờ Việt Nam, tức 11:00 ở QHC (+08:00).
- Gói "Cũ" rẻ hơn (500.000) nhưng đã ngừng bán nên không hiện.

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
cd backend && ./mvnw test -Dtest=FlightSearchTest
```

Expected: `Tests run: 8, Failures: 8`. Chưa có endpoint nên mọi request trả 404, VD `Status expected:<200> but was:<404>`.

- [ ] **Step 3: Viết `FlightSearch`**

Thay toàn bộ `backend/src/main/java/vn/edu/uit/flightbooking/flight/FlightSearch.java` bằng:

```java
package vn.edu.uit.flightbooking.flight;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.catalog.CatalogApi;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;

/**
 * Tìm hành trình một chiều (FR-11, TDD §6.1): bay thẳng (Q-04), nối chuyến cùng hãng (Q-05), gói giá đang bán trên
 * mọi chặng (Q-06), giá theo BR-21. Khứ hồi (FR-12) gọi hai lần, lần sau với tuyến ngược lại; client lọc chiều về
 * theo BR-04, báo giá (Plan 10) kiểm tra lại.
 */
@Service
public class FlightSearch {

	/**
	 * Tham số của GET /flights/search. {@code date}: ngày cất cánh theo giờ sân bay đi. Thiếu {@code children},
	 * {@code infants} thì coi là 0.
	 */
	public record SearchQuery(@NotBlank String from, @NotBlank String to, @NotNull LocalDate date,
			@NotNull @Min(1) Integer adults, @Min(0) Integer children, @Min(0) Integer infants,
			@NotNull CabinClass cabin) {

		public SearchQuery {
			children = children == null ? 0 : children;
			infants = infants == null ? 0 : infants;
		}

	}

	public record AirlineRef(String code, String name) {
	}

	/** Giờ theo múi giờ của sân bay, kèm tên múi giờ IANA (TDD §5.1). */
	public record AirportTime(String airport, String city, OffsetDateTime time, String timezone) {
	}

	/** {@code layoverMinutesAfter}: thời gian nối tới chặng sau, {@code null} ở chặng cuối. */
	public record Segment(long flightId, String flightNumber, AirlineRef airline, AirportTime departure,
			AirportTime arrival, long durationMinutes, String aircraftModel, Long layoverMinutesAfter) {
	}

	/**
	 * {@code adultPrice}: giá người lớn của chiều (BR-20). {@code totalPrice}: cho đúng số khách đã nhập (BR-21).
	 * {@code seatsLeft}: số ghế còn nhỏ nhất qua các chặng.
	 */
	public record FareOption(long fareFamilyId, String name, CabinClass cabinClass, int carryOnKg,
			int checkedBaggageKg, boolean refundable, long refundFee, boolean changeable, long changeFee,
			long adultPrice, long totalPrice, int seatsLeft) {
	}

	/** Gói giá xếp theo {@code totalPrice} tăng dần. */
	public record Itinerary(int stops, long totalDurationMinutes, List<Segment> segments,
			List<FareOption> fareOptions) {
	}

	public record SearchResult(List<Itinerary> itineraries) {
	}

	private record Leg(Segment segment, Instant departure, Instant arrival, int seats) {
	}

	private record Family(long id, String name, int carryOnKg, int checkedBaggageKg, boolean refundable,
			long refundFee, boolean changeable, long changeFee) {
	}

	private record FareRow(long flightId, long price, Family family) {
	}

	/** Q-04, chỉ lấy id: chi tiết chặng đọc chung một lần ở {@link #LEGS}. */
	private static final String DIRECT = """
			SELECT f.id
			FROM flights f
			JOIN flight_cabins c ON c.flight_id = f.id AND c.cabin_class = :cabin
			WHERE f.departure_airport = :origin
			  AND f.arrival_airport = :destination
			  AND f.departure_time >= :dayStart AND f.departure_time < :dayEnd
			  AND f.departure_time >= :minDeparture
			  AND f.status = 'SCHEDULED'
			  AND c.available_seats >= :seats
			""";

	/** Q-05. */
	private static final String CONNECTING = """
			SELECT f1.id AS first_flight_id, f2.id AS second_flight_id
			FROM flights f1
			JOIN flight_cabins c1 ON c1.flight_id = f1.id AND c1.cabin_class = :cabin
			JOIN flights f2       ON f2.departure_airport = f1.arrival_airport
			                     AND f2.airline_code = f1.airline_code
			                     AND f2.arrival_airport = :destination
			                     AND f2.status = 'SCHEDULED'
			                     AND f2.departure_time BETWEEN f1.arrival_time + :minConnection * INTERVAL '1 minute'
			                                               AND f1.arrival_time + :maxConnection * INTERVAL '1 minute'
			JOIN flight_cabins c2 ON c2.flight_id = f2.id AND c2.cabin_class = :cabin
			WHERE f1.departure_airport = :origin
			  AND f1.arrival_airport <> :destination
			  AND f1.departure_time >= :dayStart AND f1.departure_time < :dayEnd
			  AND f1.departure_time >= :minDeparture
			  AND f1.status = 'SCHEDULED'
			  AND c1.available_seats >= :seats
			  AND c2.available_seats >= :seats
			""";

	/** Đọc thẳng bảng airlines, airports của catalog để lấy tên hãng, thành phố, múi giờ (giống FlightQueries). */
	private static final String LEGS = """
			SELECT f.id, f.flight_number, f.airline_code, al.name AS airline_name,
			       f.departure_airport, dep.city AS departure_city, dep.timezone AS departure_timezone, f.departure_time,
			       f.arrival_airport, arr.city AS arrival_city, arr.timezone AS arrival_timezone, f.arrival_time,
			       f.aircraft_model, c.available_seats
			FROM flights f
			JOIN airlines al ON al.code = f.airline_code
			JOIN airports dep ON dep.code = f.departure_airport
			JOIN airports arr ON arr.code = f.arrival_airport
			JOIN flight_cabins c ON c.flight_id = f.id AND c.cabin_class = :cabin
			WHERE f.id IN (:ids)
			""";

	/** Q-06. */
	private static final String FARES = """
			SELECT ff.flight_id, ff.price,
			       fam.id AS fare_family_id, fam.name, fam.carry_on_kg, fam.checked_baggage_kg,
			       fam.refundable, fam.refund_fee, fam.changeable, fam.change_fee
			FROM flight_fares ff
			JOIN fare_families fam ON fam.id = ff.fare_family_id
			WHERE ff.flight_id IN (:ids)
			  AND fam.cabin_class = :cabin
			  AND fam.active
			""";

	private final JdbcClient jdbc;

	private final CatalogApi catalog;

	private final SettingsApi settings;

	FlightSearch(JdbcClient jdbc, CatalogApi catalog, SettingsApi settings) {
		this.jdbc = jdbc;
		this.catalog = catalog;
		this.settings = settings;
	}

	/**
	 * Hành trình thoả BR-01–BR-06, xếp theo giá rẻ nhất rồi giờ hạ cánh. Sân bay đi không tồn tại hoặc không có
	 * chuyến thì trả danh sách rỗng.
	 */
	@Transactional(readOnly = true)
	public SearchResult search(SearchQuery q) {
		int seated = q.adults() + q.children();
		if (q.from().equals(q.to())) {
			throw BusinessException.invalidField("to", "Sân bay đến phải khác sân bay đi");
		}
		if (q.infants() > q.adults()) {
			throw BusinessException.invalidField("infants", "Số em bé không được nhiều hơn số người lớn");
		}
		int maxSeated = settings.getInt(SettingKey.BOOKING_MAX_SEATED_PASSENGERS);
		if (seated > maxSeated) {
			throw BusinessException.invalidField("adults",
					"Tối đa %d khách chiếm ghế (người lớn và trẻ em)".formatted(maxSeated));
		}
		Optional<ZoneId> zone = catalog.airport(q.from()).map(CatalogApi.AirportRef::zone);
		if (zone.isEmpty()) {
			return new SearchResult(List.of());
		}
		Instant minDeparture = Instant.now()
			.plus(settings.getInt(SettingKey.BOOKING_MIN_HOURS_BEFORE_DEPARTURE), ChronoUnit.HOURS);
		Map<String, Object> params = Map.of("origin", q.from(), "destination", q.to(), "cabin", q.cabin().name(),
				"seats", seated, "dayStart", utc(q.date().atStartOfDay(zone.get()).toInstant()), "dayEnd",
				utc(q.date().plusDays(1).atStartOfDay(zone.get()).toInstant()), "minDeparture", utc(minDeparture),
				"minConnection", settings.getInt(SettingKey.SEARCH_MIN_CONNECTION_MINUTES), "maxConnection",
				settings.getInt(SettingKey.SEARCH_MAX_CONNECTION_MINUTES));

		List<List<Long>> routes = new ArrayList<>();
		jdbc.sql(DIRECT).params(params).query(Long.class).list().forEach(id -> routes.add(List.of(id)));
		routes.addAll(jdbc.sql(CONNECTING)
			.params(params)
			.query((rs, n) -> List.of(rs.getLong("first_flight_id"), rs.getLong("second_flight_id")))
			.list());
		if (routes.isEmpty()) {
			return new SearchResult(List.of());
		}

		Set<Long> ids = routes.stream().flatMap(List::stream).collect(Collectors.toSet());
		Map<Long, Leg> legs = jdbc.sql(LEGS)
			.param("ids", ids)
			.param("cabin", q.cabin().name())
			.query(FlightSearch::leg)
			.list()
			.stream()
			.collect(Collectors.toMap(l -> l.segment().flightId(), l -> l));
		Map<Long, Map<Long, Long>> prices = new HashMap<>(); // flightId → fareFamilyId → giá người lớn của chặng
		Map<Long, Family> families = new HashMap<>();
		jdbc.sql(FARES).param("ids", ids).param("cabin", q.cabin().name()).query(FlightSearch::fare).list()
			.forEach(fare -> {
				prices.computeIfAbsent(fare.flightId(), k -> new HashMap<>()).put(fare.family().id(), fare.price());
				families.put(fare.family().id(), fare.family());
			});

		int childPercent = settings.getInt(SettingKey.PRICING_CHILD_PERCENT);
		int infantPercent = settings.getInt(SettingKey.PRICING_INFANT_PERCENT);
		List<Itinerary> itineraries = routes.stream()
			.map(route -> itinerary(route.stream().map(legs::get).toList(), prices, families, q, childPercent,
					infantPercent))
			.flatMap(Optional::stream)
			.sorted(Comparator.comparingLong((Itinerary i) -> i.fareOptions().getFirst().totalPrice())
				.thenComparing(i -> i.segments().getLast().arrival().time().toInstant()))
			.toList();
		return new SearchResult(itineraries);
	}

	/**
	 * BR-21, TDD §5.2: giá vé của từng khách trên từng chặng làm tròn half-up đến đồng, rồi mới cộng. Báo giá (Plan 10)
	 * dùng lại hàm này.
	 */
	public static long totalPrice(List<Long> adultLegPrices, int adults, int children, int infants, int childPercent,
			int infantPercent) {
		return adultLegPrices.stream()
			.mapToLong(price -> adults * price + children * percentOf(price, childPercent)
					+ infants * percentOf(price, infantPercent))
			.sum();
	}

	/** Làm tròn half-up, đúng với số không âm (TDD §5.2). */
	static long percentOf(long amount, int percent) {
		return (amount * percent + 50) / 100;
	}

	/** Chỉ giữ gói giá có trên mọi chặng (BR-05); không còn gói nào thì bỏ hành trình. */
	private static Optional<Itinerary> itinerary(List<Leg> route, Map<Long, Map<Long, Long>> prices,
			Map<Long, Family> families, SearchQuery q, int childPercent, int infantPercent) {
		Set<Long> common = new LinkedHashSet<>(prices.getOrDefault(route.getFirst().segment().flightId(), Map.of())
			.keySet());
		route.forEach(leg -> common.retainAll(prices.getOrDefault(leg.segment().flightId(), Map.of()).keySet()));
		if (common.isEmpty()) {
			return Optional.empty();
		}
		int seatsLeft = route.stream().mapToInt(Leg::seats).min().orElseThrow();
		List<FareOption> options = common.stream().map(id -> {
			Family f = families.get(id);
			List<Long> legPrices = route.stream().map(leg -> prices.get(leg.segment().flightId()).get(id)).toList();
			return new FareOption(id, f.name(), q.cabin(), f.carryOnKg(), f.checkedBaggageKg(), f.refundable(),
					f.refundFee(), f.changeable(), f.changeFee(), legPrices.stream().mapToLong(Long::longValue).sum(),
					totalPrice(legPrices, q.adults(), q.children(), q.infants(), childPercent, infantPercent),
					seatsLeft);
		}).sorted(Comparator.comparingLong(FareOption::totalPrice)).toList();

		List<Segment> segments = new ArrayList<>();
		for (int i = 0; i < route.size(); i++) {
			Segment s = route.get(i).segment();
			Long layover = i + 1 < route.size() ? minutes(route.get(i).arrival(), route.get(i + 1).departure()) : null;
			segments.add(new Segment(s.flightId(), s.flightNumber(), s.airline(), s.departure(), s.arrival(),
					s.durationMinutes(), s.aircraftModel(), layover));
		}
		return Optional.of(new Itinerary(route.size() - 1,
				minutes(route.getFirst().departure(), route.getLast().arrival()), segments, options));
	}

	private static Leg leg(ResultSet rs, int rowNum) throws SQLException {
		Instant departure = rs.getObject("departure_time", OffsetDateTime.class).toInstant();
		Instant arrival = rs.getObject("arrival_time", OffsetDateTime.class).toInstant();
		Segment segment = new Segment(rs.getLong("id"), rs.getString("flight_number"),
				new AirlineRef(rs.getString("airline_code"), rs.getString("airline_name")),
				airportTime(rs.getString("departure_airport"), rs.getString("departure_city"), departure,
						rs.getString("departure_timezone")),
				airportTime(rs.getString("arrival_airport"), rs.getString("arrival_city"), arrival,
						rs.getString("arrival_timezone")),
				minutes(departure, arrival), rs.getString("aircraft_model"), null);
		return new Leg(segment, departure, arrival, rs.getInt("available_seats"));
	}

	private static AirportTime airportTime(String airport, String city, Instant time, String zone) {
		return new AirportTime(airport, city, time.atZone(ZoneId.of(zone)).toOffsetDateTime(), zone);
	}

	private static long minutes(Instant from, Instant to) {
		return Duration.between(from, to).toMinutes();
	}

	private static FareRow fare(ResultSet rs, int rowNum) throws SQLException {
		return new FareRow(rs.getLong("flight_id"), rs.getLong("price"),
				new Family(rs.getLong("fare_family_id"), rs.getString("name"), rs.getInt("carry_on_kg"),
						rs.getInt("checked_baggage_kg"), rs.getBoolean("refundable"), rs.getLong("refund_fee"),
						rs.getBoolean("changeable"), rs.getLong("change_fee")));
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

}
```

Vì sao viết như vậy:

- Tham số thời gian truyền vào SQL là `OffsetDateTime` ở UTC (hàm `utc`), không phải `Instant`: driver PostgreSQL không nhận `Instant`. `FlightService` của Plan 06 cũng làm vậy.
- `JdbcClient.params(Map)` bỏ qua tham số thừa, nên Q-04 và Q-05 dùng chung một `Map`.
- `LinkedHashSet` + `retainAll` giữ các gói có trên mọi chặng. Một chặng không có gói nào của hạng này thì phép giao rỗng và hành trình bị bỏ (BR-05).

- [ ] **Step 4: Thêm endpoint vào controller**

Trong `backend/src/main/java/vn/edu/uit/flightbooking/flight/web/FlightController.java`:

1. Thêm import (giữ thứ tự nhóm import sẵn có):

```java
import org.springdoc.core.annotations.ParameterObject;
```

đặt ngay trước `import org.springframework.data.domain.Pageable;`, và

```java
import vn.edu.uit.flightbooking.flight.FlightSearch;
import vn.edu.uit.flightbooking.flight.FlightSearch.SearchQuery;
import vn.edu.uit.flightbooking.flight.FlightSearch.SearchResult;
```

đặt ngay trước `import vn.edu.uit.flightbooking.flight.domain.FlightQueries;`.

2. Thay Javadoc, trường và constructor:

```java
/** Quản trị chuyến bay (FR-90–92, FR-94): màn hình S-05, A-04–A-06. Quyền theo đường dẫn ở SecurityConfig. */
@RestController
@RequestMapping("/api")
class FlightController {

	private final FlightService flights;

	private final FlightQueries queries;

	FlightController(FlightService flights, FlightQueries queries) {
		this.flights = flights;
		this.queries = queries;
	}
```

bằng:

```java
/**
 * Tìm chuyến (FR-11, FR-12: màn hình C-02) và quản trị chuyến bay (FR-90–92, FR-94: S-05, A-04–A-06). Quyền theo đường
 * dẫn ở SecurityConfig.
 */
@RestController
@RequestMapping("/api")
class FlightController {

	private final FlightService flights;

	private final FlightQueries queries;

	private final FlightSearch search;

	FlightController(FlightService flights, FlightQueries queries, FlightSearch search) {
		this.flights = flights;
		this.queries = queries;
		this.search = search;
	}

	/** Công khai, không phân trang (TDD §6.1). */
	@GetMapping("/flights/search")
	SearchResult searchFlights(@Valid @ParameterObject SearchQuery query) {
		return search.search(query);
	}
```

`SecurityConfig` đã có `GET /api/flights/search` trong danh sách `permitAll` (Plan 01), không phải sửa.

- [ ] **Step 5: Chạy test, xác nhận xanh**

```bash
cd backend && ./mvnw test -Dtest='FlightSearchTest,TicketPriceTest,ModularityTest'
```

Expected: `Tests run: 12, Failures: 0, Errors: 0` (8 + 3 + 1).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/flight backend/src/test/java/vn/edu/uit/flightbooking/flight/web/FlightSearchTest.java
git commit -m "feat(flight): tìm chuyến bay thẳng và nối chuyến cùng hãng"
```

---

### Task 3: Kiểm chứng trên seed, kiểu API cho frontend và cập nhật tài liệu

**Files:**

- Test: `backend/src/test/java/vn/edu/uit/flightbooking/flight/SearchSeedTest.java`
- Modify: `frontend/src/lib/api-types.ts` (sinh lại, không sửa tay)
- Modify: `CLAUDE.md`
- Modify: `docs/superpowers/plans/2026-10-09-00-roadmap.md`

**Interfaces:**

- Consumes: `GET /api/flights/search` (Task 2); seed `V100`–`V102`, `V106` (Plan 05, 06).
- Produces: kiểu frontend `Schemas["SearchResult"]`, `["Itinerary"]`, `["Segment"]`, `["FareOption"]`, `["AirportTime"]`, `["AirlineRef"]`; tham số query ở `operations["searchFlights"]`.

- [ ] **Step 1: Viết test trên seed**

Test này xanh ngay vì Task 2 đã xong logic. Nó ghim các con số của ví dụ TDD §8.8 trên dữ liệu demo thật và đo NFR-01.

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/flight/SearchSeedTest.java`:

```java
package vn.edu.uit.flightbooking.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;

/** Tìm kiếm trên seed V106 (1.200 chuyến): ví dụ TDD §8.8 và NFR-01. Seed chạy trong transaction của test rồi rollback. */
@IntegrationTest
@Transactional
@Sql({ "classpath:db/seed/V100__seed_airports.sql", "classpath:db/seed/V101__seed_airlines.sql",
		"classpath:db/seed/V102__seed_fare_families.sql", "classpath:db/seed/V106__seed_flights.sql" })
class SearchSeedTest {

	/** Ngày mai theo giờ Việt Nam: chuyến sớm nhất (06:00) luôn cách hiện tại hơn 3 giờ (BR-01). */
	static final LocalDate TOMORROW = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1);

	@Autowired
	MockMvc mvc;

	ResultActions search(String from, String to, LocalDate date) throws Exception {
		return mvc.perform(get("/api/flights/search").param("from", from)
			.param("to", to)
			.param("date", date.toString())
			.param("adults", "2")
			.param("children", "1")
			.param("infants", "1")
			.param("cabin", "ECONOMY"));
	}

	/** HAN→PQC: bay thẳng VN1233 rẻ hơn nên đứng trước; nối chuyến VN213 + VN1825 cho đúng các con số của TDD §8.8. */
	@Test
	void hanoiToPhuQuocMatchesTddExample() throws Exception {
		search("HAN", "PQC", TOMORROW).andExpect(status().isOk())
			.andExpect(jsonPath("$.itineraries[0].segments[*].flightNumber", contains("VN1233")))
			.andExpect(jsonPath("$.itineraries[1].segments[*].flightNumber", contains("VN213", "VN1825")))
			.andExpect(jsonPath("$.itineraries[1].totalDurationMinutes").value(360))
			.andExpect(jsonPath("$.itineraries[1].segments[0].layoverMinutesAfter").value(170))
			.andExpect(jsonPath("$.itineraries[1].segments[1].arrival.city").value("Phú Quốc"))
			.andExpect(jsonPath("$.itineraries[1].fareOptions[*].name",
					contains("Economy Lite", "Economy Classic", "Economy Flex")))
			.andExpect(jsonPath("$.itineraries[1].fareOptions[1].adultPrice").value(3_020_000))
			.andExpect(jsonPath("$.itineraries[1].fareOptions[1].totalPrice").value(9_060_000))
			.andExpect(jsonPath("$.itineraries[1].fareOptions[1].seatsLeft").value(168));
	}

	/** NFR-01: p95 dưới 1 giây. Đo 100 lần tìm trên 5 tuyến × 20 ngày, sau 10 lần chạy làm nóng. */
	@Test
	void searchP95IsUnderOneSecond() throws Exception {
		String[][] routes = { { "HAN", "PQC" }, { "HAN", "SGN" }, { "SGN", "HAN" }, { "SGN", "SIN" },
				{ "HAN", "ICN" } };
		for (int i = 0; i < 10; i++) {
			search("HAN", "PQC", TOMORROW).andExpect(status().isOk());
		}
		List<Long> millis = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			String[] route = routes[i % routes.length];
			long start = System.nanoTime();
			search(route[0], route[1], TOMORROW.plusDays(i / routes.length)).andExpect(status().isOk());
			millis.add((System.nanoTime() - start) / 1_000_000);
		}
		millis.sort(null);
		assertThat(millis.get(94)).as("p95 (ms)").isLessThan(1000);
	}

}
```

Số liệu: Economy Classic của VN213 là 1.290.000 × 1,45 = 1.870.500, làm tròn đến 10.000 thành 1.870.000; của VN1825 là 790.000 × 1,45 = 1.145.500, thành 1.150.000 (hệ số giá của seed, BACKEND_SCHEMA §8.3). VN213 hạ cánh SGN 08:10, VN1825 cất cánh 11:00, nên nối 170 phút. Bay thẳng VN1233 rẻ hơn (Economy Lite 1.490.000 so với 2.080.000) nên đứng đầu.

- [ ] **Step 2: Chạy test**

```bash
cd backend && ./mvnw test -Dtest=SearchSeedTest
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`. Lần chạy đầu mất khoảng 15–20 giây vì phải nạp 1.200 chuyến.

- [ ] **Step 3: Chạy toàn bộ test**

```bash
cd backend && ./mvnw -B verify
```

Expected: `Tests run: 127, Failures: 0, Errors: 0`, `BUILD SUCCESS`.

- [ ] **Step 4: Chạy thử profile `dev`**

DB dev đã có `V106` từ Plan 06, plan này không thêm migration.

```bash
docker compose up -d postgres mailpit
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Đợi log `Started FlightBookingApplication`. Để backend chạy, mở terminal khác (không cần đăng nhập):

```bash
D=$(date -d tomorrow +%F)
curl -s "http://localhost:8080/api/flights/search?from=HAN&to=PQC&date=$D&adults=2&children=1&infants=1&cabin=ECONOMY" | grep -o '"flightNumber":"[A-Z0-9]*"'
curl -s "http://localhost:8080/api/flights/search?from=HAN&to=HAN&date=$D&adults=1&cabin=ECONOMY"
```

Expected: lệnh đầu in `"flightNumber":"VN1233"`, `"flightNumber":"VN213"`, `"flightNumber":"VN1825"` theo thứ tự đó. Lệnh sau trả 400 với `"code":"VALIDATION_FAILED"` và `"errors":[{"field":"to","message":"Sân bay đến phải khác sân bay đi"}]`.

Nếu lệnh đầu trả `{"itineraries":[]}`: seed của DB dev đã cũ hơn 60 ngày (lộ trình §8). Làm mới bằng `docker compose down -v` rồi chạy lại.

- [ ] **Step 5: Sinh lại kiểu API cho frontend**

Backend vẫn chạy ở cổng 8080 từ Step 4. Trong terminal khác:

```bash
cd frontend && npm run gen:api && npm run lint && npm run build
```

Expected: `grep -cE "^\s+(SearchResult|Itinerary|Segment|FareOption|AirportTime|AirlineRef): \{" frontend/src/lib/api-types.ts` in `6`; `grep -n 'operations\["searchFlights"\]' frontend/src/lib/api-types.ts` in đúng một dòng; lint và build xanh. Tắt backend (Ctrl+C ở terminal của Step 4).

- [ ] **Step 6: Cập nhật CLAUDE.md**

Trong đoạn `## Status`, thay:

```markdown
and `flight` (Plan 06: flight admin, `SeatInventory`, seed `V106` with 1,200 flights) are done.
```

bằng:

```markdown
and `flight` (Plan 06: flight admin, `SeatInventory`, seed `V106` with 1,200 flights; Plan 07: `FlightSearch` and the public `GET /api/flights/search`) are done.
```

- [ ] **Step 7: Cập nhật lộ trình**

Trong `docs/superpowers/plans/2026-10-09-00-roadmap.md`:

1. **Bảng đầu tài liệu:** thêm Plan 07 vào cuối dòng "Plan chi tiết đã có":

```markdown
| Plan chi tiết đã có | [Plan 01 — Nền móng backend](2026-10-09-01-backend-foundation.md) · [Plan 02 — Tài khoản, tham số, khung email](2026-10-09-02-accounts-settings-email.md) · [Plan 04 — Màn hình tài khoản](2026-10-10-04-account-screens.md) · [Plan 05 — Danh mục](2026-10-10-05-catalog.md) · [Plan 06 — Quản trị chuyến bay và kho ghế](2026-10-11-06-flights.md) · [Plan 07 — Tìm chuyến](2026-10-11-07-flight-search.md) |
```

2. **§2:** ở dòng Plan 07, đổi `☐` cuối dòng thành `☑`.

3. **§6:** thay dòng

```markdown
| 07 | Dự kiến: `FlightSearch`, `FlightApi` (đọc chuyến, kiểm tra và định giá hành trình) |
```

bằng:

```markdown
| 07 | `flight.FlightSearch` (API công khai): `search(SearchQuery(from, to, date, adults, children, infants, cabin)) → SearchResult(itineraries)`; mỗi `Itinerary(stops, totalDurationMinutes, segments, fareOptions)`, `Segment(flightId, flightNumber, airline{code, name}, departure/arrival{airport, city, time, timezone}, durationMinutes, aircraftModel, layoverMinutesAfter)` (`null` ở chặng cuối), `FareOption(fareFamilyId, name, cabinClass, carryOnKg, checkedBaggageKg, refundable, refundFee, changeable, changeFee, adultPrice, totalPrice, seatsLeft)`. Hành trình xếp theo giá rẻ nhất rồi giờ hạ cánh; gói giá xếp theo `totalPrice`. `FlightSearch.totalPrice(adultLegPrices, adults, children, infants, childPercent, infantPercent)` (BR-21, làm tròn từng chặng) để Plan 10 dùng lại. **Chưa có `FlightApi`**: Plan 10 tạo (kiểm tra BR-01–05 và định giá hành trình đã chọn). API: `GET /api/flights/search?from=&to=&date=&adults=&children=&infants=&cabin=` (công khai; không có kết quả thì `itineraries` rỗng; số khách sai BR-11 hay `from = to` trả 400 `VALIDATION_FAILED` theo trường). Khứ hồi: client gọi hai lần và tự lọc chiều về theo BR-04. Kiểu frontend: `Schemas["SearchResult"]`, `["Itinerary"]`, `["Segment"]`, `["FareOption"]`, `["AirportTime"]`, `["AirlineRef"]`; tham số ở `operations["searchFlights"]["parameters"]["query"]` |
```

4. **§7:** thêm mục sau vào cuối danh sách (sau mục 8 về Plan 06):

```markdown
9. **Plan 07 có 2 điểm cố ý khác TDD và lộ trình** (chi tiết ở [Plan 07](2026-10-11-07-flight-search.md#điểm-cố-ý-khác-tdd-và-lộ-trình)): chưa tạo `FlightApi`, Plan 10 tạo cùng với báo giá (Plan 07 chỉ để lại hàm tính giá BR-21); tìm chiều về không có tham số riêng, Plan 08 lọc BR-04 ở client và Plan 10 kiểm tra lại ở server.
```

- [ ] **Step 8: Commit**

```bash
git add backend/src/test/java/vn/edu/uit/flightbooking/flight/SearchSeedTest.java frontend/src/lib/api-types.ts CLAUDE.md docs/superpowers/plans/2026-10-09-00-roadmap.md
git status --short
git commit -m "feat(flight): kiểm chứng tìm chuyến trên seed, sinh kiểu API và cập nhật lộ trình"
```

Expected: `git status --short` **không** liệt kê `.env`.

---

## Kiểm tra cuối plan

- [ ] `cd backend && ./mvnw -B clean verify` → `BUILD SUCCESS`, 127 test.
- [ ] `cd frontend && npm run lint && npm run build` → xanh.
- [ ] Profile `dev`: HAN→PQC ngày mai trả VN1233, rồi VN213 + VN1825 (Task 3 Step 4).
- [ ] Swagger (http://localhost:8080/swagger-ui.html, profile `dev`) có `GET /api/flights/search` với 7 tham số query riêng lẻ, không phải một tham số `query` dạng object.
- [ ] CI trên GitHub xanh (nếu đã push).
- [ ] Lộ trình đã đánh dấu Plan 07 (§2), cập nhật hợp đồng (§6) và thêm mục 9 (§7) theo code thật.
