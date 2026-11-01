# Plan 16 — Sự cố chuyến bay và manifest: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Admin huỷ được chuyến chưa cất cánh (FR-93). Khi huỷ, trong cùng một transaction, hệ thống làm đủ 4 hệ quả của BR-84:

- (a) huỷ booking chờ thanh toán, trả ghế và lượt voucher;
- (b) huỷ yêu cầu đổi đang dở có chặng trên chuyến, trả ghế chuyến mới;
- (c) tạo hoặc chuyển yêu cầu hoàn 100% nguồn `FLIGHT_CANCELLED` cho mỗi chiều đã xuất vé;
- (d) sau commit, gửi email "chuyến bị huỷ".

Đổi giờ chuyến gửi email cho khách có vé (FR-92, BR-83). Staff xem danh sách hành khách của chuyến (FR-72). Thao tác huỷ chuyến và xác nhận đổi chuyến được thử lại khi gặp deadlock (TDD §5.4). Test T12 xanh.

**Architecture:**

- `common.LockRetry` là lớp bọc `@Retryable` dùng chung, đứng ngoài method `@Transactional`.
- `flight` khoá chuyến `FOR UPDATE`, chuyển chuyến sang `CANCELLED`, rồi phát `flight.FlightCancelled`. Hai listener đồng bộ chạy theo thứ tự (TDD §3.3, SD-05):
  1. `booking` (`@Order(1)`): khoá mọi booking bị ảnh hưởng (Q-16) theo `id` tăng dần, rồi huỷ booking `PENDING_PAYMENT` bằng logic sẵn có của `BookingCancellation`;
  2. `aftersales` (`@Order(2)`): huỷ yêu cầu đổi bằng `BookingApi.cancelChange`, rồi ghi `refund_requests` cho các chiều mà `BookingApi.issuedJourneysOn` trả về.
- `notification` nghe `FlightTimeChanged` và `FlightCancelled` sau commit. Module này đọc chuyến qua `FlightApi.flight`, đọc email liên hệ qua `BookingApi.contactsOn`, và gửi mỗi booking một email.
- Manifest là truy vấn chỉ đọc Q-17 trong `FlightQueries`, như cách module này đã đọc `booking_segments` để đếm booking.

**Tech Stack:** Java 21, Spring Boot 4.1.1 (Spring Framework 7.0: `@Retryable`, `@EnableResilientMethods`; `JdbcClient`, `@EventListener` + `@Order`, `@ApplicationModuleListener`, Thymeleaf), Spring Modulith 2.1.1, PostgreSQL 18, Testcontainers, MockMvc, `@RecordApplicationEvents`, JUnit 5 + AssertJ. Phần frontend chỉ chạy lại `npm run gen:api`.

**Spec:**

- [PRD](../../PRD.md): FR-72, FR-92, FR-93, FR-130 (email đổi giờ, huỷ chuyến); BR-44, BR-45, BR-66, BR-83, BR-84, BR-87.
- [TDD](../../TDD.md): §3.2, §3.3 (`FlightTimeChanged`, `FlightCancelled`), §5.4 (khoá, `@Retryable`), §6.8, §6.10, §8.6, §8.7, §9, §11.2 (T12).
- [App Flow](../../APP_FLOW.md): UF-05, UF-07 bước 4, S-06 (chỉ API), SD-05.
- [Backend Schema](../../BACKEND_SCHEMA.md): Q-16, Q-17; ràng buộc của `refund_requests`, `reschedules`.
- [Lộ trình](2026-10-09-00-roadmap.md) §6 (dòng Plan 06, 11, 14, 15) · [Plan 15](2026-10-19-15-reschedule.md) (điểm khác TDD số 1).

## Global Constraints

- Mọi ràng buộc ở Global Constraints của Plan 01–15 vẫn giữ nguyên:
  - ProblemDetail có `code`, câu lỗi tiếng Việt;
  - DTO là `record`, tiền là `long` VND;
  - ghi bằng `JdbcClient`, không dùng entity JPA;
  - giờ chuyến bay trả theo múi giờ sân bay.
- **Phụ thuộc giữa module** (`ModularityTest` kiểm tra vòng và gói nội bộ): `notification → common, identity, flight, booking, aftersales`. `flight` mới, đúng như TDD §3.2. Phụ thuộc của các module khác không đổi; `flight` **không** phụ thuộc `booking` hay `aftersales`.
- **Không có migration mới.** `refund_requests` đã có các CHECK sau, nên yêu cầu `FLIGHT_CANCELLED` phải có `journey_id`, không có `payment_id`, và `fee_amount = 0`:
  - `refund_amount = GREATEST(base_amount - fee_amount, 0)`;
  - `(source = 'INVALID_PAYMENT') = (journey_id IS NULL)`;
  - `source = 'CUSTOMER' OR fee_amount = 0`.
- **Thứ tự khoá khi huỷ chuyến:** `flights FOR UPDATE` → `bookings` (Q-16, `id` tăng dần) → `flight_cabins`, `vouchers` (hệ quả a) → `reschedules FOR UPDATE` (`id` tăng dần) → `flight_cabins` (hệ quả b) → `refund_requests` (hệ quả c).
  - `flight_cabins` bị khoá trước `reschedules`, lệch thứ tự chung của TDD §5.4. Cách này vẫn không gây deadlock: mọi đường khác ghi `reschedules` hoặc `refund_requests` của một booking đều khoá booking trước (`lockJourney`, `Reschedules.lock`, `RefundRequests.lockWithBooking`), mà huỷ chuyến đã giữ khoá của mọi booking bị ảnh hưởng.
  - Riêng `RefundRequests.reject` chỉ khoá dòng yêu cầu. Thao tác này không chờ khoá nào khác, nên cũng không tạo vòng chờ.
- **Câu lỗi của huỷ chuyến** (kiểm tra sau khi đã khoá, theo thứ tự):
  1. không có chuyến: 404 `RESOURCE_NOT_FOUND` "Không tìm thấy chuyến bay";
  2. `status = CANCELLED`: 409 `INVALID_STATE` "Chuyến đã bị huỷ";
  3. `departure_time ≤ now` (BR-87): 409 `DEADLINE_PASSED` "Chuyến đã cất cánh nên không huỷ được".
- **Số tiền của hệ quả (c)** là giá trị chiều theo Q-08 (`JourneyRef.value`) tại lúc huỷ. Cả yêu cầu mới lẫn yêu cầu được chuyển nguồn đều có `base_amount = refund_amount = value` và `fee_amount = 0`. Yêu cầu được chuyển nguồn giữ nguyên `id`, `reason` và `requested_by`.
- **`@Retryable`** (TDD §5.4):
  - chỉ thử lại khi gặp `PessimisticLockingFailureException`;
  - tối đa 3 lần thử lại, chờ 100 ms giữa các lần;
  - đặt ở `LockRetry`, ngoài method `@Transactional`, để mỗi lần thử là một transaction mới.
- **Email** (FR-130):
  - mỗi booking một email, gửi tới `contact_email`;
  - người nhận là các booking `ISSUED` có chặng `ACTIVE` trên chuyến, đọc sau commit;
  - giờ in theo `HH:mm dd/MM/yyyy`, giờ địa phương của sân bay;
  - tiêu đề:
    - đổi giờ: `Chuyến bay {số hiệu} đổi giờ – mã đặt chỗ {code}`;
    - huỷ chuyến: `Chuyến bay {số hiệu} bị huỷ – mã đặt chỗ {code}`.

### Điểm cố ý khác TDD

1. **`@Retryable` nằm ở một lớp bọc dùng chung**, `common.LockRetry.run(Supplier)`, không phải một lớp bọc riêng cho từng thao tác. Plan này bọc huỷ chuyến và xác nhận đổi chuyến; xác nhận đổi chuyến là phần Plan 15 hẹn chuyển sang đây. Ba thao tác còn lại vẫn chưa bọc, giữ nguyên quyết định cũ:
   - xác nhận thanh toán (Plan 12): VNPay tự gọi lại khi nhận `99`;
   - hoàn tất đổi chuyến qua IPN (Plan 15): cũng đi qua callback, nên VNPay tự gọi lại;
   - duyệt hoàn (Plan 14): thứ tự khoá không tạo vòng chờ với huỷ chuyến.

