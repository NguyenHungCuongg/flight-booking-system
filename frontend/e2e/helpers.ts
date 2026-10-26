import { createHmac } from "node:crypto";
import { expect, test, type Locator, type Page } from "@playwright/test";
import type { Schemas } from "../src/lib/api";

/** Ngày theo giờ Việt Nam, n ngày sau hôm nay (yyyy-mm-dd). */
export const day = (n: number) =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh" }).format(
    Date.now() + n * 86_400_000,
  );

export const field = (scope: Page | Locator, label: string) =>
  scope.getByLabel(label, { exact: true });

/** Lấy header X-XSRF-TOKEN từ cookie sau khi gọi GET /api/auth/csrf. */
export async function csrfHeaders(
  page: Page,
): Promise<{ "X-XSRF-TOKEN": string }> {
  const csrfRes = await page.request.get("/api/auth/csrf");
  expect(csrfRes.ok()).toBe(true);
  const csrf = (await page.context().cookies()).find(
    (c) => c.name === "XSRF-TOKEN",
  )?.value;
  expect(csrf).toBeTruthy();
  return { "X-XSRF-TOKEN": csrf! };
}

/**
 * Return URL VNPay báo thanh toán thành công cho lượt thu trong paymentUrl, ký như Vnpay.hash của backend: tham số
 * vnp_* khác rỗng, sắp theo tên, key=value nối bằng &, HMAC-SHA512 bằng VNPAY_HASH_SECRET (cùng ../.env với backend).
 */
export function paidReturn(paymentUrl: string) {
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

/** Đăng ký tài khoản khách mới trên C-04 (đã đăng nhập sau đó), trả email. */
export async function register(page: Page) {
  const email = `e2e-${test.info().testId}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}@example.com`;
  await page.goto("/register");
  await field(page, "Họ và tên").fill("Phạm Thu Hà");
  await field(page, "Email").fill(email);
  await field(page, "Số điện thoại").fill("0912345678");
  await field(page, "Mật khẩu").fill("matkhau1");
  await page.getByRole("button", { name: "Tạo tài khoản" }).click();
  await expect(page).toHaveURL("/bookings");
  return email;
}

/** Tạo một đặt chỗ đã xuất vé bằng API (dùng chung cookie với trang) rồi mở C-09. */
export async function issuedBooking(
  page: Page,
  fare: string,
  legs: { from: string; to: string; date: string; flight: string }[],
) {
  const email = await register(page);

  const headers = await csrfHeaders(page);

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
  const { code } =
    (await createRes.json()) as Schemas["BookingCreatedResponse"];

  const payRes = await page.request.post(`/api/bookings/${code}/payments`, {
    headers,
  });
  expect(payRes.ok()).toBe(true);
  const { paymentUrl } = (await payRes.json()) as Schemas["PaymentUrlResponse"];

  await page.goto(paidReturn(paymentUrl));
  await expect(page).toHaveURL(`/bookings/${code}?payment=success`);
  return { code, email };
}
