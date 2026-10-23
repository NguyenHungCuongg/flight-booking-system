import { expect, test, type Locator, type Page } from "@playwright/test";
import type { Schemas } from "../src/lib/api";
import { csrfHeaders, day, field } from "./helpers";

// E2E màn Admin A-01–A-10. Trình duyệt ở múi giờ New York (TDD §7).
test.use({ timezoneId: "America/New_York" });

/** Bỏ phiên hiện tại rồi đăng nhập Admin của seed V105: trang mặc định là A-04 (APP_FLOW §1). */
export async function loginAdmin(page: Page) {
  await page.context().clearCookies();
  await page.goto("/login");
  await field(page, "Email").fill("admin@demo.local");
  await field(page, "Mật khẩu").fill("Demo@1234");
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page).toHaveURL("/admin/flights");
}

/** Dòng bảng có một ô đúng bằng `text` (mã sân bay, hãng...). */
const rowOf = (table: Locator, text: string) =>
  table.getByRole("row").filter({ has: table.page().getByRole("cell", { name: text, exact: true }) });

/** Chuỗi n chữ in hoa khác nhau giữa các lần chạy, cho mã sân bay/hãng tạo mới. */
const letters = (n: number) =>
  Array.from({ length: n }, (_, i) =>
    String.fromCharCode(65 + (Math.floor(Date.now() / 26 ** i) % 26)),
  ).join("");

