# Plan 12 — Thanh toán VNPay và xuất vé: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khách bấm thanh toán một booking đang chờ thanh toán thì nhận URL VNPay đã ký. Link này hết hạn cùng lúc với hạn giữ chỗ (FR-30, BR-41). IPN và Return URL dùng chung một hàm xử lý: kiểm chữ ký, khoá lượt thu, kiểm số tiền, mỗi kết quả chỉ xử lý một lần (BR-42). Thanh toán thành công thì booking `ISSUED` và có số vé 13 chữ số (FR-31, BR-43). Nếu booking không còn chờ thanh toán thì hệ thống phát `UnexpectedPaymentReceived` để Plan 14 tạo yêu cầu hoàn (FR-32, BR-44). Sau khi commit, email vé điện tử được gửi tới email liên hệ; Staff gửi lại được email này (FR-73). Test T3 và T4 xanh, và một lượt thanh toán thật trên sandbox đi tới `ISSUED` kèm email trong Mailpit.

**Architecture:** Plan này tạo module mới `payment`, chỉ phụ thuộc `common` và chỉ ghi bảng `payments`.

- `payment.domain.Vnpay` ký và kiểm chữ ký theo code mẫu VNPay 2.1.0.
- `payment.PaymentApi.charge` ghi lượt thu `PENDING` rồi trả URL thanh toán.
- `payment.domain.PaymentCallbackService.handle` chạy theo đúng 6 bước của TDD §6.3. Khi thành công, hàm này phát `payment.PaymentSucceeded` trong cùng transaction.

`booking` phụ thuộc thêm `payment`, đúng như TDD §3.2. `booking.domain.BookingPayments` có hai việc:

- Kiểm tra điều kiện tạo lượt thu.
- Nghe `PaymentSucceeded` một cách đồng bộ: khoá booking, rồi xuất vé bằng `BookingService.issue` của Plan 11, hoặc phát `booking.UnexpectedPaymentReceived`.

Thứ tự khoá vì vậy là `payments → bookings`, khớp TDD §5.4. `notification` nghe `BookingIssued` và sự kiện mới `TicketEmailRequested` (FR-73) sau khi commit. Dữ liệu cho email vé lấy qua `booking.BookingApi.issued`.

**Tech Stack:** Java 21, Spring Boot 4.1.1 (Spring MVC, `JdbcClient`, `@EventListener`, `@ApplicationModuleListener`, Thymeleaf), Spring Modulith 2.1.1, `javax.crypto.Mac` (HMAC-SHA512), PostgreSQL 18 (`jsonb`), Testcontainers, MockMvc, `@RecordApplicationEvents`, JUnit 5 + AssertJ. Phần frontend chỉ chạy lại `npm run gen:api`.

**Spec:** [PRD](../../PRD.md) FR-30, FR-31, FR-32, FR-41, FR-73, FR-130 (email vé), BR-41, BR-42, BR-43, BR-44, BR-45 (ân hạn) · [TDD](../../TDD.md) §3.2, §3.3, §4.2, §4.3, §5.2, §5.4, §6.3, §6.4, §8.3, §8.5, §8.6, §9, §10.1, §11.1, §11.2 (T3, T4), §12, AD-07 · [App Flow](../../APP_FLOW.md) C-09, S-02, §4.4, SD-01 · [Backend Schema](../../BACKEND_SCHEMA.md) bảng `payments`, Q-13 · [Lộ trình](2026-10-09-00-roadmap.md) §2, §6 (dòng Plan 11) · [Plan 11](2026-10-13-11-booking-hold.md) · Tài liệu VNPay: <https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html>

## Global Constraints

- Mọi ràng buộc ở Global Constraints của Plan 01, 02, 06, 07, 09, 10 và [Plan 11](2026-10-13-11-booking-hold.md) vẫn giữ nguyên: ProblemDetail có `code`, CSRF SPA, câu lỗi tiếng Việt, DTO là `record`, tiền là `long` VND, ghi bằng `JdbcClient`, không dùng entity JPA.
- **Phụ thuộc giữa module** (`ModularityTest` kiểm tra):
  - `payment → common`;
  - `booking → common, catalog, flight, identity, payment, promotion`;
  - `notification → common, identity, booking`.

  `payment` **không** phụ thuộc `booking`. Kết quả thanh toán đi qua sự kiện (TDD §3.2, §3.3).

- **Không có migration mới.** Bảng `payments` và sequence `ticket_number_seq` đã có trong `V1__init.sql`.
- **Thứ tự khoá** (TDD §5.4): callback khoá `payments` (`FOR UPDATE OF p`), sau đó listener của booking khoá `bookings` (`FOR UPDATE`). Không ai khoá ngược chiều: huỷ và hết hạn của Plan 11 chỉ khoá `bookings → flight_cabins → vouchers`.
- **VNPay (TDD §6.3):**
  - `vnp_Version=2.1.0`, `vnp_Command=pay`, `vnp_CurrCode=VND`, `vnp_Locale=vn`, `vnp_OrderType=other`.
  - `vnp_Amount = amount × 100`. `vnp_OrderInfo` không dấu: `Thanh toan dat ve {CODE}`.
  - `vnp_CreateDate` và `vnp_ExpireDate` theo định dạng `yyyyMMddHHmmss`, múi giờ GMT+7. `vnp_ExpireDate` bằng hạn giữ chỗ.
  - `vnp_TxnRef` là UUID bỏ dấu gạch nối.
  - **Chuỗi ký:** chỉ lấy tham số `vnp_*` có giá trị khác rỗng, trừ `vnp_SecureHash` và `vnp_SecureHashType`. Sắp theo tên tăng dần, nối thành `key=value` bằng `&`, với key và value URL-encode bằng `URLEncoder` US-ASCII (khoảng trắng thành `+`). Chữ ký là HMAC-SHA512 dạng hex. So chữ ký không phân biệt hoa thường.
- **Mã phản hồi IPN** (theo VNPay):

  | Trường hợp                             | `RspCode` | `Message`                 |
  | -------------------------------------- | --------- | ------------------------- |
  | Đã ghi nhận (thành công hoặc thất bại) | `00`      | `Confirm Success`         |
  | Không tìm thấy lượt thu                | `01`      | `Order not found`         |
  | Đã xử lý trước đó                      | `02`      | `Order already confirmed` |
  | Sai số tiền                            | `04`      | `Invalid amount`          |
  | Sai chữ ký                             | `97`      | `Invalid signature`       |
  | Lỗi không lường trước                  | `99`      | `Unknown error`           |

  `Message` để tiếng Anh vì đây là giao thức giữa hai máy, người dùng không thấy.

- **BR-42:** thanh toán chỉ thành công khi `vnp_ResponseCode = 00` **và** `vnp_TransactionStatus = 00`. Mọi kết quả khác chuyển lượt thu sang `FAILED`, còn booking vẫn chờ thanh toán (FR-30).
- **Log (TDD §12):** mỗi callback ghi kênh (IPN hay Return), `txn_ref`, `vnp_ResponseCode` và `RspCode`. **Không** ghi hash secret hay `vnp_SecureHash`.
- **Cấu hình:** `app.vnpay.tmn-code`, `app.vnpay.hash-secret`, `app.vnpay.pay-url`, `app.vnpay.return-url` đọc từ biến môi trường `VNPAY_*` (TDD §10.1). Test đặt mã và secret riêng trong `@IntegrationTest` và không đọc `.env`.
- Test tự tạo dữ liệu bằng `TestBookings.trip` (Plan 11) trong transaction của test. Không có test nào commit dữ liệu.

### Điểm cố ý khác TDD

1. **Xác nhận thanh toán không bọc `@Retryable`** (TDD §5.4). Callback khoá `payments → bookings`, đúng thứ tự chung, và không đụng tới `flight_cabins` hay `vouchers`, nên không có vòng chờ với thao tác huỷ hoặc job hết hạn. Nếu vẫn có lỗi, IPN trả `99` và VNPay tự gọi lại tối đa 10 lần (TDD §6.3). Plan 16 (huỷ chuyến, cũng khoá `flights`) xem lại điểm này.
2. **`PaymentSucceeded(paymentId, bookingId, amount)` chưa có `rescheduleId`.** Chưa có lượt thu nào của đổi chuyến. Plan 15 thêm trường này cùng với listener của `aftersales`.
3. **`payment` đọc `bookings.code` bằng SQL** để Return URL chuyển về đúng `/bookings/{code}`. Lý do là `payment` không được phụ thuộc `booking` (TDD §3.2). Code hiện có đã làm cách này: `BookingQueries` đọc `flights` và `airports`, `FlightApi` đọc `fare_families`.
4. **`POST /api/bookings/{code}/payments` trả 201 `{paymentUrl}`**, vì mỗi lần gọi tạo một lượt thu mới. TDD §8.3 chỉ ghi là "trả về `paymentUrl`".
5. **Sự kiện mới `booking.TicketEmailRequested(bookingId)` cho FR-73** (TDD §3.3 chưa có). Tách sự kiện này khỏi `BookingIssued` để những listener sau này của "đã xuất vé" không chạy lại khi Staff chỉ bấm gửi lại email.
6. **`booking.BookingApi` mới chỉ có `issued(bookingId)`** để lấy dữ liệu cho email vé. Plan 14 và 15 thêm các hàm khoá và thao tác trên chiều/chặng khi có nơi dùng.
7. **Chưa thêm `VNPAY_API_URL`.** Biến này dành cho Refund API, thuộc Plan 14.

Những điểm TDD không nói, plan tự chọn:

- **Return URL trả `payment=success` khi lượt thu `SUCCESS`**, kể cả khi `IPN` đã xử lý trước (`02`), và kể cả khi booking không được xuất vé do BR-44 (C-09 hiện trạng thái thật của booking). Nếu chữ ký sai hoặc không có lượt thu thì chuyển về `{APP_BASE_URL}/bookings?payment=failed`, vì khi đó chưa biết mã đặt chỗ.
- **Thanh toán đến trong 5 phút ân hạn vẫn xuất vé**, vì booking vẫn `PENDING_PAYMENT`. Ân hạn của BR-45 có mặt chính là để đón trường hợp này.
- **Tạo lượt thu chỉ đọc booking, không khoá.** Nếu khách huỷ đúng lúc đó, lần thanh toán sau sẽ rơi vào BR-44 và vẫn đúng.
- **Chi tiết booking (FR-41, S-02) có thêm `payments`**, gồm mọi lượt thu và lượt hoàn, kể cả lượt `PENDING`, cũ nhất trước. S-02 yêu cầu "toàn bộ lịch sử thu"; C-09 (Plan 13) tự lọc phần cần hiện.
- **Thiếu hash secret thì ứng dụng vẫn khởi động.** Khi đó tạo lượt thu báo 500 và IPN trả `99`, để CI và `docker compose` không có cấu hình VNPay vẫn chạy được các phần khác.

## Review Focus

Những tình huống tài liệu không nói rõ nhưng dễ gây lỗi thật, xếp theo khả năng gặp. Mỗi dòng đã có test ghim lại.

1. **Return URL đến sau IPN** (trường hợp thường gặp nhất khi có tunnel) phải hiện `payment=success`, không phải `failed`, dù lần xử lý thứ hai trả `02` (Task 3 `issuesTicketsExactlyOnceAcrossIpnRetryAndReturn`).
2. **Khách trả cả hai link thanh toán của cùng một booking.** Lần thứ hai không cấp lại số vé, không phát lại `BookingIssued`, mà phát `UnexpectedPaymentReceived` (Task 3 `secondPaymentForIssuedBookingIsReported`).
3. **Khách huỷ booking khi tab VNPay còn mở rồi vẫn trả tiền.** Lượt thu vẫn `SUCCESS` để còn hoàn được, booking vẫn `CANCELLED`, không có số vé (Task 3 `paymentForCancelledBookingIsReportedInsteadOfIssued`).
4. **Thanh toán đến trong 5 phút ân hạn** vẫn xuất vé (Task 3 `paymentArrivingDuringGracePeriodStillIssues`).
5. **VNPay gửi chữ ký chữ hoa, kèm `vnp_SecureHashType` và tham số rỗng.** Callback vẫn được chấp nhận. Còn sửa bất kỳ tham số nào sau khi ký thì bị từ chối (Task 1 `verifiesCallbackSignature`).

---

## Trước khi bắt đầu

