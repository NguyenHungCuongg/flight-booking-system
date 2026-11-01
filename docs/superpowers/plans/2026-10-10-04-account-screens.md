# Plan 04 — Màn hình tài khoản: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Người dùng đăng ký, đăng nhập, quên và đặt lại mật khẩu, sửa hồ sơ, đổi mật khẩu và đăng xuất trên giao diện SkyLine, dùng API thật của Plan 02.

**Architecture:** Các màn C-03–C-06 nằm trong route group `(auth)` dùng chung một layout chia đôi (form bên trái, bầu trời và máy bay bên phải). C-12 là `/account`. Form là Client Component gọi API qua một hàm `apiFetch` duy nhất; `/api/*` đi qua `rewrites` của Next.js tới backend nên cookie phiên và cookie `XSRF-TOKEN` thuộc cùng origin với frontend. `proxy.ts` chuyển route cần đăng nhập về `/login?next=` khi không có cookie phiên.

**Tech Stack:** Next.js 16.4 (App Router, Cache Components), React 19.3, TypeScript, Tailwind v4, font Inter (subset tiếng Việt), Playwright (`@playwright/test`) cho E2E.

**Spec:** [App Flow](../../APP_FLOW.md) §1 (trang mặc định theo vai trò), §2.1 C-03–C-06, C-12, UF-06 · [PRD](../../PRD.md) FR-01–04, BR-100 · [TDD](../../TDD.md) §4.1, §4.2, §9 · [DESIGN.md](../../../DESIGN.md) · [Lộ trình](2026-10-09-00-roadmap.md) §5 (quy ước frontend), §7.3, §7.4 · API thật: [Plan 02](2026-10-09-02-accounts-settings-email.md) (`AuthController`, `MeController`, `UserResponse`)

## Global Constraints

