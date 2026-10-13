# Plan 11 — Giữ chỗ và vòng đời booking: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khách giữ chỗ được (`POST /api/bookings`). Ghế và lượt voucher bị trừ nguyên tử, giá được chốt. Booking có tổng tiền 0 được xuất vé ngay. Khách huỷ được booking chưa thanh toán, và job mỗi phút huỷ booking đã quá hạn hơn 5 phút; cả hai trường hợp đều trả ghế và lượt voucher đúng một lần. Khách xem được danh sách và chi tiết booking của mình, Staff tra cứu được mọi booking. 20 luồng cùng giữ 5 ghế cuối không bán vượt (T1), và một tài khoản dùng cùng một voucher cho hai booking cùng lúc thì chỉ một booking thành công (T16). Plan này cũng xử lý 6 lỗi nhỏ còn lại từ Plan 10.

**Architecture:** Module `booking` ghi các bảng của mình bằng `JdbcClient`, giống module `flight`. Không dùng entity JPA, vì mọi chuyển trạng thái đều phải khoá dòng (`FOR UPDATE`) rồi `UPDATE` có điều kiện, và trộn với persistence context sẽ gặp lại bẫy "entity đã nạp không thấy SQL" của Plan 09.

`BookingService.create` làm theo thứ tự sau:

1. Khoá chia sẻ các chuyến (`SeatInventory.lockFlights`, thứ tự khoá TDD §5.4).
2. Gọi lại `QuoteService.quote` trên dữ liệu đã khoá, nên mọi lỗi của báo giá cũng là lỗi của giữ chỗ.
3. Giữ ghế theo `flightId` tăng dần (Q-01), rồi giữ lượt voucher (Q-14).
4. Sinh mã đặt chỗ bằng `INSERT … ON CONFLICT (code) DO NOTHING` và ghi 6 bảng. Vi phạm `uq_bookings_voucher_user` trả `VOUCHER_INVALID`/`ALREADY_USED`.

`BookingService.issue` cấp số vé (Q-13) và phát `booking.BookingIssued`; Plan 12 dùng lại hàm này khi thanh toán thành công. `BookingCancellation` dùng chung một hàm trả ghế và trả lượt cho việc khách huỷ (FR-33) và job hết hạn (TDD §6.5). `BookingQueries` đọc danh sách và chi tiết bằng SQL.

**Tech Stack:** Java 21, Spring Boot 4.1.1 (Spring MVC, `JdbcClient`, `@Scheduled`, Bean Validation), Spring Modulith 2.1.1, PostgreSQL 18, Testcontainers, MockMvc, `@RecordApplicationEvents`, JUnit 5 + AssertJ. Frontend chỉ chạy lại `npm run gen:api`.

**Spec:** [PRD](../../PRD.md) FR-24, FR-33, FR-40, FR-41, FR-70, BR-24, BR-32, BR-40, BR-43, BR-45, BR-46, BR-104 · [TDD](../../TDD.md) §3.2, §3.3 (`BookingIssued`), §4.3, §5.1, §5.4, §6.2 (bước 5), §6.4, §6.5, §8.3, §8.6, §8.8, §9, §11.2 (T1, T2, T5, T15, T16) · [App Flow](../../APP_FLOW.md) C-08, C-09, S-01, S-02, §4.1, §4.3, SD-01, SD-02 · [Backend Schema](../../BACKEND_SCHEMA.md) §5 (bảng `bookings` … `booking_baggage`), Q-01, Q-02, Q-07, Q-13, Q-14, Q-15 · [Lộ trình](2026-10-09-00-roadmap.md) §2, §6 (dòng Plan 10) · [Plan 10](2026-10-13-10-booking-quote.md)

## Global Constraints

- Mọi ràng buộc ở Global Constraints của Plan 01, 02, 06, 07, 09 và [Plan 10](2026-10-13-10-booking-quote.md) vẫn giữ nguyên: ProblemDetail có `code`, CSRF SPA, câu lỗi tiếng Việt, DTO là `record`, tiền `long` VND, giờ theo múi giờ sân bay.
- Phụ thuộc giữa module: `booking → common, catalog, flight, identity, promotion`. Phụ thuộc `identity` là mới (xem "Điểm cố ý khác TDD"). `ModularityTest` kiểm tra.
- **Không có migration mới.** Mọi bảng đã có trong `V1__init.sql`.
- **Thứ tự khoá** (TDD §5.4): `flights` (FOR SHARE) → `bookings` → `flight_cabins` → `vouchers`. Trong cùng bảng thì theo `id` tăng dần.
  - Giữ chỗ: khoá chuyến → giữ ghế (Q-01, `flightId` tăng dần) → giữ lượt (Q-14) → thêm dòng `bookings`.
  - Huỷ và hết hạn: khoá dòng `bookings` (`FOR UPDATE`) và kiểm tra lại trạng thái → trả ghế (Q-02, `flightId` tăng dần) → trả lượt (Q-15).
- **Bất biến ghế** (APP_FLOW §4.3): mỗi chặng `ACTIVE` giữ đúng (số người lớn + số trẻ em) ghế trong hạng ghế của chiều. Khi chặng rời `ACTIVE`, số ghế đó được cộng trả lại **đúng một lần**. Em bé không chiếm ghế.
- **BR-40, BR-45:**
  - Hạn giữ chỗ = thời điểm tạo + `booking.hold_minutes` (đọc khi tạo, BR-25).
  - Job chỉ huỷ booking `PENDING_PAYMENT` có `hold_expires_at < now − 5 phút`. 5 phút là hằng số (PRD §7). Mỗi lượt tối đa 100 booking (Q-07), mỗi booking một transaction riêng.
  - Tổng thanh toán 0 thì xuất vé ngay trong cùng transaction.
- **BR-46:** mã 6 ký tự lấy từ `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`, khớp CHECK `^[A-HJ-NP-Z2-9]{6}$`. Thử tối đa 5 mã khi trùng.
- **BR-43, Q-13:** số vé = `ticket_prefix` của hãng + `lpad(nextval('ticket_number_seq'), 10, '0')`, cấp cho mọi vé của chặng `ACTIVE`, và ghi `issued_at` cùng lúc (CHECK của `tickets`).
- **BR-104:** Customer gọi API với booking không phải của mình thì nhận 404 `RESOURCE_NOT_FOUND`, giống hệt mã không tồn tại.
- **Giờ của booking** (`holdExpiresAt`, `issuedAt`, `createdAt`) trả theo `+07:00`, như thời gian của voucher, vì booking không gắn với một sân bay. Giờ của chặng trả theo múi giờ sân bay.
- Mã đặt chỗ trên URL và ô tìm kiếm không phân biệt hoa thường (khách hay gõ chữ thường).
- Test tự tạo dữ liệu. Test chạy trong transaction dùng `TestBookings.trip` (dữ liệu theo ví dụ TDD §8.8). Test đồng thời không dùng `@Transactional`: tự tạo dữ liệu bằng SQL với mã riêng (`Q8`, `QYA`, `QYB`, `QVRACE11`) và tự dọn.

### Sáu lỗi nhỏ còn lại từ Plan 10

| #   | Lỗi                                                                                           | Xử lý                                                                                                                                                                                                                                                                                             | Task |
| --- | --------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- |
| 1   | Lỗi trả theo từng tầng, nên form sai nhiều chỗ nhận tới 4 lần 400                             | **Sửa một phần:** lỗi BR-10, BR-13 và lỗi hành lý gom vào **một** lần trả. Mã là `PASSENGER_RULES_VIOLATED` nếu có ít nhất một lỗi hành khách, ngược lại là `VALIDATION_FAILED`. Bean Validation vẫn chạy trước (Spring dừng ở đó). BR-11 vẫn chạy trước hành trình vì kiểm tra ghế cần số khách. | 1    |
| 2   | Chuỗi rỗng ở `nationality`, `passportNumber` trên chuyến nội địa trả 400                      | **Sửa:** `PassengerRequest` đổi chuỗi trống thành `null` trước khi Bean Validation chạy. Chuyến quốc tế vẫn báo thiếu (BR-13).                                                                                                                                                                    | 1    |
| 3   | `Quote` không mang giá từng vé từng chặng, nên giữ chỗ phải đọc lại tỉ lệ giá trẻ em và em bé | **Sửa:** `Quote` mang thêm `childPercent`, `infantPercent` đã dùng khi tính giá. Giữ chỗ tính `tickets.fare_amount` bằng `Pricing.ticketPrice` với đúng hai số đó, nên tổng các vé luôn bằng `fareTotal`.                                                                                         | 2    |
| 4   | `FlightSearch.percentOf` thành `public` để booking dùng                                       | **Giữ nguyên.** Mới có hai nơi dùng; khi module thứ ba cần thì tách ra một lớp tính tiền chung.                                                                                                                                                                                                   | —    |
| 5   | Câu báo thiếu ghế hiện tên enum "hạng ECONOMY"                                                | **Giữ nguyên** theo đúng câu mẫu của TDD §8.8. C-07 không hiện câu này mà đưa khách về C-02 với thông báo riêng (APP_FLOW C-07).                                                                                                                                                                  | —    |
| 6   | Mỗi dòng hành lý chỉ báo lỗi đầu tiên                                                         | **Sửa:** mỗi dòng kiểm tra độc lập ba nhóm (khách, chiều, mức hành lý) và báo đủ cả ba.                                                                                                                                                                                                           | 1    |

### Điểm cố ý khác TDD

1. **Chưa tạo `booking.BookingApi`, chưa có sự kiện `UnexpectedPaymentReceived`** (lộ trình §6 ghi "dự kiến" cho Plan 11). Chưa module nào gọi sang `booking`. Plan 12 (thanh toán) và Plan 14 (hậu mãi) tạo khi có nơi dùng, giống cách Plan 05 và 06 hoãn `CatalogApi`. Plan này chỉ tạo sự kiện `booking.BookingIssued(bookingId)` (TDD §3.3), vì xuất vé đã xảy ra ở đây (BR-40).
2. **`booking` phụ thuộc thêm `identity`** (TDD §3.2 không liệt kê) để chi tiết booking của Staff có tài khoản đã đặt (FR-70, S-02), lấy qua `UserApi.get`. `identity` chỉ phụ thuộc `common` nên không tạo vòng phụ thuộc.
3. **Chi tiết booking chưa có lượt thu, lượt hoàn, yêu cầu hoàn và yêu cầu đổi** (FR-41). Các bảng đó chưa được module nào ghi; Plan 12, 14, 15 thêm vào `BookingDetail`.
4. **Job hết hạn chưa xử lý `reschedules`** (TDD §6.5 bước 3). Phần đó thuộc Plan 15 (đổi chuyến, BR-75).
5. **`POST /api/bookings/{code}/cancel` trả 204**, vì TDD §8.3 không nói phản hồi. C-09 tải lại chi tiết sau khi huỷ.

Những điểm TDD không nói, plan tự chọn:

- **Giữ chỗ khoá chuyến trước rồi mới báo giá lại.** TDD §6.2 làm bước 1–4 rồi mới khoá và "kiểm tra lại trạng thái `SCHEDULED`". Cách ở đây cho kết quả tương đương mà gọn hơn: mọi kiểm tra của báo giá chạy trên các chuyến đã khoá chia sẻ, nên không cần kiểm tra trạng thái lần thứ hai.
- **Mã trùng xử lý bằng `ON CONFLICT (code) DO NOTHING RETURNING id`.** Không có dòng trả về thì sinh mã khác. Cách này không làm hỏng transaction như khi bắt lỗi unique, và vi phạm `uq_bookings_voucher_user` vẫn nổ thành `DuplicateKeyException` để đổi sang `ALREADY_USED` (T16).
- **Khách được huỷ cả khi đã quá hạn giữ chỗ nhưng còn trong 5 phút ân hạn**, vì booking vẫn `PENDING_PAYMENT` (APP_FLOW §4.1).
- **`expire` kiểm tra lại cả trạng thái lẫn hạn sau khi khoá.** Nếu booking đã được thanh toán, đã bị huỷ, hoặc chưa quá ân hạn thì bỏ qua và trả `false`. Nhờ vậy job và khách huỷ cùng lúc không trả ghế hai lần.
- **"Chặng hiện hành" trong danh sách và chi tiết là chặng `ACTIVE` hoặc `CANCELLED`.** Booking đã huỷ vẫn hiện tuyến bay. Plan 15 (đổi chuyến) sẽ tinh chỉnh cho chặng `PENDING_CHANGE` và `REPLACED`.
- **Tra cứu của Staff khớp nguyên văn**: mã đặt chỗ và email không phân biệt hoa thường, số điện thoại so đúng chuỗi. FR-70 không yêu cầu tìm gần đúng.
- **`@EnableScheduling` bật ở mọi profile, kể cả khi chạy test.** Job chỉ thấy dữ liệu đã commit và quá hạn hơn 5 phút. Test đồng thời không tạo dữ liệu như vậy, và test hết hạn chạy trong transaction riêng của test, nên job nền không chen vào được.

## Review Focus

Những tình huống tài liệu không nói rõ nhưng dễ gây lỗi thật, xếp theo khả năng gặp. Mỗi dòng đã có test ghim lại.

1. **Trả ghế hai lần.** Khách huỷ rồi job hết hạn chạy, hoặc ngược lại, thì ghế vẫn chỉ được cộng một lần (Task 4 `releasesSeatsOnlyOnce`).
2. **Huỷ trong 5 phút ân hạn** vẫn được, vì booking chưa hết hạn chính thức. Job không đụng tới booking còn trong ân hạn (Task 4 `customerMayCancelDuringGrace`, `expiryJobWaitsForGracePeriod`).
3. **Giữ chỗ thất bại không để lại gì:** không ghế nào bị trừ, không lượt voucher nào bị giữ, không có dòng booking nào. Áp dụng cho cả lỗi dữ liệu lẫn lỗi hết ghế (Task 2 `failedHoldKeepsEverySeatAndVoucherUse`, `rejectedHoldWritesNothing`).
4. **Admin đổi giá hoặc gói giá sau khi giữ chỗ** thì chi tiết booking vẫn hiện giá và quy định lúc giữ chỗ (BR-24; Task 5 `detailShowsSnapshotPassengersTicketsAndBaggage`).
5. **Mã đặt chỗ gõ chữ thường** vẫn tìm thấy, ở cả trang của khách, thao tác huỷ và tra cứu của Staff. Mã của người khác trả 404 giống mã không tồn tại (Task 4 `onlyOwnersCancelAndOnlyWhilePending`; Task 5 `otherCustomersGetNotFound`, `staffFindsByCodeEmailOrPhoneAndSeesAccount`).

---

## Trước khi bắt đầu

- **Cần có:** Docker Desktop đang chạy, Git Bash, JDK 21 trở lên, Node 24. Plan 01–10 đã xong.
- **Chạy lệnh ở đâu:** Git Bash, từ thư mục gốc repo, trừ khi bước đó ghi `cd backend` hoặc `cd frontend`. Ở PowerShell, đặt đối số `-D...` trong dấu nháy.
- **Cổng PostgreSQL:** `.env` của máy dev dùng `DB_PORT=5433`. Chỉ Task 6 (chạy profile `dev`) cần.
- **Nhánh:** tạo nhánh mới từ `develop`, VD `feat/11-booking-hold`.
- **Trạng thái kiểm chứng:** plan viết ngày 2026-10-13 và **chưa được chạy thử**. Mọi tên lớp và hàm được đối chiếu với code thật sau Plan 10 (`QuoteService`, `Quote`, `BookingRequest`, `PassengerRules`, `FieldErrors`, `Pricing`, `FlightApi`, `SeatInventory`, `VoucherApi`, `VoucherRejection`, `UserApi`, `BookingQuoteTest`, `TestFlights`, `TestVouchers`, `TestUsers`, `SeatInventoryTest`, `V1__init.sql`). Nếu một bước đỏ ngoài dự kiến, sửa code cho đúng ý định của bước, đừng nới test.

### Ba điều cần biết khi viết test cho plan này

- **`now()` của PostgreSQL đứng yên trong một transaction.** Test `@Transactional` đặt `hold_expires_at = now() - interval '6 minutes'` là tính từ lúc test bắt đầu. Mọi booking tạo trong cùng test có cùng `created_at`, nên danh sách sắp theo `created_at DESC, id DESC`.
- **Job nền không thấy dữ liệu của test `@Transactional`**, vì dữ liệu chưa commit. Test hết hạn gọi thẳng `BookingExpiryJob.expireOverdue()` trong transaction của test.
- **Test đồng thời commit dữ liệu thật.** `BookingConcurrencyTest` tự tạo hãng `Q8`, sân bay `QYA`/`QYB`, voucher `QVRACE11` và một tài khoản, rồi xoá theo đúng thứ tự khoá ngoại ở `@AfterEach`. Đừng đổi những mã này sang mã mà test khác đang dùng (`Q1`–`Q3`, `Q9`, `QHA`–`QHE`, `QZA`, `QZB`).

## Cấu trúc file sau plan