- **Cần có:** Docker Desktop đang chạy, Git Bash, JDK 21 trở lên, Node 24. Plan 01–11 đã xong. `.env` có `VNPAY_TMN_CODE` và `VNPAY_HASH_SECRET` thật (chỉ Task 5 cần).
- **Chạy lệnh ở đâu:** Git Bash, từ thư mục gốc repo, trừ khi bước đó ghi `cd backend` hoặc `cd frontend`. Ở PowerShell, đặt đối số `-D...` trong dấu nháy.
- **Cổng PostgreSQL:** `.env` của máy dev dùng `DB_PORT=5433`. Chỉ Task 5 (chạy profile `dev`) cần.
- **Nhánh:** tạo nhánh mới từ `develop`, VD `feat/12-vnpay-payment`.
- **Trạng thái kiểm chứng:** plan viết ngày 2026-10-14 và **chưa được chạy thử**. Mọi tên lớp và hàm đã được đối chiếu với code thật sau Plan 11: `BookingService.issue`, `BookingCancellation.notFound`, `BookingQueries`, `BookingController`, `StaffBookingController`, `BookingIssued`, `Mailer`, `PasswordResetEmail`, `TestBookings`, `TestUsers`, `TestVouchers`, `TestMailSender`, `IntegrationTest`, `V1__init.sql`. Bộ chữ ký mẫu ở Task 1 được tính bằng Java và đối chiếu lại bằng `openssl dgst -sha512 -hmac`. Nếu một bước đỏ ngoài dự kiến, sửa code cho đúng ý định của bước, đừng nới test.

### Ba điều cần biết khi viết test cho plan này

- **Listener `@ApplicationModuleListener` không chạy trong test `@Transactional`**, vì nó chỉ chạy sau commit. T3 đếm sự kiện bằng `@RecordApplicationEvents`. Test email gọi thẳng `TicketEmail.send`, giống cách job hết hạn được gọi thẳng ở Plan 11.
- **Listener của booking cho `PaymentSucceeded` là `@EventListener` đồng bộ**, nên chạy trong transaction của test. Gọi IPN qua MockMvc xong là booking đã `ISSUED` ngay, không cần chờ.
- **Callback giả lập ký bằng chính bean `Vnpay`** (`TestVnpay.callback`). Thuật toán ký được ghim riêng bằng bộ số mẫu tính độc lập ở `VnpayTest`, nên test tích hợp không phải tự cài lại HMAC.

## Cấu trúc file sau plan

```
.env.example                                                 # Task 1
backend/src/
├── main/
│   ├── resources/
│   │   ├── application.properties                          # Task 1: app.vnpay.*
│   │   └── templates/email/ticket.html                     # Task 4
│   └── java/vn/edu/uit/flightbooking/
│       ├── payment/
│       │   ├── PaymentApi.java                             # Task 2
│       │   ├── PaymentSucceeded.java                       # Task 3
│       │   ├── domain/Vnpay.java                           # Task 1
│       │   ├── domain/PaymentCallbackService.java          # Task 3
│       │   └── web/VnpayCallbackController.java            # Task 3
│       ├── booking/
│       │   ├── BookingApi.java                             # Task 4
│       │   ├── UnexpectedPaymentReceived.java              # Task 3
│       │   ├── TicketEmailRequested.java                   # Task 4
│       │   ├── domain/BookingPayments.java                 # Task 2, Task 3 (thêm listener)
│       │   ├── domain/BookingQueries.java                  # Task 2 (thêm payments)
│       │   ├── domain/BookingService.java                  # Task 4 (thêm resendTicketEmail)
│       │   ├── web/BookingController.java                  # Task 2 (thay toàn bộ)
│       │   ├── web/PaymentUrlResponse.java                 # Task 2
│       │   └── web/StaffBookingController.java             # Task 4 (thay toàn bộ)
│       └── notification/domain/TicketEmail.java            # Task 4
└── test/java/vn/edu/uit/flightbooking/
    ├── IntegrationTest.java                                # Task 1 (thêm properties)
    ├── payment/TestVnpay.java                              # Task 1
    ├── payment/domain/VnpayTest.java                       # Task 1
    ├── payment/web/VnpayCallbackTest.java                  # Task 3 (T3, T4)
    ├── booking/web/BookingPaymentTest.java                 # Task 2
    ├── booking/web/ResendTicketEmailTest.java              # Task 4
    └── notification/domain/TicketEmailTest.java            # Task 4
frontend/src/lib/api-types.ts                               # Task 5
docs/superpowers/plans/2026-10-09-00-roadmap.md, CLAUDE.md  # Task 5
```

## API sau plan

| Method | Đường dẫn                                        | Quyền             | Thành công                      | Lỗi                                      |
| ------ | ------------------------------------------------ | ----------------- | ------------------------------- | ---------------------------------------- |
| POST   | `/api/bookings/{code}/payments`                  | Chủ               | 201 `{paymentUrl}`              | 404, 409 `HOLD_EXPIRED`, `INVALID_STATE` |
| GET    | `/api/payments/vnpay/ipn`                        | Công khai, chữ ký | 200 `{RspCode, Message}`        | luôn 200, lỗi nằm trong `RspCode`        |
| GET    | `/api/payments/vnpay/return`                     | Công khai, chữ ký | 302 `/bookings/{code}?payment=` |                                          |
| POST   | `/api/staff/bookings/{code}/resend-ticket-email` | Staff             | 204                             | 404, 409 `INVALID_STATE`                 |

`GET /api/bookings/{code}` và `GET /api/staff/bookings/{code}` có thêm mảng `payments`. `SecurityConfig` đã có `/api/payments/vnpay/**` công khai, `/api/bookings/**` cho `CUSTOMER` và `/api/staff/**` cho `STAFF`, nên không phải sửa. Callback là `GET` nên không cần CSRF (TDD §4.2).

---

### Task 1: Ký và kiểm chữ ký VNPay

**Files:**

- Create: `backend/src/main/java/vn/edu/uit/flightbooking/payment/domain/Vnpay.java`
- Modify: `backend/src/main/resources/application.properties` (thêm 4 dòng cuối)
- Modify: `.env.example` (thêm khối VNPay)
- Modify: `backend/src/test/java/vn/edu/uit/flightbooking/IntegrationTest.java`
- Create: `backend/src/test/java/vn/edu/uit/flightbooking/payment/TestVnpay.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/payment/domain/VnpayTest.java`

**Interfaces:**

- Consumes: không có.
- Produces:
  - `payment.domain.Vnpay` (`@Component`, public): `String payUrl(String txnRef, long amount, String orderInfo, String clientIp, Instant createdAt, Instant expiresAt)`, `boolean verify(Map<String, String> params)`, `String hash(Map<String, String> params)` (tự lọc tham số được ký).
  - Test: `payment.TestVnpay.TMN_CODE = "TESTTMN1"`, `SECRET`, `query(String url) → Map<String, String>` (đã giải mã), `paymentUrl(mvc, customer, code) → String`, `start(mvc, customer, code) → String txnRef`, `callback(Vnpay, txnRef, long amount, String responseCode) → LinkedMultiValueMap<String, String>` (đã ký).

- [ ] **Step 1: Viết test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/payment/domain/VnpayTest.java`:

```java
package vn.edu.uit.flightbooking.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import vn.edu.uit.flightbooking.payment.TestVnpay;

/**
 * Chữ ký VNPay 2.1.0 (TDD §6.3). {@link #HASH} được tính độc lập bằng
 * {@code printf '%s' "$QUERY" | openssl dgst -sha512 -hmac "$SECRET"}, nên test ghim đúng thuật toán của VNPay chứ
 * không chỉ so code với chính nó.
 */
class VnpayTest {

	static final Vnpay VNPAY = new Vnpay(TestVnpay.TMN_CODE, TestVnpay.SECRET,
			"https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", "http://localhost:3000/api/payments/vnpay/return");

	static final String QUERY = "vnp_Amount=302000000&vnp_Command=pay&vnp_CreateDate=20261201080000"
			+ "&vnp_CurrCode=VND&vnp_ExpireDate=20261201081500&vnp_IpAddr=127.0.0.1&vnp_Locale=vn"
			+ "&vnp_OrderInfo=Thanh+toan+dat+ve+K7Q2XM&vnp_OrderType=other"
			+ "&vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A3000%2Fapi%2Fpayments%2Fvnpay%2Freturn&vnp_TmnCode=TESTTMN1"
			+ "&vnp_TxnRef=0123456789abcdef0123456789abcdef&vnp_Version=2.1.0";

	static final String HASH = "4c4b04c7b5d5b05d14c194170cb123823948ac9f27361f44a083eb1d73a1bd4d"
			+ "a767a3b399f82ed857fc7e05c8d8df76249382a8326ce4e5264fa9648b77a7f0";

	/** 08:00 và 08:15 giờ Việt Nam; số tiền nhân 100; khoảng trắng thành {@code +}, ký tự đặc biệt thành {@code %XX}. */
	@Test
	void buildsSignedPaymentUrl() {
		String url = VNPAY.payUrl("0123456789abcdef0123456789abcdef", 3_020_000, "Thanh toan dat ve K7Q2XM",
				"127.0.0.1", Instant.parse("2026-12-01T01:00:00Z"), Instant.parse("2026-12-01T01:15:00Z"));

		assertThat(url)
			.isEqualTo("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?" + QUERY + "&vnp_SecureHash=" + HASH);
	}

	/** Chữ ký chữ hoa, {@code vnp_SecureHashType} và tham số rỗng không ảnh hưởng; sửa một tham số thì sai chữ ký. */
	@Test
	void verifiesCallbackSignature() {
		Map<String, String> params = new HashMap<>(Map.ofEntries(Map.entry("vnp_Amount", "302000000"),
				Map.entry("vnp_Command", "pay"), Map.entry("vnp_CreateDate", "20261201080000"),
				Map.entry("vnp_CurrCode", "VND"), Map.entry("vnp_ExpireDate", "20261201081500"),
				Map.entry("vnp_IpAddr", "127.0.0.1"), Map.entry("vnp_Locale", "vn"),
				Map.entry("vnp_OrderInfo", "Thanh toan dat ve K7Q2XM"), Map.entry("vnp_OrderType", "other"),
				Map.entry("vnp_ReturnUrl", "http://localhost:3000/api/payments/vnpay/return"),
				Map.entry("vnp_TmnCode", "TESTTMN1"), Map.entry("vnp_TxnRef", "0123456789abcdef0123456789abcdef"),
				Map.entry("vnp_Version", "2.1.0")));
		params.put("vnp_SecureHash", HASH);
		assertThat(VNPAY.verify(params)).isTrue();

		params.put("vnp_SecureHash", HASH.toUpperCase(Locale.ROOT));
		params.put("vnp_SecureHashType", "HmacSHA512");
		params.put("vnp_CardType", "");
		assertThat(VNPAY.verify(params)).isTrue();

		params.put("vnp_Amount", "302000100");
		assertThat(VNPAY.verify(params)).isFalse();
		params.put("vnp_Amount", "302000000");
		params.remove("vnp_SecureHash");
		assertThat(VNPAY.verify(params)).isFalse();
	}

}
```

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/payment/TestVnpay.java`:

```java
package vn.edu.uit.flightbooking.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import com.jayway.jsonpath.JsonPath;

import vn.edu.uit.flightbooking.payment.domain.Vnpay;

/** Cấu hình VNPay của test (đặt ở {@code @IntegrationTest}) và các hàm giả lập phía VNPay. */
public final class TestVnpay {

	public static final String TMN_CODE = "TESTTMN1";

	public static final String SECRET = "TESTSECRETTESTSECRETTESTSECRET12";

	private TestVnpay() {
	}

	/** Tham số trên URL thanh toán, đã giải mã ({@code +} thành khoảng trắng). */
	public static Map<String, String> query(String url) {
		Map<String, String> params = new TreeMap<>();
		UriComponentsBuilder.fromUriString(url)
			.build()
			.getQueryParams()
			.forEach((key, values) -> params.put(key, URLDecoder.decode(values.getFirst(), StandardCharsets.UTF_8)));
		return params;
	}

	/** POST /api/bookings/{code}/payments (phải trả 201), trả URL thanh toán. */
	public static String paymentUrl(MockMvc mvc, MockHttpSession customer, String code) throws Exception {
		String json = mvc.perform(post("/api/bookings/{code}/payments", code).with(csrf(mvc)).session(customer))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(json, "$.paymentUrl");
	}

	/** Tạo một lượt thu, trả {@code vnp_TxnRef} của lượt đó. */
	public static String start(MockMvc mvc, MockHttpSession customer, String code) throws Exception {
		return query(paymentUrl(mvc, customer, code)).get("vnp_TxnRef");
	}

	/**
	 * Tham số VNPay gửi về IPN và Return URL, ký bằng secret của test. {@code responseCode} {@code "00"} đi kèm
	 * {@code vnp_TransactionStatus = 00} (thành công); mã khác (VD {@code "24"}: khách huỷ) kèm {@code "02"}.
	 * {@code amount} tính bằng đồng, hàm tự nhân 100.
	 */
	public static LinkedMultiValueMap<String, String> callback(Vnpay vnpay, String txnRef, long amount,
			String responseCode) {
		Map<String, String> params = new TreeMap<>();
		params.put("vnp_Amount", String.valueOf(amount * 100));
		params.put("vnp_BankCode", "NCB");
		params.put("vnp_CardType", "ATM");
		params.put("vnp_OrderInfo", "Thanh toan dat ve");
		params.put("vnp_PayDate", "20261201081000");
		params.put("vnp_ResponseCode", responseCode);
		params.put("vnp_TmnCode", TMN_CODE);
		params.put("vnp_TransactionNo", "14000001");
		params.put("vnp_TransactionStatus", "00".equals(responseCode) ? "00" : "02");
		params.put("vnp_TxnRef", txnRef);
		params.put("vnp_SecureHashType", "HmacSHA512");
		params.put("vnp_SecureHash", vnpay.hash(params));
		LinkedMultiValueMap<String, String> result = new LinkedMultiValueMap<>();
		params.forEach(result::add);
		return result;
	}

}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
cd backend && ./mvnw test -Dtest=VnpayTest
```

