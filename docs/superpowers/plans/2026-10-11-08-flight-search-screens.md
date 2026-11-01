# Plan 08 — Màn hình tìm chuyến: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khách (không cần đăng nhập) tìm chuyến trên giao diện SkyLine: ô tìm chuyến ở trang chủ (C-01) gợi ý sân bay thật từ `GET /api/airports`, trang kết quả `/flights` (C-02) hiện hành trình bay thẳng và nối chuyến của Plan 07, lọc và sắp xếp ngay ở trình duyệt, khứ hồi đi theo 2 bước (chiều đi rồi chiều về, chỉ cho chọn chiều về thoả BR-04). Chọn gói giá xong thì lựa chọn nằm trong `sessionStorage` và trình duyệt sang `/checkout` (chưa đăng nhập thì qua `/login?next=%2Fcheckout`).

**Architecture:** Không đổi backend. C-01 giữ nguyên CSS của bản thiết kế (`landing.css`), chỉ thay nguồn sân bay của ô tìm chuyến bằng hook `useAirports()` (tải `GET /airports` một lần). C-02 là Client Component trong `<Suspense>` (vì `useSearchParams`): đọc điều kiện tìm từ URL, gọi `GET /flights/search` cho chiều đi và (nếu khứ hồi) chiều về cùng lúc, rồi giao cho `ResultList` lọc và sắp xếp bằng các hàm thuần trong `itineraries.ts`. Bước khứ hồi nằm trong URL (`out`, `outFare`), nên Back, Forward và tải lại trang giữ đúng bước. C-02 dùng token `@theme` của Plan 03 như các màn tài khoản.

**Tech Stack:** Next.js 16.4 (App Router, Cache Components), React 19.3, TypeScript, Tailwind v4, Playwright 1.64 (E2E và unit test chạy bằng test runner của Playwright, không mở trình duyệt). Không thêm thư viện.

**Spec:** [App Flow](../../APP_FLOW.md) §2.1 C-01, C-02, UF-01 · [PRD](../../PRD.md) FR-10, FR-11, FR-12, BR-04, BR-11, NFR-05, NFR-09 · [TDD](../../TDD.md) §7 (frontend: giờ theo sân bay, lựa chọn ở `sessionStorage`), §8.4, §8.8 (dạng JSON tìm kiếm và `journeys`) · [DESIGN.md](../../../DESIGN.md) · [Lộ trình](2026-10-09-00-roadmap.md) §5 (quy ước frontend), §6 (hợp đồng Plan 03, 04, 05, 07), §7.3, §7.4 · API thật: [Plan 05](2026-10-10-05-catalog.md) (`GET /api/airports`), [Plan 07](2026-10-11-07-flight-search.md) (`GET /api/flights/search`, kiểu `Schemas["SearchResult"]`)

## Global Constraints

- Mọi ràng buộc ở Global Constraints của [Plan 04](2026-10-10-04-account-screens.md) vẫn giữ: `--spacing: 1px` nên `p-22` = 22px; nút là pill (`pillDark`, `pillLine` của `src/components/form.tsx`), ô nhập và panel là khối vuông viền hairline, không shadow; chữ phụ dùng token `muted`; chế độ tối tự đổi qua token ngữ nghĩa (`ink`, `paper`, `line`...), không viết `dark:`; chữ hiển thị tiếng Việt, không dùng gạch dài.
- **Không gọi `new Date()`, `Date.now()` hay `Math.random()` khi render** (Cache Components), kể cả Client Component. `Date.parse(chuỗi)` và `new Date(y, m, d)` dựng từ dữ liệu thì được, vì không đọc giờ hiện tại. Component dùng `useSearchParams` nằm trong `<Suspense>`.
- **Không thêm thư viện npm.** Unit test của logic thuần chạy bằng `@playwright/test` đã có (test không dùng fixture `page` thì không mở trình duyệt).
- **Không sửa backend, không chạy lại `npm run gen:api`.** Kiểu API lấy từ `Schemas[...]` của `@/lib/api` (`AirportResponse`, `SearchResult`, `Itinerary`, `Segment`, `FareOption`, `AirportTime`), không viết tay.
- **Không đổi `landing.css`.** Ô tìm chuyến chỉ đổi nguồn dữ liệu, giữ nguyên class và hành vi bàn phím.
- **Giờ (TDD §7, NFR-05):** mọi giờ hiển thị là giờ địa phương của sân bay, không bao giờ theo múi giờ trình duyệt. Khung giờ cất cánh của bộ lọc cũng tính theo giờ sân bay đi.
- **Tiền:** số nguyên VND, hiển thị dạng `990.000 đ` (`Intl.NumberFormat("vi-VN")`, khoảng trắng không ngắt trước `đ`).
- **Đường dẫn route mới** phải vào danh sách `PUBLIC` hoặc `PROTECTED` của `e2e/smoke.spec.ts` (CLAUDE.md).

### Điểm cố ý khác TDD và APP_FLOW

1. **Ô tìm chuyến tải `GET /airports` một lần rồi lọc ở client**, không gọi `GET /airports?q=` mỗi lần gõ như APP_FLOW C-01 ghi. Danh mục chỉ có 29 sân bay đang hoạt động; tải một lần thì không trễ mạng ở mỗi phím, không phải xử lý kết quả về sai thứ tự, và giữ được thứ tự gợi ý sẵn có của ô tìm (đúng mã → mã bắt đầu bằng → thành phố bắt đầu bằng → thành phố hoặc tên chứa → tên quốc gia). API vẫn là nguồn dữ liệu duy nhất (FR-10: chỉ sân bay đang hoạt động). Khi danh mục lên hàng nghìn sân bay thì đổi sang `?q=` (ghi bằng comment `ponytail:` trong `airports.ts`).
2. **Giờ hiển thị đọc thẳng từ chuỗi thời gian của API**, không qua `Intl.DateTimeFormat` với `timeZone` như TDD §7 ghi. Plan 07 đã trả giờ theo múi giờ sân bay kèm offset (`2026-12-01T08:10:00+07:00`), nên `time.slice(11, 16)` cho đúng giờ sân bay, không phụ thuộc dữ liệu ICU của trình duyệt và không có đường nào dính múi giờ trình duyệt. E2E ghim điều này bằng trình duyệt đặt múi giờ New York.
3. **`/checkout` có ngay ở plan này dưới dạng trang tạm** (C-07 thuộc Plan 13) và đã nằm trong `matcher` của `proxy.ts`. Nhờ vậy luồng UF-01 "chọn gói → chưa đăng nhập → C-03 → quay lại C-07" chạy được và E2E kiểm được ngay. Plan 13 chỉ thay nội dung trang.

### Điểm plan tự chọn (APP_FLOW và PRD không nói)

- **Bước khứ hồi nằm trong URL.** Chọn gói chiều đi thì URL thêm `out` (id các chặng nối bằng dấu phẩy, VD `out=101,205`) và `outFare` (`fareFamilyId`). Bước 2 tìm lại hành trình đó trong kết quả chiều đi. Không khớp (hết chỗ, gói ngừng bán, URL sửa tay) thì báo "Chiều đi đã chọn không còn bán hoặc đã hết chỗ" và về bước 1.
- **Hai chiều tải song song ngay từ bước 1**; tham số tìm không đổi giữa hai bước nên chuyển bước không tải lại.
- **Chiều về vi phạm BR-04 bị ẩn**, kèm dòng "Đã ẩn N hành trình cất cánh trước giờ hạ cánh của chiều đi." So thời điểm (`Date.parse` hai chuỗi có offset), nên đúng cả khi hai sân bay khác múi giờ. Cất cánh **đúng bằng** giờ hạ cánh cũng bị ẩn ("sau" là lớn hơn hẳn).
- **Bộ lọc:** hãng, số điểm dừng, khung giờ cất cánh (4 khung: 00–06, 06–12, 12–18, 18–24 theo giờ sân bay đi, biên trái thuộc khung), giá tối đa. Trong một nhóm là "hoặc", giữa các nhóm là "và"; nhóm chưa chọn gì thì bỏ qua. Giá so với gói rẻ nhất của hành trình (tổng cho đúng số khách). "Khoảng giá" của FR-11 làm bằng một thanh giá tối đa: cận dưới luôn là hành trình rẻ nhất nên thanh thứ hai không lọc thêm được gì có ích. Mỗi bước có bộ lọc riêng (BR-04: hai chiều độc lập về hãng).
- **Sắp xếp:** giá thấp nhất (mặc định), cất cánh sớm nhất, bay nhanh nhất; bằng nhau thì hành trình rẻ hơn đứng trước.
- **Lựa chọn lưu ở `sessionStorage["skyline.selection"]`** theo dạng `journeys` của `POST /bookings/quote` (TDD §8.8: `direction`, `flightIds`, `fareFamilyId`), kèm `itinerary`, `fare` để C-07 hiển thị mà không phải tìm lại, và số khách. Plan 13 đọc khoá này.
- **Ô tìm chuyến khi đang tải:** ô "Từ", "Đến" vẫn hiện mã mặc định (`HAN`, `SGN`), dòng thành phố để trống tới khi danh sách về. Popup gợi ý hiện "Đang tải danh sách sân bay..." hoặc "Không tải được danh sách sân bay. Hãy tải lại trang."
- **`data.ts` của landing chỉ còn dữ liệu cho khối "tuyến gợi ý"** (7 sân bay của `ROUTES`). Thẻ minh hoạ đặt chỗ (`story.tsx`) lấy mã thẳng từ form, để sân bay ngoài danh sách mẫu (VD `VCS`) vẫn hiện đúng.
- **Lỗi 400 do URL sửa tay** (VD em bé nhiều hơn người lớn) hiện câu của backend dưới tiêu đề "Điều kiện tìm kiếm chưa hợp lệ." kèm link về trang chủ; mất mạng thì có nút "Thử lại". Ngày về trước ngày đi bị chặn ở client (FR-12), không gọi API.
- **Playwright chạy 4 luồng** (`workers: 4`). Mặc định là một nửa số nhân (8 trên máy dev 16 nhân): `npm run dev` dịch route theo yêu cầu, trang hydrate chậm hơn thời gian chờ 5 giây và click rơi vào form chưa hydrate. Đã gặp với cả `account.spec.ts` có sẵn; 4 luồng xanh ổn định.

### Lỗi đã biết, không sửa ở plan này

- `GET /api/flights/search?adults=0` trả câu tiếng Anh `must be greater than or equal to 1` vì `@Min` của `FlightSearch.SearchQuery` (Plan 07) chưa có `message`. Chỉ gặp khi sửa tay URL (ô tìm chuyến không cho xuống dưới 1 người lớn). Sửa bằng cách thêm `message` tiếng Việt cho `@Min`, `@NotNull`, `@NotBlank` của record đó khi có dịp sửa backend.

## Review Focus

Những tình huống tài liệu không nói rõ nhưng dễ gây lỗi thật, xếp theo khả năng gặp. Mỗi dòng đã có test ghim lại.