```
backend/src/
├── main/java/vn/edu/uit/flightbooking/
│   ├── FlightBookingApplication.java                       # Task 4: @EnableScheduling
│   ├── flight/SeatInventory.java                           # Task 2: lockFlights
│   └── booking/
│       ├── BookingIssued.java                              # Task 2 (sự kiện công khai)
│       ├── domain/
│       │   ├── BookingRequest.java, FieldErrors.java, PassengerRules.java   # Task 1 (sửa)
│       │   ├── QuoteService.java                           # Task 1 (thay toàn bộ), Task 2 (sửa 1 dòng)
│       │   ├── Quote.java                                  # Task 2 (thêm 2 trường)
│       │   ├── BookingStatus.java, BookingCodes.java, BookingService.java   # Task 2
│       │   ├── BookingCancellation.java, BookingExpiryJob.java              # Task 4
│       │   └── BookingQueries.java                         # Task 5
│       └── web/
│           ├── BookingController.java                      # Task 2, 4, 5 (thay toàn bộ mỗi lần)
│           ├── BookingCreatedResponse.java                 # Task 2
│           └── StaffBookingController.java                 # Task 5
└── test/java/vn/edu/uit/flightbooking/
    ├── flight/SeatInventoryTest.java                       # Task 2 (thêm 1 test)
    └── booking/
        ├── TestBookings.java                               # Task 2
        ├── BookingConcurrencyTest.java                     # Task 3 (T1, T16)
        ├── domain/PassengerRulesTest.java                  # Task 1 (sửa lời gọi)
        ├── domain/BookingCodesTest.java                    # Task 2
        ├── domain/BookingLifecycleTest.java                # Task 4 (T5)
        └── web/
            ├── BookingQuoteTest.java                       # Task 1 (thêm 3 test)
            ├── BookingHoldTest.java                        # Task 2 (T2)
            └── BookingQueriesTest.java                     # Task 5 (T15)
frontend/src/lib/api-types.ts                               # Task 6
docs/superpowers/plans/2026-10-09-00-roadmap.md, CLAUDE.md  # Task 6
```

## API sau plan

| Method | Đường dẫn                                    | Quyền    | Thành công                       | Lỗi                      |
| ------ | -------------------------------------------- | -------- | -------------------------------- | ------------------------ |
| POST   | `/api/bookings`                              | Customer | 201 (mẫu TDD §8.8)               | như `/quote`             |
| GET    | `/api/bookings?status=&page=&size=`          | Customer | 200 `PagedModel<BookingSummary>` |                          |
| GET    | `/api/bookings/{code}`                       | Chủ      | 200 `BookingDetail`              | 404                      |
| POST   | `/api/bookings/{code}/cancel`                | Chủ      | 204                              | 404, 409 `INVALID_STATE` |
| GET    | `/api/staff/bookings?q=&status=&page=&size=` | Staff    | 200 `PagedModel<BookingSummary>` |                          |
| GET    | `/api/staff/bookings/{code}`                 | Staff    | 200 `StaffBookingDetail`         | 404                      |

`SecurityConfig` đã có `/api/bookings/**` → `CUSTOMER` và `/api/staff/**` → `STAFF`, nên không phải sửa.

---

### Task 1: Ba lỗi nhỏ của báo giá (lỗi nhỏ 1, 2, 6 của Plan 10)

**Files:**

