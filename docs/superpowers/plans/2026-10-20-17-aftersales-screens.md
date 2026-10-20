# Plan 17 — Màn hình hậu mãi: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khách tự hoàn hoặc đổi một chiều trên giao diện: C-09 hiện "Hoàn chiều này", "Đổi chiều này", danh sách yêu cầu hoàn, yêu cầu đổi và nút "Thanh toán đổi chuyến"; C-10 `/bookings/[code]/refund/[direction]` xem số tiền hoàn và gửi yêu cầu; C-11 `/bookings/[code]/reschedule/[direction]` chọn ngày, chọn chuyến thay thế, xem báo giá, xác nhận rồi thanh toán qua VNPay (hoặc hoàn tất ngay khi phải trả 0 đ).

**Architecture:** Không đổi backend, không sinh lại kiểu API: mọi API đã có từ Plan 14, 15. C-10 và C-11 là Client Component trong `<Suspense>` (đọc `useParams`), gọi API qua `apiFetch` như C-09. Quy tắc hiện nút hoàn, đổi trên C-09 là một hàm thuần `journeyActions` ở `src/lib/booking.ts`, có unit test; backend vẫn kiểm tra lại đủ BR-51, BR-60, BR-70 và C-10, C-11 hiện câu báo lỗi của backend. E2E tạo booking đã xuất vé bằng API rồi ký Return URL của VNPay bằng `VNPAY_HASH_SECRET` trong `.env` (cùng khoá với backend profile `dev`), nên đi được trọn luồng thanh toán đổi chuyến mà không cần mạng ngoài.

**Tech Stack:** Next.js 16.4 (App Router, Cache Components), React 19.3, TypeScript, Tailwind v4, Playwright 1.64 (E2E, `page.route`; unit test không mở trình duyệt), `node:crypto` trong E2E. Không thêm thư viện.

**Spec:** [App Flow](../../APP_FLOW.md) §2.1 C-09 (bảng thao tác), C-10, C-11, UF-02, UF-03, §4.5, §4.6 · [PRD](../../PRD.md) FR-41, FR-50–FR-52, FR-60–FR-62, BR-50–BR-53, BR-60, BR-61, BR-70–BR-75, BR-104 · [TDD](../../TDD.md) §6.3 (chữ ký VNPay), §7 (giờ), §9 (mã lỗi) · [DESIGN.md](../../../DESIGN.md) · [Lộ trình](2026-10-09-00-roadmap.md) §6 (hợp đồng Plan 12–16) · Plan frontend trước: [Plan 13](2026-10-15-13-booking-screens.md) (C-09, `booking.ts`, cách viết E2E và unit test)

## Global Constraints

