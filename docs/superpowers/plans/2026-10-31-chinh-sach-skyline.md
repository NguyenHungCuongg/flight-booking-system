# Bộ chính sách SkyLine — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ba trang chính sách (điều khoản, quyền riêng tư, hoàn/đổi/hành lý) viết bằng Markdown, số liệu động lấy từ endpoint công khai mới `GET /api/settings/public`.

**Architecture:** Backend thêm một controller chỉ đọc ở module `common`, trả 8 tham số của `SettingKey`. Frontend đọc file `frontend/content/chinh-sach/*.md` lúc prerender (đồng bộ, nằm trong static shell), còn phần thân văn bản lấy tham số lúc có request (`connection()` trong `Suspense`), thay chỗ giữ chỗ `{{khoá}}` rồi dựng HTML bằng `marked` phía server. Logic thuần (thay chỗ giữ chỗ, sinh `id` heading, mục lục) nằm trong `src/lib/policies.ts` và được unit test không cần trình duyệt.

**Tech Stack:** Spring Boot 4 / Java 21, Next.js 16 (Cache Components), Tailwind 4, `marked`, Playwright.

**Spec:** [docs/superpowers/specs/2026-10-31-chinh-sach-skyline-design.md](../specs/2026-10-31-chinh-sach-skyline-design.md)

## Global Constraints

- Nhánh `feature/chinh-sach` (tách từ `develop`). Không sửa gì trong `agent/`.
- Văn bản, comment, thông báo lỗi bằng tiếng Việt.
- Mọi câu trong văn bản phải đúng với code trên nhánh này; không thêm quy tắc PRD không có; không nhắc tới trợ lý AI.
- Không ghi cứng số của 8 tham số Admin sửa được; dùng `{{khoá}}`. Hằng số không cấu hình được (5 phút chờ BR-45, hộ chiếu còn hạn 6 tháng, phiên hết hạn sau 30 phút không hoạt động, link đặt lại mật khẩu sống 30 phút) ghi thẳng.
- Pháp nhân hư cấu "Công ty TNHH SkyLine (pháp nhân mô phỏng)", email đuôi `.example`, ghi rõ là văn bản mô phỏng của đồ án môn học. Không dùng danh tính có thật.
- Frontend theo `DESIGN.md`: role token (không hex), line-height viết bằng px, `--spacing: 1px` (`p-16` = 16px), đúng ở cả giao diện sáng và tối.
- Cache Components: không `new Date()`/`Date.now()` khi render; `next build` phải chạy được khi backend tắt.
- `marked` cài bằng `npm install --save-exact marked` (dependencies của repo ghi version chính xác).

## Review Focus

1. **Backend tắt hoặc trả lỗi** khi khách mở trang → app hiện trang lỗi chung (`app/error.tsx`), không hiện văn bản thiếu số hay `{{...}}`. Test: Task 2 `fillSettings` ném lỗi khi thiếu giá trị; Task 4 Step kiểm tay với backend tắt.
2. **Gõ sai tên khoá trong Markdown** (`{{booking.hold_minute}}`) → bị bắt khi chạy unit test, không đợi tới khi khách mở trang. Test: Task 3 `mọi chỗ giữ chỗ là khoá công khai`.
3. **Chỗ giữ chỗ nằm trong heading** → mục lục (tĩnh) và `id` sẽ lệch với nội dung đã thay số. Test: Task 3 `heading không chứa chỗ giữ chỗ`.
4. **Heading trùng tên trong một văn bản** → hai mục cùng `id`, link neo nhảy sai. Test: Task 3 `id heading không trùng trong mỗi văn bản`.
5. **Tham số mới thêm vào `SettingKey` sau này** → không được tự lộ ra endpoint công khai. Test: Task 1 `trả đúng 8 khoá` so với danh sách viết tay trong test, không so với `SettingKey.values()`.

---

## File Structure

