# Plan 13 — Màn hình đặt vé và quản lý booking: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khách đã chọn vé ở C-02 hoàn tất đặt chỗ trên giao diện: C-07 `/checkout` khai hành khách, liên hệ, hành lý, voucher, thấy bảng giá cập nhật theo `POST /bookings/quote` rồi giữ chỗ; C-09 `/bookings/[code]` hiện đủ chi tiết FR-41, đếm ngược hạn giữ chỗ, thanh toán (chuyển sang VNPay) và huỷ giữ chỗ; C-08 `/bookings` liệt kê booking của tôi, lọc theo trạng thái, phân trang.

**Architecture:** Không đổi backend, không sinh lại kiểu API. Ba màn là Client Component gọi API qua `apiFetch` như C-02 và C-12: C-07 đọc lựa chọn ở `sessionStorage` (Plan 08) bằng `useSyncExternalStore`, C-08 và C-09 đọc tham số URL nên nằm trong `<Suspense>` (Cache Components). Logic thuần (chuẩn hoá họ tên, dựng body `BookingRequest`, xác định chặng quốc tế, dựng lại URL C-02, định dạng giờ và đếm ngược) nằm ở `src/app/checkout/booking-request.ts` và `src/lib/booking.ts`, có unit test chạy bằng test runner của Playwright. Tên ô trên form C-07 đặt đúng theo đường dẫn trường của `errors` backend (`passengers[0].dateOfBirth`, `contact.email`), nên lỗi theo trường hiện đúng chỗ mà không cần bảng chuyển đổi.

**Tech Stack:** Next.js 16.4 (App Router, Cache Components), React 19.3, TypeScript, Tailwind v4, Playwright 1.64 (E2E, `page.clock`, `page.route`; unit test không mở trình duyệt). Không thêm thư viện.

**Spec:** [App Flow](../../APP_FLOW.md) §2.1 C-07, C-08, C-09, UF-01, SD-01 · [PRD](../../PRD.md) FR-20–FR-24, FR-30, FR-33, FR-40, FR-41, BR-10–BR-14, BR-22, BR-30, BR-40, BR-41, BR-45, BR-104 · [TDD](../../TDD.md) §7 (frontend: chuẩn hoá họ tên, lựa chọn ở `sessionStorage`), §8.8 (body và phản hồi của quote, giữ chỗ), §9 (mã lỗi) · [DESIGN.md](../../../DESIGN.md) · [Lộ trình](2026-10-09-00-roadmap.md) §5 (quy ước frontend), §6 (hợp đồng Plan 03, 04, 05, 08, 10, 11, 12), §7.3, §7.4 · Plan frontend trước: [Plan 08](2026-10-11-08-flight-search-screens.md) (giờ đọc từ chuỗi có offset, `selection.ts`, cách viết E2E và unit test)

## Global Constraints

- Mọi ràng buộc ở Global Constraints của [Plan 04](2026-10-10-04-account-screens.md) và [Plan 08](2026-10-11-08-flight-search-screens.md) vẫn giữ: `--spacing: 1px` nên `p-22` = 22px; nút là pill (`pillDark`, `pillLine` của `src/components/form.tsx`), ô nhập và panel là khối vuông viền hairline, không shadow; chữ phụ dùng token `muted`; chế độ tối tự đổi qua token ngữ nghĩa (`ink`, `paper`, `line`...), không viết `dark:`; chữ hiển thị tiếng Việt, không dùng gạch dài.
- **Không gọi `new Date()`, `Date.now()` hay `Math.random()` khi render** (Cache Components), kể cả Client Component. Giá trị phụ thuộc giờ hoặc `sessionStorage` đọc qua `useSyncExternalStore` với `getServerSnapshot` trả `null`/`undefined` (`useNow()`, `useSelection()` của plan này). Component dùng `useSearchParams` hoặc `useParams` (route `[code]` không có `generateStaticParams`) phải nằm trong `<Suspense>`: xem `frontend/node_modules/next/dist/docs/01-app/03-api-reference/04-functions/use-params.md`, mục "Cache Components".
- **Không thêm thư viện npm. Không sửa backend, không chạy lại `npm run gen:api`.** Kiểu lấy từ `Schemas[...]` của `@/lib/api`: `BookingRequest`, `QuoteResponse`, `BookingCreatedResponse`, `BaggageOptionResponse`, `PagedModelBookingSummary`, `BookingDetail`, `PaymentUrlResponse`, `UserResponse`.
- **Trường có thể `null` dù kiểu sinh ra ghi `string`** (cờ `--properties-required-by-default` của `gen:api`): `BookingDetail.voucherCode`, `issuedAt`; `PassengerView.nationality`, `passportNumber`, `passportExpiry`; `TicketView.ticketNumber`; `PaymentView.completedAt`. Kiểm tra trước khi dùng.
- **Giờ (TDD §7, NFR-05):** giờ bay đọc thẳng từ chuỗi có offset của API (`hhmm`, `clock`, `dmy`), không qua `Date`, nên đúng giờ sân bay ở mọi múi giờ trình duyệt. Mốc của booking (`createdAt`, `holdExpiresAt`, `issuedAt`) API trả theo `+07:00`, hiển thị kèm chữ "(giờ Việt Nam)". Đếm ngược là phép trừ hai thời điểm tuyệt đối (`Date.parse(holdExpiresAt) - now`).
- **Tiền:** số nguyên VND, hiển thị bằng `vnd()` của `src/app/flights/itineraries.ts` (`1.206.000 đ`, khoảng trắng không ngắt trước `đ`).
- **Mọi lời gọi API qua `apiFetch`.** API trả 401 (phiên hết hạn) thì `router.replace("/login?next=...")` về đúng trang đang mở.
- **Route mới** phải vào danh sách `PUBLIC` hoặc `PROTECTED` của `e2e/smoke.spec.ts` (CLAUDE.md).

### Điểm cố ý khác TDD và APP_FLOW

1. **Báo giá chỉ gọi khi mọi ô của form đã hợp lệ** (theo ràng buộc HTML, chờ 400 ms sau lần sửa cuối), không phải "mỗi lần dữ liệu thay đổi" như APP_FLOW C-07 ghi. FR-20 bắt báo giá kiểm tra giống hệt giữ chỗ, nên gọi khi họ tên, ngày sinh còn trống chỉ nhận về lỗi. Trước lần báo giá đầu, bảng giá hiện tạm tính từ giá lúc tìm chuyến (`fare.totalPrice` trong lựa chọn) kèm câu giải thích.
2. **Đọc dữ liệu ở Client Component** (`apiFetch` trong `useEffect`), không ở Server Component chuyển tiếp cookie như TDD §7 ghi. Giống C-02 (Plan 08) và C-12 (Plan 04): ba màn đều cần trạng thái ở client (form, đếm ngược, thao tác), và chỉ có một đường gọi API nên 401 xử lý ở một chỗ.
3. **Giờ đọc thẳng từ chuỗi có offset**, không qua `Intl.DateTimeFormat` với `timeZone` (TDD §7), cùng lý do với Plan 08 điểm 2. E2E của plan chạy toàn bộ với trình duyệt ở múi giờ New York để ghim điều này.
4. **C-09 chưa có "Hoàn chiều này", "Đổi chiều này", "Thanh toán đổi chuyến" và danh sách yêu cầu hoàn, đổi** (APP_FLOW C-09, FR-41). Các API đó thuộc Plan 14, 15; màn hình thuộc Plan 17.

### Điểm plan tự chọn (APP_FLOW và PRD không nói)

- **Chặng quốc tế (BR-13) xác định ở client** từ danh mục sân bay (`useAirports()` của Plan 08, đã tải một lần), vì kết quả tìm chuyến không có quốc gia. Ô hộ chiếu chỉ hiện và bắt buộc khi có chặng quốc tế.
- **Họ tên chuẩn hoá khi rời ô** (khách thấy đúng giá trị sẽ gửi: "nguyễn" → "NGUYEN") và chuẩn hoá lại khi dựng request. Quốc tịch, số hộ chiếu viết hoa khi rời ô.
- **BR-10 (tuổi khớp loại khách) không kiểm tra ở client.** Báo giá trả lỗi đúng ô ngày sinh với câu tiếng Việt của backend; client chỉ chặn ngày sinh sau ngày bay (`max`) và ghi gợi ý độ tuổi dưới ô.
- **Voucher áp bằng nút "Áp dụng" hoặc Enter trong ô**, không theo từng phím; Enter trong ô voucher không gửi form giữ chỗ. Mã không dùng được thì báo câu của backend dưới ô, bỏ mã và tính lại giá không có mã.
- **Lỗi hành trình** (`SEATS_UNAVAILABLE`, `FLIGHT_NOT_BOOKABLE`, `ITINERARY_INVALID`): lúc báo giá thì hiện câu của backend kèm link "Chọn chuyến khác"; lúc bấm "Giữ chỗ" thì mở lại C-02 với đúng điều kiện tìm cộng `unavailable=1`, C-02 hiện thông báo chung. Không đưa câu của backend lên URL để URL không mang văn bản tuỳ ý.
- **Giữ chỗ xong** thì xoá lựa chọn khỏi `sessionStorage` và `router.replace` sang C-09: Back không quay về form đã dùng, mở lại `/checkout` thấy "Chưa có chuyến bay nào được chọn."
- **Chặn bấm "Giữ chỗ" hai lần bằng ref**, không chờ React kịp vô hiệu hoá nút (bấm đúp gửi hai sự kiện submit trước lần render kế tiếp).
- **Báo giá về sai thứ tự**: mỗi lần gọi có số thứ tự, chỉ kết quả của lần mới nhất được hiện.
- **Huỷ giữ chỗ hỏi lại bằng `confirm()` của trình duyệt.**
- **Đếm ngược về 0 thì ẩn nút "Thanh toán" ngay** dù booking còn `PENDING_PAYMENT` trong 5 phút ân hạn (BR-45): BR-41 chỉ cho tạo lượt thu trước hạn, backend sẽ trả `HOLD_EXPIRED`. Nút "Huỷ giữ chỗ" vẫn còn tới khi booking rời `PENDING_PAYMENT`.
- **C-09 sau VNPay** (`?payment=`): `success` và booking `ISSUED` thì báo đã gửi vé tới email liên hệ; `success` mà booking còn chờ thì báo đang xác nhận; `failed` thì báo chưa thành công, kèm "thanh toán lại được" nếu còn hạn. `/bookings?payment=failed` (Return URL không xác định được booking) cũng có thông báo.
- **C-08:** trạng thái lọc và trang nằm trong URL (`?status=&page=`, trang đếm từ 1), 20 booking mỗi trang (mặc định của backend).
- **C-09 hiện mã hãng** (`VJ123` đã chứa mã), vì `JourneyView` không có tên hãng.
- **E2E giả lập trang VNPay** bằng `page.route("https://sandbox.vnpayment.vn/**")`: test không cần mạng ngoài hay khoá sandbox. Backend vẫn tạo link khi `VNPAY_TMN_CODE` rỗng (`Vnpay.payUrl` không kiểm tra).
- **Hết hạn giữ chỗ trong E2E** dùng `page.clock` (đồng hồ trình duyệt chạy nhanh 16 phút), backend không đổi.

## Review Focus

Những tình huống tài liệu không nói rõ nhưng dễ gây lỗi thật, xếp theo khả năng gặp. Mỗi dòng đã có test ghim lại.

1. **Bấm đúp "Giữ chỗ".** Khách bấm hai lần nhanh chỉ được một booking, không giữ hai lần ghế và voucher (Task 2 E2E: `dblclick` rồi đếm `GET /api/bookings` còn đúng 1; Task 4 đếm lại trên C-08).
2. **Báo giá về sai thứ tự.** Khách chọn hành lý rồi bỏ ngay; phản hồi của lần chọn về sau phản hồi của lần bỏ. Bảng giá phải theo lần sửa cuối (Task 2 E2E: `page.route` làm chậm báo giá có hành lý 1,5 giây, tổng vẫn là `990.000 đ`).
3. **Trình duyệt ở múi giờ khác sân bay.** Khách ở nước ngoài vẫn thấy VJ123 cất cánh 08:00 giờ Hà Nội trên C-07, C-09, C-08, và đếm ngược đúng (cả file `booking.spec.ts` chạy với `timezoneId: "America/New_York"`).
4. **Hết hạn giữ chỗ khi đang mở C-09.** Đếm ngược về 0 thì nút "Thanh toán" biến mất và có dòng "Đã hết hạn giữ chỗ." mà không cần tải lại (Task 3 E2E dùng `page.clock.fastForward("16:00")`).
5. **Họ tên có dấu, chữ Đ, khoảng trắng thừa.** " Nguyễn văn Đức " phải gửi thành `NGUYEN` / `VAN DUC` (BR-12), không bị backend từ chối (Task 1 unit `asciiUpper`, `bookingRequest`; Task 2 E2E kiểm giá trị ô sau khi rời ô).

Đã kiểm thêm, ngoài 5 dòng trên: Enter trong ô voucher không giữ chỗ (Task 2), `sessionStorage` hỏng không làm vỡ trang (Task 1 `parseSelection`), chọn vé khi chưa đăng nhập rồi đăng nhập vẫn còn lựa chọn (Task 2).

---

## Trước khi bắt đầu

- **Cần có:** Plan 04, 08, 12 đã xong; Node 24; Docker Desktop đang chạy.
- **Nhánh:** tạo `feature/booking-screens` từ `develop`.
- **Chạy lệnh ở đâu:** Git Bash, từ thư mục gốc repo, trừ khi bước đó ghi `cd frontend`.
- **Backend cho E2E:** mọi bước E2E cần backend profile `dev` ở cổng 8080 với seed `V104`–`V106` còn mới. Mở một terminal riêng và để chạy suốt plan:

