import { expect, test, type Page } from "@playwright/test";

// Header chung và menu tên (spec 2026-10-19 điều hướng khách hàng). Cần backend profile dev (seed V105).

async function login(page: Page) {
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill("customer@demo.local");
  await page.getByLabel("Mật khẩu", { exact: true }).fill("Demo@1234");
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page).toHaveURL("/bookings");
}

const menuButton = (page: Page) =>
  page.getByRole("button", { name: /^Menu tài khoản/ }).first();

test("ô tìm trên /flights theo chế độ tối như trang chủ", async ({ page }) => {
  await page.emulateMedia({ colorScheme: "dark" });
  await page.goto("/flights");
  const box = page.getByRole("search", { name: "Tìm chuyến bay" });
  await expect(box).toBeVisible();
  const bg = await box.evaluate((el) => getComputedStyle(el).backgroundColor);
  expect(bg).not.toBe("rgb(255, 255, 255)");
});

test("chọn mục đang mở trong menu tên thì menu đóng", async ({ page }) => {
  await login(page);
  await page.goto("/account");
  await menuButton(page).click();
  const item = page.getByRole("link", { name: "Tài khoản", exact: true });
  await item.click();
  await expect(item).toBeHidden();
});

test("menu tên mở ngay dưới nút, cả màn rộng lẫn điện thoại", async ({
  page,
}) => {
  await login(page);
  for (const width of [1920, 375]) {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/bookings");
    await menuButton(page).click();
    const button = (await menuButton(page).boundingBox())!;
    const menu = (await page
      .getByRole("button", { name: "Đăng xuất" })
      .locator("..")
      .boundingBox())!;
    expect(menu.y).toBeGreaterThanOrEqual(button.y + button.height - 1);
    expect(
      Math.abs(menu.x + menu.width - (button.x + button.width)),
    ).toBeLessThan(24);
    await page.keyboard.press("Escape");
  }
});

test("menu tên trên trang chủ: Đăng xuất cùng cỡ chữ với các mục khác", async ({
  page,
}) => {
  await login(page);
  await page.goto("/");
  await page
    .getByRole("navigation", { name: "Điều hướng chính" })
    .getByRole("button", { name: /^Menu tài khoản/ })
    .click();
  const size = (name: string, role: "link" | "button") =>
    page
      .getByRole(role, { name, exact: true })
      .evaluate((el) => getComputedStyle(el).fontSize);
  expect(await size("Đăng xuất", "button")).toBe(
    await size("Tài khoản", "link"),
  );
});
