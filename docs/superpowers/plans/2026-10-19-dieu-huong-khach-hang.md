# Điều hướng khu khách hàng và trang Tìm chuyến bay — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mọi trang khách hàng dùng chung một header (có menu tên). `/flights` thành trang "Tìm chuyến bay" độc lập, sửa được tìm kiếm ngay tại chỗ. `/checkout` có header theo bước.

**Architecture:** Hai hàm thuần trong `src/lib/search-query.ts`: một hàm đổi URL thành giá trị ban đầu của form, một hàm dựng lại URL tìm từ lựa chọn. Thêm một hook `useCurrentUser` (gọi `GET /api/me`) và một component `UserMenu` (Popover API của trình duyệt), dùng chung cho header mới `SiteHeader` và navbar landing. Trang `/flights` dùng lại nguyên `SearchDock` của landing: bọc trong `SearchProvider` (thêm prop `initial`) và một thẻ `.sk`, rồi gắn `key` theo điều kiện tìm để form và trạng thái mở/đóng tự reset sau mỗi lần tìm.

**Tech Stack:** Next.js 16 (App Router, Cache Components), React 19, TypeScript, Tailwind v4 (token DESIGN.md, `--spacing: 1px`), Playwright (e2e và unit test không cần trình duyệt).

**Spec:** `docs/superpowers/specs/2026-10-19-dieu-huong-khach-hang-design.md`

## Global Constraints

- Chỉ sửa frontend (`frontend/`), không đổi API backend, không thêm dependency.
- Đọc `frontend/node_modules/next/dist/docs/` trước khi viết code Next.js (`frontend/AGENTS.md`).
- Không gọi `new Date()`, `Date.now()` hay `Math.random()` lúc render (Cache Components).
- Chữ hiển thị, comment và tên test bằng tiếng Việt.
- Theo DESIGN.md: nút chính dùng `pillDark` (`#000d10`), nút phụ dùng `pillLine`, link dùng `textLink` (`src/components/form.tsx`). `p-N` = N px.
- Không có link nào trong khu khách hàng dẫn về `/` để tìm chuyến. Logo (`Brand`) về `/` thì vẫn giữ.
- Mọi lệnh chạy trong `frontend/`. Unit test (`npx playwright test search-query`) không cần backend. E2E cần backend profile `dev` chạy ở cổng 8080 (CLAUDE.md).

## Review Focus

1. **Tìm lại từ `/flights` khi đang ở bước 2 của khứ hồi** (URL có `out`, `outFare`): tìm mới phải bỏ `out`/`outFare` và quay về bước 1. Đã có sẵn, vì `SearchDock` dựng URL mới từ đầu; Task 4 có test.
2. **Đăng xuất khi đang ở `/`**: nếu chỉ `router.push("/")` thì header vẫn giữ user cũ. Phải tải lại trang (`window.location.assign("/")`); Task 2 có test (xong thì thấy "Đăng nhập").
3. **`/checkout` lúc prerender hoặc chưa có lựa chọn** (`selection` là `undefined` hoặc `null`): bước 1 trỏ `/flights`, không được vỡ trang. Task 5 có test unit và smoke.
4. **Hành trình nối chuyến**: điểm đến trong URL dựng lại phải là sân bay đến của chặng **cuối**, không phải chặng đầu. Task 1 có test.
5. **Query string hỏng** (`cabin=XYZ`, `adults=abc`): form trên `/flights` bỏ qua giá trị sai, dùng mặc định thay vì ra `NaN` hoặc hạng ghế lạ. Task 1 có test.

---

### Task 1: Hàm chuyển đổi URL tìm kiếm

**Files:**

- Create: `frontend/src/lib/search-query.ts`
- Test: `frontend/e2e/search-query.spec.ts`

**Interfaces:**

- Consumes: `SearchForm` (type, `src/components/landing/search-context.tsx`); `CABINS` (`src/components/landing/data.ts`, có `id`); `Selection`, `Journey` (`src/lib/selection.ts`); `first`, `last` (`src/app/flights/itineraries.ts`).
- Produces:
  - `formFromParams(params: URLSearchParams): Partial<SearchForm>`
  - `flightsUrl(selection: Selection): string`