```bash
docker compose up -d postgres mailpit
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Kiểm tra seed còn hạn như Plan 08: `curl -s "http://localhost:8080/api/flights/search?from=HAN&to=SGN&date=$(TZ=Asia/Ho_Chi_Minh date -d tomorrow +%F)&adults=1&cabin=ECONOMY" | grep -o '"flightNumber":"VJ123"'` phải in ra một dòng. Không có thì làm mới DB bằng `docker compose down -v` rồi chạy lại hai lệnh trên.

`VNPAY_PAY_URL` trong `.env` phải là mặc định `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html` (E2E giả lập đúng địa chỉ này). Không cần `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`.

- **Dữ liệu seed E2E dựa vào:** ngày mai có VJ123 HAN→SGN 08:00–10:10, gói Eco 990.000 đ; QH201 HAN→SGN, gói Economy Saver; VN651 SGN→SIN 09:30, gói Economy Lite (`V106`). Hành lý VJ xếp theo cân nặng 15, 20, 25, 30 kg, mức 20 kg giá 350.000 đ (`V103`). `WELCOME10` giảm 10%, tối đa 200.000 đ, mỗi tài khoản dùng một lần (`V104`, BR-30 điều kiện 5). Tài khoản `customer@demo.local` / `Demo@1234` (`V105`, số điện thoại `0900000003`).
- **Chạy E2E:** `cd frontend && npx playwright test <tên file>`; Playwright tự bật `npm run dev` ở cổng 3000 (hoặc dùng lại server đang chạy).
- **Trạng thái kiểm chứng:** code trong plan viết theo code thật của Plan 03–12 (đọc ngày 2026-10-15) nhưng **chưa chạy thử**. Ở mỗi bước "Run", nếu kết quả khác "Expected" thì tìm nguyên nhân, sửa code và sửa luôn plan cho khớp, rồi mới đi tiếp.

## Cấu trúc file sau plan

```
frontend/
  e2e/
    booking-request.spec.ts            # Task 1: unit test logic C-07, C-08, C-09 (không mở trình duyệt)
    booking.spec.ts                    # Task 2 (C-07), Task 3 (C-09), Task 4 (C-08)
    smoke.spec.ts                      # Task 3: + /bookings/ZZZZZZ vào PROTECTED
  src/
    lib/
      booking.ts                       # Task 1: nhãn trạng thái, dmy, clock, countdown, useNow
      selection.ts                     # Task 1: + parseSelection, useSelection, clearSelection
    components/
      form.tsx                         # Task 2: + Note (tách từ Field), SelectField
      price-table.tsx                  # Task 2: bảng giá dùng chung C-07, C-09
      customer-header.tsx              # Task 2: đầu trang C-07, C-08, C-09
    app/
      checkout/
        page.tsx                       # Task 2: thay trang tạm bằng CheckoutView
        booking-request.ts             # Task 1: asciiUpper, passengerTypes, isInternational, bagField, bookingRequest, searchUrl
        checkout-view.tsx              # Task 2: tải lựa chọn, tài khoản, sân bay, bảng giá hành lý
        checkout-form.tsx              # Task 2: form, báo giá, voucher, giữ chỗ
      flights/
        flight-results.tsx             # Task 2: thông báo ?unavailable=1
        itineraries.ts                 # Task 3: + fareRules (chuyển từ itinerary-card.tsx)
        itinerary-card.tsx             # Task 3: dùng fareRules từ itineraries.ts
      bookings/
        page.tsx                       # Task 4: Suspense + BookingList (thay trang tạm)
        booking-list.tsx               # Task 4: C-08
        [code]/
          page.tsx                     # Task 3: Suspense + BookingView
          booking-view.tsx             # Task 3: C-09
```

## API dùng trong plan (đã có, không đổi)

| Method | Đường dẫn                              | Dùng ở                                        |
| ------ | -------------------------------------- | --------------------------------------------- |
| GET    | `/api/me`                              | C-07: điền sẵn liên hệ                        |
| GET    | `/api/airports`                        | C-07: quốc gia của sân bay (BR-13)            |
| GET    | `/api/airlines/{code}/baggage-options` | C-07: mức hành lý đang bán, xếp theo cân nặng |
| POST   | `/api/bookings/quote`                  | C-07: bảng giá (200 `QuoteResponse`)          |
| POST   | `/api/bookings`                        | C-07: giữ chỗ (201 `BookingCreatedResponse`)  |
| GET    | `/api/bookings?status=&page=&size=`    | C-08 (`page` của API đếm từ 0)                |
| GET    | `/api/bookings/{code}`                 | C-09 (của người khác hoặc không có: 404)      |
| POST   | `/api/bookings/{code}/payments`        | C-09: 201 `{paymentUrl}` rồi chuyển trang     |
| POST   | `/api/bookings/{code}/cancel`          | C-09: 204                                     |

Backend chuyển trình duyệt về `/bookings/{code}?payment=success|failed` hoặc `/bookings?payment=failed` sau VNPay (Plan 12).

## Mã lỗi và cách hiện

| Mã                                                                 | Ở đâu   | Hiện thế nào                                                                                                          |
| ------------------------------------------------------------------ | ------- | --------------------------------------------------------------------------------------------------------------------- |
| 401 (mọi mã)                                                       | mọi màn | `router.replace("/login?next=...")`                                                                                   |
| `PASSENGER_RULES_VIOLATED`, `VALIDATION_FAILED` (400, có `errors`) | C-07    | Lỗi dưới đúng ô (tên ô = `field`); lỗi `passengers` (BR-11) và `baggage[i]...` liệt kê trong khung thông báo cạnh nút |
| `VOUCHER_INVALID` (409)                                            | C-07    | Câu của backend dưới ô voucher, bỏ mã, tính lại giá                                                                   |
| `SEATS_UNAVAILABLE`, `FLIGHT_NOT_BOOKABLE`, `ITINERARY_INVALID`    | C-07    | Báo giá: khung thông báo kèm link "Chọn chuyến khác". Giữ chỗ: mở C-02 với `unavailable=1`                            |
| `RESOURCE_NOT_FOUND` (404)                                         | C-09    | Tiêu đề "Không tìm thấy đặt chỗ." (BR-104)                                                                            |
| `HOLD_EXPIRED`, `INVALID_STATE` (409)                              | C-09    | Câu của backend, tải lại booking                                                                                      |

---

### Task 1: Logic thuần cho đặt vé và booking

**Files:**

- Create: `frontend/src/app/checkout/booking-request.ts`, `frontend/src/lib/booking.ts`
- Modify: `frontend/src/lib/selection.ts`
- Test: `frontend/e2e/booking-request.spec.ts`

**Interfaces:**

- Consumes: `Schemas` của `src/lib/api.ts`; `Airport` của `src/lib/airports.ts` (`{code, countryCode, ...}`); `Selection`, `Journey`, `SELECTION_KEY` của `src/lib/selection.ts` (Plan 08).
- Produces:
  - `src/app/checkout/booking-request.ts`:
    - `type PassengerType = "ADULT" | "CHILD" | "INFANT"`.
    - `asciiUpper(s: string): string`: " Nguyễn văn Đức " → "NGUYEN VAN DUC".
    - `passengerTypes({adults, children, infants}): PassengerType[]`: người lớn, trẻ em, rồi em bé.
    - `isInternational(selection: Selection, airports: Airport[]): boolean` (BR-13).
    - `bagField(direction: "OUTBOUND" | "RETURN", index: number): string`: tên ô chọn hành lý, VD `"bag-OUTBOUND-0"`.
    - `bookingRequest(selection: Selection, data: FormData, voucherCode: string): Schemas["BookingRequest"]`. Đọc các ô `passengers[i].lastName|firstName|gender|dateOfBirth|nationality|passportNumber|passportExpiry`, `contact.name|email|phone`, `bagField(...)`.
    - `searchUrl(selection: Selection): string`: URL C-02 của lựa chọn, VD `/flights?from=HAN&to=SGN&date=2026-12-01&adults=1&children=0&infants=0&cabin=ECONOMY` (khứ hồi có `returnDate` sau `date`).
  - `src/lib/booking.ts`: `type BookingStatus`; `STATUS_LABEL: Record<BookingStatus, string>`; `DIRECTION_LABEL: Record<"OUTBOUND" | "RETURN", string>`; `PASSENGER_LABEL: Record<PassengerType, string>`; `dmy(iso): string` ("16/10/2026"); `clock(iso): string` ("08:00"); `countdown(ms: number): string` ("14:05"); `useNow(): number | null` (ms, cập nhật mỗi giây, `null` khi prerender).
  - `src/lib/selection.ts` thêm: `parseSelection(raw: string | null): Selection | null`; `useSelection(): Selection | null | undefined` (`undefined` khi prerender); `clearSelection(): void`.

- [ ] **Step 1: Viết unit test**

Tạo `frontend/e2e/booking-request.spec.ts`:

```ts
import { expect, test } from "@playwright/test";
import {
  asciiUpper,
  bagField,
  bookingRequest,
  isInternational,
  passengerTypes,
  searchUrl,
} from "../src/app/checkout/booking-request";
import { clock, countdown, dmy } from "../src/lib/booking";
import {
  parseSelection,
  type Journey,
  type Selection,
} from "../src/lib/selection";

// Unit test cho logic của C-07, C-08, C-09: không mở trình duyệt, không cần backend.

const at = (airport: string, time: string) => ({
  airport,
  city: airport,
  time,
  timezone: "Asia/Ho_Chi_Minh",
});

const fare = {
  fareFamilyId: 7,
  name: "Economy Lite",
  cabinClass: "ECONOMY" as const,
  carryOnKg: 10,
  checkedBaggageKg: 0,
  refundable: false,
  refundFee: 0,
  changeable: true,
  changeFee: 600_000,
  adultPrice: 1_290_000,
  totalPrice: 1_290_000,
  seatsLeft: 9,
};

/** Một chiều gồm các chặng [flightId, đi, đến, giờ đi, giờ đến]. */
function journey(
  direction: Journey["direction"],
  legs: [number, string, string, string, string][],
): Journey {
  const segments = legs.map(([flightId, from, to, dep, arr]) => ({
    flightId,
    flightNumber: `VN${flightId}`,
    airline: { code: "VN", name: "Vietnam Airlines" },
    departure: at(from, dep),
    arrival: at(to, arr),
    durationMinutes: 120,
    aircraftModel: "Airbus A321",
    layoverMinutesAfter: 0,
  }));
  return {
    direction,
    flightIds: segments.map((s) => s.flightId),
    fareFamilyId: fare.fareFamilyId,
    itinerary: {
      stops: segments.length - 1,
      totalDurationMinutes: 120 * segments.length,
      segments,
      fareOptions: [fare],
    },
    fare,
  };
}

const domestic: Selection = {
  adults: 2,
  children: 1,
  infants: 1,
  journeys: [
    journey("OUTBOUND", [
      [
        213,
        "HAN",
        "SGN",
        "2026-12-01T06:00:00+07:00",
        "2026-12-01T08:10:00+07:00",
      ],
    ]),
  ],
};

const roundTrip: Selection = {
  adults: 1,
  children: 0,
  infants: 0,
  journeys: [
    journey("OUTBOUND", [
      [
        651,
        "SGN",
        "SIN",
        "2026-12-01T09:30:00+07:00",
        "2026-12-01T12:30:00+08:00",
      ],
    ]),
    journey("RETURN", [
      [
        652,
        "SIN",
        "SGN",
        "2026-12-05T14:00:00+08:00",
        "2026-12-05T15:00:00+07:00",
      ],
    ]),
  ],
};

const airports = [
  ["HAN", "VN"],
  ["SGN", "VN"],
  ["SIN", "SG"],
].map(([code, countryCode]) => ({
  code,
  countryCode,
  name: code,
  city: code,
  timezone: "Asia/Ho_Chi_Minh",
  active: true,
}));

test("asciiUpper: bỏ dấu, Đ thành D, viết hoa, gộp khoảng trắng (BR-12)", () => {
  expect(asciiUpper("  Nguyễn  văn Đức ")).toBe("NGUYEN VAN DUC");
  expect(asciiUpper("đặng thị ánh")).toBe("DANG THI ANH");
  expect(asciiUpper("Lê Hoàng Phúc")).toBe("LE HOANG PHUC");
});

test("passengerTypes: người lớn, rồi trẻ em, rồi em bé", () => {
  expect(passengerTypes(domestic)).toEqual([
    "ADULT",
    "ADULT",
    "CHILD",
    "INFANT",
  ]);
});

test("isInternational: có chặng mà hai sân bay khác quốc gia (BR-13)", () => {
  expect(isInternational(domestic, airports)).toBe(false);
  expect(isInternational(roundTrip, airports)).toBe(true);
});

test("bookingRequest: chuyến nội địa, có hành lý và voucher (TDD §8.8)", () => {
  const family: Selection = { ...domestic, adults: 1, children: 0, infants: 1 };
  const form = new FormData();
  const pax = (i: number, values: Record<string, string>) =>
    Object.entries(values).forEach(([k, v]) =>
      form.set(`passengers[${i}].${k}`, v),
    );
  pax(0, {
    lastName: "Nguyễn",
    firstName: " văn  an ",
    gender: "MALE",
    dateOfBirth: "1990-05-20",
  });
  pax(1, {
    lastName: "nguyễn",
    firstName: "bé na",
    gender: "FEMALE",
    dateOfBirth: "2026-01-15",
  });
  form.set(bagField("OUTBOUND", 0), "12");
  form.set("contact.name", " Trần Minh Khoa ");
  form.set("contact.email", "khoa@example.com");
  form.set("contact.phone", "0912345678");

  expect(bookingRequest(family, form, "WELCOME10")).toEqual({
    journeys: [{ direction: "OUTBOUND", flightIds: [213], fareFamilyId: 7 }],
    passengers: [
      {
        type: "ADULT",
        lastName: "NGUYEN",
        firstName: "VAN AN",
        gender: "MALE",
        dateOfBirth: "1990-05-20",
      },
      {
        type: "INFANT",
        lastName: "NGUYEN",
        firstName: "BE NA",
        gender: "FEMALE",
        dateOfBirth: "2026-01-15",
      },
    ],
    baggage: [
      { passengerIndex: 0, direction: "OUTBOUND", baggageOptionId: 12 },
    ],
    contact: {
      name: "Trần Minh Khoa",
      email: "khoa@example.com",
      phone: "0912345678",
    },
    voucherCode: "WELCOME10",
  });
});

test("bookingRequest: khứ hồi quốc tế, hộ chiếu viết hoa, không voucher", () => {
  const form = new FormData();
  form.set("passengers[0].lastName", "tran");
  form.set("passengers[0].firstName", "thi mai");
  form.set("passengers[0].gender", "FEMALE");
  form.set("passengers[0].dateOfBirth", "1985-02-14");
  form.set("passengers[0].nationality", "vn");
  form.set("passengers[0].passportNumber", "c1234567");
  form.set("passengers[0].passportExpiry", "2030-01-01");
  form.set(bagField("OUTBOUND", 0), "");
  form.set(bagField("RETURN", 0), "31");

  const body = bookingRequest(roundTrip, form, "");
  expect(body.journeys.map((j) => j.direction)).toEqual(["OUTBOUND", "RETURN"]);
  expect(body.passengers[0]).toEqual({
    type: "ADULT",
    lastName: "TRAN",
    firstName: "THI MAI",
    gender: "FEMALE",
    dateOfBirth: "1985-02-14",
    nationality: "VN",
    passportNumber: "C1234567",
    passportExpiry: "2030-01-01",
  });
  expect(body.baggage).toEqual([
    { passengerIndex: 0, direction: "RETURN", baggageOptionId: 31 },
  ]);
  expect(body.voucherCode).toBeUndefined();
});

test("searchUrl: mở lại C-02 với đúng điều kiện tìm", () => {
  expect(searchUrl(domestic)).toBe(
    "/flights?from=HAN&to=SGN&date=2026-12-01&adults=2&children=1&infants=1&cabin=ECONOMY",
  );
  expect(searchUrl(roundTrip)).toBe(
    "/flights?from=SGN&to=SIN&date=2026-12-01&returnDate=2026-12-05&adults=1&children=0&infants=0&cabin=ECONOMY",
  );
});

test("parseSelection: thiếu hoặc hỏng thì là null", () => {
  for (const raw of [null, "", "{oops", "null", "5", "{}"]) {
    expect(parseSelection(raw)).toBeNull();
  }
  expect(
    parseSelection(JSON.stringify({ ...domestic, journeys: [] })),
  ).toBeNull();
  expect(parseSelection(JSON.stringify(domestic))).toEqual(domestic);
});

test("dmy, clock: đọc thẳng từ chuỗi có offset", () => {
  expect(dmy("2026-12-01T23:30:00+07:00")).toBe("01/12/2026");
  expect(clock("2026-12-01T23:30:00+07:00")).toBe("23:30");
  expect(dmy("1990-05-20")).toBe("20/05/1990");
});