Những điểm TDD không nói, plan tự chọn:

- **`POST /api/admin/flights/{id}/cancel` trả 200 `FlightDetail`**, giống `PUT`. A-06 hiện ngay trạng thái mới. Phần "xác nhận hai bước" (FR-93) do màn hình A-06 làm (Plan 20).
- **Email huỷ chuyến đọc người nhận sau commit**, bằng cùng câu đọc của email đổi giờ: booking `ISSUED` có chặng `ACTIVE` trên chuyến. Lúc đó chặng vẫn `ACTIVE`, vì ghế chỉ được trả khi Staff duyệt yêu cầu hoàn. Có một giới hạn: Staff duyệt trước khi email kịp chạy (vài giây) thì booking đó không nhận email.
- **Ghế của chuyến bị huỷ vẫn được trả** ở hệ quả (a) và (b), đúng chữ của BR-84. Nhờ vậy `available_seats` vẫn khớp với các chặng đang giữ ghế.
- **Manifest trả `{flight, passengers}`.** S-06 cần thông tin chuyến ở đầu trang, và `FlightView` đã có sẵn. Danh sách không phân trang, vì một chuyến có tối đa 999 ghế mỗi hạng.

## Review Focus

Những tình huống tài liệu không nói rõ nhưng dễ gây lỗi thật, xếp theo khả năng gặp. Mỗi dòng đã có test ghim lại.

1. **Thanh toán đến sau khi chuyến bị huỷ**, cho một booking đang chờ thanh toán hoặc một yêu cầu đổi đang dở. Kết quả đúng: tạo yêu cầu hoàn `INVALID_PAYMENT` đúng bằng lượt thu; không xuất vé và không hoàn tất đổi chuyến (Task 4 `latePaymentsAfterCancellationBecomeInvalidPaymentRefunds`).
2. **Yêu cầu đổi có chặng _mới_ trên chuyến bị huỷ, còn chuyến cũ vẫn bay.** Kết quả đúng: yêu cầu bị huỷ, chiều giữ nguyên chặng cũ, và không có yêu cầu hoàn (Task 4 T12, booking `incoming`).
3. **Chiều nối chuyến bị huỷ một chặng.** Kết quả đúng: hoàn trọn giá trị chiều, không chỉ phần của chặng bị huỷ; chiều còn lại không bị đụng tới (Task 4 `connectingJourneyIsRefundedWhole`).
4. **Chuyến đã huỷ không còn dùng được.** Không giữ chỗ được, không đổi sang được, không huỷ lần hai, và không hiện trong kết quả tìm kiếm (Task 2 `cancelRejectsCancelledDepartedOrMissingFlight`, `cancelMarksFlightCancelledAndPublishesEvent`; Task 4 `cancelledFlightCannotBeBookedOrChangedInto`).
5. **Email chỉ gửi cho booking đã xuất vé, mỗi booking một email.** Booking có nhiều khách vẫn chỉ nhận một email; booking chờ thanh toán không nhận email nào (Task 5).

---

## Trước khi bắt đầu

- **Cần có:** Docker Desktop đang chạy, Git Bash, JDK 21 trở lên, Node 24. Plan 01–15 đã xong.
- **Chạy lệnh ở đâu:** Git Bash, từ thư mục gốc repo, trừ khi bước đó ghi `cd backend` hoặc `cd frontend`.
- **Cổng PostgreSQL:** `.env` của máy dev dùng `DB_PORT=5433`. Chỉ Task 7 cần.
- **Nhánh:** tạo nhánh mới từ `develop`, VD `feat/16-flight-disruption`.
- **Trạng thái kiểm chứng:** plan viết ngày 2026-10-20 và **chưa được chạy thử**.
  - Mọi tên lớp và hàm đã được đối chiếu với code thật sau Plan 15:
    - main: `FlightService`, `FlightController`, `FlightQueries`, `FlightApi`, `FlightTimeChanged`, `BookingApi` (`JOURNEY`, `read`, `JourneyRef`), `BookingCancellation` (`release`, `Locked`), `Reschedules`, `RefundRequests`, `CustomerReschedules.confirm`, `RescheduleController`, `RescheduleEmail`, `Mailer`;
    - test: `TestBookings`, `TestRefunds`, `TestReschedules`, `TestVouchers`, `TestVnpay`.
  - `@Retryable` và `@EnableResilientMethods` đã được kiểm tra trong `spring-context-7.0.9.jar`, gói `org.springframework.resilience.annotation`. Thuộc tính dùng ở plan này: `includes`, `maxRetries`, `delay`.
  - Nếu một bước đỏ ngoài dự kiến, sửa code cho đúng ý định của bước, đừng nới test.

### Dữ liệu test dùng chung

- `TestBookings.trip`:
  - chiều đi Q1100 + Q1200 (QHA→QHB→QHE), 1.870.000 + 1.150.000 = 3.020.000, hạ cánh 12:00 ngày `DAY`;
  - chiều về Q1300 (QHE→QHA), ngày `DAY + 3` lúc 06:00, 2.160.000;
  - mọi chuyến có ECONOMY 100 ghế.
- `q1310 = TestBookings.returnFlight(mvc, jdbc, trip, "Q1310", DAY.plusDays(4), "06:00", 2_500_000)`.
- Gọi `TestReschedules.changeFee` (phí đổi 200.000) và `TestRefunds.refundable` (phí hoàn 300.000) **trước** khi giữ chỗ, vì quy định gói được chụp lại lúc giữ chỗ (BR-24).
- Đổi chiều về Q1300 → Q1310: phải trả 200.000 + 340.000 = 540.000. Đổi Q1310 → Q1300: chỉ trả phí 200.000. Cả hai đều cần thanh toán, nên yêu cầu đổi ở `PENDING_PAYMENT`.

## Cấu trúc file sau plan

```
backend/src/
├── main/
│   ├── resources/templates/email/flight-time-changed.html, flight-cancelled.html   # Task 5
│   └── java/vn/edu/uit/flightbooking/
│       ├── FlightBookingApplication.java                                  # Task 1 (@EnableResilientMethods)
│       ├── common/LockRetry.java                                          # Task 1
│       ├── flight/
│       │   ├── FlightCancelled.java                                       # Task 2
│       │   ├── FlightTimeChanged.java                                     # Task 5 (Javadoc)
│       │   ├── FlightApi.java                                             # Task 5 (flight)
│       │   ├── domain/FlightService.java                                  # Task 2 (cancel)
│       │   ├── domain/FlightQueries.java                                  # Task 6 (manifest)
│       │   └── web/FlightController.java                                  # Task 2, Task 6
│       ├── booking/
│       │   ├── BookingApi.java                                            # Task 3 (issuedJourneysOn), Task 5 (contactsOn)
│       │   └── domain/BookingCancellation.java                            # Task 3 (listener FlightCancelled)
│       ├── aftersales/
│       │   ├── domain/FlightCancellations.java                            # Task 4
│       │   └── web/RescheduleController.java                              # Task 1 (LockRetry)
│       └── notification/domain/FlightEmail.java                           # Task 5
└── test/java/vn/edu/uit/flightbooking/
    ├── common/LockRetryTest.java                                          # Task 1
    ├── flight/web/CancelFlightTest.java                                   # Task 2
    ├── flight/web/ManifestTest.java                                       # Task 6
    ├── booking/BookingFlightCancelTest.java                               # Task 3
    ├── aftersales/web/FlightCancelledTest.java                            # Task 4 (T12)
    └── notification/domain/FlightEmailTest.java                           # Task 5
frontend/src/lib/api-types.ts                                              # Task 7
docs/superpowers/plans/2026-10-09-00-roadmap.md, CLAUDE.md                 # Task 7
```