1. **Trình duyệt ở múi giờ khác sân bay.** Khách ở nước ngoài mở SGN→SIN vẫn phải thấy VN651 cất cánh 09:30 (giờ Việt Nam) và hạ cánh 12:30 (giờ Singapore), không phải giờ máy mình (Task 3 E2E `giờ hiện theo giờ địa phương của sân bay`, chạy với `timezoneId: "America/New_York"`; Task 2 `hạ cánh hôm sau thì lệch 1 ngày`).
2. **Biên BR-04.** Chiều về cất cánh đúng phút chiều đi hạ cánh thì không được chọn; so thời điểm nên chuyến ở múi giờ +08:00 cất cánh 13:05 vẫn là trước 12:10 giờ +07:00 (Task 2 `BR-04: ...`). Trên seed, chọn QH201 (hạ cánh 12:10) thì VN1825 + VN1232 (cất cánh 11:00) bị ẩn (Task 3 E2E `khứ hồi 2 bước`).
3. **Back, Forward, tải lại trang ở bước 2.** Back về bước 1, Forward và F5 vẫn ở bước 2 với đúng chiều đi đã chọn (Task 3 E2E `khứ hồi 2 bước`).
4. **Chiều đi trong URL không còn khớp** (hết chỗ giữa hai bước, hoặc URL sửa tay): báo rõ và về bước 1, không vỡ trang (Task 3 E2E `URL sai hoặc chiều đi không còn`).
5. **URL thiếu hoặc sai tham số:** `/flights` trống vẫn có tiêu đề và link về trang chủ (smoke test); em bé nhiều hơn người lớn hiện câu tiếng Việt của backend; ngày về trước ngày đi bị chặn ở client (Task 3 E2E `URL sai hoặc chiều đi không còn`).

---

## Trước khi bắt đầu

- **Cần có:** Plan 03, 04, 05, 07 đã xong; Node 24; Docker Desktop đang chạy.
- **Nhánh:** tạo `feature/flight-search-screens` từ `develop`.
- **Chạy lệnh ở đâu:** Git Bash, từ thư mục gốc repo, trừ khi bước đó ghi `cd frontend`.
- **Backend cho E2E:** mọi bước E2E cần backend profile `dev` ở cổng 8080 với seed `V106` còn mới (seed sinh 60 ngày tính từ ngày chạy migration; E2E tìm ngày mai). Mở một terminal riêng và để chạy suốt plan:

```bash
docker compose up -d postgres mailpit
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

  Kiểm tra seed còn hạn: `curl -s "http://localhost:8080/api/flights/search?from=HAN&to=SGN&date=$(TZ=Asia/Ho_Chi_Minh date -d tomorrow +%F)&adults=1&cabin=ECONOMY" | grep -o '"flightNumber":"[A-Z0-9]*"'` phải in `VJ123`, `QH201`, `VN213`, `VN1233`, `VN1826`. Nếu ra `{"itineraries":[]}` thì làm mới DB bằng `docker compose down -v` rồi chạy lại hai lệnh trên.
- **Chạy E2E:** `cd frontend && npx playwright test <tên file>`; Playwright tự bật `npm run dev` ở cổng 3000 (hoặc dùng lại server đang chạy). Lần đầu trên máy mới: `npx playwright install chromium`.
- **Đã kiểm chứng:** toàn bộ code trong plan đã chạy thử ngày 2026-10-11 trên Windows 11, Node 24.15, backend profile `dev` với seed `V106` cùng ngày. Cuối plan: `npm run lint`, `npm run build` xanh; `npx playwright test` cho 20 passed (4 unit, 4 E2E tìm chuyến, 10 smoke, 2 tài khoản), chạy 3 lần liên tiếp đều xanh. Đã thử cố ý đổi so sánh BR-04 từ `>` thành `>=`: test `BR-04` đỏ. Đã xem C-02 ở 390px và 1280px.

## Cấu trúc file sau plan

```
frontend/
  playwright.config.ts                 # Task 1: workers 4
  e2e/
    flight-search.spec.ts              # Task 1 (test C-01), Task 3 (C-02: khứ hồi, lọc, sắp xếp, múi giờ, URL sai)
    itineraries.spec.ts                # Task 2: unit test logic lọc, sắp xếp, BR-04 (không mở trình duyệt)
    smoke.spec.ts                      # Task 3: + /checkout vào PROTECTED
  src/
    proxy.ts                           # Task 3: + /checkout cần đăng nhập
    lib/
      airports.ts                      # Task 1: useAirports(), countryName()
      selection.ts                     # Task 3: Selection, Journey, saveSelection() (sessionStorage)
    components/landing/
      data.ts                          # Task 1: chỉ còn dữ liệu của khối tuyến gợi ý
      search-dock.tsx                  # Task 1: sân bay từ useAirports()
      story.tsx                        # Task 1: mã tuyến lấy thẳng từ form
    app/
      flights/
        page.tsx                       # Task 3: Suspense + FlightResults (thay trang tạm)
        itineraries.ts                 # Task 2: hàm thuần (giờ, tiền, lọc, sắp xếp, BR-04)
        flight-results.tsx             # Task 3: đọc URL, gọi API, hai bước khứ hồi, lưu lựa chọn
        result-list.tsx                # Task 3: bộ lọc, sắp xếp, danh sách
        itinerary-card.tsx             # Task 3: thẻ hành trình, chi tiết chặng, gói giá
      checkout/page.tsx                # Task 3: trang tạm C-07
```

## API dùng trong plan (đã có, không đổi)

| Method | Đường dẫn | Quyền | Dùng ở |
|---|---|---|---|
| GET | `/api/airports` (không có `q`: mọi sân bay đang hoạt động) | Công khai | C-01 |
| GET | `/api/flights/search?from=&to=&date=&adults=&children=&infants=&cabin=` | Công khai | C-02 (khứ hồi gọi hai lần, chiều về đảo `from`/`to` và dùng ngày về) |

## URL của C-02

`/flights?from=HAN&to=SGN&date=2026-12-01&returnDate=2026-12-05&adults=2&children=1&infants=1&cabin=ECONOMY` (C-01 tạo đúng dạng này, đã có từ trước). `returnDate` không có thì là một chiều. Bước 2 của khứ hồi thêm `&out=<flightId>[,<flightId>]&outFare=<fareFamilyId>`.

---

### Task 1: Ô tìm chuyến lấy sân bay từ `GET /airports` (C-01)

**Files:**

- Create: `frontend/src/lib/airports.ts`
- Modify: `frontend/src/components/landing/search-dock.tsx`, `frontend/src/components/landing/story.tsx:4,79`, `frontend/src/components/landing/data.ts` (viết lại), `frontend/playwright.config.ts:12`
- Test: `frontend/e2e/flight-search.spec.ts` (tạo mới, test đầu tiên)

**Interfaces:**

- Consumes: `apiFetch<T>(path)` và `Schemas` của `src/lib/api.ts` (Plan 03); `GET /api/airports` trả `Schemas["AirportResponse"][]` = `{code, name, city, countryCode, timezone, active}` (Plan 05).
- Produces:
  - `type Airport = Schemas["AirportResponse"]` (`src/lib/airports.ts`).
  - `useAirports(): { airports: Airport[] | null; failed: boolean }`: `airports` là `null` khi đang tải; cả trang dùng chung một lần tải.
  - `countryName(code: string): string`: tên quốc gia tiếng Việt, VD `"KR"` → `"Hàn Quốc"`, `"HK"` → `"Hồng Kông"`.
  - `data.ts` vẫn xuất `CABINS`, `CabinId`, `ROUTES`, `airportByCode` (chỉ còn 7 sân bay của `ROUTES`, kiểu `{code, city, cc}`), `HOLD_MINUTES`. Không còn xuất `AIRPORTS` và kiểu `Airport`.

- [ ] **Step 1: Giới hạn số luồng của Playwright**

Trong `frontend/playwright.config.ts`, thay:

```ts
  testDir: "./e2e",
  fullyParallel: true,
```

bằng:

```ts
  testDir: "./e2e",
  fullyParallel: true,
  // npm run dev dịch route theo yêu cầu: 8 luồng (mặc định trên máy 16 nhân) làm trang hydrate chậm, click rơi vào
  // form chưa hydrate. 4 luồng chạy ổn định.
  workers: 4,
```

- [ ] **Step 2: Viết test**

Tạo `frontend/e2e/flight-search.spec.ts` (Task 3 viết lại toàn bộ file này, thêm phần C-02):

```ts
import { expect, test } from "@playwright/test";

test("ô tìm chuyến gợi ý sân bay từ GET /airports rồi mở C-02", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("button", { name: /^Đến SGN/ }).click();
  // Côn Đảo (VCS) chỉ có trong seed V100, không có trong dữ liệu mẫu cũ của landing.
  await page.getByRole("textbox", { name: "Bay đến" }).fill("con dao");
  await page.getByRole("button", { name: /^VCS/ }).click();
  await expect(
    page.getByRole("button", { name: /^Đến VCS Côn Đảo/ }),
  ).toBeVisible();

  await page
    .getByRole("search", { name: "Tìm chuyến bay" })
    .getByRole("button", { name: "Tìm chuyến bay" })
    .click();
  await expect(page).toHaveURL(/\/flights\?from=HAN&to=VCS&date=/);
});
```

Nút "Tìm chuyến bay" phải tìm trong vùng `search`: trang chủ còn một nút cùng tên ở khối kêu gọi cuối trang.

- [ ] **Step 3: Chạy test để thấy nó đỏ**

```bash
cd frontend && npx playwright test flight-search
```

Expected: `1 failed`, lỗi `locator.click: Timeout` ở `getByRole('button', { name: /^VCS/ })`: danh sách mẫu trong `data.ts` không có Côn Đảo.

- [ ] **Step 4: Tạo hook đọc sân bay**

Tạo `frontend/src/lib/airports.ts`:

```ts
import { useEffect, useState } from "react";
import { apiFetch, type Schemas } from "./api";

export type Airport = Schemas["AirportResponse"];

// ponytail: tải cả danh sách một lần (29 sân bay) rồi lọc ở client; lên hàng nghìn sân bay thì gọi GET /airports?q= theo từng lần gõ.
let all: Promise<Airport[]> | undefined;

/** FR-10: sân bay đang hoạt động (GET /airports không có q). `airports` là null khi đang tải. */
export function useAirports() {
  const [airports, setAirports] = useState<Airport[] | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    all ??= apiFetch<Airport[]>("/airports");
    let live = true;
    all.then(
      (list) => live && setAirports(list),
      () => {
        all = undefined; // lần mở trang sau tải lại
        if (live) setFailed(true);
      },
    );
    return () => {
      live = false;
    };
  }, []);
  return { airports, failed };
}

const region = new Intl.DisplayNames(["vi"], {
  type: "region",
  style: "short",
});