test("countdown: mm:ss, hết giờ là 00:00", () => {
  expect(countdown(15 * 60_000)).toBe("15:00");
  expect(countdown(61_999)).toBe("01:01");
  expect(countdown(-5_000)).toBe("00:00");
});
```

- [ ] **Step 2: Chạy test để thấy nó đỏ**

```bash
cd frontend && npx playwright test booking-request
```

Expected: lỗi nạp file, VD `Cannot find module '../src/app/checkout/booking-request'`.

- [ ] **Step 3: Viết `booking-request.ts`**

Tạo `frontend/src/app/checkout/booking-request.ts`:

```ts
// Dựng body của POST /bookings/quote và POST /bookings (TDD §8.8) từ lựa chọn ở C-02 và form C-07. Hàm thuần.
import type { Airport } from "@/lib/airports";
import type { Schemas } from "@/lib/api";
import type { Selection } from "@/lib/selection";

type PassengerRequest = Schemas["PassengerRequest"];
export type PassengerType = PassengerRequest["type"];
type Direction = Schemas["JourneyRequest"]["direction"];

/** TDD §7, BR-12: bỏ dấu, Đ thành D, viết hoa, các từ cách nhau một khoảng trắng. "Nguyễn  văn Đức " → "NGUYEN VAN DUC". */
export const asciiUpper = (s: string) =>
  s
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[đĐ]/g, "D")
    .toUpperCase()
    .trim()
    .replace(/\s+/g, " ");

/** Thứ tự khách trên form và trong request: người lớn, trẻ em, rồi em bé. `passengerIndex` của hành lý theo thứ tự này. */
export const passengerTypes = ({
  adults,
  children,
  infants,
}: Pick<Selection, "adults" | "children" | "infants">): PassengerType[] => [
  ...Array<PassengerType>(adults).fill("ADULT"),
  ...Array<PassengerType>(children).fill("CHILD"),
  ...Array<PassengerType>(infants).fill("INFANT"),
];

/** BR-13: có chặng mà sân bay đi và sân bay đến thuộc hai quốc gia khác nhau. */
export function isInternational(selection: Selection, airports: Airport[]) {
  const country = new Map(airports.map((a) => [a.code, a.countryCode]));
  return selection.journeys.some((j) =>
    j.itinerary.segments.some(
      (s) =>
        country.get(s.departure.airport) !== country.get(s.arrival.airport),
    ),
  );
}

/** Tên ô chọn hành lý của khách thứ `index` ở một chiều. */
export const bagField = (direction: Direction, index: number) =>
  `bag-${direction}-${index}`;

const text = (data: FormData, name: string) =>
  String(data.get(name) ?? "").trim();

/** `voucherCode` rỗng là không dùng voucher. Trường để trống thì là undefined, JSON bỏ trường đó. */
export function bookingRequest(
  selection: Selection,
  data: FormData,
  voucherCode: string,
): Schemas["BookingRequest"] {
  const types = passengerTypes(selection);
  return {
    journeys: selection.journeys.map(
      ({ direction, flightIds, fareFamilyId }) => ({
        direction,
        flightIds,
        fareFamilyId,
      }),
    ),
    passengers: types.map((type, i) => {
      const field = (k: string) => text(data, `passengers[${i}].${k}`);
      return {
        type,
        lastName: asciiUpper(field("lastName")),
        firstName: asciiUpper(field("firstName")),
        gender: field("gender") as PassengerRequest["gender"],
        dateOfBirth: field("dateOfBirth"),
        // Ba ô dưới chỉ có khi hành trình quốc tế (BR-13).
        nationality: field("nationality").toUpperCase() || undefined,
        passportNumber: field("passportNumber").toUpperCase() || undefined,
        passportExpiry: field("passportExpiry") || undefined,
      };
    }),
    baggage: selection.journeys.flatMap(({ direction }) =>
      types.flatMap((_, i) => {
        const id = text(data, bagField(direction, i));
        return id
          ? [{ passengerIndex: i, direction, baggageOptionId: Number(id) }]
          : [];
      }),
    ),
    contact: {
      name: text(data, "contact.name"),
      email: text(data, "contact.email"),
      phone: text(data, "contact.phone"),
    },
    voucherCode: voucherCode || undefined,
  };
}

/** C-02 của lựa chọn này, cùng dạng URL mà C-01 tạo: để chọn lại khi chuyến đã chọn không còn bán (APP_FLOW C-07). */
export function searchUrl(selection: Selection) {
  const [out, ret] = selection.journeys;
  const segs = out.itinerary.segments;
  const p = new URLSearchParams({
    from: segs[0].departure.airport,
    to: segs[segs.length - 1].arrival.airport,
    date: segs[0].departure.time.slice(0, 10),
  });
  if (ret)
    p.set("returnDate", ret.itinerary.segments[0].departure.time.slice(0, 10));
  p.set("adults", String(selection.adults));
  p.set("children", String(selection.children));
  p.set("infants", String(selection.infants));
  p.set("cabin", out.fare.cabinClass);
  return `/flights?${p}`;
}
```

Bộ lọc dấu dùng dải `̀-ͯ` như `norm` của `search-dock.tsx`: `\p{M}` cần target ES2018, còn `tsconfig.json` đang là ES2017.

- [ ] **Step 4: Viết `src/lib/booking.ts`**

Tạo `frontend/src/lib/booking.ts`:

```ts
import { useSyncExternalStore } from "react";
import type { Schemas } from "./api";

export type BookingStatus = Schemas["BookingDetail"]["status"];

/** APP_FLOW §4.1. */
export const STATUS_LABEL: Record<BookingStatus, string> = {
  PENDING_PAYMENT: "Chờ thanh toán",
  ISSUED: "Đã xuất vé",
  EXPIRED: "Hết hạn giữ chỗ",
  CANCELLED: "Đã huỷ",
  REFUNDED: "Đã hoàn vé",
};

export const DIRECTION_LABEL: Record<
  Schemas["JourneyRequest"]["direction"],
  string
> = { OUTBOUND: "Chiều đi", RETURN: "Chiều về" };

export const PASSENGER_LABEL: Record<
  Schemas["PassengerRequest"]["type"],
  string
> = { ADULT: "Người lớn", CHILD: "Trẻ em", INFANT: "Em bé" };

/**
 * Ngày và giờ đọc thẳng từ chuỗi có offset của API (giờ sân bay, hoặc giờ Việt Nam cho các mốc của booking), không qua
 * Date nên không dính múi giờ trình duyệt. "2026-10-16T08:00:00+07:00" → "16/10/2026" và "08:00". `dmy` nhận cả chuỗi
 * chỉ có ngày ("1990-05-20").
 */
export const dmy = (iso: string) =>
  `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}`;
export const clock = (iso: string) => iso.slice(11, 16);

const two = (n: number) => String(n).padStart(2, "0");

/** Thời gian còn lại dạng "mm:ss"; hết giờ là "00:00". */
export function countdown(ms: number) {
  const s = Math.max(0, Math.floor(ms / 1000));
  return `${two(Math.floor(s / 60))}:${two(s % 60)}`;
}

const everySecond = (tick: () => void) => {
  const id = setInterval(tick, 1000);
  return () => clearInterval(id);
};

/**
 * Giờ hiện tại (ms, tròn giây), cập nhật mỗi giây sau khi hydrate. Ở server trả null: Cache Components không cho đọc
 * giờ khi prerender. Snapshot tròn giây nên giữ nguyên giữa hai lần React đọc trong cùng một giây.
 */
export function useNow() {
  return useSyncExternalStore<number | null>(
    everySecond,
    () => Math.floor(Date.now() / 1000) * 1000,
    () => null,
  );
}
```

- [ ] **Step 5: Thêm hàm đọc lựa chọn vào `selection.ts`**

Trong `frontend/src/lib/selection.ts`, thay dòng đầu:

```ts
import type { Schemas } from "./api";
```

bằng:

```ts
import { useMemo, useSyncExternalStore } from "react";
import type { Schemas } from "./api";
```

rồi thêm vào cuối file:

```ts
/** Lựa chọn đã lưu; null khi không có hoặc hỏng (VD ai đó sửa tay sessionStorage). */
export function parseSelection(raw: string | null): Selection | null {
  if (!raw) return null;
  try {
    const s = JSON.parse(raw) as Selection | null;
    return s && Array.isArray(s.journeys) && s.journeys.length > 0 ? s : null;
  } catch {
    return null;
  }
}

const subscribeNone = () => () => {};

/** C-07 đọc lựa chọn sau khi hydrate: undefined khi prerender (server không có sessionStorage), null khi không có. */
export function useSelection() {
  const raw = useSyncExternalStore<string | null | undefined>(
    subscribeNone,
    () => sessionStorage.getItem(SELECTION_KEY),
    () => undefined,
  );
  return useMemo(
    () => (raw === undefined ? undefined : parseSelection(raw)),
    [raw],
  );
}

/** Giữ chỗ xong thì xoá, để mở lại /checkout không giữ chỗ lần nữa được. */
export function clearSelection() {
  sessionStorage.removeItem(SELECTION_KEY);
}
```

Sửa luôn comment đầu file: thay `để C-07 (Plan 13) đọc lại` bằng `để C-07 đọc lại (useSelection)`.

- [ ] **Step 6: Chạy test để thấy nó xanh**

```bash
cd frontend && npx playwright test booking-request
```

Expected: `9 passed`.

- [ ] **Step 7: Lint**

```bash
cd frontend && npx prettier --write src/app/checkout/booking-request.ts src/lib/booking.ts src/lib/selection.ts e2e/booking-request.spec.ts && npm run lint
```

Expected: lint không báo lỗi.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/checkout/booking-request.ts frontend/src/lib/booking.ts frontend/src/lib/selection.ts frontend/e2e/booking-request.spec.ts
git commit -m "feat(frontend): logic dựng body đặt vé, đọc lựa chọn và định dạng booking"
```

---

### Task 2: Màn đặt vé (C-07)

**Files:**

- Create: `frontend/src/components/price-table.tsx`, `frontend/src/components/customer-header.tsx`, `frontend/src/app/checkout/checkout-view.tsx`, `frontend/src/app/checkout/checkout-form.tsx`
- Modify: `frontend/src/components/form.tsx` (tách `Note` khỏi `Field`, thêm `SelectField`), `frontend/src/app/checkout/page.tsx` (viết lại), `frontend/src/app/flights/flight-results.tsx` (thông báo `unavailable`)
- Test: `frontend/e2e/booking.spec.ts` (tạo mới)

**Interfaces:**

- Consumes: Task 1 (`asciiUpper`, `bagField`, `bookingRequest`, `isInternational`, `passengerTypes`, `searchUrl`, `PassengerType`, `DIRECTION_LABEL`, `PASSENGER_LABEL`, `dmy`, `useSelection`, `clearSelection`); `useAirports()` (Plan 08); `Field`, `Notice`, `FormSkeleton`, `pillDark`, `pillLine`, `textLink` (Plan 04); `Brand` (`src/components/auth-shell.tsx`); `first`, `last`, `hhmm`, `duration`, `vnd` (`src/app/flights/itineraries.ts`).
- Produces:
  - `SelectField` trong `src/components/form.tsx`: props của `<select>` cộng `label`, `name`, `error?`, `hint?`; lỗi `required` báo "Hãy chọn {nhãn viết thường}.".
  - `PriceTable({ rows: [string, number][], total: number })` (default export của `src/components/price-table.tsx`): `<dl>`, số âm hiện `-` (giảm giá), dòng cuối "Tổng thanh toán".
  - `CustomerHeader()` (default export của `src/components/customer-header.tsx`): logo và link "Tìm chuyến bay", "Đặt chỗ của tôi", "Tài khoản".
  - `/checkout` (C-07). Giữ chỗ thành công mở `/bookings/{code}` (Task 3 làm màn này). C-02 hiện "Chuyến bay bạn chọn không còn đủ chỗ hoặc đã ngừng bán. Hãy chọn chuyến khác." khi URL có `unavailable`.

- [ ] **Step 1: Viết E2E**

Tạo `frontend/e2e/booking.spec.ts`:

```ts
import {
  expect,
  test,
  type Locator,
  type Page,
  type Request,
} from "@playwright/test";

// C-07, C-08, C-09 trên backend thật (profile dev). Seed: ngày mai có VJ123 HAN→SGN 08:00–10:10 gói Eco 990.000 đ,
// QH201 HAN→SGN, VN651 SGN→SIN (V106); hành lý VJ 15, 20, 25, 30 kg, mức 20 kg 350.000 đ (V103); WELCOME10 giảm 10%
// (V104); customer@demo.local (V105). Trình duyệt ở múi giờ New York: giờ trên màn hình phải là giờ sân bay (TDD §7).
test.use({ timezoneId: "America/New_York" });

/** Ngày theo giờ Việt Nam, n ngày sau hôm nay (yyyy-mm-dd). */
const day = (n: number) =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh" }).format(
    Date.now() + n * 86_400_000,
  );

const search = (q: Record<string, string>) =>
  "/flights?" +
  new URLSearchParams({
    adults: "1",
    children: "0",
    infants: "0",
    cabin: "ECONOMY",
    ...q,
  });

const field = (scope: Page | Locator, label: string) =>
  scope.getByLabel(label, { exact: true });

const group = (page: Page, name: string) =>
  page.getByRole("group", { name, exact: true });

async function login(page: Page, email: string, password: string) {
  await field(page, "Email").fill(email);
  await field(page, "Mật khẩu").fill(password);
  await page.getByRole("button", { name: "Đăng nhập" }).click();
}

/** Mở chi tiết một hành trình trên C-02 rồi chọn gói. */
async function choose(page: Page, card: string, fare: string) {
  const it = page.getByRole("article", { name: card });
  await it.getByRole("button", { name: "Chọn", exact: true }).click();
  await it.getByRole("button", { name: `Chọn gói ${fare}` }).click();
}

async function fillPassenger(
  scope: Locator,
  p: { last: string; first: string; gender: "Nam" | "Nữ"; dob: string },
) {
  await field(scope, "Họ").fill(p.last);
  await field(scope, "Tên đệm và tên").fill(p.first);
  await field(scope, "Giới tính").selectOption({ label: p.gender });
  await field(scope, "Ngày sinh").fill(p.dob);
}

const isBagQuote = (r: Request) =>
  r.method() === "POST" &&
  r.url().endsWith("/api/bookings/quote") &&
  r.postDataJSON().baggage.length > 0;

test("chọn vé, đăng nhập, khai hành khách, hành lý, voucher rồi giữ chỗ", async ({
  page,
}) => {
  test.slow();
  // Tài khoản mới mỗi lần chạy: WELCOME10 chỉ dùng được một lần mỗi tài khoản (BR-30 điều kiện 5).
  const email = `e2e-${test.info().testId}-${Date.now()}@example.com`;
  await page.goto("/register");
  await field(page, "Họ và tên").fill("Trần Minh Khoa");
  await field(page, "Email").fill(email);
  await field(page, "Số điện thoại").fill("0912345678");
  await field(page, "Mật khẩu").fill("matkhau1");
  await page.getByRole("button", { name: "Tạo tài khoản" }).click();
  await expect(page).toHaveURL("/bookings");

  // UF-01: chọn vé khi chưa đăng nhập, đăng nhập xong quay lại C-07 với lựa chọn còn nguyên.
  await page.context().clearCookies();
  await page.goto(search({ from: "HAN", to: "SGN", date: day(1) }));
  await choose(page, "Vietjet Air VJ123", "Eco");
  await expect(page).toHaveURL("/login?next=%2Fcheckout");
  await login(page, email, "matkhau1");
  await expect(page).toHaveURL("/checkout");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Hoàn tất đặt vé.",
  );
  await expect(page.getByRole("region", { name: "Hành trình" })).toContainText(
    "VJ123: 08:00 HAN → 10:10 SGN",
  );

  // Liên hệ điền sẵn từ tài khoản (FR-21); chuyến nội địa không hỏi hộ chiếu (BR-13).
  await expect(field(page, "Họ tên người liên hệ")).toHaveValue(
    "Trần Minh Khoa",
  );
  await expect(page.getByLabel("Số hộ chiếu")).toHaveCount(0);

  // Họ tên có dấu được bỏ dấu và viết hoa khi rời ô (BR-12, TDD §7).
  const adult = group(page, "Người lớn 1");
  await fillPassenger(adult, {
    last: "nguyễn",
    first: "văn  đức",
    gender: "Nam",
    dob: "1990-05-20",
  });
  await expect(field(adult, "Họ")).toHaveValue("NGUYEN");
  await expect(field(adult, "Tên đệm và tên")).toHaveValue("VAN DUC");

  // Đủ thông tin thì tự báo giá (FR-20).
  const prices = page.getByRole("region", { name: "Bảng giá" });
  const total = prices.getByRole("definition").last();
  await expect(prices).toContainText("Chiều đi, người lớn × 1");
  await expect(total).toHaveText("990.000 đ");

  // Báo giá về sai thứ tự: phản hồi về trễ của lần sửa trước không được đè lần sửa sau.
  const bag = group(page, "Hành lý chiều đi").getByLabel("Người lớn 1", {
    exact: true,
  });
  await page.route("**/api/bookings/quote", async (route) => {
    if (isBagQuote(route.request())) {
      await new Promise((r) => setTimeout(r, 1500));
    }
    await route.continue();
  });
  const slowSent = page.waitForRequest(isBagQuote);
  const slowDone = page.waitForResponse((r) => isBagQuote(r.request()));
  await bag.selectOption({ index: 2 }); // 20 kg
  await slowSent;
  await bag.selectOption({ index: 0 }); // Không mua thêm
  await slowDone;
  await page.waitForTimeout(500); // để trang xử lý xong phản hồi về trễ
  await expect(total).toHaveText("990.000 đ");
  await page.unroute("**/api/bookings/quote");

  await bag.selectOption({ index: 2 });
  await expect(prices).toContainText("Chiều đi, hành lý");
  await expect(total).toHaveText("1.340.000 đ");

  // Voucher sai báo ngay dưới ô; Enter trong ô voucher không giữ chỗ (FR-23).
  const voucher = field(page, "Mã giảm giá");
  await voucher.fill("KHONGCO");
  await voucher.press("Enter");
  await expect(page.getByText("Mã giảm giá không tồn tại")).toBeVisible();
  await expect(page).toHaveURL("/checkout");
  await voucher.fill("welcome10");
  await page.getByRole("button", { name: "Áp dụng" }).click();
  await expect(prices).toContainText("Giảm giá WELCOME10");
  await expect(total).toHaveText("1.206.000 đ");

  // Bấm đúp "Giữ chỗ" chỉ tạo một booking (FR-24).
  await page.getByRole("button", { name: "Giữ chỗ", exact: true }).dblclick();
  await expect(page).toHaveURL(/\/bookings\/[A-Z0-9]{6}$/);
  const code = page.url().slice(-6);
  const mine = await (await page.request.get("/api/bookings")).json();
  expect(mine.content.map((b: { code: string }) => b.code)).toEqual([code]);

  // Lựa chọn đã dùng thì xoá: mở lại C-07 không giữ chỗ lần nữa được.
  await page.goto("/checkout");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Chưa có chuyến bay nào được chọn.",
  );
});

test("hành trình quốc tế hỏi hộ chiếu, báo giá đánh dấu đúng ô sai", async ({
  page,
}) => {
  await page.goto(
    search({ from: "SGN", to: "SIN", date: day(1), children: "1" }),
  );
  await choose(page, "Vietnam Airlines VN651", "Economy Lite");
  await expect(page).toHaveURL("/login?next=%2Fcheckout");
  await login(page, "customer@demo.local", "Demo@1234");
  await expect(page).toHaveURL("/checkout");

  const adult = group(page, "Người lớn 1");
  const child = group(page, "Trẻ em 1");
  await fillPassenger(adult, {
    last: "tran",
    first: "thi mai",
    gender: "Nữ",
    dob: "1985-02-14",
  });
  await field(adult, "Quốc tịch").fill("vn");
  await field(adult, "Số hộ chiếu").fill("c1234567");
  await field(adult, "Ngày hết hạn hộ chiếu").fill(day(800));
  await expect(field(adult, "Quốc tịch")).toHaveValue("VN");
  await expect(field(adult, "Số hộ chiếu")).toHaveValue("C1234567");

  // Trẻ em khai ngày sinh của người lớn (BR-10), hộ chiếu chỉ còn 30 ngày (BR-13).
  await fillPassenger(child, {
    last: "tran",
    first: "minh an",
    gender: "Nam",
    dob: "1990-01-01",
  });
  await field(child, "Quốc tịch").fill("VN");
  await field(child, "Số hộ chiếu").fill("C7654321");
  await field(child, "Ngày hết hạn hộ chiếu").fill(day(30));

  await expect(
    child.getByText("Theo ngày sinh, hành khách là người lớn tại ngày bay"),
  ).toBeVisible();
  await expect(
    child.getByText(
      "Hộ chiếu phải còn hạn ít nhất 6 tháng tính từ ngày bay của chặng cuối",
    ),
  ).toBeVisible();
  await expect(field(child, "Ngày sinh")).toHaveAttribute(
    "aria-invalid",
    "true",
  );

  // Bấm "Giữ chỗ" vẫn ở lại C-07, giữ nguyên lỗi theo ô (APP_FLOW C-07).
  await page.getByRole("button", { name: "Giữ chỗ", exact: true }).click();
  await expect(
    page.getByText("Thông tin hành khách chưa hợp lệ"),
  ).toBeVisible();
  await expect(page).toHaveURL("/checkout");
});

test("chuyến không còn bán thì quay lại C-02 kèm thông báo", async ({
  page,
}) => {
  const d = day(1);
  await page.goto(search({ from: "HAN", to: "SGN", date: d }));
  await choose(page, "Bamboo Airways QH201", "Economy Saver");
  await expect(page).toHaveURL("/login?next=%2Fcheckout");
  await login(page, "customer@demo.local", "Demo@1234");
  await expect(page).toHaveURL("/checkout");

  // Giả lập chuyến bị gỡ sau khi khách chọn: id chuyến không tồn tại.
  await page.evaluate(() => {
    const s = JSON.parse(sessionStorage.getItem("skyline.selection") ?? "{}");
    s.journeys[0].flightIds = [2147483647];
    sessionStorage.setItem("skyline.selection", JSON.stringify(s));
  });
  await page.reload();
  await fillPassenger(group(page, "Người lớn 1"), {
    last: "le",
    first: "van binh",
    gender: "Nam",
    dob: "1988-08-08",
  });
  // Báo giá đã báo lỗi ngay khi đủ thông tin.
  await expect(
    page.getByText("Chuyến bay đã chọn không còn được bán"),
  ).toBeVisible();

  await page.getByRole("button", { name: "Giữ chỗ", exact: true }).click();
  await expect(page).toHaveURL(
    `/flights?from=HAN&to=SGN&date=${d}&adults=1&children=0&infants=0&cabin=ECONOMY&unavailable=1`,
  );
  await expect(
    page.getByText("Chuyến bay bạn chọn không còn đủ chỗ hoặc đã ngừng bán."),
  ).toBeVisible();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Chọn chuyến bay.",
  );
});
```

Ghi chú cho người làm:

- `getByRole("group", ...)` tìm `<fieldset>` theo `<legend>`. Nhãn "Người lớn 1" có ở cả nhóm hành khách lẫn ô chọn hành lý, nên ô hành lý tìm trong nhóm "Hành lý chiều đi".
- `selectOption({ index: 2 })` chọn mức 20 kg: lựa chọn đầu là "Không mua thêm", bảng giá hành lý xếp theo cân nặng. Không chọn theo nhãn vì nhãn có khoảng trắng không ngắt trước `đ`.
- `page.waitForTimeout(500)` là chờ có chủ đích: lỗi cần bắt chính là trang áp phản hồi về trễ sau khi Playwright đã nhận phản hồi đó.

- [ ] **Step 2: Chạy test để thấy nó đỏ**

```bash
cd frontend && npx playwright test booking.spec
```

Expected: `3 failed`. Test đầu và test thứ ba đỏ ở `toHaveText("Hoàn tất đặt vé.")` hoặc `getByLabel("Họ")` (trang tạm C-07 chưa có form), test thứ hai đỏ ở `getByLabel("Họ")`.

- [ ] **Step 3: Thêm `SelectField` vào `form.tsx`**

Trong `frontend/src/components/form.tsx`:

3a. Import. Thay:

```tsx
import {
  useId,
  useState,
  type FormEvent,
  type InputHTMLAttributes,
  type ReactNode,
} from "react";
```

bằng:

```tsx
import {
  useId,
  useState,
  type FormEvent,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
} from "react";
```

3b. Tách phần lỗi hoặc gợi ý dưới ô thành `Note`. Thay:

```tsx
/** Nhãn trên ô, lỗi dưới ô (DESIGN.md: ô nhập là khối vuông viền hairline, không bo góc). */
export function Field({
```

bằng:

```tsx
/** Dưới ô nhập: lỗi (ưu tiên) hoặc gợi ý. */
function Note({
  id,
  message,
  hint,
}: {
  id: string;
  message?: string;
  hint?: string;
}) {
  if (message) {
    return (
      <p
        id={id}
        role="alert"
        className="flex items-start gap-8 text-caption font-bold"
      >
        <Icon name="alert" className={`${iconClass} mt-2`} />
        {message}
      </p>
    );
  }
  return hint ? (
    <p id={id} className="text-caption text-muted">
      {hint}
    </p>
  ) : null;
}

/** Nhãn trên ô, lỗi dưới ô (DESIGN.md: ô nhập là khối vuông viền hairline, không bo góc). */
export function Field({
```

3c. Trong `Field`, thay:

```tsx
      {message ? (
        <p
          id={noteId}
          role="alert"
          className="flex items-start gap-8 text-caption font-bold"
        >
          <Icon name="alert" className={`${iconClass} mt-2`} />
          {message}
        </p>
      ) : (
        hint && (
          <p id={noteId} className="text-caption text-muted">
            {hint}
          </p>
        )
      )}
    </div>
  );
}
```

bằng:

```tsx
      <Note id={noteId} message={message} hint={hint} />
    </div>
  );
}

type SelectFieldProps = SelectHTMLAttributes<HTMLSelectElement> & {
  label: string;
  name: string;
  /** Lỗi từ backend cho trường này. */
  error?: string;
  hint?: string;
};

/** Như Field nhưng là ô chọn (giới tính, hành lý mua thêm). */
export function SelectField({
  label,
  error,
  hint,
  onChange,
  children,
  ...select
}: SelectFieldProps) {
  const id = useId();
  const [own, setOwn] = useState<string>();
  const message = own ?? error;
  const noteId = `${id}-note`;

  return (
    <div className="grid gap-8">
      <label htmlFor={id} className="text-caption font-bold">
        {label}
      </label>
      <div
        className={`flex border bg-paper transition-colors focus-within:outline-2 focus-within:-outline-offset-2 focus-within:outline-ink ${
          message ? "border-ink" : "border-line-strong"
        }`}
      >
        <select
          {...select}
          id={id}
          aria-invalid={message ? true : undefined}
          aria-describedby={message || hint ? noteId : undefined}
          className="min-w-0 flex-1 bg-transparent px-16 py-13 text-body text-ink outline-none"
          onInvalid={(e) => {
            e.preventDefault();
            setOwn(`Hãy chọn ${label.toLowerCase()}.`);
          }}
          onChange={(e) => {
            setOwn(undefined);
            onChange?.(e);
          }}
        >
          {children}
        </select>
      </div>
      <Note id={noteId} message={message} hint={hint} />
    </div>
  );
}
```

- [ ] **Step 4: Tạo bảng giá và đầu trang dùng chung**

Tạo `frontend/src/components/price-table.tsx`:

```tsx
import { vnd } from "@/app/flights/itineraries";

/** Bảng giá của C-07 và C-09: từng dòng, rồi tổng thanh toán. Số âm là giảm giá. */
export default function PriceTable({
  rows,
  total,
}: {
  rows: [string, number][];
  total: number;
}) {
  return (
    <dl className="grid gap-8">
      {rows.map(([label, amount], i) => (
        <div key={i} className="flex justify-between gap-16 text-caption">
          <dt className="text-muted">{label}</dt>
          <dd>{amount < 0 ? `-${vnd(-amount)}` : vnd(amount)}</dd>
        </div>
      ))}
      <div className="flex justify-between gap-16 border-t border-line pt-13 font-bold">
        <dt>Tổng thanh toán</dt>
        <dd>{vnd(total)}</dd>
      </div>
    </dl>
  );
}
```

Tạo `frontend/src/components/customer-header.tsx`:

```tsx
import Link from "next/link";
import { Brand } from "./auth-shell";

const LINKS = [
  ["/", "Tìm chuyến bay"],
  ["/bookings", "Đặt chỗ của tôi"],
  ["/account", "Tài khoản"],
] as const;

/** Đầu trang của C-07, C-08, C-09. */
export default function CustomerHeader() {
  return (
    <header className="mx-auto flex max-w-page flex-wrap items-center justify-between gap-16 px-16 py-22 sm:px-38">
      <Brand />
      <nav
        aria-label="Khách hàng"
        className="flex flex-wrap gap-x-22 gap-y-8 text-caption"
      >
        {LINKS.map(([href, label]) => (
          <Link
            key={href}
            href={href}
            className="text-muted transition-colors hover:text-ink"
          >
            {label}
          </Link>
        ))}
      </nav>
    </header>
  );
}
```

- [ ] **Step 5: Tạo form đặt vé**

Tạo `frontend/src/app/checkout/checkout-form.tsx`:

```tsx
"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState, type FocusEvent, type FormEvent } from "react";
import {
  Field,
  Notice,
  SelectField,
  pillDark,
  pillLine,
  textLink,
} from "@/components/form";
import PriceTable from "@/components/price-table";
import { ApiError, apiFetch, type Schemas, type User } from "@/lib/api";
import { DIRECTION_LABEL, PASSENGER_LABEL, dmy } from "@/lib/booking";
import { clearSelection, type Journey, type Selection } from "@/lib/selection";
import { duration, first, hhmm, last, vnd } from "../flights/itineraries";
import {
  asciiUpper,
  bagField,
  bookingRequest,
  passengerTypes,
  searchUrl,
  type PassengerType,
} from "./booking-request";

type Quote = Schemas["QuoteResponse"];
export type BaggageOption = Schemas["BaggageOptionResponse"];

/** Lỗi về hành trình: khách phải chọn lại chuyến ở C-02 (APP_FLOW C-07). */
const UNAVAILABLE = [
  "SEATS_UNAVAILABLE",
  "FLIGHT_NOT_BOOKABLE",
  "ITINERARY_INVALID",
];

/** BR-10, tính tại ngày cất cánh chặng đầu của chiều đi. */
const AGE_HINT: Record<PassengerType, string> = {
  ADULT: "Từ 12 tuổi trở lên tại ngày bay.",
  CHILD: "Từ 2 đến dưới 12 tuổi tại ngày bay.",
  INFANT: "Dưới 2 tuổi tại ngày bay.",
};

/**
 * Mọi ô đã thoả ràng buộc HTML. Không dùng form.checkValidity(): hàm đó bắn sự kiện invalid, làm hiện lỗi ở cả những
 * ô khách chưa nhập tới.
 */
const complete = (form: HTMLFormElement) =>
  Array.from(form.elements).every(
    (el) =>
      !(el instanceof HTMLInputElement || el instanceof HTMLSelectElement) ||
      el.validity.valid,
  );

/** Rời ô họ tên: hiện đúng giá trị sẽ gửi (BR-12). */
const toAscii = (e: FocusEvent<HTMLInputElement>) => {
  e.currentTarget.value = asciiUpper(e.currentTarget.value);
};
const toUpper = (e: FocusEvent<HTMLInputElement>) => {
  e.currentTarget.value = e.currentTarget.value.trim().toUpperCase();
};

function quoteRows(q: Quote, voucher: string) {
  const rows: [string, number][] = [];
  for (const j of q.journeys) {
    const dir = DIRECTION_LABEL[j.direction];
    for (const f of j.passengerFares) {
      rows.push([
        `${dir}, ${PASSENGER_LABEL[f.type].toLowerCase()} × ${f.count}`,
        f.total,
      ]);
    }
    if (j.baggageTotal) rows.push([`${dir}, hành lý`, j.baggageTotal]);
  }
  if (q.discountTotal) rows.push([`Giảm giá ${voucher}`, -q.discountTotal]);
  return rows;
}

function JourneySummary({ journey }: { journey: Journey }) {
  const it = journey.itinerary;
  const a = first(it);
  const z = last(it);
  const numbers = it.segments.map((s) => s.flightNumber).join(" + ");
  return (
    <div className="grid gap-4 border border-line p-22">
      <p className="text-caption text-muted">
        {`${DIRECTION_LABEL[journey.direction]}, ${dmy(a.departure.time)}`}
      </p>
      <p className="font-bold">
        {`${a.airline.name} ${numbers}: ${hhmm(a.departure)} ${a.departure.airport} → ${hhmm(z.arrival)} ${z.arrival.airport}`}
      </p>
      <p className="text-caption text-muted">
        {`Gói ${journey.fare.name}, ${it.stops ? `nối chuyến tại ${a.arrival.city}` : "bay thẳng"}, ${duration(it.totalDurationMinutes)}`}
      </p>
    </div>
  );
}

/** Một hành khách (FR-21). Tên ô theo đường dẫn trường của errors backend, VD passengers[0].dateOfBirth. */
function PassengerFields({
  index,
  label,
  type,
  departure,
  international,
  errors,
}: {
  index: number;
  label: string;
  type: PassengerType;
  departure: string;
  international: boolean;
  errors: Record<string, string>;
}) {
  const name = (k: string) => `passengers[${index}].${k}`;
  return (
    <fieldset className="grid gap-16">
      <legend className="mb-16 font-bold">{label}</legend>
      <div className="grid gap-22 sm:grid-cols-2">
        <Field
          label="Họ"
          name={name("lastName")}
          required
          maxLength={50}
          autoComplete="off"
          onBlur={toAscii}
          error={errors[name("lastName")]}
        />
        <Field
          label="Tên đệm và tên"
          name={name("firstName")}
          required
          maxLength={50}
          autoComplete="off"
          onBlur={toAscii}
          error={errors[name("firstName")]}
        />
        <SelectField
          label="Giới tính"
          name={name("gender")}
          required
          defaultValue=""
          error={errors[name("gender")]}
        >
          <option value="" disabled>
            Chọn giới tính
          </option>
          <option value="MALE">Nam</option>
          <option value="FEMALE">Nữ</option>
        </SelectField>
        <Field
          label="Ngày sinh"
          name={name("dateOfBirth")}
          type="date"
          required
          max={departure}
          invalidMessage="Ngày sinh không được sau ngày bay."
          hint={AGE_HINT[type]}
          error={errors[name("dateOfBirth")]}
        />
        {international && (
          <>
            <Field
              label="Quốc tịch"
              name={name("nationality")}
              required
              maxLength={2}
              pattern="[A-Za-z]{2}"
              invalidMessage="Mã quốc gia gồm 2 chữ cái, VD VN."
              hint="Mã quốc gia 2 chữ cái, VD VN."
              autoComplete="off"
              onBlur={toUpper}
              error={errors[name("nationality")]}
            />
            <Field
              label="Số hộ chiếu"
              name={name("passportNumber")}
              required
              maxLength={20}
              pattern="[A-Za-z0-9]{6,20}"
              invalidMessage="Số hộ chiếu gồm 6 đến 20 chữ cái hoặc chữ số."
              autoComplete="off"
              onBlur={toUpper}
              error={errors[name("passportNumber")]}
            />
            <Field
              label="Ngày hết hạn hộ chiếu"
              name={name("passportExpiry")}
              type="date"
              required
              hint="Còn hạn ít nhất 6 tháng tính từ ngày bay của chặng cuối."
              error={errors[name("passportExpiry")]}
            />
          </>
        )}
      </div>
    </fieldset>
  );
}

/**
 * C-07 (APP_FLOW §2.1, UF-01). Form không điều khiển (đọc bằng FormData). Mỗi lần sửa, chờ 400 ms rồi báo giá nếu mọi ô
 * đã hợp lệ (FR-20); "Giữ chỗ" gửi POST /bookings (FR-24) rồi mở C-09.
 */
export default function CheckoutForm({
  selection,
  user,
  international,
  baggage,
}: {
  selection: Selection;
  user: User;
  international: boolean;
  /** Mức hành lý đang bán theo mã hãng; null khi tải lỗi. */
  baggage: Record<string, BaggageOption[]> | null;
}) {
  const router = useRouter();
  const formRef = useRef<HTMLFormElement>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  /** Số thứ tự lần báo giá mới nhất: phản hồi về trễ của lần cũ bị bỏ. */
  const latest = useRef(0);
  /** Chặn bấm đúp "Giữ chỗ" trước khi React kịp vô hiệu hoá nút. */
  const busy = useRef(false);
  const [quote, setQuote] = useState<Quote | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [voucher, setVoucher] = useState("");
  const [voucherError, setVoucherError] = useState<string>();
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [problem, setProblem] = useState<ApiError>();
  const [pending, setPending] = useState(false);
  const [held, setHeld] = useState<string>();

  const types = passengerTypes(selection);
  const labels = types.map(
    (t, i) =>
      `${PASSENGER_LABEL[t]} ${types.slice(0, i + 1).filter((x) => x === t).length}`,
  );
  const departure = first(selection.journeys[0].itinerary).departure.time.slice(
    0,
    10,
  );
  const estimate = selection.journeys.reduce(
    (sum, j) => sum + j.fare.totalPrice,
    0,
  );

  async function runQuote(code: string) {
    const seq = ++latest.current;
    const form = formRef.current;
    if (!form || !complete(form)) {
      setQuote(null);
      setQuoting(false);
      return;
    }
    setQuoting(true);
    try {
      const q = await apiFetch<Quote>("/bookings/quote", {
        method: "POST",
        body: bookingRequest(selection, new FormData(form), code),
      });
      if (seq !== latest.current) return;
      setQuote(q);
      setErrors({});
      setProblem(undefined);
    } catch (e) {
      if (!(e instanceof ApiError)) throw e;
      if (seq !== latest.current) return;
      setQuote(null);
      show(e);
    } finally {
      if (seq === latest.current) setQuoting(false);
    }
  }

  function show(e: ApiError) {
    if (e.status === 401) {
      router.replace("/login?next=%2Fcheckout");
      return;
    }
    if (e.code === "VOUCHER_INVALID") {
      // FR-23: báo lý do dưới ô, bỏ mã và tính lại giá không có mã.
      setVoucherError(e.detail);
      setVoucher("");
      void runQuote("");
      return;
    }
    setErrors(Object.fromEntries(e.errors.map((f) => [f.field, f.message])));
    setProblem(e);
  }

  function schedule(e: FormEvent<HTMLFormElement>) {
    // Mã voucher chỉ áp khi bấm "Áp dụng"; gõ mã không cần báo giá lại.
    if ((e.target as HTMLInputElement).name === "voucher") return;
    clearTimeout(timer.current);
    timer.current = setTimeout(() => void runQuote(voucher), 400);
  }

  function applyCode(code: string) {
    clearTimeout(timer.current);
    setVoucherError(undefined);
    setVoucher(code);
    void runQuote(code);
  }

  function applyVoucher() {
    const input = formRef.current?.elements.namedItem(
      "voucher",
    ) as HTMLInputElement;
    applyCode(input.value.trim().toUpperCase());
  }

  async function hold(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (busy.current) return;
    busy.current = true;
    clearTimeout(timer.current);
    latest.current++; // bỏ kết quả của lần báo giá đang chạy
    setQuoting(false);
    setPending(true);
    setProblem(undefined);
    try {
      const created = await apiFetch<Schemas["BookingCreatedResponse"]>(
        "/bookings",
        {
          method: "POST",
          body: bookingRequest(
            selection,
            new FormData(e.currentTarget),
            voucher,
          ),
        },
      );
      clearSelection();
      setHeld(created.code);
      router.replace(`/bookings/${created.code}`);
    } catch (err) {
      busy.current = false;
      setPending(false);
      if (!(err instanceof ApiError)) throw err;
      if (UNAVAILABLE.includes(err.code)) {
        router.push(`${searchUrl(selection)}&unavailable=1`);
      } else {
        show(err);
      }
    }
  }

  if (held) {
    return (
      <p role="status" className="text-muted">
        Đã giữ chỗ {held}. Đang mở chi tiết đặt chỗ...
      </p>
    );
  }

  const unavailable =
    problem !== undefined && UNAVAILABLE.includes(problem.code);
  // Lỗi không gắn với ô nào trên form: số khách (BR-11) và hành lý.
  const loose = Object.entries(errors).filter(
    ([f]) => f === "passengers" || f.startsWith("baggage"),
  );

  return (
    <form
      ref={formRef}
      onSubmit={hold}
      onChange={schedule}
      className="grid gap-53 lg:grid-cols-[minmax(0,1fr)_400px] lg:items-start"
    >
      <div className="grid gap-53">
        <section aria-label="Hành trình" className="grid gap-16">
          <div className="flex flex-wrap items-center justify-between gap-16">
            <h2 className="text-subheading font-bold">Hành trình</h2>
            <Link href={searchUrl(selection)} className={textLink}>
              Đổi chuyến bay
            </Link>
          </div>
          {selection.journeys.map((j) => (
            <JourneySummary key={j.direction} journey={j} />
          ))}
        </section>

        <section className="grid gap-22">
          <div className="grid gap-8">
            <h2 className="text-subheading font-bold">Hành khách</h2>
            <p className="text-caption text-muted">
              Họ tên như trên giấy tờ tuỳ thân. Chữ có dấu được tự bỏ dấu và
              viết hoa.
            </p>
          </div>
          {types.map((type, i) => (
            <PassengerFields
              key={i}
              index={i}
              label={labels[i]}
              type={type}
              departure={departure}
              international={international}
              errors={errors}
            />
          ))}
        </section>

        <section className="grid gap-22">
          <div className="grid gap-8">
            <h2 className="text-subheading font-bold">Liên hệ</h2>
            <p className="text-caption text-muted">
              Vé điện tử và mọi thông báo về đặt chỗ được gửi tới email này.
            </p>
          </div>
          <div className="grid gap-22 sm:grid-cols-2">
            <Field
              label="Họ tên người liên hệ"
              name="contact.name"
              autoComplete="name"
              required
              maxLength={100}
              defaultValue={user.fullName}
              error={errors["contact.name"]}
            />
            <Field
              label="Email"
              name="contact.email"
              type="email"
              autoComplete="email"
              required
              maxLength={255}
              defaultValue={user.email}
              error={errors["contact.email"]}
            />
            <Field
              label="Số điện thoại"
              name="contact.phone"
              type="tel"
              autoComplete="tel"
              required
              pattern="\+?[0-9]{9,15}"
              invalidMessage="Số điện thoại gồm 9 đến 15 chữ số, có thể có dấu + ở đầu."
              defaultValue={user.phone}
              error={errors["contact.phone"]}
            />
          </div>
        </section>

        <section className="grid gap-22">
          <h2 className="text-subheading font-bold">Hành lý mua thêm</h2>
          {baggage === null && (
            <Notice>
              Không tải được bảng giá hành lý. Tải lại trang nếu muốn mua thêm
              hành lý.
            </Notice>
          )}
          {selection.journeys.map((j) => {
            const airline = first(j.itinerary).airline;
            const options = baggage?.[airline.code] ?? [];
            const included = j.fare.checkedBaggageKg
              ? ` và ${j.fare.checkedBaggageKg} kg ký gửi`
              : ", chưa có hành lý ký gửi";
            return (
              <fieldset key={j.direction} className="grid gap-16">
                <legend className="mb-8 font-bold">
                  {`Hành lý ${DIRECTION_LABEL[j.direction].toLowerCase()}`}
                </legend>
                <p className="text-caption text-muted">
                  {`${airline.name}, gói ${j.fare.name} đã gồm ${j.fare.carryOnKg} kg xách tay${included}.`}
                </p>
                {baggage !== null && options.length === 0 ? (
                  <p className="text-caption">
                    Hãng chưa bán hành lý mua thêm cho chiều này.
                  </p>
                ) : (
                  <div className="grid gap-22 sm:grid-cols-2">
                    {types.map(
                      (type, i) =>
                        type !== "INFANT" && (
                          <SelectField
                            key={i}
                            label={labels[i]}
                            name={bagField(j.direction, i)}
                            defaultValue=""
                          >
                            <option value="">Không mua thêm</option>
                            {options.map((o) => (
                              <option key={o.id} value={o.id}>
                                {`${o.weightKg} kg, ${vnd(o.price)}`}
                              </option>
                            ))}
                          </SelectField>
                        ),
                    )}
                  </div>
                )}
              </fieldset>
            );
          })}
        </section>

        <section className="grid gap-13">
          <div className="flex flex-wrap items-start gap-13">
            <div className="min-w-0 flex-1">
              <Field
                label="Mã giảm giá"
                name="voucher"
                autoComplete="off"
                maxLength={50}
                error={voucherError}
                onKeyDown={(e) => {
                  // Enter trong ô này là áp mã, không phải giữ chỗ.
                  if (e.key === "Enter") {
                    e.preventDefault();
                    applyVoucher();
                  }
                }}
              />
            </div>
            <button
              type="button"
              className={`${pillLine} mt-30`}
              onClick={applyVoucher}
            >
              Áp dụng
            </button>
          </div>
          {voucher && (
            <p role="status" className="text-caption">
              {quote
                ? `Đã áp mã ${voucher}, giảm ${vnd(quote.discountTotal)}. `
                : `Mã ${voucher} sẽ được kiểm tra khi bạn điền đủ thông tin hành khách. `}
              <button
                type="button"
                className={textLink}
                onClick={() => applyCode("")}
              >
                Bỏ mã
              </button>
            </p>
          )}
        </section>
      </div>

      <aside className="grid gap-22 lg:sticky lg:top-22">
        <section
          aria-label="Bảng giá"
          aria-busy={quoting}
          className="grid gap-16 border border-ink p-22"
        >
          <h2 className="text-subheading font-bold">Bảng giá</h2>
          {quote ? (
            <PriceTable
              rows={quoteRows(quote, voucher)}
              total={quote.totalAmount}
            />
          ) : (
            <>
              <PriceTable
                rows={selection.journeys.map((j): [string, number] => [
                  `${DIRECTION_LABEL[j.direction]}, gói ${j.fare.name}`,
                  j.fare.totalPrice,
                ])}
                total={estimate}
              />
              <p className="text-caption text-muted">
                Tạm tính theo giá lúc tìm chuyến. Điền đủ thông tin hành khách
                để xem giá cuối cùng, gồm hành lý và mã giảm giá.
              </p>
            </>
          )}
          {quoting && (
            <p className="text-caption text-muted">Đang tính giá...</p>
          )}
        </section>
        {problem && (
          <Notice>
            <p className="font-bold">{problem.detail}</p>
            {loose.map(([f, m]) => (
              <p key={f}>{m}</p>
            ))}
            {unavailable && (
              <p className="mt-8">
                <Link
                  href={`${searchUrl(selection)}&unavailable=1`}
                  className={textLink}
                >
                  Chọn chuyến khác
                </Link>
              </p>
            )}
          </Notice>
        )}
        <button
          type="submit"
          className={`${pillDark} w-full`}
          disabled={pending}
        >
          {pending ? "Đang giữ chỗ..." : "Giữ chỗ"}
        </button>
        <p className="text-caption text-muted">
          Giữ chỗ chưa trừ tiền. Bạn thanh toán qua VNPay ở bước sau, trong thời
          hạn giữ chỗ.
        </p>
      </aside>
    </form>
  );
}
```

