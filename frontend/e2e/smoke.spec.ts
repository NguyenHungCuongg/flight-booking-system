import { expect, test, type Page } from "@playwright/test";

// Smoke cho mọi route (Plan 03): trang mở được, có tiêu đề h1 và không có lỗi JavaScript.
// Thêm route mới thì thêm vào một trong hai danh sách dưới đây.
const PUBLIC = [
  "/",
  "/flights",
  "/login",
  "/register",
  "/forgot-password",
  "/reset-password",
];
const PROTECTED = ["/account", "/bookings"];

async function expectRenders(page: Page, path: string) {
  const errors: Error[] = [];
  page.on("pageerror", (e) => errors.push(e));
  const res = await page.goto(path);
  expect(res?.status()).toBeLessThan(400);
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  expect(errors).toEqual([]);
}

for (const path of PUBLIC) {
  test(`${path} hiển thị được`, ({ page }) => expectRenders(page, path));
}

for (const path of PROTECTED) {
  test(`${path} chưa đăng nhập thì chuyển về /login?next=`, async ({
    page,
  }) => {
    await page.goto(path);
    await expect(page).toHaveURL(`/login?next=${encodeURIComponent(path)}`);
  });
}

test("route cần đăng nhập hiển thị được sau khi đăng nhập", async ({
  page,
}) => {
  // Tài khoản demo của seed V105 (profile dev).
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill("customer@demo.local");
  await page.getByLabel("Mật khẩu", { exact: true }).fill("Demo@1234");
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page).toHaveURL("/bookings");
  for (const path of PROTECTED) await expectRenders(page, path);
});