/** Tên quốc gia tiếng Việt từ mã ISO, VD "KR" → "Hàn Quốc". */
export const countryName = (code: string) => region.of(code) ?? code;
```

`all` là biến cấp module: mọi component trên trang dùng chung một promise, nên mở lại popup không gọi lại API. Gọi thất bại thì xoá promise để lần mở trang sau tải lại.

- [ ] **Step 5: Cho ô tìm chuyến dùng `useAirports()`**

Sửa `frontend/src/components/landing/search-dock.tsx` theo đúng thứ tự dưới đây (mỗi đoạn "thay" xuất hiện đúng một lần trong file).

5a. Import. Thay:

```tsx
import { AIRPORTS, CABINS, airportByCode, type Airport } from "./data";
```

bằng:

```tsx
import { countryName, useAirports, type Airport } from "@/lib/airports";
import { CABINS } from "./data";
```

5b. `filterAirports` nhận danh sách làm tham số và dùng `countryCode`. Thay:

```tsx
function filterAirports(query: string, other: string): Airport[] {
  const q = norm(query);
  let list = AIRPORTS.filter((a) => a.code !== other);
```

bằng:

```tsx
function filterAirports(
  airports: Airport[],
  query: string,
  other: string,
): Airport[] {
  const q = norm(query);
  let list = airports.filter((a) => a.code !== other);
```

và thay:

```tsx
        else if (norm(a.country).includes(q)) score = 4;
```

bằng:

```tsx
        else if (norm(countryName(a.countryCode)).includes(q)) score = 4;
```

5c. Gọi hook ngay dưới `useSearch()`. Thay:

```tsx
  const { form, today: todayIso, update, flash } = useSearch();
```

bằng:

```tsx
  const { form, today: todayIso, update, flash } = useSearch();
  const { airports, failed } = useAirports();
```

5d. Tra sân bay và danh sách gợi ý. Thay:

```tsx
  const F = airportByCode(form.from);
  const T = airportByCode(form.to);
```

bằng:

```tsx
  const byCode = (code: string) =>
    airports?.find((a) => a.code === code) ?? null;
  const F = byCode(form.from);
  const T = byCode(form.to);
```

và thay:

```tsx
  const list = isAp
    ? filterAirports(s.query, s.panel === "from" ? form.to : form.from)
    : [];
```

bằng:

```tsx
  const list =
    isAp && airports
      ? filterAirports(
          airports,
          s.query,
          s.panel === "from" ? form.to : form.from,
        )
      : [];
  const empty = failed
    ? "Không tải được danh sách sân bay. Hãy tải lại trang."
    : !airports
      ? "Đang tải danh sách sân bay..."
      : "Không tìm thấy sân bay phù hợp. Thử gõ tên thành phố hoặc mã 3 chữ cái, ví dụ DAD.";
```

5e. Cảnh báo hành trình quốc tế. Thay:

```tsx
        {F && T && F.cc !== T.cc && (
```

bằng:

```tsx
        {F && T && F.countryCode !== T.countryCode && (
```

5f. Ô "Từ": mã hiện ngay cả khi danh sách chưa về. Thay:

```tsx
              main={F ? F.code : "Chọn sân bay"}
              sub={F ? F.city : "Điểm đi"}
              mainCls={F ? tick(form.fromPar) : "is-empty"}
```

bằng:

```tsx
              main={form.from || "Chọn sân bay"}
              sub={F?.city ?? (form.from ? "\u00a0" : "Điểm đi")}
              mainCls={form.from ? tick(form.fromPar) : "is-empty"}
```

5g. Ô "Đến", tương tự. Thay:

```tsx
              main={T ? T.code : "Chọn sân bay"}
              sub={T ? T.city : "Điểm đến"}
              mainCls={T ? tick(form.toPar) : "is-empty"}
```

bằng:

```tsx
              main={form.to || "Chọn sân bay"}
              sub={T?.city ?? (form.to ? "\u00a0" : "Điểm đến")}
              mainCls={form.to ? tick(form.toPar) : "is-empty"}
```

`"\u00a0"` (khoảng trắng không ngắt) giữ chiều cao dòng thành phố trong lúc tải, ô không bị nhảy.

5h. Truyền câu báo rỗng vào cả hai `<AirportPop>`: ở **mỗi** chỗ có dòng

```tsx
                list={list}
```

thêm ngay bên dưới:

```tsx
                empty={empty}
```

5i. Trong `function AirportPop(p: {...})`, thay:

```tsx
  list: Airport[];
  hi: number;
```

bằng:

```tsx
  list: Airport[];
  /** Câu hiện khi list rỗng: đang tải, lỗi tải hoặc không có sân bay khớp. */
  empty: string;
  hi: number;
```

thay:

```tsx
            <span className="ap-tag">{a.cc === "VN" ? "" : a.country}</span>
```

bằng:

```tsx
            <span className="ap-tag">
              {a.countryCode === "VN" ? "" : countryName(a.countryCode)}
            </span>
```

và thay:

```tsx
        {p.list.length === 0 && (
          <p className="ap-empty">
            Không tìm thấy sân bay phù hợp. Thử gõ tên thành phố hoặc mã 3 chữ
            cái, ví dụ DAD.
          </p>
        )}
```

bằng:

```tsx
        {p.list.length === 0 && <p className="ap-empty">{p.empty}</p>}
```

- [ ] **Step 6: Thẻ minh hoạ đặt chỗ lấy mã thẳng từ form**

Trong `frontend/src/components/landing/story.tsx`, thay:

```tsx
import { HOLD_MINUTES, airportByCode } from "./data";
```

bằng:

```tsx
import { HOLD_MINUTES } from "./data";
```

và thay:

```tsx
  const route = `${airportByCode(form.from)?.code ?? "HAN"} → ${airportByCode(form.to)?.code ?? "SGN"}`;
```

bằng:

```tsx
  const route = `${form.from || "HAN"} → ${form.to || "SGN"}`;
```

Nếu không đổi, chọn sân bay ngoài danh sách mẫu (VD `VCS`) thì thẻ này hiện nhầm `HAN`.

- [ ] **Step 7: Thu gọn dữ liệu mẫu**

Sau Step 5, 6, chỉ `routes.tsx` còn dùng `airportByCode` (với 7 sân bay của `ROUTES`, các trường `code`, `city`, `cc`). Thay toàn bộ `frontend/src/components/landing/data.ts` bằng:

```ts
// Dữ liệu mẫu cho các khối minh hoạ của landing (tuyến gợi ý). Ô tìm chuyến lấy sân bay thật từ GET /api/airports
// (src/lib/airports.ts).
interface RouteAirport {
  code: string;
  city: string;
  cc: string;
}

const AIRPORTS: RouteAirport[] = [
  { code: "HAN", city: "Hà Nội", cc: "VN" },
  { code: "SGN", city: "TP. Hồ Chí Minh", cc: "VN" },
  { code: "DAD", city: "Đà Nẵng", cc: "VN" },
  { code: "PQC", city: "Phú Quốc", cc: "VN" },
  { code: "HPH", city: "Hải Phòng", cc: "VN" },
  { code: "VII", city: "Vinh", cc: "VN" },
  { code: "SIN", city: "Singapore", cc: "SG" },
];

export const CABINS = [
  { id: "ECONOMY", label: "Phổ thông" },
  { id: "PREMIUM_ECONOMY", label: "Phổ thông đặc biệt" },
  { id: "BUSINESS", label: "Thương gia" },
  { id: "FIRST", label: "Hạng nhất" },
] as const;

export type CabinId = (typeof CABINS)[number]["id"];

export const ROUTES: { from: string; to: string }[] = [
  { from: "HAN", to: "SGN" },
  { from: "SGN", to: "DAD" },
  { from: "HAN", to: "PQC" },
  { from: "SGN", to: "HPH" },
  { from: "SGN", to: "VII" },
  { from: "SGN", to: "SIN" },
];

export const airportByCode = (code: string) =>
  AIRPORTS.find((a) => a.code === code) ?? null;

// Thời hạn giữ chỗ (phút). Giá trị thật là tham số hệ thống của PRD §7, đọc qua SettingsApi ở backend.
export const HOLD_MINUTES = 15;
```

- [ ] **Step 8: Chạy test để thấy nó xanh**

```bash
cd frontend && npx playwright test flight-search && npm run lint && npm run build
```

Expected: `1 passed`; lint không có lỗi; build xanh. Mở http://localhost:3000, bấm ô "Đến", gõ `sing`: thấy `SIN Singapore Changi` với nhãn quốc gia `Singapore`; gõ `han quoc`: thấy `ICN Seoul`.

- [ ] **Step 9: Commit**

```bash
git add frontend/playwright.config.ts frontend/e2e/flight-search.spec.ts frontend/src/lib/airports.ts frontend/src/components/landing/search-dock.tsx frontend/src/components/landing/story.tsx frontend/src/components/landing/data.ts
git commit -m "feat(frontend): ô tìm chuyến lấy sân bay từ GET /airports (C-01)"
```

---

### Task 2: Lọc, sắp xếp và BR-04 ở client

**Files:**

- Create: `frontend/src/app/flights/itineraries.ts`
- Test: `frontend/e2e/itineraries.spec.ts`

**Interfaces:**

- Consumes: `Schemas["Itinerary"]`, `["FareOption"]`, `["AirportTime"]` (Plan 07). Lưu ý: `Segment.layoverMinutesAfter` có kiểu `number` nhưng là `null` ở chặng cuối (Plan 07); chỉ đọc nó ở các chặng trước chặng cuối.
- Produces (Task 3 dùng đúng các tên này, tất cả xuất từ `src/app/flights/itineraries.ts`):
  - Kiểu: `Itinerary`, `FareOption`, `AirportTime`, `DayPart` (`"night" | "morning" | "afternoon" | "evening"`), `SortKey` (`"price" | "departure" | "duration"`), `Filters { airlines: string[]; stops: number[]; dayParts: DayPart[]; maxPrice: number | null }`.
  - Hằng: `NO_FILTERS: Filters`, `DAY_PARTS` (`{id, label, range, from}`), `SORTS` (`{id, label}`).
  - `first(it)`, `last(it)`: chặng đầu, chặng cuối.
  - `itineraryKey(it): string`: id các chặng nối bằng dấu phẩy.
  - `cheapest(it): number`: `totalPrice` nhỏ nhất của các gói.
  - `hhmm(t: AirportTime): string`: `"08:10"`.
  - `dayShift(from: AirportTime, to: AirportTime): number`: số ngày lệch giữa hai ngày địa phương.
  - `duration(minutes): string`: `"2 giờ 10 phút"`, `"2 giờ"`, `"45 phút"`.
  - `vnd(amount): string`: `"990.000 đ"`.
  - `departsAfter(outbound) → (ret) => boolean`: BR-04.
  - `dayPart(t): DayPart`, `applyFilters(list, filters): Itinerary[]`, `sortItineraries(list, key): Itinerary[]` (trả mảng mới, không sửa mảng vào).

- [ ] **Step 1: Viết test**

Tạo `frontend/e2e/itineraries.spec.ts`. Test không dùng fixture `page` nên Playwright không mở trình duyệt; file nằm trong `e2e/` vì đó là `testDir`.

```ts
import { expect, test } from "@playwright/test";
import {
  NO_FILTERS,
  applyFilters,
  dayPart,
  dayShift,
  departsAfter,
  duration,
  sortItineraries,
  type Itinerary,
} from "../src/app/flights/itineraries";

// Unit test cho logic lọc, sắp xếp của C-02: không mở trình duyệt, không cần backend.

const at = (time: string, airport = "HAN") => ({
  airport,
  city: airport,
  time,
  timezone: "Asia/Ho_Chi_Minh",
});

/** Hành trình một chặng, giá rẻ nhất `price`. */
function trip(
  dep: string,
  arr: string,
  price: number,
  minutes = 120,
  airline = "VN",
): Itinerary {
  return {
    stops: 0,
    totalDurationMinutes: minutes,
    segments: [
      {
        flightId: price,
        flightNumber: `${airline}${price}`,
        airline: { code: airline, name: airline },
        departure: at(dep),
        arrival: at(arr, "SGN"),
        durationMinutes: minutes,
        aircraftModel: "A321",
        layoverMinutesAfter: 0,
      },
    ],
    fareOptions: [price + 500, price].map((totalPrice) => ({
      fareFamilyId: totalPrice,
      name: "Eco",
      cabinClass: "ECONOMY",
      carryOnKg: 7,
      checkedBaggageKg: 0,
      refundable: false,
      refundFee: 0,
      changeable: true,
      changeFee: 0,
      adultPrice: totalPrice,
      totalPrice,
      seatsLeft: 9,
    })),
  };
}

test("BR-04: chiều về cất cánh đúng giờ hạ cánh của chiều đi thì không được chọn", () => {
  const out = trip("2026-12-01T10:00:00+07:00", "2026-12-01T12:10:00+07:00", 1);
  const ok = departsAfter(out);
  expect(
    ok(trip("2026-12-01T12:11:00+07:00", "2026-12-01T14:00:00+07:00", 2)),
  ).toBe(true);
  expect(
    ok(trip("2026-12-01T12:10:00+07:00", "2026-12-01T14:00:00+07:00", 3)),
  ).toBe(false);
  // Khác múi giờ: 13:05 giờ Singapore là 12:05 giờ Việt Nam, vẫn trước 12:10.
  expect(
    ok(trip("2026-12-01T13:05:00+08:00", "2026-12-01T15:00:00+08:00", 4)),
  ).toBe(false);
});

test("khung giờ cất cánh theo giờ địa phương, 06:00 thuộc buổi sáng", () => {
  expect(dayPart(at("2026-12-01T05:59:00+07:00"))).toBe("night");
  expect(dayPart(at("2026-12-01T06:00:00+07:00"))).toBe("morning");
  expect(dayPart(at("2026-12-01T12:00:00+08:00"))).toBe("afternoon");
  expect(dayPart(at("2026-12-01T23:30:00+07:00"))).toBe("evening");
});

test("hạ cánh hôm sau thì lệch 1 ngày, kể cả qua cuối tháng", () => {
  expect(
    dayShift(at("2026-11-30T23:30:00+07:00"), at("2026-12-01T06:15:00+09:00")),
  ).toBe(1);
  expect(
    dayShift(at("2026-12-01T09:30:00+07:00"), at("2026-12-01T12:30:00+08:00")),
  ).toBe(0);
  expect(duration(45)).toBe("45 phút");
  expect(duration(120)).toBe("2 giờ");
  expect(duration(330)).toBe("5 giờ 30 phút");
});

test("lọc kết hợp các nhóm, sắp xếp bằng nhau thì rẻ hơn đứng trước", () => {
  const a = trip(
    "2026-12-01T06:00:00+07:00",
    "2026-12-01T08:10:00+07:00",
    1_290_000,
    130,
  );
  const b = trip(
    "2026-12-01T08:00:00+07:00",
    "2026-12-01T10:10:00+07:00",
    990_000,
    130,
    "VJ",
  );
  const c = trip(
    "2026-12-01T19:00:00+07:00",
    "2026-12-01T21:10:00+07:00",
    1_190_000,
    130,
    "QH",
  );
  const all = [a, b, c];

  expect(sortItineraries(all, "price")).toEqual([b, c, a]);
  expect(sortItineraries(all, "departure")).toEqual([a, b, c]);
  expect(sortItineraries(all, "duration")).toEqual([b, c, a]);

  expect(applyFilters(all, NO_FILTERS)).toEqual(all);
  expect(applyFilters(all, { ...NO_FILTERS, airlines: ["VN", "QH"] })).toEqual([
    a,
    c,
  ]);
  expect(
    applyFilters(all, {
      ...NO_FILTERS,
      airlines: ["VN", "QH"],
      dayParts: ["evening"],
    }),
  ).toEqual([c]);
  // Giá tối đa so với gói rẻ nhất, tính cả biên.
  expect(applyFilters(all, { ...NO_FILTERS, maxPrice: 1_190_000 })).toEqual([
    b,
    c,
  ]);
});
```

- [ ] **Step 2: Chạy test để thấy nó đỏ**

```bash
cd frontend && npx playwright test itineraries
```

Expected: lỗi nạp file, `Cannot find module '../src/app/flights/itineraries'`.

- [ ] **Step 3: Viết các hàm**

Tạo `frontend/src/app/flights/itineraries.ts`:

```ts
// Lọc, sắp xếp và định dạng kết quả tìm chuyến ở client (FR-11, FR-12). Không gọi API, không đọc giờ hiện tại.
import type { Schemas } from "@/lib/api";

export type Itinerary = Schemas["Itinerary"];
export type FareOption = Schemas["FareOption"];
export type AirportTime = Schemas["AirportTime"];

export const first = (it: Itinerary) => it.segments[0];
export const last = (it: Itinerary) => it.segments[it.segments.length - 1];

/** Khoá ổn định của hành trình: id các chặng, VD "101,205". Dùng cho key React và tham số `out` của URL. */
export const itineraryKey = (it: Itinerary) =>
  it.segments.map((s) => s.flightId).join(",");

/** Tổng giá rẻ nhất cho đúng số khách đã nhập. */
export const cheapest = (it: Itinerary) =>
  Math.min(...it.fareOptions.map((f) => f.totalPrice));

/**
 * Giờ địa phương của sân bay, VD "08:10". API trả giờ đã theo múi giờ sân bay kèm offset
 * ("2026-12-01T08:10:00+07:00"), nên đọc thẳng từ chuỗi; không qua Date nên không dính múi giờ trình duyệt.
 */
export const hhmm = (t: AirportTime) => t.time.slice(11, 16);

/** Số ngày lệch giữa hai ngày địa phương, VD cất cánh 23:30 và hạ cánh 06:15 hôm sau → 1. */
export const dayShift = (from: AirportTime, to: AirportTime) =>
  Math.round(
    (Date.parse(to.time.slice(0, 10)) - Date.parse(from.time.slice(0, 10))) /
      86_400_000,
  );

export function duration(minutes: number) {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (!h) return `${m} phút`;
  return m ? `${h} giờ ${m} phút` : `${h} giờ`;
}

const money = new Intl.NumberFormat("vi-VN");
/** VD "990.000 đ"; khoảng trắng không ngắt để "đ" không rớt xuống dòng. */
export const vnd = (amount: number) => `${money.format(amount)}\u00a0đ`;

/** BR-04: chặng đầu chiều về phải cất cánh sau giờ hạ cánh chặng cuối chiều đi. Chuỗi có offset nên so thời điểm được. */
export const departsAfter = (outbound: Itinerary) => (ret: Itinerary) =>
  Date.parse(first(ret).departure.time) >
  Date.parse(last(outbound).arrival.time);

export const DAY_PARTS = [
  { id: "night", label: "Đêm, sáng sớm", range: "00:00 đến 06:00", from: 0 },
  { id: "morning", label: "Buổi sáng", range: "06:00 đến 12:00", from: 6 },
  { id: "afternoon", label: "Buổi chiều", range: "12:00 đến 18:00", from: 12 },
  { id: "evening", label: "Buổi tối", range: "18:00 đến 24:00", from: 18 },
] as const;
export type DayPart = (typeof DAY_PARTS)[number]["id"];

/** Khung giờ cất cánh theo giờ địa phương của sân bay đi. */
export function dayPart(t: AirportTime): DayPart {
  const hour = Number(hhmm(t).slice(0, 2));
  return DAY_PARTS.findLast((p) => hour >= p.from)!.id;
}

export interface Filters {
  airlines: string[];
  stops: number[];
  dayParts: DayPart[];
  /** Giá tối đa của gói rẻ nhất; null là không giới hạn. */
  maxPrice: number | null;
}

export const NO_FILTERS: Filters = {
  airlines: [],
  stops: [],
  dayParts: [],
  maxPrice: null,
};

/** Trong một nhóm: khớp một giá trị bất kỳ đã chọn (nhóm chưa chọn gì thì bỏ qua). Giữa các nhóm: phải khớp tất cả. */
export const applyFilters = (list: Itinerary[], f: Filters) =>
  list.filter(
    (it) =>
      (!f.airlines.length || f.airlines.includes(first(it).airline.code)) &&
      (!f.stops.length || f.stops.includes(it.stops)) &&
      (!f.dayParts.length ||
        f.dayParts.includes(dayPart(first(it).departure))) &&
      (f.maxPrice === null || cheapest(it) <= f.maxPrice),
  );

export const SORTS = [
  { id: "price", label: "Giá thấp nhất" },
  { id: "departure", label: "Cất cánh sớm nhất" },
  { id: "duration", label: "Bay nhanh nhất" },
] as const;
export type SortKey = (typeof SORTS)[number]["id"];

const SORT_BY: Record<SortKey, (it: Itinerary) => number> = {
  price: cheapest,
  departure: (it) => Date.parse(first(it).departure.time),
  duration: (it) => it.totalDurationMinutes,
};

/** Bằng nhau thì hành trình rẻ hơn đứng trước. */
export const sortItineraries = (list: Itinerary[], key: SortKey) =>
  [...list].sort(
    (a, b) => SORT_BY[key](a) - SORT_BY[key](b) || cheapest(a) - cheapest(b),
  );
```

`findLast` có từ ES2023; `tsconfig.json` dùng `lib: ["esnext"]` nên kiểu có sẵn, Chrome, Edge, Firefox, Safari bản hiện hành đều chạy được.

- [ ] **Step 4: Chạy test để thấy nó xanh**

```bash
cd frontend && npx playwright test itineraries && npm run lint
```

Expected: `4 passed`; lint không có lỗi.

- [ ] **Step 5: Commit**

```bash
git add frontend/e2e/itineraries.spec.ts frontend/src/app/flights/itineraries.ts
git commit -m "feat(frontend): lọc, sắp xếp và BR-04 cho kết quả tìm chuyến"
```

---

### Task 3: Màn kết quả tìm kiếm (C-02), khứ hồi 2 bước

**Files:**

- Create: `frontend/src/lib/selection.ts`, `frontend/src/app/flights/itinerary-card.tsx`, `frontend/src/app/flights/result-list.tsx`, `frontend/src/app/flights/flight-results.tsx`, `frontend/src/app/checkout/page.tsx`
- Modify: `frontend/src/app/flights/page.tsx` (viết lại), `frontend/src/proxy.ts:17-23`, `frontend/e2e/smoke.spec.ts:13`, `CLAUDE.md` (đoạn Status và Commands), `docs/superpowers/plans/2026-10-09-00-roadmap.md`
- Test: `frontend/e2e/flight-search.spec.ts` (viết lại toàn bộ)

**Interfaces:**

- Consumes: các hàm và kiểu của Task 2 (danh sách ở Task 2 "Produces"); `apiFetch`, `ApiError(status, code, detail, errors)`, `Schemas` (Plan 03); `Brand` của `src/components/auth-shell.tsx`; `Notice`, `pillDark`, `pillLine`, `textLink` của `src/components/form.tsx` (Plan 04); `CABINS` của `landing/data.ts`; `parseIso`, `weekday`, `ddmm` của `landing/search-context.tsx`.
- Produces (Plan 13 dùng):
  - `src/lib/selection.ts`: `interface Journey { direction: "OUTBOUND" | "RETURN"; flightIds: number[]; fareFamilyId: number; itinerary: Schemas["Itinerary"]; fare: Schemas["FareOption"] }`, `interface Selection { adults; children; infants: number; journeys: Journey[] }`, `SELECTION_KEY = "skyline.selection"`, `saveSelection(selection)`. Khứ hồi thì `journeys[0]` là `OUTBOUND`, `journeys[1]` là `RETURN`.
  - Route `/checkout` (trang tạm C-07, cần đăng nhập qua `proxy.ts`).
  - URL bước 2 của C-02: thêm `out`, `outFare` (xem mục "URL của C-02").

- [ ] **Step 1: Viết test**

Thay toàn bộ `frontend/e2e/flight-search.spec.ts` bằng:

```ts
import { expect, test, type Page } from "@playwright/test";

// C-01 và C-02 trên backend thật (profile dev) với seed V106: mỗi ngày có VJ123 08:00, QH201 10:00, VN213 06:00
// và nối chuyến VN1233 + VN1826 cho HAN→SGN; chiều SGN→HAN có VN1825 + VN1232 11:00, QH202 15:00, VN220 17:00,
// VJ122 19:00. Seed sinh 60 ngày tính từ ngày chạy migration (lộ trình §8).

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

const cards = (page: Page) =>
  page
    .getByRole("article")
    .evaluateAll((els) => els.map((e) => e.getAttribute("aria-label")));

test("ô tìm chuyến gợi ý sân bay từ GET /airports rồi mở C-02", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("button", { name: /^Đến SGN/ }).click();
  // Côn Đảo (VCS) chỉ có trong seed V100, không có trong dữ liệu mẫu cũ của landing.
  await page.getByRole("textbox", { name: "Bay đến" }).fill("con dao");
  await page.getByRole("button", { name: /^VCS/ }).click();
  await expect(
    page.getByRole("button", { name: /^Đến VCS Côn Đảo/ }),
  ).toBeVisible();

  await page
    .getByRole("search", { name: "Tìm chuyến bay" })
    .getByRole("button", { name: "Tìm chuyến bay" })
    .click();
  await expect(page).toHaveURL(/\/flights\?from=HAN&to=VCS&date=/);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Chọn chuyến bay.",
  );
  await expect(page.getByText("Không có chuyến bay phù hợp.")).toBeVisible();
});

test("khứ hồi 2 bước, lọc và sắp xếp ở client, BR-04, rồi sang đăng nhập", async ({
  page,
}) => {
  const d = day(1);
  await page.goto(search({ from: "HAN", to: "SGN", date: d, returnDate: d }));
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Chọn chiều đi.",
  );

  // Mặc định xếp theo giá rẻ nhất.
  await expect
    .poll(() => cards(page))
    .toEqual([
      "Vietjet Air VJ123",
      "Bamboo Airways QH201",
      "Vietnam Airlines VN213",
      "Vietnam Airlines VN1233 + VN1826",
    ]);
  await page.getByLabel("Sắp xếp").selectOption({ label: "Cất cánh sớm nhất" });
  expect(await cards(page)).toEqual([
    "Vietnam Airlines VN213",
    "Vietjet Air VJ123",
    "Vietnam Airlines VN1233 + VN1826",
    "Bamboo Airways QH201",
  ]);
  // Ba chuyến thẳng cùng 2 giờ 10 phút: bằng nhau thì rẻ hơn đứng trước.
  await page.getByLabel("Sắp xếp").selectOption({ label: "Bay nhanh nhất" });
  expect(await cards(page)).toEqual([
    "Vietjet Air VJ123",
    "Bamboo Airways QH201",
    "Vietnam Airlines VN213",
    "Vietnam Airlines VN1233 + VN1826",
  ]);

  await page.getByRole("checkbox", { name: "Bay thẳng" }).check();
  await page.getByRole("checkbox", { name: "Vietnam Airlines" }).check();
  expect(await cards(page)).toEqual(["Vietnam Airlines VN213"]);
  await expect(page.getByText("1 trên 4 hành trình")).toBeVisible();
  await page.getByRole("button", { name: "Xoá bộ lọc" }).click();
  await expect(page.getByRole("article")).toHaveCount(4);

  // Bước 1: QH201 hạ cánh 12:10.
  const qh = page.getByRole("article", { name: "Bamboo Airways QH201" });
  await qh.getByRole("button", { name: "Chọn", exact: true }).click();
  await qh.getByRole("button", { name: "Chọn gói Economy Saver" }).click();
  await expect(page).toHaveURL(/&out=\d+&outFare=\d+$/);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Chọn chiều về.",
  );
  await expect(
    page.getByRole("region", { name: "Chiều đi đã chọn" }),
  ).toContainText("QH201");

  // BR-04: VN1825 + VN1232 cất cánh 11:00, trước 12:10, nên bị ẩn. Bộ lọc của bước 1 không mang sang.
  await expect
    .poll(() => cards(page))
    .toEqual([
      "Vietjet Air VJ122",
      "Bamboo Airways QH202",
      "Vietnam Airlines VN220",
    ]);
  await expect(
    page.getByText(
      "Đã ẩn 1 hành trình cất cánh trước giờ hạ cánh của chiều đi.",
    ),
  ).toBeVisible();

  // Bước nằm trong URL: Back về bước 1, Forward và tải lại vẫn ở bước 2.
  await page.goBack();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Chọn chiều đi.",
  );
  await page.goForward();
  await page.reload();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Chọn chiều về.",
  );

  const vj = page.getByRole("article", { name: "Vietjet Air VJ122" });
  await vj.getByRole("button", { name: "Chọn", exact: true }).click();
  await vj.getByRole("button", { name: "Chọn gói Eco" }).click();
  // Chưa đăng nhập: /checkout chuyển về đăng nhập, lựa chọn đã nằm trong sessionStorage.
  await expect(page).toHaveURL("/login?next=%2Fcheckout");
  const selection = await page.evaluate(() =>
    JSON.parse(sessionStorage.getItem("skyline.selection") ?? "null"),
  );
  expect(selection.adults).toBe(1);
  expect(
    selection.journeys.map(
      (j: {
        direction: string;
        itinerary: { segments: { flightNumber: string }[] };
      }) =>
        `${j.direction} ${j.itinerary.segments.map((s) => s.flightNumber).join("+")}`,
    ),
  ).toEqual(["OUTBOUND QH201", "RETURN VJ122"]);
});

