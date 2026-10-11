import { expect, test, type Page } from "@playwright/test";

// C-01 và C-02 trên backend thật (profile dev) với seed V106: mỗi ngày có VJ123 08:00, QH201 10:00, VN213 06:00
// và nối chuyến VN1233 + VN1826 cho HAN→SGN; chiều SGN→HAN có VN1825 + VN1232 11:00, QH202 15:00, VN220 17:00,
// VJ122 19:00. Seed sinh 60 ngày tính từ ngày chạy migration (lộ trình §8).

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

const cards = (page: Page) =>
  page
    .getByRole("article")
    .evaluateAll((els) => els.map((e) => e.getAttribute("aria-label")));

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
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Chọn chuyến bay.",
  );
  await expect(page.getByText("Không có chuyến bay phù hợp.")).toBeVisible();
});

test("khứ hồi 2 bước, lọc và sắp xếp ở client, BR-04, rồi sang đăng nhập", async ({
  page,
}) => {
  const d = day(1);
  await page.goto(search({ from: "HAN", to: "SGN", date: d, returnDate: d }));
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Chọn chiều đi.",
  );

  // Mặc định xếp theo giá rẻ nhất.
  await expect
    .poll(() => cards(page))
    .toEqual([
      "Vietjet Air VJ123",
      "Bamboo Airways QH201",
      "Vietnam Airlines VN213",
      "Vietnam Airlines VN1233 + VN1826",
    ]);
  await page.getByLabel("Sắp xếp").selectOption({ label: "Cất cánh sớm nhất" });
  expect(await cards(page)).toEqual([
    "Vietnam Airlines VN213",
    "Vietjet Air VJ123",
    "Vietnam Airlines VN1233 + VN1826",
    "Bamboo Airways QH201",
  ]);
  // Ba chuyến thẳng cùng 2 giờ 10 phút: bằng nhau thì rẻ hơn đứng trước.
  await page.getByLabel("Sắp xếp").selectOption({ label: "Bay nhanh nhất" });
  expect(await cards(page)).toEqual([
    "Vietjet Air VJ123",
    "Bamboo Airways QH201",
    "Vietnam Airlines VN213",
    "Vietnam Airlines VN1233 + VN1826",
  ]);

  await page.getByRole("checkbox", { name: "Bay thẳng" }).check();
  await page.getByRole("checkbox", { name: "Vietnam Airlines" }).check();
  expect(await cards(page)).toEqual(["Vietnam Airlines VN213"]);
  await expect(page.getByText("1 trên 4 hành trình")).toBeVisible();
  await page.getByRole("button", { name: "Xoá bộ lọc" }).click();
  await expect(page.getByRole("article")).toHaveCount(4);

  // Bước 1: QH201 hạ cánh 12:10.
  const qh = page.getByRole("article", { name: "Bamboo Airways QH201" });
  await qh.getByRole("button", { name: "Chọn", exact: true }).click();
  await qh.getByRole("button", { name: "Chọn gói Economy Saver" }).click();
  await expect(page).toHaveURL(/&out=\d+&outFare=\d+$/);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Chọn chiều về.",
  );
  await expect(
    page.getByRole("region", { name: "Chiều đi đã chọn" }),
  ).toContainText("QH201");

  // BR-04: VN1825 + VN1232 cất cánh 11:00, trước 12:10, nên bị ẩn. Bộ lọc của bước 1 không mang sang.
  await expect
    .poll(() => cards(page))
    .toEqual([
      "Vietjet Air VJ122",
      "Bamboo Airways QH202",
      "Vietnam Airlines VN220",
    ]);
  await expect(
    page.getByText(
      "Đã ẩn 1 hành trình cất cánh trước giờ hạ cánh của chiều đi.",
    ),
  ).toBeVisible();

  // Bước nằm trong URL: Back về bước 1, Forward và tải lại vẫn ở bước 2.
  await page.goBack();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Chọn chiều đi.",
  );
  await page.goForward();
  await page.reload();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Chọn chiều về.",
  );

  const vj = page.getByRole("article", { name: "Vietjet Air VJ122" });
  await vj.getByRole("button", { name: "Chọn", exact: true }).click();
  await vj.getByRole("button", { name: "Chọn gói Eco" }).click();
  // Chưa đăng nhập: /checkout chuyển về đăng nhập, lựa chọn đã nằm trong sessionStorage.
  await expect(page).toHaveURL("/login?next=%2Fcheckout");
  const selection = await page.evaluate(() =>
    JSON.parse(sessionStorage.getItem("skyline.selection") ?? "null"),
  );
  expect(selection.adults).toBe(1);
  expect(
    selection.journeys.map(
      (j: {
        direction: string;
        itinerary: { segments: { flightNumber: string }[] };
      }) =>
        `${j.direction} ${j.itinerary.segments.map((s) => s.flightNumber).join("+")}`,
    ),
  ).toEqual(["OUTBOUND QH201", "RETURN VJ122"]);
});

test.describe("trình duyệt ở múi giờ khác", () => {
  test.use({ timezoneId: "America/New_York" });

  test("giờ hiện theo giờ địa phương của sân bay", async ({ page }) => {
    await page.goto(search({ from: "SGN", to: "SIN", date: day(1) }));
    // VN651 cất cánh 09:30 giờ Việt Nam, bay 120 phút, hạ cánh 12:30 giờ Singapore.
    const vn = page.getByRole("article", { name: "Vietnam Airlines VN651" });
    await expect(vn).toContainText("09:30");
    await expect(vn).toContainText("12:30");
  });
});

test("URL sai hoặc chiều đi không còn thì báo rõ, không vỡ trang", async ({
  page,
}) => {
  const d = day(1);
  await page.goto(
    search({
      from: "HAN",
      to: "SGN",
      date: d,
      returnDate: d,
      out: "1",
      outFare: "1",
    }),
  );
  await expect(page.getByText("Chiều đi đã chọn không còn bán")).toBeVisible();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(
    "Chọn chiều đi.",
  );

  await page.goto(search({ from: "HAN", to: "SGN", date: d, infants: "2" }));
  await expect(
    page.getByText("Số em bé không được nhiều hơn số người lớn"),
  ).toBeVisible();

  await page.goto(
    search({ from: "HAN", to: "SGN", date: day(3), returnDate: d }),
  );
  await expect(
    page.getByText("Ngày về không được trước ngày đi."),
  ).toBeVisible();
});
