import { expect, test, type Page } from "@playwright/test";
import { day, field, issuedBooking } from "./helpers";

// E2E màn Staff S-01–S-06. Trình duyệt ở múi giờ New York (TDD §7).
test.use({ timezoneId: "America/New_York" });

/** Bỏ phiên hiện tại rồi đăng nhập Staff của seed V105: trang mặc định là S-01 (APP_FLOW §1). */
export async function loginStaff(page: Page) {
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
  await expect(page.getByRole("alert").first()).toContainText(
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