Expected: `COMPILATION ERROR`, `cannot find symbol: class Vnpay`.

- [ ] **Step 3: Cài `Vnpay`**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/payment/domain/Vnpay.java`:

```java
package vn.edu.uit.flightbooking.payment.domain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tạo URL thanh toán và kiểm chữ ký VNPay 2.1.0 (TDD §6.3), theo code mẫu của VNPay: ký các tham số {@code vnp_*}
 * khác rỗng (trừ {@code vnp_SecureHash}, {@code vnp_SecureHashType}), sắp theo tên, nối {@code key=value} bằng
 * {@code &} với key và value URL-encode US-ASCII, rồi HMAC-SHA512 bằng hash secret.
 */
@Component
public class Vnpay {

	/** {@code vnp_CreateDate}, {@code vnp_ExpireDate}: yyyyMMddHHmmss theo GMT+7. */
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
		.withZone(ZoneOffset.ofHours(7));

	private final String tmnCode;

	private final String hashSecret;

	private final String payUrl;

	private final String returnUrl;

	public Vnpay(@Value("${app.vnpay.tmn-code}") String tmnCode, @Value("${app.vnpay.hash-secret}") String hashSecret,
			@Value("${app.vnpay.pay-url}") String payUrl, @Value("${app.vnpay.return-url}") String returnUrl) {
		this.tmnCode = tmnCode;
		this.hashSecret = hashSecret;
		this.payUrl = payUrl;
		this.returnUrl = returnUrl;
	}

	/** FR-30: {@code amount} tính bằng đồng; link hết hạn lúc {@code expiresAt} (BR-41). {@code orderInfo} không dấu. */
	public String payUrl(String txnRef, long amount, String orderInfo, String clientIp, Instant createdAt,
			Instant expiresAt) {
		Map<String, String> params = new TreeMap<>();
		params.put("vnp_Version", "2.1.0");
		params.put("vnp_Command", "pay");
		params.put("vnp_TmnCode", tmnCode);
		params.put("vnp_Amount", String.valueOf(amount * 100));
		params.put("vnp_CurrCode", "VND");
		params.put("vnp_TxnRef", txnRef);
		params.put("vnp_OrderInfo", orderInfo);
		params.put("vnp_OrderType", "other");
		params.put("vnp_Locale", "vn");
		params.put("vnp_ReturnUrl", returnUrl);
		params.put("vnp_IpAddr", clientIp);
		params.put("vnp_CreateDate", TIME.format(createdAt));
		params.put("vnp_ExpireDate", TIME.format(expiresAt));
		return payUrl + "?" + query(params) + "&vnp_SecureHash=" + hash(params);
	}