test.describe("trình duyệt ở múi giờ khác", () => {
  test.use({ timezoneId: "America/New_York" });

  test("giờ hiện theo giờ địa phương của sân bay", async ({ page }) => {
    await page.goto(search({ from: "SGN", to: "SIN", date: day(1) }));
    // VN651 cất cánh 09:30 giờ Việt Nam, bay 120 phút, hạ cánh 12:30 giờ Singapore.
    const vn = page.getByRole("article", { name: "Vietnam Airlines VN651" });
    await expect(vn).toContainText("09:30");
    await expect(vn).toContainText("12:30");
  });
});

test("URL sai hoặc chiều đi không còn thì báo rõ, không vỡ trang", async ({
  page,
}) => {
  const d = day(1);
  await page.goto(
    search({
      from: "HAN",
      to: "SGN",
      date: d,
      returnDate: d,
      out: "1",
      outFare: "1",
    }),
  );
  await expect(page.getByText("Chiều đi đã chọn không còn bán")).toBeVisible();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Chọn chiều đi.",
  );

  await page.goto(search({ from: "HAN", to: "SGN", date: d, infants: "2" }));
  await expect(
    page.getByText("Số em bé không được nhiều hơn số người lớn"),
  ).toBeVisible();

  await page.goto(
    search({ from: "HAN", to: "SGN", date: day(3), returnDate: d }),
  );
  await expect(
    page.getByText("Ngày về không được trước ngày đi."),
  ).toBeVisible();
});
```

Số liệu lấy từ seed `V106` (BACKEND_SCHEMA §8.6): với 1 người lớn, gói rẻ nhất của VJ123 là 990.000, QH201 1.190.000, VN213 1.290.000, VN1233 + VN1826 là 1.490.000 + 790.000 = 2.280.000. Nối chuyến HAN→PQC→SGN: VN1233 hạ cánh PQC 11:10, VN1826 cất cánh 13:30 (nối 140 phút), tổng 330 phút. Chiều về SGN→HAN cùng ngày: VN1825 + VN1232 cất cánh 11:00, nên bị ẩn khi chiều đi là QH201 (hạ cánh 12:10).

- [ ] **Step 2: Chạy test để thấy nó đỏ**

```bash
cd frontend && npx playwright test flight-search
```

Expected: `4 failed`. Test C-01 và test khứ hồi dừng ở tiêu đề h1 (`Expected: "Chọn chuyến bay."` hoặc `"Chọn chiều đi."`, `Received: "Kết quả tìm kiếm"` của trang tạm); test múi giờ không thấy `article`; test URL sai không thấy câu "Chiều đi đã chọn không còn bán".

- [ ] **Step 3: Kiểu và hàm lưu lựa chọn**

Tạo `frontend/src/lib/selection.ts`:

```ts
import type { Schemas } from "./api";