Vài điểm dễ sai:

- `new FormData(e.currentTarget)` trong `hold` nằm ở đối số của `apiFetch`, nên chạy trước `await` đầu tiên. Sau `await`, React đã đặt `e.currentTarget` về `null`.
- `runQuote` tăng `latest` cả khi form chưa đủ, để một phản hồi đang bay không đè lên trạng thái "chưa đủ thông tin".
- `schedule` đọc `voucher` của lần render tạo ra nó. `applyCode` xoá hẹn giờ cũ trước khi đổi mã, nên không có lần báo giá nào chạy với mã cũ sau khi mã đã đổi.

- [ ] **Step 6: Tạo phần tải dữ liệu và trang**

Tạo `frontend/src/app/checkout/checkout-view.tsx`:

```tsx
"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import CustomerHeader from "@/components/customer-header";
import { FormSkeleton, Notice, textLink } from "@/components/form";
import { useAirports } from "@/lib/airports";
import { ApiError, apiFetch, type User } from "@/lib/api";
import { useSelection } from "@/lib/selection";
import { first } from "../flights/itineraries";
import { isInternational } from "./booking-request";
import CheckoutForm, { type BaggageOption } from "./checkout-form";

/**
 * C-07: đọc lựa chọn ở sessionStorage (Plan 08), tài khoản để điền sẵn liên hệ (FR-21), danh mục sân bay để biết chặng
 * quốc tế (BR-13) và bảng giá hành lý của từng hãng (FR-22), rồi mới mở form.
 */
export default function CheckoutView() {
  const router = useRouter();
  const selection = useSelection();
  const { airports, failed } = useAirports();
  const [user, setUser] = useState<User>();
  const [loadError, setLoadError] = useState<string>();
  // undefined: đang tải; null: tải lỗi (form vẫn mở, chỉ không mua thêm hành lý được).
  const [bags, setBags] = useState<Record<string, BaggageOption[]> | null>();

  useEffect(() => {
    apiFetch<User>("/me").then(setUser, (e: ApiError) => {
      if (e.status === 401) router.replace("/login?next=%2Fcheckout");
      else setLoadError(e.detail);
    });
  }, [router]);

  // Mã các hãng khai thác, nối thành chuỗi để effect chỉ chạy lại khi đổi hãng.
  const airlines = selection
    ? [
        ...new Set(
          selection.journeys.map((j) => first(j.itinerary).airline.code),
        ),
      ].join(",")
    : "";
  useEffect(() => {
    if (!airlines) return;
    let live = true;
    Promise.all(
      airlines
        .split(",")
        .map((code) =>
          apiFetch<BaggageOption[]>(`/airlines/${code}/baggage-options`).then(
            (list) => [code, list] as const,
          ),
        ),
    ).then(
      (pairs) => live && setBags(Object.fromEntries(pairs)),
      () => live && setBags(null),
    );
    return () => {
      live = false;
    };
  }, [airlines]);

  return (
    <div className="min-h-dvh bg-paper">
      <CustomerHeader />
      <main className="mx-auto grid max-w-page gap-38 px-16 pt-22 pb-119 sm:px-38">
        {selection === null ? (
          <div className="grid gap-13">
            <h1 className="text-heading-lg font-bold break-words">
              Chưa có chuyến bay nào được chọn.
            </h1>
            <p className="text-muted">
              Hãy tìm và chọn chuyến bay trước khi đặt vé.{" "}
              <Link href="/" className={textLink}>
                Tìm chuyến bay
              </Link>
            </p>
          </div>
        ) : (
          <>
            <h1 className="text-heading-lg font-bold break-words">
              Hoàn tất đặt vé.
            </h1>
            {loadError || failed ? (
              <Notice>
                {loadError ??
                  "Không tải được danh sách sân bay. Hãy tải lại trang."}
              </Notice>
            ) : selection && user && airports && bags !== undefined ? (
              <CheckoutForm
                selection={selection}
                user={user}
                international={isInternational(selection, airports)}
                baggage={bags}
              />
            ) : (
              <FormSkeleton rows={4} />
            )}
          </>
        )}
      </main>
    </div>
  );
}
```

Viết lại `frontend/src/app/checkout/page.tsx`:

```tsx
import type { Metadata } from "next";
import CheckoutView from "./checkout-view";

export const metadata: Metadata = { title: "Đặt vé | SkyLine" };

/** C-07 (APP_FLOW §2.1): Customer. proxy.ts chặn khi chưa có cookie phiên; lựa chọn hành trình nằm ở sessionStorage. */
export default function Page() {
  return <CheckoutView />;
}
```

`CheckoutView` không dùng `useSearchParams` nên không cần `<Suspense>`: `useSelection()` trả `undefined` lúc prerender và trang hiện khung xương.

- [ ] **Step 7: C-02 báo khi chuyến đã chọn không còn bán**

Trong `frontend/src/app/flights/flight-results.tsx`:

7a. Bước 1 sang bước 2 của khứ hồi không mang theo `unavailable`. Thay:

```tsx
const p = new URLSearchParams(params);
p.set("out", itineraryKey(itinerary));
```

bằng:

```tsx
const p = new URLSearchParams(params);
p.delete("unavailable");
p.set("out", itineraryKey(itinerary));
```

7b. Thêm thông báo ngay sau khối `{lost && (...)}`. Thay:

```tsx
        {picked && (
          <section
            aria-label="Chiều đi đã chọn"
```

bằng:

```tsx
        {params.get("unavailable") && (
          <Notice>
            Chuyến bay bạn chọn không còn đủ chỗ hoặc đã ngừng bán. Hãy chọn
            chuyến khác.
          </Notice>
        )}

        {picked && (
          <section
            aria-label="Chiều đi đã chọn"
```

- [ ] **Step 8: Chạy E2E để thấy nó xanh**

```bash
cd frontend && npx prettier --write src/components/form.tsx src/components/price-table.tsx src/components/customer-header.tsx src/app/checkout src/app/flights/flight-results.tsx e2e/booking.spec.ts && npx playwright test booking.spec account flight-search
```

Expected: `9 passed` (3 đặt vé, 2 tài khoản vì `Field` vừa đổi, 4 tìm chuyến vì `flight-results.tsx` vừa đổi). Test đặt vé đầu kết thúc ở `/bookings/{mã}`, lúc này còn là trang 404 của Next.js; Task 3 làm màn đó.

Nếu test "bấm đúp" thấy 2 booking: kiểm tra `busy.current` được đặt trước mọi `await` trong `hold`.

- [ ] **Step 9: Lint, build, xem giao diện**

```bash
cd frontend && npm run lint && npm run build
```

Expected: lint không lỗi; build xong, không có lỗi "Uncached data was accessed outside of <Suspense>".

Mở `http://localhost:3000/checkout` sau khi chọn một chuyến khứ hồi có trẻ em, ở bề rộng 390px và 1280px, sáng và tối: bảng giá nằm cột phải và dính khi cuộn ở 1280px, nằm dưới form ở 390px; không có thanh cuộn ngang.

- [ ] **Step 10: Commit**

```bash
git add frontend/src/components frontend/src/app/checkout frontend/src/app/flights/flight-results.tsx frontend/e2e/booking.spec.ts
git commit -m "feat(frontend): màn đặt vé (C-07): hành khách, hành lý, voucher, báo giá và giữ chỗ"
```

---

### Task 3: Chi tiết booking (C-09)

**Files:**

- Create: `frontend/src/app/bookings/[code]/page.tsx`, `frontend/src/app/bookings/[code]/booking-view.tsx`
- Modify: `frontend/src/app/flights/itineraries.ts` (thêm `fareRules`), `frontend/src/app/flights/itinerary-card.tsx` (bỏ `fareRules` cục bộ), `frontend/e2e/booking.spec.ts`, `frontend/e2e/smoke.spec.ts`
- Test: `frontend/e2e/booking.spec.ts`, `frontend/e2e/smoke.spec.ts`

**Interfaces:**

- Consumes: Task 1 (`STATUS_LABEL`, `DIRECTION_LABEL`, `PASSENGER_LABEL`, `dmy`, `clock`, `countdown`, `useNow`); Task 2 (`CustomerHeader`, `PriceTable`); `CABINS` của `src/components/landing/data.ts`; `Schemas["BookingDetail"]`, `["PaymentUrlResponse"]`.
- Produces:
  - `fareRules(f: Pick<FareOption, "carryOnKg" | "checkedBaggageKg" | "refundable" | "refundFee" | "changeable" | "changeFee">): string[]` trong `src/app/flights/itineraries.ts` (dùng cho cả `FareOption` và `JourneyView`).
  - `/bookings/[code]` (C-09): vùng `region` "Trạng thái", "Hành trình", "Hành khách", "Bảng giá", "Liên hệ", "Lịch sử thanh toán" (chỉ khi có lượt thu); nút "Thanh toán {tổng}" và "Huỷ giữ chỗ".

- [ ] **Step 1: Viết E2E cho C-09**

1a. Trong `frontend/e2e/booking.spec.ts`, ở test đầu tiên, thêm đoạn sau vào cuối test: ngay sau lệnh `expect` kiểm tra tiêu đề "Chưa có chuyến bay nào được chọn.", trước `});` đóng test:

```ts
// C-09: tiêu đề, đếm ngược, giờ sân bay, hành khách, vé chưa xuất, bảng giá (FR-41).
await page.goto(`/bookings/${code}`);
await expect(page.getByRole("heading", { level: 1 })).toHaveText(
  `Đặt chỗ ${code}.`,
);
await expect(page.getByText("Chờ thanh toán", { exact: true })).toBeVisible();
await expect(page.getByText(/^Còn \d{2}:\d{2} để thanh toán$/)).toBeVisible();
await expect(page.getByRole("region", { name: "Hành trình" })).toContainText(
  "VJ123: 08:00 HAN → 10:10 SGN",
);
const passengers = page.getByRole("region", { name: "Hành khách" });
await expect(passengers).toContainText("NGUYEN VAN DUC");
await expect(passengers).toContainText("Vé VJ123: chưa xuất vé");
await expect(passengers).toContainText("Hành lý mua thêm chiều đi: 20 kg");
await expect(
  page.getByRole("region", { name: "Bảng giá" }).getByRole("definition").last(),
).toHaveText("1.206.000 đ");

// Thanh toán: chuyển sang VNPay với đúng số tiền (vnp_Amount = tiền × 100). Trang VNPay giả lập, không cần mạng.
await page.route("https://sandbox.vnpayment.vn/**", (route) =>
  route.fulfill({ contentType: "text/html", body: "<h1>VNPay sandbox</h1>" }),
);
await page.getByRole("button", { name: /^Thanh toán/ }).click();
await expect(page).toHaveURL(
  /^https:\/\/sandbox\.vnpayment\.vn\/paymentv2\/vpcpay\.html\?.*vnp_Amount=120600000/,
);

// Return URL báo thất bại (FR-30): có thông báo, lượt thu còn chờ, vẫn thanh toán lại được.
await page.goto(`/bookings/${code}?payment=failed`);
await expect(page.getByText("Thanh toán chưa thành công.")).toBeVisible();
await expect(
  page.getByRole("region", { name: "Lịch sử thanh toán" }),
).toContainText("Thanh toán, đang xử lý, 1.206.000 đ");
await expect(page.getByRole("button", { name: /^Thanh toán/ })).toBeVisible();

// Hết hạn giữ chỗ khi đang mở trang thì ẩn nút thanh toán (BR-41). Chỉ đồng hồ trình duyệt chạy nhanh 16 phút.
await page.clock.install();
await page.reload();
await expect(page.getByText(/^Còn \d{2}:\d{2} để thanh toán$/)).toBeVisible();
await page.clock.fastForward("16:00");
await expect(page.getByText("Đã hết hạn giữ chỗ.")).toBeVisible();
await expect(page.getByRole("button", { name: /^Thanh toán/ })).toHaveCount(0);

// Huỷ giữ chỗ (FR-33): hỏi lại, rồi booking thành Đã huỷ.
page.once("dialog", (dialog) => dialog.accept());
await page.getByRole("button", { name: "Huỷ giữ chỗ" }).click();
await expect(page.getByText("Đã huỷ", { exact: true })).toBeVisible();
await expect(page.getByRole("button", { name: "Huỷ giữ chỗ" })).toHaveCount(0);
```

Đổi tên test đầu thành `"đặt vé từ C-07 tới VNPay, hết hạn rồi huỷ trên C-09"`.

1b. Trong `frontend/e2e/smoke.spec.ts`, thay:

```ts
const PROTECTED = ["/account", "/bookings", "/checkout"];
```

bằng:

```ts
// Mã không tồn tại: C-09 vẫn phải có tiêu đề "Không tìm thấy đặt chỗ." (BR-104).
const PROTECTED = ["/account", "/bookings", "/checkout", "/bookings/ZZZZZZ"];
```

- [ ] **Step 2: Chạy test để thấy nó đỏ**

```bash
cd frontend && npx playwright test booking.spec smoke
```

Expected: test đặt vé đầu đỏ ở `toHaveText("Đặt chỗ ...")` (trang 404); smoke "route cần đăng nhập hiển thị được sau khi đăng nhập" đỏ ở `/bookings/ZZZZZZ` (không có `h1`). Các test khác xanh.

