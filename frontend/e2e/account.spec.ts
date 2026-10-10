import { expect, test, type Page } from "@playwright/test";

// Ô mật khẩu có nút "Hiện mật khẩu ..." nên tìm theo nhãn phải khớp chính xác.
const field = (page: Page, label: string) =>
  page.getByLabel(label, { exact: true });

test("đăng ký, sửa hồ sơ, đổi mật khẩu, đăng xuất rồi đăng nhập lại", async ({
  page,
}) => {
  const email = `e2e-${test.info().testId}-${Date.now()}@example.com`;

  // C-04: đăng ký xong backend đăng nhập luôn, Customer về /bookings (APP_FLOW §1).
  await page.goto("/register");
  await field(page, "Họ và tên").fill("Lê Thu Hà");
  await field(page, "Email").fill(email);
  await field(page, "Số điện thoại").fill("0938271604");
  await field(page, "Mật khẩu").fill("matkhau1");
  await page.getByRole("button", { name: "Tạo tài khoản" }).click();
  await expect(page).toHaveURL("/bookings");

  // C-12: sửa hồ sơ, tải lại vẫn thấy tên mới.
  await page.goto("/account");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Lê Thu Hà");
  await field(page, "Họ và tên").fill("Lê Thu Hà Phương");
  await page.getByRole("button", { name: "Lưu hồ sơ" }).click();
  await expect(page.getByText("Đã lưu hồ sơ.")).toBeVisible();
  await page.reload();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Lê Thu Hà Phương",
  );

  // Sai mật khẩu hiện tại: lỗi nằm dưới đúng ô đó, phiên vẫn còn.
  await field(page, "Mật khẩu hiện tại").fill("saimatkhau9");
  await field(page, "Mật khẩu mới").fill("matkhau2");
  await field(page, "Nhập lại mật khẩu mới").fill("matkhau2");
  await page.getByRole("button", { name: "Đổi mật khẩu" }).click();
  await expect(field(page, "Mật khẩu hiện tại")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  await expect(page.getByText("Mật khẩu hiện tại không đúng")).toBeVisible();

  await field(page, "Mật khẩu hiện tại").fill("matkhau1");
  await page.getByRole("button", { name: "Đổi mật khẩu" }).click();
  await expect(page.getByText("Đã đổi mật khẩu.")).toBeVisible();

  // Đăng xuất: route cần đăng nhập chuyển về /login?next=.
  await page.getByRole("button", { name: "Đăng xuất" }).click();
  await expect(page).toHaveURL("/login");
  await page.goto("/account");
  await expect(page).toHaveURL("/login?next=%2Faccount");

  // C-03: mật khẩu cũ không còn dùng được, mật khẩu mới thì đưa về đúng next.
  await field(page, "Email").fill(email);
  await field(page, "Mật khẩu").fill("matkhau1");
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page.getByText("Email hoặc mật khẩu không đúng")).toBeVisible();
  await field(page, "Mật khẩu").fill("matkhau2");
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page).toHaveURL("/account");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Lê Thu Hà Phương",
  );
});

test("next trỏ ra ngoài site thì về trang mặc định của vai trò", async ({
  page,
}) => {
  await page.goto("/login?next=//example.org/phish");
  await field(page, "Email").fill("customer@demo.local");
  await field(page, "Mật khẩu").fill("Demo@1234");
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page).toHaveURL("/bookings");
});