- [ ] **Step 1: Viết test hỏng**

`e2e/search-query.spec.ts` (unit test, kiểu `e2e/itineraries.spec.ts`; dựng `Journey` tối thiểu rồi ép kiểu `as Journey`):

```ts
test("formFromParams đọc đủ điều kiện khứ hồi", () => {
  const p = new URLSearchParams(
    "from=HAN&to=SGN&date=2026-11-02&returnDate=2026-11-05&adults=2&children=1&infants=0&cabin=BUSINESS",
  );
  expect(formFromParams(p)).toEqual({
    trip: "round",
    from: "HAN",
    to: "SGN",
    dep: "2026-11-02",
    ret: "2026-11-05",
    adults: 2,
    children: 1,
    infants: 0,
    cabin: "BUSINESS",
  });
});
test("formFromParams không có tham số thì chỉ là một chiều", () => {
  expect(formFromParams(new URLSearchParams())).toEqual({ trip: "oneway" });
});
test("formFromParams bỏ qua hạng ghế và số khách sai", () => {
  expect(formFromParams(new URLSearchParams("cabin=XYZ&adults=abc"))).toEqual({
    trip: "oneway",
  });
});
test("flightsUrl một chiều nối chuyến lấy sân bay đến của chặng cuối", () => {
  // OUTBOUND: HAN 2026-11-02T08:00:00+07:00 → DAD, DAD → SGN; fare.cabinClass "ECONOMY"; adults 1, children 0, infants 0
  expect(flightsUrl(sel)).toBe(
    "/flights?from=HAN&to=SGN&date=2026-11-02&adults=1&children=0&infants=0&cabin=ECONOMY",
  );
});
test("flightsUrl khứ hồi thêm returnDate theo chặng đầu của chiều về", () => {
  // RETURN: SGN 2026-11-05T17:00:00+07:00 → HAN
  expect(flightsUrl(roundSel)).toBe(
    "/flights?from=HAN&to=SGN&date=2026-11-02&adults=1&children=0&infants=0&cabin=ECONOMY&returnDate=2026-11-05",
  );
});
```

- [ ] **Step 2: Chạy để thấy hỏng**

Run: `npx playwright test search-query`
Expected: FAIL, không import được `../src/lib/search-query`.

- [ ] **Step 3: Viết `formFromParams` và `flightsUrl` trong `src/lib/search-query.ts`**

- `formFromParams`: `trip` là `"round"` khi có `returnDate`, ngược lại `"oneway"`. Các khoá khác chỉ có khi tham số tồn tại và hợp lệ: `from`, `to`, `dep` (lấy từ `date`), `ret` (lấy từ `returnDate`), số khách là số nguyên ≥ 0 (`Number.isInteger`), `cabin` phải nằm trong `CABINS`.
- `flightsUrl`: thứ tự tham số giống `SearchDock` (`from, to, date, adults, children, infants, cabin`, rồi `returnDate` nếu có chiều `RETURN`). Ngày lấy bằng `departure.time.slice(0, 10)`: chuỗi `OffsetDateTime` đã ở múi giờ của sân bay (TDD §5), nên không đổi qua `Date`.

- [ ] **Step 4: Chạy để thấy qua**

Run: `npx playwright test search-query`
Expected: 5 passed.

- [ ] **Step 5: Commit**

```bash
git add src/lib/search-query.ts e2e/search-query.spec.ts
git commit -m "feat(frontend): chuyển đổi giữa URL tìm chuyến, form tìm và lựa chọn đã lưu"
```

---

### Task 2: Header chung `SiteHeader` và menu tên `UserMenu`

**Files:**