| File                                                                                                                         | Trách nhiệm                                                                                                      |
| ---------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `backend/.../common/web/PublicSettingsController.java` (tạo)                                                                 | `GET /api/settings/public`                                                                                       |
| `backend/.../common/web/SecurityConfig.java` (sửa)                                                                           | Mở quyền công khai cho endpoint                                                                                  |
| `backend/src/test/.../common/web/PublicSettingsControllerTest.java` (tạo)                                                    | Integration test                                                                                                 |
| `frontend/src/lib/policies.ts` (tạo)                                                                                         | Danh sách văn bản, 8 khoá, `fillSettings`, `headingId`, `sections`, `renderPolicy`                               |
| `frontend/e2e/policies.spec.ts` (tạo)                                                                                        | Unit test logic và kiểm nội dung ba file Markdown                                                                |
| `frontend/content/chinh-sach/{dieu-khoan,quyen-rieng-tu,hoan-doi-hanh-ly}.md` (tạo)                                          | Nội dung văn bản                                                                                                 |
| `frontend/src/app/chinh-sach/policy-page.tsx` (tạo)                                                                          | Khung trang chung: header, danh sách văn bản, mục lục, thân văn bản                                              |
| `frontend/src/app/chinh-sach/page.tsx` + `dieu-khoan/page.tsx`, `quyen-rieng-tu/page.tsx`, `hoan-doi-hanh-ly/page.tsx` (tạo) | Route. Ba route tĩnh thay vì `[slug]`: Cache Components không cho `dynamicParams`, route tĩnh tự 404 với slug lạ |
| `frontend/src/app/globals.css` (sửa)                                                                                         | Kiểu chữ cho HTML sinh từ Markdown (lớp `.policy`)                                                               |
| `frontend/src/components/landing/footer.tsx`, `frontend/src/app/(auth)/register/register-form.tsx` (sửa)                     | Link vào trang                                                                                                   |
| `frontend/e2e/smoke.spec.ts`, `frontend/e2e/demo.spec.ts` (sửa)                                                              | Route mới; tham số đổi thì trang đổi                                                                             |
| `docs/TDD.md`, `docs/superpowers/plans/2026-10-09-00-roadmap.md`, `CLAUDE.md` (sửa)                                          | Ghi lại API và trạng thái                                                                                        |

---

### Task 1: Endpoint `GET /api/settings/public`

**Files:**

- Create: `backend/src/main/java/vn/edu/uit/flightbooking/common/web/PublicSettingsController.java`
- Modify: `backend/src/main/java/vn/edu/uit/flightbooking/common/web/SecurityConfig.java:25`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/common/web/PublicSettingsControllerTest.java`
- Modify: `docs/TDD.md` (§4.3 bảng quyền quanh dòng 171, §8.2 bảng API), `frontend/src/lib/api-types.ts` (sinh lại)

**Interfaces:**

- Consumes: `SettingsApi.getInt(SettingKey)`, enum `SettingKey` (`key()` trả chuỗi như `booking.hold_minutes`).
- Produces: `GET /api/settings/public` → `200`, JSON object `{ "<khoá>": <int>, ... }` đúng 8 khoá; schema OpenAPI là map `string → integer`.

- [ ] **Step 1: Viết test hỏng** — `PublicSettingsControllerTest`, `@IntegrationTest @Transactional`, theo mẫu `AdminSettingsControllerTest`:

```java
static final Set<String> PUBLIC_KEYS = Set.of("booking.hold_minutes", "booking.min_hours_before_departure",
		"booking.max_seated_passengers", "pricing.child_percent", "pricing.infant_percent",
		"aftersales.min_hours_before_departure", "search.min_connection_minutes", "search.max_connection_minutes");

@Test
void anonymousGetsExactlyThePublicKeys() // GET không session: 200; tập khoá của JSON == PUBLIC_KEYS;
                                         // $['booking.hold_minutes'] == 15, $['pricing.child_percent'] == 90
@Test
void changedSettingShowsOnNextRead()     // Admin PUT /api/admin/settings/booking.hold_minutes {"value": 20}
                                         // rồi GET không session: $['booking.hold_minutes'] == 20