/**
 * Lựa chọn hành trình ở C-02, lưu ở sessionStorage (TDD §7) để C-07 (Plan 13) đọc lại. `direction`, `flightIds`,
 * `fareFamilyId` đúng dạng `journeys` của POST /bookings/quote và /bookings (TDD §8.8); `itinerary`, `fare` để hiển thị.
 */
export interface Journey {
  direction: "OUTBOUND" | "RETURN";
  flightIds: number[];
  fareFamilyId: number;
  itinerary: Schemas["Itinerary"];
  fare: Schemas["FareOption"];
}

export interface Selection {
  adults: number;
  children: number;
  infants: number;
  journeys: Journey[];
}

export const SELECTION_KEY = "skyline.selection";

export function saveSelection(selection: Selection) {
  sessionStorage.setItem(SELECTION_KEY, JSON.stringify(selection));
}
```

- [ ] **Step 4: Trang tạm `/checkout` và quyền**

Tạo `frontend/src/app/checkout/page.tsx`:

```tsx
import type { Metadata } from "next";
import PlaceholderPage from "@/components/placeholder-page";

export const metadata: Metadata = { title: "Đặt vé | SkyLine" };

/** C-07 (APP_FLOW §2.1), Plan 13 làm. Lựa chọn hành trình đã nằm trong sessionStorage (src/lib/selection.ts). */
export default function Page() {
  return <PlaceholderPage code="C-07" title="Đặt vé" />;
}
```

Trong `frontend/src/proxy.ts`, thay:

```ts
    "/account/:path*",