- Create: `frontend/src/lib/use-current-user.ts`
- Create: `frontend/src/components/user-menu.tsx`
- Create: `frontend/src/components/site-header.tsx`
- Modify: `frontend/src/app/bookings/booking-list.tsx` (import và `<CustomerHeader />` ở dòng 6, 141)
- Modify: `frontend/src/app/bookings/[code]/booking-view.tsx` (dòng 6, 251)
- Modify: `frontend/src/app/checkout/checkout-view.tsx` (dòng 6, 65; tạm dùng `SiteHeader`, Task 5 thay tiếp)
- Modify: `frontend/src/app/account/account-view.tsx` (bỏ header riêng ở dòng 208–228, `logout()`, state `leaving`, các import bị thừa)
- Delete: `frontend/src/components/customer-header.tsx`
- Test: `frontend/e2e/account.spec.ts`, `frontend/e2e/smoke.spec.ts`

**Interfaces:**

- Consumes: `apiFetch`, `homeOf`, `type User` (`src/lib/api.ts`); `Brand` (`src/components/auth-shell.tsx`); `pillDark`, `pillLine` (`src/components/form.tsx`).
- Produces:
  - `useCurrentUser(): User | null | undefined`: `undefined` khi đang tải, `null` khi chưa đăng nhập (mọi lỗi của `GET /me`), `User` khi đã đăng nhập.
  - `UserMenu({ user, className }: { user: User; className?: string })`: nút có `aria-label={\`Menu tài khoản: ${user.fullName}\`}`, chữ hiển thị là tên gọi (`fullName.split(" ").at(-1)`) kèm ▾.
  - `SiteHeader()`: không có prop.

- [ ] **Step 1: Sửa test e2e theo hành vi mới (hỏng)**

`e2e/account.spec.ts`, thay đoạn đăng xuất (dòng 47–49):

```ts
// Đăng xuất từ menu tên: về trang chủ, header hiện lại Đăng nhập.
await page.getByRole("button", { name: /^Menu tài khoản/ }).click();
await page.getByRole("button", { name: "Đăng xuất" }).click();
await expect(page).toHaveURL("/");
await expect(
  page.getByRole("link", { name: "Đăng nhập" }).first(),
).toBeVisible();
```

`e2e/smoke.spec.ts`, cuối test "route cần đăng nhập hiển thị được sau khi đăng nhập":

```ts
await page.goto("/bookings");
await expect(
  page.getByRole("link", { name: "Đặt chỗ của tôi" }),
).toHaveAttribute("aria-current", "page");
await expect(
  page.getByRole("button", { name: /^Menu tài khoản/ }),
).toBeVisible();
```

- [ ] **Step 2: Chạy để thấy hỏng**

Run: `npx playwright test account smoke` (backend profile `dev` đang chạy)
Expected: FAIL, không tìm thấy nút `Menu tài khoản`.

- [ ] **Step 3: Viết `useCurrentUser` trong `src/lib/use-current-user.ts`**

Dùng `useEffect` + `apiFetch<User>("/me")`. Có cờ `live` để không `setState` sau khi unmount, giống `useFlightSearch`.

- [ ] **Step 4: Viết `UserMenu` trong `src/components/user-menu.tsx`**

- Dùng Popover API: nút có `popoverTarget={id}` (`useId`), panel có `popover="auto"`, nên tự đóng khi bấm ra ngoài hoặc nhấn Esc.
- Panel đặt `fixed` ở góc phải trên (khoảng `top-72 right-16`, và nhớ reset `inset`/`margin` mặc định của UA cho `[popover]`), nền `bg-paper`, viền `border-line`.
- Thêm comment `// ponytail: vị trí cố định, dùng CSS anchor positioning khi các trình duyệt đều hỗ trợ`.
- Nội dung từ trên xuống:
  - Họ tên và email (chỉ hiển thị).
  - Khách hàng: link "Đặt chỗ của tôi" (`/bookings`); Staff/Admin: link "Trang làm việc" (`homeOf(user.role)`).
  - Link "Tài khoản" (`/account`).
  - Nút "Đăng xuất": `POST /auth/logout` (bỏ qua lỗi, như `account-view.tsx` đang làm), rồi `window.location.assign("/")`. Dùng reload để mọi header đọc lại `/me`; xem Review Focus 2.