	/** Bước 1 của TDD §6.3: so chữ ký không phân biệt hoa thường, thời gian so sánh không phụ thuộc nội dung. */
	public boolean verify(Map<String, String> params) {
		String received = params.get("vnp_SecureHash");
		return received != null && MessageDigest.isEqual(hash(params).getBytes(StandardCharsets.US_ASCII),
				received.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
	}

	/** Chữ ký hex chữ thường của các tham số được ký trong {@code params}; tham số khác bị bỏ qua. */
	public String hash(Map<String, String> params) {
		Map<String, String> signed = new TreeMap<>();
		params.forEach((key, value) -> {
			if (key.startsWith("vnp_") && !key.equals("vnp_SecureHash") && !key.equals("vnp_SecureHashType")
					&& value != null && !value.isEmpty()) {
				signed.put(key, value);
			}
		});
		try {
			Mac mac = Mac.getInstance("HmacSHA512");
			mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
			return HexFormat.of().formatHex(mac.doFinal(query(signed).getBytes(StandardCharsets.US_ASCII)));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String query(Map<String, String> sorted) {
		return sorted.entrySet()
			.stream()
			.map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
			.collect(Collectors.joining("&"));
	}

	private static String encode(String text) {
		return URLEncoder.encode(text, StandardCharsets.US_ASCII);
	}

}
```

- [ ] **Step 4: Cấu hình**

Thêm vào cuối `backend/src/main/resources/application.properties`:

```properties
# VNPay sandbox (TDD §6.3, §10.1). Mã và secret nằm trong .env; thiếu secret thì app vẫn chạy, chỉ thanh toán báo lỗi.
app.vnpay.tmn-code=${VNPAY_TMN_CODE:}
app.vnpay.hash-secret=${VNPAY_HASH_SECRET:}
app.vnpay.pay-url=${VNPAY_PAY_URL:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}
app.vnpay.return-url=${VNPAY_RETURN_URL:http://localhost:3000/api/payments/vnpay/return}
```

Thêm vào cuối `.env.example`:

```properties

# VNPay sandbox (TDD §6.3). Đăng ký tại https://sandbox.vnpayment.vn/devreg/, VNPay gửi mã và secret qua email.
# Return URL đi qua Next.js (cổng 3000), nên khi thử thanh toán phải chạy cả frontend.
VNPAY_TMN_CODE=
VNPAY_HASH_SECRET=
VNPAY_PAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=http://localhost:3000/api/payments/vnpay/return
```

Trong `backend/src/test/java/vn/edu/uit/flightbooking/IntegrationTest.java`, thêm import `vn.edu.uit.flightbooking.payment.TestVnpay;` rồi thay khối Javadoc và annotation `@SpringBootTest`:

```java
/**
 * Mọi integration test dùng chung annotation này để Spring cache một context
 * và chỉ khởi động một container PostgreSQL cho cả bộ test. Email gửi đi được giữ trong {@link TestMailSender}.
 * VNPay dùng mã và secret riêng của test ({@link TestVnpay}), không đọc {@code .env}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = { "app.vnpay.tmn-code=" + TestVnpay.TMN_CODE,
		"app.vnpay.hash-secret=" + TestVnpay.SECRET })
```

- [ ] **Step 5: Chạy test, xác nhận xanh**

```bash
cd backend && ./mvnw test -Dtest='VnpayTest,SecurityConfigTest'
```

Expected: `BUILD SUCCESS`. `VnpayTest` có 2 test, `SecurityConfigTest` chứng tỏ context vẫn khởi động được với cấu hình mới.

- [ ] **Step 6: Commit**

```bash
git add .env.example backend/src/main/resources/application.properties \
  backend/src/main/java/vn/edu/uit/flightbooking/payment/domain/Vnpay.java \
  backend/src/test/java/vn/edu/uit/flightbooking/IntegrationTest.java \
  backend/src/test/java/vn/edu/uit/flightbooking/payment
git commit -m "feat(payment): ký và kiểm chữ ký VNPay 2.1.0 (TDD §6.3)"
```

---

### Task 2: Tạo lượt thu (FR-30, BR-41) và lịch sử thu trong chi tiết booking

**Files:**

- Create: `backend/src/main/java/vn/edu/uit/flightbooking/payment/PaymentApi.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingPayments.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/PaymentUrlResponse.java`
- Modify (thay toàn bộ): `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/BookingController.java`
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingQueries.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/booking/web/BookingPaymentTest.java`

**Interfaces:**

- Consumes: `Vnpay.payUrl` (Task 1); `BookingCancellation.notFound()`, `BookingStatus` (Plan 11); `TestBookings`, `TestUsers`, `TestVouchers`, `TestVnpay.query` (Task 1).
- Produces:
  - `payment.PaymentApi.charge(long bookingId, long amount, Instant expiresAt, String orderInfo, String clientIp) → String paymentUrl`: ghi `payments` (`CHARGE`, `VNPAY`, `PENDING`, `txn_ref` UUID không gạch nối, `expires_at`).
  - `booking.domain.BookingPayments.start(long userId, String code, String clientIp) → String paymentUrl`.
  - `BookingQueries.PaymentView(kind, status, amount, createdAt, completedAt)`; `BookingDetail` có thêm trường cuối `List<PaymentView> payments`.
  - `POST /api/bookings/{code}/payments` → 201 `PaymentUrlResponse(paymentUrl)`.

- [ ] **Step 1: Viết test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/booking/web/BookingPaymentTest.java`:

```java
package vn.edu.uit.flightbooking.booking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.jayway.jsonpath.JsonPath;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.payment.TestVnpay;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;
import vn.edu.uit.flightbooking.promotion.TestVouchers;

/** FR-30, BR-41, FR-41 (lịch sử thu): POST /api/bookings/{code}/payments. Một người lớn, một chiều: 3.020.000. */
@IntegrationTest
@Transactional
class BookingPaymentTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	Vnpay vnpay;

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

	ResultActions pay(MockHttpSession session, String code) throws Exception {
		return mvc.perform(post("/api/bookings/{code}/payments", code).with(csrf(mvc)).session(session));
	}

	/** Số lượt thu của mọi booking thuộc {@link #customer}. */
	int payments() {
		return jdbc.sql("SELECT count(*) FROM payments p JOIN bookings b ON b.id = p.booking_id WHERE b.user_id = ?")
			.param(customer.id())
			.query(Integer.class)
			.single();
	}

	/**
	 * TDD §6.3: lượt thu PENDING đúng số tiền, URL ký đúng, IP lấy từ X-Forwarded-For, link hết hạn cùng hạn giữ
	 * chỗ. BR-41: tạo lại được (thử lại), mỗi lần một {@code txn_ref} mới; mã chữ thường vẫn tìm thấy.
	 */
	@Test
	void createsSignedPaymentUrlThatExpiresWithTheHold() throws Exception {
		String code = hold(Map.of());

		String json = mvc
			.perform(post("/api/bookings/{code}/payments", code).with(csrf(mvc))
				.session(customer.session())
				.header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();

		String url = JsonPath.read(json, "$.paymentUrl");
		assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
		Map<String, String> params = TestVnpay.query(url);
		assertThat(vnpay.verify(params)).isTrue();
		assertThat(params).containsEntry("vnp_Version", "2.1.0")
			.containsEntry("vnp_Command", "pay")
			.containsEntry("vnp_TmnCode", TestVnpay.TMN_CODE)
			.containsEntry("vnp_Amount", "302000000")
			.containsEntry("vnp_CurrCode", "VND")
			.containsEntry("vnp_OrderInfo", "Thanh toan dat ve " + code)
			.containsEntry("vnp_OrderType", "other")
			.containsEntry("vnp_Locale", "vn")
			.containsEntry("vnp_ReturnUrl", "http://localhost:3000/api/payments/vnpay/return")
			.containsEntry("vnp_IpAddr", "203.0.113.7");
		assertThat(params.get("vnp_TxnRef")).matches("[0-9a-f]{32}");
		assertThat(params.get("vnp_ExpireDate")).isEqualTo(jdbc.sql("""
				SELECT to_char(hold_expires_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYYMMDDHH24MISS')
				FROM bookings WHERE code = ?
				""").param(code).query(String.class).single());
		assertThat(jdbc.sql("""
				SELECT p.kind, p.provider, p.status, p.amount, p.expires_at = b.hold_expires_at AS same_expiry,
				       p.completed_at
				FROM payments p JOIN bookings b ON b.id = p.booking_id
				WHERE p.txn_ref = ?
				""").param(params.get("vnp_TxnRef")).query().singleRow())
			.containsEntry("kind", "CHARGE")
			.containsEntry("provider", "VNPAY")
			.containsEntry("status", "PENDING")
			.containsEntry("amount", 3_020_000L)
			.containsEntry("same_expiry", true)
			.containsEntry("completed_at", null);

		String retry = TestVnpay.start(mvc, customer.session(), code.toLowerCase());
		assertThat(retry).isNotEqualTo(params.get("vnp_TxnRef"));
		assertThat(payments()).isEqualTo(2);
	}

	/** FR-30, BR-41: quá hạn giữ chỗ là HOLD_EXPIRED; đã huỷ hoặc đã xuất vé là INVALID_STATE; không ghi lượt thu. */
	@Test
	void refusesWhenHoldIsOverOrBookingIsNotPending() throws Exception {
		String overdue = hold(Map.of());
		jdbc.sql("UPDATE bookings SET hold_expires_at = now() - INTERVAL '1 minute' WHERE code = ?")
			.param(overdue)
			.update();
		String expired = hold(Map.of());
		jdbc.sql("UPDATE bookings SET status = 'EXPIRED' WHERE code = ?").param(expired).update();
		String cancelled = hold(Map.of());
		mvc.perform(post("/api/bookings/{code}/cancel", cancelled).with(csrf(mvc)).session(customer.session()))
			.andExpect(status().isNoContent());
		TestVouchers.insert(jdbc, "QVFREE", Map.of("discount_value", 100));
		String issued = hold(Map.of("voucherCode", "QVFREE"));

		pay(customer.session(), overdue).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
		pay(customer.session(), expired).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
		pay(customer.session(), cancelled).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
		pay(customer.session(), issued).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
		assertThat(payments()).isZero();
	}

	/** BR-104: booking của người khác như không tồn tại. */
	@Test
	void otherCustomersGetNotFound() throws Exception {
		String code = hold(Map.of());

		pay(TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER).session(), code).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		pay(customer.session(), "ZZZZZZ").andExpect(status().isNotFound());
		assertThat(payments()).isZero();
	}

	/** FR-41, S-02: chi tiết booking liệt kê lượt thu, kể cả lượt chưa có kết quả. */
	@Test
	void detailListsPayments() throws Exception {
		String code = hold(Map.of());
		pay(customer.session(), code).andExpect(status().isCreated());

		mvc.perform(get("/api/bookings/{code}", code).session(customer.session()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.payments.length()").value(1))
			.andExpect(jsonPath("$.payments[0].kind").value("CHARGE"))
			.andExpect(jsonPath("$.payments[0].status").value("PENDING"))
			.andExpect(jsonPath("$.payments[0].amount").value(3_020_000))
			.andExpect(jsonPath("$.payments[0].createdAt").isNotEmpty())
			.andExpect(jsonPath("$.payments[0].completedAt").value(nullValue()));
	}

}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
cd backend && ./mvnw test -Dtest=BookingPaymentTest
```

Expected: `Tests run: 4, Failures: 4`. `POST /api/bookings/{code}/payments` chưa có nên Spring trả 404 với `code = VALIDATION_FAILED` (quy ước Plan 01 cho lỗi 4xx của Spring MVC). Vì vậy test chờ 201 hoặc 409 đỏ, và `otherCustomersGetNotFound` cũng đỏ ở bước kiểm `$.code = RESOURCE_NOT_FOUND`.

- [ ] **Step 3: `PaymentApi`**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/payment/PaymentApi.java`:

```java
package vn.edu.uit.flightbooking.payment;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.payment.domain.Vnpay;

/**
 * API công khai của payment (TDD §3.2): tạo lượt thu VNPay. Kết quả thanh toán báo về bằng {@link PaymentSucceeded}.
 * Plan 14 thêm hoàn tiền và liệt kê lượt thu còn hoàn được.
 */
@Service
public class PaymentApi {

	private static final String INSERT_CHARGE = """
			INSERT INTO payments (booking_id, kind, provider, status, amount, txn_ref, expires_at)
			VALUES (:bookingId, 'CHARGE', 'VNPAY', 'PENDING', :amount, :txnRef, :expiresAt)
			""";

	private final JdbcClient jdbc;

	private final Vnpay vnpay;

	PaymentApi(JdbcClient jdbc, Vnpay vnpay) {
		this.jdbc = jdbc;
		this.vnpay = vnpay;
	}

	/**
	 * FR-30, TDD §6.3: ghi lượt thu PENDING và trả URL thanh toán VNPay hết hạn lúc {@code expiresAt} (BR-41). Bên gọi
	 * tự kiểm tra trạng thái và hạn giữ chỗ. {@code orderInfo} phải không dấu.
	 */
	@Transactional
	public String charge(long bookingId, long amount, Instant expiresAt, String orderInfo, String clientIp) {
		String txnRef = UUID.randomUUID().toString().replace("-", "");
		jdbc.sql(INSERT_CHARGE)
			.param("bookingId", bookingId)
			.param("amount", amount)
			.param("txnRef", txnRef)
			.param("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))
			.update();
		return vnpay.payUrl(txnRef, amount, orderInfo, clientIp, Instant.now(), expiresAt);
	}

}
```

- [ ] **Step 4: `BookingPayments.start`**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingPayments.java`:

```java
package vn.edu.uit.flightbooking.booking.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.payment.PaymentApi;

/** Thanh toán booking (FR-30, BR-41): tạo lượt thu qua {@link PaymentApi}. */
@Service
public class BookingPayments {

	private record Payable(long id, long userId, BookingStatus status, long totalAmount, Instant holdExpiresAt) {
	}

	private final PaymentApi payments;

	private final JdbcClient jdbc;

	BookingPayments(PaymentApi payments, JdbcClient jdbc) {
		this.payments = payments;
		this.jdbc = jdbc;
	}

	/**
	 * FR-30, BR-41: chỉ booking PENDING_PAYMENT còn trong hạn giữ chỗ; khách thử lại được nhiều lần. Không khoá booking:
	 * nếu booking bị huỷ ngay sau đó, khoản tiền trả về sau rơi vào BR-44.
	 */
	@Transactional
	public String start(long userId, String code, String clientIp) {
		String upper = code.toUpperCase(Locale.ROOT);
		Payable booking = jdbc.sql("""
				SELECT id, user_id, status, total_amount, hold_expires_at FROM bookings WHERE code = ?
				""")
			.param(upper)
			.query((rs, rowNum) -> new Payable(rs.getLong("id"), rs.getLong("user_id"),
					BookingStatus.valueOf(rs.getString("status")), rs.getLong("total_amount"),
					rs.getObject("hold_expires_at", OffsetDateTime.class).toInstant()))
			.optional()
			.filter(b -> b.userId() == userId)
			.orElseThrow(BookingCancellation::notFound);
		boolean pending = booking.status() == BookingStatus.PENDING_PAYMENT;
		if (booking.status() == BookingStatus.EXPIRED || pending && !Instant.now().isBefore(booking.holdExpiresAt())) {
			throw new BusinessException(ErrorCode.HOLD_EXPIRED, "Booking đã quá hạn giữ chỗ, vui lòng đặt lại");
		}
		if (!pending) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "Chỉ thanh toán được booking đang chờ thanh toán");
		}
		return payments.charge(booking.id(), booking.totalAmount(), booking.holdExpiresAt(),
				"Thanh toan dat ve " + upper, clientIp);
	}

}
```

- [ ] **Step 5: Controller**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/PaymentUrlResponse.java`:

```java
package vn.edu.uit.flightbooking.booking.web;

/** Phản hồi 201 của POST /bookings/{code}/payments: frontend chuyển hướng người dùng tới URL này (TDD §6.3). */
public record PaymentUrlResponse(String paymentUrl) {
}
```

Thay toàn bộ `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/BookingController.java` bằng:

```java
package vn.edu.uit.flightbooking.booking.web;

import jakarta.servlet.http.HttpServletRequest;
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
import vn.edu.uit.flightbooking.booking.domain.BookingPayments;
import vn.edu.uit.flightbooking.booking.domain.BookingQueries;
import vn.edu.uit.flightbooking.booking.domain.BookingQueries.BookingDetail;
import vn.edu.uit.flightbooking.booking.domain.BookingQueries.BookingSummary;
import vn.edu.uit.flightbooking.booking.domain.BookingRequest;
import vn.edu.uit.flightbooking.booking.domain.BookingService;
import vn.edu.uit.flightbooking.booking.domain.BookingStatus;
import vn.edu.uit.flightbooking.booking.domain.QuoteService;
import vn.edu.uit.flightbooking.common.CurrentUser;

/**
 * Màn hình C-07, C-08, C-09: báo giá (FR-20), giữ chỗ (FR-24), huỷ giữ chỗ (FR-33), thanh toán (FR-30), danh sách
 * (FR-40) và chi tiết (FR-41). Quyền CUSTOMER theo đường dẫn ở SecurityConfig; quyền chủ booking kiểm tra trong
 * service (BR-104).
 */
@RestController
@RequestMapping("/api/bookings")
class BookingController {

	private final QuoteService quotes;

	private final BookingService bookings;

	private final BookingCancellation cancellation;

	private final BookingPayments payments;

	private final BookingQueries queries;

	BookingController(QuoteService quotes, BookingService bookings, BookingCancellation cancellation,
			BookingPayments payments, BookingQueries queries) {
		this.quotes = quotes;
		this.bookings = bookings;
		this.cancellation = cancellation;
		this.payments = payments;
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

	@PostMapping("/{code}/payments")
	@ResponseStatus(HttpStatus.CREATED)
	PaymentUrlResponse pay(@AuthenticationPrincipal CurrentUser me, @PathVariable String code,
			HttpServletRequest request) {
		return new PaymentUrlResponse(payments.start(me.id(), code, clientIp(request)));
	}

	/** {@code vnp_IpAddr} (TDD §6.3): IP đầu tiên trong X-Forwarded-For (Next.js chuyển tiếp), không có thì IP kết nối. */
	private static String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].strip();
	}

}
```

- [ ] **Step 6: Lịch sử thu trong chi tiết booking**

Sửa `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingQueries.java`.

(a) Trong Javadoc của lớp, thay câu `Đọc thẳng bảng {@code flights},

- {@code airports} (flight, catalog) và {@code vouchers} (promotion) bằng SQL để lấy số hiệu, múi giờ, mã voucher.` bằng:

```java
 * Đọc thẳng bảng {@code flights},
 * {@code airports} (flight, catalog), {@code vouchers} (promotion) và {@code payments} (payment) bằng SQL để lấy số
 * hiệu, múi giờ, mã voucher và lịch sử thu.
```

(b) Thay record `BookingDetail` và comment ngay trên nó bằng:

```java
	/** Mọi lượt thu và lượt hoàn của booking (FR-41, S-02), cũ nhất trước; giờ theo giờ Việt Nam. */
	public record PaymentView(String kind, String status, long amount, OffsetDateTime createdAt,
			OffsetDateTime completedAt) {
	}

	/** Yêu cầu hoàn và đổi chuyến do Plan 14, 15 thêm. */
	public record BookingDetail(String code, BookingStatus status, OffsetDateTime holdExpiresAt,
			OffsetDateTime issuedAt, OffsetDateTime createdAt, Contact contact, String voucherCode, long fareTotal,
			long baggageTotal, long discountTotal, long totalAmount, List<JourneyView> journeys,
			List<PassengerView> passengers, List<PaymentView> payments) {
	}
```

(c) Thêm hằng số ngay sau hằng số `BAGGAGE`:

```java
	private static final String PAYMENTS = """
			SELECT kind, status, amount, created_at, completed_at
			FROM payments
			WHERE booking_id = :id
			ORDER BY created_at, id
			""";
```

(d) Trong hàm `private BookingDetail detail(Row booking)`, thêm ngay sau khối đọc `baggage` (trước dòng `Map<Long, Integer> passengerIndex = new HashMap<>();`):

```java
		List<PaymentView> payments = jdbc.sql(PAYMENTS)
			.params(id)
			.query((rs, n) -> new PaymentView(rs.getString("kind"), rs.getString("status"), rs.getLong("amount"),
					vietnam(rs, "created_at"), vietnam(rs, "completed_at")))
			.list();
```

(e) Thay câu `return` cuối của hàm đó bằng:

```java
		return new BookingDetail(booking.code(), booking.status(), booking.holdExpiresAt(), booking.issuedAt(),
				booking.createdAt(), booking.contact(), booking.voucherCode(), booking.fareTotal(),
				booking.baggageTotal(), booking.discountTotal(), booking.totalAmount(), journeyViews, passengerViews,
				payments);
```

- [ ] **Step 7: Chạy test, xác nhận xanh**

```bash
cd backend && ./mvnw test -Dtest='BookingPaymentTest,BookingQueriesTest,ModularityTest'
```

Expected: `BUILD SUCCESS`. `ModularityTest` chấp nhận phụ thuộc mới `booking → payment`. `BookingQueriesTest` của Plan 11 vẫn xanh, vì trường mới nằm ở cuối.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/payment/PaymentApi.java \
  backend/src/main/java/vn/edu/uit/flightbooking/booking \
  backend/src/test/java/vn/edu/uit/flightbooking/booking/web/BookingPaymentTest.java
git commit -m "feat(payment): tạo lượt thu VNPay cho booking và lịch sử thu trong chi tiết (FR-30, BR-41, FR-41)"
```

---

### Task 3: IPN, Return URL và xuất vé (FR-31, FR-32, BR-42–44, T3, T4)

**Files:**

- Create: `backend/src/main/java/vn/edu/uit/flightbooking/payment/PaymentSucceeded.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/payment/domain/PaymentCallbackService.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/payment/web/VnpayCallbackController.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/UnexpectedPaymentReceived.java`
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingPayments.java` (thêm listener)
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/payment/web/VnpayCallbackTest.java`

**Interfaces:**

- Consumes: `Vnpay.verify`, `TestVnpay.callback`, `TestVnpay.start` (Task 1); `BookingPayments` (Task 2); `BookingService.issue(long bookingId)` và `BookingIssued` (Plan 11).
- Produces:
  - `payment.PaymentSucceeded(long paymentId, long bookingId, long amount)`: phát trong transaction của callback.
  - `payment.domain.PaymentCallbackService.handle(Map<String, String> params) → Outcome(rspCode, message, bookingCode, paid)`.
  - `booking.UnexpectedPaymentReceived(long paymentId, long bookingId)` (BR-44; Plan 14 thêm listener tạo yêu cầu hoàn `INVALID_PAYMENT`).
  - `GET /api/payments/vnpay/ipn`, `GET /api/payments/vnpay/return`.

- [ ] **Step 1: Viết test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/payment/web/VnpayCallbackTest.java`:

```java
package vn.edu.uit.flightbooking.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.booking.BookingIssued;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.booking.UnexpectedPaymentReceived;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.payment.PaymentSucceeded;
import vn.edu.uit.flightbooking.payment.TestVnpay;
import vn.edu.uit.flightbooking.payment.domain.Vnpay;

/**
 * FR-31, FR-32, BR-42–44, T3, T4. Mỗi test giữ chỗ một người lớn một chiều (3.020.000) và tạo một lượt thu. Listener
 * của booking chạy đồng bộ trong transaction của test, nên gọi IPN xong là thấy kết quả ngay.
 */
@IntegrationTest
@Transactional
@RecordApplicationEvents
class VnpayCallbackTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	Vnpay vnpay;

	@Autowired
	ApplicationEvents events;

	TestUsers.TestUser customer;

	String code;

	String txnRef;

	@BeforeEach
	void setUp() throws Exception {
		TestBookings.Trip trip = TestBookings.trip(mvc, jdbc);
		customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER);
		code = TestBookings.holdCode(mvc, customer.session(), TestBookings.body(trip, Map.of()));
		txnRef = TestVnpay.start(mvc, customer.session(), code);
	}

	ResultActions ipn(MultiValueMap<String, String> params) throws Exception {
		return mvc.perform(get("/api/payments/vnpay/ipn").params(params)).andExpect(status().isOk());
	}

	ResultActions back(MultiValueMap<String, String> params) throws Exception {
		return mvc.perform(get("/api/payments/vnpay/return").params(params)).andExpect(status().isFound());
	}

	/** Kết quả thành công cho lượt thu {@code ref}, đúng số tiền. */
	LinkedMultiValueMap<String, String> paid(String ref) {
		return TestVnpay.callback(vnpay, ref, 3_020_000, "00");
	}

	String bookingStatus() {
		return jdbc.sql("SELECT status FROM bookings WHERE code = ?").param(code).query(String.class).single();
	}

	Map<String, Object> payment(String ref) {
		return jdbc.sql("""
				SELECT id, status, provider_txn_no, provider_txn_date, response_code,
				       raw_response ->> 'vnp_BankCode' AS bank_code, completed_at
				FROM payments WHERE txn_ref = ?
				""").param(ref).query().singleRow();
	}

	long paymentId(String ref) {
		return ((Number) payment(ref).get("id")).longValue();
	}

	List<String> tickets() {
		return jdbc.sql("""
				SELECT t.ticket_number FROM tickets t JOIN passengers p ON p.id = t.passenger_id
				JOIN bookings b ON b.id = p.booking_id WHERE b.code = ? ORDER BY t.id
				""").param(code).query(String.class).list();
	}

	/**
	 * T3, BR-42, BR-43: IPN thành công thì xuất vé; VNPay gửi lại IPN thì trả 02; Return URL đến sau vẫn báo thành
	 * công. Số vé không đổi, PaymentSucceeded và BookingIssued (email vé) chỉ phát một lần.
	 */
	@Test
	void issuesTicketsExactlyOnceAcrossIpnRetryAndReturn() throws Exception {
		ipn(paid(txnRef)).andExpect(jsonPath("$.RspCode").value("00"))
			.andExpect(jsonPath("$.Message").value("Confirm Success"));

		assertThat(bookingStatus()).isEqualTo("ISSUED");
		List<String> numbers = tickets();
		assertThat(numbers).hasSize(2).allMatch(number -> number.matches("901[0-9]{10}"));
		assertThat(payment(txnRef)).containsEntry("status", "SUCCESS")
			.containsEntry("provider_txn_no", "14000001")
			.containsEntry("provider_txn_date", "20261201081000")
			.containsEntry("response_code", "00")
			.containsEntry("bank_code", "NCB");
		assertThat(payment(txnRef).get("completed_at")).isNotNull();

		ipn(paid(txnRef)).andExpect(jsonPath("$.RspCode").value("02"));
		back(paid(txnRef)).andExpect(redirectedUrl("http://localhost:3000/bookings/" + code + "?payment=success"));

		assertThat(tickets()).isEqualTo(numbers);
		assertThat(events.stream(PaymentSucceeded.class)).hasSize(1);
		assertThat(events.stream(BookingIssued.class))
			.containsExactly(new BookingIssued(TestBookings.bookingId(jdbc, code)));
		mvc.perform(get("/api/bookings/{code}", code).session(customer.session()))
			.andExpect(jsonPath("$.status").value("ISSUED"))
			.andExpect(jsonPath("$.payments[0].status").value("SUCCESS"))
			.andExpect(jsonPath("$.payments[0].completedAt").isNotEmpty());
	}

	/** T4: sai chữ ký (97), thiếu chữ ký (97), không có lượt thu (01), sai số tiền (04); dữ liệu không đổi. */
	@Test
	void rejectsForgedUnknownAndWrongAmountCallbacks() throws Exception {
		LinkedMultiValueMap<String, String> forged = paid(txnRef);
		forged.set("vnp_Amount", "100");
		LinkedMultiValueMap<String, String> unsigned = paid(txnRef);
		unsigned.remove("vnp_SecureHash");

		ipn(forged).andExpect(jsonPath("$.RspCode").value("97"));
		ipn(unsigned).andExpect(jsonPath("$.RspCode").value("97"));
		ipn(paid("f".repeat(32))).andExpect(jsonPath("$.RspCode").value("01"));
		ipn(TestVnpay.callback(vnpay, txnRef, 3_019_000, "00")).andExpect(jsonPath("$.RspCode").value("04"));
		back(forged).andExpect(redirectedUrl("http://localhost:3000/bookings?payment=failed"));

		assertThat(payment(txnRef)).containsEntry("status", "PENDING").containsEntry("completed_at", null);
		assertThat(bookingStatus()).isEqualTo("PENDING_PAYMENT");
		assertThat(tickets()).containsOnlyNulls();
		assertThat(events.stream(PaymentSucceeded.class)).isEmpty();
	}

	/** FR-30: khách huỷ trên VNPay (mã 24) thì lượt thu FAILED, booking vẫn chờ; thử lại bằng lượt thu mới thì được. */
	@Test
	void failedPaymentKeepsBookingPendingAndAllowsRetry() throws Exception {
		LinkedMultiValueMap<String, String> cancelledOnVnpay = TestVnpay.callback(vnpay, txnRef, 3_020_000, "24");
		ipn(cancelledOnVnpay).andExpect(jsonPath("$.RspCode").value("00"));
		back(cancelledOnVnpay)
			.andExpect(redirectedUrl("http://localhost:3000/bookings/" + code + "?payment=failed"));

		assertThat(payment(txnRef)).containsEntry("status", "FAILED").containsEntry("response_code", "24");
		assertThat(payment(txnRef).get("completed_at")).isNotNull();
		assertThat(bookingStatus()).isEqualTo("PENDING_PAYMENT");
		assertThat(events.stream(PaymentSucceeded.class)).isEmpty();

		String retry = TestVnpay.start(mvc, customer.session(), code);
		ipn(paid(retry)).andExpect(jsonPath("$.RspCode").value("00"));
		assertThat(bookingStatus()).isEqualTo("ISSUED");
	}

	/** BR-45: hết hạn giữ chỗ nhưng còn trong 5 phút ân hạn thì booking vẫn chờ, nên thanh toán đến vẫn xuất vé. */
	@Test
	void paymentArrivingDuringGracePeriodStillIssues() throws Exception {
		jdbc.sql("UPDATE bookings SET hold_expires_at = now() - INTERVAL '2 minutes' WHERE code = ?")
			.param(code)
			.update();

		ipn(paid(txnRef)).andExpect(jsonPath("$.RspCode").value("00"));

		assertThat(bookingStatus()).isEqualTo("ISSUED");
	}

	/** BR-44: khách đã huỷ booking rồi vẫn trả tiền. Lượt thu ghi nhận, booking giữ nguyên, phát sự kiện để hoàn. */
	@Test
	void paymentForCancelledBookingIsReportedInsteadOfIssued() throws Exception {
		mvc.perform(post("/api/bookings/{code}/cancel", code).with(csrf(mvc)).session(customer.session()))
			.andExpect(status().isNoContent());

		ipn(paid(txnRef)).andExpect(jsonPath("$.RspCode").value("00"));

		assertThat(payment(txnRef)).containsEntry("status", "SUCCESS");
		assertThat(bookingStatus()).isEqualTo("CANCELLED");
		assertThat(tickets()).containsOnlyNulls();
		assertThat(events.stream(BookingIssued.class)).isEmpty();
		assertThat(events.stream(UnexpectedPaymentReceived.class)).containsExactly(
				new UnexpectedPaymentReceived(paymentId(txnRef), TestBookings.bookingId(jdbc, code)));
	}

	/** BR-44: khách trả cả hai link của cùng một booking. Lần hai không cấp lại số vé mà phát sự kiện để hoàn. */
	@Test
	void secondPaymentForIssuedBookingIsReported() throws Exception {
		String second = TestVnpay.start(mvc, customer.session(), code);
		ipn(paid(txnRef)).andExpect(jsonPath("$.RspCode").value("00"));
		List<String> numbers = tickets();

		ipn(paid(second)).andExpect(jsonPath("$.RspCode").value("00"));

		assertThat(tickets()).isEqualTo(numbers);
		assertThat(events.stream(BookingIssued.class)).hasSize(1);
		assertThat(events.stream(UnexpectedPaymentReceived.class)).containsExactly(
				new UnexpectedPaymentReceived(paymentId(second), TestBookings.bookingId(jdbc, code)));
	}

}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
cd backend && ./mvnw test -Dtest=VnpayCallbackTest
```

Expected: `COMPILATION ERROR`, `cannot find symbol` cho `PaymentSucceeded` và `UnexpectedPaymentReceived`.

- [ ] **Step 3: Hai sự kiện**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/payment/PaymentSucceeded.java`:

```java
package vn.edu.uit.flightbooking.payment;

/**
 * Lượt thu vừa thành công (TDD §3.3, BR-42). Phát trong transaction của callback; listener đồng bộ của booking xuất vé
 * hoặc báo thanh toán ngoài dự kiến (BR-43, BR-44). Plan 15 thêm {@code rescheduleId} cho lượt thu của đổi chuyến.
 */
public record PaymentSucceeded(long paymentId, long bookingId, long amount) {
}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/UnexpectedPaymentReceived.java`:

```java
package vn.edu.uit.flightbooking.booking;

/**
 * BR-44: lượt thu thành công nhưng booking không còn chờ thanh toán (đã hết hạn, đã huỷ, hoặc đã trả bằng lượt thu
 * khác). Phát trong transaction của callback; Plan 14 thêm listener tạo yêu cầu hoàn {@code INVALID_PAYMENT} (TDD §3.3).
 */
public record UnexpectedPaymentReceived(long paymentId, long bookingId) {
}
```

- [ ] **Step 4: `PaymentCallbackService`**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/payment/domain/PaymentCallbackService.java`:

```java
package vn.edu.uit.flightbooking.payment.domain;

import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;
import vn.edu.uit.flightbooking.payment.PaymentSucceeded;

/**
 * Xử lý kết quả VNPay cho cả IPN và Return URL (TDD §6.3, AD-07). Khoá lượt thu trước khi đọc trạng thái, nên cùng một
 * kết quả đến nhiều lần chỉ được xử lý một lần (BR-42). Listener của booking chạy trong cùng transaction và khoá
 * {@code bookings} sau {@code payments} (TDD §5.4).
 */
@Service
public class PaymentCallbackService {

	/**
	 * {@code rspCode}, {@code message}: phản hồi IPN theo VNPay. {@code bookingCode}: {@code null} khi chữ ký sai hoặc
	 * không có lượt thu. {@code paid}: lượt thu đang SUCCESS, kể cả khi đã được xử lý từ trước.
	 */
	public record Outcome(String rspCode, String message, String bookingCode, boolean paid) {
	}

	private record Locked(long id, long bookingId, long amount, String status, String bookingCode) {
	}

	/**
	 * Đọc {@code bookings.code} để Return URL về đúng trang booking: payment không phụ thuộc module booking (TDD
	 * §3.2), nên đọc thẳng bảng như {@code BookingQueries} đọc {@code flights}.
	 */
	private static final String LOCK = """
			SELECT p.id, p.booking_id, p.amount, p.status, b.code
			FROM payments p
			JOIN bookings b ON b.id = p.booking_id
			WHERE p.txn_ref = :txnRef AND p.kind = 'CHARGE'
			FOR UPDATE OF p
			""";

	private static final String COMPLETE = """
			UPDATE payments
			SET status = :status, provider_txn_no = :txnNo, provider_txn_date = :payDate,
			    response_code = :responseCode, raw_response = CAST(:raw AS jsonb), completed_at = now()
			WHERE id = :id
			""";

	private final Vnpay vnpay;

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	PaymentCallbackService(Vnpay vnpay, JdbcClient jdbc, ApplicationEventPublisher events) {
		this.vnpay = vnpay;
		this.jdbc = jdbc;
		this.events = events;
	}

	/** Đúng 6 bước của TDD §6.3; lỗi ở listener làm rollback toàn bộ, IPN trả 99 và VNPay gọi lại. */
	@Transactional
	public Outcome handle(Map<String, String> params) {
		if (!vnpay.verify(params)) {
			return new Outcome("97", "Invalid signature", null, false);
		}
		Optional<Locked> found = jdbc.sql(LOCK)
			.param("txnRef", params.get("vnp_TxnRef"))
			.query((rs, rowNum) -> new Locked(rs.getLong("id"), rs.getLong("booking_id"), rs.getLong("amount"),
					rs.getString("status"), rs.getString("code")))
			.optional();
		if (found.isEmpty()) {
			return new Outcome("01", "Order not found", null, false);
		}
		Locked payment = found.get();
		if (!String.valueOf(payment.amount() * 100).equals(params.get("vnp_Amount"))) {
			return new Outcome("04", "Invalid amount", payment.bookingCode(), false);
		}
		if (!"PENDING".equals(payment.status())) {
			return new Outcome("02", "Order already confirmed", payment.bookingCode(),
					"SUCCESS".equals(payment.status()));
		}
		boolean paid = "00".equals(params.get("vnp_ResponseCode"))
				&& "00".equals(params.get("vnp_TransactionStatus"));
		jdbc.sql(COMPLETE)
			.param("status", paid ? "SUCCESS" : "FAILED")
			.param("txnNo", params.get("vnp_TransactionNo"))
			.param("payDate", params.get("vnp_PayDate"))
			.param("responseCode", params.get("vnp_ResponseCode"))
			.param("raw", JsonMapper.shared().writeValueAsString(params))
			.param("id", payment.id())
			.update();
		if (paid) {
			events.publishEvent(new PaymentSucceeded(payment.id(), payment.bookingId(), payment.amount()));
		}
		return new Outcome("00", "Confirm Success", payment.bookingCode(), paid);
	}

}
```

- [ ] **Step 5: Controller callback**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/payment/web/VnpayCallbackController.java`:

```java
package vn.edu.uit.flightbooking.payment.web;

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.payment.domain.PaymentCallbackService;
import vn.edu.uit.flightbooking.payment.domain.PaymentCallbackService.Outcome;

/**
 * Callback của VNPay (TDD §8.5), công khai và được bảo vệ bằng chữ ký. Là GET nên không cần CSRF (TDD §4.2). Log mỗi
 * callback theo TDD §12, không ghi chữ ký.
 */
@RestController
@RequestMapping("/api/payments/vnpay")
class VnpayCallbackController {

	private static final Logger log = LoggerFactory.getLogger(VnpayCallbackController.class);

	private final PaymentCallbackService callbacks;

	private final String baseUrl;

	VnpayCallbackController(PaymentCallbackService callbacks, @Value("${app.base-url}") String baseUrl) {
		this.callbacks = callbacks;
		this.baseUrl = baseUrl;
	}

	/** VNPay gọi từ server; luôn trả 200. Lỗi không lường trước trả 99 để VNPay gọi lại (TDD §6.3 bước 6). */
	@GetMapping("/ipn")
	Map<String, String> ipn(@RequestParam Map<String, String> params) {
		try {
			Outcome outcome = callbacks.handle(params);
			logged("IPN", params, outcome.rspCode());
			return Map.of("RspCode", outcome.rspCode(), "Message", outcome.message());
		}
		catch (RuntimeException e) {
			log.error("VNPay IPN lỗi: txnRef={}", params.get("vnp_TxnRef"), e);
			return Map.of("RspCode", "99", "Message", "Unknown error");
		}
	}

	/** AD-07: trình duyệt quay về; xử lý như IPN (idempotent) rồi chuyển về C-09 kèm kết quả. */
	@GetMapping("/return")
	ResponseEntity<Void> back(@RequestParam Map<String, String> params) {
		Outcome outcome = callbacks.handle(params);
		logged("Return", params, outcome.rspCode());
		String target = outcome.bookingCode() == null ? baseUrl + "/bookings?payment=failed"
				: baseUrl + "/bookings/" + outcome.bookingCode() + "?payment=" + (outcome.paid() ? "success" : "failed");
		return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
	}

	private static void logged(String channel, Map<String, String> params, String rspCode) {
		log.info("VNPay {}: txnRef={}, vnp_ResponseCode={}, RspCode={}", channel, params.get("vnp_TxnRef"),
				params.get("vnp_ResponseCode"), rspCode);
	}

}
```

- [ ] **Step 6: Listener của booking**

Sửa `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingPayments.java`.

(a) Thay khối import bằng:

```java
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.booking.UnexpectedPaymentReceived;
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
import vn.edu.uit.flightbooking.payment.PaymentApi;
import vn.edu.uit.flightbooking.payment.PaymentSucceeded;
```

(b) Thay Javadoc của lớp, các trường và constructor bằng:

```java
/**
 * Thanh toán booking: tạo lượt thu qua {@link PaymentApi} (FR-30, BR-41) và nhận kết quả qua {@link PaymentSucceeded}
 * (FR-31, BR-43, BR-44).
 */
@Service
public class BookingPayments {

	private record Payable(long id, long userId, BookingStatus status, long totalAmount, Instant holdExpiresAt) {
	}

	private final PaymentApi payments;

	private final BookingService bookings;

	private final JdbcClient jdbc;

	private final ApplicationEventPublisher events;

	BookingPayments(PaymentApi payments, BookingService bookings, JdbcClient jdbc, ApplicationEventPublisher events) {
		this.payments = payments;
		this.bookings = bookings;
		this.jdbc = jdbc;
		this.events = events;
	}
```

(c) Thêm hàm sau vào cuối lớp (trước dấu `}` cuối cùng):

```java
	/**
	 * TDD §6.3: chạy đồng bộ trong transaction của callback, đã khoá {@code payments}; khoá booking rồi kiểm tra lại.
	 * Booking còn chờ thanh toán (kể cả trong 5 phút ân hạn của BR-45) thì xuất vé (BR-43). Ngược lại phát
	 * {@link UnexpectedPaymentReceived} để hoàn 100% lượt thu (BR-44).
	 */
	@EventListener
	void on(PaymentSucceeded event) {
		BookingStatus status = jdbc.sql("SELECT status FROM bookings WHERE id = ? FOR UPDATE")
			.param(event.bookingId())
			.query((rs, rowNum) -> BookingStatus.valueOf(rs.getString("status")))
			.single();
		if (status == BookingStatus.PENDING_PAYMENT) {
			bookings.issue(event.bookingId());
		}
		else {
			events.publishEvent(new UnexpectedPaymentReceived(event.paymentId(), event.bookingId()));
		}
	}
```

- [ ] **Step 7: Chạy test, xác nhận xanh**

```bash
cd backend && ./mvnw test -Dtest='VnpayCallbackTest,BookingPaymentTest,ModularityTest'
```

Expected: `BUILD SUCCESS`. `VnpayCallbackTest` có 6 test.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/payment \
  backend/src/main/java/vn/edu/uit/flightbooking/booking/UnexpectedPaymentReceived.java \
  backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingPayments.java \
  backend/src/test/java/vn/edu/uit/flightbooking/payment/web/VnpayCallbackTest.java
git commit -m "feat(payment): IPN và Return URL VNPay, xuất vé khi thanh toán thành công (FR-31, FR-32, BR-42–44, T3, T4)"
```

---

### Task 4: Email vé điện tử và gửi lại (FR-31, FR-73, FR-130)

**Files:**

- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/BookingApi.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/booking/TicketEmailRequested.java`
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingService.java` (thêm `resendTicketEmail`)
- Modify (thay toàn bộ): `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/StaffBookingController.java`
- Create: `backend/src/main/java/vn/edu/uit/flightbooking/notification/domain/TicketEmail.java`
- Create: `backend/src/main/resources/templates/email/ticket.html`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/notification/domain/TicketEmailTest.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/booking/web/ResendTicketEmailTest.java`

**Interfaces:**

- Consumes: `BookingIssued`, `BookingService.issue`, `BookingCancellation.notFound()` (Plan 11); `Mailer.send(to, subject, template, variables)` (Plan 02); `TestBookings`, `TestUsers`, `TestVouchers`, `TestMailSender`.
- Produces:
  - `booking.BookingApi.issued(long bookingId) → IssuedBooking(code, contactName, contactEmail, totalAmount, tickets)`, mỗi vé là `IssuedTicket(passengerName, flightNumber, departureAirport, departureTime, arrivalAirport, arrivalTime, ticketNumber)`. Chỉ vé đã có số của chặng `ACTIVE`, theo chiều, thứ tự chặng rồi thứ tự khách. Giờ theo múi giờ sân bay.
  - `booking.TicketEmailRequested(long bookingId)`.
  - `BookingService.resendTicketEmail(String code)`: 404 hoặc 409 `INVALID_STATE` nếu booking chưa `ISSUED`.
  - `POST /api/staff/bookings/{code}/resend-ticket-email` → 204.
  - `notification.domain.TicketEmail.send(long bookingId)`: tiêu đề `Vé điện tử SkyLine – mã đặt chỗ {CODE}`, template `ticket`.

- [ ] **Step 1: Viết test**

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/notification/domain/TicketEmailTest.java`:

```java
package vn.edu.uit.flightbooking.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.edu.uit.flightbooking.booking.TestBookings.ADULT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.INFANT_BORN;
import static vn.edu.uit.flightbooking.booking.TestBookings.passenger;
import static vn.edu.uit.flightbooking.flight.TestFlights.DAY;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestMailSender;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.booking.domain.BookingService;
import vn.edu.uit.flightbooking.common.Role;

/**
 * FR-31, FR-130: email vé gửi tới email liên hệ và có đủ số vé của mọi khách trên mọi chặng. Gọi thẳng
 * {@link TicketEmail#send}, vì listener sau commit không chạy trong test {@code @Transactional}. Email liên hệ ngẫu
 * nhiên, để không lẫn với email của test khác trong {@link TestMailSender}.
 */
@IntegrationTest
@Transactional
class TicketEmailTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TestMailSender mail;

	@Autowired
	BookingService bookings;

	@Autowired
	TicketEmail email;

	/** Một người lớn và một em bé, Q1100 QHA→QHB rồi Q1200 QHB→QHE: 4 vé, tổng 3.020.000 + 302.000. */
	@Test
	void ticketEmailListsEveryTicket() throws Exception {
		TestBookings.Trip trip = TestBookings.trip(mvc, jdbc);
		String contact = TestUsers.randomEmail();
		String code = TestBookings.holdCode(mvc, TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER).session(),
				TestBookings.body(trip, Map.of("passengers",
						List.of(passenger("ADULT", ADULT_BORN), passenger("INFANT", INFANT_BORN)), "contact",
						Map.of("name", "NGUYEN VAN AN", "email", contact, "phone", "0901234567"))));
		long bookingId = TestBookings.bookingId(jdbc, code);
		bookings.issue(bookingId);

		email.send(bookingId);

		List<String> numbers = jdbc.sql("""
				SELECT t.ticket_number FROM tickets t JOIN passengers p ON p.id = t.passenger_id
				WHERE p.booking_id = ?
				""").param(bookingId).query(String.class).list();
		assertThat(numbers).hasSize(4).doesNotContainNull();
		assertThat(mail.sentTo(contact)).hasSize(1);
		MimeMessage message = mail.sentTo(contact).getFirst();
		String html = (String) message.getContent();
		assertThat(message.getSubject()).isEqualTo("Vé điện tử SkyLine – mã đặt chỗ " + code);
		assertThat(html).contains(code, "NGUYEN VAN AN", "Q1100", "Q1200", "QHA", "QHB", "QHE", "3.322.000",
				"06:00 " + DAY.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
				"http://localhost:3000/bookings/" + code);
		assertThat(html).contains(numbers);
	}

}
```

Tạo `backend/src/test/java/vn/edu/uit/flightbooking/booking/web/ResendTicketEmailTest.java`:

```java
package vn.edu.uit.flightbooking.booking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.uit.flightbooking.TestCsrf.csrf;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.uit.flightbooking.IntegrationTest;
import vn.edu.uit.flightbooking.TestUsers;
import vn.edu.uit.flightbooking.booking.TestBookings;
import vn.edu.uit.flightbooking.booking.TicketEmailRequested;
import vn.edu.uit.flightbooking.common.Role;
import vn.edu.uit.flightbooking.promotion.TestVouchers;

/** FR-73: Staff gửi lại email vé, chỉ cho booking ISSUED. */
@IntegrationTest
@Transactional
@RecordApplicationEvents
class ResendTicketEmailTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	ResultActions resend(MockHttpSession session, String code) throws Exception {
		return mvc.perform(post("/api/staff/bookings/{code}/resend-ticket-email", code).with(csrf(mvc)).session(session));
	}

	@Test
	void staffResendsTicketEmailOnlyForIssuedBookings() throws Exception {
		TestBookings.Trip trip = TestBookings.trip(mvc, jdbc);
		MockHttpSession customer = TestUsers.loggedIn(mvc, jdbc, Role.CUSTOMER).session();
		MockHttpSession staff = TestUsers.loggedIn(mvc, jdbc, Role.STAFF).session();
		TestVouchers.insert(jdbc, "QVFREE", Map.of("discount_value", 100));
		String issued = TestBookings.holdCode(mvc, customer,
				TestBookings.body(trip, Map.of("voucherCode", "QVFREE")));
		String pending = TestBookings.holdCode(mvc, customer, TestBookings.body(trip, Map.of()));

		resend(staff, issued.toLowerCase()).andExpect(status().isNoContent());
		resend(staff, pending).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
		resend(staff, "ZZZZZZ").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		resend(customer, issued).andExpect(status().isForbidden());

		assertThat(events.stream(TicketEmailRequested.class))
			.containsExactly(new TicketEmailRequested(TestBookings.bookingId(jdbc, issued)));
	}

}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

```bash
cd backend && ./mvnw test -Dtest='TicketEmailTest,ResendTicketEmailTest'
```

Expected: `COMPILATION ERROR`, `cannot find symbol` cho `TicketEmail` và `TicketEmailRequested`.

- [ ] **Step 3: `BookingApi` và `TicketEmailRequested`**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/TicketEmailRequested.java`:

```java
package vn.edu.uit.flightbooking.booking;

/**
 * FR-73: Staff yêu cầu gửi lại email vé. Tách khỏi {@link BookingIssued} để listener của "đã xuất vé" không chạy lại.
 * Module notification gửi email sau khi commit.
 */
public record TicketEmailRequested(long bookingId) {
}
```

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/booking/BookingApi.java`:

```java
package vn.edu.uit.flightbooking.booking;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API công khai của booking (TDD §3.2). Hiện có dữ liệu cho email vé (FR-31, FR-73). Plan 14, 15 thêm các hàm khoá và
 * thao tác trên chiều/chặng mà hậu mãi cần.
 */
@Service
public class BookingApi {

	/** Giờ theo múi giờ sân bay tương ứng (TDD §5.1). */
	public record IssuedTicket(String passengerName, String flightNumber, String departureAirport,
			OffsetDateTime departureTime, String arrivalAirport, OffsetDateTime arrivalTime, String ticketNumber) {
	}

	/** {@code tickets}: vé đã có số của các chặng ACTIVE, theo chiều, thứ tự chặng rồi thứ tự khách. */
	public record IssuedBooking(String code, String contactName, String contactEmail, long totalAmount,
			List<IssuedTicket> tickets) {
	}

	private record Contact(String code, String name, String email, long totalAmount) {
	}

	private static final String TICKETS = """
			SELECT p.last_name || ' ' || p.first_name AS passenger, f.flight_number,
			       f.departure_airport, dep.timezone AS departure_timezone, f.departure_time,
			       f.arrival_airport, arr.timezone AS arrival_timezone, f.arrival_time, t.ticket_number
			FROM tickets t
			JOIN passengers p ON p.id = t.passenger_id
			JOIN booking_segments s ON s.id = t.segment_id
			JOIN booking_journeys j ON j.id = s.journey_id
			JOIN flights f ON f.id = s.flight_id
			JOIN airports dep ON dep.code = f.departure_airport
			JOIN airports arr ON arr.code = f.arrival_airport
			WHERE j.booking_id = :id AND s.status = 'ACTIVE' AND t.ticket_number IS NOT NULL
			ORDER BY j.direction, s.seq, p.id
			""";

	private final JdbcClient jdbc;

	BookingApi(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	public IssuedBooking issued(long bookingId) {
		Contact contact = jdbc.sql("SELECT code, contact_name, contact_email, total_amount FROM bookings WHERE id = ?")
			.param(bookingId)
			.query((rs, rowNum) -> new Contact(rs.getString("code"), rs.getString("contact_name"),
					rs.getString("contact_email"), rs.getLong("total_amount")))
			.single();
		List<IssuedTicket> tickets = jdbc.sql(TICKETS)
			.param("id", bookingId)
			.query((rs, rowNum) -> new IssuedTicket(rs.getString("passenger"), rs.getString("flight_number"),
					rs.getString("departure_airport"), local(rs, "departure_time", "departure_timezone"),
					rs.getString("arrival_airport"), local(rs, "arrival_time", "arrival_timezone"),
					rs.getString("ticket_number")))
			.list();
		return new IssuedBooking(contact.code(), contact.name(), contact.email(), contact.totalAmount(), tickets);
	}

	private static OffsetDateTime local(ResultSet rs, String time, String zone) throws SQLException {
		return rs.getObject(time, OffsetDateTime.class)
			.atZoneSameInstant(ZoneId.of(rs.getString(zone)))
			.toOffsetDateTime();
	}

}
```

- [ ] **Step 4: Gửi lại email vé (FR-73)**

Sửa `backend/src/main/java/vn/edu/uit/flightbooking/booking/domain/BookingService.java`.

(a) Thêm các import (giữ thứ tự nhóm như file hiện có):

```java
import java.util.Locale;
```

```java
import vn.edu.uit.flightbooking.booking.TicketEmailRequested;
```

```java
import vn.edu.uit.flightbooking.common.BusinessException;
import vn.edu.uit.flightbooking.common.ErrorCode;
```

(b) Thêm hàm sau ngay sau hàm `issue`:

```java
	/** FR-73: Staff gửi lại email vé, chỉ cho booking đã xuất vé. Email gửi sau khi commit, như lúc xuất vé. */
	@Transactional
	public void resendTicketEmail(String code) {
		record Found(long id, BookingStatus status) {
		}
		Found booking = jdbc.sql("SELECT id, status FROM bookings WHERE code = ?")
			.param(code.toUpperCase(Locale.ROOT))
			.query((rs, rowNum) -> new Found(rs.getLong("id"), BookingStatus.valueOf(rs.getString("status"))))
			.optional()
			.orElseThrow(BookingCancellation::notFound);
		if (booking.status() != BookingStatus.ISSUED) {
			throw new BusinessException(ErrorCode.INVALID_STATE, "Chỉ gửi lại email vé cho booking đã xuất vé");
		}
		events.publishEvent(new TicketEmailRequested(booking.id()));
	}
```

Thay toàn bộ `backend/src/main/java/vn/edu/uit/flightbooking/booking/web/StaffBookingController.java` bằng:

```java
package vn.edu.uit.flightbooking.booking.web;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.uit.flightbooking.booking.domain.BookingQueries;
import vn.edu.uit.flightbooking.booking.domain.BookingQueries.BookingSummary;
import vn.edu.uit.flightbooking.booking.domain.BookingQueries.StaffBookingDetail;
import vn.edu.uit.flightbooking.booking.domain.BookingService;
import vn.edu.uit.flightbooking.booking.domain.BookingStatus;

/**
 * Màn hình S-01, S-02: tra cứu booking (FR-70), gửi lại email vé (FR-73). Quyền STAFF (Admin kế thừa) theo đường dẫn ở
 * SecurityConfig.
 */
@RestController
@RequestMapping("/api/staff/bookings")
class StaffBookingController {

	private final BookingQueries queries;

	private final BookingService bookings;

	StaffBookingController(BookingQueries queries, BookingService bookings) {
		this.queries = queries;
		this.bookings = bookings;
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

	@PostMapping("/{code}/resend-ticket-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resendTicketEmail(@PathVariable String code) {
		bookings.resendTicketEmail(code);
	}

}
```

- [ ] **Step 5: Email vé**

Tạo `backend/src/main/java/vn/edu/uit/flightbooking/notification/domain/TicketEmail.java`:

```java
package vn.edu.uit.flightbooking.notification.domain;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import vn.edu.uit.flightbooking.booking.BookingApi;
import vn.edu.uit.flightbooking.booking.BookingApi.IssuedBooking;
import vn.edu.uit.flightbooking.booking.BookingIssued;
import vn.edu.uit.flightbooking.booking.TicketEmailRequested;
import vn.edu.uit.flightbooking.notification.infra.Mailer;

/**
 * Email vé điện tử (FR-31, FR-73, FR-130) gửi tới email liên hệ của booking. Chạy bất đồng bộ, sau khi transaction xuất
 * vé hoặc yêu cầu gửi lại commit; gửi lỗi chỉ ghi log.
 */
@Component
class TicketEmail {

	private final BookingApi bookings;

	private final Mailer mailer;

	private final String baseUrl;

	TicketEmail(BookingApi bookings, Mailer mailer, @Value("${app.base-url}") String baseUrl) {
		this.bookings = bookings;
		this.mailer = mailer;
		this.baseUrl = baseUrl;
	}

	@ApplicationModuleListener
	void on(BookingIssued event) {
		send(event.bookingId());
	}

	@ApplicationModuleListener
	void on(TicketEmailRequested event) {
		send(event.bookingId());
	}

	void send(long bookingId) {
		IssuedBooking booking = bookings.issued(bookingId);
		mailer.send(booking.contactEmail(), "Vé điện tử SkyLine – mã đặt chỗ " + booking.code(), "ticket",
				Map.of("contactName", booking.contactName(), "code", booking.code(), "totalAmount",
						booking.totalAmount(), "tickets", booking.tickets(), "link",
						baseUrl + "/bookings/" + booking.code()));
	}

}
```

Tạo `backend/src/main/resources/templates/email/ticket.html`:

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
  <head>
    <meta charset="UTF-8" />
    <title>Vé điện tử SkyLine</title>
  </head>
  <body
    style="margin:0;padding:24px;background:#ffffff;color:#000d10;font-family:Inter,Arial,sans-serif;font-size:16px;line-height:1.6;"
  >
    <p>Xin chào <span th:text="${contactName}">NGUYEN VAN AN</span>,</p>
    <p>
      Thanh toán đã thành công và vé của bạn đã được xuất. Mã đặt chỗ:
      <strong th:text="${code}" style="font-size:20px;letter-spacing:2px;"
        >K7Q2XM</strong
      >
    </p>
    <table style="border-collapse:collapse;width:100%;font-size:14px;">
      <thead>
        <tr style="text-align:left;border-bottom:1px solid #e5e5e5;">
          <th style="padding:8px 8px 8px 0;">Hành khách</th>
          <th style="padding:8px;">Chuyến bay</th>
          <th style="padding:8px;">Hành trình</th>
          <th style="padding:8px;">Khởi hành</th>
          <th style="padding:8px 0 8px 8px;">Số vé</th>
        </tr>
      </thead>
      <tbody>
        <tr th:each="t : ${tickets}" style="border-bottom:1px solid #e5e5e5;">
          <td style="padding:8px 8px 8px 0;" th:text="${t.passengerName()}">
            NGUYEN VAN AN
          </td>
          <td style="padding:8px;" th:text="${t.flightNumber()}">VN213</td>
          <td
            style="padding:8px;"
            th:text="${t.departureAirport() + ' → ' + t.arrivalAirport()}"
          >
            HAN → SGN
          </td>
          <td
            style="padding:8px;"
            th:text="${#temporals.format(t.departureTime(), 'HH:mm dd/MM/yyyy')}"
          >
            06:00 01/12/2026
          </td>
          <td
            style="padding:8px 0 8px 8px;font-family:monospace;"
            th:text="${t.ticketNumber()}"
          >
            7381234567890
          </td>
        </tr>
      </tbody>
    </table>
    <p>
      Tổng thanh toán:
      <strong
        ><span th:text="${#numbers.formatInteger(totalAmount, 1, 'POINT')}"
          >5.848.000</span
        >
        đ</strong
      >
    </p>
    <p>
      <a
        th:href="${link}"
        href="#"
        style="display:inline-block;padding:12px 24px;border-radius:1000px;background:#000d10;color:#ffffff;text-decoration:none;font-weight:700;"
        >Xem booking</a
      >
    </p>
    <p style="color:#8e8e95;">
      Giờ khởi hành là giờ địa phương của sân bay đi. Vui lòng có mặt tại sân
      bay ít nhất 2 giờ trước giờ khởi hành.
    </p>
  </body>
</html>
```

- [ ] **Step 6: Chạy test, xác nhận xanh**

```bash
cd backend && ./mvnw test -Dtest='TicketEmailTest,ResendTicketEmailTest,ModularityTest'
```

Expected: `BUILD SUCCESS`. `ModularityTest` chấp nhận phụ thuộc mới `notification → booking`.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/vn/edu/uit/flightbooking/booking \
  backend/src/main/java/vn/edu/uit/flightbooking/notification/domain/TicketEmail.java \
  backend/src/main/resources/templates/email/ticket.html \
  backend/src/test/java/vn/edu/uit/flightbooking/notification/domain/TicketEmailTest.java \
  backend/src/test/java/vn/edu/uit/flightbooking/booking/web/ResendTicketEmailTest.java
git commit -m "feat(notification): email vé điện tử khi xuất vé và Staff gửi lại (FR-31, FR-73, FR-130)"
```

---

### Task 5: Toàn bộ test, kiểu API, thanh toán thật trên sandbox và tài liệu

**Files:**

- Modify: `frontend/src/lib/api-types.ts` (sinh lại, không sửa tay)
- Modify: `docs/superpowers/plans/2026-10-09-00-roadmap.md`
- Modify: `CLAUDE.md`

**Interfaces:**

- Consumes: toàn bộ Task 1–4.
- Produces: kiểu frontend `Schemas["PaymentUrlResponse"]`, `Schemas["PaymentView"]`; `Schemas["BookingDetail"]` có thêm `payments`.

- [ ] **Step 1: Chạy toàn bộ test backend**

```bash
cd backend && ./mvnw verify
```

Expected: `BUILD SUCCESS`. Toàn bộ test của Plan 01–11 vẫn xanh.

- [ ] **Step 2: Sinh lại kiểu API**

Mở terminal 1 và chạy:

```bash
docker compose up -d postgres mailpit
cd backend && ./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Ở terminal 2:

```bash
cd frontend && npm run gen:api && npm run lint && npm run build
git diff --stat src/lib/api-types.ts
```

Expected: `api-types.ts` có thêm `PaymentUrlResponse`, `PaymentView`, `BookingDetail.payments`, đường dẫn `/api/bookings/{code}/payments`, `/api/payments/vnpay/ipn`, `/api/payments/vnpay/return` và `/api/staff/bookings/{code}/resend-ticket-email`. Lint và build xanh.

- [ ] **Step 3: Thanh toán thật trên sandbox**

Bước này kiểm chứng kết quả của roadmap: "Thanh toán sandbox → `ISSUED`, số vé 13 chữ số, email vé trong Mailpit".

Cần có `.env` chứa `VNPAY_TMN_CODE` và `VNPAY_HASH_SECRET` thật. Backend vẫn chạy ở terminal 1. Ở terminal 2, chạy frontend để Return URL `http://localhost:3000/api/payments/vnpay/return` đi qua được rewrites:

```bash
cd frontend && npm run dev
```

Ở terminal 3 (Git Bash, thư mục gốc repo), đăng nhập tài khoản demo, giữ chỗ chuyến bay thẳng HAN→SGN đầu tiên sau 7 ngày, rồi in URL thanh toán:

```bash
B=http://localhost:8080/api
J=$(mktemp)
xsrf() { awk '$6=="XSRF-TOKEN"{print $7}' "$J"; }
curl -s -c "$J" -b "$J" -o /dev/null "$B/auth/csrf"
curl -s -c "$J" -b "$J" -H "X-XSRF-TOKEN: $(xsrf)" -H 'Content-Type: application/json' \
  -d '{"email":"customer@demo.local","password":"Demo@1234"}' -o /dev/null "$B/auth/login"
curl -s -c "$J" -b "$J" -o /dev/null "$B/auth/csrf"
DATE=$(date -d '+7 days' +%F)
BODY=$(curl -s "$B/flights/search?from=HAN&to=SGN&date=$DATE&adults=1&children=0&infants=0&cabin=ECONOMY" | node -e '
  const r = JSON.parse(require("fs").readFileSync(0, "utf8"));
  const it = r.itineraries.find(i => i.stops === 0);
  console.log(JSON.stringify({
    journeys: [{ direction: "OUTBOUND", flightIds: it.segments.map(s => s.flightId), fareFamilyId: it.fareOptions[0].fareFamilyId }],
    passengers: [{ type: "ADULT", lastName: "NGUYEN", firstName: "VAN AN", gender: "MALE", dateOfBirth: "1990-05-20" }],
    contact: { name: "NGUYEN VAN AN", email: "an@example.com", phone: "0901234567" }
  }));')
CODE=$(curl -s -c "$J" -b "$J" -H "X-XSRF-TOKEN: $(xsrf)" -H 'Content-Type: application/json' -d "$BODY" "$B/bookings" \
  | node -e 'console.log(JSON.parse(require("fs").readFileSync(0, "utf8")).code)')
echo "Mã đặt chỗ: $CODE"
curl -s -c "$J" -b "$J" -H "X-XSRF-TOKEN: $(xsrf)" -X POST "$B/bookings/$CODE/payments"; echo
```

Nếu lệnh tìm chuyến không có kết quả, seed `V106` đã quá cũ: làm mới bằng `docker compose down -v` rồi chạy lại từ Step 2.

Mở `paymentUrl` vừa in ra bằng trình duyệt. Chọn ngân hàng NCB, nhập thẻ `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456`.

VNPay chuyển về `http://localhost:3000/api/payments/vnpay/return?...`, sau đó backend chuyển tiếp tới `/bookings/{CODE}?payment=success`. Trình duyệt chưa đăng nhập nên `proxy.ts` sẽ đưa tới `/login?next=...`; đó là hành vi đúng. Màn C-09 thuộc Plan 13.

Kiểm tra kết quả:

```bash
docker compose exec postgres psql -U flightbooking -d flightbooking -c "
  SELECT b.status, t.ticket_number, length(t.ticket_number) AS digits
  FROM bookings b JOIN passengers p ON p.booking_id = b.id JOIN tickets t ON t.passenger_id = p.id
  WHERE b.code = '$CODE';"
docker compose exec postgres psql -U flightbooking -d flightbooking -c "
  SELECT status, response_code, provider_txn_no, provider_txn_date FROM payments
  WHERE booking_id = (SELECT id FROM bookings WHERE code = '$CODE');"
```

Expected:

- Booking ở `ISSUED`, mỗi vé có `digits = 13`.
- Lượt thu ở `SUCCESS`, `response_code = 00`, có `provider_txn_no` và `provider_txn_date`.
- Log backend có dòng `VNPay Return: txnRef=..., vnp_ResponseCode=00, RspCode=00`.
- Mailpit (http://localhost:8025) có email "Vé điện tử SkyLine – mã đặt chỗ {CODE}" gửi tới `an@example.com`.

Nếu VNPay báo "Sai chữ ký", kiểm tra lại `VNPAY_HASH_SECRET` trong `.env` (không có khoảng trắng thừa) và khởi động lại backend.

Bước tuỳ chọn: thử IPN thật qua tunnel (TDD §6.3).

1. Chạy `cloudflared tunnel --url http://localhost:3000`.
2. Khai báo `https://<tunnel>/api/payments/vnpay/ipn` trong cổng merchant sandbox.
3. Lặp lại các bước trên với một booking mới.

Log lúc này có thêm dòng `VNPay IPN: ... RspCode=00`, và dòng `VNPay Return` đến sau sẽ là `RspCode=02`.

Dừng backend và frontend sau khi kiểm tra xong.

- [ ] **Step 4: Cập nhật lộ trình**

Trong `docs/superpowers/plans/2026-10-09-00-roadmap.md`:

(a) Ở dòng "Plan chi tiết đã có" của bảng đầu file, thêm vào cuối: ` · [Plan 12 — Thanh toán VNPay và xuất vé](2026-10-14-12-vnpay-payment.md)`.

(b) Ở §2, dòng Plan 12, đổi `☐` cuối dòng thành `☑`.

(c) Ở §6, thay dòng `| 12 | Dự kiến: `PaymentApi`, sự kiện `PaymentSucceeded` |` bằng:

```markdown
| 12 | Module `payment` (chỉ phụ thuộc `common`, ghi bảng `payments` bằng `JdbcClient`). `payment.PaymentApi.charge(bookingId, amount, expiresAt, orderInfo, clientIp) → paymentUrl`: ghi lượt thu `CHARGE`/`VNPAY`/`PENDING`, `txn_ref` là UUID không gạch nối, link hết hạn cùng `expiresAt`. `payment.domain.Vnpay` (`payUrl`, `verify`, `hash`) ký theo code mẫu VNPay 2.1.0; cấu hình `app.vnpay.tmn-code`, `hash-secret`, `pay-url`, `return-url` từ `VNPAY_*`. `PaymentCallbackService.handle(params) → Outcome(rspCode, message, bookingCode, paid)` dùng chung cho IPN và Return URL theo đúng 6 bước TDD §6.3 (khoá `payments FOR UPDATE OF p`, đọc `bookings.code` bằng SQL). Sự kiện `payment.PaymentSucceeded(paymentId, bookingId, amount)` phát trong transaction của callback (chưa có `rescheduleId`; Plan 15 thêm). `booking` phụ thuộc thêm `payment`: `booking.domain.BookingPayments.start(userId, code, clientIp)` (BR-41: quá hạn hoặc `EXPIRED` báo `HOLD_EXPIRED`, trạng thái khác báo `INVALID_STATE`) và `@EventListener` đồng bộ cho `PaymentSucceeded`: khoá booking, `PENDING_PAYMENT` (kể cả trong ân hạn) thì `BookingService.issue`, ngược lại phát `booking.UnexpectedPaymentReceived(paymentId, bookingId)` (chưa có listener; Plan 14 tạo yêu cầu hoàn `INVALID_PAYMENT`). `booking.BookingApi.issued(bookingId) → IssuedBooking(code, contactName, contactEmail, totalAmount, tickets[IssuedTicket])`. Sự kiện `booking.TicketEmailRequested(bookingId)` cho FR-73. `notification` phụ thuộc thêm `booking`: `TicketEmail` nghe `BookingIssued` và `TicketEmailRequested`, template `email/ticket.html`. `BookingDetail` có thêm `payments[{kind, status, amount, createdAt, completedAt}]` (mọi lượt, cũ nhất trước). API: `POST /api/bookings/{code}/payments` (201 `{paymentUrl}`), `GET /api/payments/vnpay/ipn` (luôn 200 `{RspCode, Message}`), `GET /api/payments/vnpay/return` (302 `{APP_BASE_URL}/bookings/{code}?payment=success\|failed`, không biết mã thì `/bookings?payment=failed`), `POST /api/staff/bookings/{code}/resend-ticket-email` (204, chưa `ISSUED` thì 409 `INVALID_STATE`). Test: `payment.TestVnpay` (`TMN_CODE`, `SECRET` đặt trong `@IntegrationTest`; `query(url)`, `paymentUrl`, `start`, `callback(vnpay, txnRef, amount, responseCode)`). Kiểu frontend: `Schemas["PaymentUrlResponse"]`, `["PaymentView"]` |
```

(d) Ở §7, thêm mục mới vào cuối danh sách:

```markdown
13. **Plan 12 có 7 điểm cố ý khác TDD** (chi tiết ở [Plan 12](2026-10-14-12-vnpay-payment.md#điểm-cố-ý-khác-tdd)):
    - xác nhận thanh toán chưa bọc `@Retryable`, vì thứ tự khoá không tạo vòng chờ và VNPay tự gọi lại khi nhận `99`;
    - `PaymentSucceeded` chưa có `rescheduleId`;
    - `payment` đọc `bookings.code` bằng SQL cho Return URL;
    - tạo lượt thu trả 201;
    - có sự kiện mới `TicketEmailRequested` cho FR-73;
    - `BookingApi` mới chỉ có hàm cho email vé;
    - chưa có `VNPAY_API_URL` (Plan 14).
```

- [ ] **Step 5: Cập nhật CLAUDE.md**

Trong `CLAUDE.md`, mục Status, thay đoạn:

```
and `booking` (Plan 10: `POST /api/bookings/quote`, with `FlightApi` checking and pricing the chosen itinerary; Plan 11: hold, cancel, expiry job, booking list/detail and staff lookup) are done.
```

bằng:

```
`booking` (Plan 10: `POST /api/bookings/quote`, with `FlightApi` checking and pricing the chosen itinerary; Plan 11: hold, cancel, expiry job, booking list/detail and staff lookup) and `payment` (Plan 12: VNPay charge, IPN and Return URL, ticket issuing on `PaymentSucceeded`, the ticket email and staff resend; VNPay sandbox keys `VNPAY_TMN_CODE`/`VNPAY_HASH_SECRET` go in `.env`) are done.
```

- [ ] **Step 6: Commit**

```bash
git add frontend/src/lib/api-types.ts docs/superpowers/plans/2026-10-09-00-roadmap.md CLAUDE.md
git commit -m "docs: hoàn tất Plan 12, cập nhật hợp đồng payment và kiểu API frontend"
```