```

bằng:

```ts
    "/account/:path*",
    "/checkout/:path*",
```

Trong `frontend/e2e/smoke.spec.ts`, thay:

```ts
const PROTECTED = ["/account", "/bookings"];
```

bằng:

```ts
const PROTECTED = ["/account", "/bookings", "/checkout"];
```

- [ ] **Step 5: Thẻ hành trình**

Tạo `frontend/src/app/flights/itinerary-card.tsx`. Dòng tóm tắt: hãng và số hiệu, giờ cất cánh và hạ cánh (kèm `+1 ngày` khi hạ cánh hôm sau), tổng thời lượng, số điểm dừng, giá rẻ nhất cho đúng số khách. Bấm "Chọn" mở chi tiết từng chặng (thành phố, loại máy bay, thời lượng, thời gian nối) và các gói giá (FR-11: hành lý, hoàn, đổi, giá người lớn, tổng giá, số ghế còn). `aria-label` của `article` là `"<hãng> <số hiệu>"`, E2E tìm thẻ theo tên này.

```tsx
"use client";

import { pillDark, pillLine } from "@/components/form";
import {
  cheapest,
  dayShift,
  duration,
  first,
  hhmm,
  itineraryKey,
  last,
  vnd,
  type AirportTime,
  type FareOption,
  type Itinerary,
} from "./itineraries";

function Time({ t, shift = 0 }: { t: AirportTime; shift?: number }) {
  return (
    <p className="grid">
      <span className="text-subheading font-bold">
        {hhmm(t)}
        {shift > 0 && (
          <span className="ml-4 align-top text-caption">+{shift} ngày</span>
        )}
      </span>
      <span className="text-caption text-muted">{t.airport}</span>
    </p>
  );
}

function fareRules(f: FareOption) {
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

/** Một hành trình (FR-11): tóm tắt trên một dòng; bấm "Chọn" mở chi tiết chặng và các gói giá. */
export default function ItineraryCard({
  itinerary,
  passengers,
  open,
  onToggle,
  onChoose,
}: {
  itinerary: Itinerary;
  passengers: number;
  open: boolean;
  onToggle: () => void;
  onChoose: (fare: FareOption) => void;
}) {
  const segs = itinerary.segments;
  const a = first(itinerary);
  const z = last(itinerary);
  const numbers = segs.map((s) => s.flightNumber).join(" + ");
  const panelId = `fares-${itineraryKey(itinerary)}`;

  return (
    <article
      aria-label={`${a.airline.name} ${numbers}`}
      className="border border-line bg-paper"
    >
      <div className="grid gap-16 p-22 md:grid-cols-[minmax(0,1fr)_auto] md:items-center md:gap-38">
        <div className="grid gap-13">
          <p className="text-caption text-muted">
            <span className="font-bold text-ink">{a.airline.name}</span>
            {" · "}
            {numbers}
          </p>
          <div className="flex items-center gap-16">
            <Time t={a.departure} />
            <div className="grid flex-1 justify-items-center gap-4 text-caption text-muted">
              <span>{duration(itinerary.totalDurationMinutes)}</span>
              <span aria-hidden="true" className="h-1 w-full bg-line-strong" />
              <span>
                {itinerary.stops === 0
                  ? "Bay thẳng"
                  : `1 điểm dừng, ${a.arrival.airport}`}
              </span>
            </div>
            <Time t={z.arrival} shift={dayShift(a.departure, z.arrival)} />
          </div>
        </div>
        <div className="flex items-center justify-between gap-16 md:grid md:justify-items-end">
          <p className="grid md:justify-items-end">
            <span className="text-caption text-muted">Từ</span>
            <span className="text-subheading font-bold">
              {vnd(cheapest(itinerary))}
            </span>
            <span className="text-caption text-muted">
              cho {passengers} khách
            </span>
          </p>
          <button
            type="button"
            className={open ? pillLine : pillDark}
            aria-expanded={open}
            aria-controls={panelId}
            onClick={onToggle}
          >
            {open ? "Thu gọn" : "Chọn"}
          </button>
        </div>
      </div>

      {open && (
        <div id={panelId} className="grid gap-22 border-t border-line p-22">
          <ol className="grid gap-16">
            {segs.map((s, i) => (
              <li key={s.flightId} className="grid gap-4">
                <p className="font-bold">
                  {hhmm(s.departure)} {s.departure.city} ({s.departure.airport})
                  {" → "}
                  {hhmm(s.arrival)} {s.arrival.city} ({s.arrival.airport})
                </p>
                <p className="text-caption text-muted">
                  {s.airline.name} {s.flightNumber} · {s.aircraftModel} ·{" "}
                  {duration(s.durationMinutes)}
                </p>
                {i < segs.length - 1 && (
                  <p className="text-caption">
                    Nối chuyến tại {s.arrival.city}, chờ{" "}
                    {duration(s.layoverMinutesAfter)}
                  </p>
                )}
              </li>
            ))}
          </ol>

          <ul className="grid gap-16 sm:grid-cols-2 lg:grid-cols-3">
            {itinerary.fareOptions.map((f) => (
              <li
                key={f.fareFamilyId}
                className="grid content-start gap-13 border border-line p-22"
              >
                <p className="text-subheading font-bold">{f.name}</p>
                <ul className="grid gap-4 text-caption">
                  {fareRules(f).map((r) => (
                    <li key={r}>{r}</li>
                  ))}
                </ul>
                <p className="grid">
                  <span className="text-caption text-muted">
                    Tổng cho {passengers} khách
                  </span>
                  <span className="text-subheading font-bold">
                    {vnd(f.totalPrice)}
                  </span>
                  <span className="text-caption text-muted">
                    Người lớn {vnd(f.adultPrice)}, còn {f.seatsLeft} ghế
                  </span>
                </p>
                <button
                  type="button"
                  className={pillDark}
                  aria-label={`Chọn gói ${f.name}`}
                  onClick={() => onChoose(f)}
                >
                  Chọn gói này
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </article>
  );
}
```

- [ ] **Step 6: Danh sách có bộ lọc và sắp xếp**

Tạo `frontend/src/app/flights/result-list.tsx`. Từ 1024px (`lg`) bộ lọc là cột trái 280px; nhỏ hơn thì ẩn sau nút "Bộ lọc". Danh sách hãng và số điểm dừng chỉ gồm giá trị có trong kết quả; thanh giá chỉ hiện khi các hành trình có giá khác nhau. `step="any"` để kéo hết về phải đúng bằng giá cao nhất (bước 10.000 có thể không chạm tới giá cao nhất và lỡ lọc mất hành trình đắt nhất); giá trị bằng giá cao nhất nghĩa là bỏ lọc giá.

```tsx
"use client";

import { useState, type ReactNode } from "react";
import { pillLine, textLink } from "@/components/form";
import ItineraryCard from "./itinerary-card";
import {
  DAY_PARTS,
  NO_FILTERS,
  SORTS,
  applyFilters,
  cheapest,
  first,
  itineraryKey,
  sortItineraries,
  vnd,
  type FareOption,
  type Filters,
  type Itinerary,
  type SortKey,
} from "./itineraries";

const box =
  "size-18 shrink-0 accent-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink";

function toggle<T>(list: T[], value: T) {
  return list.includes(value)
    ? list.filter((v) => v !== value)
    : [...list, value];
}

function Group({ legend, children }: { legend: string; children: ReactNode }) {
  return (
    <fieldset className="grid gap-11 border-t border-line pt-16">
      <legend className="float-left mb-11 text-caption font-bold">
        {legend}
      </legend>
      {children}
    </fieldset>
  );
}

function Check({
  label,
  hint,
  checked,
  onChange,
}: {
  label: string;
  hint?: string;
  checked: boolean;
  onChange: () => void;
}) {
  return (
    <label className="flex cursor-pointer items-start gap-11 text-caption">
      <input
        type="checkbox"
        className={`${box} mt-2`}
        checked={checked}
        onChange={onChange}
      />
      <span className="grid">
        {label}
        {hint && <span className="text-muted">{hint}</span>}
      </span>
    </label>
  );
}

/**
 * Danh sách hành trình của một chiều, lọc và sắp xếp ở client (FR-11). Bộ lọc theo hãng, số điểm dừng, khung giờ
 * cất cánh, giá tối đa; sắp xếp theo giá, giờ cất cánh, tổng thời lượng.
 */
export default function ResultList({
  itineraries,
  passengers,
  note,
  onChoose,
}: {
  itineraries: Itinerary[];
  passengers: number;
  /** Dòng ghi chú trên danh sách, VD số chuyến chiều về bị ẩn theo BR-04. */
  note?: string;
  onChoose: (itinerary: Itinerary, fare: FareOption) => void;
}) {
  const [filters, setFilters] = useState<Filters>(NO_FILTERS);
  const [sort, setSort] = useState<SortKey>("price");
  const [open, setOpen] = useState<string | null>(null);
  const [showFilters, setShowFilters] = useState(false);

  const shown = sortItineraries(applyFilters(itineraries, filters), sort);
  const airlines = [
    ...new Map(
      itineraries.map((it) => [first(it).airline.code, first(it).airline]),
    ).values(),
  ].sort((x, y) => x.name.localeCompare(y.name, "vi"));
  const stops = [...new Set(itineraries.map((it) => it.stops))].sort(
    (x, y) => x - y,
  );
  const prices = itineraries.map(cheapest);
  const minPrice = Math.min(...prices);
  const maxPrice = Math.max(...prices);
  const set = (patch: Partial<Filters>) =>
    setFilters((f) => ({ ...f, ...patch }));
  const filtered =
    filters.airlines.length > 0 ||
    filters.stops.length > 0 ||
    filters.dayParts.length > 0 ||
    filters.maxPrice !== null;

  if (itineraries.length === 0) {
    return (
      <div className="grid gap-13 border border-line p-22">
        <p className="text-subheading font-bold">
          Không có chuyến bay phù hợp.
        </p>
        <p className="text-muted">
          {note ?? "Thử chọn ngày khác, hạng ghế khác hoặc ít khách hơn."}
        </p>
      </div>
    );
  }

  return (
    <div className="grid gap-22 lg:grid-cols-[280px_minmax(0,1fr)] lg:items-start lg:gap-38">
      <button
        type="button"
        className={`${pillLine} justify-self-start lg:hidden`}
        aria-expanded={showFilters}
        aria-controls="bo-loc"
        onClick={() => setShowFilters((v) => !v)}
      >
        {showFilters ? "Ẩn bộ lọc" : "Bộ lọc"}
      </button>

      <aside
        id="bo-loc"
        aria-label="Bộ lọc"
        className={`${showFilters ? "grid" : "hidden"} gap-22 lg:grid`}
      >
        <Group legend="Hãng bay">
          {airlines.map((a) => (
            <Check
              key={a.code}
              label={a.name}
              checked={filters.airlines.includes(a.code)}
              onChange={() =>
                set({ airlines: toggle(filters.airlines, a.code) })
              }
            />
          ))}
        </Group>
        <Group legend="Số điểm dừng">
          {stops.map((n) => (
            <Check
              key={n}
              label={n === 0 ? "Bay thẳng" : `${n} điểm dừng`}
              checked={filters.stops.includes(n)}
              onChange={() => set({ stops: toggle(filters.stops, n) })}
            />
          ))}
        </Group>
        <Group legend="Giờ cất cánh">
          {DAY_PARTS.map((p) => (
            <Check
              key={p.id}
              label={p.label}
              hint={p.range}
              checked={filters.dayParts.includes(p.id)}
              onChange={() => set({ dayParts: toggle(filters.dayParts, p.id) })}
            />
          ))}
        </Group>
        {minPrice < maxPrice && (
          <Group legend="Giá tối đa">
            <label className="grid gap-8 text-caption">
              <span>{vnd(filters.maxPrice ?? maxPrice)}</span>
              <input
                type="range"
                className="accent-ink"
                min={minPrice}
                max={maxPrice}
                step="any"
                value={filters.maxPrice ?? maxPrice}
                aria-label="Giá tối đa"
                aria-valuetext={vnd(filters.maxPrice ?? maxPrice)}
                onChange={(e) => {
                  const v = Number(e.target.value);
                  set({ maxPrice: v >= maxPrice ? null : Math.round(v) });
                }}
              />
            </label>
          </Group>
        )}
        {filtered && (
          <button
            type="button"
            className={`${textLink} justify-self-start text-caption`}
            onClick={() => setFilters(NO_FILTERS)}
          >
            Xoá bộ lọc
          </button>
        )}
      </aside>

      <section aria-label="Kết quả" className="grid gap-16">
        <div className="flex flex-wrap items-center justify-between gap-16">
          <p className="text-caption text-muted" aria-live="polite">
            {shown.length} trên {itineraries.length} hành trình
          </p>
          <label className="flex items-center gap-11 text-caption font-bold">
            Sắp xếp
            <select
              className="border border-line-strong bg-paper px-13 py-8 font-normal text-ink"
              value={sort}
              onChange={(e) => setSort(e.target.value as SortKey)}
            >
              {SORTS.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.label}
                </option>
              ))}
            </select>
          </label>
        </div>
        {note && <p className="text-caption text-muted">{note}</p>}
        {shown.map((it) => {
          const key = itineraryKey(it);
          return (
            <ItineraryCard
              key={key}
              itinerary={it}
              passengers={passengers}
              open={open === key}
              onToggle={() => setOpen(open === key ? null : key)}
              onChoose={(fare) => onChoose(it, fare)}
            />
          );
        })}
        {shown.length === 0 && (
          <div className="grid gap-13 border border-line p-22">
            <p className="font-bold">Không có hành trình khớp bộ lọc.</p>
            <button
              type="button"
              className={`${textLink} justify-self-start text-caption`}
              onClick={() => setFilters(NO_FILTERS)}
            >
              Xoá bộ lọc
            </button>
          </div>
        )}
      </section>
    </div>
  );
}
```

- [ ] **Step 7: Component chính của C-02**

Tạo `frontend/src/app/flights/flight-results.tsx`. `useFlightSearch` không gọi `setState` đồng bộ trong `useEffect` (luật `react-hooks/set-state-in-effect` của `eslint-config-next`): trạng thái "đang tải" suy ra từ việc kết quả đang giữ có khớp chuỗi tham số hiện tại hay không. `ResultList` có `key` khác nhau ở hai bước để bộ lọc và sắp xếp của bước 1 không mang sang bước 2.

```tsx
"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { Brand } from "@/components/auth-shell";
import { Notice, pillLine, textLink } from "@/components/form";
import { CABINS } from "@/components/landing/data";
import { ddmm, parseIso, weekday } from "@/components/landing/search-context";
import { ApiError, apiFetch, type Schemas } from "@/lib/api";
import { saveSelection, type Journey } from "@/lib/selection";
import {
  departsAfter,
  first,
  hhmm,
  itineraryKey,
  last,
  vnd,
  type FareOption,
  type Itinerary,
} from "./itineraries";
import ResultList from "./result-list";

type Load =
  | { status: "loading" }
  | { status: "ok"; itineraries: Itinerary[] }
  | { status: "error"; error: ApiError };

/** Gọi GET /flights/search khi chuỗi tham số đổi; qs = null thì không gọi. */
function useFlightSearch(qs: string | null) {
  const [result, setResult] = useState<{ qs: string; load: Load } | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!qs) return;
    let live = true;
    apiFetch<Schemas["SearchResult"]>(`/flights/search?${qs}`).then(
      (r) =>
        live &&
        setResult({ qs, load: { status: "ok", itineraries: r.itineraries } }),
      (e: ApiError) =>
        live && setResult({ qs, load: { status: "error", error: e } }),
    );
    return () => {
      live = false;
    };
  }, [qs, attempt]);
  const load: Load = result?.qs === qs ? result.load : { status: "loading" };
  return {
    load,
    retry: () => {
      setResult(null);
      setAttempt((n) => n + 1);
    },
  };
}