- **Plan 03 chưa xong.** Plan này làm luôn ba phần của Plan 03 mà màn tài khoản cần: token DESIGN.md trong `@theme`, `rewrites` `/api/*`, `apiFetch`, `proxy.ts`. Phần còn lại của Plan 03 (`gen:api`, Playwright smoke cho mọi route) vẫn thuộc Plan 03. Kiểu dữ liệu API viết tay trong `src/lib/api.ts` cho tới khi có `gen:api`.
- **Token (lộ trình §7.3, §7.4):** `--spacing: 1px` nên `p-22` = 22px. Line-height viết bằng px qua `--text-*--line-height`. Nút chính màu `#000d10`, không dùng clay trên các màn này. Không đổi `landing.css` (landing vẫn dùng CSS riêng scope `.sk`).
- **Hình khối:** nút là pill (radius 1000px), ô nhập và panel là khối vuông (radius 0) viền hairline `pebble`, giống ô tìm chuyến trên landing. Không dùng shadow.
- **Màu chữ phụ:** `cool-ash` (`#8e8e95`) chỉ đạt 3.3:1 trên nền trắng, không đủ WCAG AA cho chữ 17–18px. Chữ phụ dùng token `muted` (`#64646c`, 5.9:1), cùng giá trị landing đang dùng.
- **Chế độ tối:** theo `prefers-color-scheme`, cùng bảng màu tối của landing (`#0f0f1c`, `#f2f3f6`...). Token ngữ nghĩa (`ink`, `paper`, `muted`, `line`) đổi giá trị trong media query, nên class Tailwind không cần `dark:`.
- **CSRF (TDD §4.2):** mọi request ghi gửi header `X-XSRF-TOKEN` lấy từ cookie `XSRF-TOKEN`. Chưa có cookie thì gọi `GET /api/auth/csrf` trước.
- **Lỗi:** `apiFetch` ném `ApiError` có `status`, `code`, `detail` và `errors[{field, message}]` (ProblemDetail của Plan 01). Lỗi theo trường hiện ngay dưới ô nhập; lỗi chung hiện trên nút gửi. Câu lỗi lấy từ `detail`/`message` của backend (đã là tiếng Việt).
- **`next` (chống open redirect):** chỉ nhận đường dẫn bắt đầu bằng `/` và không bắt đầu bằng `//` hoặc `/\`. Sai thì dùng trang mặc định của vai trò: `CUSTOMER → /bookings`, `STAFF → /staff/bookings`, `ADMIN → /admin/flights` (APP_FLOW §1).
- **Mật khẩu (BR-100):** kiểm tra ở client cùng luật với backend (≥ 8 ký tự, có chữ và số) chỉ để báo sớm; backend vẫn là nơi quyết định.
- **Không gọi `new Date()`/`Date.now()` khi render** (Cache Components). Component dùng `useSearchParams` phải nằm trong `<Suspense>`.
- **Chữ hiển thị:** tiếng Việt, không dùng gạch dài (em-dash).

### Điểm plan tự chọn (APP_FLOW không nói)

1. Sau đăng nhập không gọi lại `GET /auth/csrf` (APP_FLOW C-03 có ghi). Cookie `XSRF-TOKEN` của `csrf.spa()` không gắn với session nên vẫn dùng được sau khi đổi session id; `apiFetch` tự lấy token khi thiếu.
2. `proxy.ts` chỉ kiểm tra có cookie `JSESSIONID` hay không. Phiên hết hạn nhưng cookie còn thì trang vẫn mở, `GET /me` trả 401 và trang tự chuyển về `/login?next=`.
3. Trang mặc định của Staff và Admin (`/staff/bookings`, `/admin/flights`) chưa có tới Plan 18 và 20, nên tạm thời là 404.
4. Thanh điều hướng của landing chưa đổi theo trạng thái đăng nhập (vẫn hiện "Đăng nhập"). Việc này để Plan 13, khi có thêm các màn Customer.

## Review Focus

1. **Open redirect qua `next`:** `/login?next=//evil.com` hoặc `next=https://evil.com` phải về trang mặc định, không rời site (`safeNext`).
2. **POST đầu tiên của trình duyệt mới** (chưa có cookie `XSRF-TOKEN`) không được 403: `apiFetch` gọi `GET /api/auth/csrf` trước.
3. **Đổi mật khẩu sai mật khẩu hiện tại** trả 400 có `errors[{field: "currentPassword"}]` (Plan 02, điểm khác TDD 3): lỗi phải hiện dưới ô "Mật khẩu hiện tại", không được hiểu thành hết phiên.
4. **Token đặt lại mật khẩu sai hoặc đã dùng** (`INVALID_TOKEN`) hiện lời nhắc và link làm lại từ C-05 (UF-06 bước 3).
5. **`/reset-password` không có `token`** không gửi request mà báo link không hợp lệ ngay.

---

## Trước khi bắt đầu

- **Cần có:** Plan 02 xong; Node 24; Docker Desktop đang chạy (E2E cần backend thật).
- **Nhánh:** `feature/account-screens` từ `develop`.
- **Chạy E2E:** `docker compose up -d postgres mailpit`, chạy backend profile `dev` (`cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`), rồi `cd frontend && npm run e2e`. Playwright tự bật `npm run dev`.

## Cấu trúc file sau plan

```
frontend/
  next.config.ts                     # + rewrites /api/* → BACKEND_URL
  playwright.config.ts               # mới
  e2e/account.spec.ts                # mới: đăng ký → đăng xuất → đăng nhập → sửa hồ sơ → đổi mật khẩu → đăng xuất
  src/
    proxy.ts                         # mới: /account, /bookings, /staff, /admin cần cookie phiên
    app/globals.css                  # + @theme token DESIGN.md
    lib/api.ts                       # mới: apiFetch, ApiError, kiểu User, safeNext, homeOf
    components/auth/
      auth-shell.tsx                 # layout chia đôi
      field.tsx                      # ô nhập có nhãn, lỗi, hiện/ẩn mật khẩu
      form-state.ts                  # useForm: giữ lỗi theo trường và lỗi chung từ ApiError
    app/(auth)/layout.tsx            # dùng AuthShell
    app/(auth)/login/page.tsx        # C-03 (chuyển từ app/login)
    app/(auth)/register/page.tsx     # C-04
    app/(auth)/forgot-password/page.tsx  # C-05
    app/(auth)/reset-password/page.tsx   # C-06 (mới)
    app/account/page.tsx             # C-12
```

