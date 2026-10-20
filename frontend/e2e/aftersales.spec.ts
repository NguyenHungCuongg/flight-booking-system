import { createHmac } from "node:crypto";
import { expect, test, type Locator, type Page } from "@playwright/test";
import type { Schemas } from "../src/lib/api";

// E2E hậu mãi C-10, C-11 và thao tác hậu mãi trên C-09. Trình duyệt ở múi giờ New York (TDD §7).
test.use({ timezoneId: "America/New_York" });

/** Ngày theo giờ Việt Nam, n ngày sau hôm nay (yyyy-mm-dd). */
const day = (n: number) =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh" }).format(
    Date.now() + n * 86_400_000,
  );

const field = (scope: Page | Locator, label: string) =>
  scope.getByLabel(label, { exact: true });

/**
 * Return URL VNPay báo thanh toán thành công cho lượt thu trong paymentUrl, ký như Vnpay.hash của backend: tham số
 * vnp_* khác rỗng, sắp theo tên, key=value nối bằng &, HMAC-SHA512 bằng VNPAY_HASH_SECRET (cùng ../.env với backend).
 */
function paidReturn(paymentUrl: string) {
  if (!process.env.VNPAY_HASH_SECRET) process.loadEnvFile("../.env");
  const secret = process.env.VNPAY_HASH_SECRET;
  if (!secret)
    throw new Error("Cần VNPAY_HASH_SECRET trong .env để ký Return URL");
  const sent = new URL(paymentUrl).searchParams;
  const params = new URLSearchParams({
    vnp_Amount: sent.get("vnp_Amount")!,
    vnp_TxnRef: sent.get("vnp_TxnRef")!,
    vnp_ResponseCode: "00",
    vnp_TransactionStatus: "00",
    vnp_TransactionNo: String(Date.now() % 100_000_000),
    vnp_PayDate: "20261020120000",
    vnp_BankCode: "NCB",
  });
  params.sort();
  const hash = createHmac("sha512", secret)
    .update(params.toString())
    .digest("hex");
  return `/api/payments/vnpay/return?${params}&vnp_SecureHash=${hash}`;
}

/** Tạo một đặt chỗ đã xuất vé bằng API (dùng chung cookie với trang) rồi mở C-09. */
async function issuedBooking(
  page: Page,
  fare: string,
  legs: { from: string; to: string; date: string; flight: string }[],
) {
  const email = `e2e-${test.info().testId}-${Date.now()}@example.com`;
  await page.goto("/register");
  await field(page, "Họ và tên").fill("Phạm Thu Hà");
  await field(page, "Email").fill(email);
  await field(page, "Số điện thoại").fill("0912345678");
  await field(page, "Mật khẩu").fill("matkhau1");
  await page.getByRole("button", { name: "Tạo tài khoản" }).click();
  await expect(page).toHaveURL("/bookings");

  const csrfRes = await page.request.get("/api/auth/csrf");
  expect(csrfRes.ok()).toBe(true);
  const csrf = (await page.context().cookies()).find(
    (c) => c.name === "XSRF-TOKEN",
  )?.value;
  expect(csrf).toBeTruthy();
  const headers = { "X-XSRF-TOKEN": csrf! };

  const journeys: Schemas["JourneyRequest"][] = [];
  for (let i = 0; i < legs.length; i++) {
    const leg = legs[i];
    const searchRes = await page.request.get(
      `/api/flights/search?from=${leg.from}&to=${leg.to}&date=${leg.date}&adults=1&children=0&infants=0&cabin=ECONOMY`,
    );
    expect(searchRes.ok()).toBe(true);
    const { itineraries } = (await searchRes.json()) as Schemas["SearchResult"];
    const it = itineraries.find(
      (x) =>
        x.segments.length === 1 && x.segments[0].flightNumber === leg.flight,
    );
    expect(it).toBeTruthy();
    const opt = it!.fareOptions.find((f) => f.name === fare);
    expect(opt).toBeTruthy();
    journeys.push({
      direction: i === 0 ? "OUTBOUND" : "RETURN",
      flightIds: [it!.segments[0].flightId],
      fareFamilyId: opt!.fareFamilyId,
    });
  }

  const createRes = await page.request.post("/api/bookings", {
    headers,
    data: {
      journeys,
      passengers: [
        {
          type: "ADULT",
          lastName: "PHAM",
          firstName: "THU HA",
          gender: "FEMALE",
          dateOfBirth: "1992-03-04",
          nationality: null,
          passportNumber: null,
          passportExpiry: null,
        },
      ],
      baggage: [],
      contact: { name: "Phạm Thu Hà", email, phone: "0912345678" },
      voucherCode: "",
    },
  });
  expect(createRes.ok()).toBe(true);
  const { code } = (await createRes.json()) as Schemas["BookingCreatedResponse"];

  const payRes = await page.request.post(`/api/bookings/${code}/payments`, {
    headers,
  });
  expect(payRes.ok()).toBe(true);
  const { paymentUrl } = (await payRes.json()) as Schemas["PaymentUrlResponse"];

  await page.goto(paidReturn(paymentUrl));
  await expect(page).toHaveURL(`/bookings/${code}?payment=success`);
  return { code, email };
}

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

  // Mở lại C-10 bằng URL cũ: câu của backend, không có form (FR-50).
  await page.goto(`/bookings/${code}/refund/outbound`);
  await expect(
    page.getByText("Chiều này đang có yêu cầu hoàn hoặc đổi chuyến chưa xong"),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Gửi yêu cầu hoàn" }),
  ).toHaveCount(0);
  await page.getByRole("link", { name: `Đặt chỗ ${code}` }).click();
  await expect(page).toHaveURL(`/bookings/${code}`);
});

