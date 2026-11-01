import { expect, test, type Page } from "@playwright/test";
import { dmy } from "../src/lib/booking";
import { csrfHeaders, day, field, issuedBooking } from "./helpers";

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

  // S-05 (APP_FLOW §2.2): ô "Trạng thái" để trống vẫn lọc đúng; giờ theo sân bay dù trình duyệt ở New York.
  await nav.getByRole("link", { name: "Chuyến bay" }).click();
  await field(page, "Điểm đi").selectOption("HAN");
  await field(page, "Điểm đến").selectOption("SGN");
  await field(page, "Ngày bay").fill(day(3));
  await field(page, "Hãng bay").selectOption("QH");
  await page.getByRole("button", { name: "Lọc", exact: true }).click();
  await expect(page).toHaveURL(
    `/staff/flights?from=HAN&to=SGN&date=${day(3)}&airline=QH&status=`,
  );
  const flight = page.getByRole("row", { name: /QH201/ });
  await expect(flight).toContainText(`10:00 ${dmy(day(3))}`);
  await expect(flight).toContainText("Theo lịch");
  await expect(flight).toContainText(/Phổ thông \d+\/\d+/);

  // S-06 (FR-72): khách của booking vừa tạo có trong manifest, link về S-02.
  await flight.getByRole("link", { name: "Hành khách" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Hành khách QH201.",
  );
  const passenger = page.getByRole("row", { name: new RegExp(code) });
  await expect(passenger).toContainText("PHAM THU HA");
  await expect(passenger).toContainText("Người lớn");
  await expect(passenger).toContainText("Phổ thông");
  await expect(passenger).toContainText(/\d{13}/);
  await expect(passenger).toContainText("0912345678");
  await passenger.getByRole("link", { name: code }).click();
  await expect(page).toHaveURL(`/staff/bookings/${code}`);

  await page.getByRole("link", { name: "Tra cứu đặt chỗ" }).click();
  await expect(page).toHaveURL("/staff/bookings");
});