- [ ] **Step 5: Viết `SiteHeader` trong `src/components/site-header.tsx`**

- Khung giống `CustomerHeader` cũ (`max-w-page`, `flex-wrap`, `px-16 sm:px-38`, `py-22`).
- `Brand` bên trái. Hai link "Tìm chuyến bay" (`/flights`) và "Đặt chỗ của tôi" (`/bookings`); link nào có `usePathname()` bắt đầu bằng `href` của nó thì đặt `aria-current="page"` và in đậm `text-ink`, còn lại `text-muted`.
- Ngoài cùng bên phải: `undefined` → ô trống giữ chỗ (`min-w-120`); `null` → "Đăng nhập" (`pillDark`, `/login`) và "Đăng ký" (`pillLine`, `/register`); `User` → `<UserMenu user={user} />`.

- [ ] **Step 6: Thay header ở 4 trang, xoá `customer-header.tsx`**

- `booking-list.tsx`, `booking-view.tsx`, `checkout-view.tsx`: `CustomerHeader` → `SiteHeader`.
- `account-view.tsx`: thêm `<SiteHeader />` vào chỗ header cũ, và bỏ `logout`, `leaving`, cùng các import `Link`, `Brand`, `pillLine`, `homeOf` nếu không còn dùng.
- Xoá file `src/components/customer-header.tsx`.

- [ ] **Step 7: Chạy kiểm tra**

Run: `npm run lint && npm run build && npx playwright test account smoke`
Expected: lint và build không lỗi, các test passed.

- [ ] **Step 8: Commit**

```bash
git add -A src e2e/account.spec.ts e2e/smoke.spec.ts
git commit -m "feat(frontend): header chung và menu tên cho các trang khách hàng"
```

---

### Task 3: Navbar trang chủ dùng cùng mục và menu tên

**Files:**

- Modify: `frontend/src/components/landing/nav.tsx`
- Test: `frontend/e2e/smoke.spec.ts`

**Interfaces:**

- Consumes: `useCurrentUser()`, `UserMenu` (Task 2).

- [ ] **Step 1: Viết test hỏng**

Thêm vào `e2e/smoke.spec.ts`:

```ts
test("trang chủ có link Tìm chuyến bay tới /flights", async ({ page }) => {
  await page.goto("/");
  await expect(
    page
      .getByRole("navigation", { name: "Điều hướng chính" })
      .getByRole("link", { name: "Tìm chuyến bay" }),
  ).toHaveAttribute("href", "/flights");
});
```

Thêm vào cuối test đăng nhập của smoke:

```ts
await page.goto("/");
await expect(
  page
    .getByRole("navigation", { name: "Điều hướng chính" })
    .getByRole("button", { name: /^Menu tài khoản/ }),
).toBeVisible();
```

- [ ] **Step 2: Chạy để thấy hỏng**

Run: `npx playwright test smoke`
Expected: FAIL ở 2 chỗ trên.

- [ ] **Step 3: Sửa `nav.tsx`**

- Bỏ `useState<User>` và `useEffect` gọi `/me`, thay bằng `const user = useCurrentUser()`.
- Desktop: thêm `<Link className="nav-link" href="/flights">Tìm chuyến bay</Link>` trước "Cách đặt vé". Chỗ "Tài khoản/Đăng nhập": `undefined` → không render gì; `null` → pill "Đăng nhập" như cũ; `User` → `<UserMenu user={user} className="nav-link" />`.
- Mobile menu: thêm "Tìm chuyến bay" vào `menu-links`. Trong `menu-cta`, khi có `User` thì thay pill "Tài khoản" bằng `<UserMenu user={user} className="pill pill-line" />`.

- [ ] **Step 4: Chạy kiểm tra**

Run: `npm run lint && npx playwright test smoke flight-search`
Expected: passed (`flight-search` bảo đảm ô tìm trên landing vẫn chạy).

- [ ] **Step 5: Commit**