## API sau plan

| Method | Đường dẫn                            | Quyền | Thành công                                                                                                                         | Lỗi                                         |
| ------ | ------------------------------------ | ----- | ---------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------- |
| POST   | `/api/admin/flights/{id}/cancel`     | Admin | 200 `FlightDetail` (`flight.status = CANCELLED`)                                                                                   | 404, 409 `INVALID_STATE`, `DEADLINE_PASSED` |
| GET    | `/api/staff/flights/{id}/passengers` | Staff | 200 `Manifest{flight, passengers[{bookingCode, lastName, firstName, type, ticketNumber, cabinClass, contactPhone, contactEmail}]}` | 404                                         |

`SecurityConfig` không phải sửa: `/api/admin/**` dành cho `ADMIN`, `/api/staff/**` dành cho `STAFF`, và `ADMIN` kế thừa `STAFF`.

---

### Task 1: `LockRetry` — thử lại khi deadlock (TDD §5.4)

**Files:**

- Create: `backend/src/main/java/vn/edu/uit/flightbooking/common/LockRetry.java`
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/FlightBookingApplication.java`
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/aftersales/web/RescheduleController.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/common/LockRetryTest.java`

**Interfaces:**

- Produces: `common.LockRetry` (`@Component`, public) với `public <T> T run(Supplier<T> action)`, gắn `@Retryable(includes = PessimisticLockingFailureException.class, maxRetries = 3, delay = 100)`. Bên gọi không được đang ở trong transaction.

- [ ] **Step 1: Viết test hỏng**

`LockRetryTest` (`@IntegrationTest`, gói `common`, **không** `@Transactional`). Test dùng context thật, nên kiểm tra luôn việc `@EnableResilientMethods` đã được bật:

```java
	@Autowired
	LockRetry retry;

	/** TDD §5.4: lỗi khoá (deadlock) được thử lại, lần thứ ba thành công. */
	@Test
	void retriesLockFailures() {
		AtomicInteger calls = new AtomicInteger();
		String result = retry.run(() -> {
			if (calls.incrementAndGet() < 3) {
				throw new CannotAcquireLockException("deadlock");
			}
			return "ok";
		});
		assertThat(result).isEqualTo("ok");
		assertThat(calls).hasValue(3);
	}

	/** Tối đa 3 lần thử lại: tổng cộng 4 lần gọi, rồi ném lỗi gốc. */
	@Test
	void givesUpAfterThreeRetries() {
		AtomicInteger calls = new AtomicInteger();
		assertThatThrownBy(() -> retry.run(() -> {
			calls.incrementAndGet();
			throw new CannotAcquireLockException("deadlock");
		})).isInstanceOf(CannotAcquireLockException.class);
		assertThat(calls).hasValue(4);
	}

	/** Lỗi nghiệp vụ không được thử lại. */
	@Test
	void doesNotRetryBusinessErrors() {
		AtomicInteger calls = new AtomicInteger();
		assertThatThrownBy(() -> retry.run(() -> {
			calls.incrementAndGet();
			throw new BusinessException(ErrorCode.INVALID_STATE, "x");
		})).isInstanceOf(BusinessException.class);
		assertThat(calls).hasValue(1);
	}
```

`CannotAcquireLockException` (gói `org.springframework.dao`) là lớp con của `PessimisticLockingFailureException`.

- [ ] **Step 2: Chạy, thấy hỏng**

Run: `cd backend && ./mvnw test -Dtest=LockRetryTest`
Expected: lỗi biên dịch, chưa có `LockRetry`.

- [ ] **Step 3: Cài đặt**

- `LockRetry`:
  - Javadoc: "TDD §5.4: thử lại thao tác khoá nhiều dòng khi gặp deadlock. Đặt ngoài method `@Transactional`, nên mỗi lần thử là một transaction mới.";
  - `@Retryable` lấy từ `org.springframework.resilience.annotation`.
- `FlightBookingApplication`:
  - thêm `@EnableResilientMethods`;
  - Javadoc thêm câu: "`@EnableResilientMethods`: `@Retryable` của `LockRetry` (TDD §5.4)."
- `RescheduleController.confirm`:
  - tiêm `LockRetry`, rồi trả `retry.run(() -> reschedules.confirm(me.id(), code, direction, body.flightIds()))`;
  - Javadoc của hàm ghi: "Thử lại khi deadlock; đường 0 đ khoá `flight_cabins` không theo id tăng dần (Plan 15)."

- [ ] **Step 4: Chạy, thấy xanh**

Run: `cd backend && ./mvnw test -Dtest='LockRetryTest,RescheduleTest,ModularityTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/common/LockRetry.java \
  backend/src/main/java/vn/edu/uit/flightbooking/FlightBookingApplication.java \
  backend/src/main/java/vn/edu/uit/flightbooking/aftersales/web/RescheduleController.java \
  backend/src/test/java/vn/edu/uit/flightbooking/common/LockRetryTest.java
git commit -m "feat(common): LockRetry thử lại khi deadlock, bọc xác nhận đổi chuyến"
```

---

### Task 2: Huỷ chuyến trong module `flight` (FR-93, BR-87)

**Files:**

- Create: `backend/src/main/java/vn/edu/uit/flightbooking/flight/FlightCancelled.java`
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/flight/domain/FlightService.java`
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/flight/web/FlightController.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/flight/web/CancelFlightTest.java`

**Interfaces:**

- Consumes: `LockRetry.run` (Task 1).
- Produces:
  - sự kiện `flight.FlightCancelled(long flightId)`;
  - `FlightService.cancel(long id)` (`@Transactional`);
  - `POST /api/admin/flights/{id}/cancel` → 200 `FlightDetail`.

- [ ] **Step 1: Viết test hỏng**

`CancelFlightTest` (`@IntegrationTest @Transactional @RecordApplicationEvents`, gói `flight.web`).

`setUp`:

- `admin` = session Admin;
- `catalog = TestFlights.catalog(jdbc)`;
- `flightId = createOne(mvc, admin, newFlight(catalog, Map.of()))`, tức Q1100 QHA→QHB ngày `DAY`.

Hàm phụ `cancel(id, session)` gửi `POST /api/admin/flights/{id}/cancel` kèm CSRF.

```java
	/** FR-93: chuyến chuyển CANCELLED, phát FlightCancelled, không còn trong kết quả tìm kiếm. */
	@Test
	void cancelMarksFlightCancelledAndPublishesEvent() throws Exception {
		cancel(flightId, admin).andExpect(status().isOk())
			.andExpect(jsonPath("$.flight.id").value(flightId))
			.andExpect(jsonPath("$.flight.status").value("CANCELLED"));

		assertThat(events.stream(FlightCancelled.class)).containsExactly(new FlightCancelled(flightId));
		mvc.perform(get("/api/flights/search").param("from", "QHA").param("to", "QHB")
				.param("date", DAY.toString()).param("adults", "1").param("cabin", "ECONOMY"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itineraries").isEmpty());
	}
```

Thêm test **`cancelRejectsCancelledDepartedOrMissingFlight`**, kiểm tra đúng những điều sau:

1. Huỷ `flightId` lần đầu → 200.
2. Huỷ lần hai → 409, `$.code` `INVALID_STATE`, `$.detail` = "Chuyến đã bị huỷ".
3. Chuyến đã cất cánh, chèn bằng SQL như `EditFlightTest.departedOrCancelledFlightCannotBeEdited` → 409, `$.code` `DEADLINE_PASSED`, `$.detail` = "Chuyến đã cất cánh nên không huỷ được".
4. Id `999999999` → 404.
5. Session Staff → 403.
6. `events.stream(FlightCancelled.class)` có đúng một phần tử.

- [ ] **Step 2: Chạy, thấy hỏng**

Run: `cd backend && ./mvnw test -Dtest=CancelFlightTest`
Expected: lỗi biên dịch, chưa có `FlightCancelled`.

