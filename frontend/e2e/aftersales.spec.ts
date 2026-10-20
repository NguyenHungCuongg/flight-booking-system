import { expect, test, type Page } from "@playwright/test";
import type { Schemas } from "../src/lib/api";
import { dmy } from "../src/lib/booking";
import { day, field, issuedBooking, paidReturn } from "./helpers";

// E2E hậu mãi C-10, C-11 và thao tác hậu mãi trên C-09. Trình duyệt ở múi giờ New York (TDD §7).
test.use({ timezoneId: "America/New_York" });

const vnpay = (page: Page) =>
  page.route("https://sandbox.vnpayment.vn/**", (route) =>
    route.fulfill({ contentType: "text/html", body: "<h1>VNPay sandbox</h1>" }),
  );

test("hoàn chiều đi trên C-10 rồi theo dõi trên C-09 (UF-02)", async ({
  page,
}) => {
  test.slow();
  const { code, email } = await issuedBooking(page, "Economy Smart", [
    { from: "HAN", to: "SGN", date: day(3), flight: "QH201" },
    { from: "SGN", to: "HAN", date: day(5), flight: "QH202" },
  ]);
  const outbound = page.getByRole("article", { name: "Chiều đi" });
  await expect(page.getByText("Đã xuất vé", { exact: true })).toBeVisible();
  await outbound.getByRole("link", { name: "Hoàn chiều này" }).click();

  // C-10 (FR-50): giá trị chiều, phí hoàn, số tiền được hoàn (BR-52, BR-53, BR-61).
  await expect(page).toHaveURL(`/bookings/${code}/refund/outbound`);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Hoàn chiều đi.",
  );
  const amounts = page.getByRole("region", { name: "Số tiền hoàn" });
  await expect(amounts).toContainText("Giá trị chiều");
  await expect(amounts).toContainText("1.730.000 đ");
  await expect(amounts).toContainText("-500.000 đ");
  await expect(amounts.getByRole("definition").last()).toHaveText(
    "1.230.000 đ",
  );

  // Lý do bắt buộc (FR-51); bấm đúp chỉ gửi một yêu cầu.
  let posts = 0;
  page.on("request", (r) => {
    if (r.method() === "POST" && r.url().endsWith("/refund-requests")) posts++;
  });
  const send = page.getByRole("button", { name: "Gửi yêu cầu hoàn" });
  await send.click();
  await expect(page.getByText("Hãy nhập lý do hoàn vé.")).toBeVisible();
  await field(page, "Lý do hoàn vé").fill("Đổi kế hoạch công tác");
  await send.dblclick();

  await expect(page).toHaveURL(`/bookings/${code}?refund=sent`);
  await expect(page.getByRole("status")).toContainText(
    `Đã gửi yêu cầu hoàn. Nhân viên sẽ xét duyệt và báo kết quả qua email ${email}.`,
  );
  expect(posts).toBe(1);
  const requests = page
    .getByRole("region", { name: "Yêu cầu hoàn" })
    .getByRole("listitem");
  await expect(requests).toHaveCount(1);
  await expect(requests.first()).toContainText(
    "Chiều đi, Chờ duyệt, hoàn 1.230.000 đ",
  );
  await expect(requests.first()).toContainText("Lý do: Đổi kế hoạch công tác");
  // Chiều đi có yêu cầu đang mở thì không hoàn, đổi được nữa; chiều về vẫn được (BR-51).
  await expect(outbound.getByRole("link")).toHaveCount(0);
  await expect(
    page
      .getByRole("article", { name: "Chiều về" })
      .getByRole("link", { name: "Hoàn chiều này" }),
  ).toBeVisible();

  // Mở lại C-10 và C-11 bằng URL cũ: câu của backend, không có form hay danh sách (FR-50, FR-60).
  await page.goto(`/bookings/${code}/refund/outbound`);
  await expect(
    page.getByText("Chiều này đang có yêu cầu hoàn hoặc đổi chuyến chưa xong"),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Gửi yêu cầu hoàn" }),
  ).toHaveCount(0);
  await page.goto(`/bookings/${code}/reschedule/outbound`);
  await expect(
    page.getByText("Chiều này đang có yêu cầu hoàn hoặc đổi chuyến chưa xong"),
  ).toBeVisible();
  await expect(
    page.getByRole("list", { name: "Chuyến thay thế" }),
  ).toHaveCount(0);
  await page.getByRole("link", { name: `Đặt chỗ ${code}` }).click();
  await expect(page).toHaveURL(`/bookings/${code}`);
});