- Modify (thay toàn bộ): `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingRequest.java`
- Modify (thay toàn bộ): `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/FieldErrors.java`
- Modify (thay toàn bộ): `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/PassengerRules.java`
- Modify (thay toàn bộ): `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/QuoteService.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/booking/web/BookingQuoteTest.java` (thêm 3 test)
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/booking/domain/PassengerRulesTest.java` (sửa lời gọi theo chữ ký mới)

**Interfaces:**

- Consumes: toàn bộ module `booking` của Plan 10; các hàm phụ có sẵn trong `BookingQuoteTest` (`quote`, `passenger`, `journey`, `baggageOption`, `bag`, `ADULT_BORN`, `INFANT_BORN`, trường `international`).
- Produces:
  - `PassengerRules.checkDetails(List<PassengerRequest>, LocalDate firstDeparture, LocalDate lastDeparture, boolean international, FieldErrors errors)`: **thêm** lỗi vào `errors`, không ném.
  - `FieldErrors.isEmpty()`.
  - `PassengerRequest`: `nationality` và `passportNumber` trống thành `null`.
  - `QuoteService.quote` trả lỗi BR-10, BR-13 và lỗi hành lý trong một lần.

- [ ] **Step 1: Viết test**

Trong `backend/src/test/java/vn/edu/uit/flightbooking/booking/web/BookingQuoteTest.java`, thêm vào cuối lớp (trước dấu `}` cuối cùng):

```java
	/** Lỗi nhỏ 1 của Plan 10: lỗi hành khách và lỗi hành lý trả về trong cùng một lần. */
	@Test
	void passengerAndBaggageErrorsComeTogether() throws Exception {
		long bag20 = baggageOption("Q1", 20, 350_000, true);

		quote(Map.of("passengers", List.of(passenger("ADULT", ADULT_BORN), passenger("CHILD", INFANT_BORN)),
				"baggage", List.of(bag(0, "RETURN", bag20))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PASSENGER_RULES_VIOLATED"))
			.andExpect(jsonPath("$.errors[*].field", contains("passengers[1].dateOfBirth", "baggage[0].direction")));
	}

	/** Lỗi nhỏ 6 của Plan 10: một dòng hành lý sai nhiều chỗ thì báo đủ mọi chỗ. */
	@Test
	void baggageLineReportsEveryProblem() throws Exception {
		long retired = baggageOption("Q1", 30, 550_000, false);

		quote(Map.of("baggage", List.of(bag(0, "RETURN", retired)))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[*].field", contains("baggage[0].direction", "baggage[0].baggageOptionId")));
	}

	/** Lỗi nhỏ 2 của Plan 10: ô hộ chiếu để trống gửi chuỗi rỗng; nội địa thì bỏ qua, quốc tế thì báo thiếu. */
	@Test
	void blankPassportFieldsCountAsMissing() throws Exception {
		Map<String, Object> blank = passenger("ADULT", ADULT_BORN);
		blank.put("nationality", "");
		blank.put("passportNumber", "  ");
		blank.put("passportExpiry", null);

		quote(Map.of("passengers", List.of(blank))).andExpect(status().isOk());
		quote(Map.of("journeys", List.of(journey("OUTBOUND", international)), "passengers", List.of(blank)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PASSENGER_RULES_VIOLATED"))
			.andExpect(jsonPath("$.errors[*].field", contains("passengers[0].nationality",
					"passengers[0].passportNumber", "passengers[0].passportExpiry")));
	}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
cd backend && ./mvnw test -Dtest=BookingQuoteTest
```

Expected: `Tests run: 14, Failures: 3`.

- `passengerAndBaggageErrorsComeTogether`: `errors` chỉ có `passengers[1].dateOfBirth`.
- `baggageLineReportsEveryProblem`: chỉ có `baggage[0].direction`.
- `blankPassportFieldsCountAsMissing`: lần gọi đầu trả 400 thay vì 200.

- [ ] **Step 3: `BookingRequest`, `FieldErrors`, `PassengerRules`**

Thay toàn bộ `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingRequest.java` bằng:

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
 * Body chung của POST /bookings/quote (FR-20) và POST /bookings (FR-24), theo ví dụ TDD §8.8. Bean Validation kiểm
 * tra định dạng (BR-12, BR-14, định dạng hộ chiếu của BR-13); quy tắc cần dữ liệu chuyến bay nằm ở
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
```

Thay toàn bộ `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/FieldErrors.java` bằng:

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

	boolean isEmpty() {
		return errors.isEmpty();
	}

	void throwIfAny(ErrorCode code, String detail) {
		if (!errors.isEmpty()) {
			throw new BusinessException(code, detail, Map.of("errors", List.copyOf(errors)));
		}
	}

}
```

Thay toàn bộ `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/PassengerRules.java` bằng:

```java
package vn.edu.uit.flightbooking.booking.domain;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

import vn.edu.uit.flightbooking.booking.domain.BookingRequest.PassengerRequest;
import vn.edu.uit.flightbooking.common.ErrorCode;

/**
 * BR-10, BR-11, BR-13 (TDD §6.2 bước 3). Định dạng tên và hộ chiếu (BR-12, BR-13) do Bean Validation ở
 * {@link BookingRequest} kiểm tra. Vi phạm báo PASSENGER_RULES_VIOLATED kèm {@code errors}.
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
	 * hộ chiếu còn hạn tới ít nhất {@code lastDeparture} (ngày cất cánh chặng cuối của booking) cộng 6 tháng. Thêm vi
	 * phạm vào {@code errors} để báo giá trả cùng lỗi hành lý trong một lần; bên gọi tự ném.
	 */
	static void checkDetails(List<PassengerRequest> passengers, LocalDate firstDeparture, LocalDate lastDeparture,
			boolean international, FieldErrors errors) {
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

Trong `backend/src/test/java/vn/edu/uit/flightbooking/booking/domain/PassengerRulesTest.java`:

1. Thay **mọi** chỗ `PassengerRules.checkDetails(` bằng `details(` (có 6 chỗ, trong 2 test).
2. Thêm hàm phụ ngay sau hàm `withPassport`:

```java
	/** Gọi {@link PassengerRules#checkDetails} rồi ném lỗi như báo giá làm khi chỉ có lỗi hành khách. */
	static void details(List<PassengerRequest> passengers, LocalDate first, LocalDate last, boolean international) {
		FieldErrors errors = new FieldErrors();
		PassengerRules.checkDetails(passengers, first, last, international, errors);
		errors.throwIfAny(ErrorCode.PASSENGER_RULES_VIOLATED, PassengerRules.DETAIL);
	}
```

- [ ] **Step 4: `QuoteService` gom lỗi một lần**

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
 * Báo giá (FR-20–FR-23, TDD §6.2 bước 1–4). Giữ chỗ ({@link BookingService#create}) gọi lại {@link #quote} trên các
 * chuyến đã khoá rồi mới giữ ghế và lượt voucher, nên lỗi nào xảy ra khi giữ chỗ cũng hiện ra khi báo giá.
 */
@Service
public class QuoteService {

	/**
	 * BR-30 điều kiện 5, cùng điều kiện với {@code uq_bookings_voucher_user}. Hai request đồng thời vẫn có thể cùng qua
	 * truy vấn này, nên giữ chỗ bắt thêm vi phạm unique index khi ghi (T16).
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
	 * điều kiện 5. Lỗi hành khách (BR-10, BR-13) và lỗi hành lý (BR-22) trả về trong cùng một lần.
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

		FieldErrors errors = new FieldErrors();
		PassengerRules.checkDetails(passengers, segments.getFirst().departureTime().toLocalDate(),
				segments.getLast().departureTime().toLocalDate(),
				segments.stream().anyMatch(PricedSegment::international), errors);
		boolean passengerRulesBroken = !errors.isEmpty();
		List<List<BaggageLine>> baggage = baggage(request.baggage(), passengers, directions, itineraries, errors);
		if (passengerRulesBroken) {
			errors.throwIfAny(ErrorCode.PASSENGER_RULES_VIOLATED, PassengerRules.DETAIL);
		}
		errors.throwIfAny(ErrorCode.VALIDATION_FAILED, "Dữ liệu không hợp lệ");

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
	 * Mỗi dòng kiểm tra độc lập ba nhóm (khách, chiều, mức hành lý) và thêm mọi lỗi vào {@code errors}. Trả các mức
	 * hợp lệ của từng chiều, cùng thứ tự với {@code directions}.
	 */
	private List<List<BaggageLine>> baggage(List<BaggageRequest> requests, List<PassengerRequest> passengers,
			List<Direction> directions, List<PricedJourney> itineraries, FieldErrors errors) {
		Map<Long, CatalogApi.BaggageRef> options = catalog
			.baggageOptions(requests.stream().map(BaggageRequest::baggageOptionId).toList());
		List<List<BaggageLine>> lines = new ArrayList<>();
		directions.forEach(d -> lines.add(new ArrayList<>()));
		Set<String> chosen = new HashSet<>();
		for (int i = 0; i < requests.size(); i++) {
			BaggageRequest b = requests.get(i);
			String field = "baggage[%d].".formatted(i);
			int journey = directions.indexOf(b.direction());
			CatalogApi.BaggageRef option = options.get(b.baggageOptionId());
			boolean valid = true;
			if (b.passengerIndex() >= passengers.size()) {
				errors.add(field + "passengerIndex", "Không có hành khách này");
				valid = false;
			}
			else if (passengers.get(b.passengerIndex()).type() == PassengerType.INFANT) {
				errors.add(field + "passengerIndex", "Em bé không được mua thêm hành lý");
				valid = false;
			}
			if (journey < 0) {
				errors.add(field + "direction", "Hành trình không có chiều này");
				valid = false;
			}
			else if (!chosen.add(b.passengerIndex() + ":" + b.direction())) {
				errors.add(field + "direction", "Mỗi hành khách chỉ chọn một mức hành lý cho mỗi chiều");
				valid = false;
			}
			if (option == null || !option.active()
					|| (journey >= 0 && !option.airlineCode().equals(itineraries.get(journey).airlineCode()))) {
				errors.add(field + "baggageOptionId", "Mức hành lý không có trong bảng giá đang bán của hãng");
				valid = false;
			}
			if (valid) {
				lines.get(journey)
					.add(new BaggageLine(b.passengerIndex(), b.baggageOptionId(), option.weightKg(), option.price()));
			}
		}
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
cd backend && ./mvnw test -Dtest='BookingQuoteTest,PassengerRulesTest,PricingTest,ModularityTest'
```

Expected: `Tests run: 21, Failures: 0, Errors: 0` (14 + 4 + 2 + 1). Mười một test cũ của `BookingQuoteTest` vẫn xanh. Riêng `baggageRulesReportEveryWrongItem` vẫn cho đúng 5 trường như cũ, vì mỗi dòng sai trong test đó chỉ sai một nhóm.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/booking backend/src/test/java/vn/edu/uit/flightbooking/booking
git commit -m "fix(booking): gom lỗi hành khách và hành lý, báo đủ lỗi mỗi dòng hành lý, ô hộ chiếu trống là không khai"
```

---

### Task 2: Giữ chỗ `POST /api/bookings` và xuất vé khi tổng bằng 0 (FR-24, BR-40, BR-43, BR-46, T2)

**Files:**

- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/flight/SeatInventory.java` (thêm `lockFlights`)
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/Quote.java` (thêm 2 trường)
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/QuoteService.java` (1 dòng tạo `Quote`)
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/BookingIssued.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingStatus.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingCodes.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingService.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/BookingCreatedResponse.java`
- Modify (thay toàn bộ): `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/BookingController.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/flight/SeatInventoryTest.java` (thêm 1 test)
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/booking/TestBookings.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/booking/domain/BookingCodesTest.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/booking/web/BookingHoldTest.java`

**Interfaces:**

- Consumes: `QuoteService.quote`, `Quote`, `Pricing.ticketPrice` (Plan 10, Task 1); `SeatInventory.hold` (Plan 06); `VoucherApi.reserve`, `VoucherRejection.ALREADY_USED` (Plan 09); `SettingKey.BOOKING_HOLD_MINUTES` (Plan 01); `QuoteResponse.of`, `QuoteResponse.JourneyTotals` (Plan 10); test: `TestFlights`, `TestUsers`, `TestVouchers`, `TestCsrf`.
- Produces:
  - `flight.SeatInventory.lockFlights(Collection<Long> flightIds)`: `SELECT … FOR SHARE` theo id tăng dần.
  - `Quote` có thêm `int childPercent, int infantPercent` (cuối danh sách thành phần).
  - `booking.BookingIssued(long bookingId)`: sự kiện công khai.
  - `booking.domain.BookingStatus` (`PENDING_PAYMENT`, `ISSUED`, `EXPIRED`, `CANCELLED`, `REFUNDED`).
  - `BookingCodes.next() → String`, `BookingCodes.ALPHABET`.
  - `BookingService.create(long userId, BookingRequest) → BookingService.Created(String code, BookingStatus status, OffsetDateTime holdExpiresAt, Quote quote)`.
  - `BookingService.issue(long bookingId)`: bên gọi đã khoá booking và kiểm tra trạng thái. Plan 12 dùng lại.
  - `BookingService.VIETNAM` (`ZoneId`, package-private).
  - `POST /api/bookings` → 201 `BookingCreatedResponse(code, status, holdExpiresAt, journeys, fareTotal, baggageTotal, discountTotal, totalAmount)`.
  - Test: `booking.TestBookings` (`trip`, `Trip.journey/oneWay/roundTrip`, `passenger`, `body`, `hold`, `holdCode`, `baggageOption`, `bag`, `bookingId`, `available`, `ADULT_BORN`, `CHILD_BORN`, `INFANT_BORN`).

- [ ] **Step 1: Viết test**

Trong `backend/src/test/java/vn/edu/uit/flightbooking/flight/SeatInventoryTest.java`, thêm vào nhóm import:

```java
import java.util.List;
import java.util.concurrent.Future;

import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
```

thêm trường ngay sau trường `jdbc`:

```java
	@Autowired
	PlatformTransactionManager transactions;
```

rồi thêm vào cuối lớp (trước dấu `}` cuối cùng):

```java
	/** TDD §5.4: khoá chia sẻ chặn khoá ghi (huỷ chuyến, Plan 16) tới khi transaction giữ chỗ kết thúc. */
	@Test
	void lockedFlightsCannotBeLockedForUpdate() throws Exception {
		TransactionTemplate tx = new TransactionTemplate(transactions);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(1);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
			seats.lockFlights(List.of(flightId));
			locked.countDown();
			try {
				done.await(10, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}));
		try {
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> tx.executeWithoutResult(status -> jdbc
				.sql("SELECT id FROM flights WHERE id = ? FOR UPDATE NOWAIT")
				.param(flightId)
				.query(Long.class)
				.list())).isInstanceOf(DataAccessException.class);
			// Khoá chia sẻ không chặn nhau: một lượt giữ chỗ khác vẫn khoá được.
			tx.executeWithoutResult(status -> seats.lockFlights(List.of(flightId)));
		}
		finally {
			done.countDown();
			holder.get(10, TimeUnit.SECONDS);
			pool.shutdown();
		}
	}
```

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/booking/TestBookings.java`:

```java
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
```

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/booking/domain/BookingCodesTest.java`:

```java
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
```

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/booking/web/BookingHoldTest.java`:

```java
package vn.edu.uit.flightbooking.booking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.booking.TestBookings.ADULT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.CHILD_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.INFANT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.passenger;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.booking.BookingIssued;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.promotion.TestVouchers;

/** FR-24, BR-24, BR-40, BR-43, BR-46, T2 qua POST /api/bookings. */
@IntegrationTest
@Transactional
@RecordApplicationEvents
class BookingHoldTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
	}

	ResultActions hold(Map<String, Object> changes) throws Exception {
		return TestBookings.hold(mvc, customer.session(), TestBookings.body(trip, changes));
	}

	int available(long flightId) {
		return TestBookings.available(jdbc, flightId);
	}

	long bookingCount() {
		return jdbc.sql("SELECT count(*) FROM bookings WHERE user_id = ?").param(customer.id()).query(Long.class).single();
	}

	/** Ví dụ giữ chỗ TDD §8.8: phản hồi đúng mẫu, mọi bảng được ghi, giá được chốt (BR-24), ghế và lượt bị trừ. */
	@Test
	void holdsTddExampleAndSnapshotsPrices() throws Exception {
		long bag20 = TestBookings.baggageOption(jdbc, "Q1", 20, 350_000);
		long welcome = TestVouchers.insert(jdbc, "QVWELCOME", Map.of("max_discount", 200_000));
		Instant before = Instant.now();

		String json = hold(Map.of("journeys", trip.roundTrip(), "passengers",
				List.of(passenger("ADULT", ADULT_BORN), passenger("INFANT", INFANT_BORN)), "baggage",
				List.of(TestBookings.bag(0, "OUTBOUND", bag20)), "voucherCode", "QVWELCOME"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.journeys[*].direction", contains("OUTBOUND", "RETURN")))
			.andExpect(jsonPath("$.journeys[*].fareTotal", contains(3_322_000, 2_376_000)))
			.andExpect(jsonPath("$.journeys[*].baggageTotal", contains(350_000, 0)))
			.andExpect(jsonPath("$.journeys[*].discount", contains(121_428, 78_572)))
			.andExpect(jsonPath("$.fareTotal").value(5_698_000))
			.andExpect(jsonPath("$.discountTotal").value(200_000))
			.andExpect(jsonPath("$.totalAmount").value(5_848_000))
			.andReturn()
			.getResponse()
			.getContentAsString();

		String code = JsonPath.read(json, "$.code");
		assertThat(code).matches("[A-HJ-NP-Z2-9]{6}");
		OffsetDateTime holdExpiresAt = OffsetDateTime.parse(JsonPath.read(json, "$.holdExpiresAt"));
		assertThat(holdExpiresAt.getOffset()).isEqualTo(ZoneOffset.ofHours(7));
		assertThat(holdExpiresAt.toInstant()).isBetween(before.plusSeconds(15 * 60 - 1),
				Instant.now().plusSeconds(15 * 60 + 1));

		long id = TestBookings.bookingId(jdbc, code);
		assertThat(jdbc.sql("""
				SELECT status, user_id, voucher_id, fare_total, baggage_total, discount_total, total_amount, contact_email
				FROM bookings WHERE id = ?
				""").param(id).query().singleRow())
			.containsEntry("status", "PENDING_PAYMENT")
			.containsEntry("user_id", customer.id())
			.containsEntry("voucher_id", welcome)
			.containsEntry("fare_total", 5_698_000L)
			.containsEntry("baggage_total", 350_000L)
			.containsEntry("discount_total", 200_000L)
			.containsEntry("total_amount", 5_848_000L)
			.containsEntry("contact_email", "an@example.com");
		assertThat(jdbc.sql("""
				SELECT direction || ':' || fare_family_name || ':' || cabin_class || ':' || discount_amount
				FROM booking_journeys WHERE booking_id = ? ORDER BY direction
				""").param(id).query(String.class).list())
			.containsExactly("OUTBOUND:Eco:ECONOMY:121428", "RETURN:Eco:ECONOMY:78572");
		assertThat(jdbc.sql("""
				SELECT s.adult_price FROM booking_segments s JOIN booking_journeys j ON j.id = s.journey_id
				WHERE j.booking_id = ? AND s.status = 'ACTIVE' ORDER BY j.direction, s.seq
				""").param(id).query(Long.class).list()).containsExactly(1_870_000L, 1_150_000L, 2_160_000L);
		assertThat(ticketFares(id, "ADULT")).containsExactly(1_870_000L, 1_150_000L, 2_160_000L);
		assertThat(ticketFares(id, "INFANT")).containsExactly(187_000L, 115_000L, 216_000L);
		assertThat(jdbc.sql("""
				SELECT bb.weight_kg || ':' || bb.price || ':' || p.type FROM booking_baggage bb
				JOIN passengers p ON p.id = bb.passenger_id WHERE p.booking_id = ?
				""").param(id).query(String.class).list()).containsExactly("20:350000:ADULT");

		// Em bé không chiếm ghế: mỗi chặng trừ đúng 1 ghế.
		assertThat(available(trip.firstLeg())).isEqualTo(99);
		assertThat(available(trip.secondLeg())).isEqualTo(99);
		assertThat(available(trip.back())).isEqualTo(99);
		assertThat(TestVouchers.usedCount(jdbc, welcome)).isEqualTo(1);
		assertThat(events.stream(BookingIssued.class)).isEmpty();
	}

	List<Long> ticketFares(long bookingId, String type) {
		return jdbc.sql("""
				SELECT t.fare_amount FROM tickets t
				JOIN passengers p ON p.id = t.passenger_id
				JOIN booking_segments s ON s.id = t.segment_id
				WHERE p.booking_id = ? AND p.type = ?
				ORDER BY s.id
				""").params(bookingId, type).query(Long.class).list();
	}

	/** T2: khứ hồi nối chuyến, chặng cuối hết ghế: không chặng nào bị trừ ghế, không giữ lượt, không có booking. */
	@Test
	void failedHoldKeepsEverySeatAndVoucherUse() throws Exception {
		long voucher = TestVouchers.insert(jdbc, "QVKEEP", Map.of());
		jdbc.sql("UPDATE flight_cabins SET available_seats = 0 WHERE flight_id = ? AND cabin_class = 'ECONOMY'")
			.param(trip.back())
			.update();

		hold(Map.of("journeys", trip.roundTrip(), "voucherCode", "QVKEEP")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SEATS_UNAVAILABLE"));

		assertThat(available(trip.firstLeg())).isEqualTo(100);
		assertThat(available(trip.secondLeg())).isEqualTo(100);
		assertThat(TestVouchers.usedCount(jdbc, voucher)).isZero();
		assertThat(bookingCount()).isZero();
	}

	/** Giữ chỗ kiểm tra giống hệt báo giá (FR-20); dữ liệu sai thì không ghi gì. */
	@Test
	void rejectedHoldWritesNothing() throws Exception {
		hold(Map.of("passengers", List.of(passenger("CHILD", CHILD_BORN)))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PASSENGER_RULES_VIOLATED"));

		assertThat(bookingCount()).isZero();
		assertThat(available(trip.firstLeg())).isEqualTo(100);
	}

	/** BR-40, BR-43: tổng thanh toán 0 thì xuất vé ngay, số vé 13 chữ số bắt đầu bằng mã hãng, phát BookingIssued. */
	@Test
	void zeroTotalIsIssuedAtOnce() throws Exception {
		TestVouchers.insert(jdbc, "QVFREE", Map.of("discount_value", 100));

		String json = hold(Map.of("voucherCode", "QVFREE")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("ISSUED"))
			.andExpect(jsonPath("$.totalAmount").value(0))
			.andReturn()
			.getResponse()
			.getContentAsString();

		long id = TestBookings.bookingId(jdbc, JsonPath.read(json, "$.code"));
		assertThat(jdbc.sql("""
				SELECT t.ticket_number FROM tickets t JOIN passengers p ON p.id = t.passenger_id
				WHERE p.booking_id = ? AND t.issued_at IS NOT NULL
				""").param(id).query(String.class).list())
			.hasSize(2)
			.doesNotHaveDuplicates()
			.allMatch(number -> number.matches("901[0-9]{10}"));
		assertThat(jdbc.sql("SELECT status = 'ISSUED' AND issued_at IS NOT NULL FROM bookings WHERE id = ?")
			.param(id)
			.query(Boolean.class)
			.single()).isTrue();
		assertThat(events.stream(BookingIssued.class).map(BookingIssued::bookingId)).containsExactly(id);
		assertThat(available(trip.firstLeg())).isEqualTo(99);
	}

}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
cd backend && ./mvnw test -Dtest='BookingCodesTest,BookingHoldTest,SeatInventoryTest'
```

Expected: `COMPILATION ERROR`, không tìm thấy `SeatInventory.lockFlights`, `BookingCodes`, `BookingIssued`.

- [ ] **Step 3: `SeatInventory.lockFlights`, `Quote` mang tỉ lệ giá (lỗi nhỏ 3 của Plan 10)**

Trong `backend/src/main/java/vn/edu/uit/flightbooking/flight/SeatInventory.java`:

1. Thêm `import java.util.Collection;` lên đầu nhóm import.
2. Thêm hàm sau ngay trước hàm `hold`:

```java
	/**
	 * TDD §5.4: khoá chia sẻ các chuyến theo id tăng dần trước khi giữ ghế. Nhiều lượt giữ chỗ khoá cùng lúc được,
	 * còn huỷ chuyến (khoá FOR UPDATE, Plan 16) phải chờ các lượt đó xong.
	 */
	@Transactional
	public void lockFlights(Collection<Long> flightIds) {
		jdbc.sql("SELECT id FROM flights WHERE id IN (:ids) ORDER BY id FOR SHARE")
			.param("ids", flightIds)
			.query(Long.class)
			.list();
	}
```

3. Sửa câu cuối trong Javadoc của lớp: đổi `và tự khoá chuyến {@code FOR SHARE} để chặn huỷ chuyến chen vào (Plan 11).` thành `và khoá chuyến bằng {@link #lockFlights} trước khi giữ ghế.`

Trong `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/Quote.java`, thay:

```java
/**
 * Kết quả báo giá (FR-20, TDD §6.2 bước 1–4). Giữ chỗ (Plan 11) dùng lại để ghi booking và chốt giá (BR-24).
 * {@code voucherId} là {@code null} khi không dùng voucher. {@code totalAmount = fareTotal + baggageTotal −
 * discountTotal} (BR-23).
 */
public record Quote(List<JourneyQuote> journeys, Long voucherId, long fareTotal, long baggageTotal,
		long discountTotal, long totalAmount) {
```

bằng:

```java
/**
 * Kết quả báo giá (FR-20, TDD §6.2 bước 1–4). Giữ chỗ dùng lại để ghi booking và chốt giá (BR-24).
 * {@code voucherId} là {@code null} khi không dùng voucher. {@code totalAmount = fareTotal + baggageTotal −
 * discountTotal} (BR-23). {@code childPercent}, {@code infantPercent}: tỉ lệ đã dùng để tính giá (BR-21), để giữ chỗ
 * tính giá từng vé ({@code tickets.fare_amount}) bằng đúng hai số đó.
 */
public record Quote(List<JourneyQuote> journeys, Long voucherId, long fareTotal, long baggageTotal,
		long discountTotal, long totalAmount, int childPercent, int infantPercent) {
```

Trong `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/QuoteService.java`, thay:

```java
				discount, fareTotal + baggageTotal - discount);
```

bằng:

```java
				discount, fareTotal + baggageTotal - discount, childPercent, infantPercent);
```

- [ ] **Step 4: Sự kiện, trạng thái, mã đặt chỗ**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/BookingIssued.java`:

```java
package vn.edu.uit.flightbooking.booking;

/**
 * Booking vừa được xuất vé (TDD §3.3): khi giữ chỗ với tổng tiền 0 (BR-40) hoặc khi thanh toán thành công (Plan 12).
 * Module notification gửi email vé sau khi commit (Plan 12).
 */
public record BookingIssued(long bookingId) {
}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingStatus.java`:

```java
package vn.edu.uit.flightbooking.booking.domain;

/** Cột {@code bookings.status} (APP_FLOW §4.1). */
public enum BookingStatus {

	PENDING_PAYMENT,

	ISSUED,

	EXPIRED,

	CANCELLED,

	REFUNDED

}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingCodes.java`:

```java
package vn.edu.uit.flightbooking.booking.domain;

import java.security.SecureRandom;

/** BR-46: mã đặt chỗ 6 ký tự, bỏ các ký tự dễ nhầm 0, O, 1, I (khớp CHECK của {@code bookings.code}). */
final class BookingCodes {

	static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private static final SecureRandom RANDOM = new SecureRandom();

	private BookingCodes() {
	}

	static String next() {
		char[] code = new char[6];
		for (int i = 0; i < code.length; i++) {
			code[i] = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
		}
		return new String(code);
	}

}
```

- [ ] **Step 5: `BookingService`**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingService.java`:

```java
package vn.edu.uit.flightbooking.booking.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.booking.BookingIssued;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest.ContactRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest.PassengerRequest;
import vn.edu.uit.flightbooking.booking.domain.Quote.BaggageLine;
import vn.edu.uit.flightbooking.booking.domain.Quote.JourneyQuote;
import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.common.SettingKey;
import vn.edu.uit.flightbooking.common.SettingsApi;
import vn.edu.uit.flightbooking.flight.FlightApi.FareSnapshot;
import vn.edu.uit.flightbooking.flight.FlightApi.PricedSegment;
import vn.edu.uit.flightbooking.flight.SeatInventory;
import vn.edu.uit.flightbooking.promotion.VoucherApi;
import vn.edu.uit.flightbooking.promotion.VoucherRejection;

/**
 * Giữ chỗ (FR-24, TDD §6.2 bước 5) và xuất vé (TDD §6.4). Ghi bằng JdbcClient như module flight: mọi chuyển trạng
 * thái đều khoá dòng rồi {@code UPDATE} có điều kiện, nên không cần entity JPA.
 */
@Service
public class BookingService {

	/** Giờ của booking (hạn giữ chỗ, thời điểm tạo, xuất vé) trả theo giờ Việt Nam: booking không gắn với một sân bay. */
	static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

	/** TDD §6.2 bước 5.4: sinh lại mã khi trùng, tối đa 5 lần. */
	private static final int CODE_ATTEMPTS = 5;

	public record Created(String code, BookingStatus status, OffsetDateTime holdExpiresAt, Quote quote) {
	}

	private record Hold(long flightId, CabinClass cabin) {
	}

	/** Mã trùng thì không thêm dòng nào (sinh mã khác); vi phạm {@code uq_bookings_voucher_user} vẫn báo lỗi. */
	private static final String INSERT_BOOKING = """
			INSERT INTO bookings (code, user_id, status, contact_name, contact_email, contact_phone, voucher_id,
			                      fare_total, baggage_total, discount_total, total_amount, hold_expires_at)
			VALUES (:code, :userId, 'PENDING_PAYMENT', :name, :email, :phone, :voucherId,
			        :fareTotal, :baggageTotal, :discountTotal, :totalAmount, :holdExpiresAt)
			ON CONFLICT (code) DO NOTHING
			RETURNING id
			""";

	private static final String INSERT_JOURNEY = """
			INSERT INTO booking_journeys (booking_id, direction, airline_code, fare_family_id, fare_family_name,
			                              cabin_class, carry_on_kg, checked_baggage_kg, refundable, refund_fee,
			                              changeable, change_fee, discount_amount)
			VALUES (:bookingId, :direction, :airlineCode, :fareFamilyId, :name, :cabin, :carryOnKg, :checkedBaggageKg,
			        :refundable, :refundFee, :changeable, :changeFee, :discount)
			RETURNING id
			""";

	private static final String INSERT_SEGMENT = """
			INSERT INTO booking_segments (journey_id, flight_id, seq, status, adult_price)
			VALUES (:journeyId, :flightId, :seq, 'ACTIVE', :adultPrice)
			RETURNING id
			""";

	private static final String INSERT_PASSENGER = """
			INSERT INTO passengers (booking_id, type, last_name, first_name, gender, date_of_birth, nationality,
			                        passport_number, passport_expiry)
			VALUES (:bookingId, :type, :lastName, :firstName, :gender, :dateOfBirth, :nationality, :passportNumber,
			        :passportExpiry)
			RETURNING id
			""";

	private static final String INSERT_TICKET = """
			INSERT INTO tickets (segment_id, passenger_id, fare_amount) VALUES (:segmentId, :passengerId, :fareAmount)
			""";

	private static final String INSERT_BAGGAGE = """
			INSERT INTO booking_baggage (journey_id, passenger_id, baggage_option_id, weight_kg, price)
			VALUES (:journeyId, :passengerId, :optionId, :weightKg, :price)
			""";

	/** Q-13 cho mọi vé chưa có số của các chặng ACTIVE; {@code nextval} chạy riêng cho từng dòng. */
	private static final String ISSUE_TICKETS = """
			UPDATE tickets t
			SET ticket_number = a.ticket_prefix || lpad(nextval('ticket_number_seq')::text, 10, '0'),
			    issued_at = now()
			FROM booking_segments s
			JOIN booking_journeys j ON j.id = s.journey_id
			JOIN airlines a ON a.code = j.airline_code
			WHERE t.segment_id = s.id AND j.booking_id = :bookingId AND s.status = 'ACTIVE'
			  AND t.ticket_number IS NULL
			""";

	private final QuoteService quotes;

	private final SeatInventory seats;

	private final VoucherApi vouchers;

	private final SettingsApi settings;

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	BookingService(QuoteService quotes, SeatInventory seats, VoucherApi vouchers, SettingsApi settings,
			JdbcClient jdbc, ApplicationEventPublisher events) {
		this.quotes = quotes;
		this.seats = seats;
		this.vouchers = vouchers;
		this.settings = settings;
		this.jdbc = jdbc;
		this.events = events;
	}

	/**
	 * FR-24. Thứ tự khoá TDD §5.4: khoá chia sẻ các chuyến, báo giá lại trên dữ liệu đã khoá (mọi lỗi của FR-20), giữ
	 * ghế theo {@code flightId} tăng dần (Q-01), giữ lượt voucher (Q-14), rồi ghi booking. Lỗi ở bất kỳ bước nào làm
	 * rollback toàn bộ. Tổng thanh toán 0 thì xuất vé ngay (BR-40).
	 */
	@Transactional
	public Created create(long userId, BookingRequest request) {
		List<Long> flightIds = request.journeys()
			.stream()
			.flatMap(j -> j.flightIds().stream())
			.distinct()
			.sorted()
			.toList();
		if (!flightIds.isEmpty()) {
			seats.lockFlights(flightIds);
		}
		Quote quote = quotes.quote(userId, request);
		List<PassengerRequest> passengers = request.passengers();
		int seated = (int) passengers.stream().filter(p -> p.type() != PassengerType.INFANT).count();
		quote.journeys()
			.stream()
			.flatMap(j -> j.itinerary()
				.segments()
				.stream()
				.map(s -> new Hold(s.flightId(), j.itinerary().fare().cabinClass())))
			.sorted(Comparator.comparingLong(Hold::flightId))
			.forEach(h -> seats.hold(h.flightId(), h.cabin(), seated));
		if (quote.voucherId() != null) {
			vouchers.reserve(quote.voucherId());
		}

		Instant holdExpiresAt = Instant.now()
			.plus(settings.getInt(SettingKey.BOOKING_HOLD_MINUTES), ChronoUnit.MINUTES);
		String code = null;
		Long bookingId = null;
		for (int attempt = 0; attempt < CODE_ATTEMPTS && bookingId == null; attempt++) {
			code = BookingCodes.next();
			bookingId = insertBooking(code, userId, request.contact(), quote, holdExpiresAt).orElse(null);
		}
		if (bookingId == null) {
			throw new IllegalStateException("Không sinh được mã đặt chỗ sau %d lần thử".formatted(CODE_ATTEMPTS));
		}

		List<Long> passengerIds = new ArrayList<>();
		for (PassengerRequest p : passengers) {
			passengerIds.add(insertPassenger(bookingId, p));
		}
		for (JourneyQuote journey : quote.journeys()) {
			long journeyId = insertJourney(bookingId, journey);
			List<PricedSegment> segments = journey.itinerary().segments();
			for (int seq = 1; seq <= segments.size(); seq++) {
				PricedSegment segment = segments.get(seq - 1);
				long segmentId = insertSegment(journeyId, segment, seq);
				for (int i = 0; i < passengers.size(); i++) {
					long fare = Pricing.ticketPrice(segment.adultPrice(), passengers.get(i).type(),
							quote.childPercent(), quote.infantPercent());
					jdbc.sql(INSERT_TICKET)
						.param("segmentId", segmentId)
						.param("passengerId", passengerIds.get(i))
						.param("fareAmount", fare)
						.update();
				}
			}
			for (BaggageLine line : journey.baggage()) {
				jdbc.sql(INSERT_BAGGAGE)
					.param("journeyId", journeyId)
					.param("passengerId", passengerIds.get(line.passengerIndex()))
					.param("optionId", line.baggageOptionId())
					.param("weightKg", line.weightKg())
					.param("price", line.price())
					.update();
			}
		}

		BookingStatus status = BookingStatus.PENDING_PAYMENT;
		if (quote.totalAmount() == 0) {
			issue(bookingId);
			status = BookingStatus.ISSUED;
		}
		return new Created(code, status, holdExpiresAt.atZone(VIETNAM).toOffsetDateTime(), quote);
	}

	/**
	 * TDD §6.4, BR-43: cấp số vé cho mọi vé của chặng ACTIVE (Q-13), chuyển booking sang ISSUED và phát
	 * {@link BookingIssued}. Bên gọi đã khoá booking và kiểm tra trạng thái: giữ chỗ với tổng tiền 0 (BR-40), hoặc
	 * thanh toán thành công (Plan 12).
	 */
	@Transactional
	public void issue(long bookingId) {
		jdbc.sql(ISSUE_TICKETS).param("bookingId", bookingId).update();
		jdbc.sql("UPDATE bookings SET status = 'ISSUED', issued_at = now(), updated_at = now() WHERE id = ?")
			.param(bookingId)
			.update();
		events.publishEvent(new BookingIssued(bookingId));
	}

	/**
	 * Rỗng khi mã đã có người dùng. Vi phạm unique còn lại chỉ có thể là {@code uq_bookings_voucher_user} (BR-30 điều
	 * kiện 5): request khác của cùng tài khoản vừa giữ chỗ bằng voucher này (T16).
	 */
	private Optional<Long> insertBooking(String code, long userId, ContactRequest contact, Quote quote,
			Instant holdExpiresAt) {
		try {
			return jdbc.sql(INSERT_BOOKING)
				.param("code", code)
				.param("userId", userId)
				.param("name", contact.name())
				.param("email", contact.email())
				.param("phone", contact.phone())
				.param("voucherId", quote.voucherId())
				.param("fareTotal", quote.fareTotal())
				.param("baggageTotal", quote.baggageTotal())
				.param("discountTotal", quote.discountTotal())
				.param("totalAmount", quote.totalAmount())
				.param("holdExpiresAt", holdExpiresAt.atOffset(ZoneOffset.UTC))
				.query(Long.class)
				.optional();
		}
		catch (DuplicateKeyException e) {
			throw VoucherRejection.ALREADY_USED.toException();
		}
	}

	private long insertPassenger(long bookingId, PassengerRequest p) {
		return jdbc.sql(INSERT_PASSENGER)
			.param("bookingId", bookingId)
			.param("type", p.type().name())
			.param("lastName", p.lastName())
			.param("firstName", p.firstName())
			.param("gender", p.gender().name())
			.param("dateOfBirth", p.dateOfBirth())
			.param("nationality", p.nationality())
			.param("passportNumber", p.passportNumber())
			.param("passportExpiry", p.passportExpiry())
			.query(Long.class)
			.single();
	}

	/** Chốt quy định gói giá và mức giảm của chiều (BR-24, BR-33). */
	private long insertJourney(long bookingId, JourneyQuote journey) {
		FareSnapshot fare = journey.itinerary().fare();
		return jdbc.sql(INSERT_JOURNEY)
			.param("bookingId", bookingId)
			.param("direction", journey.direction().name())
			.param("airlineCode", journey.itinerary().airlineCode())
			.param("fareFamilyId", fare.fareFamilyId())
			.param("name", fare.name())
			.param("cabin", fare.cabinClass().name())
			.param("carryOnKg", fare.carryOnKg())
			.param("checkedBaggageKg", fare.checkedBaggageKg())
			.param("refundable", fare.refundable())
			.param("refundFee", fare.refundFee())
			.param("changeable", fare.changeable())
			.param("changeFee", fare.changeFee())
			.param("discount", journey.discount())
			.query(Long.class)
			.single();
	}

	/** Chốt giá người lớn của chặng (BR-24). */
	private long insertSegment(long journeyId, PricedSegment segment, int seq) {
		return jdbc.sql(INSERT_SEGMENT)
			.param("journeyId", journeyId)
			.param("flightId", segment.flightId())
			.param("seq", seq)
			.param("adultPrice", segment.adultPrice())
			.query(Long.class)
			.single();
	}

}
```

- [ ] **Step 6: Phản hồi và controller**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/BookingCreatedResponse.java`:

```java
package vn.edu.uit.flightbooking.booking.web;

import java.time.OffsetDateTime;
import java.util.List;

import vn.edu.uit.flightbooking.booking.domain.BookingService;
import vn.edu.uit.flightbooking.booking.domain.BookingStatus;

/** Phản hồi 201 của POST /bookings, đúng ví dụ TDD §8.8: phần bảng giá giống hệt POST /bookings/quote. */
public record BookingCreatedResponse(String code, BookingStatus status, OffsetDateTime holdExpiresAt,
		List<QuoteResponse.JourneyTotals> journeys, long fareTotal, long baggageTotal, long discountTotal,
		long totalAmount) {

	static BookingCreatedResponse of(BookingService.Created created) {
		QuoteResponse totals = QuoteResponse.of(created.quote());
		return new BookingCreatedResponse(created.code(), created.status(), created.holdExpiresAt(), totals.journeys(),
				totals.fareTotal(), totals.baggageTotal(), totals.discountTotal(), totals.totalAmount());
	}

}
```

Thay toàn bộ `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/BookingController.java` bằng:

```java
package vn.edu.uit.flightbooking.booking.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.booking.domain.BookingRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingService;
import vn.edu.uit.flightbooking.booking.domain.QuoteService;
import vn.edu.uit.flightbooking.common.CurrentUser;

/** Màn hình C-07: báo giá (FR-20) và giữ chỗ (FR-24). Quyền CUSTOMER theo đường dẫn ở SecurityConfig. */
@RestController
@RequestMapping("/api/bookings")
class BookingController {

	private final QuoteService quotes;

	private final BookingService bookings;

	BookingController(QuoteService quotes, BookingService bookings) {
		this.quotes = quotes;
		this.bookings = bookings;
	}

	@PostMapping("/quote")
	QuoteResponse quote(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody BookingRequest body) {
		return QuoteResponse.of(quotes.quote(me.id(), body));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	BookingCreatedResponse create(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody BookingRequest body) {
		return BookingCreatedResponse.of(bookings.create(me.id(), body));
	}

}
```

- [ ] **Step 7: Chạy test, xác nhận xanh**

```bash
cd backend && ./mvnw test -Dtest='BookingCodesTest,BookingHoldTest,SeatInventoryTest,BookingQuoteTest,ModularityTest'
```

Expected: `BUILD SUCCESS`, 0 failures:

- `BookingCodesTest`: 1 test.
- `BookingHoldTest`: 4 test.
- `SeatInventoryTest`: thêm 1 test mới.
- `BookingQuoteTest`: 14 test.
- `ModularityTest`: 1 test.

Nếu `holdsTddExampleAndSnapshotsPrices` báo `voucher_id` có kiểu khác `Long`, kiểm tra lại kiểu cột `BIGINT` ở `V1__init.sql` trước khi sửa test.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/flight/SeatInventory.java backend/src/main/java/vn/edu/uit/flightbooking/booking backend/src/test/java/vn/edu/uit/flightbooking/flight/SeatInventoryTest.java backend/src/test/java/vn/edu/uit/flightbooking/booking
git commit -m "feat(booking): giữ chỗ POST /bookings, chốt giá và xuất vé ngay khi tổng bằng 0 (FR-24, BR-40, BR-46)"
```

---

### Task 3: Giữ chỗ đồng thời (T1, T16)

**Files:**

- Test: `backend/src/test/java/vn/edu/uit/flightbooking/booking/BookingConcurrencyTest.java`

**Interfaces:**

- Consumes: `BookingService.create`, `BookingRequest` và các record lồng, `PassengerType`, `Gender`, `Direction` (Task 2, Plan 10); `TestUsers.create`, `TestUsers.randomEmail`; `TestVouchers.insert`, `TestVouchers.usedCount`.
- Produces: không có. Task này chỉ thêm test, nên **không có bước đỏ**: code đã có từ Task 2. Nếu test đỏ thì đó là lỗi thật của Task 2, phải sửa ở `BookingService` chứ không sửa test.

- [ ] **Step 1: Viết test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/booking/BookingConcurrencyTest.java`:

```java
package vn.edu.uit.flightbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest.ContactRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest.JourneyRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest.PassengerRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingService;
import vn.edu.uit.flightbooking.booking.domain.Direction;
import vn.edu.uit.flightbooking.booking.domain.Gender;
import vn.edu.uit.flightbooking.booking.domain.PassengerType;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.promotion.TestVouchers;

/**
 * T1, T16. Không dùng {@code @Transactional}: mỗi luồng cần transaction và connection riêng, nên dữ liệu được commit
 * thật. Hãng Q8, sân bay QYA, QYB, voucher QVRACE11 chỉ dùng trong test này và được xoá ở {@link #cleanUp()}.
 */
@IntegrationTest
class BookingConcurrencyTest {

	@Autowired
	BookingService bookings;

	@Autowired
	JdbcClient jdbc;

	long userId;

	long fareFamilyId;

	long flightId;

	/** Chuyến Q8100 QYA→QYB sau 10 ngày, ECONOMY còn 5 ghế, gói Eco 1.000.000. */
	@BeforeEach
	void setUp() {
		jdbc.sql("INSERT INTO airlines (code, name, ticket_prefix) VALUES ('Q8', 'Hãng Đua', '908')").update();
		jdbc.sql("""
				INSERT INTO airports (code, name, city, country_code, timezone) VALUES
				    ('QYA', 'Đi', 'Hà Nội', 'VN', 'Asia/Ho_Chi_Minh'),
				    ('QYB', 'Đến', 'Đà Nẵng', 'VN', 'Asia/Ho_Chi_Minh')
				""").update();
		fareFamilyId = jdbc.sql("""
				INSERT INTO fare_families (airline_code, cabin_class, name, carry_on_kg, checked_baggage_kg,
				                           refundable, changeable)
				VALUES ('Q8', 'ECONOMY', 'Eco', 7, 0, FALSE, TRUE)
				RETURNING id
				""").query(Long.class).single();
		flightId = jdbc.sql("""
				INSERT INTO flights (airline_code, flight_number, departure_airport, arrival_airport,
				                     departure_time, arrival_time)
				VALUES ('Q8', 'Q8100', 'QYA', 'QYB', now() + interval '10 days', now() + interval '10 days 1 hour')
				RETURNING id
				""").query(Long.class).single();
		jdbc.sql("""
				INSERT INTO flight_cabins (flight_id, cabin_class, total_seats, available_seats)
				VALUES (?, 'ECONOMY', 5, 5)
				""").param(flightId).update();
		jdbc.sql("INSERT INTO flight_fares (flight_id, fare_family_id, price) VALUES (?, ?, 1000000)")
			.params(flightId, fareFamilyId)
			.update();
		userId = TestUsers.create(jdbc, Role.CUSTOMER, TestUsers.randomEmail());
	}

	/** Xoá theo thứ tự khoá ngoại: vé, chặng, chiều, hành khách, booking, rồi tới danh mục. */
	@AfterEach
	void cleanUp() {
		jdbc.sql("""
				DELETE FROM tickets WHERE passenger_id IN
				    (SELECT p.id FROM passengers p JOIN bookings b ON b.id = p.booking_id WHERE b.user_id = ?)
				""").param(userId).update();
		jdbc.sql("""
				DELETE FROM booking_segments WHERE journey_id IN
				    (SELECT j.id FROM booking_journeys j JOIN bookings b ON b.id = j.booking_id WHERE b.user_id = ?)
				""").param(userId).update();
		jdbc.sql("DELETE FROM booking_journeys WHERE booking_id IN (SELECT id FROM bookings WHERE user_id = ?)")
			.param(userId)
			.update();
		jdbc.sql("DELETE FROM passengers WHERE booking_id IN (SELECT id FROM bookings WHERE user_id = ?)")
			.param(userId)
			.update();
		jdbc.sql("DELETE FROM bookings WHERE user_id = ?").param(userId).update();
		jdbc.sql("DELETE FROM users WHERE id = ?").param(userId).update();
		jdbc.sql("DELETE FROM vouchers WHERE code = 'QVRACE11'").update();
		jdbc.sql("DELETE FROM flights WHERE airline_code = 'Q8'").update();
		jdbc.sql("DELETE FROM fare_families WHERE airline_code = 'Q8'").update();
		jdbc.sql("DELETE FROM airports WHERE code IN ('QYA', 'QYB')").update();
		jdbc.sql("DELETE FROM airlines WHERE code = 'Q8'").update();
	}

	BookingRequest request(String voucherCode) {
		return new BookingRequest(List.of(new JourneyRequest(Direction.OUTBOUND, List.of(flightId), fareFamilyId)),
				List.of(new PassengerRequest(PassengerType.ADULT, "NGUYEN", "VAN AN", Gender.MALE,
						LocalDate.of(1990, 5, 20), null, null, null)),
				List.of(), new ContactRequest("NGUYEN VAN AN", "an@example.com", "0901234567"), voucherCode);
	}

	int available() {
		return jdbc.sql("SELECT available_seats FROM flight_cabins WHERE flight_id = ?")
			.param(flightId)
			.query(Integer.class)
			.single();
	}

	long bookingCount() {
		return jdbc.sql("SELECT count(*) FROM bookings WHERE user_id = ?").param(userId).query(Long.class).single();
	}

	/**
	 * Chạy {@code task} trên {@code threads} luồng cùng lúc. Kết quả mỗi luồng là {@code "OK"}, mã lỗi (VD
	 * {@code "SEATS_UNAVAILABLE"}), hoặc mã lỗi kèm lý do (VD {@code "VOUCHER_INVALID:ALREADY_USED"}).
	 */
	List<String> race(int threads, Runnable task) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<String>> futures = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			futures.add(pool.submit(() -> {
				start.await();
				try {
					task.run();
					return "OK";
				}
				catch (BusinessException e) {
					Object reason = e.properties().get("reason");
					return reason == null ? e.code().name() : e.code().name() + ":" + reason;
				}
			}));
		}
		start.countDown();
		pool.shutdown();
		assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
		List<String> results = new ArrayList<>();
		for (Future<String> future : futures) {
			results.add(future.get());
		}
		return results;
	}

	/** T1: 20 luồng cùng giữ chỗ trên chuyến còn 5 ghế, mỗi luồng 1 khách. */
	@Test
	void twentyConcurrentHoldsSellExactlyFiveSeats() throws Exception {
		List<String> results = race(20, () -> bookings.create(userId, request(null)));

		assertThat(results).filteredOn(r -> r.equals("OK")).hasSize(5);
		assertThat(results).filteredOn(r -> r.equals("SEATS_UNAVAILABLE")).hasSize(15);
		assertThat(available()).isZero();
		assertThat(bookingCount()).isEqualTo(5);
	}

	/** T16: một tài khoản dùng cùng một voucher cho nhiều booking cùng lúc thì chỉ một booking thành công. */
	@Test
	void sameVoucherAtOnceKeepsOneBooking() throws Exception {
		long voucher = TestVouchers.insert(jdbc, "QVRACE11", Map.of());

		List<String> results = race(5, () -> bookings.create(userId, request("QVRACE11")));

		assertThat(results).filteredOn(r -> r.equals("OK")).hasSize(1);
		assertThat(results).filteredOn(r -> r.equals("VOUCHER_INVALID:ALREADY_USED")).hasSize(4);
		assertThat(TestVouchers.usedCount(jdbc, voucher)).isEqualTo(1);
		assertThat(available()).isEqualTo(4);
		assertThat(bookingCount()).isEqualTo(1);
	}

}
```

- [ ] **Step 2: Chạy test**

```bash
cd backend && ./mvnw test -Dtest=BookingConcurrencyTest
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`. Nếu `twentyConcurrentHoldsSellExactlyFiveSeats` thấy kết quả khác `SEATS_UNAVAILABLE` (VD `INTERNAL_ERROR` do deadlock), kiểm tra lại thứ tự khoá trong `BookingService.create`: `lockFlights` trước, giữ ghế theo `flightId` tăng dần. Nếu `sameVoucherAtOnceKeepsOneBooking` có luồng ra `INTERNAL_ERROR`, kiểm tra `insertBooking` đã đổi `DuplicateKeyException` sang `ALREADY_USED` chưa.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/vn/edu/uit/flightbooking/booking/BookingConcurrencyTest.java
git commit -m "test(booking): giữ chỗ đồng thời không bán vượt ghế, một voucher một booking (T1, T16)"
```

---

### Task 4: Huỷ giữ chỗ và job hết hạn (FR-33, BR-45, T5)

**Files:**

- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingCancellation.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingExpiryJob.java`
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/FlightBookingApplication.java` (`@EnableScheduling`)
- Modify (thay toàn bộ): `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/BookingController.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/booking/domain/BookingLifecycleTest.java`

**Interfaces:**

- Consumes: `SeatInventory.release` (Plan 06); `VoucherApi.release` (Plan 09); `BookingStatus`, `BookingService` (Task 2); test: `TestBookings`, `TestVouchers`, `TestUsers`, `TestCsrf`.
- Produces:
  - `BookingCancellation.cancel(long userId, String code)`: FR-33. Không phải chủ thì 404; không còn `PENDING_PAYMENT` thì 409 `INVALID_STATE`.
  - `BookingCancellation.expire(long bookingId) → boolean`: `true` khi đã chuyển sang `EXPIRED`.
  - `BookingCancellation.overdue() → List<Long>` (Q-07).
  - `BookingCancellation.notFound() → BusinessException` (package-private, Task 5 dùng lại).
  - `BookingExpiryJob.expireOverdue() → int` (package-private, chạy mỗi 60 giây).
  - `POST /api/bookings/{code}/cancel` → 204.

- [ ] **Step 1: Viết test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/booking/domain/BookingLifecycleTest.java`:

```java
package vn.edu.uit.flightbooking.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

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
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.promotion.TestVouchers;

/**
 * FR-33, BR-45, T5, BR-104. Chạy trong transaction của test, nên job nền không thấy dữ liệu; test gọi thẳng
 * {@link BookingExpiryJob#expireOverdue()}. {@code now()} của PostgreSQL là thời điểm test bắt đầu.
 */
@IntegrationTest
@Transactional
class BookingLifecycleTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	BookingExpiryJob job;

	@Autowired
	BookingCancellation cancellation;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
	}

	String hold(Map<String, Object> changes) throws Exception {
		return TestBookings.holdCode(mvc, customer.session(), TestBookings.body(trip, changes));
	}

	ResultActions cancel(MockHttpSession session, String code) throws Exception {
		return mvc.perform(post("/api/bookings/{code}/cancel", code).with(csrf(mvc)).session(session));
	}

	String bookingStatus(String code) {
		return jdbc.sql("SELECT status FROM bookings WHERE code = ?").param(code).query(String.class).single();
	}

	int available(long flightId) {
		return TestBookings.available(jdbc, flightId);
	}

	void holdEndedMinutesAgo(String code, int minutes) {
		jdbc.sql("UPDATE bookings SET hold_expires_at = now() - :minutes * INTERVAL '1 minute' WHERE code = :code")
			.param("minutes", minutes)
			.param("code", code)
			.update();
	}

	/** FR-33: khách huỷ thì mọi chặng thành CANCELLED, ghế và lượt voucher được trả (BR-32, BR-45). */
	@Test
	void customerCancelsPendingBookingAndGetsSeatsAndVoucherBack() throws Exception {
		long voucher = TestVouchers.insert(jdbc, "QVBACK", Map.of());
		String code = hold(Map.of("voucherCode", "QVBACK"));
		assertThat(available(trip.firstLeg())).isEqualTo(99);
		assertThat(TestVouchers.usedCount(jdbc, voucher)).isEqualTo(1);

		cancel(customer.session(), code).andExpect(status().isNoContent());

		assertThat(bookingStatus(code)).isEqualTo("CANCELLED");
		assertThat(jdbc.sql("""
				SELECT s.status FROM booking_segments s JOIN booking_journeys j ON j.id = s.journey_id
				JOIN bookings b ON b.id = j.booking_id WHERE b.code = ?
				""").param(code).query(String.class).list()).hasSize(2).containsOnly("CANCELLED");
		assertThat(available(trip.firstLeg())).isEqualTo(100);
		assertThat(available(trip.secondLeg())).isEqualTo(100);
		assertThat(TestVouchers.usedCount(jdbc, voucher)).isZero();
		cancel(customer.session(), code).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
	}

	/** BR-104: booking của người khác như không tồn tại. Booking đã xuất vé không huỷ được. Mã không phân biệt hoa thường. */
	@Test
	void onlyOwnersCancelAndOnlyWhilePending() throws Exception {
		String code = hold(Map.of());
		MockHttpSession someoneElse = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER).session();

		cancel(someoneElse, code).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		cancel(customer.session(), "ZZZZZZ").andExpect(status().isNotFound());
		assertThat(bookingStatus(code)).isEqualTo("PENDING_PAYMENT");

		TestVouchers.insert(jdbc, "QVFREE", Map.of("discount_value", 100));
		String issued = hold(Map.of("voucherCode", "QVFREE"));
		cancel(customer.session(), issued).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
		assertThat(bookingStatus(issued)).isEqualTo("ISSUED");

		cancel(customer.session(), code.toLowerCase()).andExpect(status().isNoContent());
		assertThat(bookingStatus(code)).isEqualTo("CANCELLED");
	}

	/** T5: quá hạn hơn 5 phút thì EXPIRED, trả ghế và lượt voucher; còn trong 5 phút ân hạn thì không bị đụng tới. */
	@Test
	void expiryJobWaitsForGracePeriod() throws Exception {
		long voucher = TestVouchers.insert(jdbc, "QVLATE", Map.of());
		String overdue = hold(Map.of("voucherCode", "QVLATE"));
		String inGrace = hold(Map.of());
		holdEndedMinutesAgo(overdue, 6);
		holdEndedMinutesAgo(inGrace, 2);
		assertThat(available(trip.firstLeg())).isEqualTo(98);

		assertThat(job.expireOverdue()).isEqualTo(1);

		assertThat(bookingStatus(overdue)).isEqualTo("EXPIRED");
		assertThat(bookingStatus(inGrace)).isEqualTo("PENDING_PAYMENT");
		assertThat(available(trip.firstLeg())).isEqualTo(99);
		assertThat(available(trip.secondLeg())).isEqualTo(99);
		assertThat(TestVouchers.usedCount(jdbc, voucher)).isZero();
	}

	/** APP_FLOW §4.3: khách huỷ rồi job chạy (hoặc ngược lại) thì ghế chỉ được trả một lần. */
	@Test
	void releasesSeatsOnlyOnce() throws Exception {
		String code = hold(Map.of());
		cancel(customer.session(), code).andExpect(status().isNoContent());
		holdEndedMinutesAgo(code, 10);

		assertThat(cancellation.expire(TestBookings.bookingId(jdbc, code))).isFalse();

		assertThat(bookingStatus(code)).isEqualTo("CANCELLED");
		assertThat(available(trip.firstLeg())).isEqualTo(100);
	}

	/** Quá hạn giữ chỗ nhưng còn trong 5 phút ân hạn: booking vẫn PENDING_PAYMENT nên khách vẫn huỷ được. */
	@Test
	void customerMayCancelDuringGrace() throws Exception {
		String code = hold(Map.of());
		holdEndedMinutesAgo(code, 2);

		cancel(customer.session(), code).andExpect(status().isNoContent());

		assertThat(bookingStatus(code)).isEqualTo("CANCELLED");
		assertThat(available(trip.firstLeg())).isEqualTo(100);
	}

}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
cd backend && ./mvnw test -Dtest=BookingLifecycleTest
```

Expected: `COMPILATION ERROR`, không tìm thấy `BookingExpiryJob`, `BookingCancellation`.

- [ ] **Step 3: `BookingCancellation` và `BookingExpiryJob`**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingCancellation.java`:

```java
package vn.edu.uit.flightbooking.booking.domain;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.flight.SeatInventory;
import vn.edu.uit.flightbooking.promotion.VoucherApi;

/**
 * Huỷ booking chưa thanh toán (FR-33, BR-45, TDD §6.5): khách tự huỷ thì CANCELLED, quá hạn giữ chỗ thì EXPIRED. Cả
 * hai dùng chung {@link #release}: thứ tự khoá TDD §5.4 là {@code bookings} → {@code flight_cabins} →
 * {@code vouchers}.
 */
@Service
public class BookingCancellation {

	/** BR-45: chờ thêm 5 phút sau hạn giữ chỗ để kịp nhận kết quả thanh toán đến trễ (hằng số, PRD §7). */
	static final Duration GRACE = Duration.ofMinutes(5);

	private record Locked(long id, long userId, BookingStatus status, Long voucherId, Instant holdExpiresAt) {
	}

	private record Seat(long flightId, CabinClass cabin) {
	}

	/** Q-07. */
	private static final String OVERDUE = """
			SELECT id
			FROM bookings
			WHERE status = 'PENDING_PAYMENT'
			  AND hold_expires_at < now() - INTERVAL '5 minutes'
			ORDER BY hold_expires_at
			LIMIT 100
			""";

	/** APP_FLOW §4.3: chặng ACTIVE thành CANCELLED; trả về chuyến và hạng ghế để trả ghế. */
	private static final String CANCEL_SEGMENTS = """
			UPDATE booking_segments s
			SET status = 'CANCELLED'
			FROM booking_journeys j
			WHERE j.id = s.journey_id AND j.booking_id = :bookingId AND s.status = 'ACTIVE'
			RETURNING s.flight_id, j.cabin_class
			""";

	private final SeatInventory seats;

	private final VoucherApi vouchers;

	private final JdbcClient jdbc;

	BookingCancellation(SeatInventory seats, VoucherApi vouchers, JdbcClient jdbc) {
		this.seats = seats;
		this.vouchers = vouchers;
		this.jdbc = jdbc;
	}

	/** Booking của người khác cũng báo không tìm thấy, để không lộ việc booking đó có tồn tại (BR-104). */
	static BusinessException notFound() {
		return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy booking");
	}

	/** Q-07: tối đa 100 booking chờ thanh toán đã quá hạn giữ chỗ hơn 5 phút, quá hạn lâu nhất trước. */
	@Transactional(readOnly = true)
	public List<Long> overdue() {
		return jdbc.sql(OVERDUE).query(Long.class).list();
	}

	/** FR-33: khách huỷ booking chưa thanh toán của mình, kể cả trong 5 phút ân hạn sau hạn giữ chỗ. */
	@Transactional
	public void cancel(long userId, String code) {
		Locked booking = lock("code = :key", code.toUpperCase(Locale.ROOT)).filter(b -> b.userId() == userId)
			.orElseThrow(BookingCancellation::notFound);
		if (booking.status() != BookingStatus.PENDING_PAYMENT) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "Chỉ huỷ được booking đang chờ thanh toán");
		}
		release(booking, BookingStatus.CANCELLED);
	}

	/**
	 * TDD §6.5: khoá rồi kiểm tra lại. Booking đã được thanh toán, đã bị huỷ, hoặc chưa quá 5 phút ân hạn thì bỏ qua và
	 * trả {@code false}, nên job và khách huỷ cùng lúc không trả ghế hai lần.
	 */
	@Transactional
	public boolean expire(long bookingId) {
		Optional<Locked> booking = lock("id = :key", bookingId);
		if (booking.isEmpty() || booking.get().status() != BookingStatus.PENDING_PAYMENT
				|| !booking.get().holdExpiresAt().plus(GRACE).isBefore(Instant.now())) {
			return false;
		}
		release(booking.get(), BookingStatus.EXPIRED);
		return true;
	}

	private Optional<Locked> lock(String where, Object key) {
		return jdbc.sql("SELECT id, user_id, status, voucher_id, hold_expires_at FROM bookings WHERE " + where
				+ " FOR UPDATE")
			.param("key", key)
			.query(BookingCancellation::locked)
			.optional();
	}

	/**
	 * Mỗi chặng ACTIVE trả đúng (người lớn + trẻ em) ghế một lần, theo {@code flightId} tăng dần (Q-02). Trả lượt
	 * voucher (Q-15, BR-32). Chuyển booking sang {@code status}.
	 */
	private void release(Locked booking, BookingStatus status) {
		int seated = jdbc.sql("SELECT count(*) FROM passengers WHERE booking_id = ? AND type <> 'INFANT'")
			.param(booking.id())
			.query(Integer.class)
			.single();
		jdbc.sql(CANCEL_SEGMENTS)
			.param("bookingId", booking.id())
			.query((rs, rowNum) -> new Seat(rs.getLong("flight_id"), CabinClass.valueOf(rs.getString("cabin_class"))))
			.list()
			.stream()
			.sorted(Comparator.comparingLong(Seat::flightId))
			.forEach(seat -> seats.release(seat.flightId(), seat.cabin(), seated));
		if (booking.voucherId() != null) {
			vouchers.release(booking.voucherId());
		}
		jdbc.sql("UPDATE bookings SET status = :status, updated_at = now() WHERE id = :id")
			.param("status", status.name())
			.param("id", booking.id())
			.update();
	}

	private static Locked locked(ResultSet rs, int rowNum) throws SQLException {
		return new Locked(rs.getLong("id"), rs.getLong("user_id"), BookingStatus.valueOf(rs.getString("status")),
				rs.getObject("voucher_id", Long.class),
				rs.getObject("hold_expires_at", OffsetDateTime.class).toInstant());
	}

}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingExpiryJob.java`:

```java
package vn.edu.uit.flightbooking.booking.domain;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TDD §6.5, SD-02: mỗi phút huỷ các booking đã quá hạn giữ chỗ hơn 5 phút. Mỗi booking chạy trong một transaction
 * riêng (gọi qua proxy của {@link BookingCancellation}), nên lỗi ở một booking không chặn các booking sau. Backend
 * chạy một instance nên không cần khoá phân tán (TDD §6.5).
 */
@Component
class BookingExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(BookingExpiryJob.class);

	private final BookingCancellation bookings;

	BookingExpiryJob(BookingCancellation bookings) {
		this.bookings = bookings;
	}

	@Scheduled(fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
	void run() {
		expireOverdue();
	}

	/** Trả số booking đã chuyển sang EXPIRED. */
	int expireOverdue() {
		int expired = 0;
		for (long id : bookings.overdue()) {
			try {
				if (bookings.expire(id)) {
					expired++;
				}
			}
			catch (RuntimeException e) {
				log.error("Không huỷ được booking quá hạn {}", id, e);
			}
		}
		return expired;
	}

}
```

Trong `backend/src/main/java/vn/edu/uit/flightbooking/FlightBookingApplication.java`:

1. Thêm `import org.springframework.scheduling.annotation.EnableScheduling;` ngay sau dòng import `EnableAsync`.
2. Thêm `@EnableScheduling` ngay dưới `@EnableAsync`.
3. Đổi Javadoc của lớp thành `/** {@code @EnableAsync}: listener {@code @ApplicationModuleListener} (gửi email) chạy bất đồng bộ (TDD §3.3). {@code @EnableScheduling}: job hết hạn giữ chỗ (TDD §6.5). */`

- [ ] **Step 4: Controller thêm huỷ**

Thay toàn bộ `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/BookingController.java` bằng:

```java
package vn.edu.uit.flightbooking.booking.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.booking.domain.BookingCancellation;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingService;
import vn.edu.uit.flightbooking.booking.domain.QuoteService;
import vn.edu.uit.flightbooking.common.CurrentUser;

/**
 * Màn hình C-07, C-09: báo giá (FR-20), giữ chỗ (FR-24), huỷ giữ chỗ (FR-33). Quyền CUSTOMER theo đường dẫn ở
 * SecurityConfig; quyền chủ booking kiểm tra trong service (BR-104).
 */
@RestController
@RequestMapping("/api/bookings")
class BookingController {

	private final QuoteService quotes;

	private final BookingService bookings;

	private final BookingCancellation cancellation;

	BookingController(QuoteService quotes, BookingService bookings, BookingCancellation cancellation) {
		this.quotes = quotes;
		this.bookings = bookings;
		this.cancellation = cancellation;
	}

	@PostMapping("/quote")
	QuoteResponse quote(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody BookingRequest body) {
		return QuoteResponse.of(quotes.quote(me.id(), body));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	BookingCreatedResponse create(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody BookingRequest body) {
		return BookingCreatedResponse.of(bookings.create(me.id(), body));
	}

	@PostMapping("/{code}/cancel")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void cancel(@AuthenticationPrincipal CurrentUser me, @PathVariable String code) {
		cancellation.cancel(me.id(), code);
	}

}
```

- [ ] **Step 5: Chạy test, xác nhận xanh**

```bash
cd backend && ./mvnw test -Dtest='BookingLifecycleTest,BookingHoldTest,ModularityTest'
```

Expected: `Tests run: 10, Failures: 0, Errors: 0` (5 + 4 + 1).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/FlightBookingApplication.java backend/src/main/java/vn/edu/uit/flightbooking/booking backend/src/test/java/vn/edu/uit/flightbooking/booking
git commit -m "feat(booking): huỷ giữ chỗ và job hết hạn trả ghế, trả lượt voucher (FR-33, BR-45, T5)"
```

---

### Task 5: Danh sách, chi tiết booking và tra cứu của Staff (FR-40, FR-41, FR-70, BR-104, T15)

**Files:**

- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingQueries.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/StaffBookingController.java`
- Modify (thay toàn bộ): `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/BookingController.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/booking/web/BookingQueriesTest.java`

**Interfaces:**

- Consumes: `BookingCancellation.notFound()` (Task 4); `BookingService.VIETNAM` (Task 2); `BookingStatus`, `Direction`, `PassengerType`, `Gender` (Plan 10, Task 2); `identity.UserApi.get(long) → UserSummary(id, email, fullName)` (Plan 02); `CabinClass` (Plan 05); test: `TestBookings`, `TestVouchers`, `TestUsers`.
- Produces:
  - `BookingQueries.mine(long userId, BookingStatus status, Pageable) → Page<BookingSummary>`.
  - `BookingQueries.search(String q, BookingStatus status, Pageable) → Page<BookingSummary>`.
  - `BookingQueries.detail(long userId, String code) → BookingDetail` (404 khi không phải chủ).
  - `BookingQueries.staffDetail(String code) → StaffBookingDetail`.
  - Record công khai lồng trong `BookingQueries`: `RouteView`, `BookingSummary`, `Contact`, `SegmentView`, `BaggageView`, `JourneyView`, `TicketView`, `PassengerView`, `BookingDetail`, `StaffBookingDetail` (trường liệt kê trong code dưới đây).
  - `GET /api/bookings`, `GET /api/bookings/{code}`, `GET /api/staff/bookings`, `GET /api/staff/bookings/{code}`.

- [ ] **Step 1: Viết test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/booking/web/BookingQueriesTest.java`:

```java
package vn.edu.uit.flightbooking.booking.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;
import static vn.edu.uit.flightbooking.booking.TestBookings.ADULT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.INFANT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.passenger;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;

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
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.promotion.TestVouchers;

/** FR-40, FR-41, FR-70, BR-24, BR-104, T15 (phần 404). */
@IntegrationTest
@Transactional
class BookingQueriesTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	TestBookings.Trip trip;

	TestUsers.TestUser customer;

	@BeforeEach
	void setUp() throws Exception {
		trip = TestBookings.trip(mvc, jdbc);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
	}

	String hold(MockHttpSession session, Map<String, Object> changes) throws Exception {
		return TestBookings.holdCode(mvc, session, TestBookings.body(trip, changes));
	}

	ResultActions getAs(MockHttpSession session, String url, Object... vars) throws Exception {
		return mvc.perform(get(url, vars).session(session));
	}

	/** Truyền {@code q} bằng {@code param} chứ không qua URI template: dấu {@code +} trong template bị đọc thành khoảng trắng. */
	ResultActions search(MockHttpSession session, String q) throws Exception {
		return mvc.perform(get("/api/staff/bookings").param("q", q).session(session));
	}

	/** FR-40: mới nhất trước, lọc theo trạng thái, chỉ booking của mình; tuyến theo múi giờ sân bay. */
	@Test
	void listsMyBookingsNewestFirstWithStatusFilter() throws Exception {
		String oneWay = hold(customer.session(), Map.of());
		String roundTrip = hold(customer.session(), Map.of("journeys", trip.roundTrip()));
		mvc.perform(post("/api/bookings/{code}/cancel", oneWay).with(csrf(mvc)).session(customer.session()))
			.andExpect(status().isNoContent());
		hold(TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER).session(), Map.of());

		getAs(customer.session(), "/api/bookings").andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].code", contains(roundTrip, oneWay)))
			.andExpect(jsonPath("$.page.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].status").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.content[0].totalAmount").value(5_180_000))
			.andExpect(jsonPath("$.content[0].routes[*].direction", contains("OUTBOUND", "RETURN")))
			.andExpect(jsonPath("$.content[0].routes[0].departureAirport").value("QHA"))
			.andExpect(jsonPath("$.content[0].routes[0].arrivalAirport").value("QHE"))
			.andExpect(jsonPath("$.content[0].routes[0].departureTime").value(DAY + "T06:00:00+07:00"))
			.andExpect(jsonPath("$.content[0].routes[1].departureAirport").value("QHE"))
			.andExpect(jsonPath("$.content[0].routes[1].arrivalAirport").value("QHA"))
			.andExpect(jsonPath("$.content[1].routes[0].arrivalAirport").value("QHE"));
		getAs(customer.session(), "/api/bookings?status=CANCELLED")
			.andExpect(jsonPath("$.content[*].code", contains(oneWay)));
	}

	/** FR-41, BR-24: chi tiết đọc từ snapshot, Admin đổi gói giá hay giá bán sau khi giữ chỗ không ảnh hưởng. */
	@Test
	void detailShowsSnapshotPassengersTicketsAndBaggage() throws Exception {
		long bag20 = TestBookings.baggageOption(jdbc, "Q1", 20, 350_000);
		TestVouchers.insert(jdbc, "QVWELCOME", Map.of("max_discount", 200_000));
		String code = hold(customer.session(), Map.of("journeys", trip.roundTrip(), "passengers",
				List.of(passenger("ADULT", ADULT_BORN), passenger("INFANT", INFANT_BORN)), "baggage",
				List.of(TestBookings.bag(0, "OUTBOUND", bag20)), "voucherCode", "QVWELCOME"));
		jdbc.sql("UPDATE fare_families SET name = 'Eco Mới', refundable = TRUE WHERE id = ?")
			.param(trip.catalog().economyFare())
			.update();
		jdbc.sql("UPDATE flight_fares SET price = 9999000 WHERE flight_id = ?").param(trip.firstLeg()).update();

		getAs(customer.session(), "/api/bookings/{code}", code).andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
			.andExpect(jsonPath("$.contact.name").value("NGUYEN VAN AN"))
			.andExpect(jsonPath("$.voucherCode").value("QVWELCOME"))
			.andExpect(jsonPath("$.totalAmount").value(5_848_000))
			.andExpect(jsonPath("$.journeys[*].direction", contains("OUTBOUND", "RETURN")))
			.andExpect(jsonPath("$.journeys[0].fareFamilyName").value("Eco"))
			.andExpect(jsonPath("$.journeys[0].refundable").value(false))
			.andExpect(jsonPath("$.journeys[0].discount").value(121_428))
			.andExpect(jsonPath("$.journeys[0].segments[*].flightNumber", contains("Q1100", "Q1200")))
			.andExpect(jsonPath("$.journeys[0].segments[0].adultPrice").value(1_870_000))
			.andExpect(jsonPath("$.journeys[0].segments[0].departureTime").value(DAY + "T06:00:00+07:00"))
			.andExpect(jsonPath("$.journeys[0].segments[0].status").value("ACTIVE"))
			.andExpect(jsonPath("$.journeys[0].baggage[0].passengerIndex").value(0))
			.andExpect(jsonPath("$.journeys[0].baggage[0].weightKg").value(20))
			.andExpect(jsonPath("$.journeys[1].baggage").isEmpty())
			.andExpect(jsonPath("$.passengers[*].type", contains("ADULT", "INFANT")))
			.andExpect(jsonPath("$.passengers[1].tickets[*].flightNumber", contains("Q1100", "Q1200", "Q1300")))
			.andExpect(jsonPath("$.passengers[1].tickets[*].fareAmount", contains(187_000, 115_000, 216_000)))
			.andExpect(jsonPath("$.passengers[1].tickets[0].ticketNumber").value(nullValue()));
	}

	/** T15 phần 404, BR-104: booking của người khác giống hệt mã không tồn tại; mã không phân biệt hoa thường. */
	@Test
	void otherCustomersGetNotFound() throws Exception {
		String code = hold(customer.session(), Map.of());
		MockHttpSession someoneElse = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER).session();

		getAs(someoneElse, "/api/bookings/{code}", code).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		getAs(customer.session(), "/api/bookings/{code}", "ZZZZZZ").andExpect(status().isNotFound());
		getAs(customer.session(), "/api/bookings/{code}", code.toLowerCase()).andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(code));
	}

	/** FR-70: Staff tìm theo mã, email, số điện thoại liên hệ (không phân biệt hoa thường), xem tài khoản đã đặt. */
	@Test
	void staffFindsByCodeEmailOrPhoneAndSeesAccount() throws Exception {
		String code = hold(customer.session(), Map.of("contact",
				Map.of("name", "TRAN THI BINH", "email", "Binh.Lookup@Example.com", "phone", "+84912345678")));
		MockHttpSession staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF).session();

		search(staff, code.toLowerCase()).andExpect(jsonPath("$.content[*].code", contains(code)));
		search(staff, "binh.lookup@example.com").andExpect(jsonPath("$.content[*].code", contains(code)))
			.andExpect(jsonPath("$.content[0].contactPhone").value("+84912345678"));
		search(staff, "+84912345678").andExpect(jsonPath("$.content[*].code", contains(code)));
		search(staff, "khong-co-ai").andExpect(jsonPath("$.page.totalElements").value(0));

		getAs(staff, "/api/staff/bookings/{code}", code.toLowerCase()).andExpect(status().isOk())
			.andExpect(jsonPath("$.account.email").value(customer.email()))
			.andExpect(jsonPath("$.booking.code").value(code))
			.andExpect(jsonPath("$.booking.contact.name").value("TRAN THI BINH"));
		getAs(customer.session(), "/api/staff/bookings").andExpect(status().isForbidden());
	}

}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
cd backend && ./mvnw test -Dtest=BookingQueriesTest
```

Expected: `Tests run: 4`, cả 4 test đỏ vì chưa có route `GET`. `GET /api/bookings` trả 405 (đường dẫn chỉ có `POST`). `GET /api/bookings/{code}` và `GET /api/staff/bookings…` trả 404. Mọi kỳ vọng 200 đều hỏng. Riêng `otherCustomersGetNotFound` hỏng ở lời gọi cuối, vốn chờ 200.

- [ ] **Step 3: `BookingQueries`**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingQueries.java`:

```java
package vn.edu.uit.flightbooking.booking.domain;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.catalog.CabinClass;
import vn.edu.uit.flightbooking.identity.UserApi;
import vn.edu.uit.flightbooking.identity.UserSummary;

/**
 * Đọc booking cho C-08, C-09 (FR-40, FR-41) và S-01, S-02 (FR-70). Chỉ đọc. Đọc thẳng bảng {@code flights},
 * {@code airports} (flight, catalog) và {@code vouchers} (promotion) bằng SQL để lấy số hiệu, múi giờ, mã voucher.
 * Thông tin gói giá và giá luôn lấy từ snapshot của booking (BR-24). Chặng hiện hành là chặng ACTIVE hoặc CANCELLED;
 * Plan 15 (đổi chuyến) tinh chỉnh cho chặng PENDING_CHANGE và REPLACED.
 */
@Service
public class BookingQueries {

	/** Một chiều trên C-08: điểm đi, điểm đến cuối, giờ cất cánh chặng đầu theo giờ sân bay đi. */
	public record RouteView(Direction direction, String departureAirport, String arrivalAirport,
			OffsetDateTime departureTime) {
	}

	public record BookingSummary(String code, BookingStatus status, String contactName, String contactEmail,
			String contactPhone, long totalAmount, OffsetDateTime holdExpiresAt, OffsetDateTime createdAt,
			List<RouteView> routes) {
	}

	public record Contact(String name, String email, String phone) {
	}

	/** Giờ theo múi giờ sân bay tương ứng; {@code adultPrice}: giá người lớn đã chốt (BR-24). */
	public record SegmentView(long flightId, String flightNumber, String departureAirport,
			OffsetDateTime departureTime, String arrivalAirport, OffsetDateTime arrivalTime, String status,
			long adultPrice) {
	}

	/** {@code passengerIndex}: vị trí trong {@code passengers} của chi tiết, tính từ 0. */
	public record BaggageView(int passengerIndex, int weightKg, long price) {
	}

	/** Quy định gói giá là snapshot lúc giữ chỗ (BR-24); {@code discount}: phần mức giảm của chiều (BR-33). */
	public record JourneyView(Direction direction, String status, String airlineCode, String fareFamilyName,
			CabinClass cabinClass, int carryOnKg, int checkedBaggageKg, boolean refundable, long refundFee,
			boolean changeable, long changeFee, long discount, List<SegmentView> segments,
			List<BaggageView> baggage) {
	}

	/** {@code ticketNumber} là {@code null} cho tới khi xuất vé. */
	public record TicketView(Direction direction, String flightNumber, long fareAmount, String ticketNumber) {
	}

	public record PassengerView(PassengerType type, String lastName, String firstName, Gender gender,
			LocalDate dateOfBirth, String nationality, String passportNumber, LocalDate passportExpiry,
			List<TicketView> tickets) {
	}

	/** Lượt thu, lượt hoàn, yêu cầu hoàn và đổi chuyến do Plan 12, 14, 15 thêm. */
	public record BookingDetail(String code, BookingStatus status, OffsetDateTime holdExpiresAt,
			OffsetDateTime issuedAt, OffsetDateTime createdAt, Contact contact, String voucherCode, long fareTotal,
			long baggageTotal, long discountTotal, long totalAmount, List<JourneyView> journeys,
			List<PassengerView> passengers) {
	}

	/** S-02: chi tiết kèm tài khoản đã đặt. */
	public record StaffBookingDetail(UserSummary account, BookingDetail booking) {
	}

	private record Row(long id, long userId, String code, BookingStatus status, Contact contact, String voucherCode,
			long fareTotal, long baggageTotal, long discountTotal, long totalAmount, OffsetDateTime holdExpiresAt,
			OffsetDateTime issuedAt, OffsetDateTime createdAt) {
	}

	private record Loaded(long userId, BookingDetail detail) {
	}

	private record SummaryRow(long id, String code, BookingStatus status, String contactName, String contactEmail,
			String contactPhone, long totalAmount, OffsetDateTime holdExpiresAt, OffsetDateTime createdAt) {

		BookingSummary view(List<RouteView> routes) {
			return new BookingSummary(code, status, contactName, contactEmail, contactPhone, totalAmount,
					holdExpiresAt, createdAt, routes);
		}

	}

	private record Leg(long bookingId, Direction direction, String departureAirport, OffsetDateTime departureTime,
			String arrivalAirport) {
	}

	private record JourneyRow(long id, Direction direction, String status, String airlineCode, String fareFamilyName,
			CabinClass cabinClass, int carryOnKg, int checkedBaggageKg, boolean refundable, long refundFee,
			boolean changeable, long changeFee, long discount) {

		JourneyView view(List<SegmentView> segments, List<BaggageView> baggage) {
			return new JourneyView(direction, status, airlineCode, fareFamilyName, cabinClass, carryOnKg,
					checkedBaggageKg, refundable, refundFee, changeable, changeFee, discount, segments, baggage);
		}

	}

	private record SegmentRow(long id, long journeyId, SegmentView view) {
	}

	private record PassengerRow(long id, PassengerType type, String lastName, String firstName, Gender gender,
			LocalDate dateOfBirth, String nationality, String passportNumber, LocalDate passportExpiry) {

		PassengerView view(List<TicketView> tickets) {
			return new PassengerView(type, lastName, firstName, gender, dateOfBirth, nationality, passportNumber,
					passportExpiry, tickets);
		}

	}

	private record TicketRow(long passengerId, long segmentId, long fareAmount, String ticketNumber) {
	}

	private record BaggageRow(long journeyId, long passengerId, int weightKg, long price) {
	}

	private static final String CURRENT_SEGMENT = "s.status IN ('ACTIVE', 'CANCELLED')";

	private static final String SUMMARY = """
			SELECT b.id, b.code, b.status, b.contact_name, b.contact_email, b.contact_phone, b.total_amount,
			       b.hold_expires_at, b.created_at
			FROM bookings b
			""";

	private static final String ROUTES = """
			SELECT j.booking_id, j.direction, f.departure_airport, dep.timezone AS departure_timezone,
			       f.departure_time, f.arrival_airport
			FROM booking_journeys j
			JOIN booking_segments s ON s.journey_id = j.id
			JOIN flights f ON f.id = s.flight_id
			JOIN airports dep ON dep.code = f.departure_airport
			WHERE j.booking_id IN (:ids) AND %s
			ORDER BY j.booking_id, j.direction, s.seq
			""".formatted(CURRENT_SEGMENT);

	private static final String BOOKING = """
			SELECT b.id, b.user_id, b.code, b.status, b.contact_name, b.contact_email, b.contact_phone,
			       v.code AS voucher_code, b.fare_total, b.baggage_total, b.discount_total, b.total_amount,
			       b.hold_expires_at, b.issued_at, b.created_at
			FROM bookings b
			LEFT JOIN vouchers v ON v.id = b.voucher_id
			WHERE b.code = :code
			""";

	private static final String JOURNEYS = """
			SELECT id, direction, status, airline_code, fare_family_name, cabin_class, carry_on_kg, checked_baggage_kg,
			       refundable, refund_fee, changeable, change_fee, discount_amount
			FROM booking_journeys
			WHERE booking_id = :id
			ORDER BY direction
			""";

	private static final String SEGMENTS = """
			SELECT s.id, s.journey_id, s.status, s.adult_price, f.id AS flight_id, f.flight_number,
			       f.departure_airport, dep.timezone AS departure_timezone, f.departure_time,
			       f.arrival_airport, arr.timezone AS arrival_timezone, f.arrival_time
			FROM booking_segments s
			JOIN booking_journeys j ON j.id = s.journey_id
			JOIN flights f ON f.id = s.flight_id
			JOIN airports dep ON dep.code = f.departure_airport
			JOIN airports arr ON arr.code = f.arrival_airport
			WHERE j.booking_id = :id AND %s
			ORDER BY s.journey_id, s.seq
			""".formatted(CURRENT_SEGMENT);

	private static final String PASSENGERS = """
			SELECT id, type, last_name, first_name, gender, date_of_birth, nationality, passport_number,
			       passport_expiry
			FROM passengers
			WHERE booking_id = :id
			ORDER BY id
			""";

	private static final String TICKETS = """
			SELECT t.passenger_id, t.segment_id, t.fare_amount, t.ticket_number
			FROM tickets t
			JOIN passengers p ON p.id = t.passenger_id
			WHERE p.booking_id = :id
			ORDER BY t.segment_id
			""";

	private static final String BAGGAGE = """
			SELECT bb.journey_id, bb.passenger_id, bb.weight_kg, bb.price
			FROM booking_baggage bb
			JOIN passengers p ON p.id = bb.passenger_id
			WHERE p.booking_id = :id
			ORDER BY bb.id
			""";

	private final JdbcClient jdbc;

	private final UserApi users;

	BookingQueries(JdbcClient jdbc, UserApi users) {
		this.jdbc = jdbc;
		this.users = users;
	}

	/** FR-40: mới nhất trước, lọc theo trạng thái nếu có. */
	@Transactional(readOnly = true)
	public Page<BookingSummary> mine(long userId, BookingStatus status, Pageable pageable) {
		StringBuilder where = new StringBuilder(" WHERE b.user_id = :userId");
		Map<String, Object> params = new HashMap<>(Map.of("userId", userId));
		filterStatus(where, params, status);
		return page(where, params, pageable);
	}

	/**
	 * FR-70: {@code q} khớp nguyên văn với mã đặt chỗ hoặc email liên hệ (không phân biệt hoa thường), hoặc số điện
	 * thoại liên hệ. Không có {@code q} thì liệt kê mọi booking.
	 */
	@Transactional(readOnly = true)
	public Page<BookingSummary> search(String q, BookingStatus status, Pageable pageable) {
		StringBuilder where = new StringBuilder(" WHERE TRUE");
		Map<String, Object> params = new HashMap<>();
		if (q != null && !q.isBlank()) {
			where.append(" AND (b.code = upper(:q) OR lower(b.contact_email) = lower(:q) OR b.contact_phone = :q)");
			params.put("q", q.strip());
		}
		filterStatus(where, params, status);
		return page(where, params, pageable);
	}

	/** FR-41, BR-104: booking của người khác cũng báo không tìm thấy. */
	@Transactional(readOnly = true)
	public BookingDetail detail(long userId, String code) {
		return load(code).filter(loaded -> loaded.userId() == userId)
			.map(Loaded::detail)
			.orElseThrow(BookingCancellation::notFound);
	}

	/** FR-70, S-02. */
	@Transactional(readOnly = true)
	public StaffBookingDetail staffDetail(String code) {
		Loaded loaded = load(code).orElseThrow(BookingCancellation::notFound);
		return new StaffBookingDetail(users.get(loaded.userId()), loaded.detail());
	}

	private static void filterStatus(StringBuilder where, Map<String, Object> params, BookingStatus status) {
		if (status != null) {
			where.append(" AND b.status = :status");
			params.put("status", status.name());
		}
	}

	private Page<BookingSummary> page(StringBuilder where, Map<String, Object> params, Pageable pageable) {
		long total = jdbc.sql("SELECT count(*) FROM bookings b" + where).params(params).query(Long.class).single();
		params.put("limit", pageable.getPageSize());
		params.put("offset", pageable.getOffset());
		List<SummaryRow> rows = jdbc
			.sql(SUMMARY + where + " ORDER BY b.created_at DESC, b.id DESC LIMIT :limit OFFSET :offset")
			.params(params)
			.query(BookingQueries::summary)
			.list();
		Map<Long, List<RouteView>> routes = routes(rows.stream().map(SummaryRow::id).toList());
		return new PageImpl<>(rows.stream().map(r -> r.view(routes.getOrDefault(r.id(), List.of()))).toList(),
				pageable, total);
	}

	/** Gộp các chặng theo booking và chiều: điểm đi và giờ cất cánh của chặng đầu, điểm đến của chặng cuối. */
	private Map<Long, List<RouteView>> routes(List<Long> bookingIds) {
		if (bookingIds.isEmpty()) {
			return Map.of();
		}
		List<Leg> legs = jdbc.sql(ROUTES)
			.param("ids", bookingIds)
			.query((rs, rowNum) -> new Leg(rs.getLong("booking_id"), Direction.valueOf(rs.getString("direction")),
					rs.getString("departure_airport"), local(rs, "departure_time", "departure_timezone"),
					rs.getString("arrival_airport")))
			.list();
		Map<Long, List<RouteView>> routes = new HashMap<>();
		Leg first = null;
		for (int i = 0; i < legs.size(); i++) {
			Leg leg = legs.get(i);
			if (first == null) {
				first = leg;
			}
			Leg next = i + 1 < legs.size() ? legs.get(i + 1) : null;
			if (next == null || next.bookingId() != leg.bookingId() || next.direction() != leg.direction()) {
				routes.computeIfAbsent(leg.bookingId(), k -> new ArrayList<>())
					.add(new RouteView(leg.direction(), first.departureAirport(), leg.arrivalAirport(),
							first.departureTime()));
				first = null;
			}
		}
		return routes;
	}

	private Optional<Loaded> load(String code) {
		return jdbc.sql(BOOKING)
			.param("code", code.toUpperCase(Locale.ROOT))
			.query(BookingQueries::row)
			.optional()
			.map(row -> new Loaded(row.userId(), detail(row)));
	}

	private BookingDetail detail(Row booking) {
		Map<String, Object> id = Map.of("id", booking.id());
		List<JourneyRow> journeys = jdbc.sql(JOURNEYS).params(id).query(BookingQueries::journey).list();
		List<SegmentRow> segments = jdbc.sql(SEGMENTS).params(id).query(BookingQueries::segment).list();
		List<PassengerRow> passengers = jdbc.sql(PASSENGERS).params(id).query(BookingQueries::passenger).list();
		List<TicketRow> tickets = jdbc.sql(TICKETS)
			.params(id)
			.query((rs, n) -> new TicketRow(rs.getLong("passenger_id"), rs.getLong("segment_id"),
					rs.getLong("fare_amount"), rs.getString("ticket_number")))
			.list();
		List<BaggageRow> baggage = jdbc.sql(BAGGAGE)
			.params(id)
			.query((rs, n) -> new BaggageRow(rs.getLong("journey_id"), rs.getLong("passenger_id"),
					rs.getInt("weight_kg"), rs.getLong("price")))
			.list();

		Map<Long, Integer> passengerIndex = new HashMap<>();
		for (int i = 0; i < passengers.size(); i++) {
			passengerIndex.put(passengers.get(i).id(), i);
		}
		Map<Long, Direction> directionOf = journeys.stream()
			.collect(Collectors.toMap(JourneyRow::id, JourneyRow::direction));
		Map<Long, SegmentRow> segmentById = segments.stream()
			.collect(Collectors.toMap(SegmentRow::id, Function.identity()));

		List<JourneyView> journeyViews = journeys.stream()
			.map(j -> j.view(
					segments.stream().filter(s -> s.journeyId() == j.id()).map(SegmentRow::view).toList(),
					baggage.stream()
						.filter(b -> b.journeyId() == j.id())
						.map(b -> new BaggageView(passengerIndex.get(b.passengerId()), b.weightKg(), b.price()))
						.toList()))
			.toList();
		List<PassengerView> passengerViews = passengers.stream()
			.map(p -> p.view(tickets.stream()
				.filter(t -> t.passengerId() == p.id() && segmentById.containsKey(t.segmentId()))
				.map(t -> {
					SegmentRow s = segmentById.get(t.segmentId());
					return new TicketView(directionOf.get(s.journeyId()), s.view().flightNumber(), t.fareAmount(),
							t.ticketNumber());
				})
				.toList()))
			.toList();
		return new BookingDetail(booking.code(), booking.status(), booking.holdExpiresAt(), booking.issuedAt(),
				booking.createdAt(), booking.contact(), booking.voucherCode(), booking.fareTotal(),
				booking.baggageTotal(), booking.discountTotal(), booking.totalAmount(), journeyViews, passengerViews);
	}

	private static SummaryRow summary(ResultSet rs, int rowNum) throws SQLException {
		return new SummaryRow(rs.getLong("id"), rs.getString("code"), BookingStatus.valueOf(rs.getString("status")),
				rs.getString("contact_name"), rs.getString("contact_email"), rs.getString("contact_phone"),
				rs.getLong("total_amount"), vietnam(rs, "hold_expires_at"), vietnam(rs, "created_at"));
	}

	private static Row row(ResultSet rs, int rowNum) throws SQLException {
		return new Row(rs.getLong("id"), rs.getLong("user_id"), rs.getString("code"),
				BookingStatus.valueOf(rs.getString("status")),
				new Contact(rs.getString("contact_name"), rs.getString("contact_email"), rs.getString("contact_phone")),
				rs.getString("voucher_code"), rs.getLong("fare_total"), rs.getLong("baggage_total"),
				rs.getLong("discount_total"), rs.getLong("total_amount"), vietnam(rs, "hold_expires_at"),
				vietnam(rs, "issued_at"), vietnam(rs, "created_at"));
	}

	private static JourneyRow journey(ResultSet rs, int rowNum) throws SQLException {
		return new JourneyRow(rs.getLong("id"), Direction.valueOf(rs.getString("direction")), rs.getString("status"),
				rs.getString("airline_code"), rs.getString("fare_family_name"),
				CabinClass.valueOf(rs.getString("cabin_class")), rs.getInt("carry_on_kg"),
				rs.getInt("checked_baggage_kg"), rs.getBoolean("refundable"), rs.getLong("refund_fee"),
				rs.getBoolean("changeable"), rs.getLong("change_fee"), rs.getLong("discount_amount"));
	}

	private static SegmentRow segment(ResultSet rs, int rowNum) throws SQLException {
		return new SegmentRow(rs.getLong("id"), rs.getLong("journey_id"),
				new SegmentView(rs.getLong("flight_id"), rs.getString("flight_number"),
						rs.getString("departure_airport"), local(rs, "departure_time", "departure_timezone"),
						rs.getString("arrival_airport"), local(rs, "arrival_time", "arrival_timezone"),
						rs.getString("status"), rs.getLong("adult_price")));
	}

	private static PassengerRow passenger(ResultSet rs, int rowNum) throws SQLException {
		return new PassengerRow(rs.getLong("id"), PassengerType.valueOf(rs.getString("type")),
				rs.getString("last_name"), rs.getString("first_name"), Gender.valueOf(rs.getString("gender")),
				rs.getObject("date_of_birth", LocalDate.class), rs.getString("nationality"),
				rs.getString("passport_number"), rs.getObject("passport_expiry", LocalDate.class));
	}

	/** Đổi {@code timestamptz} sang giờ địa phương của sân bay (TDD §5.1). */
	private static OffsetDateTime local(ResultSet rs, String time, String zone) throws SQLException {
		return rs.getObject(time, OffsetDateTime.class)
			.atZoneSameInstant(ZoneId.of(rs.getString(zone)))
			.toOffsetDateTime();
	}

	/** Giờ của booking theo giờ Việt Nam; {@code null} giữ nguyên (VD {@code issued_at} chưa xuất vé). */
	private static OffsetDateTime vietnam(ResultSet rs, String column) throws SQLException {
		OffsetDateTime time = rs.getObject(column, OffsetDateTime.class);
		return time == null ? null : time.atZoneSameInstant(BookingService.VIETNAM).toOffsetDateTime();
	}

}
```