- Mọi Global Constraints của [Plan 13](2026-10-15-13-booking-screens.md#global-constraints) vẫn giữ: token và pill (`pillDark`, `pillLine`, `textLink` của `src/components/form.tsx`), `--spacing: 1px`, không viết `dark:`, chữ tiếng Việt không dùng gạch dài; **không gọi `new Date()`, `Date.now()`, `Math.random()` khi render** (giờ hiện tại lấy bằng `useNow()`); component dùng `useParams`/`useSearchParams` nằm trong `<Suspense>`; tiền hiển thị bằng `vnd()`; giờ bay đọc thẳng từ chuỗi có offset (`clock`, `dmy`, `hhmm`), mốc của booking ghi kèm "(giờ Việt Nam)"; API trả 401 thì `router.replace("/login?next=" + đường dẫn hiện tại)`.
- **Không thêm thư viện npm. Không sửa backend, không chạy `npm run gen:api`.** Kiểu lấy từ `Schemas[...]`: `BookingDetail`, `JourneyView`, `RefundRequestView`, `RescheduleView`, `RefundQuote`, `RefundCreatedResponse`, `RescheduleOptions`, `RescheduleOption`, `RescheduleQuote`, `RescheduleCreatedResponse`, `ReschedulePaymentResponse`, `Itinerary`.
- **Trường có thể `null` dù kiểu sinh ra ghi `string`:** `RefundRequestView.direction` (nguồn `INVALID_PAYMENT`), `reason`, `staffNote`, `completedAt`; `RescheduleView.completedAt`. `RefundRequestView.status`, `source`, `RescheduleView.status`, `JourneyView.status` là `string` (không phải union) trong kiểu sinh ra.
- **API nhận `direction` viết hoa** (`OUTBOUND`, `RETURN`; backend so sánh nguyên văn). URL của C-10, C-11 dùng chữ thường (`/refund/outbound`, `/reschedule/return`).
- **Route mới** vào danh sách `PROTECTED` của `e2e/smoke.spec.ts`. `proxy.ts` đã chặn `/bookings/:path*`, không cần sửa.

### Điểm cố ý khác APP_FLOW

1. **C-09 không kiểm tra hạn chót 24 giờ của BR-51** khi quyết định hiện "Hoàn chiều này", "Đổi chiều này" (APP_FLOW C-09 ghi "chiều thoả BR-51"). Tham số `aftersales.min_hours_before_departure` không có trong `BookingDetail`; nút chỉ ẩn khi chặng đầu đã cất cánh. Khi quá hạn, C-10, C-11 hiện câu `DEADLINE_PASSED` của backend. Bốn điều kiện còn lại của BR-51 và BR-60, BR-70 được kiểm tra đúng trên dữ liệu của `BookingDetail`.

### Điểm plan tự chọn (APP_FLOW và PRD không nói)

- **C-10 chỉ gọi `refund-quote`**, không tải lại booking: tiêu đề ghi chiều, link quay về C-09 ghi mã đặt chỗ. Lý do hoàn là ô một dòng (`Field`, bắt buộc, tối đa 500 ký tự như `ReasonBody`).
- **C-11 tải `GET /bookings/{code}`** để hiện chiều hiện tại và lấy ngày mặc định của ô ngày = ngày cất cánh của chặng đầu theo giờ sân bay (đọc từ chuỗi có offset). Ngày nằm trong state, không đưa lên URL.
- **Danh sách chuyến thay thế** là thẻ riêng của C-11 (giờ, sân bay, thời lượng, điểm dừng, ngày bay, "Phải trả X"), không dùng lại `ItineraryCard` của C-02 vì thẻ đó hiện giá vé và các gói giá. Chọn một chuyến thì gọi `reschedule-quote` (APP_FLOW C-11) và hiện báo giá ở cột phải.
- **Đổi ngày nhanh:** mỗi lần tải danh sách gắn với ngày đang chọn; phản hồi của ngày cũ về muộn bị bỏ (cờ `live` trong cleanup của `useEffect`, như C-09).
- **Lỗi khi báo giá hoặc xác nhận** (`SEATS_UNAVAILABLE`, `FLIGHT_NOT_BOOKABLE`, `ITINERARY_INVALID`, `REQUEST_IN_PROGRESS`...): hiện câu của backend, bỏ chuyến đã chọn và tải lại danh sách.
- **Xác nhận đổi:** `COMPLETED` (phải trả 0 đ) thì `router.push("/bookings/{code}?reschedule=done")`; `PENDING_PAYMENT` thì gọi ngay `POST /reschedules/{id}/payments` rồi `window.location.assign(paymentUrl)`. Tạo lượt thu lỗi thì mở C-09, nơi yêu cầu đang chờ có nút "Thanh toán đổi chuyến".
- **Gửi xong yêu cầu hoàn** thì `router.push("/bookings/{code}?refund=sent")`; C-09 hiện thông báo theo `?refund=sent`, `?reschedule=done`.
- **Bấm đúp "Gửi yêu cầu hoàn", "Xác nhận đổi"** bị chặn bằng `useRef` (như "Giữ chỗ" của Plan 13): backend đã chỉ cho một yêu cầu mở mỗi chiều, nhưng lần bấm thứ hai sẽ hiện lỗi `REQUEST_IN_PROGRESS` thoáng qua.
- **Nút "Thanh toán đổi chuyến" trên C-09** ẩn khi đếm ngược tới `holdExpiresAt` về 0, như nút thanh toán booking (BR-73, BR-75). `?payment=failed` mà còn yêu cầu đổi thanh toán được thì thêm câu "thanh toán lại được".
- **Mỗi chiều trên C-09 là một `<article aria-label="Chiều đi">`** để link "Hoàn chiều này", "Đổi chiều này" thuộc đúng chiều.
- **E2E tạo booking đã xuất vé bằng API** (`page.request`, dùng chung cookie với trang) và ký Return URL VNPay bằng `VNPAY_HASH_SECRET` đọc từ `../.env` (`process.loadEnvFile`). C-07 đã có E2E riêng ở Plan 13 nên không đi lại qua form.

## Review Focus

Những tình huống tài liệu không nói rõ nhưng dễ gây lỗi thật, xếp theo khả năng gặp. Mỗi dòng có test ghim lại.

1. **Mở thẳng C-10 hoặc C-11 khi chiều không còn hoàn, đổi được** (đã có yêu cầu mở, bấm Back sau khi gửi, lưu link cũ). Trang phải hiện câu của backend, không có form gửi (Task 2 E2E: mở lại `/refund/outbound` sau khi gửi thấy "Chiều này đang có yêu cầu hoàn hoặc đổi chuyến chưa xong" và không có nút "Gửi yêu cầu hoàn"; Task 3 E2E: `/reschedule/outbound` cũng hiện câu đó và không có danh sách).
2. **Bấm đúp "Gửi yêu cầu hoàn" hoặc "Xác nhận đổi".** Trang chỉ gửi một request, nên không có lỗi `REQUEST_IN_PROGRESS` thoáng qua (Task 2 E2E: `dblclick` rồi đếm đúng 1 `POST .../refund-requests`, khu "Yêu cầu hoàn" có 1 dòng; Task 3 E2E: `dblclick` "Xác nhận đổi" rồi đếm đúng 1 `POST .../reschedules`, khu "Đổi chuyến" có 1 dòng).
3. **Đổi ngày nhanh trên C-11**: danh sách của ngày cũ về muộn không được đè ngày mới (Task 3 E2E: `page.route` làm chậm 1,5 giây lần tải của ngày mặc định, đổi ngay sang ngày khác; cuối cùng danh sách là của ngày mới).
4. **Trình duyệt ở múi giờ khác sân bay**: ngày mặc định của C-11 là ngày cất cánh theo giờ sân bay, không theo giờ trình duyệt (Task 3 E2E, múi giờ New York: VN213 cất cánh 06:00 giờ Hà Nội, tức 19:00 hôm trước ở New York; ô ngày phải là ngày Việt Nam).
5. **VNPay báo thất bại khi thanh toán đổi chuyến**: C-09 báo chưa thành công, yêu cầu đổi vẫn chờ, nút "Thanh toán đổi chuyến" còn và trả lại được (Task 3 E2E: mở `?payment=failed`, bấm lại nút, ký Return URL thành công thì chiều về có chuyến mới).

Đã kiểm thêm: yêu cầu hoàn `INVALID_PAYMENT` (không có chiều) không chặn nút của chiều nào (Task 1); yêu cầu bị từ chối, đổi đã hết hạn không chặn (Task 1); booking không có chiều về mà mở `/reschedule/return`, hay `direction` lạ, hiện "Không tìm thấy đặt chỗ." (Task 1 `directionParam`, smoke).

---

## Trước khi bắt đầu

- **Cần có:** Plan 13, 14, 15 đã xong (Plan 16 cũng đã xong); Node 24; Docker Desktop đang chạy.
- **Nhánh:** tạo `feature/aftersales-screens` từ `develop`.
- **Chạy lệnh ở đâu:** Git Bash, từ thư mục gốc repo, trừ khi bước đó ghi `cd frontend`.
- **Backend cho E2E:** như Plan 13, chạy suốt plan ở một terminal riêng:

```bash
docker compose up -d postgres mailpit
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Seed phải còn mới (`V106` sinh chuyến 60 ngày tính từ lúc migrate; xem cách kiểm tra ở Plan 13). `.env` phải có `VNPAY_HASH_SECRET` khác rỗng (backend cần nó để tạo link thanh toán; E2E cần nó để ký Return URL) và `VNPAY_PAY_URL` mặc định `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`.

- **Dữ liệu seed E2E dựa vào** (`V102`, `V106`): QH201 HAN→SGN 10:00–12:10, QH202 SGN→HAN 15:00–17:10, gói QH "Economy Smart" (hoàn được, phí hoàn 500.000 đ; đổi được, phí đổi 300.000 đ), giá 1.190.000 × 1,45 làm tròn 10.000 = **1.730.000 đ**; VN213 HAN→SGN 06:00–08:10, gói VN "Economy Flex" (phí đổi 0 đ). Trước khi viết số tiền vào test, xác nhận giá thật: `curl -s "http://localhost:8080/api/flights/search?from=HAN&to=SGN&date=$(TZ=Asia/Ho_Chi_Minh date -d '+3 days' +%F)&adults=1&cabin=ECONOMY" | grep -o '"name":"Economy Smart"[^}]*"totalPrice":[0-9]*'` phải có `"totalPrice":1730000`.
- **Chạy E2E:** `cd frontend && npx playwright test <tên file>`.
- **Trạng thái kiểm chứng:** plan viết theo code thật của Plan 13–16 (đọc ngày 2026-10-20) nhưng **chưa chạy thử**. Ở mỗi bước "Run", kết quả khác "Expected" thì tìm nguyên nhân, sửa code và sửa luôn plan cho khớp rồi mới đi tiếp.

## Cấu trúc file sau plan

```
frontend/
  e2e/
    booking-request.spec.ts        # Task 1: + unit test journeyActions, directionParam
    aftersales.spec.ts             # Task 2 (C-10), Task 3 (C-11): E2E, helper issuedBooking, paidReturn
    smoke.spec.ts                  # Task 2, 3: + 2 route vào PROTECTED
  src/
    lib/
      booking.ts                   # Task 1: + REFUND_STATUS, REFUND_SOURCE, RESCHEDULE_STATUS, directionParam, journeyActions
    components/
      price-table.tsx              # Task 2: + totalLabel
    app/bookings/[code]/
      booking-view.tsx             # Task 2: article theo chiều, link hoàn, khu "Yêu cầu hoàn", ?refund=sent
                                   # Task 3: link đổi, khu "Đổi chuyến", thanh toán đổi chuyến, ?reschedule=done
      refund/[direction]/
        page.tsx                   # Task 2: Suspense + RefundView
        refund-view.tsx            # Task 2: C-10
      reschedule/[direction]/
        page.tsx                   # Task 3: Suspense + RescheduleView
        reschedule-view.tsx        # Task 3: C-11
```

## API dùng trong plan (đã có, không đổi)

| Method | Đường dẫn (bỏ `/api`)                                                      | Dùng ở                                                                                          |
| ------ | -------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| GET    | `/bookings/{code}`                                                         | C-09; C-11 (chiều hiện tại, ngày mặc định)                                                      |
| GET    | `/bookings/{code}/journeys/{DIRECTION}/refund-quote`                       | C-10: 200 `RefundQuote{journeyValue, fee, refundAmount}`                                        |
| POST   | `/bookings/{code}/journeys/{DIRECTION}/refund-requests`                    | C-10: body `{reason}`, 201 `RefundCreatedResponse`                                              |
| GET    | `/bookings/{code}/journeys/{DIRECTION}/reschedule-options?date=yyyy-mm-dd` | C-11: 200 `RescheduleOptions{options[{itinerary, quote}]}`, đã xếp theo `amountDue`             |
| POST   | `/bookings/{code}/journeys/{DIRECTION}/reschedule-quote`                   | C-11: body `{flightIds}`, 200 `RescheduleQuote`                                                 |
| POST   | `/bookings/{code}/journeys/{DIRECTION}/reschedules`                        | C-11: body `{flightIds}`, 201 `RescheduleCreatedResponse{id, status, amountDue, holdExpiresAt}` |
| POST   | `/reschedules/{id}/payments`                                               | C-11, C-09: 201 `{paymentUrl}` rồi chuyển sang VNPay                                            |

Return URL của VNPay (`/api/payments/vnpay/return`) về `/bookings/{code}?payment=success|failed` cho cả lượt thu của booking lẫn của yêu cầu đổi (Plan 12, 15).

## Mã lỗi và cách hiện

| Mã                                                                                 | Ở đâu      | Hiện thế nào                                                                     |
| ---------------------------------------------------------------------------------- | ---------- | -------------------------------------------------------------------------------- |
| 401                                                                                | mọi màn    | `router.replace("/login?next=...")`                                              |
| `RESOURCE_NOT_FOUND` (404), `direction` lạ                                         | C-10, C-11 | Tiêu đề "Không tìm thấy đặt chỗ." (BR-104)                                       |
| `FARE_RULE_NOT_ALLOWED`, `DEADLINE_PASSED`, `REQUEST_IN_PROGRESS`, `INVALID_STATE` | C-10       | Lúc tải: câu của backend, không có form. Lúc gửi: câu của backend trên form      |
| như trên                                                                           | C-11       | Lúc tải danh sách: câu của backend thay cho danh sách                            |
| `SEATS_UNAVAILABLE`, `FLIGHT_NOT_BOOKABLE`, `ITINERARY_INVALID` và mọi lỗi khác    | C-11       | Lúc báo giá, xác nhận: câu của backend, bỏ chuyến đã chọn, tải lại danh sách     |
| `VALIDATION_FAILED` có `errors` (`reason`)                                         | C-10       | Dưới ô lý do (`useSubmit`)                                                       |
| `HOLD_EXPIRED`, `INVALID_STATE`                                                    | C-09       | Thanh toán đổi chuyến: câu của backend, tải lại booking (như thanh toán booking) |

---

### Task 1: Quy tắc hiện thao tác hậu mãi và nhãn trạng thái

**Files:**

- Modify: `frontend/src/lib/booking.ts`
- Test: `frontend/e2e/booking-request.spec.ts`

**Interfaces:**

- Consumes: `Schemas["BookingDetail"]`, `Schemas["JourneyView"]`, `Schemas["JourneyRequest"]["direction"]` từ `@/lib/api`.
- Produces (Task 2, 3 dùng):
  - `type Direction = Schemas["JourneyRequest"]["direction"]`
  - `REFUND_STATUS: Record<string, string>` = `PENDING` "Chờ duyệt", `APPROVED` "Đã duyệt, đang hoàn tiền", `REJECTED` "Bị từ chối", `COMPLETED` "Đã hoàn tiền"
  - `REFUND_SOURCE: Record<string, string>` = `CUSTOMER` "Khách yêu cầu", `FLIGHT_CANCELLED` "Chuyến bay bị huỷ", `INVALID_PAYMENT` "Thanh toán sau khi hết hạn giữ chỗ"
  - `RESCHEDULE_STATUS: Record<string, string>` = `PENDING_PAYMENT` "Chờ thanh toán", `COMPLETED` "Hoàn tất", `EXPIRED` "Hết hạn thanh toán", `CANCELLED` "Đã huỷ"
  - `directionParam(param: string): Direction | null` — `"outbound"` → `"OUTBOUND"`, `"return"` → `"RETURN"`, còn lại `null` (phân biệt hoa thường: URL do C-09 tạo luôn viết thường)
  - `journeyActions(booking: BookingDetail, journey: JourneyView, now: number | null): { refund: boolean; change: boolean }`

- [ ] **Step 1: Viết unit test (thêm vào cuối `e2e/booking-request.spec.ts`, import thêm `directionParam`, `journeyActions` từ `../src/lib/booking`)**

```ts
// journeyActions: dựng BookingDetail tối thiểu; chỉ các trường hàm đọc.
const NOW = Date.parse("2026-11-01T00:00:00+07:00");
type Changes = Record<string, unknown>;
const leg = (direction: "OUTBOUND" | "RETURN", changes: Changes = {}) => ({
  direction,
  status: "ACTIVE",
  refundable: true,
  changeable: true,
  segments: [{ departureTime: "2026-11-10T10:00:00+07:00" }],
  ...changes,
});
const detail = (changes: Changes = {}) =>
  ({
    status: "ISSUED",
    journeys: [leg("OUTBOUND"), leg("RETURN")],
    refundRequests: [],
    reschedules: [],
    ...changes,
  }) as unknown as Schemas["BookingDetail"];
const actions = (
  b: Schemas["BookingDetail"],
  i: number,
  now: number | null = NOW,
) => journeyActions(b, b.journeys[i], now);

test("journeyActions: đã xuất vé, chiều ACTIVE, chưa bay thì theo cờ của gói (BR-60, BR-70)", () => {
  expect(actions(detail(), 0)).toEqual({ refund: true, change: true });
  const lite = detail({ journeys: [leg("OUTBOUND", { refundable: false })] });
  expect(actions(lite, 0)).toEqual({ refund: false, change: true });
});

test("journeyActions: không hiện khi chưa xuất vé, chiều đã hoàn, đã cất cánh hoặc chưa hydrate (BR-51)", () => {
  const none = { refund: false, change: false };
  expect(actions(detail({ status: "PENDING_PAYMENT" }), 0)).toEqual(none);
  expect(
    actions(detail({ journeys: [leg("OUTBOUND", { status: "REFUNDED" })] }), 0),
  ).toEqual(none);
  expect(actions(detail(), 0, Date.parse("2026-11-10T10:00:00+07:00"))).toEqual(
    none,
  );
  expect(actions(detail(), 0, null)).toEqual(none);
});

test("journeyActions: yêu cầu hoàn đang mở hoặc đổi chờ thanh toán chỉ chặn đúng chiều đó (BR-51)", () => {
  const refund = (direction: string | null, status: string) => ({
    direction,
    status,
  });
  const open = detail({ refundRequests: [refund("OUTBOUND", "APPROVED")] });
  expect(actions(open, 0).refund).toBe(false);
  expect(actions(open, 1).refund).toBe(true);
  const closed = detail({
    refundRequests: [
      refund("OUTBOUND", "REJECTED"),
      refund("OUTBOUND", "COMPLETED"),
      refund(null, "PENDING"),
    ],
  });
  expect(actions(closed, 0)).toEqual({ refund: true, change: true });
  const changing = detail({
    reschedules: [{ direction: "RETURN", status: "PENDING_PAYMENT" }],
  });
  expect(actions(changing, 1)).toEqual({ refund: false, change: false });
  expect(actions(changing, 0)).toEqual({ refund: true, change: true });
  const expired = detail({
    reschedules: [{ direction: "RETURN", status: "EXPIRED" }],
  });
  expect(actions(expired, 1).change).toBe(true);
});

test("directionParam: chỉ nhận outbound, return", () => {
  expect(directionParam("outbound")).toBe("OUTBOUND");
  expect(directionParam("return")).toBe("RETURN");
  expect(directionParam("RETURN")).toBeNull();
  expect(directionParam("abc")).toBeNull();
});
```

Thêm `import type { Schemas } from "../src/lib/api";` nếu file chưa có.

- [ ] **Step 2: Chạy để thấy test fail**

Run: `cd frontend && npx playwright test booking-request`
Expected: FAIL, `journeyActions`/`directionParam` không được export.

- [ ] **Step 3: Cài đặt trong `src/lib/booking.ts`**

Thêm `Direction`, ba bảng nhãn và hai hàm ở Interfaces. `journeyActions`: điều kiện chung = `now !== null` và booking `ISSUED` và chiều `ACTIVE` và `Date.parse(journey.segments[0].departureTime) > now` và không có yêu cầu hoàn cùng `direction` ở `PENDING`/`APPROVED` và không có yêu cầu đổi cùng `direction` ở `PENDING_PAYMENT` (đúng truy vấn `OPEN` của `JourneyRules`: yêu cầu `INVALID_PAYMENT` không có chiều nên không chặn). `refund` = chung && `refundable`; `change` = chung && `changeable`. JSDoc ghi rõ: không kiểm tra hạn chót `aftersales.min_hours_before_departure` (không có trong `BookingDetail`); C-10, C-11 hiện `DEADLINE_PASSED` của backend.

- [ ] **Step 4: Chạy lại**

Run: `cd frontend && npx playwright test booking-request`
Expected: PASS, mọi test cũ và 4 test mới xanh.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/booking.ts frontend/e2e/booking-request.spec.ts
git commit -m "feat(frontend): quy tắc hiện thao tác hoàn, đổi một chiều (BR-51, BR-60, BR-70)"
```

---

### Task 2: Hoàn vé (C-10) và phần hoàn của C-09

**Files:**

- Create: `frontend/src/app/bookings/[code]/refund/[direction]/page.tsx`, `frontend/src/app/bookings/[code]/refund/[direction]/refund-view.tsx`, `frontend/e2e/aftersales.spec.ts`
- Modify: `frontend/src/app/bookings/[code]/booking-view.tsx`, `frontend/src/components/price-table.tsx`, `frontend/e2e/smoke.spec.ts`

**Interfaces:**

- Consumes: Task 1 (`journeyActions`, `directionParam`, `REFUND_STATUS`, `REFUND_SOURCE`, `DIRECTION_LABEL`); `Field`, `Notice`, `FormSkeleton`, `useSubmit`, `text` của `form.tsx`; `SiteHeader`.
- Produces:
  - `PriceTable({ rows, total, totalLabel = "Tổng thanh toán" })` (Task 3 dùng `totalLabel`).
  - `booking-view.tsx` export `Section({title, children})` và `at(iso)` (đang là hàm nội bộ) cho C-10, C-11.
  - E2E helper trong `aftersales.spec.ts` (Task 3 dùng): `day(n)`, `issuedBooking(page, fare, legs: {from, to, date, flight}[]) → Promise<{code, email}>`, `paidReturn(paymentUrl) → string`.
  - Link C-09 → C-10: `/bookings/{code}/refund/{direction viết thường}`.

- [ ] **Step 1: Viết E2E `e2e/aftersales.spec.ts` (helper + test hoàn) và thêm route vào smoke**

`smoke.spec.ts`: thêm `"/bookings/ZZZZZZ/refund/outbound"` vào `PROTECTED`.

Đầu `aftersales.spec.ts`: `test.use({ timezoneId: "America/New_York" })`; `day(n)` và `field(scope, label)` chép từ `booking.spec.ts`. Ba helper:

```ts
import { createHmac } from "node:crypto";

/**
 * Return URL VNPay báo thanh toán thành công cho lượt thu trong paymentUrl, ký như Vnpay.hash của backend: tham số
 * vnp_* khác rỗng, sắp theo tên, key=value nối bằng &, HMAC-SHA512 bằng VNPAY_HASH_SECRET (cùng ../.env với backend).
 */
function paidReturn(paymentUrl: string) {
  if (!process.env.VNPAY_HASH_SECRET) process.loadEnvFile("../.env");
  const secret = process.env.VNPAY_HASH_SECRET;
  if (!secret)
    throw new Error("Cần VNPAY_HASH_SECRET trong .env để ký Return URL");
  const sent = new URL(paymentUrl).searchParams;
  const params = new URLSearchParams({
    vnp_Amount: sent.get("vnp_Amount")!,
    vnp_TxnRef: sent.get("vnp_TxnRef")!,
    vnp_ResponseCode: "00",
    vnp_TransactionStatus: "00",
    vnp_TransactionNo: String(Date.now() % 100_000_000),
    vnp_PayDate: "20261020120000",
    vnp_BankCode: "NCB",
  });
  params.sort();
  const hash = createHmac("sha512", secret)
    .update(params.toString())
    .digest("hex");
  return `/api/payments/vnpay/return?${params}&vnp_SecureHash=${hash}`;
}
```

`issuedBooking(page, fare, legs)`: đăng ký tài khoản mới qua `/register` như test đầu của `booking.spec.ts` (email `e2e-${test.info().testId}-${Date.now()}@example.com`, chờ URL `/bookings`); `await page.request.get("/api/auth/csrf")` rồi lấy cookie `XSRF-TOKEN` từ `page.context().cookies()` làm header `X-XSRF-TOKEN`; với mỗi chặng gọi `GET /api/flights/search?from=&to=&date=&adults=1&children=0&infants=0&cabin=ECONOMY`, lấy hành trình có đúng một chặng mang `flightNumber` đã cho và gói có `name === fare`; `POST /api/bookings` với chặng thứ nhất `OUTBOUND`, thứ hai `RETURN`, một người lớn `{type: "ADULT", lastName: "PHAM", firstName: "THU HA", gender: "FEMALE", dateOfBirth: "1992-03-04", nationality: null, passportNumber: null, passportExpiry: null}`, `baggage: []`, `contact {name: "Phạm Thu Hà", email, phone: "0912345678"}`, `voucherCode: ""`; `POST /api/bookings/{code}/payments`; `page.goto(paidReturn(paymentUrl))` và chờ URL `/bookings/{code}?payment=success`. Mọi lời gọi `expect(res.ok()).toBe(true)` để lỗi lộ ngay chỗ.

Test:

```ts
test("hoàn chiều đi trên C-10 rồi theo dõi trên C-09 (UF-02)", async ({
  page,
}) => {
  test.slow();
  const { code, email } = await issuedBooking(page, "Economy Smart", [
    { from: "HAN", to: "SGN", date: day(3), flight: "QH201" },
    { from: "SGN", to: "HAN", date: day(5), flight: "QH202" },
  ]);
  const outbound = page.getByRole("article", { name: "Chiều đi" });
  await expect(page.getByText("Đã xuất vé", { exact: true })).toBeVisible();
  await outbound.getByRole("link", { name: "Hoàn chiều này" }).click();

  // C-10 (FR-50): giá trị chiều, phí hoàn, số tiền được hoàn (BR-52, BR-53, BR-61).
  await expect(page).toHaveURL(`/bookings/${code}/refund/outbound`);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Hoàn chiều đi.",
  );
  const amounts = page.getByRole("region", { name: "Số tiền hoàn" });
  await expect(amounts).toContainText("Giá trị chiều");
  await expect(amounts).toContainText("1.730.000 đ");
  await expect(amounts).toContainText("-500.000 đ");
  await expect(amounts.getByRole("definition").last()).toHaveText(
    "1.230.000 đ",
  );

  // Lý do bắt buộc (FR-51); bấm đúp chỉ gửi một yêu cầu.
  let posts = 0;
  page.on("request", (r) => {
    if (r.method() === "POST" && r.url().endsWith("/refund-requests")) posts++;
  });
  const send = page.getByRole("button", { name: "Gửi yêu cầu hoàn" });
  await send.click();
  await expect(page.getByText("Hãy nhập lý do hoàn vé.")).toBeVisible();
  await field(page, "Lý do hoàn vé").fill("Đổi kế hoạch công tác");
  await send.dblclick();

  await expect(page).toHaveURL(`/bookings/${code}?refund=sent`);
  await expect(page.getByRole("status")).toContainText(
    `Đã gửi yêu cầu hoàn. Nhân viên sẽ xét duyệt và báo kết quả qua email ${email}.`,
  );
  expect(posts).toBe(1);
  const requests = page
    .getByRole("region", { name: "Yêu cầu hoàn" })
    .getByRole("listitem");
  await expect(requests).toHaveCount(1);
  await expect(requests.first()).toContainText(
    "Chiều đi, Chờ duyệt, hoàn 1.230.000 đ",
  );
  await expect(requests.first()).toContainText("Lý do: Đổi kế hoạch công tác");
  // Chiều đi có yêu cầu đang mở thì không hoàn, đổi được nữa; chiều về vẫn được (BR-51).
  await expect(outbound.getByRole("link")).toHaveCount(0);
  await expect(
    page
      .getByRole("article", { name: "Chiều về" })
      .getByRole("link", { name: "Hoàn chiều này" }),
  ).toBeVisible();

  // Mở lại C-10 bằng URL cũ: câu của backend, không có form (FR-50).
  await page.goto(`/bookings/${code}/refund/outbound`);
  await expect(
    page.getByText("Chiều này đang có yêu cầu hoàn hoặc đổi chuyến chưa xong"),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Gửi yêu cầu hoàn" }),
  ).toHaveCount(0);
  await page.getByRole("link", { name: `Đặt chỗ ${code}` }).click();
  await expect(page).toHaveURL(`/bookings/${code}`);
});
```

- [ ] **Step 2: Chạy để thấy test fail**

Run: `cd frontend && npx playwright test aftersales smoke`
Expected: FAIL. `issuedBooking` chạy tới C-09 `?payment=success` (nếu fail ở đây thì sửa helper trước: chữ ký sai làm Return URL về `/bookings?payment=failed`), rồi không tìm thấy `article` "Chiều đi"; smoke fail ở route mới.

- [ ] **Step 3: `PriceTable` nhận `totalLabel`**

Prop tuỳ chọn `totalLabel?: string`, mặc định `"Tổng thanh toán"`, thay chữ cố định ở `<dt>` cuối. C-07, C-09 không đổi.

- [ ] **Step 4: C-09 — chiều là article, link hoàn, khu "Yêu cầu hoàn", thông báo `?refund=sent`**

Trong `booking-view.tsx`:

- Export `Section` và `at`.
- `BookingBody` nhận thêm `now: number | null`; `BookingView` truyền `now` sẵn có.
- Thẻ mỗi chiều đổi `div` thành `<article aria-label={DIRECTION_LABEL[j.direction]}>`, giữ nguyên class. Cuối thẻ: nếu `journeyActions(booking, j, now).refund` thì `<Link className={textLink} href={`/bookings/${code}/refund/${j.direction.toLowerCase()}`}>Hoàn chiều này</Link>` (lấy `code` từ `booking.code`).
- Khu `Section title="Yêu cầu hoàn"` ở cột phải, sau "Lịch sử thanh toán", chỉ khi `booking.refundRequests.length > 0`, `<ul>` mỗi yêu cầu một `<li className="grid gap-4">`:
  - dòng đậm: `${r.direction ? DIRECTION_LABEL[r.direction] : "Khoản thanh toán thừa"}, ${REFUND_STATUS[r.status] ?? r.status}, hoàn ${vnd(r.refundAmount)}`
  - caption muted: `Gửi lúc ${at(r.createdAt)} (giờ Việt Nam)`; nguồn khác `CUSTOMER` thêm `. ${REFUND_SOURCE[r.source]}`
  - `Lý do: ${r.reason}` nếu có; `Ghi chú của nhân viên: ${r.staffNote}` nếu có (FR-52: lý do từ chối nằm ở `staffNote`).
- `?refund=sent` (đọc `params.get("refund")`) và booking đã tải: `<Notice tone="ok">{`Đã gửi yêu cầu hoàn. Nhân viên sẽ xét duyệt và báo kết quả qua email ${booking.contact.email}.`}</Notice>`, đặt cạnh các thông báo `payment`.

- [ ] **Step 5: C-10 `refund/[direction]/page.tsx` và `refund-view.tsx`**

`page.tsx` như `bookings/[code]/page.tsx`: `metadata.title = "Hoàn vé | SkyLine"`, `<Suspense><RefundView /></Suspense>`, JSDoc tiếng Việt.

`RefundView` (`"use client"`), khung trang như C-09 (`SiteHeader`, `main` cùng class):

- `const { code, direction } = useParams<{ code: string; direction: string }>()`; `const dir = directionParam(direction)`.
- State `quote: RefundQuote | null | undefined` (undefined đang tải, null không tìm thấy), `blocked?: string`. Effect theo `[code, dir, router]`: `dir === null` thì `setQuote(null)` và không gọi API; ngược lại `GET /bookings/${encodeURIComponent(code)}/journeys/${dir}/refund-quote`; 401 → `router.replace` về login như C-09; 404 → `null`; lỗi khác → `blocked = e.detail`. Cờ `live` như C-09.
- Đầu trang: link `textLink` "Đặt chỗ {CODE}" tới `/bookings/{code}`; `h1` = `quote === null ? "Không tìm thấy đặt chỗ." : `Hoàn ${DIRECTION_LABEL[dir].toLowerCase()}.``; khi không tìm thấy thêm câu như C-09.
- `blocked` → `<Notice>{blocked}</Notice>`, không có gì khác. Đang tải → `FormSkeleton rows={2}`.
- Có `quote`: `Section title="Số tiền hoàn"` chứa `PriceTable rows={[["Giá trị chiều", journeyValue], ["Phí hoàn", -fee]]} total={refundAmount} totalLabel="Số tiền được hoàn"` và caption muted "Số tiền được chốt lúc gửi yêu cầu. Mã giảm giá đã dùng không được trả lại." Rồi form (không đặt `noValidate` để `Field.onInvalid` tự báo lỗi tiếng Việt như các form của Plan 04): `Field label="Lý do hoàn vé" name="reason" required maxLength={500} error={errors.reason}`, `formError` trong `Notice`, nút `pillDark` "Gửi yêu cầu hoàn" (`disabled={pending}`) và link `pillLine` "Quay lại" tới C-09.
- Gửi bằng `useSubmit`: ref `sent` chặn lần bấm thứ hai (đặt `true` trước khi gọi, trả về `false` khi lỗi); `POST .../refund-requests` body `{ reason: text(data, "reason") }`, thành công thì `router.push(`/bookings/${code}?refund=sent`)`.

- [ ] **Step 6: Chạy lại**

Run: `cd frontend && npx playwright test aftersales smoke booking.spec`
Expected: PASS: test hoàn, smoke (gồm route mới, sau đăng nhập có h1 "Không tìm thấy đặt chỗ."), và 3 test của `booking.spec.ts` vẫn xanh (C-09 đổi cấu trúc thẻ chiều).

- [ ] **Step 7: Commit**

```bash
git add frontend/src frontend/e2e
git commit -m "feat(frontend): hoàn vé một chiều (C-10), yêu cầu hoàn trên C-09"
```

---

### Task 3: Đổi chuyến (C-11) và phần đổi của C-09

**Files:**

- Create: `frontend/src/app/bookings/[code]/reschedule/[direction]/page.tsx`, `frontend/src/app/bookings/[code]/reschedule/[direction]/reschedule-view.tsx`
- Modify: `frontend/src/app/bookings/[code]/booking-view.tsx`, `frontend/e2e/aftersales.spec.ts`, `frontend/e2e/smoke.spec.ts`

**Interfaces:**

- Consumes: Task 1 (`journeyActions`, `directionParam`, `RESCHEDULE_STATUS`), Task 2 (`Section`, `at`, `PriceTable` có `totalLabel`, helper E2E `issuedBooking`, `paidReturn`, `day`); `hhmm`, `duration`, `first`, `last`, `fareRules`, `vnd` của `src/app/flights/itineraries.ts`; `clock`, `dmy`, `countdown`, `useNow` của `src/lib/booking.ts`.
- Produces: link C-09 → C-11 `/bookings/{code}/reschedule/{direction viết thường}`; C-09 hiểu `?reschedule=done`.

- [ ] **Step 1: Viết E2E (thêm vào `aftersales.spec.ts`) và route smoke**

`smoke.spec.ts`: thêm `"/bookings/ZZZZZZ/reschedule/outbound"` vào `PROTECTED`.

```ts
const vnpay = (page: Page) =>
  page.route("https://sandbox.vnpayment.vn/**", (route) =>
    route.fulfill({ contentType: "text/html", body: "<h1>VNPay sandbox</h1>" }),
  );

test("đổi chiều về, trả phí đổi qua VNPay (UF-03)", async ({ page }) => {
  test.slow();
  await vnpay(page);
  const { code } = await issuedBooking(page, "Economy Smart", [
    { from: "HAN", to: "SGN", date: day(3), flight: "QH201" },
    { from: "SGN", to: "HAN", date: day(5), flight: "QH202" },
  ]);
  await page
    .getByRole("article", { name: "Chiều về" })
    .getByRole("link", { name: "Đổi chiều này" })
    .click();

  // C-11: chiều hiện tại, ngày mặc định là ngày bay hiện tại; QH chỉ có QH202 mỗi ngày nên ngày đó không có lựa chọn.
  await expect(page).toHaveURL(`/bookings/${code}/reschedule/return`);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Đổi chiều về.",
  );
  await expect(
    page.getByRole("region", { name: "Chuyến hiện tại" }),
  ).toContainText(`QH202: 15:00 SGN → 17:10 HAN, ${dmy(day(5))}`);
  const date = field(page, "Ngày bay mới");
  await expect(date).toHaveValue(day(5));
  await expect(
    page.getByText("Không có chuyến thay thế trong ngày này."),
  ).toBeVisible();

  // Đổi ngày nhanh: danh sách của ngày cũ về muộn không đè ngày mới.
  await page.route(`**/reschedule-options?date=${day(5)}`, async (route) => {
    await new Promise((r) => setTimeout(r, 1500));
    await route.continue();
  });
  await date.fill(day(4));
  await date.fill(day(5));
  await date.fill(day(6));
  await page.waitForTimeout(2000);
  const option = page
    .getByRole("list", { name: "Chuyến thay thế" })
    .getByRole("article", { name: "Bamboo Airways QH202" });
  await expect(option).toContainText(dmy(day(6)));
  await expect(option).toContainText("Phải trả 300.000 đ");
  await expect(
    page.getByText("Không có chuyến thay thế trong ngày này."),
  ).toHaveCount(0);

  // Báo giá (BR-72): phí đổi 300.000 đ, cùng giá nên không có chênh lệch.
  await option.getByRole("button", { name: "Chọn" }).click();
  const quote = page.getByRole("region", { name: "Báo giá đổi chuyến" });
  await expect(quote).toContainText("Phí đổi chuyến");
  await expect(quote.getByRole("definition").last()).toHaveText("300.000 đ");
  let confirms = 0;
  page.on("request", (r) => {
    if (r.method() === "POST" && r.url().endsWith("/reschedules")) confirms++;
  });
  await quote.getByRole("button", { name: "Xác nhận đổi" }).dblclick();
  await expect(page).toHaveURL(
    /^https:\/\/sandbox\.vnpayment\.vn\/.*vnp_Amount=30000000/,
  );
  expect(confirms).toBe(1);

  // VNPay báo thất bại: yêu cầu đổi vẫn chờ, chiều về chưa đổi, thanh toán lại được (FR-62).
  await page.goto(`/bookings/${code}?payment=failed`);
  await expect(page.getByText("Thanh toán chưa thành công.")).toBeVisible();
  const changes = page
    .getByRole("region", { name: "Đổi chuyến" })
    .getByRole("listitem");
  await expect(changes).toHaveCount(1);
  await expect(changes.first()).toContainText(
    "Chiều về: QH202, Chờ thanh toán, phải trả 300.000 đ",
  );
  await expect(
    page.getByRole("article", { name: "Chiều về" }).getByRole("link"),
  ).toHaveCount(0);
  await page
    .getByRole("button", { name: "Thanh toán đổi chuyến 300.000 đ" })
    .click();
  await expect(page).toHaveURL(/^https:\/\/sandbox\.vnpayment\.vn\//);

  // Return URL thành công: chặng mới ACTIVE, yêu cầu Hoàn tất (BR-74).
  await page.goto(paidReturn(page.url()));
  await expect(page).toHaveURL(`/bookings/${code}?payment=success`);
  await expect(page.getByRole("article", { name: "Chiều về" })).toContainText(
    `QH202: 15:00 SGN → 17:10 HAN, ${dmy(day(6))}`,
  );
  await expect(changes.first()).toContainText(
    "Chiều về: QH202, Hoàn tất, phải trả 300.000 đ",
  );
});

test("đổi miễn phí hoàn tất ngay, ngày mặc định theo giờ sân bay", async ({
  page,
}) => {
  test.slow();
  const { code, email } = await issuedBooking(page, "Economy Flex", [
    { from: "HAN", to: "SGN", date: day(4), flight: "VN213" },
  ]);
  await page.goto(`/bookings/${code}/reschedule/outbound`);
  // VN213 cất cánh 06:00 giờ Hà Nội = 19:00 hôm trước ở New York: ô ngày vẫn là ngày Việt Nam.
  await expect(field(page, "Ngày bay mới")).toHaveValue(day(4));
  await field(page, "Ngày bay mới").fill(day(5));
  const option = page.getByRole("article", { name: "Vietnam Airlines VN213" });
  await expect(option).toContainText("Phải trả 0 đ");
  await option.getByRole("button", { name: "Chọn" }).click();
  await page
    .getByRole("region", { name: "Báo giá đổi chuyến" })
    .getByRole("button", { name: "Xác nhận đổi" })
    .click();

  await expect(page).toHaveURL(`/bookings/${code}?reschedule=done`);
  await expect(page.getByRole("status")).toContainText(
    `Đổi chuyến thành công. Vé mới đã được gửi tới ${email}.`,
  );
  await expect(page.getByRole("article", { name: "Chiều đi" })).toContainText(
    `VN213: 06:00 HAN → 08:10 SGN, ${dmy(day(5))}`,
  );
  await expect(page.getByRole("region", { name: "Đổi chuyến" })).toContainText(
    "Chiều đi: VN213, Hoàn tất, phải trả 0 đ",
  );
});
```

Thêm vào cuối test hoàn của Task 2 (chiều đi đang có yêu cầu hoàn mở), trước khi quay về C-09:

```ts
await page.goto(`/bookings/${code}/reschedule/outbound`);
await expect(
  page.getByText("Chiều này đang có yêu cầu hoàn hoặc đổi chuyến chưa xong"),
).toBeVisible();
await expect(page.getByRole("list", { name: "Chuyến thay thế" })).toHaveCount(
  0,
);
```

Import `dmy` từ `../src/lib/booking` và `type Page` từ `@playwright/test`.

- [ ] **Step 2: Chạy để thấy test fail**

Run: `cd frontend && npx playwright test aftersales smoke`
Expected: test hoàn của Task 2 và hai test mới FAIL (không có link "Đổi chiều này", route C-11 là 404), smoke fail ở route mới.

- [ ] **Step 3: C-09 — link đổi, khu "Đổi chuyến", thanh toán đổi chuyến, thông báo**

Trong `booking-view.tsx`:

- Thẻ chiều: thêm link `textLink` "Đổi chiều này" tới `/bookings/${booking.code}/reschedule/${j.direction.toLowerCase()}` khi `journeyActions(...).change`; hai link nằm chung một hàng `flex flex-wrap gap-16`.
- `BookingBody` nhận thêm `busy: boolean` và `onPayReschedule: (id: number) => void`. Khu `Section title="Đổi chuyến"` (cột phải, sau "Yêu cầu hoàn", chỉ khi `booking.reschedules.length > 0`), mỗi yêu cầu một `<li className="grid gap-4">`:
  - dòng đậm: `${DIRECTION_LABEL[r.direction]}: ${r.flightNumbers.join(" + ")}, ${RESCHEDULE_STATUS[r.status] ?? r.status}, phải trả ${vnd(r.amountDue)}`
  - caption muted: `Xác nhận lúc ${at(r.createdAt)} (giờ Việt Nam)`
  - `PENDING_PAYMENT`: `left = now === null ? null : Date.parse(r.holdExpiresAt) - now`; `left > 0` thì `Còn ${countdown(left)} để thanh toán` và nút `pillDark` `Thanh toán đổi chuyến ${vnd(r.amountDue)}` (`disabled={busy}`, gọi `onPayReschedule(r.id)`); `left !== null && left <= 0` thì "Đã hết hạn thanh toán đổi chuyến."
- `BookingView.payReschedule(id)`: như `pay()` nhưng gọi `POST /reschedules/${id}/payments`; gộp hai hàm thành `startPayment(path: string)` dùng chung, `pay` gọi với `/bookings/${code}/payments`.
- Thông báo: `?reschedule=done` → `<Notice tone="ok">{`Đổi chuyến thành công. Vé mới đã được gửi tới ${booking.contact.email}.`}</Notice>`. Câu `?payment=failed` thêm " Bạn có thể thanh toán lại phí đổi chuyến trước khi hết hạn." khi có yêu cầu đổi `PENDING_PAYMENT` chưa hết hạn.

- [ ] **Step 4: C-11 `reschedule/[direction]/page.tsx` và `reschedule-view.tsx`**

`page.tsx` như C-10, `metadata.title = "Đổi chuyến | SkyLine"`.

`RescheduleView` (`"use client"`), state và luồng:

- `dir = directionParam(direction)`. Tải `GET /bookings/{code}` như C-09 (401 → login, 404 → null); `journey = booking?.journeys.find(j => j.direction === dir)`; `dir` null hoặc không có `journey` thì coi như không tìm thấy. `h1`: "Không tìm thấy đặt chỗ." hoặc `Đổi ${DIRECTION_LABEL[dir].toLowerCase()}.`; link "Đặt chỗ {CODE}" về C-09 như C-10.
- `Section title="Chuyến hiện tại"`: mỗi chặng `${flightNumber}: ${clock(dep)} ${depAirport} → ${clock(arr)} ${arrAirport}, ${dmy(dep)}` (cùng dạng với C-09) và caption `${fareFamilyName}. ${fareRules(journey).join(", ")}.`
- `date`: `useState<string>()`; giá trị dùng = `date ?? journey.segments[0].departureTime.slice(0, 10)`. `Field label="Ngày bay mới" name="date" type="date" value={...} onChange={e => { setDate(e.target.value); setChosen(undefined); }}`.
- Danh sách: effect theo `[code, dir, selectedDate, version]` (chỉ chạy khi đã có `journey` và `selectedDate` khác rỗng) gọi `GET .../reschedule-options?date=${selectedDate}`, lưu `{ date: selectedDate, options }` hoặc `{ date: selectedDate, error: e.detail }` (401 → login); cờ `live` bỏ phản hồi của lần chạy cũ. Hiện: chưa có kết quả cho `selectedDate` → `FormSkeleton rows={2}`; `error` → `<Notice>`; rỗng → "Không có chuyến thay thế trong ngày này."; có → `<ul aria-label="Chuyến thay thế" className="grid gap-16">`.
- Thẻ lựa chọn (component trong file): `<article aria-label={`${first(it).airline.name} ${numbers}`}>` với `numbers` nối `flightNumber` bằng `" + "` (cùng quy ước `ItineraryCard`); nội dung: `${hhmm(dep)} ${dep.airport} → ${hhmm(arr)} ${arr.airport}, ${dmy(dep.time)}`, `duration(totalDurationMinutes)`, "Bay thẳng" hoặc "1 điểm dừng, {sân bay nối}", `Phải trả ${vnd(quote.amountDue)}`; nút `pillDark`"Chọn" (hoặc`pillLine` "Đã chọn" khi đang chọn).
- Bố cục `lg:grid-cols-[minmax(0,1fr)_400px]` như C-09: cột trái ô ngày và danh sách, cột phải `Section title="Báo giá đổi chuyến"`. Chưa chọn: caption "Chọn một chuyến để xem báo giá." Đã chọn: `POST .../reschedule-quote` body `{ flightIds }` (từ `itinerary.segments`), lưu kết quả; hiện `PriceTable rows={[["Phí đổi chuyến", changeFeeTotal], ["Chênh lệch giá vé", Math.max(0, newFareTotal - oldFareTotal)]]} total={amountDue} totalLabel="Phải trả"`, caption `Giá vé hiện tại ${vnd(old)}, giá vé mới ${vnd(new)}. Chuyến mới rẻ hơn thì phần chênh lệch không được hoàn.` (BR-72), caption `amountDue > 0` ? "Bạn sẽ được chuyển sang VNPay để thanh toán." : "Đổi xong ngay, không cần thanh toán.", và nút `pillDark` "Xác nhận đổi".
- Xác nhận: ref chặn lần bấm thứ hai; `POST .../reschedules` body `{ flightIds }`. `COMPLETED` → `router.push(`/bookings/${code}?reschedule=done`)`. `PENDING_PAYMENT` → `POST /reschedules/${id}/payments`, `window.location.assign(paymentUrl)`(giữ nút bị khoá); lỗi ở bước này →`router.push(`/bookings/${code}`)`.
- Lỗi báo giá hoặc xác nhận (ngoài 401): `actionError = e.detail` trong `<Notice>` phía trên bố cục, `setChosen(undefined)`, `setVersion(v => v + 1)` để tải lại danh sách, nhả ref.

- [ ] **Step 5: Chạy lại**

Run: `cd frontend && npx playwright test aftersales smoke booking.spec`
Expected: PASS: 3 test của `aftersales.spec.ts`, smoke, `booking.spec.ts`.

- [ ] **Step 6: Commit**

```bash
git add frontend/src frontend/e2e
git commit -m "feat(frontend): đổi chuyến một chiều (C-11), thanh toán đổi chuyến trên C-09"
```

---

### Task 4: Kiểm tra toàn bộ và cập nhật tài liệu

**Files:**

- Modify: `docs/superpowers/plans/2026-10-09-00-roadmap.md`, `CLAUDE.md`

- [ ] **Step 1: Chạy toàn bộ kiểm tra frontend**

```bash
cd frontend && npm run lint && npm run build && npx playwright test
```

Expected: lint, build không lỗi; Playwright không có test nào fail (thêm so với trước plan: 4 unit, 3 E2E hậu mãi, 2 smoke). Chạy `npx playwright test aftersales` thêm 2 lần, cả 3 lần đều xanh; chập chờn vì `npm run dev` dịch route chậm thì chạy lại trên bản build (`E2E_BASE_URL`, xem `playwright.config.ts`) để phân biệt.

- [ ] **Step 2: Thử tay trên giao diện**

Đăng nhập `customer@demo.local`, mở một booking đã xuất vé (tạo bằng Step 2 của Plan 13 hoặc chạy E2E rồi dùng tài khoản đó). Kiểm tra bằng mắt ở chế độ sáng, tối và bề rộng 375 px: C-09 hai link trong thẻ chiều, khu "Yêu cầu hoàn", "Đổi chuyến"; C-10 bảng số tiền và ô lý do; C-11 ô ngày, danh sách, cột báo giá (dưới danh sách ở màn hẹp). Mailpit (http://localhost:8025) có email đổi chuyến sau khi đổi xong.

- [ ] **Step 3: Cập nhật lộ trình `docs/superpowers/plans/2026-10-09-00-roadmap.md`**

(a) Dòng "Plan chi tiết đã có": thêm vào cuối (trước ` |`) ` · [Plan 17 — Màn hình hậu mãi](2026-10-20-17-aftersales-screens.md)`.

(b) §2, dòng Plan 17: `☐` → `☑`.

(c) §6, thêm dưới dòng `| 16 | ...`:

```markdown
| 17 | Màn `/bookings/[code]/refund/[direction]` (C-10) và `/bookings/[code]/reschedule/[direction]` (C-11), `direction` viết thường (`outbound`, `return`). `src/lib/booking.ts` thêm `Direction`, `REFUND_STATUS`, `REFUND_SOURCE`, `RESCHEDULE_STATUS`, `directionParam(param)`, `journeyActions(booking, journey, now) → {refund, change}` (BR-51 trừ hạn chót giờ, BR-60, BR-70). C-09: mỗi chiều là `article` theo nhãn chiều, link "Hoàn chiều này", "Đổi chiều này", khu "Yêu cầu hoàn", "Đổi chuyến" (thanh toán đổi chuyến, đếm ngược), thông báo `?refund=sent`, `?reschedule=done`; `booking-view.tsx` export `Section`, `at`. `PriceTable` thêm `totalLabel`. E2E `e2e/aftersales.spec.ts`: `issuedBooking(page, fare, legs)` tạo booking đã xuất vé bằng API, `paidReturn(paymentUrl)` ký Return URL VNPay bằng `VNPAY_HASH_SECRET` của `../.env` |
```

(d) §7, thêm vào cuối danh sách:

```markdown
18. **Plan 17 có 1 điểm cố ý khác APP_FLOW** (chi tiết ở [Plan 17](2026-10-20-17-aftersales-screens.md#điểm-cố-ý-khác-app_flow)): C-09 không kiểm tra hạn chót `aftersales.min_hours_before_departure` khi hiện "Hoàn chiều này", "Đổi chiều này" (tham số không có trong `BookingDetail`), chỉ ẩn khi chặng đầu đã cất cánh; C-10, C-11 hiện `DEADLINE_PASSED` của backend. E2E hậu mãi cần `VNPAY_HASH_SECRET` trong `.env`.
```

- [ ] **Step 4: Cập nhật `CLAUDE.md`**

Mục Status, thay:

```
C-08 `/bookings` and C-09 `/bookings/[code]` (hold countdown, VNPay payment, cancel) are done (Plan 13), and the other screens are placeholders.
```

bằng:

```
C-08 `/bookings` and C-09 `/bookings/[code]` (hold countdown, VNPay payment, cancel) are done (Plan 13), the after-sales screens C-10 `/bookings/[code]/refund/[direction]` and C-11 `/bookings/[code]/reschedule/[direction]` plus C-09's refund and reschedule actions, request lists and reschedule payment are done (Plan 17; `e2e/aftersales.spec.ts` signs the VNPay Return URL with `VNPAY_HASH_SECRET` from `.env`), and the other screens are placeholders.
```

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-10-09-00-roadmap.md CLAUDE.md
git commit -m "docs: hoàn tất Plan 17, cập nhật hợp đồng màn hình hậu mãi"
```
