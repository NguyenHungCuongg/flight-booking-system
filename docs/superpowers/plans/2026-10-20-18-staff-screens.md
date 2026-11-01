# Plan 18 — Màn hình Staff: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Staff (và Admin) làm việc trên giao diện: S-01 `/staff/bookings` tra cứu đặt chỗ, S-02 `/staff/bookings/[code]` xem chi tiết kèm tài khoản và gửi lại email vé, S-03 `/staff/refunds` hàng đợi yêu cầu hoàn, S-04 `/staff/refunds/[id]` duyệt, từ chối, thử lại, xác nhận hoàn thủ công, S-05 `/staff/flights` danh sách chuyến, S-06 `/staff/flights/[id]/passengers` manifest.

**Architecture:** Không đổi backend, không sinh lại kiểu API: mọi API đã có từ Plan 06, 11, 12, 14, 16. Mỗi màn là Client Component trong `<Suspense>` (đọc `useParams`/`useSearchParams`) như C-08, C-09. Bộ lọc nằm trên URL đúng route của APP_FLOW, form lọc dùng `<Form>` của `next/form` (GET, điều hướng phía client); hàm thuần `listQuery` ở `src/lib/staff.ts` đổi URL thành query của API (bỏ ô rỗng, trang đếm từ 1 sang 0). Một hook `useApi` dùng chung cho 6 màn (tải theo đường dẫn, bỏ phản hồi cũ, 401 về đăng nhập). S-02 dùng lại phần thân của C-09 (`BookingBody`) ở chế độ chỉ xem. Header chung `SiteHeader` thêm khu `staff`.

**Tech Stack:** Next.js 16.4 (App Router, Cache Components, `next/form`), React 19.3, TypeScript, Tailwind v4, Playwright 1.64 (E2E; unit test không mở trình duyệt). Không thêm thư viện.

**Spec:** [App Flow](../../APP_FLOW.md) §1 (trang mặc định của Staff), §2.2 S-01–S-06 và bảng "S-04 thao tác", UF-02, UF-07, §4.4, §4.5 · [PRD](../../PRD.md) FR-70–FR-73, BR-62–BR-66 · [TDD](../../TDD.md) §4.3 (quyền theo đường dẫn), §6.6 (duyệt hoàn hai bước), §7 (giờ), §9 (mã lỗi) · [DESIGN.md](../../../DESIGN.md) · [Lộ trình](2026-10-09-00-roadmap.md) §6 (hợp đồng Plan 06–17) · Plan frontend trước: [Plan 13](2026-10-15-13-booking-screens.md), [Plan 17](2026-10-20-17-aftersales-screens.md) (C-09, `booking.ts`, cách viết E2E)

## Global Constraints