- [ ] **Step 4: Controller**

Thay toàn bộ `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/BookingController.java` bằng:

```java
package vn.edu.uit.flightbooking.booking.web;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.booking.domain.BookingCancellation;
import vn.edu.uit.flightbooking.booking.domain.BookingQueries;
import vn.edu.uit.flightbooking.booking.domain.BookingQueries.BookingDetail;
import vn.edu.uit.flightbooking.booking.domain.BookingQueries.BookingSummary;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingService;
import vn.edu.uit.flightbooking.booking.domain.BookingStatus;
import vn.edu.uit.flightbooking.booking.domain.QuoteService;
import vn.edu.uit.flightbooking.common.CurrentUser;

/**
 * Màn hình C-07, C-08, C-09: báo giá (FR-20), giữ chỗ (FR-24), huỷ giữ chỗ (FR-33), danh sách (FR-40) và chi tiết
 * (FR-41). Quyền CUSTOMER theo đường dẫn ở SecurityConfig; quyền chủ booking kiểm tra trong service (BR-104).
 */
@RestController
@RequestMapping("/api/bookings")
class BookingController {

	private final QuoteService quotes;

	private final BookingService bookings;

	private final BookingCancellation cancellation;

	private final BookingQueries queries;

	BookingController(QuoteService quotes, BookingService bookings, BookingCancellation cancellation,
			BookingQueries queries) {
		this.quotes = quotes;
		this.bookings = bookings;
		this.cancellation = cancellation;
		this.queries = queries;
	}

	@PostMapping("/quote")
	QuoteResponse quote(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody BookingRequest body) {
		return QuoteResponse.of(quotes.quote(me.id(), body));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	BookingCreatedResponse create(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody BookingRequest body) {
		return BookingCreatedResponse.of(bookings.create(me.id(), body));
	}

	@GetMapping
	PagedModel<BookingSummary> mine(@AuthenticationPrincipal CurrentUser me,
			@RequestParam(required = false) BookingStatus status, @PageableDefault(size = 20) Pageable pageable) {
		return new PagedModel<>(queries.mine(me.id(), status, pageable));
	}

	@GetMapping("/{code}")
	BookingDetail detail(@AuthenticationPrincipal CurrentUser me, @PathVariable String code) {
		return queries.detail(me.id(), code);
	}

	@PostMapping("/{code}/cancel")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void cancel(@AuthenticationPrincipal CurrentUser me, @PathVariable String code) {
		cancellation.cancel(me.id(), code);
	}

}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/StaffBookingController.java`:

```java
package vn.edu.uit.flightbooking.booking.web;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.booking.domain.BookingQueries;
import vn.edu.uit.flightbooking.booking.domain.BookingQueries.BookingSummary;
import vn.edu.uit.flightbooking.booking.domain.BookingQueries.StaffBookingDetail;
import vn.edu.uit.flightbooking.booking.domain.BookingStatus;

/** Màn hình S-01, S-02: tra cứu booking (FR-70). Quyền STAFF (Admin kế thừa) theo đường dẫn ở SecurityConfig. */
@RestController
@RequestMapping("/api/staff/bookings")
class StaffBookingController {

	private final BookingQueries queries;

	StaffBookingController(BookingQueries queries) {
		this.queries = queries;
	}

	@GetMapping
	PagedModel<BookingSummary> search(@RequestParam(required = false) String q,
			@RequestParam(required = false) BookingStatus status, @PageableDefault(size = 20) Pageable pageable) {
		return new PagedModel<>(queries.search(q, status, pageable));
	}

	@GetMapping("/{code}")
	StaffBookingDetail detail(@PathVariable String code) {
		return queries.staffDetail(code);
	}

}
```

- [ ] **Step 5: Chạy test, xác nhận xanh**

```bash
cd backend && ./mvnw test -Dtest='BookingQueriesTest,BookingLifecycleTest,SecurityConfigTest,ModularityTest'
```