- [ ] **Step 3: Dùng chung quy định gói giá**

Trong `frontend/src/app/flights/itinerary-card.tsx`, xoá cả hàm `fareRules` (từ `function fareRules(f: FareOption) {` tới `}` đóng hàm, ngay trước comment `/** Một hành trình (FR-11)`), rồi trong khối import từ `"./itineraries"` thêm `fareRules,` sau `duration,`.

Trong `frontend/src/app/flights/itineraries.ts`, thêm sau hàm `vnd`:

```ts
/** Quy định của gói giá (BR-24): dùng cho gói đang bán (C-02) và snapshot trong booking (C-09). */
export function fareRules(
  f: Pick<
    FareOption,
    | "carryOnKg"
    | "checkedBaggageKg"
    | "refundable"
    | "refundFee"
    | "changeable"
    | "changeFee"
  >,
) {
  return [
    `Xách tay ${f.carryOnKg} kg`,
    f.checkedBaggageKg
      ? `Ký gửi ${f.checkedBaggageKg} kg`
      : "Không có hành lý ký gửi",
    !f.refundable
      ? "Không hoàn vé"
      : f.refundFee
        ? `Hoàn vé, phí ${vnd(f.refundFee)}`
        : "Hoàn vé miễn phí",
    !f.changeable
      ? "Không đổi chuyến"
      : f.changeFee
        ? `Đổi chuyến, phí ${vnd(f.changeFee)}`
        : "Đổi chuyến miễn phí",
  ];
}
```

- [ ] **Step 4: Tạo màn chi tiết**

Tạo `frontend/src/app/bookings/[code]/booking-view.tsx`:

```tsx
"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import CustomerHeader from "@/components/customer-header";
import {
  FormSkeleton,
  Notice,
  pillDark,
  pillLine,
  textLink,
} from "@/components/form";
import { CABINS } from "@/components/landing/data";
import PriceTable from "@/components/price-table";
import { ApiError, apiFetch, type Schemas } from "@/lib/api";
import {
  DIRECTION_LABEL,
  PASSENGER_LABEL,
  STATUS_LABEL,
  clock,
  countdown,
  dmy,
  useNow,
} from "@/lib/booking";
import { fareRules, vnd } from "../../flights/itineraries";

type Detail = Schemas["BookingDetail"];

const PAYMENT_KIND: Record<string, string> = {
  CHARGE: "Thanh toán",
  REFUND: "Hoàn tiền",
};
const PAYMENT_STATUS: Record<string, string> = {
  PENDING: "đang xử lý",
  SUCCESS: "thành công",
  FAILED: "thất bại",
};

/** "08:00 16/10/2026", theo giờ trong chuỗi. */
const at = (iso: string) => `${clock(iso)} ${dmy(iso)}`;

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section
      aria-label={title}
      className="grid content-start gap-16 border-t border-line pt-22"
    >
      <h2 className="text-subheading font-bold">{title}</h2>
      {children}
    </section>
  );
}

/** Phần chỉ đọc của FR-41: hành trình, hành khách và vé, bảng giá, liên hệ, lượt thu. */
function BookingBody({ booking }: { booking: Detail }) {
  const rows: [string, number][] = [["Giá vé", booking.fareTotal]];
  if (booking.baggageTotal)
    rows.push(["Hành lý mua thêm", booking.baggageTotal]);
  if (booking.discountTotal) {
    rows.push([
      `Giảm giá ${booking.voucherCode ?? ""}`,
      -booking.discountTotal,
    ]);
  }

  return (
    <div className="grid gap-38 lg:grid-cols-[minmax(0,1fr)_400px] lg:items-start">
      <div className="grid gap-38">
        <Section title="Hành trình">
          {booking.journeys.map((j) => {
            const cabin =
              CABINS.find((c) => c.id === j.cabinClass)?.label ?? j.cabinClass;
            return (
              <div
                key={j.direction}
                className="grid gap-8 border border-line p-22"
              >
                <p className="font-bold">
                  {DIRECTION_LABEL[j.direction]}
                  {j.status === "REFUNDED" && ", đã hoàn"}
                </p>
                <ol className="grid gap-4">
                  {j.segments.map((s) => (
                    <li key={s.flightId}>
                      {`${s.flightNumber}: ${clock(s.departureTime)} ${s.departureAirport} → ${clock(s.arrivalTime)} ${s.arrivalAirport}, ${dmy(s.departureTime)}`}
                      {s.status === "CANCELLED" && " (đã huỷ)"}
                    </li>
                  ))}
                </ol>
                <p className="text-caption text-muted">
                  {`${j.fareFamilyName}, ${cabin}. ${fareRules(j).join(", ")}.`}
                </p>
              </div>
            );
          })}
        </Section>

        <Section title="Hành khách">
          <ul className="grid gap-16">
            {booking.passengers.map((p, i) => (
              <li key={i} className="grid gap-4 border border-line p-22">
                <p className="font-bold">{`${p.lastName} ${p.firstName}`}</p>
                <p className="text-caption text-muted">
                  {`${PASSENGER_LABEL[p.type]}, ${p.gender === "MALE" ? "nam" : "nữ"}, sinh ngày ${dmy(p.dateOfBirth)}`}
                  {p.passportNumber &&
                    p.passportExpiry &&
                    `, hộ chiếu ${p.passportNumber} (${p.nationality}, hết hạn ${dmy(p.passportExpiry)})`}
                </p>
                {p.tickets.map((t) => (
                  <p
                    key={`${t.direction}-${t.flightNumber}`}
                    className="text-caption"
                  >
                    {`Vé ${t.flightNumber}: ${t.ticketNumber ?? "chưa xuất vé"}`}
                  </p>
                ))}
                {booking.journeys.flatMap((j) =>
                  j.baggage
                    .filter((b) => b.passengerIndex === i)
                    .map((b) => (
                      <p key={j.direction} className="text-caption">
                        {`Hành lý mua thêm ${DIRECTION_LABEL[j.direction].toLowerCase()}: ${b.weightKg} kg`}
                      </p>
                    )),
                )}
              </li>
            ))}
          </ul>
        </Section>
      </div>

      <div className="grid gap-38">
        <Section title="Bảng giá">
          <PriceTable rows={rows} total={booking.totalAmount} />
        </Section>
        <Section title="Liên hệ">
          <p>{booking.contact.name}</p>
          <p className="text-caption text-muted">
            {`${booking.contact.email}, ${booking.contact.phone}`}
          </p>
        </Section>
        {booking.payments.length > 0 && (
          <Section title="Lịch sử thanh toán">
            <ul className="grid gap-4 text-caption">
              {booking.payments.map((p, i) => (
                <li key={i}>
                  {`${PAYMENT_KIND[p.kind] ?? p.kind}, ${PAYMENT_STATUS[p.status] ?? p.status}, ${vnd(p.amount)}, ${at(p.createdAt)}`}
                </li>
              ))}
            </ul>
          </Section>
        )}
      </div>
    </div>
  );
}

/**
 * C-09 (APP_FLOW §2.1, FR-41): chỉ chủ booking (của người khác backend trả 404, BR-104). Đang chờ thanh toán thì đếm
 * ngược hạn giữ chỗ, cho thanh toán (FR-30) và huỷ (FR-33). `?payment=` là kết quả VNPay do Return URL gắn vào.
 */
export default function BookingView() {
  const { code } = useParams<{ code: string }>();
  const params = useSearchParams();
  const payment = params.get("payment");
  const router = useRouter();
  const now = useNow();
  // undefined: đang tải; null: không tìm thấy.
  const [booking, setBooking] = useState<Detail | null>();
  const [loadError, setLoadError] = useState<string>();
  const [version, setVersion] = useState(0);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string>();

  useEffect(() => {
    let live = true;
    apiFetch<Detail>(`/bookings/${encodeURIComponent(code)}`).then(
      (b) => live && setBooking(b),
      (e: ApiError) => {
        if (!live) return;
        if (e.status === 401) {
          router.replace(
            `/login?next=${encodeURIComponent(location.pathname + location.search)}`,
          );
        } else if (e.status === 404) {
          setBooking(null);
        } else {
          setLoadError(e.detail);
        }
      },
    );
    return () => {
      live = false;
    };
  }, [code, version, router]);
  const reload = () => setVersion((v) => v + 1);

  async function pay() {
    setBusy(true);
    setActionError(undefined);
    try {
      const { paymentUrl } = await apiFetch<Schemas["PaymentUrlResponse"]>(
        `/bookings/${encodeURIComponent(code)}/payments`,
        { method: "POST" },
      );
      // Giữ busy: trang đang chuyển sang VNPay.
      window.location.assign(paymentUrl);
    } catch (e) {
      setBusy(false);
      if (!(e instanceof ApiError)) throw e;
      // HOLD_EXPIRED, INVALID_STATE: trạng thái trên trang đã cũ.
      setActionError(e.detail);
      reload();
    }
  }

  async function cancel() {
    if (
      !confirm(
        `Huỷ giữ chỗ ${code.toUpperCase()}? Ghế đã giữ sẽ được trả lại, không khôi phục được.`,
      )
    ) {
      return;
    }
    setBusy(true);
    setActionError(undefined);
    try {
      await apiFetch(`/bookings/${encodeURIComponent(code)}/cancel`, {
        method: "POST",
      });
    } catch (e) {
      if (!(e instanceof ApiError)) throw e;
      setActionError(e.detail);
    } finally {
      setBusy(false);
      reload();
    }
  }

  const pending = booking?.status === "PENDING_PAYMENT";
  // Thời gian giữ chỗ còn lại (ms); null khi không chờ thanh toán hoặc chưa hydrate.
  const left =
    booking && booking.status === "PENDING_PAYMENT" && now !== null
      ? Date.parse(booking.holdExpiresAt) - now
      : null;
  const canPay = left !== null && left > 0;

  return (
    <div className="min-h-dvh bg-paper">
      <CustomerHeader />
      <main className="mx-auto grid max-w-page gap-38 px-16 pt-22 pb-119 sm:px-38">
        <div className="grid gap-13">
          <Link href="/bookings" className={`${textLink} text-caption`}>
            Tất cả đặt chỗ
          </Link>
          <h1 className="text-heading-lg font-bold break-words">
            {booking === null
              ? "Không tìm thấy đặt chỗ."
              : `Đặt chỗ ${code.toUpperCase()}.`}
          </h1>
          {booking === null && (
            <p className="text-muted">
              {`Mã ${code.toUpperCase()} không có trong các đặt chỗ của bạn.`}
            </p>
          )}
          {booking && (
            <p className="text-muted">{`Đặt lúc ${at(booking.createdAt)} (giờ Việt Nam)`}</p>
          )}
        </div>

        {loadError && <Notice>{loadError}</Notice>}
        {booking === undefined && !loadError && <FormSkeleton rows={3} />}

        {booking && payment === "success" && (
          <Notice tone="ok">
            {booking.status === "ISSUED"
              ? `Thanh toán thành công. Vé điện tử đã được gửi tới ${booking.contact.email}.`
              : "Đã nhận kết quả từ VNPay, hệ thống đang xác nhận thanh toán. Hãy tải lại trang sau ít phút."}
          </Notice>
        )}
        {booking && payment === "failed" && (
          <Notice>
            Thanh toán chưa thành công.
            {canPay && " Bạn có thể thanh toán lại trước khi hết hạn giữ chỗ."}
          </Notice>
        )}
        {actionError && <Notice>{actionError}</Notice>}

        {booking && (
          <section
            aria-label="Trạng thái"
            className="grid gap-16 border border-ink p-22 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
          >
            <div className="grid gap-4">
              <p className="text-subheading font-bold">
                {STATUS_LABEL[booking.status]}
              </p>
              {canPay && <p>{`Còn ${countdown(left ?? 0)} để thanh toán`}</p>}
              {left !== null && !canPay && <p>Đã hết hạn giữ chỗ.</p>}
              {pending && (
                <p className="text-caption text-muted">
                  {`Giữ chỗ đến ${at(booking.holdExpiresAt)} (giờ Việt Nam)`}
                </p>
              )}
              {booking.status === "ISSUED" && booking.issuedAt && (
                <p className="text-caption text-muted">
                  {`Xuất vé lúc ${at(booking.issuedAt)} (giờ Việt Nam)`}
                </p>
              )}
            </div>
            {pending && (
              <div className="flex flex-wrap gap-13">
                {canPay && (
                  <button
                    type="button"
                    className={pillDark}
                    disabled={busy}
                    onClick={pay}
                  >
                    {`Thanh toán ${vnd(booking.totalAmount)}`}
                  </button>
                )}
                <button
                  type="button"
                  className={pillLine}
                  disabled={busy}
                  onClick={cancel}
                >
                  Huỷ giữ chỗ
                </button>
              </div>
            )}
          </section>
        )}

        {booking && <BookingBody booking={booking} />}
      </main>
    </div>
  );
}
```

`canPay` đúng thì `left` chắc chắn là số; `left ?? 0` chỉ để TypeScript không phải suy điều đó qua biến trung gian.

Tạo `frontend/src/app/bookings/[code]/page.tsx`:

```tsx
import type { Metadata } from "next";
import { Suspense } from "react";
import BookingView from "./booking-view";

export const metadata: Metadata = { title: "Chi tiết đặt chỗ | SkyLine" };

/**
 * C-09 (APP_FLOW §2.1): chủ booking; proxy.ts chặn khi chưa có cookie phiên. Mã đặt chỗ chỉ biết lúc request, nên
 * useParams và useSearchParams cần Suspense (Cache Components).
 */
export default function Page() {
  return (
    <Suspense>
      <BookingView />
    </Suspense>
  );
}
```

- [ ] **Step 5: Chạy test để thấy nó xanh**

```bash
cd frontend && npx prettier --write "src/app/bookings/[code]" src/app/flights/itineraries.ts src/app/flights/itinerary-card.tsx e2e/booking.spec.ts e2e/smoke.spec.ts && npx playwright test booking.spec smoke flight-search itineraries
```

Expected: tất cả xanh: 3 đặt vé, 11 smoke, 4 tìm chuyến, 4 unit `itineraries` (`fareRules` vừa chuyển chỗ).

- [ ] **Step 6: Lint, build, xem giao diện**

```bash
cd frontend && npm run lint && npm run build
```

Expected: không lỗi. Trong bảng route của build, `/bookings/[code]` là route động (không prerender đầy đủ) và build không báo lỗi Suspense.

Mở một booking đang chờ thanh toán ở 390px và 1280px, sáng và tối: nút "Thanh toán" và "Huỷ giữ chỗ" xuống dòng gọn ở 390px; cột phải (bảng giá, liên hệ, lịch sử) nằm dưới ở 390px.

- [ ] **Step 7: Commit**

```bash
git add "frontend/src/app/bookings/[code]" frontend/src/app/flights/itineraries.ts frontend/src/app/flights/itinerary-card.tsx frontend/e2e/booking.spec.ts frontend/e2e/smoke.spec.ts
git commit -m "feat(frontend): chi tiết booking (C-09): đếm ngược, thanh toán VNPay, huỷ giữ chỗ"
```

---

### Task 4: Booking của tôi (C-08)

**Files:**

- Create: `frontend/src/app/bookings/booking-list.tsx`
- Modify: `frontend/src/app/bookings/page.tsx` (viết lại), `frontend/e2e/booking.spec.ts`
- Test: `frontend/e2e/booking.spec.ts`