```bash
git add src/components/landing/nav.tsx e2e/smoke.spec.ts
git commit -m "feat(frontend): navbar trang chủ có Tìm chuyến bay và menu tên"
```

---

### Task 4: `/flights` thành trang Tìm chuyến bay

**Files:**

- Modify: `frontend/src/components/landing/search-context.tsx` (`SearchProvider`, dòng ~79)
- Create: `frontend/src/app/flights/search-bar.tsx`
- Modify: `frontend/src/app/flights/flight-results.tsx` (header dòng 270–276, khối `!hasQuery`/`badReturn` dòng 216–234, `LoadError` dòng 96–99, dòng tóm tắt dòng 296–302)
- Modify: `frontend/src/app/flights/page.tsx` (metadata)
- Test: `frontend/e2e/flight-search.spec.ts`

**Interfaces:**

- Consumes: `formFromParams` (Task 1); `SiteHeader` (Task 2); `SearchDock` (default export của `src/components/landing/search-dock.tsx`, có `role="search"`, `aria-label="Tìm chuyến bay"`).
- Produces:
  - `SearchProvider({ children, initial }: { children: ReactNode; initial?: Partial<SearchForm> })`: `initial` ghi đè giá trị mặc định ban đầu của `raw`.
  - `SearchBar({ initial, summary }: { initial: Partial<SearchForm>; summary: ReactNode | null })`: `summary` là `null` thì thanh tìm mở sẵn và không có nút "Đổi tìm kiếm".

- [ ] **Step 1: Viết test hỏng**

Thêm vào `e2e/flight-search.spec.ts`:

```ts
test("/flights trống hiện sẵn ô tìm, không dẫn về trang chủ", async ({
  page,
}) => {
  await page.goto("/flights");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Tìm chuyến bay.",
  );
  await expect(
    page.getByRole("search", { name: "Tìm chuyến bay" }),
  ).toBeVisible();
  // Logo có aria-label "SkyLine, về trang chủ" nên chỉ bắt các link tìm lại cũ.
  await expect(
    page.getByRole("link", {
      name: /Về trang chủ để tìm|Tìm lại từ trang chủ/,
    }),
  ).toHaveCount(0);
});

test("Đổi tìm kiếm mở ô tìm điền sẵn, tìm lại thì về bước 1 và đóng ô", async ({
  page,
}) => {
  const d = day(1);
  await page.goto(
    search({
      from: "HAN",
      to: "SGN",
      date: d,
      returnDate: day(3),
      out: "1",
      outFare: "1",
    }),
  );
  await expect(
    page.getByRole("search", { name: "Tìm chuyến bay" }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Đổi tìm kiếm" }).click();
  await expect(page.getByRole("button", { name: /^Đến SGN/ })).toBeVisible();
  await page
    .getByRole("search", { name: "Tìm chuyến bay" })
    .getByRole("button", { name: "Tìm chuyến bay" })
    .click();
  await expect(page).not.toHaveURL(/out=/);
  await expect(
    page.getByRole("search", { name: "Tìm chuyến bay" }),
  ).toHaveCount(0);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Chọn chiều đi.",
  );
});
```

Trong test "URL sai hoặc chiều đi không còn…", sau dòng kiểm tra "Ngày về không được trước ngày đi.":

```ts
await expect(
  page.getByRole("search", { name: "Tìm chuyến bay" }),
).toBeVisible();
```

- [ ] **Step 2: Chạy để thấy hỏng**

Run: `npx playwright test flight-search`
Expected: FAIL ở 3 chỗ mới.

- [ ] **Step 3: Thêm prop `initial` cho `SearchProvider`**

Đổi thành `useState<SearchForm>({ ...DEFAULTS, ...initial })`, với `DEFAULTS` là object mặc định hiện có. Landing không truyền `initial` nên không đổi hành vi.

- [ ] **Step 4: Viết `SearchBar` trong `src/app/flights/search-bar.tsx`**