---

### Task 1: Token DESIGN.md trong `globals.css`

**Files:** `frontend/src/app/globals.css`

- [ ] Thêm `@theme` gồm: 7 màu của DESIGN.md (`deep-ink`, `pure-white`, `cool-ash`, `pebble`, `midnight-hull`, `charcoal-deck`, `clay-ember`); token ngữ nghĩa `ink`, `paper`, `muted`, `line`, `line-strong`, `tint`, `btn`, `btn-fg`, `btn-hover` lấy giá trị sáng của landing; `--font-sans` trỏ về biến của `next/font`; thang chữ 9 cỡ (`caption` … `hero`) kèm `--text-*--line-height` bằng px và `--text-*--letter-spacing`; `--spacing: 1px`; `--radius-pill: 1000px`; `--container-page: 1200px`.
- [ ] Token ngữ nghĩa đổi sang giá trị tối trong `@media (prefers-color-scheme: dark)`.
- [ ] `body` mặc định: nền `paper`, chữ `ink`, 18px/29px, font-sans. Giữ nền `#000d10` của `html`.
- [ ] Thay class `text-neutral-*` trong `placeholder-page.tsx` bằng token.
- [ ] Kiểm chứng: `cd frontend && npm run lint && npm run build`. Expected: build xanh; mở `/` thấy landing không đổi.
- [ ] Commit `feat(frontend): token DESIGN.md trong @theme`.

### Task 2: `rewrites`, `apiFetch`, `proxy.ts`

**Files:** `next.config.ts`, `src/lib/api.ts`, `src/proxy.ts`

- [ ] `rewrites`: `/api/:path*` → `${process.env.BACKEND_URL ?? "http://localhost:8080"}/api/:path*`.
- [ ] `apiFetch<T>(path, init?)`: thêm `Content-Type: application/json` khi có body; với method khác GET/HEAD thì đảm bảo có cookie `XSRF-TOKEN` (thiếu thì `GET /api/auth/csrf`) rồi gửi `X-XSRF-TOKEN`; `credentials: "same-origin"`; 204 trả `undefined`; lỗi đọc ProblemDetail và ném `ApiError`.
- [ ] `safeNext(next, role)` và `homeOf(role)` theo Global Constraints.
- [ ] `proxy.ts`: matcher `/account/:path*`, `/bookings/:path*`, `/staff/:path*`, `/admin/:path*`; thiếu cookie `JSESSIONID` thì redirect `/login?next=<path + query>`.
- [ ] Kiểm chứng: `npm run build`. Expected: xanh, build log có `Proxy`. Có backend chạy: `curl -i localhost:3000/api/auth/csrf` → 204 có `Set-Cookie: XSRF-TOKEN`; `curl -i localhost:3000/account` → 307 về `/login?next=%2Faccount`.
- [ ] Commit `feat(frontend): apiFetch, rewrites /api và proxy chặn route cần đăng nhập`.

### Task 3: Khung màn xác thực và C-03, C-04

**Files:** `components/auth/*`, `app/(auth)/layout.tsx`, `app/(auth)/login/page.tsx`, `app/(auth)/register/page.tsx`; xoá `app/login`, `app/register`.