- [ ] **Step 3: Cài đặt**

**`FlightCancelled`**, Javadoc: "Admin huỷ chuyến (BR-84). Listener đồng bộ: booking (`@Order(1)`), aftersales (`@Order(2)`); notification gửi email sau commit."

**`FlightService.cancel(id)`**:

1. `SELECT status, departure_time FROM flights WHERE id = ? FOR UPDATE`. Không có dòng thì `notFound()`.
2. Kiểm tra theo thứ tự câu lỗi ở Global Constraints.
3. `UPDATE flights SET status = 'CANCELLED', updated_at = now() WHERE id = ?`.
4. `events.publishEvent(new FlightCancelled(id))`.

Javadoc của hàm: "FR-93, TDD §6.8: khoá FOR UPDATE nên mọi lượt giữ ghế (khoá FOR SHARE) trên chuyến phải xong trước; lượt đến sau thấy chuyến đã huỷ. Hệ quả BR-84 do listener của `FlightCancelled` làm, trong cùng transaction." Javadoc lớp thêm FR-93.

**`FlightController`**:

- tiêm `LockRetry`;
- thêm `@PostMapping("/admin/flights/{id}/cancel") FlightDetail cancel(@PathVariable long id)`, thân hàm là `retry.run(() -> { flights.cancel(id); return queries.detail(id); })`;
- Javadoc lớp thêm FR-93.

- [ ] **Step 4: Chạy, thấy xanh**

Run: `cd backend && ./mvnw test -Dtest='CancelFlightTest,EditFlightTest,ModularityTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/flight backend/src/test/java/vn/edu/uit/flightbooking/flight
git commit -m "feat(flight): Admin huỷ chuyến, phát FlightCancelled"
```

---

### Task 3: Booking bị ảnh hưởng — hệ quả (a) của BR-84

**Files:**

- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingCancellation.java`
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/booking/BookingApi.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/booking/BookingFlightCancelTest.java`

**Interfaces:**

- Consumes: `FlightCancelled` (Task 2).
- Produces:
  - `BookingCancellation`: listener `@EventListener @Order(1) @Transactional(propagation = MANDATORY) void on(FlightCancelled event)`;
  - `BookingApi.issuedJourneysOn(long flightId) → List<JourneyRef>` (`@Transactional(propagation = MANDATORY)`). Hàm trả các chiều `ACTIVE` của booking `ISSUED` có chặng `ACTIVE` trên chuyến, theo booking id rồi journey id. `value` tính theo Q-08. Bên gọi đã khoá booking.

- [ ] **Step 1: Viết test hỏng**

`BookingFlightCancelTest` (`@IntegrationTest @Transactional`, gói `booking`). Tiêm `MockMvc`, `JdbcClient`, `Vnpay`, `BookingApi api`.

`setUp`:

- `trip`, `customer`, `admin`;
- `voucherId = TestVouchers.insert(jdbc, "QVFLIGHT", Map.of())`;
- `pending`: giữ chỗ khứ hồi kèm `voucherCode` `QVFLIGHT`;
- `issued = TestRefunds.issued(...)`: khứ hồi;
- `other`: giữ chỗ một chiều (Q1100 + Q1200), không đi qua Q1300.

```java
	/** BR-84 (a): booking chờ thanh toán có chặng trên chuyến bị huỷ cả booking, trả ghế mọi chặng và trả lượt voucher. */
	@Test
	void cancellingFlightCancelsPendingBookingsOnIt() throws Exception {
		cancel(trip.back()).andExpect(status().isOk());

		assertThat(status(pending)).isEqualTo("CANCELLED");
		assertThat(jdbc.sql("""
				SELECT DISTINCT s.status FROM booking_segments s JOIN booking_journeys j ON j.id = s.journey_id
				WHERE j.booking_id = ?
				""").param(TestBookings.bookingId(jdbc, pending)).query(String.class).list()).containsExactly("CANCELLED");
		assertThat(TestVouchers.usedCount(jdbc, voucherId)).isZero();
		assertThat(TestBookings.available(jdbc, trip.firstLeg())).isEqualTo(98);
		assertThat(TestBookings.available(jdbc, trip.back())).isEqualTo(99);
		assertThat(status(issued)).isEqualTo("ISSUED");
		assertThat(status(other)).isEqualTo("PENDING_PAYMENT");
	}

	/** BR-84 (c) đầu vào: chiều ACTIVE của booking ISSUED có chặng ACTIVE trên chuyến, kèm giá trị Q-08. */
	@Test
	void issuedJourneysOnListsOnlyIssuedActiveJourneysOnFlight() {
		long bookingId = TestBookings.bookingId(jdbc, issued);

		assertThat(api.issuedJourneysOn(trip.back())).singleElement().satisfies(j -> {
			assertThat(j.bookingId()).isEqualTo(bookingId);
			assertThat(j.journeyId()).isEqualTo(journeyId(bookingId, "RETURN"));
			assertThat(j.value()).isEqualTo(2_160_000);
		});
		assertThat(api.issuedJourneysOn(trip.secondLeg())).singleElement()
			.satisfies(j -> assertThat(j.value()).isEqualTo(3_020_000));
		api.refundJourney(journeyId(bookingId, "RETURN"));
		assertThat(api.issuedJourneysOn(trip.back())).isEmpty();
	}
```

Hàm phụ của test:

- `status(code)`: đọc `bookings.status`;
- `journeyId(bookingId, direction)`;
- `cancel(flightId)`: gọi API huỷ bằng session Admin.

Phép tính ghế của `firstLeg`: ba booking giữ 3 ghế, `pending` trả 1, còn lại 98.

- [ ] **Step 2: Chạy, thấy hỏng**

Run: `cd backend && ./mvnw test -Dtest=BookingFlightCancelTest`
Expected: lỗi biên dịch, chưa có `issuedJourneysOn`.

- [ ] **Step 3: Cài đặt**

**`BookingCancellation.on(FlightCancelled)`:**

1. Khoá mọi booking bị ảnh hưởng (Q-16) bằng **một** câu. PostgreSQL khoá theo thứ tự `ORDER BY`. Q-16 có thể đọc không khoá, vì chuyến đã bị khoá `FOR UPDATE`, nên không chặng mới nào được thêm vào chuyến này.

   ```sql
   SELECT id, user_id, status, voucher_id, hold_expires_at FROM bookings
   WHERE id IN (SELECT j.booking_id FROM booking_segments s JOIN booking_journeys j ON j.id = s.journey_id
                WHERE s.flight_id = :flightId AND s.status IN ('ACTIVE', 'PENDING_CHANGE'))
   ORDER BY id
   FOR UPDATE
   ```

   Ánh xạ dòng bằng `BookingCancellation::locked`.

2. Với mỗi booking `PENDING_PAYMENT`, gọi `release(booking, BookingStatus.CANCELLED)`, kể cả booking trong 5 phút ân hạn. Thanh toán đến sau đó đi theo đường `UnexpectedPaymentReceived` sẵn có (BR-44).

Javadoc của hàm: "TDD §6.8, BR-84 (a): chạy đồng bộ trong transaction huỷ chuyến, trước listener của aftersales (`@Order(1)`). Khoá **mọi** booking bị ảnh hưởng, kể cả booking đã xuất vé, để aftersales ghi `reschedules` và `refund_requests` của chúng mà không cần khoá lại." Javadoc lớp thêm "chuyến bị huỷ (BR-84 a)".

**`BookingApi.issuedJourneysOn(flightId)`:**

- Tách phần `SELECT … FROM … JOIN` của `JOURNEY` thành hằng `JOURNEY_COLUMNS`. `JOURNEY` thành `JOURNEY_COLUMNS + "WHERE b.code = :code AND j.direction = :direction"`.
- Tách bộ ánh xạ dòng trong `read` thành `private static Found found(ResultSet rs, int rowNum)`.
- Câu mới:

  ```sql
  -- JOURNEY_COLUMNS +
  WHERE b.status = 'ISSUED' AND j.status = 'ACTIVE'
    AND EXISTS (SELECT 1 FROM booking_segments s
                WHERE s.journey_id = j.id AND s.flight_id = :flightId AND s.status = 'ACTIVE')
  ORDER BY b.id, j.id
  ```

  Kết quả là `.query(BookingApi::found).list()` rồi map sang `Found::ref`.

