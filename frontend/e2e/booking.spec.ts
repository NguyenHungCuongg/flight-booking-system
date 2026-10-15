import {
  expect,
  test,
  type Locator,
  type Page,
  type Request,
} from "@playwright/test";

// C-07, C-08, C-09 trên backend thật (profile dev). Seed: ngày mai có VJ123 HAN→SGN 08:00–10:10 gói Eco 990.000 đ,
// QH201 HAN→SGN, VN651 SGN→SIN (V106); hành lý VJ 15, 20, 25, 30 kg, mức 20 kg 350.000 đ (V103); WELCOME10 giảm 10%
// (V104); customer@demo.local (V105). Trình duyệt ở múi giờ New York: giờ trên màn hình phải là giờ sân bay (TDD §7).
test.use({ timezoneId: "America/New_York" });

/** Ngày theo giờ Việt Nam, n ngày sau hôm nay (yyyy-mm-dd). */
const day = (n: number) =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh" }).format(
    Date.now() + n * 86_400_000,
  );

const search = (q: Record<string, string>) =>
  "/flights?" +
  new URLSearchParams({
    adults: "1",
    children: "0",
    infants: "0",
    cabin: "ECONOMY",
    ...q,
  });

const field = (scope: Page | Locator, label: string) =>
  scope.getByLabel(label, { exact: true });

const group = (page: Page, name: string) =>
  page.getByRole("group", { name, exact: true });

async function login(page: Page, email: string, password: string) {
  await field(page, "Email").fill(email);
  await field(page, "Mật khẩu").fill(password);
  await page.getByRole("button", { name: "Đăng nhập" }).click();
}

/** Mở chi tiết một hành trình trên C-02 rồi chọn gói. */
async function choose(page: Page, card: string, fare: string) {
  const it = page.getByRole("article", { name: card });
  await it.getByRole("button", { name: "Chọn", exact: true }).click();
  await it.getByRole("button", { name: `Chọn gói ${fare}` }).click();
}

async function fillPassenger(
  scope: Locator,
  p: { last: string; first: string; gender: "Nam" | "Nữ"; dob: string },
) {
  await field(scope, "Họ").fill(p.last);
  await field(scope, "Tên đệm và tên").fill(p.first);
  await field(scope, "Giới tính").selectOption({ label: p.gender });
  await field(scope, "Ngày sinh").fill(p.dob);
}

const isBagQuote = (r: Request) =>
  r.method() === "POST" &&
  r.url().endsWith("/api/bookings/quote") &&
  r.postDataJSON().baggage.length > 0;