```

JsonPath với khoá có dấu chấm viết dạng `$['booking.hold_minutes']`. Lấy tập khoá bằng cách đọc body qua `ObjectMapper` thành `Map`.

- [ ] **Step 2: Chạy test, thấy hỏng**

Run: `cd backend && ./mvnw test -Dtest=PublicSettingsControllerTest`
Expected: FAIL, `anonymousGetsExactlyThePublicKeys` nhận 401 (chưa có route, rơi vào `anyRequest().authenticated()`).

- [ ] **Step 3: Viết `PublicSettingsController`** — `@RestController @RequestMapping("/api/settings")`, package-private như `AdminSettingsController`, `@GetMapping("/public") Map<String, Integer> publicSettings()`. Danh sách khoá công khai là hằng `List<SettingKey> PUBLIC` viết tay đủ 8 phần tử (không dùng `SettingKey.values()`), trả `LinkedHashMap` theo thứ tự đó. Javadoc tiếng Việt nói rõ vì sao danh sách viết tay (spec §4). Thêm `"/api/settings/public"` vào matcher `HttpMethod.GET ... permitAll()` ở `SecurityConfig:25`.

- [ ] **Step 4: Chạy test, thấy qua**

Run: `cd backend && ./mvnw test -Dtest=PublicSettingsControllerTest+SecurityConfigTest+ModularityTest`
Expected: PASS.

- [ ] **Step 5: Cập nhật tài liệu và kiểu API**
  - `docs/TDD.md` §4.3: thêm `GET /api/settings/public` vào dòng công khai. §8.2: thêm dòng `| GET | \`/settings/public\` | Công khai | 8 tham số khách cần biết (PRD §7), dạng \`{ khoá: số }\` | Chính sách |`.
  - Chạy backend (`./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"`), rồi `cd frontend && npm run gen:api`. Kiểm `git diff src/lib/api-types.ts` có path `/api/settings/public`.

- [ ] **Step 6: Commit**

```bash
git add backend docs/TDD.md frontend/src/lib/api-types.ts
git commit -m "feat(common): GET /api/settings/public trả 8 tham số cho khách"
```

---

### Task 2: Logic văn bản chính sách (`src/lib/policies.ts`)

**Files:**

- Create: `frontend/src/lib/policies.ts`
- Test: `frontend/e2e/policies.spec.ts`
- Modify: `frontend/package.json`, `frontend/package-lock.json` (thêm `marked`)

**Interfaces:**

- Consumes: không.
- Produces:
  - `POLICIES: readonly { slug: PolicySlug; title: string }[]`, theo thứ tự: `dieu-khoan` "Điều khoản & điều kiện", `quyen-rieng-tu` "Chính sách quyền riêng tư", `hoan-doi-hanh-ly` "Chính sách hoàn, đổi vé và hành lý".
  - `type PolicySlug = "dieu-khoan" | "quyen-rieng-tu" | "hoan-doi-hanh-ly"`.
  - `SETTING_KEYS`: tuple `as const` của đúng 8 khoá như Task 1.
  - `type PublicSettings = Record<string, number>`.
  - `PLACEHOLDER: RegExp` = `/\{\{([a-z_.]+)\}\}/g` (Task 3 dùng để quét file).
  - `fillSettings(md: string, settings: PublicSettings): string` — thay mọi `{{khoá}}`; khoá không thuộc `SETTING_KEYS` hoặc `settings` không có giá trị thì `throw new Error("Thiếu tham số <khoá>")`. Số in bằng `String(n)`.
  - `headingId(text: string): string` — chữ thường, bỏ dấu (`normalize("NFD")`, bỏ `\p{M}`, `đ`→`d`), ký tự không phải `a-z0-9` thành `-`, gộp và cắt `-` ở hai đầu.
  - `sections(md: string): { id: string; title: string }[]` — các dòng bắt đầu bằng `## ` theo thứ tự, `id = headingId(title)`.
  - `renderPolicy(md: string): string` — HTML từ `marked` (đồng bộ), mọi `<h2>`/`<h3>` có `id = headingId(text thuần của heading)`.
  - File này không import `node:fs` (giữ thuần để test và để không kéo vào bundle client).

- [ ] **Step 1: Cài `marked`**

Run: `cd frontend && npm install --save-exact marked`
Expected: `package.json` có `"marked": "<x.y.z>"` không có `^`.

- [ ] **Step 2: Viết test hỏng** — `e2e/policies.spec.ts`, mở đầu bằng comment như `itineraries.spec.ts` ("Unit test ...: không mở trình duyệt, không cần backend."):

