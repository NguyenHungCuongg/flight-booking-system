import { expect, test } from "@playwright/test";
import { POLICIES } from "../src/lib/policies";

// Trang chính sách trên trình duyệt (cần backend để thân văn bản có số): smoke chỉ kiểm h1, mà trang lỗi cũng có h1.

for (const { slug } of POLICIES) {
  test(`/chinh-sach/${slug} hiện đủ thân văn bản`, async ({ page }) => {
    await page.goto(`/chinh-sach/${slug}`);
    const policy = page.locator("article.policy");
    await expect(policy).toBeVisible();
    await expect(policy).not.toContainText("{{");
  });
}

test("dòng văn bản không quá dài trên màn rộng", async ({ page }) => {
  await page.setViewportSize({ width: 1360, height: 900 });
  await page.goto("/chinh-sach/dieu-khoan");
  const box = await page.locator("article.policy").boundingBox();
  expect(box!.width).toBeLessThanOrEqual(760);
});