test("Staff từ chối, duyệt rồi xác nhận hoàn thủ công (UF-02, FR-71)", async ({
  page,
}) => {
  // "Duyệt" gọi Refund API của VNPay sandbox: tối đa ~35 giây.
  test.setTimeout(180_000);
  page.on("dialog", (d) => d.accept());
  const { code } = await issuedBooking(page, "Economy Smart", [
    { from: "HAN", to: "SGN", date: day(3), flight: "QH201" },
    { from: "SGN", to: "HAN", date: day(5), flight: "QH202" },
  ]);
  const headers = await csrfHeaders(page);
  for (const [direction, reason] of [
    ["OUTBOUND", "Đổi kế hoạch công tác"],
    ["RETURN", "Bận việc gia đình"],
  ]) {
    const res = await page.request.post(
      `/api/bookings/${code}/journeys/${direction}/refund-requests`,
      { headers, data: { reason } },
    );
    expect(res.ok()).toBe(true);
  }
  await loginStaff(page);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Tra cứu đặt chỗ.",
  );
  const nav = page.getByRole("navigation", { name: "Nhân viên" });

  // S-03: lọc chờ duyệt và nguồn khách, cũ nhất trước; mọi dòng đúng bộ lọc.
  await nav.getByRole("link", { name: "Yêu cầu hoàn" }).click();
  const form = page.locator('form[action="/staff/refunds"]');
  await field(form, "Trạng thái").selectOption("PENDING");
  await field(form, "Nguồn").selectOption("CUSTOMER");
  await form.getByRole("button", { name: "Lọc", exact: true }).click();
  await expect(page).toHaveURL("/staff/refunds?status=PENDING&source=CUSTOMER");
  const queue = page
    .getByRole("table", { name: "Hàng đợi yêu cầu hoàn" })
    .getByRole("row");
  await expect(queue.nth(1)).toBeVisible();
  for (const row of (await queue.all()).slice(1)) {
    await expect(row).toContainText("Chờ duyệt");
    await expect(row).toContainText("Khách yêu cầu");
  }

  // Từ S-02 mở yêu cầu của chiều về.
  await page.goto(`/staff/bookings/${code}`);
  const requests = page
    .getByRole("region", { name: "Yêu cầu hoàn" })
    .getByRole("listitem");
  await requests
    .filter({ hasText: "Chiều về" })
    .getByRole("link", { name: "Xử lý yêu cầu" })
    .click();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    /^Yêu cầu hoàn #\d+\.$/,
  );

  // Dữ liệu cũ (Staff khác vừa xử lý): câu 409 của backend, rồi trang tải lại.
  await page.route(
    "**/api/staff/refund-requests/*/approve",
    (route) =>
      route.fulfill({
        status: 409,
        json: {
          code: "INVALID_STATE",
          detail: "Chỉ duyệt được yêu cầu đang chờ duyệt",
        },
      }),
    { times: 1 },
  );
  const reloaded = page.waitForRequest(
    (r) =>
      r.method() === "GET" &&
      /\/api\/staff\/refund-requests\/\d+$/.test(r.url()),
  );
  await page.getByRole("button", { name: "Duyệt", exact: true }).click();
  await expect(page.getByRole("alert").first()).toContainText(
    "Chỉ duyệt được yêu cầu đang chờ duyệt",
  );
  await reloaded;

  // Từ chối: lý do bắt buộc (BR-63), ghi người xử lý (FR-71).
  const reject = page.getByRole("button", { name: "Từ chối", exact: true });
  await reject.click();
  await expect(page.getByText("Hãy nhập lý do từ chối.")).toBeVisible();
  await field(page, "Lý do từ chối").fill("Không đủ điều kiện hoàn");
  await reject.click();
  await expect(page.getByRole("status")).toContainText(
    "Đã từ chối yêu cầu. Khách sẽ nhận email kèm lý do.",
  );
  const state = page.getByRole("region", { name: "Trạng thái" });
  await expect(state).toContainText("Bị từ chối");
  await expect(page.getByRole("region", { name: "Xử lý" })).toContainText(
    "Người xử lý: staff@demo.local",
  );
  await expect(page.getByRole("region", { name: "Thao tác" })).toHaveCount(0);

  // Chiều đi: duyệt (BR-62). Lượt thu là giả nên VNPay không hoàn được: APPROVED kèm lỗi (BR-64).
  await page.getByRole("link", { name: `Đặt chỗ ${code}` }).click();
  await expect(page).toHaveURL(`/staff/bookings/${code}`);
  await requests
    .filter({ hasText: "Chiều đi" })
    .getByRole("link", { name: "Xử lý yêu cầu" })
    .click();
  const amounts = page.getByRole("region", { name: "Số tiền hoàn" });
  await expect(amounts).toContainText("1.730.000 đ");
  await expect(amounts).toContainText("-500.000 đ");
  await expect(amounts.getByRole("definition").last()).toHaveText(
    "1.230.000 đ",
  );
  await page.getByRole("button", { name: "Duyệt", exact: true }).click();
  await expect(page.getByRole("alert").first()).toContainText(
    "Hoàn tiền qua VNPay chưa xong",
    { timeout: 60_000 },
  );
  await expect(state).toContainText("Đã duyệt, đang hoàn tiền");
  await expect(page.getByRole("region", { name: "Xử lý" })).toContainText(
    "Lỗi gần nhất:",
  );
  await expect(page.getByRole("button", { name: "Thử lại" })).toBeVisible();
  const attempts = page
    .getByRole("region", { name: "Các lượt hoàn" })
    .getByRole("listitem");
  await expect(attempts.first()).toContainText("VNPay, thất bại, 1.230.000 đ");

  // Hoàn thủ công: ghi chú bắt buộc; bấm đúp chỉ gửi một lần (BR-64).
  let manual = 0;
  page.on("request", (r) => {
    if (r.method() === "POST" && r.url().endsWith("/mark-manual")) manual++;
  });
  const settle = page.getByRole("button", { name: "Xác nhận hoàn thủ công" });
  await settle.click();
  await expect(page.getByText("Hãy nhập ghi chú hoàn thủ công.")).toBeVisible();
  await field(page, "Ghi chú hoàn thủ công").fill("Đã chuyển khoản ngày 20/10");
  await settle.dblclick();
  await expect(state).toContainText("Đã hoàn tiền");
  expect(manual).toBe(1);
  await expect(amounts).toContainText("Đã hoàn 1.230.000 đ");
  await expect(attempts.last()).toContainText(
    "Thủ công, thành công, 1.230.000 đ",
  );
  await expect(page.getByRole("region", { name: "Thao tác" })).toHaveCount(0);
});