- Mọi Global Constraints của [Plan 17](2026-10-20-17-aftersales-screens.md#global-constraints) vẫn giữ: token và pill (`pillDark`, `pillLine`, `textLink` của `src/components/form.tsx`), `--spacing: 1px`, không viết `dark:`, chữ tiếng Việt không dùng gạch dài; **không gọi `new Date()`, `Date.now()`, `Math.random()` khi render** (giờ hiện tại lấy bằng `useNow()`); component dùng `useParams`/`useSearchParams` nằm trong `<Suspense>`; tiền hiển thị bằng `vnd()`; giờ bay đọc thẳng từ chuỗi có offset (`clock`, `dmy`), mốc của booking và yêu cầu hoàn ghi kèm "(giờ Việt Nam)"; API trả 401 thì `router.replace("/login?next=" + đường dẫn hiện tại)`.
- **Không thêm thư viện npm. Không sửa backend, không chạy `npm run gen:api`.** Kiểu lấy từ `Schemas[...]`: `PagedModelBookingSummary`, `BookingSummary`, `StaffBookingDetail`, `BookingDetail`, `PagedModelRefundSummary`, `RefundSummary`, `RefundDetail`, `RefundAttempt`, `PagedModelFlightView`, `FlightView`, `CabinView`, `Manifest`, `ManifestPassenger`, `AirlineResponse`.
- **Trường có thể `null` dù kiểu sinh ra ghi `string`:** `RefundSummary.direction`, `RefundDetail.direction` (nguồn `INVALID_PAYMENT`), `RefundDetail.reason`, `staffNote`, `lastError`, `processedBy`, `processedAt`, `completedAt`; `RefundAttempt.responseCode`; `BookingDetail.issuedAt`.
- **Quyền:** `/api/staff/**` cần `STAFF` (Admin kế thừa). Người khác vai trò nhận 403 `FORBIDDEN` "Bạn không có quyền thực hiện thao tác này"; trang hiện câu đó trong `Notice`, giữ `h1`. `proxy.ts` đã chặn `/staff/:path*` khi thiếu cookie, không cần sửa.
- **Route mới** vào danh sách `PROTECTED` của `e2e/smoke.spec.ts`.

### Điểm cố ý khác APP_FLOW

Không có.

### Điểm plan tự chọn (APP_FLOW và PRD không nói)

- **Header:** `SiteHeader` nhận `area?: "customer" | "staff"` (mặc định `"customer"`). Khu `staff` có `nav aria-label="Nhân viên"` với "Đặt chỗ" (`/staff/bookings`), "Yêu cầu hoàn" (`/staff/refunds`), "Chuyến bay" (`/staff/flights`). Plan 20 thêm khu `admin` theo cùng cách.
- **Bộ lọc trên URL:** form lọc là `<Form action="/staff/...">` của `next/form`, nên ô để trống vẫn lên URL dạng `from=`. API so khớp nguyên văn (`f.departure_airport = :from`), nên **mọi ô rỗng phải bị bỏ** trước khi gọi API (`listQuery`). Form có `key={params.toString()}` để `defaultValue` theo URL khi bấm link phân trang hay link header. Tham số `page` trên URL đếm từ 1 như C-08.
- **Bảng:** S-01, S-03, S-05, S-06 hiện dạng `<table aria-label=...>` (APP_FLOW ghi "bảng kết quả"), bọc trong `div.overflow-x-auto` để màn 375 px cuộn ngang trong bảng, không cuộn cả trang. Mỗi trang 20 dòng (mặc định của backend), manifest không phân trang.
- **S-02 = phần thân C-09 ở chế độ chỉ xem:** không có "Hoàn chiều này", "Đổi chiều này", "Thanh toán đổi chuyến" (các API đó chỉ cho `CUSTOMER`); mỗi yêu cầu hoàn có link "Xử lý yêu cầu" sang S-04. Thêm khu "Tài khoản đặt chỗ" và nút "Gửi lại email vé" khi `ISSUED`.
- **S-04 tóm tắt booking** bằng các trường của `RefundDetail` (mã, trạng thái, liên hệ) kèm link sang S-02; không tải thêm booking.
- **"Duyệt" và "Thử lại" hỏi `confirm()`** vì cả hai gọi Refund API chuyển tiền thật. "Từ chối" và "Xác nhận hoàn thủ công" là form có ô bắt buộc (`Lý do từ chối`, `Ghi chú hoàn thủ công`, tối đa 500 ký tự như `ReasonBody`, `NoteBody`), không hỏi thêm. "Duyệt" không gửi ghi chú.
- **Sau mỗi thao tác ở S-04** (thành công hay lỗi nghiệp vụ) đều tải lại chi tiết, vì lỗi `PAYMENT_GATEWAY_ERROR` chỉ ném sau khi yêu cầu đã sang `APPROVED` và `INVALID_STATE` nghĩa là trang đang cũ. Lỗi theo trường (`errors.reason`, `errors.note`) hiện dưới ô, không tải lại. Một `useRef` chặn lần gọi thứ hai khi đang gửi (như Plan 17).
- **S-05 lọc sân bay** bằng ô chọn từ `useAirports()` (chỉ sân bay đang hoạt động) và hãng từ `GET /airlines`; giá trị gửi lên là mã viết hoa nên khớp nguyên văn.

## Review Focus

Những tình huống tài liệu không nói rõ nhưng dễ gây lỗi thật, xếp theo khả năng gặp. Mỗi dòng có test ghim lại.

1. **Ô lọc để trống** (`/staff/flights?from=&to=SGN&date=`): gửi nguyên lên API thì `from = ''` không khớp chuyến nào và bảng trống. Query của API phải bỏ ô rỗng (Task 1: `listQuery` bỏ `from=`, `date=`; Task 3 E2E lọc bằng form có ô trống).
2. **Customer hoặc phiên khác vai trò mở `/staff/...`**: API trả 403; trang phải hiện câu của backend, không treo khung xương, không lỗi JavaScript (Task 2 E2E: customer mở `/staff/refunds` thấy "Bạn không có quyền thực hiện thao tác này"; smoke đăng nhập customer mở mọi route Staff vẫn có `h1`).
3. **VNPay không hoàn được khi duyệt**: yêu cầu vẫn `APPROVED`, hiện lỗi gần nhất, chỉ còn "Thử lại" và "Xác nhận hoàn thủ công" (Task 4 E2E: lượt thu là giả nên Refund API của sandbox luôn lỗi; sau "Duyệt" thấy "Hoàn tiền qua VNPay chưa xong", "Đã duyệt, đang hoàn tiền", "Lỗi gần nhất:", một lượt hoàn "VNPay, thất bại").
4. **Thao tác trên dữ liệu cũ** (Staff khác vừa xử lý cùng yêu cầu): backend trả 409 `INVALID_STATE`; trang hiện câu đó và tải lại để nút khớp trạng thái mới (Task 4 E2E: `page.route` trả 409 cho lần "Duyệt" đầu, thấy câu báo và có một lần `GET` chi tiết sau đó).
5. **Bấm đúp "Xác nhận hoàn thủ công"**: chỉ một request; lần thứ hai không được hiện lỗi `INVALID_STATE` thoáng qua (Task 4 E2E: `dblclick` rồi đếm đúng 1 `POST .../mark-manual`).

Đã kiểm thêm: yêu cầu do hệ thống tạo không có nút "Từ chối" (Task 1 `refundActions`); link phân trang giữ bộ lọc và bỏ ô rỗng (Task 1 `pageHref`); mã đặt chỗ gõ chữ thường vẫn tìm ra (Task 2 E2E); giờ chuyến ở S-05 theo giờ sân bay khi trình duyệt ở New York (Task 3 E2E).

---

## Trước khi bắt đầu

- **Cần có:** Plan 04, 16 và 17 đã xong (S-02 dùng lại `booking-view.tsx` của Plan 17, E2E dùng lại helper của `aftersales.spec.ts`); Node 24; Docker Desktop đang chạy.
- **Nhánh:** tạo `feature/staff-screens` từ `develop` sau khi đã merge `feature/aftersales-screens` (chưa merge thì tạo từ `feature/aftersales-screens`).
- **Chạy lệnh ở đâu:** Git Bash, từ thư mục gốc repo, trừ khi bước đó ghi `cd frontend`.
- **Backend cho E2E:** như Plan 13, 17, chạy suốt plan ở một terminal riêng:

```bash
docker compose up -d postgres mailpit
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Seed phải còn mới (`V106`). `.env` phải có `VNPAY_HASH_SECRET` (E2E ký Return URL như Plan 17). Tài khoản `staff@demo.local` / `Demo@1234` (seed `V105`).

- **Refund API trong E2E:** "Duyệt" gọi thật `VNPAY_API_URL` (mặc định sandbox). Lượt thu của E2E là giả (Return URL tự ký, VNPay chưa từng thấy giao dịch), nên sandbox trả mã lỗi; không có mạng thì lỗi kết nối. Cả hai đều để yêu cầu ở `APPROVED` kèm `last_error`, mất tối đa ~35 giây (`VnpayApi`: kết nối 5 giây, đọc 30 giây).
- **Dữ liệu seed E2E dựa vào** (như Plan 17): QH201 HAN→SGN 10:00–12:10, QH202 SGN→HAN 15:00–17:10, gói QH "Economy Smart" (phí hoàn 500.000 đ), giá chiều đi **1.730.000 đ**.
- **Chạy E2E:** `cd frontend && npx playwright test <tên file>`.
- **Trạng thái kiểm chứng:** plan viết theo code thật của Plan 06–17 (đọc ngày 2026-10-20) nhưng **chưa chạy thử**. Ở mỗi bước "Run", kết quả khác "Expected" thì tìm nguyên nhân, sửa code và sửa luôn plan cho khớp rồi mới đi tiếp.

## Cấu trúc file sau plan

```
frontend/
  e2e/
    helpers.ts                     # Task 2: day, field, csrfHeaders, paidReturn, issuedBooking (chuyển từ aftersales.spec.ts)
    aftersales.spec.ts             # Task 2: import helper từ helpers.ts
    staff-lib.spec.ts              # Task 1: unit test src/lib/staff.ts
    staff.spec.ts                  # Task 2 + 3: tra cứu, S-05, S-06; Task 4: xử lý yêu cầu hoàn
    smoke.spec.ts                  # Task 2, 3, 4: + route Staff vào PROTECTED
  src/
    lib/
      staff.ts                     # Task 1: listQuery, pageOf, pageHref, refundActions, FLIGHT_STATUS, REFUND_PROVIDER, cabinLabel
      use-api.ts                   # Task 2: useApi
      booking.ts                   # Task 2: + at, PAYMENT_STATUS (chuyển từ booking-view.tsx)
    components/
      site-header.tsx              # Task 2: + area "staff"
    app/
      bookings/[code]/booking-view.tsx   # Task 2: export BookingBody, chế độ staff
      staff/
        staff-ui.tsx               # Task 2: StaffPage, Pager, th, td, tableBox
        bookings/page.tsx, booking-search.tsx                 # Task 2: S-01
        bookings/[code]/page.tsx, staff-booking-view.tsx      # Task 2: S-02
        flights/page.tsx, flight-list.tsx                     # Task 3: S-05
        flights/[id]/passengers/page.tsx, manifest-view.tsx   # Task 3: S-06
        refunds/page.tsx, refund-queue.tsx                    # Task 4: S-03
        refunds/[id]/page.tsx, refund-detail.tsx              # Task 4: S-04
```

## API dùng trong plan (đã có, không đổi)

| Method | Đường dẫn (bỏ `/api`)                                   | Dùng ở                                                                                                                                                                             |
| ------ | ------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GET    | `/staff/bookings?q=&status=&page=`                      | S-01: `PagedModelBookingSummary`, mới nhất trước; `q` khớp mã (không phân biệt hoa thường), email (không phân biệt hoa thường) hoặc số điện thoại; không có `q` thì liệt kê tất cả |
| GET    | `/staff/bookings/{code}`                                | S-02: `StaffBookingDetail{account{id, email, fullName}, booking}`; không có thì 404                                                                                                |
| POST   | `/staff/bookings/{code}/resend-ticket-email`            | S-02: 204; chưa `ISSUED` thì 409 `INVALID_STATE`                                                                                                                                   |
| GET    | `/staff/refund-requests?status=&source=&page=`          | S-03: `PagedModel<RefundSummary>`, cũ nhất trước                                                                                                                                   |
| GET    | `/staff/refund-requests/{id}`                           | S-04: `RefundDetail` (gồm `attempts`, `refundedAmount`, `processedBy` là email Staff)                                                                                              |
| POST   | `/staff/refund-requests/{id}/approve`                   | S-04: không body; 200 `RefundDetail`; Refund API lỗi thì 502 `PAYMENT_GATEWAY_ERROR` "Hoàn tiền qua VNPay chưa xong: ..." (yêu cầu đã `APPROVED`)                                  |
| POST   | `/staff/refund-requests/{id}/reject`                    | S-04: body `{reason}` (bắt buộc, ≤ 500); nguồn khác `CUSTOMER` hoặc không `PENDING` thì 409 `INVALID_STATE`                                                                        |
| POST   | `/staff/refund-requests/{id}/retry`                     | S-04: không body; lỗi như `approve`                                                                                                                                                |
| POST   | `/staff/refund-requests/{id}/mark-manual`               | S-04: body `{note}`; trống thì 400 `errors[{field: "note"}]`                                                                                                                       |
| GET    | `/staff/flights?from=&to=&date=&airline=&status=&page=` | S-05: `PagedModelFlightView`, theo giờ cất cánh; `date` là ngày cất cánh theo giờ sân bay đi                                                                                       |
| GET    | `/staff/flights/{id}/passengers`                        | S-06: `Manifest{flight, passengers}`, xếp theo họ tên; không có chuyến thì 404                                                                                                     |
| GET    | `/airlines`, `/airports`                                | S-05: ô chọn hãng, sân bay                                                                                                                                                         |

## Mã lỗi và cách hiện

| Mã                                       | Ở đâu                      | Hiện thế nào                                                                                   |
| ---------------------------------------- | -------------------------- | ---------------------------------------------------------------------------------------------- |
| 401                                      | mọi màn                    | `router.replace("/login?next=...")` (trong `useApi` và trong thao tác)                         |
| 403 `FORBIDDEN`                          | mọi màn                    | `Notice` câu của backend, giữ `h1`                                                             |
| 404 `RESOURCE_NOT_FOUND`                 | S-02, S-04, S-06           | `h1` "Không tìm thấy đặt chỗ." / "Không tìm thấy yêu cầu hoàn." / "Không tìm thấy chuyến bay." |
| `INVALID_STATE`, `PAYMENT_GATEWAY_ERROR` | S-02 (gửi lại email), S-04 | `Notice` câu của backend; S-04 tải lại chi tiết                                                |
| `VALIDATION_FAILED` có `errors`          | S-04                       | Dưới ô `reason`/`note` (`useSubmit`)                                                           |
| lỗi khác khi tải                         | mọi màn                    | `Notice` câu của backend                                                                       |

---

### Task 1: Hàm thuần cho danh sách và thao tác của Staff

**Files:**

- Create: `frontend/src/lib/staff.ts`
- Test: `frontend/e2e/staff-lib.spec.ts`

**Interfaces:**

- Consumes: `CABINS` của `src/components/landing/data.ts`; `Schemas` của `@/lib/api`.
- Produces (Task 2–4 dùng):
  - `pageOf(params: URLSearchParams): number` — `page` trên URL, số nguyên ≥ 1, sai hoặc thiếu thì 1 (cùng công thức C-08).
  - `listQuery(params: URLSearchParams, keys: readonly string[]): string` — query của API: các khoá trong `keys` có giá trị khác rỗng (sau `trim`), theo thứ tự `keys`, rồi `page = pageOf - 1`.
  - `pageHref(path: string, params: URLSearchParams, page: number): string` — link tới trang `page`, giữ các tham số khác rỗng trừ `page`; `page = 1` thì không ghi `page`; không còn tham số thì chỉ `path`.
  - `refundActions(status: string, source: string): { approve: boolean; reject: boolean; settle: boolean }` — bảng "S-04 thao tác": `approve` khi `PENDING`; `reject` khi `PENDING` và nguồn `CUSTOMER` (BR-66); `settle` ("Thử lại" và "Xác nhận hoàn thủ công") khi `APPROVED`.
  - `FLIGHT_STATUS: Record<Schemas["FlightView"]["status"], string>` = `SCHEDULED` "Theo lịch", `CANCELLED` "Đã huỷ".
  - `REFUND_PROVIDER: Record<string, string>` = `VNPAY` "VNPay", `MANUAL` "Thủ công".
  - `cabinLabel(id: string): string` — nhãn tiếng Việt của hạng ghế trong `CABINS`, không có thì trả `id`.

- [ ] **Step 1: Viết unit test `e2e/staff-lib.spec.ts`**

```ts
import { expect, test } from "@playwright/test";
import {
  cabinLabel,
  listQuery,
  pageHref,
  pageOf,
  refundActions,
} from "../src/lib/staff";

const url = (q: string) => new URLSearchParams(q);

test("listQuery: bỏ ô rỗng, giữ thứ tự khoá, trang URL đếm từ 1 sang API đếm từ 0", () => {
  expect(
    listQuery(url("from=&to=SGN&date=&airline=QH&page=3&x=1"), [
      "from",
      "to",
      "date",
      "airline",
      "status",
    ]),
  ).toBe("to=SGN&airline=QH&page=2");
  expect(listQuery(url("q=%20%20&status="), ["q", "status"])).toBe("page=0");
  expect(listQuery(url("q=abc123&page=0"), ["q"])).toBe("q=abc123&page=0");
  expect(listQuery(url("page=abc"), ["q"])).toBe("page=0");
});

test("pageOf: thiếu, sai hoặc nhỏ hơn 1 thì là trang 1", () => {
  expect(pageOf(url(""))).toBe(1);
  expect(pageOf(url("page=-2"))).toBe(1);
  expect(pageOf(url("page=2.7"))).toBe(2);
});

test("pageHref: giữ bộ lọc khác rỗng, trang 1 không ghi page", () => {
  const p = url("status=PENDING&source=&page=2");
  expect(pageHref("/staff/refunds", p, 3)).toBe(
    "/staff/refunds?status=PENDING&page=3",
  );
  expect(pageHref("/staff/refunds", p, 1)).toBe(
    "/staff/refunds?status=PENDING",
  );
  expect(pageHref("/staff/flights", url("from=&page=4"), 1)).toBe(
    "/staff/flights",
  );
});

test("refundActions: theo bảng S-04 thao tác (BR-63, BR-64, BR-66)", () => {
  expect(refundActions("PENDING", "CUSTOMER")).toEqual({
    approve: true,
    reject: true,
    settle: false,
  });
  expect(refundActions("PENDING", "FLIGHT_CANCELLED")).toEqual({
    approve: true,
    reject: false,
    settle: false,
  });
  expect(refundActions("PENDING", "INVALID_PAYMENT").reject).toBe(false);
  expect(refundActions("APPROVED", "CUSTOMER")).toEqual({
    approve: false,
    reject: false,
    settle: true,
  });
  const none = { approve: false, reject: false, settle: false };
  expect(refundActions("REJECTED", "CUSTOMER")).toEqual(none);
  expect(refundActions("COMPLETED", "INVALID_PAYMENT")).toEqual(none);
});

test("cabinLabel: nhãn tiếng Việt, mã lạ giữ nguyên", () => {
  expect(cabinLabel("ECONOMY")).toBe("Phổ thông");
  expect(cabinLabel("XYZ")).toBe("XYZ");
});
```

- [ ] **Step 2: Chạy để thấy test fail**

Run: `cd frontend && npx playwright test staff-lib`
Expected: FAIL, không tìm thấy module `../src/lib/staff`.

- [ ] **Step 3: Cài đặt `src/lib/staff.ts`** theo Interfaces. File không có `"use client"` (chỉ hàm thuần và hằng). JSDoc tiếng Việt cho `listQuery` ghi lý do bỏ ô rỗng: `next/form` gửi ô trống thành `from=`, còn API so khớp nguyên văn.

- [ ] **Step 4: Chạy lại**

Run: `cd frontend && npx playwright test staff-lib`
Expected: PASS, 5 test.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/staff.ts frontend/e2e/staff-lib.spec.ts
git commit -m "feat(frontend): hàm lọc, phân trang và thao tác cho màn Staff"
```

---

### Task 2: Khung Staff, tra cứu booking (S-01) và chi tiết booking (S-02)

**Files:**

- Create: `frontend/src/lib/use-api.ts`, `frontend/src/app/staff/staff-ui.tsx`, `frontend/src/app/staff/bookings/page.tsx`, `frontend/src/app/staff/bookings/booking-search.tsx`, `frontend/src/app/staff/bookings/[code]/page.tsx`, `frontend/src/app/staff/bookings/[code]/staff-booking-view.tsx`, `frontend/e2e/helpers.ts`, `frontend/e2e/staff.spec.ts`
- Modify: `frontend/src/components/site-header.tsx`, `frontend/src/lib/booking.ts`, `frontend/src/app/bookings/[code]/booking-view.tsx`, `frontend/e2e/aftersales.spec.ts`, `frontend/e2e/smoke.spec.ts`

**Interfaces:**

- Consumes: Task 1 (`listQuery`, `pageOf`, `pageHref`); `Field`, `SelectField`, `Notice`, `FormSkeleton`, `pillDark`, `pillLine`, `textLink` của `form.tsx`; `STATUS_LABEL`, `DIRECTION_LABEL`, `clock`, `dmy`, `useNow` của `booking.ts`; `vnd` của `src/app/flights/itineraries.ts`.
- Produces (Task 3, 4 dùng):
  - `useApi<T>(path: string, version?: number): { data: T } | { error: ApiError } | undefined` ở `src/lib/use-api.ts` — `undefined` khi chưa có kết quả cho `path` hiện tại; đổi `version` thì tải lại nhưng vẫn trả kết quả cũ của cùng `path` trong lúc chờ; 401 thì `router.replace` về `/login?next=` (đường dẫn và query hiện tại); phản hồi của `path` cũ về muộn bị bỏ (cờ `live`).
  - `src/app/staff/staff-ui.tsx` (`"use client"`): `StaffPage({ children })` (khung `min-h-dvh bg-paper`, `<SiteHeader area="staff" />`, `main` cùng class với C-09); `Pager({ path, params, totalPages })` (nav "Phân trang" như C-08, link bằng `pageHref`, chỉ hiện khi `totalPages > 1`); class `tableBox = "overflow-x-auto"`, `th`, `td` (chữ caption, căn trái, viền dưới `border-line`, `py-11 pr-16`, `td` thêm `align-top`).
  - `SiteHeader({ area = "customer" }: { area?: "customer" | "staff" })`.
  - `src/lib/booking.ts` thêm `at(iso)` ("08:00 16/10/2026") và `PAYMENT_STATUS` (`PENDING` "đang xử lý", `SUCCESS` "thành công", `FAILED` "thất bại"), chuyển từ `booking-view.tsx`.
  - `booking-view.tsx` export `BookingBody({ booking, now, staff?, busy?, onPayReschedule? })`.
  - `e2e/helpers.ts`: `day(n)`, `field(scope, label)`, `csrfHeaders(page) → Promise<{"X-XSRF-TOKEN": string}>`, `paidReturn(paymentUrl)`, `issuedBooking(page, fare, legs) → Promise<{code, email}>`. `e2e/staff.spec.ts`: `loginStaff(page)`.

- [ ] **Step 1: Chuyển helper E2E sang `e2e/helpers.ts`**

Chuyển nguyên văn `day`, `field`, `paidReturn`, `issuedBooking` từ `aftersales.spec.ts` sang `e2e/helpers.ts` và export (file không có đuôi `.spec.ts` nên Playwright không chạy nó như test). Tách đoạn lấy CSRF trong `issuedBooking` thành `export async function csrfHeaders(page: Page)` (gọi `GET /api/auth/csrf`, đọc cookie `XSRF-TOKEN`, trả `{ "X-XSRF-TOKEN": token }`) và cho `issuedBooking` gọi nó. `aftersales.spec.ts` import từ `./helpers`, giữ `vnpay` và các test như cũ.

Run: `cd frontend && npx playwright test aftersales`
Expected: PASS, 3 test như trước khi chuyển.

- [ ] **Step 2: Viết E2E `e2e/staff.spec.ts` và thêm route vào smoke**

`smoke.spec.ts`: thêm `"/staff/bookings"`, `"/staff/bookings/ZZZZZZ"` vào `PROTECTED`.

```ts
import { expect, test, type Page } from "@playwright/test";
import { day, field, issuedBooking } from "./helpers";

// E2E màn Staff S-01–S-06. Trình duyệt ở múi giờ New York (TDD §7).
test.use({ timezoneId: "America/New_York" });

/** Bỏ phiên hiện tại rồi đăng nhập Staff của seed V105: trang mặc định là S-01 (APP_FLOW §1). */
async function loginStaff(page: Page) {
  await page.context().clearCookies();
  await page.goto("/login");
  await field(page, "Email").fill("staff@demo.local");
  await field(page, "Mật khẩu").fill("Demo@1234");
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page).toHaveURL("/staff/bookings");
}

test("Staff tra cứu booking và gửi lại email vé (UF-07)", async ({ page }) => {
  test.slow();
  const { code, email } = await issuedBooking(page, "Economy Smart", [
    { from: "HAN", to: "SGN", date: day(3), flight: "QH201" },
    { from: "SGN", to: "HAN", date: day(5), flight: "QH202" },
  ]);

  // Customer không dùng được khu Staff: câu 403 của backend, vẫn có tiêu đề.
  await page.goto("/staff/bookings");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Tra cứu đặt chỗ.",
  );
  await expect(page.getByRole("alert")).toContainText(
    "Bạn không có quyền thực hiện thao tác này",
  );

  await loginStaff(page);
  const nav = page.getByRole("navigation", { name: "Nhân viên" });
  await expect(nav.getByRole("link", { name: "Đặt chỗ" })).toHaveAttribute(
    "aria-current",
    "page",
  );

  // S-01 (FR-70): mã gõ chữ thường vẫn khớp.
  await field(page, "Mã đặt chỗ, email hoặc số điện thoại").fill(
    code.toLowerCase(),
  );
  await page.getByRole("button", { name: "Tìm", exact: true }).click();
  const rows = page
    .getByRole("table", { name: "Kết quả tra cứu" })
    .getByRole("row");
  await expect(rows).toHaveCount(2); // dòng tiêu đề + 1 đặt chỗ
  await expect(rows.nth(1)).toContainText("Đã xuất vé");
  await expect(rows.nth(1)).toContainText(email);
  await expect(rows.nth(1)).toContainText("Chiều đi: HAN → SGN");
  await rows.nth(1).getByRole("link", { name: code }).click();

  // S-02: tài khoản đã đặt; chỉ xem, không có thao tác của khách; gửi lại email vé (FR-73).
  await expect(page).toHaveURL(`/staff/bookings/${code}`);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    `Đặt chỗ ${code}.`,
  );
  await expect(
    page.getByRole("region", { name: "Tài khoản đặt chỗ" }),
  ).toContainText(`Phạm Thu Hà, ${email}`);
  await expect(
    page.getByRole("article", { name: "Chiều đi" }).getByRole("link"),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Gửi lại email vé" }).click();
  await expect(page.getByRole("status")).toContainText(
    `Đã gửi lại email vé tới ${email}.`,
  );
  await page.getByRole("link", { name: "Tra cứu đặt chỗ" }).click();
  await expect(page).toHaveURL("/staff/bookings");
});
```

- [ ] **Step 3: Chạy để thấy test fail**

Run: `cd frontend && npx playwright test staff.spec smoke`
Expected: FAIL. `issuedBooking` chạy tới C-09 rồi `/staff/bookings` là 404 (không có `h1` "Tra cứu đặt chỗ."); smoke fail ở 2 route mới.

- [ ] **Step 4: Nền dùng chung — `booking.ts`, `useApi`, `SiteHeader`, `staff-ui.tsx`**

- `src/lib/booking.ts`: thêm `export const at` và `export const PAYMENT_STATUS` (chuyển nguyên văn từ `booking-view.tsx`, nơi đó import lại).
- `src/lib/use-api.ts` theo Interfaces: state `{ path, result }`, effect theo `[path, version, router]`, trả `state?.path === path ? state.result : undefined`. JSDoc ghi: dùng cho các màn chỉ đọc theo đường dẫn; C-08, C-09 giữ cách tải riêng của chúng.
- `site-header.tsx`: bảng `NAV: Record<"customer" | "staff", { label: string; links: readonly (readonly [string, string])[] }>`; `customer` giữ nguyên `aria-label="Khách hàng"` và hai link cũ; `staff` như "Điểm plan tự chọn". Cập nhật JSDoc.
- `src/app/staff/staff-ui.tsx` theo Interfaces.

- [ ] **Step 5: C-09 — export `BookingBody` có chế độ `staff`**

Trong `booking-view.tsx`: `export function BookingBody`, props thành `{ booking: Detail; now: number | null; staff?: boolean; busy?: boolean; onPayReschedule?: (id: number) => void }` (`staff = false`, `busy = false`). Khi `staff`: không có link "Hoàn chiều này", "Đổi chiều này"; khoản đổi chuyến vẫn hiện "Còn mm:ss để thanh toán" nhưng không có nút thanh toán; mỗi `li` của "Yêu cầu hoàn" thêm `<Link className={`${textLink} text-caption justify-self-start`} href={`/staff/refunds/${r.id}`}>Xử lý yêu cầu</Link>`. C-09 không đổi hành vi.

- [ ] **Step 6: S-01 `staff/bookings/page.tsx` và `booking-search.tsx`**

`page.tsx` như `bookings/page.tsx`: `metadata.title = "Tra cứu đặt chỗ | SkyLine"`, `<Suspense><BookingSearch /></Suspense>`, JSDoc tiếng Việt (S-01, Staff và Admin).

`BookingSearch` (`"use client"`) trong `StaffPage`:

- `h1` "Tra cứu đặt chỗ."
- `<Form action="/staff/bookings" key={params.toString()}>` (lưới `grid gap-16 sm:grid-cols-[minmax(0,1fr)_220px_auto] sm:items-end`): `Field label="Mã đặt chỗ, email hoặc số điện thoại" name="q" type="search" defaultValue={q}`, `SelectField label="Trạng thái" name="status" defaultValue={status}` (`""` "Tất cả" rồi các khoá của `STATUS_LABEL`), nút `pillDark` "Tìm".
- `useApi<Paged>(`/staff/bookings?${listQuery(params, ["q", "status"])}`)`: chưa có → `FormSkeleton rows={2}`; lỗi → `Notice`; rỗng → `q` ? `Không có đặt chỗ nào khớp "${q}".`: "Chưa có đặt chỗ nào."; có → bảng`aria-label="Kết quả tra cứu"`trong`tableBox`, cột: "Mã đặt chỗ" (link `textLink`tới`/staff/bookings/{code}`), "Trạng thái" (`STATUS_LABEL`), "Liên hệ" (tên; email, số điện thoại trên dòng caption muted), "Hành trình" (mỗi chiều một dòng `${DIRECTION_LABEL}: ${dep} → ${arr}, ${clock} ${dmy}` như C-08), "Tổng tiền" (`vnd`), "Đặt lúc" (`at(createdAt)`). Dưới bảng `Pager path="/staff/bookings"`.

- [ ] **Step 7: S-02 `staff/bookings/[code]/page.tsx` và `staff-booking-view.tsx`**

`page.tsx`: `metadata.title = "Chi tiết đặt chỗ | SkyLine"`, Suspense như C-09.

`StaffBookingView` (`"use client"`) trong `StaffPage`:

- `const { code } = useParams<{ code: string }>()`; `useApi<Schemas["StaffBookingDetail"]>(`/staff/bookings/${encodeURIComponent(code)}`)`; `now = useNow()`.
- Đầu trang: link `textLink` "Tra cứu đặt chỗ" về `/staff/bookings`; `h1` = lỗi 404 ? "Không tìm thấy đặt chỗ." : `Đặt chỗ ${code.toUpperCase()}.`; có dữ liệu thì `Đặt lúc ${at(createdAt)} (giờ Việt Nam)`. Lỗi khác 404 → `Notice`.
- `Section title="Tài khoản đặt chỗ"` (import `Section` từ C-09): `${account.fullName}, ${account.email}`.
- Khối trạng thái `section aria-label="Trạng thái"` cùng class với C-09: `STATUS_LABEL`; `PENDING_PAYMENT` thì `Giữ chỗ đến ${at(holdExpiresAt)} (giờ Việt Nam)`; `ISSUED` có `issuedAt` thì `Xuất vé lúc ...`; `ISSUED` thì nút `pillLine` "Gửi lại email vé" (`disabled` khi đang gửi): `POST /staff/bookings/{code}/resend-ticket-email`, thành công `<Notice tone="ok">{`Đã gửi lại email vé tới ${booking.contact.email}.`}</Notice>`, lỗi `Notice` câu backend (401 → login).
- `<BookingBody booking={booking} now={now} staff />`.

- [ ] **Step 8: Chạy lại**

Run: `cd frontend && npx playwright test staff.spec smoke aftersales booking.spec`
Expected: PASS: test Staff, smoke (gồm 2 route mới; customer đăng nhập mở chúng vẫn có `h1`), 3 test `aftersales.spec.ts` và 3 test `booking.spec.ts` (C-09 đổi props của `BookingBody`).

- [ ] **Step 9: Commit**

```bash
git add frontend/src frontend/e2e
git commit -m "feat(frontend): tra cứu và chi tiết đặt chỗ cho Staff (S-01, S-02)"
```

---

### Task 3: Danh sách chuyến (S-05) và hành khách theo chuyến (S-06)

**Files:**

- Create: `frontend/src/app/staff/flights/page.tsx`, `frontend/src/app/staff/flights/flight-list.tsx`, `frontend/src/app/staff/flights/[id]/passengers/page.tsx`, `frontend/src/app/staff/flights/[id]/passengers/manifest-view.tsx`
- Modify: `frontend/e2e/staff.spec.ts`, `frontend/e2e/smoke.spec.ts`

**Interfaces:**

- Consumes: Task 1 (`listQuery`, `FLIGHT_STATUS`, `cabinLabel`), Task 2 (`useApi`, `StaffPage`, `Pager`, `tableBox`, `th`, `td`); `useAirports` của `src/lib/airports.ts`; `PASSENGER_LABEL`, `clock`, `dmy` của `booking.ts`.
- Produces: link S-05 → S-06 `/staff/flights/{id}/passengers`; link S-06 → S-02 theo mã đặt chỗ. Plan 20 (A-06) link sang S-06 bằng đường dẫn này.

- [ ] **Step 1: Viết E2E (thêm vào cuối test của Task 2, trước link "Tra cứu đặt chỗ") và route smoke**

`smoke.spec.ts`: thêm `"/staff/flights"`, `"/staff/flights/0/passengers"` vào `PROTECTED`. Thêm `import { dmy } from "../src/lib/booking";`.

```ts
// S-05 (APP_FLOW §2.2): ô "Trạng thái" để trống vẫn lọc đúng; giờ theo sân bay dù trình duyệt ở New York.
await nav.getByRole("link", { name: "Chuyến bay" }).click();
await field(page, "Điểm đi").selectOption("HAN");
await field(page, "Điểm đến").selectOption("SGN");
await field(page, "Ngày bay").fill(day(3));
await field(page, "Hãng bay").selectOption("QH");
await page.getByRole("button", { name: "Lọc", exact: true }).click();
await expect(page).toHaveURL(
  `/staff/flights?from=HAN&to=SGN&date=${day(3)}&airline=QH&status=`,
);
const flight = page.getByRole("row", { name: /QH201/ });
await expect(flight).toContainText(`10:00 ${dmy(day(3))}`);
await expect(flight).toContainText("Theo lịch");
await expect(flight).toContainText(/Phổ thông \d+\/\d+/);

// S-06 (FR-72): khách của booking vừa tạo có trong manifest, link về S-02.
await flight.getByRole("link", { name: "Hành khách" }).click();
await expect(page.getByRole("heading", { level: 1 })).toHaveText(
  "Hành khách QH201.",
);
const passenger = page.getByRole("row", { name: new RegExp(code) });
await expect(passenger).toContainText("PHAM THU HA");
await expect(passenger).toContainText("Người lớn");
await expect(passenger).toContainText("Phổ thông");
await expect(passenger).toContainText(/\d{13}/);
await expect(passenger).toContainText("0912345678");
await passenger.getByRole("link", { name: code }).click();
await expect(page).toHaveURL(`/staff/bookings/${code}`);
```

- [ ] **Step 2: Chạy để thấy test fail**

Run: `cd frontend && npx playwright test staff.spec smoke`
Expected: FAIL: `/staff/flights` là 404 (không có ô "Điểm đi"); smoke fail ở 2 route mới.

- [ ] **Step 3: S-05 `staff/flights/page.tsx` và `flight-list.tsx`**

`page.tsx`: `metadata.title = "Chuyến bay | SkyLine"`, Suspense.

`FlightList` (`"use client"`) trong `StaffPage`:

- `h1` "Chuyến bay."
- Form `<Form action="/staff/flights">` (lưới `grid gap-16 sm:grid-cols-[repeat(auto-fit,minmax(180px,1fr))] sm:items-end`), thứ tự ô đúng thứ tự của test URL: `SelectField` "Điểm đi" `from`, "Điểm đến" `to` (lựa chọn `""` "Tất cả" rồi `useAirports().airports`, nhãn `${code}, ${city}`, value `code`), `Field` "Ngày bay" `date` `type="date"`, `SelectField` "Hãng bay" `airline` (`""` "Tất cả" rồi `useApi<AirlineResponse[]>("/airlines")`, nhãn `${code}, ${name}`), `SelectField` "Trạng thái" `status` (`""` "Tất cả", `FLIGHT_STATUS`), nút `pillDark` "Lọc". Form chỉ hiện khi danh sách sân bay và hãng đã tải xong (hoặc lỗi, khi đó ô chọn chỉ có "Tất cả"); trước đó là `FormSkeleton rows={1}`. Lý do: `defaultValue` của `<select>` chỉ áp lúc mount, lựa chọn về sau không được chọn theo URL; còn đổi `key` khi tải xong sẽ mount lại form và xoá các ô người dùng vừa chọn. `key` của form = `params.toString()`.
- `useApi<Paged>(`/staff/flights?${listQuery(params, ["from", "to", "date", "airline", "status"])}`)`; chưa có → khung xương; lỗi → `Notice`; rỗng → "Không có chuyến nào khớp bộ lọc."; có → bảng `aria-label="Danh sách chuyến"`, cột: "Chuyến" (`flightNumber`), "Tuyến" (`${dep} → ${arr}`), "Cất cánh" (`${clock} ${dmy}` của `departureTime`), "Hạ cánh" (tương tự), "Máy bay", "Trạng thái" (`FLIGHT_STATUS`), "Ghế còn" (mỗi hạng một dòng `${cabinLabel(c.cabinClass)} ${c.availableSeats}/${c.totalSeats}`), cột cuối link `textLink`"Hành khách". Caption dưới bảng: "Giờ theo sân bay đi và đến."`Pager path="/staff/flights"`.

- [ ] **Step 4: S-06 `staff/flights/[id]/passengers/page.tsx` và `manifest-view.tsx`**

`page.tsx`: `metadata.title = "Hành khách theo chuyến | SkyLine"`, Suspense.

`ManifestView` (`"use client"`) trong `StaffPage`:

- `useApi<Schemas["Manifest"]>(`/staff/flights/${encodeURIComponent(id)}/passengers`)`.
- Link `textLink` "Danh sách chuyến" về `/staff/flights`; `h1` = 404 ? "Không tìm thấy chuyến bay." : có dữ liệu ? `Hành khách ${flight.flightNumber}.` : "Hành khách theo chuyến."; lỗi khác → `Notice`.
- Dưới `h1`: `${dep} → ${arr}, cất cánh ${clock} ${dmy} (giờ ${dep})` và `FLIGHT_STATUS`; dòng caption `${passengers.length} hành khách có vé hiệu lực.`
- Rỗng → "Chuyến này chưa có hành khách nào có vé."; có → bảng `aria-label="Hành khách"`, cột: "Họ tên" (`${lastName} ${firstName}`), "Loại khách" (`PASSENGER_LABEL[type] ?? type`), "Hạng ghế" (`cabinLabel`), "Số vé", "Mã đặt chỗ" (link `textLink` tới `/staff/bookings/{code}`), "Liên hệ" (số điện thoại, email).

- [ ] **Step 5: Chạy lại**

Run: `cd frontend && npx playwright test staff.spec smoke`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src frontend/e2e
git commit -m "feat(frontend): danh sách chuyến và hành khách theo chuyến cho Staff (S-05, S-06)"
```

---

### Task 4: Hàng đợi (S-03) và xử lý yêu cầu hoàn (S-04)

**Files:**

- Create: `frontend/src/app/staff/refunds/page.tsx`, `frontend/src/app/staff/refunds/refund-queue.tsx`, `frontend/src/app/staff/refunds/[id]/page.tsx`, `frontend/src/app/staff/refunds/[id]/refund-detail.tsx`
- Modify: `frontend/e2e/staff.spec.ts`, `frontend/e2e/smoke.spec.ts`

**Interfaces:**

- Consumes: Task 1 (`listQuery`, `refundActions`, `REFUND_PROVIDER`), Task 2 (`useApi`, `StaffPage`, `Pager`, `tableBox`, `th`, `td`, `at`, `PAYMENT_STATUS`, `Section`, link "Xử lý yêu cầu" trên S-02, `csrfHeaders`, `loginStaff`); `REFUND_STATUS`, `REFUND_SOURCE`, `DIRECTION_LABEL`, `STATUS_LABEL` của `booking.ts`; `PriceTable` (có `totalLabel`); `Field`, `Notice`, `useSubmit`, `text` của `form.tsx`.
- Produces: route `/staff/refunds?status=&source=`, `/staff/refunds/{id}`.

- [ ] **Step 1: Viết E2E (test mới trong `staff.spec.ts`) và route smoke**

`smoke.spec.ts`: thêm `"/staff/refunds"`, `"/staff/refunds/0"` vào `PROTECTED`. Import thêm `csrfHeaders` từ `./helpers`.

```ts
test("Staff từ chối, duyệt rồi xác nhận hoàn thủ công (UF-02, FR-71)", async ({
  page,
}) => {
  // "Duyệt" gọi Refund API của VNPay sandbox: tối đa ~35 giây.
  test.setTimeout(180_000);
  page.on("dialog", (d) => d.accept());
  const { code } = await issuedBooking(page, "Economy Smart", [
    { from: "HAN", to: "SGN", date: day(3), flight: "QH201" },
    { from: "SGN", to: "HAN", date: day(5), flight: "QH202" },
  ]);
  const headers = await csrfHeaders(page);
  for (const [direction, reason] of [
    ["OUTBOUND", "Đổi kế hoạch công tác"],
    ["RETURN", "Bận việc gia đình"],
  ]) {
    const res = await page.request.post(
      `/api/bookings/${code}/journeys/${direction}/refund-requests`,
      { headers, data: { reason } },
    );
    expect(res.ok()).toBe(true);
  }
  await loginStaff(page);
  const nav = page.getByRole("navigation", { name: "Nhân viên" });

  // S-03: lọc chờ duyệt và nguồn khách, cũ nhất trước; mọi dòng đúng bộ lọc.
  await nav.getByRole("link", { name: "Yêu cầu hoàn" }).click();
  await field(page, "Trạng thái").selectOption("PENDING");
  await field(page, "Nguồn").selectOption("CUSTOMER");
  await page.getByRole("button", { name: "Lọc", exact: true }).click();
  await expect(page).toHaveURL("/staff/refunds?status=PENDING&source=CUSTOMER");
  const queue = page
    .getByRole("table", { name: "Hàng đợi yêu cầu hoàn" })
    .getByRole("row");
  await expect(queue.nth(1)).toBeVisible();
  for (const row of (await queue.all()).slice(1)) {
    await expect(row).toContainText("Chờ duyệt");
    await expect(row).toContainText("Khách yêu cầu");
  }

  // Từ S-02 mở yêu cầu của chiều về.
  await page.goto(`/staff/bookings/${code}`);
  const requests = page
    .getByRole("region", { name: "Yêu cầu hoàn" })
    .getByRole("listitem");
  await requests
    .filter({ hasText: "Chiều về" })
    .getByRole("link", { name: "Xử lý yêu cầu" })
    .click();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    /^Yêu cầu hoàn #\d+\.$/,
  );

  // Dữ liệu cũ (Staff khác vừa xử lý): câu 409 của backend, rồi trang tải lại.
  await page.route(
    "**/api/staff/refund-requests/*/approve",
    (route) =>
      route.fulfill({
        status: 409,
        json: {
          code: "INVALID_STATE",
          detail: "Chỉ duyệt được yêu cầu đang chờ duyệt",
        },
      }),
    { times: 1 },
  );
  const reloaded = page.waitForRequest(
    (r) =>
      r.method() === "GET" &&
      /\/api\/staff\/refund-requests\/\d+$/.test(r.url()),
  );
  await page.getByRole("button", { name: "Duyệt", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "Chỉ duyệt được yêu cầu đang chờ duyệt",
  );
  await reloaded;

  // Từ chối: lý do bắt buộc (BR-63), ghi người xử lý (FR-71).
  const reject = page.getByRole("button", { name: "Từ chối", exact: true });
  await reject.click();
  await expect(page.getByText("Hãy nhập lý do từ chối.")).toBeVisible();
  await field(page, "Lý do từ chối").fill("Không đủ điều kiện hoàn");
  await reject.click();
  await expect(page.getByRole("status")).toContainText(
    "Đã từ chối yêu cầu. Khách sẽ nhận email kèm lý do.",
  );
  const state = page.getByRole("region", { name: "Trạng thái" });
  await expect(state).toContainText("Bị từ chối");
  await expect(page.getByRole("region", { name: "Xử lý" })).toContainText(
    "Người xử lý: staff@demo.local",
  );
  await expect(page.getByRole("region", { name: "Thao tác" })).toHaveCount(0);

  // Chiều đi: duyệt (BR-62). Lượt thu là giả nên VNPay không hoàn được: APPROVED kèm lỗi (BR-64).
  await page.getByRole("link", { name: `Đặt chỗ ${code}` }).click();
  await requests
    .filter({ hasText: "Chiều đi" })
    .getByRole("link", { name: "Xử lý yêu cầu" })
    .click();
  const amounts = page.getByRole("region", { name: "Số tiền hoàn" });
  await expect(amounts).toContainText("1.730.000 đ");
  await expect(amounts).toContainText("-500.000 đ");
  await expect(amounts.getByRole("definition").last()).toHaveText(
    "1.230.000 đ",
  );
  await page.getByRole("button", { name: "Duyệt", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "Hoàn tiền qua VNPay chưa xong",
    { timeout: 60_000 },
  );
  await expect(state).toContainText("Đã duyệt, đang hoàn tiền");
  await expect(page.getByRole("region", { name: "Xử lý" })).toContainText(
    "Lỗi gần nhất:",
  );
  await expect(page.getByRole("button", { name: "Thử lại" })).toBeVisible();
  const attempts = page
    .getByRole("region", { name: "Các lượt hoàn" })
    .getByRole("listitem");
  await expect(attempts.first()).toContainText("VNPay, thất bại, 1.230.000 đ");

  // Hoàn thủ công: ghi chú bắt buộc; bấm đúp chỉ gửi một lần (BR-64).
  let manual = 0;
  page.on("request", (r) => {
    if (r.method() === "POST" && r.url().endsWith("/mark-manual")) manual++;
  });
  const settle = page.getByRole("button", { name: "Xác nhận hoàn thủ công" });
  await settle.click();
  await expect(page.getByText("Hãy nhập ghi chú hoàn thủ công.")).toBeVisible();
  await field(page, "Ghi chú hoàn thủ công").fill("Đã chuyển khoản ngày 20/10");
  await settle.dblclick();
  await expect(state).toContainText("Đã hoàn tiền");
  expect(manual).toBe(1);
  await expect(amounts).toContainText("Đã hoàn 1.230.000 đ");
  await expect(attempts.last()).toContainText(
    "Thủ công, thành công, 1.230.000 đ",
  );
  await expect(page.getByRole("region", { name: "Thao tác" })).toHaveCount(0);
});
```

- [ ] **Step 2: Chạy để thấy test fail**

Run: `cd frontend && npx playwright test staff.spec smoke`
Expected: test mới FAIL ở `/staff/refunds` (404, không có ô "Trạng thái"); smoke fail ở 2 route mới; test của Task 2 vẫn xanh.

- [ ] **Step 3: S-03 `staff/refunds/page.tsx` và `refund-queue.tsx`**

`page.tsx`: `metadata.title = "Yêu cầu hoàn | SkyLine"`, Suspense.

`RefundQueue` (`"use client"`) trong `StaffPage`:

- `h1` "Yêu cầu hoàn."; caption muted "Cũ nhất trước."
- Form `<Form action="/staff/refunds" key={params.toString()}>`: `SelectField` "Trạng thái" `status` (`""` "Tất cả", `REFUND_STATUS`), "Nguồn" `source` (`""` "Tất cả", `REFUND_SOURCE`), nút `pillDark` "Lọc".
- `useApi<Schemas["PagedModelRefundSummary"]>(`/staff/refund-requests?${listQuery(params, ["status", "source"])}`)`. Rỗng → "Không có yêu cầu nào khớp bộ lọc."; có → bảng `aria-label="Hàng đợi yêu cầu hoàn"`, cột: "Yêu cầu" (link `textLink` `#${id}`tới`/staff/refunds/{id}`), "Mã đặt chỗ" (link tới S-02), "Chiều" (`direction ? DIRECTION_LABEL : "Khoản thanh toán thừa"`), "Nguồn" (`REFUND_SOURCE`), "Số tiền" (`vnd(refundAmount)`), "Trạng thái" (`REFUND_STATUS`), "Gửi lúc" (`at(createdAt)`). Caption: "Giờ Việt Nam." `Pager path="/staff/refunds"`.

- [ ] **Step 4: S-04 `staff/refunds/[id]/page.tsx` và `refund-detail.tsx`**

`page.tsx`: `metadata.title = "Chi tiết yêu cầu hoàn | SkyLine"`, Suspense.

`RefundDetailView` (`"use client"`) trong `StaffPage`, `useApi<Schemas["RefundDetail"]>(`/staff/refund-requests/${encodeURIComponent(id)}`, version)`:

- Đầu trang: link `textLink` "Hàng đợi yêu cầu hoàn" về `/staff/refunds`; `h1` = 404 ? "Không tìm thấy yêu cầu hoàn." : `Yêu cầu hoàn #${id}.`; lỗi tải khác → `Notice`.
- Thông báo: `done` (`Notice tone="ok"`), `actionError` (`Notice`), đặt trên các khối.
- `section aria-label="Trạng thái"` (class như khối trạng thái C-09): `REFUND_STATUS[status]` đậm; caption `${direction ? DIRECTION_LABEL[direction] : "Khoản thanh toán thừa"}, ${REFUND_SOURCE[source]}`; `Gửi lúc ${at(createdAt)} (giờ Việt Nam)`; có `completedAt` thì `Hoàn tất lúc ...`.
- Bố cục hai cột `lg:grid-cols-[minmax(0,1fr)_400px]` như C-09. Cột trái: `Section "Xử lý"` gồm `Lý do của khách: ${reason}` (nếu có), `Người xử lý: ${processedBy}, lúc ${at(processedAt)} (giờ Việt Nam)` (nếu có), `Ghi chú: ${staffNote}` (nếu có), `APPROVED` có `lastError` thì `Lỗi gần nhất: ${lastError}`; không có dòng nào thì "Chưa có ai xử lý."; `Section "Các lượt hoàn"`: `<ul>` mỗi lượt `${REFUND_PROVIDER[provider] ?? provider}, ${PAYMENT_STATUS[status] ?? status}, ${vnd(amount)}, ${at(createdAt)}` thêm `, mã ${responseCode}` nếu có; rỗng → "Chưa có lượt hoàn nào."; `Section "Thao tác"` chỉ khi `refundActions` có quyền nào (xem dưới). Cột phải: `Section "Số tiền hoàn"`: `PriceTable rows={[[source === "INVALID_PAYMENT" ? "Số tiền đã thu" : "Giá trị chiều", baseAmount], ["Phí hoàn", -feeAmount]]} total={refundAmount} totalLabel="Số tiền được hoàn"` và caption `Đã hoàn ${vnd(refundedAmount)}`; `Section "Đặt chỗ"`: link `textLink` `Đặt chỗ ${bookingCode}` tới S-02, `STATUS_LABEL[bookingStatus]`, tên liên hệ, email và số điện thoại.
- Khu "Thao tác" theo `refundActions(status, source)`:
  - `approve`: nút `pillDark` "Duyệt"; `confirm(`Duyệt yêu cầu và hoàn ${vnd(refundAmount)} cho khách qua VNPay?`)`; `POST .../approve`; xong: "Đã duyệt và hoàn tiền xong."
  - `reject`: form `Field label="Lý do từ chối" name="reason" required maxLength={500} error={errors.reason}`, nút `pillLine` "Từ chối"; `POST .../reject` body `{ reason: text(data, "reason") }`; xong: "Đã từ chối yêu cầu. Khách sẽ nhận email kèm lý do."
  - `settle`: nút `pillDark` "Thử lại" (`confirm("Gọi lại VNPay để hoàn phần còn thiếu?")`, `POST .../retry`, xong: "Đã hoàn tiền xong."); form `Field label="Ghi chú hoàn thủ công" name="note" required maxLength={500} error={errors.note}` kèm caption "Dùng khi đã chuyển tiền cho khách ngoài hệ thống.", nút `pillLine` "Xác nhận hoàn thủ công"; `POST .../mark-manual` body `{ note }`; xong: "Đã xác nhận hoàn thủ công."
- Gửi: một hàm `act(path, body, ok)` dùng chung; `useRef` chặn lần gọi thứ hai khi đang gửi (nhả ra khi xong); hai form gửi qua `useSubmit` (một instance, vì mỗi trạng thái chỉ có một form) gọi `act`. Xoá `done`, `actionError` trước mỗi lần gửi. Thành công → `setDone(ok)`, form `reset()`, `setVersion(v => v + 1)`. `ApiError` 401 → `router.replace` về login; có `errors` → trả cho `useSubmit` hiện dưới ô (không tải lại); còn lại → `setActionError(e.detail)` và tải lại. Mọi nút `disabled` khi đang gửi.

- [ ] **Step 5: Chạy lại**

Run: `cd frontend && npx playwright test staff.spec smoke`
Expected: PASS: 2 test Staff và smoke.

- [ ] **Step 6: Commit**

```bash
git add frontend/src frontend/e2e
git commit -m "feat(frontend): hàng đợi và xử lý yêu cầu hoàn cho Staff (S-03, S-04)"
```

---

### Task 5: Kiểm tra toàn bộ và cập nhật tài liệu

**Files:**

- Modify: `docs/superpowers/plans/2026-10-09-00-roadmap.md`, `CLAUDE.md`

- [ ] **Step 1: Chạy toàn bộ kiểm tra frontend**

```bash
cd frontend && npm run lint && npm run build && npx playwright test
```

Expected: lint, build không lỗi; Playwright không có test nào fail (thêm so với trước plan: 5 unit, 2 E2E Staff, 6 smoke chưa đăng nhập). Chạy `npx playwright test staff.spec` thêm 2 lần, cả 3 lần đều xanh; chập chờn vì `npm run dev` dịch route chậm thì chạy lại trên bản build (`E2E_BASE_URL`) để phân biệt.

- [ ] **Step 2: Thử tay trên giao diện**

Đăng nhập `staff@demo.local`, rồi `admin@demo.local` (Admin vào được mọi màn Staff qua link header sau khi mở `/staff/bookings`). Kiểm tra bằng mắt ở chế độ sáng, tối và bề rộng 375 px: header có 3 link Staff, bảng cuộn ngang trong khung chứ không cuộn cả trang, form lọc xuống dòng gọn; S-04 ở `PENDING` (nút Duyệt và form từ chối), `APPROVED` (Thử lại và form hoàn thủ công), `COMPLETED` (không có khu Thao tác). Mailpit (http://localhost:8025) có email vé sau "Gửi lại email vé" và email từ chối sau "Từ chối".

- [ ] **Step 3: Cập nhật lộ trình `docs/superpowers/plans/2026-10-09-00-roadmap.md`**

(a) Dòng "Plan chi tiết đã có": thêm vào cuối (trước ` |`) ` · [Plan 18 — Màn hình Staff](2026-10-20-18-staff-screens.md)`.

(b) §2, dòng Plan 18: `☐` → `☑`.

(c) §6, thêm dưới dòng `| 17 | ...`:

```markdown
| 18 | Màn Staff (Admin cũng dùng): `/staff/bookings` (S-01), `/staff/bookings/[code]` (S-02), `/staff/refunds` (S-03), `/staff/refunds/[id]` (S-04), `/staff/flights` (S-05), `/staff/flights/[id]/passengers` (S-06). `SiteHeader({area: "customer" \| "staff"})` (Plan 20 thêm `admin`). `src/lib/use-api.ts`: `useApi<T>(path, version?) → {data} \| {error} \| undefined` (401 về đăng nhập, bỏ phản hồi cũ). `src/lib/staff.ts`: `listQuery(params, keys)` (bỏ ô rỗng, trang URL từ 1 sang API từ 0), `pageOf`, `pageHref`, `refundActions(status, source)`, `FLIGHT_STATUS`, `REFUND_PROVIDER`, `cabinLabel`. `src/app/staff/staff-ui.tsx`: `StaffPage`, `Pager`, `tableBox`, `th`, `td`. Form lọc dùng `next/form` (GET). `booking.ts` thêm `at`, `PAYMENT_STATUS`; `booking-view.tsx` export `BookingBody` (prop `staff`: chỉ xem, link "Xử lý yêu cầu" sang S-04). E2E: helper chung `e2e/helpers.ts` (`day`, `field`, `csrfHeaders`, `paidReturn`, `issuedBooking`); `e2e/staff.spec.ts` (`loginStaff`) duyệt hoàn trên VNPay sandbox thật, luôn lỗi vì lượt thu tự ký |
```

- [ ] **Step 4: Cập nhật `CLAUDE.md`**

Mục Status, thay:

```
(Plan 17; `e2e/aftersales.spec.ts` signs the VNPay Return URL with `VNPAY_HASH_SECRET` from `.env`), and the other screens are placeholders.
```

bằng:

```
(Plan 17; `e2e/aftersales.spec.ts` signs the VNPay Return URL with `VNPAY_HASH_SECRET` from `.env`), the staff screens S-01–S-06 under `/staff` (header `SiteHeader area="staff"`, data via `lib/use-api.ts`, URL filters via `lib/staff.ts` and `next/form`; S-02 reuses C-09's `BookingBody` read-only) are done (Plan 18; `e2e/staff.spec.ts` approves a refund against the real VNPay sandbox, which always fails for the self-signed charge, then marks it manual; shared E2E helpers live in `e2e/helpers.ts`), and the admin screens are not built yet.
```

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-10-09-00-roadmap.md CLAUDE.md
git commit -m "docs: hoàn tất Plan 18, cập nhật hợp đồng màn hình Staff"
```