**Interfaces:**

- Consumes: Task 1 (`STATUS_LABEL`, `DIRECTION_LABEL`, `BookingStatus`, `clock`, `dmy`); Task 2 (`CustomerHeader`); `vnd`; `Schemas["PagedModelBookingSummary"]`.
- Produces: `/bookings?status=&page=&payment=` (C-08): `nav` "Lọc theo trạng thái" (link "Tất cả" và 5 trạng thái), `list` "Danh sách đặt chỗ" (mỗi dòng là link tới `/bookings/{code}`), `nav` "Phân trang" khi có hơn một trang.

- [ ] **Step 1: Viết E2E cho C-08**

Trong `frontend/e2e/booking.spec.ts`, ở test đầu tiên:

1a. Thay:

```ts
await page.getByRole("button", { name: "Tạo tài khoản" }).click();
await expect(page).toHaveURL("/bookings");
```

bằng:

```ts
await page.getByRole("button", { name: "Tạo tài khoản" }).click();
await expect(page).toHaveURL("/bookings");
await expect(page.getByText("Bạn chưa có đặt chỗ nào.")).toBeVisible();
```

1b. Thêm vào cuối test (sau dòng kiểm tra nút "Huỷ giữ chỗ" đã biến mất, trước `});` đóng test):

```ts
// C-08 (FR-40): đúng một booking (bấm đúp không tạo thêm), giờ sân bay, lọc theo trạng thái.
await page.goto("/bookings");
const rows = page
  .getByRole("list", { name: "Danh sách đặt chỗ" })
  .getByRole("listitem");
await expect(rows).toHaveCount(1);
await expect(rows.first()).toContainText(`${code}, Đã huỷ`);
await expect(rows.first()).toContainText("Chiều đi: HAN → SGN, 08:00");
await page
  .getByRole("navigation", { name: "Lọc theo trạng thái" })
  .getByRole("link", { name: "Chờ thanh toán" })
  .click();
await expect(page).toHaveURL("/bookings?status=PENDING_PAYMENT");
await expect(
  page.getByText("Không có đặt chỗ nào ở trạng thái này."),
).toBeVisible();

// Return URL không xác định được booking thì về C-08 kèm ?payment=failed (Plan 12).
await page.goto("/bookings?payment=failed");
await expect(page.getByText("Thanh toán chưa thành công.")).toBeVisible();
await rows.first().getByRole("link").click();
await expect(page).toHaveURL(`/bookings/${code}`);
```

- [ ] **Step 2: Chạy test để thấy nó đỏ**

```bash
cd frontend && npx playwright test booking.spec -g "đặt vé từ C-07"
```

Expected: `1 failed` ở `getByText("Bạn chưa có đặt chỗ nào.")` (trang tạm C-08).

- [ ] **Step 3: Tạo màn danh sách**

Tạo `frontend/src/app/bookings/booking-list.tsx`:

```tsx
"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import CustomerHeader from "@/components/customer-header";
import { Notice, textLink } from "@/components/form";
import { ApiError, apiFetch, type Schemas } from "@/lib/api";
import {
  DIRECTION_LABEL,
  STATUS_LABEL,
  clock,
  dmy,
  type BookingStatus,
} from "@/lib/booking";
import { vnd } from "../flights/itineraries";

type Paged = Schemas["PagedModelBookingSummary"];
type Load = { qs: string; data: Paged } | { qs: string; error: string };

const STATUSES = Object.keys(STATUS_LABEL) as BookingStatus[];

/** Link giữ bộ lọc trạng thái; trang đếm từ 1 trên URL (API đếm từ 0). */
function href(status: string, page = 1) {
  const u = new URLSearchParams();
  if (status) u.set("status", status);
  if (page > 1) u.set("page", String(page));
  const q = u.toString();
  return q ? `/bookings?${q}` : "/bookings";
}

/** C-08 (APP_FLOW §2.1, FR-40): booking của tôi, mới nhất trước, lọc theo trạng thái, 20 booking mỗi trang. */
export default function BookingList() {
  const router = useRouter();
  const params = useSearchParams();
  const status = params.get("status") ?? "";
  const page = Math.max(1, Math.floor(Number(params.get("page"))) || 1);
  const query = new URLSearchParams({ page: String(page - 1) });
  if (status) query.set("status", status);
  const qs = query.toString();

  const [load, setLoad] = useState<Load>();
  useEffect(() => {
    let live = true;
    apiFetch<Paged>(`/bookings?${qs}`).then(
      (data) => live && setLoad({ qs, data }),
      (e: ApiError) => {
        if (!live) return;
        if (e.status === 401) router.replace("/login?next=%2Fbookings");
        else setLoad({ qs, error: e.detail });
      },
    );
    return () => {
      live = false;
    };
  }, [qs, router]);
  const current = load?.qs === qs ? load : undefined;

  let body;
  if (!current) {
    body = (
      <div aria-hidden="true" className="grid gap-16 motion-safe:animate-pulse">
        {[0, 1, 2].map((i) => (
          <div key={i} className="h-120 border border-line bg-tint" />
        ))}
      </div>
    );
  } else if ("error" in current) {
    body = <Notice>{current.error}</Notice>;
  } else if (current.data.content.length === 0) {
    body = (
      <p className="text-muted">
        {page > 1
          ? "Trang này không có đặt chỗ nào. "
          : status
            ? "Không có đặt chỗ nào ở trạng thái này. "
            : "Bạn chưa có đặt chỗ nào. "}
        <Link href="/" className={textLink}>
          Tìm chuyến bay
        </Link>
      </p>
    );
  } else {
    const { content, page: meta } = current.data;
    body = (
      <>
        <ul aria-label="Danh sách đặt chỗ" className="grid gap-16">
          {content.map((b) => (
            <li key={b.code}>
              <Link
                href={`/bookings/${b.code}`}
                className="grid gap-8 border border-line p-22 transition-colors hover:border-ink sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
              >
                <div className="grid gap-4">
                  <p>
                    <span className="font-bold">{b.code}</span>
                    <span className="text-muted">{`, ${STATUS_LABEL[b.status]}`}</span>
                  </p>
                  {b.routes.map((r) => (
                    <p key={r.direction} className="text-caption">
                      {`${DIRECTION_LABEL[r.direction]}: ${r.departureAirport} → ${r.arrivalAirport}, ${clock(r.departureTime)} ${dmy(r.departureTime)}`}
                    </p>
                  ))}
                  {b.status === "PENDING_PAYMENT" && (
                    <p className="text-caption text-muted">
                      {`Giữ chỗ đến ${clock(b.holdExpiresAt)} ${dmy(b.holdExpiresAt)} (giờ Việt Nam)`}
                    </p>
                  )}
                </div>
                <p className="text-subheading font-bold">
                  {vnd(b.totalAmount)}
                </p>
              </Link>
            </li>
          ))}
        </ul>
        {meta.totalPages > 1 && (
          <nav
            aria-label="Phân trang"
            className="flex items-center gap-22 text-caption"
          >
            {page > 1 && (
              <Link href={href(status, page - 1)} className={textLink}>
                Trang trước
              </Link>
            )}
            <span className="text-muted">{`Trang ${page}/${meta.totalPages}`}</span>
            {page < meta.totalPages && (
              <Link href={href(status, page + 1)} className={textLink}>
                Trang sau
              </Link>
            )}
          </nav>
        )}
      </>
    );
  }

  return (
    <div className="min-h-dvh bg-paper">
      <CustomerHeader />
      <main className="mx-auto grid max-w-page gap-38 px-16 pt-22 pb-119 sm:px-38">
        <h1 className="text-heading-lg font-bold">Đặt chỗ của tôi.</h1>
        {params.get("payment") === "failed" && (
          <Notice>
            Thanh toán chưa thành công. Hãy mở đặt chỗ cần thanh toán bên dưới
            để thử lại.
          </Notice>
        )}
        <nav
          aria-label="Lọc theo trạng thái"
          className="flex flex-wrap gap-x-22 gap-y-8 text-caption"
        >
          {["", ...STATUSES].map((s) => (
            <Link
              key={s}
              href={href(s)}
              aria-current={s === status ? "page" : undefined}
              className={
                s === status
                  ? "font-bold underline underline-offset-4"
                  : "text-muted transition-colors hover:text-ink"
              }
            >
              {s ? STATUS_LABEL[s as BookingStatus] : "Tất cả"}
            </Link>
          ))}
        </nav>
        {body}
      </main>
    </div>
  );
}
```

Viết lại `frontend/src/app/bookings/page.tsx`:

```tsx
import type { Metadata } from "next";
import { Suspense } from "react";
import BookingList from "./booking-list";

export const metadata: Metadata = { title: "Đặt chỗ của tôi | SkyLine" };

/** C-08 (APP_FLOW §2.1): Customer, trang mặc định sau đăng nhập. useSearchParams cần Suspense (Cache Components). */
export default function Page() {
  return (
    <Suspense>
      <BookingList />
    </Suspense>
  );
}
```

`?status=` sai giá trị (sửa tay URL) thì backend trả 400 và trang hiện câu lỗi của backend trong khung thông báo.

- [ ] **Step 4: Chạy test để thấy nó xanh**

```bash
cd frontend && npx prettier --write src/app/bookings/booking-list.tsx src/app/bookings/page.tsx e2e/booking.spec.ts && npx playwright test booking.spec smoke account
```

Expected: tất cả xanh (3 đặt vé, 11 smoke, 2 tài khoản; đăng ký và đăng nhập của Customer đều về `/bookings`).

- [ ] **Step 5: Lint, build, xem giao diện**

```bash
cd frontend && npm run lint && npm run build
```

Expected: không lỗi. Xem `/bookings` ở 390px và 1280px, sáng và tối: hàng lọc trạng thái xuống dòng gọn, không có thanh cuộn ngang.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/bookings/booking-list.tsx frontend/src/app/bookings/page.tsx frontend/e2e/booking.spec.ts
git commit -m "feat(frontend): danh sách booking của tôi (C-08), lọc trạng thái và phân trang"
```

---

### Task 5: Kiểm tra toàn bộ và cập nhật tài liệu

**Files:**

- Modify: `docs/superpowers/plans/2026-10-09-00-roadmap.md`, `CLAUDE.md`

- [ ] **Step 1: Chạy toàn bộ kiểm tra frontend**

```bash
cd frontend && npm run lint && npm run build && npx playwright test
```

Expected: lint, build không lỗi; Playwright `33 passed` (13 unit: 4 `itineraries` + 9 `booking-request`; 4 tìm chuyến; 3 đặt vé; 11 smoke; 2 tài khoản). Chạy `npx playwright test` thêm 2 lần nữa, cả 3 lần đều xanh. Nếu test đặt vé chập chờn vì `npm run dev` dịch route chậm, chạy lại với `E2E_BASE_URL` trỏ tới bản build (`npx next start -p 3100`, xem `playwright.config.ts`) để phân biệt lỗi thật với dịch chậm.

- [ ] **Step 2: Thử tay với VNPay sandbox (cần khoá sandbox trong `.env`)**

Bỏ qua bước này nếu chưa có `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`. Nếu có: đăng nhập `customer@demo.local`, đặt VJ123 ngày mai từ trang chủ, bấm "Thanh toán", trả bằng thẻ NCB `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456`.

Expected: VNPay chuyển về `/bookings/{mã}?payment=success`; C-09 hiện "Thanh toán thành công. Vé điện tử đã được gửi tới ...", trạng thái "Đã xuất vé", mỗi vé có số 13 chữ số; Mailpit (http://localhost:8025) có email vé. Thử lại với một booking khác và bấm "Huỷ thanh toán" trên VNPay: về `?payment=failed`, vẫn còn nút "Thanh toán".

- [ ] **Step 3: Cập nhật lộ trình**

Trong `docs/superpowers/plans/2026-10-09-00-roadmap.md`:

(a) Ở dòng "Plan chi tiết đã có" của bảng đầu file, thêm vào cuối (trước ` |`): ` · [Plan 13 — Màn hình đặt vé và quản lý booking](2026-10-15-13-booking-screens.md)`.

(b) Ở §2, dòng Plan 12 và dòng Plan 13, đổi `☐` cuối dòng thành `☑` (Plan 12 đã xong nhưng chưa được đánh dấu).

(c) Ở §6, thêm dòng sau ngay dưới dòng `| 12 | ...`:

```markdown
| 13 | Màn `/checkout` (C-07), `/bookings` (C-08), `/bookings/[code]` (C-09), đầu trang chung `src/components/customer-header.tsx`. `src/lib/selection.ts` thêm `parseSelection`, `useSelection()` (`undefined` khi prerender, `null` khi không có) và `clearSelection()` (gọi sau khi giữ chỗ). `src/lib/booking.ts`: `STATUS_LABEL`, `DIRECTION_LABEL`, `PASSENGER_LABEL`, `dmy`, `clock` (đọc thẳng chuỗi có offset), `countdown(ms)`, `useNow()`. `src/app/checkout/booking-request.ts`: `asciiUpper` (BR-12), `passengerTypes`, `isInternational(selection, airports)` (BR-13), `bagField`, `bookingRequest(selection, formData, voucherCode)`, `searchUrl(selection)`. Form C-07 đặt tên ô theo đường dẫn trường của `errors` backend (`passengers[i].x`, `contact.x`). `src/components/form.tsx` thêm `SelectField`; `src/components/price-table.tsx`: `PriceTable({rows, total})`. `fareRules` chuyển sang `src/app/flights/itineraries.ts`. C-02 hiện thông báo khi URL có `unavailable=1` (C-07 mở lại C-02 khi chuyến hết chỗ hoặc ngừng bán). C-09 chưa có hoàn, đổi chuyến (Plan 17 thêm vào `booking-view.tsx`). E2E `e2e/booking.spec.ts` chạy ở múi giờ New York, giả lập trang VNPay bằng `page.route`, dùng `page.clock` cho hết hạn giữ chỗ |
```

(d) Ở §7, thêm mục mới vào cuối danh sách:

```markdown
14. **Plan 13 có 4 điểm cố ý khác TDD và APP_FLOW** (chi tiết ở [Plan 13](2026-10-15-13-booking-screens.md#điểm-cố-ý-khác-tdd-và-app_flow)): báo giá chỉ gọi khi mọi ô đã hợp lệ (chờ 400 ms), trước đó bảng giá là tạm tính; đọc dữ liệu ở Client Component, không ở Server Component chuyển tiếp cookie; giờ đọc thẳng từ chuỗi có offset; C-09 chưa có hoàn, đổi chuyến (Plan 17).
```

- [ ] **Step 4: Cập nhật CLAUDE.md**

Trong `CLAUDE.md`, mục Status, thay:

```
the account screens C-03–C-06 and C-12 are done (Plan 04), and the other screens are placeholders (`/checkout` is the login-protected C-07 placeholder).
```

bằng:

```
the account screens C-03–C-06 and C-12 are done (Plan 04), the booking screens C-07 `/checkout` (passengers, baggage, voucher, live quote, hold), C-08 `/bookings` and C-09 `/bookings/[code]` (hold countdown, VNPay payment, cancel) are done (Plan 13), and the other screens are placeholders.
```

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-10-09-00-roadmap.md CLAUDE.md
git commit -m "docs: hoàn tất Plan 13, cập nhật hợp đồng màn hình đặt vé"
```