test("chọn vé, đăng nhập, khai hành khách, hành lý, voucher rồi giữ chỗ", async ({
  page,
}) => {
  test.slow();
  // Tài khoản mới mỗi lần chạy: WELCOME10 chỉ dùng được một lần mỗi tài khoản (BR-30 điều kiện 5).
  const email = `e2e-${test.info().testId}-${Date.now()}@example.com`;
  await page.goto("/register");
  await field(page, "Họ và tên").fill("Trần Minh Khoa");
  await field(page, "Email").fill(email);
  await field(page, "Số điện thoại").fill("0912345678");
  await field(page, "Mật khẩu").fill("matkhau1");
  await page.getByRole("button", { name: "Tạo tài khoản" }).click();
  await expect(page).toHaveURL("/bookings");

  // UF-01: chọn vé khi chưa đăng nhập, đăng nhập xong quay lại C-07 với lựa chọn còn nguyên.
  await page.context().clearCookies();
  await page.goto(search({ from: "HAN", to: "SGN", date: day(1) }));
  await choose(page, "Vietjet Air VJ123", "Eco");
  await expect(page).toHaveURL("/login?next=%2Fcheckout");
  await login(page, email, "matkhau1");
  await expect(page).toHaveURL("/checkout");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Hoàn tất đặt vé.",
  );
  await expect(page.getByRole("region", { name: "Hành trình" })).toContainText(
    "VJ123: 08:00 HAN → 10:10 SGN",
  );

  // Liên hệ điền sẵn từ tài khoản (FR-21); chuyến nội địa không hỏi hộ chiếu (BR-13).
  await expect(field(page, "Họ tên người liên hệ")).toHaveValue(
    "Trần Minh Khoa",
  );
  await expect(page.getByLabel("Số hộ chiếu")).toHaveCount(0);

  // Họ tên có dấu được bỏ dấu và viết hoa khi rời ô (BR-12, TDD §7).
  const adult = group(page, "Người lớn 1");
  await fillPassenger(adult, {
    last: "nguyễn",
    first: "văn  đức",
    gender: "Nam",
    dob: "1990-05-20",
  });
  await expect(field(adult, "Họ")).toHaveValue("NGUYEN");
  await expect(field(adult, "Tên đệm và tên")).toHaveValue("VAN DUC");

  // Đủ thông tin thì tự báo giá (FR-20).
  const prices = page.getByRole("region", { name: "Bảng giá" });
  const total = prices.getByRole("definition").last();
  await expect(prices).toContainText("Chiều đi, người lớn × 1");
  await expect(total).toHaveText("990.000 đ");

  // Báo giá về sai thứ tự: phản hồi về trễ của lần sửa trước không được đè lần sửa sau.
  const bag = group(page, "Hành lý chiều đi").getByLabel("Người lớn 1", {
    exact: true,
  });
  await page.route("**/api/bookings/quote", async (route) => {
    if (isBagQuote(route.request())) {
      await new Promise((r) => setTimeout(r, 1500));
    }
    await route.continue();
  });
  const slowSent = page.waitForRequest(isBagQuote);
  const slowDone = page.waitForResponse((r) => isBagQuote(r.request()));
  await bag.selectOption({ index: 2 }); // 20 kg
  await slowSent;
  await bag.selectOption({ index: 0 }); // Không mua thêm
  await slowDone;
  await page.waitForTimeout(500); // để trang xử lý xong phản hồi về trễ
  await expect(total).toHaveText("990.000 đ");
  await page.unroute("**/api/bookings/quote");

  await bag.selectOption({ index: 2 });
  await expect(prices).toContainText("Chiều đi, hành lý");
  await expect(total).toHaveText("1.340.000 đ");

  // Voucher sai báo ngay dưới ô; Enter trong ô voucher không giữ chỗ (FR-23).
  const voucher = field(page, "Mã giảm giá");
  await voucher.fill("KHONGCO");
  await voucher.press("Enter");
  await expect(page.getByText("Mã giảm giá không tồn tại")).toBeVisible();
  await expect(page).toHaveURL("/checkout");
  await voucher.fill("welcome10");
  await page.getByRole("button", { name: "Áp dụng" }).click();
  await expect(prices).toContainText("Giảm giá WELCOME10");
  await expect(total).toHaveText("1.206.000 đ");

  // Bấm đúp "Giữ chỗ" chỉ tạo một booking (FR-24).
  await page.getByRole("button", { name: "Giữ chỗ", exact: true }).dblclick();
  await expect(page).toHaveURL(/\/bookings\/[A-Z0-9]{6}$/);
  const code = page.url().slice(-6);
  const mine = await (await page.request.get("/api/bookings")).json();
  expect(mine.content.map((b: { code: string }) => b.code)).toEqual([code]);

  // Lựa chọn đã dùng thì xoá: mở lại C-07 không giữ chỗ lần nữa được.
  await page.goto("/checkout");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Chưa có chuyến bay nào được chọn.",
  );
});