- Client component, có `import "@/components/landing/landing.css"`. State `open` khởi tạo bằng `summary === null`.
- Khi có `summary`: một hàng `border border-line p-16`, gồm `summary` bên trái và nút `pillLine` "Đổi tìm kiếm" bên phải (`aria-expanded={open}`, bấm thì đảo `open`).
- Khi `open`: `<SearchProvider initial={initial}><div className="sk"><SearchDock /></div></SearchProvider>`.

- [ ] **Step 5: Sửa `flight-results.tsx`**

- Header: thay `<header>…Brand…Đổi tìm kiếm…</header>` bằng `<SiteHeader />`. Bỏ các import thừa (`Brand`, `pillLine`).
- Trong `<main>`, ngay dưới khối tiêu đề, đặt `<SearchBar key={searchKey} initial={formFromParams(params)} summary={…} />`:
  - `searchKey` là `query(from, to, date) + (returnDate ?? "")`, không gồm `out`/`outFare`. Nhờ vậy chọn chiều đi không đóng ô tìm, còn tìm mới thì reset form và đóng ô.
  - `summary`: `hasQuery && !badReturn` thì là đoạn `<p className="text-muted">{legFrom} → {legTo}, …</p>` đang có ở dòng 296–302 (chuyển xuống đây, bỏ ở chỗ cũ). Ngược lại là `null`, để ô tìm mở sẵn.
- `!hasQuery`: thay câu "Chưa có điều kiện tìm kiếm…" và link về `/` bằng `<p className="text-muted">Chọn điểm đi, điểm đến và ngày bay để xem các chuyến còn chỗ.</p>`.
- `badReturn`: giữ dòng chữ đậm "Ngày về không được trước ngày đi.", bỏ link "Tìm lại từ trang chủ" và thay bằng "Hãy chọn lại ngày ở ô tìm phía trên."
- `LoadError`: nhánh không phải lỗi mạng thì thay link về `/` bằng chữ "Hãy sửa điều kiện bằng nút Đổi tìm kiếm phía trên."

- [ ] **Step 6: Sửa `page.tsx`**

`metadata.title` thành `"Tìm chuyến bay | SkyLine"`. Comment JSDoc ghi C-02 là trang tìm chuyến độc lập (APP_FLOW §2.1).

- [ ] **Step 7: Chạy kiểm tra**

Run: `npm run lint && npm run build && npx playwright test flight-search smoke`
Expected: passed.

- [ ] **Step 8: Kiểm tra bằng mắt**

Mở http://localhost:3000/flights?from=HAN&to=SGN&date=<ngày mai>&adults=1&children=0&infants=0&cabin=ECONOMY ở cả hai độ rộng 1280px và 375px. Bấm "Đổi tìm kiếm", mở bảng chọn sân bay và lịch: các bảng không bị cắt, không có cuộn ngang. Nếu bị cắt do `overflow-x: clip` của `.sk`, thêm `overflow: visible` cho thẻ bọc trong `search-bar.tsx`.

- [ ] **Step 9: Commit**

```bash
git add src/components/landing/search-context.tsx src/app/flights e2e/flight-search.spec.ts
git commit -m "feat(frontend): /flights là trang Tìm chuyến bay, sửa tìm kiếm tại chỗ"
```

---

### Task 5: Header theo bước cho `/checkout`, bỏ các link về trang chủ còn lại

**Files:**

- Create: `frontend/src/app/checkout/checkout-header.tsx`
- Modify: `frontend/src/app/checkout/checkout-view.tsx` (header; link "Tìm chuyến bay" ở dòng ~74)
- Modify: `frontend/src/app/bookings/booking-list.tsx` (link ở dòng ~78)
- Modify: `CLAUDE.md` (câu Status về frontend)
- Test: `frontend/e2e/booking.spec.ts`

**Interfaces:**

- Consumes: `flightsUrl` (Task 1); `useSelection()` (`src/lib/selection.ts`, trả `Selection | null | undefined`); `Brand`.
- Produces: `CheckoutHeader({ backUrl }: { backUrl: string })`.