- Javadoc lớp thêm câu: "và các hàm cho chuyến bị huỷ (Plan 16)".

- [ ] **Step 4: Chạy, thấy xanh**

Run: `cd backend && ./mvnw test -Dtest='BookingFlightCancelTest,BookingLifecycleTest,BookingApiAftersalesTest,CustomerRefundTest,ModularityTest'`
Expected: PASS. `CustomerRefundTest` xanh nghĩa là việc tách `JOURNEY` không đổi hành vi của `journey`/`lockJourney`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/booking backend/src/test/java/vn/edu/uit/flightbooking/booking
git commit -m "feat(booking): huỷ chuyến huỷ booking chờ thanh toán, liệt kê chiều đã xuất vé trên chuyến"
```

---

### Task 4: Yêu cầu đổi và yêu cầu hoàn khi huỷ chuyến — hệ quả (b), (c), T12

**Files:**

- Create: `backend/src/main/java/vn/edu/uit/flightbooking/aftersales/domain/FlightCancellations.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/aftersales/web/FlightCancelledTest.java`

**Interfaces:**

- Consumes:
  - `FlightCancelled` (Task 2);
  - `BookingApi.issuedJourneysOn` (Task 3), `BookingApi.cancelChange` (Plan 15).
- Produces: `FlightCancellations` (`@Component`, package-private) với listener `@EventListener @Order(2) @Transactional(propagation = MANDATORY) void on(FlightCancelled event)`.

- [ ] **Step 1: Viết test hỏng**

`FlightCancelledTest` (`@IntegrationTest @Transactional @RecordApplicationEvents`, gói `aftersales.web`).

`setUp`:

- `trip`, `TestReschedules.changeFee`, `TestRefunds.refundable`;
- `customer`, `staff`, `admin` (session);
- `voucherId = TestVouchers.insert(jdbc, "QVCANCEL", Map.of())`;
- `q1310` (xem "Dữ liệu test dùng chung").

Hàm phụ của test:

- `issued(journeys)`: `TestRefunds.issued(mvc, jdbc, vnpay, customer, TestBookings.body(trip, Map.of("journeys", journeys)))`;
- `cancel(flightId)`: gọi API huỷ bằng session Admin;
- `segments(code, direction)`: chép từ `RescheduleTest`;
- `bookingStatus(code)`, `rescheduleStatus(id)`;
- `ipn(txnRef, amount)`: IPN thành công;
- `refunds(code)`: các dòng `refund_requests` của booking, theo `id`:

  ```sql
  SELECT r.id, j.direction, r.source, r.status, r.base_amount, r.fee_amount, r.refund_amount, r.requested_by,
         r.payment_id
  FROM refund_requests r
  JOIN bookings b ON b.id = r.booking_id
  LEFT JOIN booking_journeys j ON j.id = r.journey_id
  WHERE b.code = ?
  ORDER BY r.id
  ```

  Đọc bằng `.query().listOfRows()`.

```java
	/**
	 * T12, BR-84 (a)–(c). Trên Q1300 cùng lúc có: booking chờ thanh toán dùng voucher; yêu cầu đổi có chặng cũ trên
	 * Q1300 (changing); yêu cầu đổi có chặng mới trên Q1300 (incoming); yêu cầu hoàn đang chờ của khách (refunding).
	 */
	@Test
	void t12CancellingFlightAppliesAllConsequences() throws Exception {
		String pending = TestBookings.holdCode(mvc, customer.session(),
				TestBookings.body(trip, Map.of("journeys", trip.roundTrip(), "voucherCode", "QVCANCEL")));
		String changing = issued(trip.roundTrip());
		long r1 = TestReschedules.confirmId(mvc, customer.session(), changing, "RETURN", q1310);
		String incoming = issued(List.of(trip.journey("OUTBOUND", trip.firstLeg(), trip.secondLeg()),
				trip.journey("RETURN", q1310)));
		long r2 = TestReschedules.confirmId(mvc, customer.session(), incoming, "RETURN", trip.back());
		String refunding = issued(trip.roundTrip());
		long customerRequest = TestRefunds.requestId(mvc, customer.session(), refunding, "RETURN", "Đổi kế hoạch");
		assertThat(TestBookings.available(jdbc, trip.back())).isEqualTo(96);
		assertThat(TestBookings.available(jdbc, q1310)).isEqualTo(98);

		cancel(trip.back()).andExpect(status().isOk()).andExpect(jsonPath("$.flight.status").value("CANCELLED"));

		// (a) booking chờ thanh toán
		assertThat(bookingStatus(pending)).isEqualTo("CANCELLED");
		assertThat(TestVouchers.usedCount(jdbc, voucherId)).isZero();
		assertThat(refunds(pending)).isEmpty();
		// (b) yêu cầu đổi đang dở, chặng cũ hoặc chặng mới trên chuyến
		assertThat(rescheduleStatus(r1)).isEqualTo("CANCELLED");
		assertThat(segments(changing, "RETURN")).containsExactly("ACTIVE:Q1300", "CANCELLED:Q1310");
		assertThat(rescheduleStatus(r2)).isEqualTo("CANCELLED");
		assertThat(segments(incoming, "RETURN")).containsExactly("ACTIVE:Q1310", "CANCELLED:Q1300");
		assertThat(TestBookings.available(jdbc, trip.back())).isEqualTo(98);
		assertThat(TestBookings.available(jdbc, q1310)).isEqualTo(99);
		// (c) yêu cầu hoàn 100%: tạo mới, hoặc chuyển nguồn yêu cầu đang chờ của khách
		assertThat(refunds(changing)).singleElement().satisfies(r -> assertThat(r)
			.containsEntry("direction", "RETURN").containsEntry("source", "FLIGHT_CANCELLED")
			.containsEntry("status", "PENDING").containsEntry("base_amount", 2_160_000L)
			.containsEntry("fee_amount", 0L).containsEntry("refund_amount", 2_160_000L)
			.containsEntry("requested_by", null));
		assertThat(refunds(refunding)).singleElement().satisfies(r -> assertThat(r)
			.containsEntry("id", customerRequest).containsEntry("source", "FLIGHT_CANCELLED")
			.containsEntry("status", "PENDING").containsEntry("base_amount", 2_160_000L)
			.containsEntry("fee_amount", 0L).containsEntry("refund_amount", 2_160_000L)
			.containsEntry("requested_by", customer.id()));
		assertThat(refunds(incoming)).isEmpty();
		assertThat(List.of(bookingStatus(changing), bookingStatus(incoming), bookingStatus(refunding)))
			.containsOnly("ISSUED");
		assertThat(events.stream(FlightCancelled.class)).containsExactly(new FlightCancelled(trip.back()));
		// BR-66: yêu cầu do hệ thống tạo (kể cả yêu cầu vừa chuyển nguồn) không từ chối được
		mvc.perform(post("/api/staff/refund-requests/{id}/reject", customerRequest).with(csrf(mvc))
				.session(staff.session()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Không\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
	}
```

Phép tính ghế của `back`:

- trước khi huỷ: `pending`, `changing`, `incoming` (chặng `PENDING_CHANGE`) và `refunding` giữ 4 ghế, còn 96;
- sau khi huỷ: `pending` và `incoming` trả ghế, còn 98. `changing` và `refunding` chỉ trả ghế khi Staff duyệt hoàn.

Thêm 3 test, mỗi test kiểm tra đúng những điều sau:

- **`latePaymentsAfterCancellationBecomeInvalidPaymentRefunds`** (Review Focus 1, BR-44):
  1. `pending` giữ chỗ khứ hồi, `ref1 = TestVnpay.start(...)`, `total` đọc từ `bookings.total_amount`.
  2. `changing = issued(roundTrip)`, `r1 = confirmId(RETURN q1310)`, `ref2 = TestReschedules.payTxnRef(r1)`.
  3. `cancel(trip.back())`, rồi `ipn(ref1, total)` và `ipn(ref2, 540_000)`.
  4. `pending` vẫn `CANCELLED`, và không vé nào của `pending` có `ticket_number`.
  5. `refunds(pending)` có đúng một dòng: `source` `INVALID_PAYMENT`, `refund_amount` = `total`.
  6. `r1` vẫn `CANCELLED`; `segments(changing, "RETURN")` vẫn là `["ACTIVE:Q1300", "CANCELLED:Q1310"]`.
  7. `refunds(changing)` có đúng hai dòng: `FLIGHT_CANCELLED` 2.160.000, và `INVALID_PAYMENT` 540.000 (`payment_id` khác null).
- **`connectingJourneyIsRefundedWhole`** (Review Focus 3):
  1. `code = issued(roundTrip)`, rồi `cancel(trip.secondLeg())`.
  2. `refunds(code)` có đúng một dòng: `direction` `OUTBOUND`, `FLIGHT_CANCELLED`, `refund_amount` 3.020.000.
  3. `segments(code, "RETURN")` vẫn là `["ACTIVE:Q1300"]`.
- **`cancelledFlightCannotBeBookedOrChangedInto`** (Review Focus 4):
  1. `incoming = issued(...)` với chiều về Q1310 như trong T12, nhưng không tạo yêu cầu đổi; rồi `cancel(trip.back())`.
  2. Giữ chỗ khứ hồi mới → 409 `FLIGHT_NOT_BOOKABLE`, `$.detail` = "Chuyến Q1300 đã bị huỷ".
  3. `TestReschedules.confirm(incoming, "RETURN", trip.back())` → 409 `FLIGHT_NOT_BOOKABLE`.
  4. `TestReschedules.options(incoming, "RETURN", DAY.plusDays(3))` → 200, `$.options` rỗng.

- [ ] **Step 2: Chạy, thấy hỏng**

Run: `cd backend && ./mvnw test -Dtest=FlightCancelledTest`
Expected: FAIL. `t12…` đỏ ở `rescheduleStatus(r1)` (vẫn `PENDING_PAYMENT`). Phần (a) đã xanh nhờ Task 3.

- [ ] **Step 3: Cài đặt**

**`FlightCancellations`** (constructor `JdbcClient`, `BookingApi`). `on(FlightCancelled e)` làm hai bước:

1. **(b)** Khoá các yêu cầu đổi đang dở có chặng cũ (`ACTIVE` của chiều) hoặc chặng mới (`PENDING_CHANGE` của yêu cầu) trên chuyến:

   ```sql
   SELECT r.id FROM reschedules r
   WHERE r.status = 'PENDING_PAYMENT'
     AND EXISTS (SELECT 1 FROM booking_segments s
                 WHERE s.flight_id = :flightId
                   AND (s.reschedule_id = r.id AND s.status = 'PENDING_CHANGE'
                        OR s.journey_id = r.journey_id AND s.status = 'ACTIVE'))
   ORDER BY r.id
   FOR UPDATE
   ```

   Với mỗi id: gọi `bookings.cancelChange(id)`, rồi `UPDATE reschedules SET status = 'CANCELLED' WHERE id = ?`.

2. **(c)** Với mỗi `JourneyRef j` trong `bookings.issuedJourneysOn(flightId)`, chuyển nguồn yêu cầu đang chờ:

   ```sql
   UPDATE refund_requests
   SET source = 'FLIGHT_CANCELLED', base_amount = :value, fee_amount = 0, refund_amount = :value
   WHERE journey_id = :journeyId AND status = 'PENDING'
   ```

   Không dòng nào được cập nhật thì chèn yêu cầu mới:

   ```sql
   INSERT INTO refund_requests (booking_id, journey_id, source, status, base_amount, fee_amount, refund_amount)
   VALUES (:bookingId, :journeyId, 'FLIGHT_CANCELLED', 'PENDING', :value, 0, :value)
   ```

   Chiều `ACTIVE` không thể có yêu cầu `APPROVED`, vì duyệt hoàn chuyển chiều sang `REFUNDED` trong cùng transaction. Vì vậy `uq_refund_open_journey` không bị vi phạm.

Javadoc lớp: "BR-84 (b), (c), TDD §6.8: chạy đồng bộ trong transaction huỷ chuyến, sau listener của booking (`@Order(2)`), nên mọi booking bị ảnh hưởng đã bị khoá. Huỷ yêu cầu đổi trước, rồi mới tạo yêu cầu hoàn cho chiều."

- [ ] **Step 4: Chạy, thấy xanh**

Run: `cd backend && ./mvnw test -Dtest='FlightCancelledTest,BookingFlightCancelTest,RescheduleTest,StaffRefundTest,ModularityTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/aftersales backend/src/test/java/vn/edu/uit/flightbooking/aftersales
git commit -m "feat(aftersales): huỷ chuyến huỷ yêu cầu đổi dở và tạo yêu cầu hoàn FLIGHT_CANCELLED (T12)"
```

---

### Task 5: Email đổi giờ và huỷ chuyến (FR-92, BR-83, BR-84 d)

**Files:**

- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/flight/FlightApi.java`, `flight/FlightTimeChanged.java` (Javadoc)
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/booking/BookingApi.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/notification/domain/FlightEmail.java`
- Create: `backend/src/main/resources/templates/email/flight-time-changed.html`, `flight-cancelled.html`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/notification/domain/FlightEmailTest.java`

**Interfaces:**

- Consumes: `FlightTimeChanged` (Plan 06); `FlightCancelled` (Task 2); `Mailer.send(to, subject, template, variables)`.
- Produces:
  - `FlightApi.FlightRef(long id, String flightNumber, String departureAirport, String arrivalAirport, OffsetDateTime departureTime, OffsetDateTime arrivalTime)`, giờ theo múi giờ sân bay;
  - `@Transactional(readOnly = true) FlightRef flight(long id)` (chuyến luôn tồn tại: `single()`);
  - `BookingApi.FlightContact(String code, String contactName, String contactEmail)`;
  - `@Transactional(readOnly = true) List<FlightContact> contactsOn(long flightId)`: booking `ISSUED` có chặng `ACTIVE` trên chuyến, mỗi booking một dòng, theo booking id;
  - `FlightEmail` (`@Component`, package-private) với `void sendTimeChanged(long flightId)` và `void sendCancelled(long flightId)`. Test gọi thẳng hai hàm này.

- [ ] **Step 1: Viết test hỏng**

`FlightEmailTest` (`@IntegrationTest @Transactional`, gói `notification.domain`, cùng mẫu với `RescheduleEmailTest`).

`setUp`:

- `trip`, `customer`;
- `issuedContact`, `pendingContact`: hai email ngẫu nhiên;
- `code = TestRefunds.issued(...)`: khứ hồi, người lớn và trẻ em, liên hệ `issuedContact`;
- một booking khứ hồi chỉ giữ chỗ, liên hệ `pendingContact`.

```java
	/** BR-83, Review Focus 5: một email cho booking đã xuất vé, kèm giờ mới; booking chờ thanh toán không nhận. */
	@Test
	void timeChangedEmailGoesOncePerIssuedBooking() throws Exception {
		jdbc.sql("""
				UPDATE flights SET departure_time = departure_time + interval '3 hours',
				                   arrival_time = arrival_time + interval '3 hours'
				WHERE id = ?
				""").param(trip.back()).update();

		email.sendTimeChanged(trip.back());

		assertThat(mail.sentTo(pendingContact)).isEmpty();
		assertThat(mail.sentTo(issuedContact)).hasSize(1);
		MimeMessage message = mail.sentTo(issuedContact).getFirst();
		assertThat(message.getSubject()).isEqualTo("Chuyến bay Q1300 đổi giờ – mã đặt chỗ " + code);
		String newDeparture = "09:00 " + DAY.plusDays(3).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
		assertThat((String) message.getContent()).contains(code, "NGUYEN VAN AN", "Q1300", "QHE → QHA", newDeparture,
				"http://localhost:3000/bookings/" + code);
	}

	/** BR-84 (d): booking đã xuất vé được báo chuyến bị huỷ và yêu cầu hoàn 100%. */
	@Test
	void cancelledEmailMentionsFullRefund() throws Exception {
		email.sendCancelled(trip.back());

		assertThat(mail.sentTo(pendingContact)).isEmpty();
		assertThat(mail.sentTo(issuedContact)).singleElement().satisfies(m -> {
			assertThat(m.getSubject()).isEqualTo("Chuyến bay Q1300 bị huỷ – mã đặt chỗ " + code);
			assertThat((String) m.getContent()).contains(code, "Q1300", "hoàn 100%",
					"http://localhost:3000/bookings/" + code);
		});
	}
```

- [ ] **Step 2: Chạy, thấy hỏng**

Run: `cd backend && ./mvnw test -Dtest=FlightEmailTest`
Expected: lỗi biên dịch, chưa có `FlightEmail`.

- [ ] **Step 3: Cài đặt**

**`FlightApi.flight(id)`:** dùng lại câu `FLIGHTS` với `ids = List.of(id)` và bộ ánh xạ `FlightApi::flight` sẵn có, rồi đổi bản ghi `Flight` sang `FlightRef`.

**`BookingApi.contactsOn(flightId)`:**

```sql
SELECT DISTINCT b.id, b.code, b.contact_name, b.contact_email
FROM bookings b
JOIN booking_journeys j ON j.booking_id = b.id
JOIN booking_segments s ON s.journey_id = j.id
WHERE s.flight_id = ? AND s.status = 'ACTIVE' AND b.status = 'ISSUED'
ORDER BY b.id
```

**`FlightEmail`** (constructor `FlightApi`, `BookingApi`, `Mailer`, `@Value("${app.base-url}") String baseUrl`):

- `@ApplicationModuleListener void on(FlightTimeChanged e)` gọi `sendTimeChanged(e.flightId())`;
- `@ApplicationModuleListener void on(FlightCancelled e)` gọi `sendCancelled(e.flightId())`;
- cả hai hàm `send…` đọc `FlightRef` một lần, rồi gửi một email cho mỗi `FlightContact`. Tiêu đề lấy ở Global Constraints. Biến template: `contactName`, `code`, `flight` (`FlightRef`), `link = baseUrl + "/bookings/" + code`.

`Mailer` tự bắt lỗi gửi, nên một địa chỉ lỗi không chặn các email sau.

Javadoc lớp: "Email chuyến bay đổi giờ (BR-83) và bị huỷ (BR-84 d), gửi tới email liên hệ của các booking đã xuất vé có chặng trên chuyến. Chạy bất đồng bộ sau commit; gửi lỗi chỉ ghi log." `FlightTimeChanged`: bỏ câu "Plan 16 thêm listener gửi email."

**Template:** chép khung `reschedule-completed.html`: phần thân, câu chào, nút "Xem booking" và chú thích giờ địa phương. Bỏ bảng vé và dòng tiền. Định dạng giờ dùng `#temporals.format(…, 'HH:mm dd/MM/yyyy')`.

- `flight-time-changed.html`:
  - `<title>`: "Chuyến bay đổi giờ";
  - đoạn chính: "Chuyến **{flightNumber}** ({departureAirport} → {arrivalAirport}) trong booking **{code}** đã đổi giờ. Giờ mới: cất cánh {departureTime}, hạ cánh {arrivalTime}.";
  - đoạn phụ (BR-83): "Nếu chuyến này nối với một chuyến khác trong hành trình, vui lòng kiểm tra lại thời gian nối chuyến và liên hệ tổng đài nếu cần hỗ trợ."
- `flight-cancelled.html`:
  - `<title>`: "Chuyến bay bị huỷ";
  - đoạn chính: "Chuyến **{flightNumber}** ({departureAirport} → {arrivalAirport}) khởi hành {departureTime} trong booking **{code}** đã bị huỷ.";
  - đoạn tiếp: "Chúng tôi đã tạo yêu cầu hoàn 100% giá trị chiều có chuyến này, không thu phí. Nhân viên sẽ xử lý và hoàn tiền về phương thức thanh toán ban đầu; bạn sẽ nhận email khi hoàn tiền xong."

- [ ] **Step 4: Chạy, thấy xanh**

Run: `cd backend && ./mvnw test -Dtest='FlightEmailTest,RescheduleEmailTest,TicketEmailTest,FlightApiTest,ModularityTest'`
Expected: PASS. `ModularityTest` xanh nghĩa là `notification → flight` không tạo vòng.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/flight backend/src/main/java/vn/edu/uit/flightbooking/booking/BookingApi.java \
  backend/src/main/java/vn/edu/uit/flightbooking/notification backend/src/main/resources/templates/email \
  backend/src/test/java/vn/edu/uit/flightbooking/notification
git commit -m "feat(notification): email chuyến bay đổi giờ và bị huỷ"
```

---

### Task 6: Danh sách hành khách theo chuyến (FR-72)

**Files:**

- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/flight/domain/FlightQueries.java`
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/flight/web/FlightController.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/flight/web/ManifestTest.java`

**Interfaces:**

- Produces, ở `FlightQueries`:
  - `record ManifestPassenger(String bookingCode, String lastName, String firstName, String type, String ticketNumber, CabinClass cabinClass, String contactPhone, String contactEmail)`. `type` là chuỗi, vì enum `PassengerType` thuộc gói nội bộ của booking;
  - `record Manifest(FlightView flight, List<ManifestPassenger> passengers)`;
  - `@Transactional(readOnly = true) Manifest manifest(long id)`: chuyến không tồn tại thì `notFound()`.
- Produces, API: `GET /api/staff/flights/{id}/passengers` → 200 `Manifest`.

- [ ] **Step 1: Viết test hỏng**

`ManifestTest` (`@IntegrationTest @Transactional`, gói `flight.web`).

`setUp`:

- `trip`, `customer`, `staff`;
- `code = TestRefunds.issued(...)`: khứ hồi, ba khách. Tạo bằng `TestBookings.passenger`, rồi sửa `firstName`:
  - người lớn `VAN AN`;
  - trẻ em `THI BE`;
  - em bé `MINH`;
- một booking khứ hồi chỉ giữ chỗ.

```java
	/** FR-72, Q-17: vé hiệu lực của booking đã xuất vé trên chuyến, xếp theo họ tên; booking chờ thanh toán bị bỏ. */
	@Test
	void manifestListsTicketsOfIssuedBookings() throws Exception {
		mvc.perform(get("/api/staff/flights/{id}/passengers", trip.back()).session(staff.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.flight.flightNumber").value("Q1300"))
			.andExpect(jsonPath("$.passengers.length()").value(3))
			.andExpect(jsonPath("$.passengers[*].firstName").value(contains("MINH", "THI BE", "VAN AN")))
			.andExpect(jsonPath("$.passengers[*].type").value(contains("INFANT", "CHILD", "ADULT")))
			.andExpect(jsonPath("$.passengers[0].bookingCode").value(code))
			.andExpect(jsonPath("$.passengers[0].lastName").value("NGUYEN"))
			.andExpect(jsonPath("$.passengers[0].ticketNumber").value(matchesPattern("901\\d{10}")))
			.andExpect(jsonPath("$.passengers[0].cabinClass").value("ECONOMY"))
			.andExpect(jsonPath("$.passengers[0].contactPhone").value("0901234567"))
			.andExpect(jsonPath("$.passengers[0].contactEmail").value("an@example.com"));
	}
```

Thêm test **`manifestNeedsStaffAndExistingFlight`**, kiểm tra đúng những điều sau:

- session Customer → 403;
- id `999999999` → 404;
- sau `api.refundJourney(journeyId RETURN)` (tiêm `BookingApi`, chạy chung transaction test) → `$.passengers` rỗng.

- [ ] **Step 2: Chạy, thấy hỏng**

Run: `cd backend && ./mvnw test -Dtest=ManifestTest`
Expected: FAIL, 404 vì chưa có đường dẫn.

- [ ] **Step 3: Cài đặt**

- `manifest(id)`:
  - `FlightView` lấy bằng `views(SELECT_FLIGHT + " WHERE f.id = :id", …)` như `detail`;
  - danh sách khách dùng nguyên Q-17 (BACKEND_SCHEMA §7).
- Javadoc lớp `FlightQueries`: thêm FR-72, và thêm `passengers`, `tickets`, `bookings` vào danh sách bảng đọc bằng SQL.
- `FlightController`: thêm `@GetMapping("/staff/flights/{id}/passengers") Manifest passengers(@PathVariable long id)`. Javadoc lớp thêm FR-72, S-06.

- [ ] **Step 4: Chạy, thấy xanh**

Run: `cd backend && ./mvnw test -Dtest='ManifestTest,FlightListTest,ModularityTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/flight backend/src/test/java/vn/edu/uit/flightbooking/flight
git commit -m "feat(flight): danh sách hành khách theo chuyến cho Staff (FR-72)"
```

---

### Task 7: Toàn bộ test, kiểu API, thử tay và tài liệu

**Files:**

- Modify: `frontend/src/lib/api-types.ts` (sinh lại, không sửa tay)
- Modify: `docs/superpowers/plans/2026-10-09-00-roadmap.md`, `CLAUDE.md`

**Interfaces:**

- Consumes: toàn bộ Task 1–6.
- Produces: kiểu frontend `Schemas["Manifest"]`, `["ManifestPassenger"]`; `operations` có `cancel` và `passengers`.

- [ ] **Step 1: Chạy toàn bộ test backend**

Run: `cd backend && ./mvnw verify`
Expected: `BUILD SUCCESS`; toàn bộ test của Plan 01–15 vẫn xanh.

- [ ] **Step 2: Sinh lại kiểu API**

Terminal 1: `docker compose up -d postgres mailpit`, rồi `cd backend && ./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"`.

Terminal 2:

```bash
cd frontend && npm run gen:api && npm run lint && npm run build
```

Expected: `api-types.ts` có hai đường dẫn mới và hai kiểu ở mục Produces; lint và build xanh.

- [ ] **Step 3: Thử tay**

Backend vẫn chạy ở terminal 1. Ở terminal 3:

1. Bằng đoạn `curl` của Plan 12 Task 5 Step 3, đăng nhập `customer@demo.local`, giữ chỗ một chuyến seed rồi thanh toán sandbox. Ghi lại `flightId` của chặng đầu.
2. Đăng nhập `staff@demo.local`, gọi `GET /api/staff/flights/{flightId}/passengers`. Expected: có khách vừa đặt.
3. Đăng nhập `admin@demo.local`. Gọi `GET /api/admin/flights/{flightId}`, rồi `PUT` lại đúng body đó với giờ cất cánh và hạ cánh dời 1 giờ. Expected: Mailpit có "Chuyến bay … đổi giờ – mã đặt chỗ …".
4. `POST /api/admin/flights/{flightId}/cancel`. Expected:
   - 200 với `flight.status = CANCELLED`;
   - Mailpit có "Chuyến bay … bị huỷ – mã đặt chỗ …";
   - `GET /api/staff/refund-requests?source=FLIGHT_CANCELLED` có yêu cầu 100% giá trị chiều.

Dừng backend sau khi xong. Chuyến seed đã bị huỷ thì không khôi phục được; làm mới dữ liệu bằng `docker compose down -v`.

- [ ] **Step 4: Cập nhật lộ trình**

Trong `docs/superpowers/plans/2026-10-09-00-roadmap.md`:

(a) Ở dòng "Plan chi tiết đã có", thêm vào cuối: ` · [Plan 16 — Sự cố chuyến bay và manifest](2026-10-20-16-flight-disruption.md)`.

(b) Ở §2, dòng Plan 16, đổi `☐` thành `☑`.

(c) Ở §6, thêm dòng sau dòng Plan 15:

```markdown
| 16 | `common.LockRetry.run(Supplier)`: `@Retryable` (`PessimisticLockingFailureException`, 3 lần, 100 ms) ngoài transaction, bật bằng `@EnableResilientMethods`; đang bọc huỷ chuyến và xác nhận đổi chuyến. `flight.FlightCancelled(flightId)`; `FlightService.cancel` (khoá `FOR UPDATE`; đã huỷ → `INVALID_STATE`, đã cất cánh → `DEADLINE_PASSED`). Listener đồng bộ: `BookingCancellation.on(FlightCancelled)` (`@Order(1)`, khoá mọi booking Q-16 theo id, huỷ booking `PENDING_PAYMENT`), `aftersales.domain.FlightCancellations` (`@Order(2)`, huỷ yêu cầu đổi có chặng cũ hoặc mới trên chuyến, tạo hoặc chuyển yêu cầu hoàn sang `FLIGHT_CANCELLED` phí 0, số tiền = giá trị chiều). `booking.BookingApi.issuedJourneysOn(flightId) → List<JourneyRef>`, `contactsOn(flightId) → List<FlightContact(code, contactName, contactEmail)>`. `flight.FlightApi.flight(id) → FlightRef(id, flightNumber, departureAirport, arrivalAirport, departureTime, arrivalTime)`. `notification` phụ thuộc thêm `flight`: `FlightEmail` nghe `FlightTimeChanged`, `FlightCancelled`, template `flight-time-changed`, `flight-cancelled`. `FlightQueries.manifest(id) → Manifest(flight, passengers[ManifestPassenger])` (Q-17). API: `POST /api/admin/flights/{id}/cancel` (200 `FlightDetail`), `GET /api/staff/flights/{id}/passengers`. Kiểu frontend: `Schemas["Manifest"]`, `["ManifestPassenger"]` |
```

(d) Ở §7, thêm mục cuối:

```markdown
17. **Plan 16 có 1 điểm cố ý khác TDD** (chi tiết ở [Plan 16](2026-10-20-16-flight-disruption.md#điểm-cố-ý-khác-tdd)): `@Retryable` nằm ở lớp bọc dùng chung `LockRetry`, mới bọc huỷ chuyến và xác nhận đổi chuyến; xác nhận thanh toán, hoàn tất đổi chuyến qua IPN và duyệt hoàn vẫn chưa bọc như quyết định của Plan 12, 14, 15.
```

- [ ] **Step 5: Cập nhật CLAUDE.md**

Trong mục Status, thay:

```
completion, `RescheduleExpiryJob` and the reschedule email) are done.
```

bằng:

```
completion, `RescheduleExpiryJob` and the reschedule email; Plan 16: flight cancellation `POST /api/admin/flights/{id}/cancel` with the BR-84 consequences run by `FlightCancelled` listeners in `booking` (`@Order(1)`) and `aftersales` (`@Order(2)`), flight time-changed and cancelled emails, the manifest `GET /api/staff/flights/{id}/passengers`, and `common.LockRetry` (`@Retryable` on deadlock)) are done.
```

- [ ] **Step 6: Commit**

```bash
git add frontend/src/lib/api-types.ts docs/superpowers/plans/2026-10-09-00-roadmap.md CLAUDE.md
git commit -m "docs: hoàn tất Plan 16, cập nhật hợp đồng sự cố chuyến bay và kiểu API frontend"
```

**Không thuộc plan này:**

- màn A-06 với nút huỷ chuyến xác nhận hai bước (Plan 20);
- màn S-06 danh sách hành khách (Plan 18);
- bọc `@Retryable` cho duyệt hoàn và xác nhận thanh toán (xem "Điểm cố ý khác TDD").