Expected: `BUILD SUCCESS`, 0 failures (`BookingQueriesTest` 4 test, `BookingLifecycleTest` 5 test). `SecurityConfigTest` vẫn xanh: Staff gọi `GET /api/bookings` vẫn nhận 403 như trước. `ModularityTest` chấp nhận phụ thuộc mới `booking → identity`.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/booking backend/src/test/java/vn/edu/uit/flightbooking/booking
git commit -m "feat(booking): danh sách, chi tiết booking và tra cứu của Staff (FR-40, FR-41, FR-70, BR-104)"
```

---

### Task 6: Kiểu frontend, lộ trình và kiểm tra toàn bộ

**Files:**

- Modify (sinh lại): `frontend/src/lib/api-types.ts`
- Modify: `docs/superpowers/plans/2026-10-09-00-roadmap.md` (4 chỗ: dòng "Plan chi tiết đã có", cột "Xong" của Plan 11 ở §2, dòng Plan 11 ở §6, thêm mục 12 ở §7)
- Modify: `CLAUDE.md` (dòng Status)

**Interfaces:**

- Consumes: mọi API của Task 2, 4, 5.
- Produces: kiểu frontend cho Plan 13 (C-07, C-08, C-09) và Plan 18 (S-01, S-02); hợp đồng `BookingService.issue`, `BookingIssued`, `BookingCancellation` trong lộ trình §6 cho Plan 12, 14, 15, 16.

- [ ] **Step 1: Chạy backend profile `dev`, thử nhanh và sinh lại kiểu TypeScript**

Terminal 1:

```bash
docker compose up -d postgres mailpit
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