function dateLabel(iso: string) {
  const d = parseIso(iso);
  return d ? `${weekday(d)}, ${ddmm(d)}` : iso;
}

function paxLabel(adults: number, children: number, infants: number) {
  return [
    `${adults} người lớn`,
    children && `${children} trẻ em`,
    infants && `${infants} em bé`,
  ]
    .filter(Boolean)
    .join(", ");
}

function Skeleton() {
  return (
    <div aria-hidden="true" className="grid gap-16 motion-safe:animate-pulse">
      {[0, 1, 2].map((i) => (
        <div key={i} className="h-140 border border-line bg-tint" />
      ))}
    </div>
  );
}

function LoadError({ error, retry }: { error: ApiError; retry: () => void }) {
  return (
    <Notice>
      <p className="font-bold">
        {error.errors.length ? "Điều kiện tìm kiếm chưa hợp lệ." : error.detail}
      </p>
      {error.errors.map((e) => (
        <p key={e.field}>{e.message}</p>
      ))}
      <p className="mt-8">
        {error.status === 0 ? (
          <button type="button" className={textLink} onClick={retry}>
            Thử lại
          </button>
        ) : (
          <Link href="/" className={textLink}>
            Tìm lại từ trang chủ
          </Link>
        )}
      </p>
    </Notice>
  );
}

/**
 * C-02 (APP_FLOW §2.1, UF-01). Một chiều: chọn gói giá là xong. Khứ hồi (FR-12): bước 1 chọn chiều đi, URL thêm
 * `out` (id các chặng) và `outFare`; bước 2 chọn chiều về, chỉ hiện hành trình thoả BR-04. Bước nằm trong URL nên
 * Back, Forward và tải lại trang giữ đúng bước. Chọn xong thì lưu lựa chọn vào sessionStorage rồi mở /checkout.
 */
export default function FlightResults() {
  const router = useRouter();
  const params = useSearchParams();
  const from = params.get("from") ?? "";
  const to = params.get("to") ?? "";
  const date = params.get("date") ?? "";
  const returnDate = params.get("returnDate");
  const adults = params.get("adults") ?? "1";
  const children = params.get("children") ?? "0";
  const infants = params.get("infants") ?? "0";
  const cabin = params.get("cabin") ?? "ECONOMY";
  const hasQuery = Boolean(from && to && date);
  const badReturn = Boolean(returnDate && returnDate < date);

  const query = (f: string, t: string, d: string) =>
    new URLSearchParams({
      from: f,
      to: t,
      date: d,
      adults,
      children,
      infants,
      cabin,
    }).toString();
  // Tải cả hai chiều cùng lúc để bước 2 hiện ngay; tham số không đổi giữa hai bước nên không tải lại.
  const outbound = useFlightSearch(hasQuery ? query(from, to, date) : null);
  const inbound = useFlightSearch(
    hasQuery && returnDate && !badReturn ? query(to, from, returnDate) : null,
  );

  // Chiều đi đã chọn (bước 2) tìm lại trong kết quả chiều đi theo `out` và `outFare` của URL.
  const outKey = params.get("out");
  const outFare = Number(params.get("outFare"));
  const picked = (() => {
    if (!returnDate || !outKey || outbound.load.status !== "ok") return null;
    const itinerary = outbound.load.itineraries.find(
      (it) => itineraryKey(it) === outKey,
    );
    const fare = itinerary?.fareOptions.find((f) => f.fareFamilyId === outFare);
    return itinerary && fare ? { itinerary, fare } : null;
  })();
  const step2 = picked !== null;
  // Chuyến đã hết chỗ hoặc URL sửa tay: quay về bước 1 và báo cho khách.
  const lost =
    Boolean(returnDate && outKey) && outbound.load.status === "ok" && !step2;

  const counts = {
    adults: Number(adults),
    children: Number(children),
    infants: Number(infants),
  };
  const passengers = counts.adults + counts.children + counts.infants;
  const cabinLabel = CABINS.find((c) => c.id === cabin)?.label ?? cabin;
  const step1Url = (() => {
    const p = new URLSearchParams(params);
    p.delete("out");
    p.delete("outFare");
    return `/flights?${p}`;
  })();

  const journey = (
    direction: Journey["direction"],
    itinerary: Itinerary,
    fare: FareOption,
  ): Journey => ({
    direction,
    flightIds: itinerary.segments.map((s) => s.flightId),
    fareFamilyId: fare.fareFamilyId,
    itinerary,
    fare,
  });

  const choose = (itinerary: Itinerary, fare: FareOption) => {
    if (returnDate && !picked) {
      const p = new URLSearchParams(params);
      p.set("out", itineraryKey(itinerary));
      p.set("outFare", String(fare.fareFamilyId));
      router.push(`/flights?${p}`);
      return;
    }
    const journeys = picked
      ? [
          journey("OUTBOUND", picked.itinerary, picked.fare),
          journey("RETURN", itinerary, fare),
        ]
      : [journey("OUTBOUND", itinerary, fare)];
    saveSelection({ ...counts, journeys });
    // /checkout cần đăng nhập: proxy.ts chuyển về /login?next=%2Fcheckout, đăng nhập xong quay lại (UF-01).
    router.push("/checkout");
  };

  const title = !hasQuery
    ? "Tìm chuyến bay."
    : !returnDate
      ? "Chọn chuyến bay."
      : step2
        ? "Chọn chiều về."
        : "Chọn chiều đi.";
  const [legFrom, legTo, legDate] = picked
    ? [to, from, returnDate ?? date]
    : [from, to, date];
  const current = picked ? inbound : outbound;

  let body;
  if (!hasQuery) {
    body = (
      <p className="text-muted">
        Chưa có điều kiện tìm kiếm.{" "}
        <Link href="/" className={textLink}>
          Về trang chủ để tìm chuyến
        </Link>
      </p>
    );
  } else if (badReturn) {
    body = (
      <Notice>
        <p className="font-bold">Ngày về không được trước ngày đi.</p>
        <p className="mt-8">
          <Link href="/" className={textLink}>
            Tìm lại từ trang chủ
          </Link>
        </p>
      </Notice>
    );
  } else if (current.load.status === "loading") {
    body = <Skeleton />;
  } else if (current.load.status === "error") {
    body = <LoadError error={current.load.error} retry={current.retry} />;
  } else if (picked) {
    const all = current.load.itineraries;
    const allowed = all.filter(departsAfter(picked.itinerary));
    const hidden = all.length - allowed.length;
    body = (
      <ResultList
        key="return"
        itineraries={allowed}
        passengers={passengers}
        note={
          hidden
            ? `Đã ẩn ${hidden} hành trình cất cánh trước giờ hạ cánh của chiều đi.`
            : undefined
        }
        onChoose={choose}
      />
    );
  } else {
    body = (
      <ResultList
        key="outbound"
        itineraries={current.load.itineraries}
        passengers={passengers}
        onChoose={choose}
      />
    );
  }

  return (
    <div className="min-h-dvh bg-paper">
      <header className="mx-auto flex max-w-page items-center justify-between gap-16 px-16 py-22 sm:px-38">
        <Brand />
        <Link href="/" className={pillLine}>
          Đổi tìm kiếm
        </Link>
      </header>

      <main className="mx-auto grid max-w-page gap-38 px-16 pt-22 pb-119 sm:px-38">
        <div className="grid gap-13">
          {returnDate && hasQuery && (
            <ol className="flex gap-22 text-caption">
              {["Chiều đi", "Chiều về"].map((label, i) => {
                const active = (i === 1) === step2;
                return (
                  <li
                    key={label}
                    aria-current={active ? "step" : undefined}
                    className={active ? "font-bold" : "text-muted"}
                  >
                    {i + 1}. {label}
                  </li>
                );
              })}
            </ol>
          )}
          <h1 className="text-heading-lg font-bold">{title}</h1>
          {hasQuery && (
            <p className="text-muted">
              {legFrom} → {legTo}, {dateLabel(legDate)},{" "}
              {paxLabel(counts.adults, counts.children, counts.infants)},{" "}
              {cabinLabel}
            </p>
          )}
        </div>

        {lost && (
          <Notice>
            Chiều đi đã chọn không còn bán hoặc đã hết chỗ. Hãy chọn lại chiều
            đi.
          </Notice>
        )}

        {picked && (
          <section
            aria-label="Chiều đi đã chọn"
            className="grid gap-16 border border-ink p-22 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
          >
            <div className="grid gap-4">
              <p className="text-caption text-muted">Chiều đi đã chọn</p>
              <p className="font-bold">
                {first(picked.itinerary).airline.name}{" "}
                {picked.itinerary.segments
                  .map((s) => s.flightNumber)
                  .join(" + ")}
                {": "}
                {hhmm(first(picked.itinerary).departure)} {from} →{" "}
                {hhmm(last(picked.itinerary).arrival)} {to}, {dateLabel(date)}
              </p>
              <p className="text-caption text-muted">
                {picked.fare.name}, {vnd(picked.fare.totalPrice)}
              </p>
            </div>
            <Link href={step1Url} className={pillLine}>
              Đổi chiều đi
            </Link>
          </section>
        )}

        {body}
      </main>
    </div>
  );
}
```

- [ ] **Step 8: Thay trang tạm `/flights`**

Thay toàn bộ `frontend/src/app/flights/page.tsx` bằng:

```tsx
import type { Metadata } from "next";
import { Suspense } from "react";
import FlightResults from "./flight-results";