- [ ] **Step 1: Viết test hỏng**

`e2e/booking.spec.ts`, test "đặt vé từ C-07 tới VNPay…", ngay sau `await expect(page).toHaveURL("/checkout");` ở dòng 85:

```ts
const steps = page.getByRole("navigation", { name: "Các bước đặt vé" });
await expect(
  steps.getByRole("link", { name: "1. Chọn chuyến" }),
).toHaveAttribute(
  "href",
  `/flights?from=HAN&to=SGN&date=${day(1)}&adults=1&children=0&infants=0&cabin=ECONOMY`,
);
await expect(steps.getByText("2. Thông tin hành khách")).toHaveAttribute(
  "aria-current",
  "step",
);
await expect(page.getByRole("link", { name: "Đặt chỗ của tôi" })).toHaveCount(
  0,
);
```

(Nếu `booking.spec.ts` chưa có hàm `day`, chép từ `flight-search.spec.ts`.)

- [ ] **Step 2: Chạy để thấy hỏng**

Run: `npx playwright test booking -g "VNPay"`
Expected: FAIL, không thấy navigation "Các bước đặt vé".

- [ ] **Step 3: Viết `CheckoutHeader` trong `src/app/checkout/checkout-header.tsx`**

- Khung giống `SiteHeader`, gồm `Brand` và `<nav aria-label="Các bước đặt vé"><ol>` với 3 bước:
  - "1. Chọn chuyến" là `Link` tới `backUrl`.
  - "2. Thông tin hành khách" là `<span aria-current="step" className="font-bold">`.
  - "3. Thanh toán" là chữ `text-muted`.
- Giữa các bước có dấu `›` với `aria-hidden`. Dưới 640px chỉ hiện bước hiện tại và link bước 1.

- [ ] **Step 4: Dùng header trong `checkout-view.tsx`, sửa các link về `/`**

- `checkout-view.tsx`: `<SiteHeader />` → `<CheckoutHeader backUrl={selection ? flightsUrl(selection) : "/flights"} />` (`selection` là `undefined` hoặc `null` thì về `/flights`). Link "Tìm chuyến bay" ở trạng thái chưa có lựa chọn đổi `href="/"` thành `href="/flights"`.
- `booking-list.tsx` dòng ~78: `href="/"` → `href="/flights"`.

- [ ] **Step 5: Kiểm tra không còn link về trang chủ để tìm chuyến**

Run: `grep -rn 'href="/"' src/app src/components --include=*.tsx | grep -v landing`
Expected: chỉ còn `auth-shell.tsx` (`Brand` và "Về trang chủ" của trang đăng nhập) và `placeholder-page.tsx`.

- [ ] **Step 6: Cập nhật `CLAUDE.md`**

Trong đoạn Status về frontend, thêm: header chung `SiteHeader` và `UserMenu`; `/flights` là trang tìm chuyến độc lập, dùng lại `SearchDock`; `/checkout` có `CheckoutHeader`.

- [ ] **Step 7: Chạy toàn bộ kiểm tra**

Run: `npm run lint && npm run build && npm run e2e`
Expected: lint và build không lỗi, mọi e2e passed.

- [ ] **Step 8: Đi tay hết vòng (spec §7)**

Đi lần lượt: `/` → "Tìm chuyến bay" → tìm → "Đổi tìm kiếm" → tìm lại → chọn vé → đăng nhập → `/checkout` (bấm "1. Chọn chuyến" thì về đúng kết quả, quay lại) → giữ chỗ → `/bookings/[code]` → menu tên → "Tài khoản" → "Tìm chuyến bay" → menu tên → "Đăng xuất". Không lần nào bị đưa về landing ngoài ý muốn; xong thì header hiện "Đăng nhập".

- [ ] **Step 9: Commit**

```bash
git add src/app/checkout src/app/bookings/booking-list.tsx e2e/booking.spec.ts ../CLAUDE.md
git commit -m "feat(frontend): header theo bước cho /checkout, bỏ link tìm chuyến về trang chủ"
```