(PowerShell: `./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"`.) Khi log báo `Started FlightBookingApplication`, chạy ở terminal 2. Đoạn này giữ chỗ một chuyến seed HAN→SGN sau 5 ngày rồi xem chi tiết:

```bash
B=http://localhost:8080
curl -s -c jar.txt -o /dev/null $B/api/auth/csrf; T=$(grep XSRF-TOKEN jar.txt | awk '{print $7}')
curl -s -b jar.txt -c jar.txt -o /dev/null -H "X-XSRF-TOKEN: $T" -H "Content-Type: application/json" \
  -d '{"email":"customer@demo.local","password":"Demo@1234"}' $B/api/auth/login
T=$(grep XSRF-TOKEN jar.txt | awk '{print $7}')
curl -s "$B/api/flights/search?from=HAN&to=SGN&date=$(date -d '+5 days' +%F)&adults=1&cabin=ECONOMY" > s.json
BODY=$(node -e 'const i=require("./s.json").itineraries[0];console.log(JSON.stringify({journeys:[{direction:"OUTBOUND",flightIds:i.segments.map(x=>x.flightId),fareFamilyId:i.fareOptions[0].fareFamilyId}],passengers:[{type:"ADULT",lastName:"NGUYEN",firstName:"VAN AN",gender:"MALE",dateOfBirth:"1990-05-20"}],contact:{name:"NGUYEN VAN AN",email:"an@example.com",phone:"0901234567"}}))')
CODE=$(curl -s -b jar.txt -H "X-XSRF-TOKEN: $T" -H "Content-Type: application/json" -d "$BODY" $B/api/bookings | node -pe 'JSON.parse(require("fs").readFileSync(0)).code')
curl -s -b jar.txt $B/api/bookings/$CODE | head -c 400; echo
rm -f jar.txt s.json
```

Expected: chi tiết booking `PENDING_PAYMENT` có `holdExpiresAt` theo `+07:00`. Trong log backend, mỗi phút có một lượt `BookingExpiryJob` chạy mà không báo lỗi.

Giữ backend chạy, rồi ở terminal 2:

```bash
cd frontend && npm run gen:api
git diff --stat src/lib/api-types.ts
grep -n "BookingCreatedResponse\|BookingSummary\|BookingDetail\|StaffBookingDetail\|/api/staff/bookings" src/lib/api-types.ts
```

Expected: `api-types.ts` có đường dẫn `/api/bookings` (POST, GET), `/api/bookings/{code}`, `/api/bookings/{code}/cancel`, `/api/staff/bookings`, `/api/staff/bookings/{code}` và các schema `BookingCreatedResponse`, `PagedModelBookingSummary`, `BookingSummary`, `RouteView`, `BookingDetail`, `JourneyView`, `SegmentView`, `BaggageView`, `PassengerView`, `TicketView`, `Contact`, `StaffBookingDetail`. Dừng backend (Ctrl+C). Nếu cổng 8080 vẫn bị giữ sau khi dừng, tắt tiến trình `java` của lần chạy này.

- [ ] **Step 2: Cập nhật lộ trình**

Trong `docs/superpowers/plans/2026-10-09-00-roadmap.md`:

1. Dòng bảng đầu tài liệu: ngay sau `[Plan 10 — Báo giá booking](2026-10-13-10-booking-quote.md)` thêm ` · [Plan 11 — Giữ chỗ và vòng đời booking](2026-10-13-11-booking-hold.md)`.
2. Bảng §2, dòng Plan 11: đổi `| 10 | 20 luồng đặt cùng lúc không bán vượt ghế; job hết hạn trả ghế và lượt voucher | P0 | ☐ |` thành `| 10 | 20 luồng đặt cùng lúc không bán vượt ghế; job hết hạn trả ghế và lượt voucher | P0 | ☑ |`.
3. Bảng §6: thay dòng `| 11 | Dự kiến: \`BookingApi\`, sự kiện \`BookingIssued\`, \`UnexpectedPaymentReceived\` |` bằng:

```
| 11 | Module `booking` phụ thuộc thêm `identity` (`UserApi.get` cho chi tiết của Staff). Ghi bằng `JdbcClient`, không có entity JPA. **Chưa có `BookingApi` và `UnexpectedPaymentReceived`**: Plan 12, 14 tạo khi có nơi dùng. `flight.SeatInventory.lockFlights(flightIds)` (FOR SHARE theo id tăng dần). `booking.domain.BookingService.create(userId, BookingRequest) → Created(code, status, holdExpiresAt, quote)`: khoá chuyến, gọi lại `QuoteService.quote`, giữ ghế theo `flightId` tăng dần, giữ lượt voucher, ghi `bookings`, `booking_journeys` (snapshot gói và `discount_amount`), `booking_segments` (`ACTIVE`, `adult_price`), `passengers`, `tickets` (`fare_amount` = `Pricing.ticketPrice` với `Quote.childPercent`, `infantPercent`), `booking_baggage`; mã BR-46 bằng `ON CONFLICT (code) DO NOTHING`; vi phạm `uq_bookings_voucher_user` thành `VOUCHER_INVALID`/`ALREADY_USED`; tổng bằng 0 thì gọi `issue`. `BookingService.issue(bookingId)`: Q-13 cho vé của chặng `ACTIVE`, booking `ISSUED` kèm `issued_at`, phát `booking.BookingIssued(bookingId)` (chưa có listener; Plan 12 thêm email vé). Bên gọi `issue` tự khoá booking và kiểm tra `PENDING_PAYMENT`. `BookingCancellation.cancel(userId, code)` (FR-33), `expire(bookingId) → boolean`, `overdue()` (Q-07), `notFound()`: khoá booking `FOR UPDATE`, chặng `ACTIVE` thành `CANCELLED`, trả ghế (Q-02) và lượt (Q-15) đúng một lần. `BookingExpiryJob` chạy mỗi 60 giây (`@EnableScheduling`), chưa xử lý `reschedules` (Plan 15). `BookingQueries.mine/search/detail/staffDetail`; chặng hiện hành là `ACTIVE` hoặc `CANCELLED` (Plan 15 tinh chỉnh). Giờ của booking theo `+07:00` (`BookingService.VIETNAM`). Enum `BookingStatus`. API: `POST /api/bookings` (201, mẫu TDD §8.8), `GET /api/bookings?status=&page=&size=`, `GET /api/bookings/{code}` (của người khác trả 404), `POST /api/bookings/{code}/cancel` (204, không còn chờ thanh toán thì 409 `INVALID_STATE`), `GET /api/staff/bookings?q=&status=` (khớp mã, email không phân biệt hoa thường, hoặc số điện thoại), `GET /api/staff/bookings/{code}` (`{account, booking}`). Đã sửa lỗi nhỏ 1, 2, 3, 6 của Plan 10 (lỗi hành khách và hành lý trả cùng lúc; chuỗi hộ chiếu trống là `null`; `Quote` mang tỉ lệ giá; mỗi dòng hành lý báo đủ lỗi). Test: `booking.TestBookings` (`trip`, `body`, `hold`, `holdCode`, `baggageOption`, `bag`, `bookingId`, `available`). Kiểu frontend: `Schemas["BookingCreatedResponse"]`, `["PagedModelBookingSummary"]`, `["BookingSummary"]`, `["RouteView"]`, `["BookingDetail"]`, `["JourneyView"]`, `["SegmentView"]`, `["BaggageView"]`, `["PassengerView"]`, `["TicketView"]`, `["StaffBookingDetail"]` |
```

4. Mục §7: sau mục 11 (Plan 10), thêm:

```
12. **Plan 11 có 5 điểm cố ý khác TDD** (chi tiết ở [Plan 11](2026-10-13-11-booking-hold.md#điểm-cố-ý-khác-tdd)): chưa tạo `BookingApi` và `UnexpectedPaymentReceived`; `booking` phụ thuộc thêm `identity` cho chi tiết của Staff; chi tiết booking chưa có lượt thu, hoàn, đổi (Plan 12, 14, 15 thêm); job hết hạn chưa xử lý yêu cầu đổi chuyến (Plan 15); huỷ giữ chỗ trả 204. Hai lỗi nhỏ của Plan 10 được giữ nguyên có chủ ý: `FlightSearch.percentOf` vẫn ở lớp tìm kiếm, và câu báo thiếu ghế vẫn ghi tên hạng bằng enum như mẫu TDD §8.8.
```

Trong `CLAUDE.md`, mục Status: đổi ``and `booking` (Plan 10: `POST /api/bookings/quote`, with `FlightApi` checking and pricing the chosen itinerary) are done.`` thành ``and `booking` (Plan 10: `POST /api/bookings/quote`, with `FlightApi` checking and pricing the chosen itinerary; Plan 11: hold, cancel, expiry job, booking list/detail and staff lookup) are done.``

- [ ] **Step 3: Kiểm tra toàn bộ**

```bash
cd backend && ./mvnw -B verify
cd ../frontend && npm run lint && npm run build
```

Expected: `BUILD SUCCESS` với 0 failures. Plan 11 thêm 3 test vào `BookingQuoteTest`, 1 vào `SeatInventoryTest`, cùng 1 + 4 + 2 + 5 + 4 test ở các lớp mới, tổng cộng 20 test mới. `npm run lint` không lỗi; `npm run build` thành công. E2E không đổi vì plan này không thêm route frontend.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/api-types.ts docs/superpowers/plans/2026-10-09-00-roadmap.md docs/superpowers/plans/2026-10-13-11-booking-hold.md CLAUDE.md
git commit -m "docs: hoàn tất Plan 11, cập nhật hợp đồng BookingService, BookingCancellation và kiểu API frontend"
```

---

## Tự kiểm tra plan

- **Độ phủ spec:**
  - FR-24, TDD §6.2 bước 5 (khoá chuyến, giữ ghế, giữ lượt, sinh mã, ghi 6 bảng, `ALREADY_USED`, tổng 0 xuất vé ngay) → Task 2, 3.
  - BR-24 (chốt giá) → Task 2 (ghi), Task 5 (đọc lại sau khi Admin sửa). BR-40, BR-43, Q-13, sự kiện `BookingIssued` → Task 2. BR-46 → Task 2.
  - FR-33, BR-45, TDD §6.5, Q-07, Q-02, Q-15, BR-32 → Task 4.
  - FR-40, FR-41, FR-70, BR-104 → Task 5.
  - T1, T16 → Task 3. T2 → Task 2. T5 → Task 4. T15 phần 404 → Task 4, 5; phần 403 đã có ở `SecurityConfigTest`.
  - Lỗi nhỏ 1, 2, 6 của Plan 10 → Task 1; lỗi nhỏ 3 → Task 2; lỗi nhỏ 4, 5 giữ nguyên, có lý do ở bảng "Sáu lỗi nhỏ" và lộ trình §7.
  - Không thuộc plan này: phần `reschedules` của TDD §6.5 (Plan 15); lượt thu, hoàn, đổi trong chi tiết (Plan 12, 14, 15); email vé (Plan 12).
- **Nhất quán tên:** `SeatInventory.lockFlights`, `BookingService.create / issue / Created / VIETNAM`, `BookingCodes.next / ALPHABET`, `BookingStatus`, `BookingIssued`, `BookingCancellation.cancel / expire / overdue / notFound / GRACE`, `BookingExpiryJob.expireOverdue`, `BookingQueries.mine / search / detail / staffDetail` và các record lồng, `Quote.childPercent / infantPercent`, `PassengerRules.checkDetails(…, FieldErrors)`, `FieldErrors.isEmpty`, `TestBookings.*`. Mỗi tên được dùng giống nhau ở mọi task và ở dòng §6 của lộ trình.
- **Review Focus:** mỗi dòng trong mục Review Focus có test nêu tên ở cuối dòng.