test("hành trình quốc tế hỏi hộ chiếu, báo giá đánh dấu đúng ô sai", async ({
  page,
}) => {
  await page.goto(
    search({ from: "SGN", to: "SIN", date: day(1), children: "1" }),
  );
  await choose(page, "Vietnam Airlines VN651", "Economy Lite");
  await expect(page).toHaveURL("/login?next=%2Fcheckout");
  await login(page, "customer@demo.local", "Demo@1234");
  await expect(page).toHaveURL("/checkout");

  const adult = group(page, "Người lớn 1");
  const child = group(page, "Trẻ em 1");
  await fillPassenger(adult, {
    last: "tran",
    first: "thi mai",
    gender: "Nữ",
    dob: "1985-02-14",
  });
  await field(adult, "Quốc tịch").fill("vn");
  await field(adult, "Số hộ chiếu").fill("c1234567");
  await field(adult, "Ngày hết hạn hộ chiếu").fill(day(800));
  await expect(field(adult, "Quốc tịch")).toHaveValue("VN");
  await expect(field(adult, "Số hộ chiếu")).toHaveValue("C1234567");

  // Trẻ em khai ngày sinh của người lớn (BR-10), hộ chiếu chỉ còn 30 ngày (BR-13).
  await fillPassenger(child, {
    last: "tran",
    first: "minh an",
    gender: "Nam",
    dob: "1990-01-01",
  });
  await field(child, "Quốc tịch").fill("VN");
  await field(child, "Số hộ chiếu").fill("C7654321");
  await field(child, "Ngày hết hạn hộ chiếu").fill(day(30));

  await expect(
    child.getByText("Theo ngày sinh, hành khách là người lớn tại ngày bay"),
  ).toBeVisible();
  await expect(
    child.getByText(
      "Hộ chiếu phải còn hạn ít nhất 6 tháng tính từ ngày bay của chặng cuối",
    ),
  ).toBeVisible();
  await expect(field(child, "Ngày sinh")).toHaveAttribute(
    "aria-invalid",
    "true",
  );

  // Bấm "Giữ chỗ" vẫn ở lại C-07, giữ nguyên lỗi theo ô (APP_FLOW C-07).
  await page.getByRole("button", { name: "Giữ chỗ", exact: true }).click();
  await expect(
    page.getByText("Thông tin hành khách chưa hợp lệ"),
  ).toBeVisible();
  await expect(page).toHaveURL("/checkout");
});

test("chuyến không còn bán thì quay lại C-02 kèm thông báo", async ({
  page,
}) => {
  const d = day(1);
  await page.goto(search({ from: "HAN", to: "SGN", date: d }));
  await choose(page, "Bamboo Airways QH201", "Economy Saver");
  await expect(page).toHaveURL("/login?next=%2Fcheckout");
  await login(page, "customer@demo.local", "Demo@1234");
  await expect(page).toHaveURL("/checkout");

  // Giả lập chuyến bị gỡ sau khi khách chọn: id chuyến không tồn tại.
  await page.evaluate(() => {
    const s = JSON.parse(sessionStorage.getItem("skyline.selection") ?? "{}");
    s.journeys[0].flightIds = [2147483647];
    sessionStorage.setItem("skyline.selection", JSON.stringify(s));
  });
  await page.reload();
  await fillPassenger(group(page, "Người lớn 1"), {
    last: "le",
    first: "van binh",
    gender: "Nam",
    dob: "1988-08-08",
  });
  // Báo giá đã báo lỗi ngay khi đủ thông tin.
  await expect(
    page.getByText("Chuyến bay đã chọn không còn được bán"),
  ).toBeVisible();

  await page.getByRole("button", { name: "Giữ chỗ", exact: true }).click();
  await expect(page).toHaveURL(
    `/flights?from=HAN&to=SGN&date=${d}&adults=1&children=0&infants=0&cabin=ECONOMY&unavailable=1`,
  );
  await expect(
    page.getByText("Chuyến bay bạn chọn không còn đủ chỗ hoặc đã ngừng bán."),
  ).toBeVisible();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Chọn chuyến bay.",
  );
});