```ts
test("fillSettings thay mọi chỗ giữ chỗ", () => {
  expect(
    fillSettings(
      "Giữ {{booking.hold_minutes}} phút, trẻ em {{pricing.child_percent}}%.",
      { "booking.hold_minutes": 15, "pricing.child_percent": 90 },
    ),
  ).toBe("Giữ 15 phút, trẻ em 90%.");
});
test("fillSettings báo lỗi với khoá lạ", () => {
  expect(() =>
    fillSettings("{{booking.hold_minute}}", { "booking.hold_minutes": 15 }),
  ).toThrow("Thiếu tham số booking.hold_minute");
});
test("fillSettings báo lỗi khi backend thiếu giá trị", () => {
  expect(() => fillSettings("{{booking.hold_minutes}}", {})).toThrow(
    "Thiếu tham số booking.hold_minutes",
  );
});
test("headingId bỏ dấu tiếng Việt", () => {
  expect(headingId("Đổi chuyến")).toBe("doi-chuyen");
  expect(headingId("Cookie và lưu trữ trên trình duyệt")).toBe(
    "cookie-va-luu-tru-tren-trinh-duyet",
  );
  expect(headingId("Giá & thanh toán")).toBe("gia-thanh-toan");
});
test("sections lấy heading cấp 2 theo thứ tự", () => {
  expect(
    sections("Mở đầu\n\n## Hoàn vé\n\n### Phí\n\n## Đổi chuyến\n"),
  ).toEqual([
    { id: "hoan-ve", title: "Hoàn vé" },
    { id: "doi-chuyen", title: "Đổi chuyến" },
  ]);
});
test("renderPolicy gắn id cho heading và dựng bảng", () => {
  const html = renderPolicy(
    "## Đổi chuyến\n\n| Gói | Phí |\n|---|---|\n| Eco | 0 |\n",
  );
  expect(html).toContain('<h2 id="doi-chuyen">Đổi chuyến</h2>');
  expect(html).toContain("<table>");
});
```

- [ ] **Step 3: Chạy test, thấy hỏng**

Run: `cd frontend && npx playwright test policies --project chromium`
Expected: FAIL, không tìm thấy module `../src/lib/policies`.

- [ ] **Step 4: Viết `src/lib/policies.ts`** theo Interfaces. Gắn `id` cho heading bằng một instance `new Marked({ renderer: { heading(...) } })`; đọc chữ ký `heading` của bản `marked` vừa cài trong `node_modules/marked/lib/marked.d.ts` (từ v13 nhận một token `{ tokens, depth, text }` và dùng `this.parser.parseInline(tokens)`). Gọi `parse(md, { async: false })` để nhận `string`.

- [ ] **Step 5: Chạy test, thấy qua**

Run: `cd frontend && npx playwright test policies --project chromium && npm run lint`
Expected: PASS, lint sạch.

- [ ] **Step 6: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/src/lib/policies.ts frontend/e2e/policies.spec.ts
git commit -m "feat(chinh-sach): thay tham số, sinh id heading và dựng Markdown cho văn bản chính sách"
```

---

### Task 3: Nội dung ba văn bản

**Files:**

- Create: `frontend/content/chinh-sach/dieu-khoan.md`, `frontend/content/chinh-sach/quyen-rieng-tu.md`, `frontend/content/chinh-sach/hoan-doi-hanh-ly.md`
- Test: `frontend/e2e/policies.spec.ts` (thêm)

**Interfaces:**

- Consumes: `POLICIES`, `SETTING_KEYS`, `PLACEHOLDER`, `sections` từ Task 2.
- Produces: ba file, tên file = `slug` + `.md`. Không có dòng `# ` (trang tự dựng `<h1>` từ `POLICIES[].title`). Mục lục và `id` lấy từ các dòng `## `.

**Khuôn mỗi file** (giống nhau ở cả ba): đoạn mở đầu gồm ngày hiệu lực `01/11/2026`, câu "Đây là văn bản mô phỏng của đồ án môn học, không có giá trị pháp lý.", và một câu nêu văn bản áp dụng cho dịch vụ của Công ty TNHH SkyLine (pháp nhân mô phỏng). Mục cuối `## Liên hệ` của cả ba file giống hệt nhau: tên pháp nhân, địa chỉ, mã số thuế, hotline, email `hotro@skyline.example` — người viết đặt địa chỉ, MST, hotline hư cấu và chép y nguyên sang ba file.