test("đổi chiều về, trả phí đổi qua VNPay (UF-03)", async ({ page }) => {
  test.slow();
  await vnpay(page);
  const { code } = await issuedBooking(page, "Economy Smart", [
    { from: "HAN", to: "SGN", date: day(3), flight: "QH201" },
    { from: "SGN", to: "HAN", date: day(5), flight: "QH202" },
  ]);
  await page
    .getByRole("article", { name: "Chiều về" })
    .getByRole("link", { name: "Đổi chiều này" })
    .click();

  // C-11: chiều hiện tại, ngày mặc định là ngày bay hiện tại; QH chỉ có QH202 mỗi ngày nên ngày đó không có lựa chọn.
  await expect(page).toHaveURL(`/bookings/${code}/reschedule/return`);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Đổi chiều về.",
  );
  await expect(
    page.getByRole("region", { name: "Chuyến hiện tại" }),
  ).toContainText(`QH202: 15:00 SGN → 17:10 HAN, ${dmy(day(5))}`);
  const date = field(page, "Ngày bay mới");
  await expect(date).toHaveValue(day(5));
  await expect(
    page.getByText("Không có chuyến thay thế trong ngày này."),
  ).toBeVisible();

  // Đổi ngày nhanh: danh sách của ngày cũ về muộn không đè ngày mới.
  await page.route(`**/reschedule-options?date=${day(5)}`, async (route) => {
    await new Promise((r) => setTimeout(r, 1500));
    await route.continue();
  });
  await date.fill(day(4));
  await date.fill(day(5));
  await date.fill(day(6));
  await page.waitForTimeout(2000);
  const option = page
    .getByRole("list", { name: "Chuyến thay thế" })
    .getByRole("article", { name: "Bamboo Airways QH202" });
  await expect(option).toContainText(dmy(day(6)));
  await expect(option).toContainText("Phải trả 300.000 đ");
  await expect(
    page.getByText("Không có chuyến thay thế trong ngày này."),
  ).toHaveCount(0);

  // Báo giá (BR-72): phí đổi 300.000 đ, cùng giá nên không có chênh lệch.
  await option.getByRole("button", { name: "Chọn" }).click();
  const quote = page.getByRole("region", { name: "Báo giá đổi chuyến" });
  await expect(quote).toContainText("Phí đổi chuyến");
  await expect(quote.getByRole("definition").last()).toHaveText("300.000 đ");
  let confirms = 0;
  page.on("request", (r) => {
    if (r.method() === "POST" && r.url().endsWith("/reschedules")) confirms++;
  });
  await quote.getByRole("button", { name: "Xác nhận đổi" }).dblclick();
  await expect(page).toHaveURL(
    /^https:\/\/sandbox\.vnpayment\.vn\/.*vnp_Amount=30000000/,
  );
  expect(confirms).toBe(1);

  // VNPay báo thất bại: yêu cầu đổi vẫn chờ, chiều về chưa đổi, thanh toán lại được (FR-62).
  await page.goto(`/bookings/${code}?payment=failed`);
  await expect(page.getByText("Thanh toán chưa thành công.")).toBeVisible();
  const changes = page
    .getByRole("region", { name: "Đổi chuyến" })
    .getByRole("listitem");
  await expect(changes).toHaveCount(1);
  await expect(changes.first()).toContainText(
    "Chiều về: QH202, Chờ thanh toán, phải trả 300.000 đ",
  );
  await expect(
    page.getByRole("article", { name: "Chiều về" }).getByRole("link"),
  ).toHaveCount(0);
  await page
    .getByRole("button", { name: "Thanh toán đổi chuyến 300.000 đ" })
    .click();
  await expect(page).toHaveURL(/^https:\/\/sandbox\.vnpayment\.vn\//);

  // Return URL thành công: chặng mới ACTIVE, yêu cầu Hoàn tất (BR-74).
  await page.goto(paidReturn(page.url()));
  await expect(page).toHaveURL(`/bookings/${code}?payment=success`);
  await expect(page.getByRole("article", { name: "Chiều về" })).toContainText(
    `QH202: 15:00 SGN → 17:10 HAN, ${dmy(day(6))}`,
  );
  await expect(changes.first()).toContainText(
    "Chiều về: QH202, Hoàn tất, phải trả 300.000 đ",
  );
});

test("đổi miễn phí hoàn tất ngay, ngày mặc định theo giờ sân bay", async ({
  page,
}) => {
  test.slow();
  const { code, email } = await issuedBooking(page, "Economy Flex", [
    { from: "HAN", to: "SGN", date: day(4), flight: "VN213" },
  ]);
  await page.goto(`/bookings/${code}/reschedule/outbound`);
  // VN213 cất cánh 06:00 giờ Hà Nội = 19:00 hôm trước ở New York: ô ngày vẫn là ngày Việt Nam.
  await expect(field(page, "Ngày bay mới")).toHaveValue(day(4));
  await field(page, "Ngày bay mới").fill(day(5));
  const option = page.getByRole("article", { name: "Vietnam Airlines VN213" });
  await expect(option).toContainText("Phải trả 0 đ");
  await option.getByRole("button", { name: "Chọn" }).click();
  await page
    .getByRole("region", { name: "Báo giá đổi chuyến" })
    .getByRole("button", { name: "Xác nhận đổi" })
    .click();

  await expect(page).toHaveURL(`/bookings/${code}?reschedule=done`);
  await expect(page.getByRole("status")).toContainText(
    `Đổi chuyến thành công. Vé mới đã được gửi tới ${email}.`,
  );
  await expect(page.getByRole("article", { name: "Chiều đi" })).toContainText(
    `VN213: 06:00 HAN → 08:10 SGN, ${dmy(day(5))}`,
  );
  await expect(page.getByRole("region", { name: "Đổi chuyến" })).toContainText(
    "Chiều đi: VN213, Hoàn tất, phải trả 0 đ",
  );
});