- [ ] `AuthShell`: lưới 2 cột từ `lg` (form 1fr, ảnh 1fr); dưới `lg` chỉ còn cột form, rộng tối đa 440px, lề 16px trên điện thoại. Cột form có logo về `/`. Cột ảnh: nền bầu trời chuyển màu như hero landing, ảnh `skyline-climb-jet.webp` (`next/image`, `priority`), câu "Cất cánh." 63px.
- [ ] `Field`: nhãn trên ô, ô vuông viền `line`, focus viền `ink` 2px; lỗi dưới ô (`role="alert"`, `aria-invalid`, `aria-describedby`); ô mật khẩu có nút Hiện/Ẩn.
- [ ] C-03: email, mật khẩu, nút "Đăng nhập", link "Quên mật khẩu?" và "Tạo tài khoản" (giữ `next`). Thành công → `router.replace(safeNext(next, user.role))`.
- [ ] C-04: họ tên, email, số điện thoại, mật khẩu (gợi ý luật BR-100 dưới ô). `POST /auth/register` rồi chuyển trang như đăng nhập. `EMAIL_ALREADY_USED` hiện dưới ô email.
- [ ] Kiểm chứng: `npm run lint && npm run build` xanh; mở `/login`, `/register` ở 375px và 1440px, sáng và tối.
- [ ] Commit `feat(frontend): màn đăng nhập và đăng ký (C-03, C-04)`.

### Task 4: C-05, C-06

**Files:** `app/(auth)/forgot-password/page.tsx`, `app/(auth)/reset-password/page.tsx`; xoá `app/forgot-password`.

- [ ] C-05: ô email. Gửi xong luôn hiện "Nếu email này có tài khoản, chúng tôi đã gửi link đặt lại mật khẩu. Link có hiệu lực trong 30 phút." và link về đăng nhập.
- [ ] C-06: đọc `token` từ URL; không có thì báo link không hợp lệ. Hai ô mật khẩu mới, phải khớp. Thành công → `/login?reset=1`, C-03 hiện "Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới." `INVALID_TOKEN` → báo link hết hạn hoặc đã dùng, link sang `/forgot-password`.
- [ ] Kiểm chứng: lint, build xanh.
- [ ] Commit `feat(frontend): quên và đặt lại mật khẩu (C-05, C-06)`.

### Task 5: C-12 Tài khoản

**Files:** `app/account/page.tsx`, `components/account/*`

- [ ] Đầu trang: logo, link về trang chủ, nút "Đăng xuất" (pill viền). Tiêu đề là họ tên, dưới là email và vai trò.
- [ ] Khối "Hồ sơ": họ tên, số điện thoại (email chỉ đọc). `PUT /me`; xong hiện "Đã lưu hồ sơ." cạnh nút.
- [ ] Khối "Đổi mật khẩu": mật khẩu hiện tại, mật khẩu mới, nhập lại. `PUT /me/password`; xong xoá ô và hiện "Đã đổi mật khẩu.".
- [ ] Đang tải: khung xương đúng hình bố cục. `GET /me` trả 401 → `/login?next=/account`.
- [ ] Đăng xuất: `POST /auth/logout` rồi `router.replace("/login")`.
- [ ] Kiểm chứng: lint, build xanh.
- [ ] Commit `feat(frontend): màn tài khoản (C-12)`.

### Task 6: E2E và cập nhật tài liệu

**Files:** `package.json` (+ `@playwright/test`, script `e2e`), `playwright.config.ts`, `e2e/account.spec.ts`, roadmap §2 và §6, `CLAUDE.md`.

- [ ] Một spec: đăng ký email ngẫu nhiên → về `/bookings` → mở `/account` → sửa họ tên → tải lại thấy tên mới → đổi mật khẩu (thử sai mật khẩu hiện tại trước, thấy lỗi dưới ô) → đăng xuất → `/account` chuyển về `/login?next=%2Faccount` → đăng nhập bằng mật khẩu mới → quay lại `/account`. Thêm một test `next=//evil.com` không rời site.
- [ ] Kiểm chứng: có backend chạy, `npm run e2e`. Expected: 2 passed.
- [ ] Tick "Xong" Plan 04 ở roadmap §2, thêm dòng 04 ở §6, ghi phần Plan 03 đã làm; CLAUDE.md cập nhật trạng thái frontend và lệnh `npm run e2e`.
- [ ] Commit `test(frontend): E2E luồng tài khoản` và `docs: cập nhật lộ trình sau Plan 04`.

## Kiểm tra cuối plan

`cd frontend && npm run lint && npm run build && npm run e2e` xanh (backend profile `dev` đang chạy).