export const metadata: Metadata = { title: "Kết quả tìm kiếm | SkyLine" };

/** C-02 (APP_FLOW §2.1). Công khai. useSearchParams đọc điều kiện tìm, nên nằm trong Suspense (Cache Components). */
export default function Page() {
  return (
    <Suspense>
      <FlightResults />
    </Suspense>
  );
}
```

- [ ] **Step 9: Chạy test để thấy nó xanh**

```bash
cd frontend && npm run lint && npm run build && npx playwright test
```

Expected: lint không có lỗi; build xanh và danh sách route có `○ /checkout`, `○ /flights`; Playwright `20 passed` (4 `itineraries`, 4 `flight-search`, 10 `smoke`, 2 `account`).

- [ ] **Step 10: Xem bằng mắt**

Với `npm run dev` đang chạy, mở `http://localhost:3000/flights?from=HAN&to=SGN&date=<ngày mai>&returnDate=<ngày mai>&adults=2&children=1&infants=1&cabin=ECONOMY` ở 1280px và 390px, chế độ sáng và tối. Kiểm tra:

- 1280px: bộ lọc cột trái, 4 thẻ bên phải, VJ123 đứng đầu với "Từ 2.970.000 đ cho 4 khách" (990.000 × 2 + 891.000 + 99.000).
- 390px: chỉ có nút "Bộ lọc", thẻ xếp giá và nút "Chọn" xuống dưới giờ bay, không có thanh cuộn ngang.
- Mở thẻ VN1233 + VN1826: hai chặng, dòng "Nối chuyến tại Phú Quốc, chờ 2 giờ 20 phút", ba gói Economy Lite, Classic, Flex; chữ `đ` không rớt dòng.
- Chọn một gói: sang bước 2, khung "Chiều đi đã chọn" viền đậm có nút "Đổi chiều đi".
- Chế độ tối: nền, chữ, viền đổi theo token, không còn mảng trắng.

- [ ] **Step 11: Cập nhật CLAUDE.md**

Trong đoạn `## Status`, thay:

```markdown
The frontend lives in `frontend/`: the C-01 landing page is built from the Claude Design mock, the account screens C-03–C-06 and C-12 are done (Plan 04), and the other screens are placeholders.
```

bằng:

```markdown
The frontend lives in `frontend/`: the C-01 landing page is built from the Claude Design mock and its search box reads `GET /api/airports`, C-02 flight results (client-side filter and sort, two-step round trip, selection saved to `sessionStorage` by `src/lib/selection.ts`) is done (Plan 08), the account screens C-03–C-06 and C-12 are done (Plan 04), and the other screens are placeholders (`/checkout` is the login-protected C-07 placeholder).
```

Trong mục `## Commands`, ở dòng Frontend, ngay sau câu `E2E: \`npm run e2e\` needs the backend running (profile \`dev\`) and reuses or starts \`npm run dev\` on port 3000; set \`E2E_BASE_URL\` to test another server (first run: \`npx playwright install chromium\`).` thêm câu:

```markdown
Pure frontend logic is unit-tested with the same runner without a browser (`npx playwright test itineraries`); flight E2E needs seed `V106` younger than ~58 days (refresh with `docker compose down -v`).
```

- [ ] **Step 12: Cập nhật lộ trình**

Trong `docs/superpowers/plans/2026-10-09-00-roadmap.md`:

1. **Bảng đầu tài liệu:** thêm Plan 08 vào cuối dòng "Plan chi tiết đã có":

```markdown
| Plan chi tiết đã có | [Plan 01 — Nền móng backend](2026-10-09-01-backend-foundation.md) · [Plan 02 — Tài khoản, tham số, khung email](2026-10-09-02-accounts-settings-email.md) · [Plan 04 — Màn hình tài khoản](2026-10-10-04-account-screens.md) · [Plan 05 — Danh mục](2026-10-10-05-catalog.md) · [Plan 06 — Quản trị chuyến bay và kho ghế](2026-10-11-06-flights.md) · [Plan 07 — Tìm chuyến](2026-10-11-07-flight-search.md) · [Plan 08 — Màn hình tìm chuyến](2026-10-11-08-flight-search-screens.md) |
```

2. **§2:** ở dòng Plan 08, đổi `☐` cuối dòng thành `☑`. Xoá khối "**Đã làm trước thứ tự plan (chưa tick "Xong"):**" cùng gạch đầu dòng "**Plan 08:** đã có phần giao diện của C-01..." ngay dưới bảng (phần còn thiếu đã làm xong).

3. **§6:** thêm dòng sau ngay dưới dòng `| 07 | ... |`:

```markdown
| 08 | Ô tìm chuyến của C-01 dùng `src/lib/airports.ts`: `useAirports() → {airports, failed}` (tải `GET /airports` một lần cho cả trang), `countryName(code)`. Màn C-02 `/flights?from=&to=&date=&returnDate=&adults=&children=&infants=&cabin=` (công khai); bước 2 của khứ hồi thêm `out` (id các chặng nối bằng dấu phẩy) và `outFare` (`fareFamilyId`). Hàm thuần ở `src/app/flights/itineraries.ts`: `hhmm`, `dayShift`, `duration`, `vnd`, `cheapest`, `itineraryKey`, `departsAfter` (BR-04), `applyFilters`, `sortItineraries`. Chọn gói xong lưu `sessionStorage["skyline.selection"]` = `Selection{adults, children, infants, journeys}`, mỗi `Journey{direction: "OUTBOUND" \| "RETURN", flightIds, fareFamilyId, itinerary, fare}` (`src/lib/selection.ts`: `SELECTION_KEY`, `saveSelection`; Plan 13 thêm hàm đọc), rồi mở `/checkout` (trang tạm C-07, đã cần đăng nhập trong `proxy.ts`). Unit test logic frontend chạy bằng Playwright không mở trình duyệt (`e2e/itineraries.spec.ts`); Playwright chạy 4 luồng |
```

4. **§7:** thêm mục sau vào cuối danh sách (sau mục 9 về Plan 07):

```markdown
10. **Plan 08 có 3 điểm cố ý khác TDD và APP_FLOW** (chi tiết ở [Plan 08](2026-10-11-08-flight-search-screens.md#điểm-cố-ý-khác-tdd-và-app_flow)): ô tìm chuyến tải `GET /airports` một lần rồi lọc ở client, không gọi `?q=` mỗi lần gõ; giờ hiển thị đọc thẳng từ chuỗi có offset của API thay vì qua `Intl.DateTimeFormat`; `/checkout` có sẵn dạng trang tạm và đã cần đăng nhập để luồng chọn vé → đăng nhập → C-07 chạy được từ plan này.
```

- [ ] **Step 13: Commit**

```bash
git add frontend/src/lib/selection.ts frontend/src/app/flights frontend/src/app/checkout frontend/src/proxy.ts frontend/e2e/smoke.spec.ts frontend/e2e/flight-search.spec.ts
git commit -m "feat(frontend): màn kết quả tìm chuyến (C-02), lọc, sắp xếp và khứ hồi 2 bước"
git add CLAUDE.md docs/superpowers/plans/2026-10-09-00-roadmap.md
git status --short
git commit -m "docs: cập nhật lộ trình sau Plan 08"
```

Expected: `git status --short` trước commit thứ hai **không** liệt kê `.env`, `frontend/test-results/` hay `.playwright-cli/`.

---

## Kiểm tra cuối plan

- [ ] `cd frontend && npm run lint && npm run build` → xanh.
- [ ] Backend profile `dev` đang chạy: `cd frontend && npx playwright test` → `20 passed`.
- [ ] UF-01 đến bước đăng nhập bằng tay: trang chủ → chọn Khứ hồi, ngày về → "Tìm chuyến bay" → chọn chiều đi → chọn chiều về → `/login?next=%2Fcheckout` → đăng nhập `customer@demo.local` / `Demo@1234` → về `/checkout` (trang tạm C-07).
- [ ] CI trên GitHub xanh (nếu đã push): job `frontend` chạy lint và build; E2E không chạy trong CI.
- [ ] Lộ trình đã đánh dấu Plan 08 (§2), thêm hợp đồng (§6) và mục 10 (§7) theo code thật.
