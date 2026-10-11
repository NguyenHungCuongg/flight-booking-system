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