test("A-01: Admin thêm, sửa, xoá sân bay; sân bay đã có chuyến thì không xoá được", async ({
  page,
}) => {
  await loginAdmin(page);
  await page.goto("/admin/airports");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Sân bay.");
  const table = page.getByRole("table", { name: "Bảng sân bay" });
  await expect(table.getByRole("cell", { name: "HAN", exact: true })).toBeVisible();

  // HAN đã có chuyến bay (seed V106): backend trả 409 RESOURCE_IN_USE.
  await rowOf(table, "HAN").getByRole("button", { name: "Xoá" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Xoá sân bay" }).click();
  await expect(page.getByRole("alert").first()).toContainText("không thể xoá");

  // Mã trùng: lỗi hiện ngay trong hộp thoại đang mở, không bị lớp phủ che.
  await page.getByRole("button", { name: "+ Thêm sân bay" }).click();
  await field(page.getByRole("dialog"), "Mã IATA (3 chữ cái)").fill("HAN");
  await field(page.getByRole("dialog"), "Tên sân bay").fill("Trùng mã");
  await field(page.getByRole("dialog"), "Thành phố").fill("Hà Nội");
  await page.getByRole("dialog").getByRole("button", { name: "Lưu sân bay" }).click();
  await expect(page.getByRole("dialog").getByRole("alert")).toBeVisible();
  await page.getByRole("dialog").getByRole("button", { name: "Huỷ" }).click();

  const code = letters(3);
  await page.getByRole("button", { name: "+ Thêm sân bay" }).click();
  const dialog = page.getByRole("dialog");
  await field(dialog, "Mã IATA (3 chữ cái)").fill(code);
  await field(dialog, "Tên sân bay").fill("Sân bay Thử nghiệm");
  await field(dialog, "Thành phố").fill("Đà Lạt");
  await dialog.getByRole("button", { name: "Lưu sân bay" }).click();
  const row = rowOf(table, code);
  await expect(row).toContainText("Hoạt động");

  await row.getByRole("button", { name: "Sửa" }).click();
  await field(dialog, "Thành phố").fill("Lâm Đồng");
  await dialog.getByText("Đang hoạt động").click();
  await dialog.getByRole("button", { name: "Lưu sân bay" }).click();
  await expect(row).toContainText("Lâm Đồng");
  await expect(row).toContainText("Ngừng kích hoạt");

  await row.getByRole("button", { name: "Xoá" }).click();
  await dialog.getByRole("button", { name: "Xoá sân bay" }).click();
  await expect(row).toHaveCount(0);
});

test("A-02: Admin thêm, sửa, xoá hãng bay; hãng đã có chuyến thì không xoá được", async ({
  page,
}) => {
  await loginAdmin(page);
  await page.goto("/admin/airlines");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Hãng bay.");
  const table = page.getByRole("table", { name: "Bảng hãng bay" });
  await expect(table.getByText("Bamboo Airways")).toBeVisible();

  await rowOf(table, "QH").getByRole("button", { name: "Xoá" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Xoá hãng" }).click();
  await expect(page.getByRole("alert").first()).toContainText("không thể xoá");

  const code = letters(2);
  const prefix = String(Date.now() % 900 + 100);
  await page.getByRole("button", { name: "+ Thêm hãng bay" }).click();
  const dialog = page.getByRole("dialog");
  await field(dialog, "Mã IATA (2 ký tự)").fill(code);
  await field(dialog, "Mã số vé (3 chữ số duy nhất)").fill(prefix);
  await field(dialog, "Tên hãng hàng không").fill("Hãng Thử nghiệm");
  await dialog.getByRole("button", { name: "Lưu hãng bay" }).click();
  const row = rowOf(table, code);
  await expect(row).toContainText(prefix);

  await row.getByRole("button", { name: "Sửa" }).click();
  await field(dialog, "Tên hãng hàng không").fill("Hãng Thử nghiệm Mới");
  await dialog.getByRole("button", { name: "Lưu hãng bay" }).click();
  await expect(row).toContainText("Hãng Thử nghiệm Mới");

  await row.getByRole("button", { name: "Xoá" }).click();
  await dialog.getByRole("button", { name: "Xoá hãng" }).click();
  await expect(row).toHaveCount(0);
});

test("A-03: Admin quản lý gói giá và bảng giá hành lý của hãng", async ({ page }) => {
  await loginAdmin(page);
  await page.goto("/admin/airlines");
  await rowOf(page.getByRole("table", { name: "Bảng hãng bay" }), "QH")
    .getByRole("link", { name: "Gói giá & Bảng giá hành lý →" })
    .click();
  await expect(page).toHaveURL("/admin/airlines/QH");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Hãng QH.");
  await expect(page.getByRole("tab", { name: "Gói giá (Fare Families)" })).toHaveAttribute(
    "aria-selected",
    "true",
  );
  const dialog = page.getByRole("dialog");

  // Gói giá: tạo, sửa (hạng ghế khoá), xoá.
  const fare = `E2E ${Date.now()}`;
  await page.getByRole("button", { name: "+ Thêm gói giá" }).click();
  await field(dialog, "Tên gói giá").fill(fare);
  await field(dialog, "Hạng ghế").selectOption("BUSINESS");
  await dialog.getByRole("button", { name: "Lưu gói giá" }).click();
  const fares = page.getByRole("table", { name: "Bảng gói giá" });
  const fareRow = rowOf(fares, fare);
  await expect(fareRow).toContainText("Thương gia");

  await fareRow.getByRole("button", { name: "Sửa" }).click();
  await expect(field(dialog, "Hạng ghế")).toHaveCount(0);
  await field(dialog, "Hành lý ký gửi (kg)").fill("30");
  await dialog.getByRole("button", { name: "Lưu gói giá" }).click();
  await expect(fareRow).toContainText("30 kg");

  await fareRow.getByRole("button", { name: "Xoá" }).click();
  await dialog.getByRole("button", { name: "Xoá mục này" }).click();
  await expect(fareRow).toHaveCount(0);

  // Hành lý: tạo rồi xoá một mức cân lạ.
  await page.getByRole("tab", { name: "Bảng giá hành lý (Baggage Options)" }).click();
  await page.getByRole("button", { name: "+ Thêm mức hành lý" }).click();
  await field(dialog, "Trọng lượng mua thêm (kg)").fill("97");
  await field(dialog, "Giá bán (₫)").fill("990000");
  await dialog.getByRole("button", { name: "Lưu mức hành lý" }).click();
  const bagRow = rowOf(page.getByRole("table", { name: "Bảng giá hành lý" }), "97 kg");
  await expect(bagRow).toContainText("990.000");
  await bagRow.getByRole("button", { name: "Xoá" }).click();
  await dialog.getByRole("button", { name: "Xoá mục này" }).click();
  await expect(bagRow).toHaveCount(0);
});

test("A-04 & A-06: Admin lọc chuyến, sửa, huỷ hai bước rồi xoá chuyến chưa có booking", async ({
  page,
}) => {
  await loginAdmin(page);
  const headers = await csrfHeaders(page);
  const faresRes = await page.request.get("/api/admin/airlines/QH/fare-families");
  const fares = (await faresRes.json()) as Schemas["FareFamilyResponse"][];
  const economy = fares.filter((f) => f.cabinClass === "ECONOMY" && f.active);
  const date = day(20);
  const flightNumber = `QH${String(Date.now() % 9000 + 1000)}`;
  const createRes = await page.request.post("/api/admin/flights", {
    headers,
    data: {
      airlineCode: "QH",
      flightNumber,
      departureAirport: "HAN",
      arrivalAirport: "SGN",
      departureTime: "05:15",
      durationMinutes: 130,
      fromDate: date,
      toDate: date,
      daysOfWeek: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"],
      aircraftModel: "Airbus A320",
      cabins: [{ cabinClass: "ECONOMY", totalSeats: 120 }],
      fares: economy.map((f) => ({ fareFamilyId: f.id, price: 1_200_000 })),
    } satisfies Schemas["NewFlights"],
  });
  expect(createRes.ok()).toBe(true);
  const { flightIds } = (await createRes.json()) as Schemas["CreatedFlights"];

  await page.goto(`/admin/flights?airline=QH&date=${date}`);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Chuyến bay.");
  const list = page.getByRole("table", { name: "Danh sách chuyến bay" });
  await rowOf(list, flightNumber).getByRole("link", { name: "Chi tiết" }).click();
  await expect(page).toHaveURL(`/admin/flights/${flightIds[0]}`);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(`Chuyến bay ${flightNumber}.`);
  await expect(page.getByText("05:15").first()).toBeVisible();

  const dialog = page.getByRole("dialog");
  await page.getByRole("button", { name: "Sửa chuyến bay" }).click();
  await field(dialog, "Loại máy bay").fill("Boeing 787");
  await field(dialog, "Giờ cất cánh (giờ địa phương)").fill(`${date}T06:40`);
  await field(dialog, "Giờ hạ cánh (giờ địa phương)").fill(`${date}T06:00`);
  await dialog.getByRole("button", { name: "Lưu thay đổi" }).click();
  // Hạ cánh trước cất cánh: câu báo theo trường của backend, không phải "Dữ liệu không hợp lệ".
  await expect(page.getByRole("alert").first()).toContainText("Giờ hạ cánh phải sau giờ cất cánh");
  await field(dialog, "Giờ hạ cánh (giờ địa phương)").fill(`${date}T08:50`);
  await dialog.getByRole("button", { name: "Lưu thay đổi" }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByText("Loại máy bay: Boeing 787")).toBeVisible();
  await expect(page.getByText("06:40").first()).toBeVisible();

  await page.getByRole("button", { name: "Huỷ chuyến" }).click();
  await expect(dialog).toContainText("FLIGHT_CANCELLED");
  await dialog.getByRole("button", { name: "Xác nhận huỷ chuyến" }).click();
  await expect(page.getByText("Đã huỷ", { exact: true }).first()).toBeVisible();
  await expect(page.getByRole("button", { name: "Sửa chuyến bay" })).toHaveCount(0);

  await page.getByRole("button", { name: "Xoá chuyến" }).click();
  await dialog.getByRole("button", { name: "Xoá vĩnh viễn" }).click();
  await expect(page).toHaveURL("/admin/flights");
});

test("A-05: Admin tạo chuyến lẻ rồi khách thấy chuyến đó trong kết quả tìm kiếm; tạo lại thì bị bỏ qua", async ({
  page,
}) => {
  test.slow();
  await loginAdmin(page);
  await page.getByRole("link", { name: "+ Tạo chuyến bay" }).click();
  await expect(page).toHaveURL("/admin/flights/new");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Tạo chuyến bay mới.");

  const flightNumber = `VN${String(Date.now() % 9000 + 1000)}`;
  const date = day(10);
  // Trang A-04 vừa rời vẫn nằm ẩn trong DOM (cũng có ô "Ngày bay"), nên chỉ tìm trong form tạo chuyến.
  const form = page.locator("form", { has: page.getByRole("button", { name: "Phát hành chuyến bay" }) });
  const basics = async () => {
    await field(form, "Hãng hàng không").selectOption("VN");
    await field(form, "Số hiệu chuyến bay").fill(flightNumber);
    await field(form, "Sân bay đi").selectOption("HAN");
    await field(form, "Sân bay đến").selectOption("PQC");
    await field(form, "Giờ cất cánh (giờ địa phương)").fill("04:35");
    await field(form, "Thời lượng bay (phút)").fill("120");
    await field(form, "Loại máy bay").fill("Airbus A321");
  };
  const fill = async () => {
    await basics();
    await field(form, "Ngày bay").fill(date);
    await form.getByRole("button", { name: "Phát hành chuyến bay" }).click();
  };
  await fill();
  await expect(page.getByRole("heading", { level: 2 })).toHaveText("Kết quả tạo chuyến bay:");
  await expect(page.getByText("Đã tạo thành công: 1 chuyến bay.")).toBeVisible();

  // Định kỳ day(10)..day(16) trừ Chủ nhật: ngày day(10) đã có chuyến nên bị bỏ qua (BR-80).
  await page.getByRole("button", { name: "Tiếp tục tạo chuyến khác" }).click();
  await basics();
  await form.getByText("Tạo lịch định kỳ hàng loạt").click();
  await form.getByRole("button", { name: "Chủ nhật" }).click();
  await field(form, "Từ ngày").fill(date);
  await field(form, "Đến ngày (tối đa 180 ngày)").fill(day(16));
  await form.getByRole("button", { name: "Phát hành chuyến bay" }).click();
  const dates = Array.from({ length: 7 }, (_, i) => day(10 + i));
  const flying = dates.filter((d) => new Date(`${d}T00:00Z`).getUTCDay() !== 0);
  const firstFlies = flying.includes(date);
  await expect(
    page.getByText(`Đã tạo thành công: ${flying.length - (firstFlies ? 1 : 0)} chuyến bay.`),
  ).toBeVisible();
  if (firstFlies) await expect(page.getByText("Đã bỏ qua: 1 ngày")).toBeVisible();

  await page.goto(
    `/flights?from=HAN&to=PQC&date=${date}&adults=1&children=0&infants=0&cabin=ECONOMY`,
  );
  await expect(page.getByText(flightNumber).first()).toBeVisible();

  // Dọn các chuyến vừa tạo (chưa có booking nên xoá được).
  const headers = await csrfHeaders(page);
  for (const d of dates) {
    const res = await page.request.get(`/api/staff/flights?from=HAN&to=PQC&date=${d}&airline=VN&size=100`);
    const { content } = (await res.json()) as Schemas["PagedModelFlightView"];
    for (const f of content.filter((x) => x.flightNumber === flightNumber)) {
      expect((await page.request.delete(`/api/admin/flights/${f.id}`, { headers })).ok()).toBe(true);
    }
  }
});

test("A-07: Admin tạo và sửa voucher; mã trùng bị từ chối", async ({ page }) => {
  await loginAdmin(page);
  await page.goto("/admin/vouchers");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Voucher khuyến mãi.");
  const table = page.getByRole("table", { name: "Bảng voucher" });
  await expect(table.getByRole("cell", { name: "WELCOME10", exact: true })).toBeVisible();

  const dialog = page.getByRole("dialog");
  const fill = async (code: string) => {
    await page.getByRole("button", { name: "+ Thêm voucher" }).click();
    await field(dialog, "Mã voucher (in hoa, duy nhất)").fill(code);
    await field(dialog, "Mô tả").fill("Giảm 15% cho khách thử nghiệm");
    await field(dialog, "Giá trị giảm (% hoặc ₫)").fill("15");
    await field(dialog, "Mức giảm tối đa (₫)").fill("300000");
    await field(dialog, "Hiệu lực từ (giờ Việt Nam)").fill(`${day(0)}T00:00`);
    await field(dialog, "Hiệu lực đến (giờ Việt Nam)").fill(`${day(30)}T23:59`);
    await dialog.getByRole("button", { name: "Lưu voucher" }).click();
  };

  await fill("WELCOME10");
  await expect(dialog.getByRole("alert")).toContainText("Mã voucher đã tồn tại");
  await dialog.getByRole("button", { name: "Huỷ" }).click();

  const code = `E2E${letters(6)}`;
  await fill(code);
  const row = rowOf(table, code);
  await expect(row).toContainText("15%");
  await expect(row).toContainText("0 / ∞");

  await row.getByRole("button", { name: "Sửa" }).click();
  await expect(field(dialog, "Hiệu lực đến (giờ Việt Nam)")).toHaveValue(`${day(30)}T23:59`);
  await dialog.getByText("Đang kích hoạt").click();
  await dialog.getByRole("button", { name: "Lưu voucher" }).click();
  await expect(row).toContainText("Đã tắt");
});

test("A-08: Admin tìm, tạo tài khoản Staff, khoá và mở khoá; không tự khoá chính mình (BR-105)", async ({
  page,
}) => {
  await loginAdmin(page);
  await page.goto("/admin/users");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Người dùng & Tài khoản.");
  const table = page.getByRole("table", { name: "Bảng người dùng" });

  await field(page, "Tìm kiếm theo Email hoặc Họ tên").fill("admin@demo.local");
  await page.getByRole("button", { name: "Tìm kiếm" }).click();
  await expect(page).toHaveURL(/q=admin%40demo.local/);
  const me = rowOf(table, "admin@demo.local");
  await expect(me).toContainText("(Chính bạn)");
  await expect(me.getByRole("button")).toHaveCount(0);

  const email = `staff-${Date.now()}@example.com`;
  await page.getByRole("button", { name: "+ Tạo tài khoản Staff / Admin" }).click();
  const dialog = page.getByRole("dialog");
  await field(dialog, "Họ và tên").fill("Lê Văn Trực");
  await field(dialog, "Email đăng nhập").fill(email);
  await field(dialog, "Số điện thoại").fill("0987654321");
  await field(dialog, "Mật khẩu khởi tạo").fill("matkhau1");
  await dialog.getByRole("button", { name: "Tạo tài khoản" }).click();
  await expect(dialog).toHaveCount(0);

  await field(page, "Tìm kiếm theo Email hoặc Họ tên").fill(email);
  await page.getByRole("button", { name: "Tìm kiếm" }).click();
  const row = rowOf(table, email);
  await expect(row).toContainText("Nhân viên");
  await row.getByRole("button", { name: "Khoá tài khoản" }).click();
  await dialog.getByRole("button", { name: "Khoá tài khoản" }).click();
  await expect(row).toContainText("Đã khoá");
  await row.getByRole("button", { name: "Mở khoá" }).click();
  await dialog.getByRole("button", { name: "Mở khoá" }).click();
  await expect(row).toContainText("Hoạt động");
});

test("A-09: Admin sửa tham số trong khoảng hợp lệ và thấy người sửa", async ({ page }) => {
  await loginAdmin(page);
  await page.goto("/admin/settings");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Tham số hệ thống.");
  const table = page.getByRole("table", { name: "Bảng tham số hệ thống" });
  await expect(table.getByText("Khoảng cách đặt vé tối thiểu")).toBeVisible();
  const row = rowOf(table, "Thời gian giữ chỗ");
  await expect(row).toContainText("5 – 60");
  const original = (await row.getByRole("cell").nth(1).innerText()).split(" ")[0];
  const changed = original === "16" ? "17" : "16";

  const dialog = page.getByRole("dialog");
  const save = async (value: string) => {
    await row.getByRole("button", { name: "Sửa" }).click();
    await field(dialog, "Giá trị mới (phút)").fill(value);
    await dialog.getByRole("button", { name: "Lưu giá trị mới" }).click();
  };
  await save("70");
  await expect(dialog.getByRole("alert")).toContainText("Giá trị phải từ 5 đến 60");
  await dialog.getByRole("button", { name: "Huỷ" }).click();

  await save(changed);
  await expect(row.getByRole("cell").nth(1)).toHaveText(`${changed} phút`);
  // updatedByName là họ tên của Admin seed V105, thời điểm sửa theo giờ Việt Nam.
  await expect(row.getByRole("cell").nth(4)).not.toHaveText("Hệ thống");
  await save(original);
  await expect(row.getByRole("cell").nth(1)).toHaveText(`${original} phút`);
});

test("A-10: Admin xem ba báo cáo dòng tiền, doanh số và tỉ lệ lấp đầy", async ({ page }) => {
  await loginAdmin(page);
  await page.goto("/admin/reports");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Báo cáo & Thống kê.");
  // Mặc định 30 ngày gần nhất theo giờ Việt Nam.
  await expect(field(page, "Từ ngày")).toHaveValue(day(-30));
  await expect(field(page, "Đến ngày")).toHaveValue(day(0));
  await expect(page.getByText("Tổng thu (Charged)")).toBeVisible();
  await expect(page.getByText("Tổng hoàn (Refunded)")).toBeVisible();
  await expect(page.getByRole("table", { name: "Báo cáo dòng tiền" })).toBeVisible();
  await field(page, "Nhóm theo").selectOption("MONTH");
  await expect(page.getByRole("table", { name: "Báo cáo dòng tiền" })).toBeVisible();

  await page.getByRole("tab", { name: "Doanh số vé (Sales)" }).click();
  await expect(page.getByText("Tổng doanh thu vé")).toBeVisible();
  await field(page, "Nhóm theo").selectOption("ROUTE");
  await expect(page.getByRole("columnheader", { name: "Tuyến bay" })).toBeVisible();

  await page.getByRole("tab", { name: "Tỉ lệ lấp đầy (Load Factor)" }).click();
  await field(page, "Lọc theo hãng bay").selectOption("QH");
  await expect(page.getByText("Tỉ lệ lấp đầy bình quân toàn bộ chuyến")).toBeVisible();
  await expect(page.getByRole("table", { name: "Báo cáo tỉ lệ lấp đầy" })).toBeVisible();
  await expect(page.getByRole("main").getByRole("alert")).toHaveCount(0);
});

test("Phân quyền: khách vãng lai và Staff không dùng được /admin/**", async ({ page }) => {
  await page.goto("/admin/flights");
  await expect(page).toHaveURL("/login?next=%2Fadmin%2Fflights");

  await field(page, "Email").fill("staff@demo.local");
  await field(page, "Mật khẩu").fill("Demo@1234");
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page).toHaveURL("/admin/flights");
  // Staff có phiên nhưng không có ROLE_ADMIN: trang vẫn có tiêu đề, dữ liệu báo 403 của backend.
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Chuyến bay.");
  await page.goto("/admin/users");
  await expect(page.getByRole("main").getByRole("alert").first()).toContainText(
    "Bạn không có quyền thực hiện thao tác này",
  );
});

test("Hộp thoại Admin: focus vào hộp thoại, Escape đóng và trả focus về nút mở", async ({ page }) => {
  await loginAdmin(page);
  await page.goto("/admin/airports");
  const open = page.getByRole("button", { name: "+ Thêm sân bay" });
  await open.click();
  const dialog = page.getByRole("dialog", { name: "Thêm sân bay mới" });
  await expect(dialog).toBeVisible();
  await expect(dialog.locator(":focus")).toHaveCount(1);
  await page.keyboard.press("Escape");
  await expect(dialog).toHaveCount(0);
  await expect(open).toBeFocused();
});

test("A-06: chuyến đã cất cánh không còn nút sửa và huỷ (BR-87)", async ({ page }) => {
  await loginAdmin(page);
  const res = await page.request.get(`/api/staff/flights?date=${day(-1)}&status=SCHEDULED&size=1`);
  const { content } = (await res.json()) as Schemas["PagedModelFlightView"];
  expect(content.length).toBe(1);
  await page.goto(`/admin/flights/${content[0].id}`);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(`Chuyến bay ${content[0].flightNumber}.`);
  await expect(page.getByRole("link", { name: "Danh sách hành khách (Manifest)" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Sửa chuyến bay" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Huỷ chuyến" })).toHaveCount(0);
});