- [ ] **Step 1: Viết test hỏng** (thêm vào `policies.spec.ts`; đọc file bằng `readFileSync(new URL(\`../content/chinh-sach/${slug}.md\`, import.meta.url), "utf8")`hoặc`path.join(\_\_dirname, ...)`, theo cách các spec hiện có đang dùng được):

```ts
for (const { slug } of POLICIES) {
  test(`${slug}: mọi chỗ giữ chỗ là khoá công khai`, () => {
    for (const [, key] of md(slug).matchAll(PLACEHOLDER))
      expect(SETTING_KEYS).toContain(key);
  });
  test(`${slug}: không có {{ lạc`, () => {
    expect(md(slug).replace(PLACEHOLDER, "")).not.toContain("{{");
  });
  test(`${slug}: có mục, không có heading cấp 1`, () => {
    expect(sections(md(slug)).length).toBeGreaterThanOrEqual(4);
    expect(md(slug)).not.toMatch(/^# /m);
  });
  test(`${slug}: heading không chứa chỗ giữ chỗ`, () => {
    for (const s of sections(md(slug))) expect(s.title).not.toContain("{{");
  });
  test(`${slug}: id heading không trùng trong mỗi văn bản`, () => {
    const ids = sections(md(slug)).map((s) => s.id);
    expect(new Set(ids).size).toBe(ids.length);
  });
}
test("cả ba văn bản có cùng mục Liên hệ", () => {
  const contact = (s: string) => s.slice(s.indexOf("## Liên hệ"));
  expect(contact(md("quyen-rieng-tu"))).toBe(contact(md("dieu-khoan")));
  expect(contact(md("hoan-doi-hanh-ly"))).toBe(contact(md("dieu-khoan")));
});
test("hoàn đổi nhắc thời hạn giữ chỗ và hạn chót hoàn đổi", () => {
  expect(md("hoan-doi-hanh-ly")).toContain("{{booking.hold_minutes}}");
  expect(md("hoan-doi-hanh-ly")).toContain(
    "{{aftersales.min_hours_before_departure}}",
  );
});
```

- [ ] **Step 2: Chạy test, thấy hỏng**

Run: `cd frontend && npx playwright test policies --project chromium`
Expected: FAIL, `ENOENT` khi đọc file.

- [ ] **Step 3: Viết `dieu-khoan.md`** — mục và nguồn theo spec §3.2. Bắt buộc dùng chỗ giữ chỗ: `{{booking.min_hours_before_departure}}` (BR-01), `{{search.min_connection_minutes}}` và `{{search.max_connection_minutes}}` (BR-03), `{{booking.max_seated_passengers}}` (BR-11), `{{pricing.child_percent}}` và `{{pricing.infant_percent}}` (BR-21), `{{booking.hold_minutes}}` (BR-40). Mục hoàn đổi chỉ tóm tắt một đoạn và link `/chinh-sach/hoan-doi-hanh-ly`. Đối chiếu từng câu với PRD §6 (BR-01–06, BR-10–14, BR-20–25, BR-30–33, BR-40–46, BR-66, BR-83, BR-84, BR-100–105).

- [ ] **Step 4: Viết `quyen-rieng-tu.md`** — mục theo spec §3.1. Trước khi viết, kiểm sự thật trong code:
  - Trường VNPay nhận: đọc `backend/src/main/java/vn/edu/uit/flightbooking/payment/domain/Vnpay.java` (tham số `vnp_*` gửi đi, có `vnp_IpAddr`).
  - Lưu trữ trình duyệt: `grep -rn "localStorage\|sessionStorage" frontend/src` (hiện có `skyline-theme` trong `localStorage`; lựa chọn hành trình trong `sessionStorage`, đọc lại ở `/checkout`).
  - Cookie: phiên `JSESSIONID` hết hạn sau 30 phút không hoạt động, `XSRF-TOKEN`.
  - Không viết gì về trợ lý AI hay Gemini.

- [ ] **Step 5: Viết `hoan-doi-hanh-ly.md`** — mục theo spec §3.3, đối chiếu BR-50–53, BR-60–66, BR-70–75, BR-22. Bảng ví dụ điều kiện gói giá: chép 3–4 dòng y nguyên từ `backend/src/main/resources/db/seed/V102__seed_fare_families.sql` (VD VN Economy Lite, VN Economy Classic, VJ Eco, QH Economy Smart: hành lý xách tay, ký gửi, hoàn được không + phí, đổi được không + phí), ngay trên bảng ghi "Bảng dưới là ví dụ minh hoạ; điều kiện thực tế hiển thị khi bạn chọn chuyến." Mục đổi chuyến dùng `{{booking.hold_minutes}}` cho hạn thanh toán đổi (BR-73).

- [ ] **Step 6: Chạy test, thấy qua**

Run: `cd frontend && npx playwright test policies --project chromium`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/content frontend/e2e/policies.spec.ts
git commit -m "docs(chinh-sach): điều khoản, quyền riêng tư, hoàn đổi và hành lý"
```

---

### Task 4: Trang `/chinh-sach/*`

**Files:**

- Create: `frontend/src/app/chinh-sach/policy-page.tsx`, `frontend/src/app/chinh-sach/page.tsx`, `frontend/src/app/chinh-sach/dieu-khoan/page.tsx`, `frontend/src/app/chinh-sach/quyen-rieng-tu/page.tsx`, `frontend/src/app/chinh-sach/hoan-doi-hanh-ly/page.tsx`
- Modify: `frontend/src/app/globals.css`, `frontend/e2e/smoke.spec.ts:5-12`

**Interfaces:**

- Consumes: `POLICIES`, `PolicySlug`, `fillSettings`, `renderPolicy`, `sections` (Task 2); ba file Markdown (Task 3); `GET /api/settings/public` (Task 1); `SiteHeader` (`components/site-header.tsx`, mặc định `area="customer"`).
- Produces: `export default function PolicyPage({ slug }: { slug: PolicySlug })` (Server Component). Route `/chinh-sach` → `redirect("/chinh-sach/dieu-khoan")`.

- [ ] **Step 1: Thêm route vào smoke test** — thêm `"/chinh-sach"`, `"/chinh-sach/dieu-khoan"`, `"/chinh-sach/quyen-rieng-tu"`, `"/chinh-sach/hoan-doi-hanh-ly"` vào `PUBLIC` của `e2e/smoke.spec.ts`.

- [ ] **Step 2: Chạy smoke, thấy hỏng** (backend profile `dev` đang chạy)

Run: `cd frontend && npx playwright test smoke -g chinh-sach`
Expected: FAIL, status 404.

- [ ] **Step 3: Viết `policy-page.tsx`**
  - Đọc Markdown bằng `readFileSync(path.join(process.cwd(), "content/chinh-sach", \`${slug}.md\`), "utf8")`(đồng bộ nên hoàn tất lúc prerender, theo`node_modules/next/dist/docs/01-app/01-getting-started/08-caching.md` mục "Predictable values").
  - Phần tĩnh (nằm trong static shell): `<SiteHeader />`; `<h1>` = `title` của slug; nav "Văn bản" liệt kê `POLICIES`, mục đang mở có `aria-current="page"` (kiểu chọn giống bộ lọc trạng thái ở `app/bookings/booking-list.tsx`); mục lục từ `sections(md)` là link `#id`. Màn `lg` trở lên: hai cột, cột trái `sticky`. Dưới `lg`: nav văn bản thành hàng ngang cuộn được, ẩn mục lục.
  - Phần động: component async `PolicyBody({ md })` bọc trong `<Suspense>`: `await connection()` (từ `next/server`, để không gọi backend lúc build), `fetch(\`${process.env.BACKEND_URL ?? "http://localhost:8080"}/api/settings/public\`)`, `!res.ok`thì`throw new Error(...)`(để`app/error.tsx`hiện trang lỗi), rồi`<article className="policy" dangerouslySetInnerHTML={{ __html: renderPolicy(fillSettings(md, settings)) }} />`. Comment tiếng Việt ghi vì sao chèn HTML an toàn (nội dung là file trong repo).
  - Khung trang theo mẫu `booking-list.tsx`: `<div className="min-h-dvh">`, `<main className="mx-auto max-w-page px-16 pt-16 pb-80 sm:px-38 ...">`.

- [ ] **Step 4: Viết các route**
  - Ba `page.tsx` con: `export const metadata: Metadata = { title: "<title> | SkyLine" }` và `return <PolicyPage slug="..." />`.
  - `chinh-sach/page.tsx`: `redirect("/chinh-sach/dieu-khoan")` từ `next/navigation`.

- [ ] **Step 5: Kiểu chữ `.policy` trong `globals.css`** — dưới `@layer components` (hoặc chỗ các lớp dùng chung đang nằm): `h2` dùng `--text-heading-sm`, `h3` dùng `--text-subheading`, `p`/`li` dùng `--text-body`, màu `--color-ink`/`--color-muted`, link màu `--color-accent` có gạch chân, `ul`/`ol` có dấu đầu dòng, `table` viền `--color-line` và chữ `--text-table`, bảng nằm trong vùng cuộn ngang được trên màn hẹp (bọc bằng `overflow-x: auto` ở `article`, hoặc `display: block; overflow-x: auto` cho `table`), `h2` có `scroll-margin-top` đủ để không bị header che khi nhảy tới `#id`. Chỉ dùng biến token, không hex.

- [ ] **Step 6: Kiểm**

Run: `cd frontend && npx playwright test smoke -g chinh-sach && npm run lint && npm run build`
Expected: smoke PASS; lint sạch; build thành công. Chạy `npm run build` thêm một lần **khi backend tắt**: vẫn thành công.

- [ ] **Step 7: Kiểm bằng tay** (`npm run dev`, backend chạy): mở ba trang ở sáng/tối, rộng/hẹp (375px); bấm mục lục nhảy đúng mục; `/chinh-sach/abc` ra 404; tắt backend rồi tải lại `/chinh-sach/dieu-khoan` thì thấy trang lỗi chung, không thấy `{{`.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/chinh-sach frontend/src/app/globals.css frontend/e2e/smoke.spec.ts
git commit -m "feat(chinh-sach): trang điều khoản, quyền riêng tư, hoàn đổi và hành lý"
```

---

### Task 5: Link vào trang chính sách

**Files:**

- Modify: `frontend/src/components/landing/footer.tsx:81-97`, `frontend/src/app/(auth)/register/register-form.tsx:83-97`
- Test: `frontend/e2e/policies.spec.ts` (thêm test có trình duyệt, cần frontend chạy)

**Interfaces:**

- Consumes: `POLICIES` (Task 2), route Task 4, `textLink` từ `components/form.tsx`.

- [ ] **Step 1: Viết test hỏng**

```ts
test("chân trang trang chủ dẫn tới ba văn bản", async ({ page }) => {
  await page.goto("/");
  const footer = page.getByRole("contentinfo");
  for (const { slug, title } of POLICIES)
    await expect(footer.getByRole("link", { name: title })).toHaveAttribute(
      "href",
      `/chinh-sach/${slug}`,
    );
  await expect(
    footer.getByRole("link", { name: "Đổi và hoàn vé" }),
  ).toHaveAttribute("href", "/chinh-sach/hoan-doi-hanh-ly");
});
test("trang đăng ký dẫn tới điều khoản và quyền riêng tư", async ({ page }) => {
  await page.goto("/register");
  await expect(
    page.getByRole("link", { name: "Điều khoản & điều kiện" }),
  ).toHaveAttribute("href", "/chinh-sach/dieu-khoan");
  await expect(
    page.getByRole("link", { name: "Chính sách quyền riêng tư" }),
  ).toHaveAttribute("href", "/chinh-sach/quyen-rieng-tu");
});
```

- [ ] **Step 2: Chạy test, thấy hỏng**

Run: `cd frontend && npx playwright test policies -g "dẫn tới"`
Expected: FAIL, không tìm thấy link.

- [ ] **Step 3: Sửa footer** — thêm `<nav aria-label="Chính sách">` cùng kiểu với ba nhóm hiện có, tiêu đề "Chính sách", ba `<Link>` lặp từ `POLICIES` (chữ hiển thị = `title`). Đổi link "Đổi và hoàn vé" ở nhóm Hỗ trợ từ `#doi-hoan` sang `<Link href="/chinh-sach/hoan-doi-hanh-ly">`. Lưới hiện có `lg:grid-cols-[minmax(0,1.6fr)_repeat(3,minmax(0,1fr))]` đổi thành `repeat(4,...)`.

- [ ] **Step 4: Sửa form đăng ký** — dưới hàng nút "Tạo tài khoản", thêm `<p className="text-caption text-muted">` nội dung: "Bằng việc đăng ký, bạn đồng ý với [Điều khoản & điều kiện] và [Chính sách quyền riêng tư] của SkyLine." Hai link dùng `className={\`text-ink ${textLink}\`}` như link "Đăng nhập" bên cạnh.

- [ ] **Step 5: Chạy test, thấy qua**

Run: `cd frontend && npx playwright test policies account smoke && npm run lint`
Expected: PASS (`account.spec.ts` vẫn đăng ký được), lint sạch.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/landing/footer.tsx "frontend/src/app/(auth)/register/register-form.tsx" frontend/e2e/policies.spec.ts
git commit -m "feat(chinh-sach): link từ chân trang và trang đăng ký"
```

---

### Task 6: Tham số đổi thì văn bản đổi, và cập nhật tài liệu

**Files:**

- Modify: `frontend/e2e/demo.spec.ts` (thêm test sau D9), `CLAUDE.md` (mục Status), `docs/superpowers/plans/2026-10-09-00-roadmap.md` (§6 thêm dòng)

**Interfaces:**

- Consumes: hàm `login`, `csrfHeaders` đã có trong `demo.spec.ts`/`helpers.ts`; `GET /api/settings/public`; `PUT /api/admin/settings/{key}`.

- [ ] **Step 1: Viết test** — trong `demo.spec.ts`, ngay sau test D9:

```ts
test("Chính sách: đổi booking.hold_minutes thì trang hoàn đổi hiện số mới", async ({
  page,
}) => {
  await login(page, "admin@demo.local", "/admin/flights");
  const original = (
    (await (await page.request.get("/api/settings/public")).json()) as Record<
      string,
      number
    >
  )["booking.hold_minutes"];
  const changed = original === 20 ? 25 : 20;
  const put = (value: number) =>
    csrfHeaders(page).then((headers) =>
      page.request.put("/api/admin/settings/booking.hold_minutes", {
        headers,
        data: { value },
      }),
    );
  expect((await put(changed)).status()).toBe(204);
  try {
    await page.goto("/chinh-sach/hoan-doi-hanh-ly");
    await expect(page.locator("article.policy")).toContainText(
      `${changed} phút`,
    );
    await expect(page.locator("article.policy")).not.toContainText("{{");
  } finally {
    expect((await put(original)).status()).toBe(204);
  }
});
```

- [ ] **Step 2: Chạy test**

Run: `cd frontend && npx playwright test --project demo --no-deps -g "Chính sách"`
Expected: PASS. (Test viết sau tính năng vì đây là kiểm chứng đầu-cuối; nếu nó qua ngay mà chưa từng thấy hỏng, sửa tạm `changed` thành số khác số in ra để thấy nó hỏng một lần, rồi trả lại.)

- [ ] **Step 3: Cập nhật tài liệu**
  - `CLAUDE.md` mục Status: thêm một câu: trang chính sách `/chinh-sach/{dieu-khoan,quyen-rieng-tu,hoan-doi-hanh-ly}` đọc Markdown ở `frontend/content/chinh-sach/` (nguồn chung cho web và agent Bước 7), tham số dạng `{{khoá}}` thay bằng `GET /api/settings/public` (spec `docs/superpowers/specs/2026-10-31-chinh-sach-skyline-design.md`).
  - Roadmap §6: thêm dòng "Chính sách" liệt kê `GET /api/settings/public` (8 khoá, `PublicSettingsController`), `src/lib/policies.ts` (`POLICIES`, `SETTING_KEYS`, `PLACEHOLDER`, `fillSettings`, `headingId`, `sections`, `renderPolicy`), `PolicyPage`, ba file Markdown.

- [ ] **Step 4: Kiểm toàn bộ**

Run: `cd backend && ./mvnw verify` rồi `cd frontend && npm run lint && npm run build && npx playwright test policies smoke`
Expected: tất cả xanh.

- [ ] **Step 5: Commit**

```bash
git add frontend/e2e/demo.spec.ts CLAUDE.md docs/superpowers/plans/2026-10-09-00-roadmap.md
git commit -m "test(chinh-sach): văn bản theo tham số hệ thống; cập nhật tài liệu"
```
