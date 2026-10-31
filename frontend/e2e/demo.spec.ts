import { expect, test, type Browser, type Page } from "@playwright/test";
import type { Schemas } from "../src/lib/api";
import { csrfHeaders, day, field, paidReturn, register } from "./helpers";

// Kịch bản demo D1, D2, D4, D8–D11 của PRD §7 (D3, D5, D6, D7 nằm ở booking, flight-search, aftersales, staff).
// Chạy trong project "demo" sau mọi spec khác và tuần tự (playwright.config.ts): D9 đổi tham số dùng chung, D10 so
// số liệu báo cáo trước và sau, nên không được có booking khác chen vào. Seed V103–V106 như booking.spec.ts.
test.use({ timezoneId: "America/New_York" });

type Booking = Schemas["BookingDetail"];
type Passenger = Schemas["PassengerRequest"];

const MAILPIT = process.env.MAILPIT_URL ?? "http://localhost:8025";

const adult: Passenger = {
  type: "ADULT",
  lastName: "PHAM",
  firstName: "THU HA",
  gender: "FEMALE",
  dateOfBirth: "1992-03-04",
  nationality: null,
  passportNumber: null,
  passportExpiry: null,
} as unknown as Passenger;

async function login(page: Page, email: string, home: string | RegExp) {
  await page.context().clearCookies();
  await page.goto("/login");
  await field(page, "Email").fill(email);
  await field(page, "Mật khẩu").fill("Demo@1234");
  await page.getByRole("button", { name: "Đăng nhập" }).click();
  await expect(page).toHaveURL(home);
}

/** Trang mới với phiên riêng (một khách khác), cùng baseURL với cấu hình. */
async function otherPage(browser: Browser) {
  const ctx = await browser.newContext({
    baseURL: test.info().project.use.baseURL,
    timezoneId: "America/New_York",
  });
  return ctx.newPage();
}

/** Chuyến bay thẳng đầu tiên có số hiệu `flight` và gói `fare`. */
async function journey(
  page: Page,
  q: { from: string; to: string; date: string; flight: string; fare: string },
  direction: "OUTBOUND" | "RETURN" = "OUTBOUND",
): Promise<Schemas["JourneyRequest"]> {
  const res = await page.request.get(
    `/api/flights/search?from=${q.from}&to=${q.to}&date=${q.date}&adults=1&children=0&infants=0&cabin=ECONOMY`,
  );
  expect(res.ok()).toBe(true);
  const { itineraries } = (await res.json()) as Schemas["SearchResult"];
  const it = itineraries.find(
    (x) => x.segments.length === 1 && x.segments[0].flightNumber === q.flight,
  );
  expect(it, `${q.flight} ngày ${q.date}`).toBeTruthy();
  const opt = it!.fareOptions.find((f) => f.name === q.fare);
  expect(opt).toBeTruthy();
  return {
    direction,
    flightIds: [it!.segments[0].flightId],
    fareFamilyId: opt!.fareFamilyId,
  };
}

/** POST /api/bookings cho một người lớn, trả phản hồi thô (để kiểm cả mã lỗi). */
async function hold(
  page: Page,
  email: string,
  journeys: Schemas["JourneyRequest"][],
  voucherCode = "",
) {
  return page.request.post("/api/bookings", {
    headers: await csrfHeaders(page),
    data: {
      journeys,
      passengers: [adult],
      baggage: [],
      contact: { name: "Phạm Thu Hà", email, phone: "0912345678" },
      voucherCode,
    },
  });
}

async function holdCode(...args: Parameters<typeof hold>) {
  const res = await hold(...args);
  expect(res.status(), await res.text()).toBe(201);
  return ((await res.json()) as Schemas["BookingCreatedResponse"]).code;
}

/** Thanh toán bằng Return URL VNPay đã ký (như khách trả thành công trên cổng). */
async function pay(page: Page, code: string) {
  const res = await page.request.post(`/api/bookings/${code}/payments`, {
    headers: await csrfHeaders(page),
  });
  expect(res.ok()).toBe(true);
  const { paymentUrl } = (await res.json()) as Schemas["PaymentUrlResponse"];
  await page.goto(paidReturn(paymentUrl));
  await expect(page).toHaveURL(`/bookings/${code}?payment=success`);
}

async function detail(page: Page, code: string) {
  const res = await page.request.get(`/api/bookings/${code}`);
  expect(res.ok()).toBe(true);
  return (await res.json()) as Booking;
}

const tickets = (b: Booking) =>
  b.passengers.flatMap((p) => p.tickets.map((t) => t.ticketNumber));

/** Tiêu đề các email Mailpit đã nhận cho `to`, chờ email gửi sau commit (bất đồng bộ). */
async function mailSubjects(page: Page, to: string, atLeast = 1) {
  let subjects: string[] = [];
  await expect
    .poll(async () => {
      const res = await page.request.get(
        `${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${to}"`)}`,
      );
      const { messages } = (await res.json()) as {
        messages: { Subject: string }[];
      };
      subjects = messages.map((m) => m.Subject);
      return subjects.length;
    })
    .toBeGreaterThanOrEqual(atLeast);
  return subjects;
}

/** Admin tạo một chuyến VN HAN→DAD ngày `date`, các gói phổ thông cùng giá 1.000.000 đ. */
async function newFlight(admin: Page, date: string, seats: number) {
  const headers = await csrfHeaders(admin);
  const fares = (await (
    await admin.request.get("/api/admin/airlines/VN/fare-families")
  ).json()) as Schemas["FareFamilyResponse"][];
  const flightNumber = `VN${String((Date.now() % 9000) + 1000)}`;
  const res = await admin.request.post("/api/admin/flights", {
    headers,
    data: {
      airlineCode: "VN",
      flightNumber,
      departureAirport: "HAN",
      arrivalAirport: "DAD",
      departureTime: "05:05",
      durationMinutes: 80,
      fromDate: date,
      toDate: date,
      daysOfWeek: [
        "MONDAY",
        "TUESDAY",
        "WEDNESDAY",
        "THURSDAY",
        "FRIDAY",
        "SATURDAY",
        "SUNDAY",
      ],
      aircraftModel: "Airbus A321",
      cabins: [{ cabinClass: "ECONOMY", totalSeats: seats }],
      fares: fares
        .filter((f) => f.cabinClass === "ECONOMY" && f.active)
        .map((f) => ({ fareFamilyId: f.id, price: 1_000_000 })),
    } satisfies Schemas["NewFlights"],
  });
  expect(res.ok(), await res.text()).toBe(true);
  const created = (await res.json()) as Schemas["CreatedFlights"];
  expect(created.created).toBe(1);
  return { id: created.flightIds[0], flightNumber };
}

test("D1: khứ hồi 4 khách, hành lý, WELCOME10, thanh toán ra 8 số vé và email vé", async ({
  page,
}) => {
  test.slow();
  const d = day(7);
  const email = await register(page);
  await page.goto(
    `/flights?from=HAN&to=SGN&date=${d}&returnDate=${d}&adults=2&children=1&infants=1&cabin=ECONOMY`,
  );
  for (const [card, step] of [
    ["Vietnam Airlines VN213", "Chọn chiều đi."],
    ["Vietnam Airlines VN220", "Chọn chiều về."],
  ]) {
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(step);
    const it = page.getByRole("article", { name: card, exact: true });
    await it.getByRole("button", { name: "Chọn", exact: true }).click();
    await it.getByRole("button", { name: "Chọn gói Economy Classic" }).click();
  }
  await expect(page).toHaveURL("/checkout");

  const people = [
    ["Người lớn 1", "nguyen", "van an", "Nam", "1985-01-10"],
    ["Người lớn 2", "tran", "thi binh", "Nữ", "1987-02-20"],
    ["Trẻ em 1", "nguyen", "minh chau", "Nữ", "2018-06-01"],
    ["Em bé 1", "nguyen", "gia bao", "Nam", day(-300)],
  ] as const;
  for (const [legend, last, first, gender, dob] of people) {
    const g = page.getByRole("group", { name: legend, exact: true });
    await field(g, "Họ").fill(last);
    await field(g, "Tên đệm và tên").fill(first);
    await field(g, "Giới tính").selectOption({ label: gender });
    await field(g, "Ngày sinh").fill(dob);
  }
  // 20 kg (350.000 đ) cho người lớn 1, chỉ chiều đi.
  await page
    .getByRole("group", { name: "Hành lý chiều đi", exact: true })
    .getByLabel("Người lớn 1", { exact: true })
    .selectOption({ index: 2 });
  await field(page, "Mã giảm giá").fill("WELCOME10");
  await page.getByRole("button", { name: "Áp dụng" }).click();

  // Mỗi chiều 2 × 1.870.000 + 1.683.000 + 187.000 = 5.610.000; + 350.000 hành lý; WELCOME10 chạm trần 200.000.
  const prices = page.getByRole("region", { name: "Bảng giá" });
  await expect(prices).toContainText("Giảm giá WELCOME10");
  await expect(prices.getByRole("definition").last()).toHaveText(
    "11.370.000 đ",
  );
  await page.getByRole("button", { name: "Giữ chỗ", exact: true }).click();
  await expect(page).toHaveURL(/\/bookings\/[A-Z0-9]{6}$/);
  const code = page.url().slice(-6);

  // Thanh toán từ nút trên C-09: lấy link VNPay (trang giả lập) rồi trả về bằng Return URL đã ký.
  await page.route("https://sandbox.vnpayment.vn/**", (route) =>
    route.fulfill({ contentType: "text/html", body: "<h1>VNPay sandbox</h1>" }),
  );
  await page.getByRole("button", { name: /^Thanh toán/ }).click();
  await expect(page).toHaveURL(/vnp_Amount=1137000000/);
  await page.goto(paidReturn(page.url()));
  await expect(page).toHaveURL(`/bookings/${code}?payment=success`);
  await expect(page.getByText("Đã xuất vé", { exact: true })).toBeVisible();

  const b = await detail(page, code);
  expect(b.status).toBe("ISSUED");
  expect(b.totalAmount).toBe(11_370_000);
  // BR-33: mức giảm chia cho hai chiều, cộng lại đúng 200.000.
  expect(b.journeys.map((j) => j.direction)).toEqual(["OUTBOUND", "RETURN"]);
  expect(b.journeys.every((j) => j.discount > 0)).toBe(true);
  expect(b.journeys.reduce((s, j) => s + j.discount, 0)).toBe(200_000);
  // 4 khách × 2 chặng, số vé 13 chữ số đầu 738 (VN), không trùng.
  const numbers = tickets(b);
  expect(numbers).toHaveLength(8);
  expect(new Set(numbers).size).toBe(8);
  for (const n of numbers) expect(n).toMatch(/^738\d{10}$/);

  const subjects = await mailSubjects(page, email);
  expect(subjects.some((s) => s.includes(code))).toBe(true);
});

test("D2: nối chuyến HAN→SGN→PQC cùng hãng, mỗi khách 2 số vé", async ({
  page,
}) => {
  const d = day(7);
  await register(page);
  await page.goto(
    `/flights?from=HAN&to=PQC&date=${d}&adults=1&children=0&infants=0&cabin=ECONOMY`,
  );
  const it = page.getByRole("article", {
    name: "Vietnam Airlines VN213 + VN1825",
    exact: true,
  });
  await expect(it).toContainText("1 điểm dừng, SGN");
  // Không có hành trình nối chuyến khác hãng (BR-02).
  const { itineraries } = (await (
    await page.request.get(
      `/api/flights/search?from=HAN&to=PQC&date=${d}&adults=1&children=0&infants=0&cabin=ECONOMY`,
    )
  ).json()) as Schemas["SearchResult"];
  for (const i of itineraries) {
    expect(new Set(i.segments.map((s) => s.airline.code)).size).toBe(1);
  }
  await it.getByRole("button", { name: "Chọn", exact: true }).click();
  await it.getByRole("button", { name: "Chọn gói Economy Lite" }).click();
  await expect(page).toHaveURL("/checkout");

  const g = page.getByRole("group", { name: "Người lớn 1", exact: true });
  await field(g, "Họ").fill("le");
  await field(g, "Tên đệm và tên").fill("van binh");
  await field(g, "Giới tính").selectOption({ label: "Nam" });
  await field(g, "Ngày sinh").fill("1988-08-08");
  await page.getByRole("button", { name: "Giữ chỗ", exact: true }).click();
  await expect(page).toHaveURL(/\/bookings\/[A-Z0-9]{6}$/);
  const code = page.url().slice(-6);
  await pay(page, code);

  const b = await detail(page, code);
  expect(b.status).toBe("ISSUED");
  const legs = b.journeys[0].segments;
  expect(legs.map((s) => s.flightNumber)).toEqual(["VN213", "VN1825"]);
  // BR-03: thời gian nối 60–720 phút.
  const layover =
    (Date.parse(legs[1].departureTime) - Date.parse(legs[0].arrivalTime)) /
    60_000;
  expect(layover).toBeGreaterThanOrEqual(60);
  expect(layover).toBeLessThanOrEqual(720);
  const numbers = tickets(b);
  expect(numbers).toHaveLength(2);
  for (const n of numbers) expect(n).toMatch(/^738\d{10}$/);
  await expect(page.getByRole("region", { name: "Hành trình" })).toContainText(
    "VN1825",
  );
});

test("D4: hai khách cùng giữ ghế cuối, chỉ một người được", async ({
  page,
  browser,
}) => {
  await login(page, "admin@demo.local", "/admin/flights");
  const flight = await newFlight(page, day(25), 1);

  const [a, b] = [await otherPage(browser), await otherPage(browser)];
  const emails = [await register(a), await register(b)];
  const body = await journey(a, {
    from: "HAN",
    to: "DAD",
    date: day(25),
    flight: flight.flightNumber,
    fare: "Economy Lite",
  });
  const results = await Promise.all(
    [a, b].map((p, i) => hold(p, emails[i], [body])),
  );
  expect(results.map((r) => r.status()).sort()).toEqual([201, 409]);
  const lost = results.find((r) => r.status() === 409)!;
  expect(((await lost.json()) as { code: string }).code).toBe(
    "SEATS_UNAVAILABLE",
  );

  const res = await page.request.get(`/api/admin/flights/${flight.id}`);
  const { flight: view } = (await res.json()) as Schemas["FlightDetail"];
  expect(view.cabins[0].availableSeats).toBe(0);
});

test("D8: huỷ chuyến huỷ booking chờ thanh toán, tạo yêu cầu hoàn 100% cho booking đã xuất vé", async ({
  page,
  browser,
}) => {
  test.slow();
  const date = day(26);
  await login(page, "admin@demo.local", "/admin/flights");
  const flight = await newFlight(page, date, 50);
  const q = {
    from: "HAN",
    to: "DAD",
    date,
    flight: flight.flightNumber,
    fare: "Economy Classic",
  };

  const issued = await otherPage(browser);
  const issuedEmail = await register(issued);
  const issuedCode = await holdCode(issued, issuedEmail, [
    await journey(issued, q),
  ]);
  await pay(issued, issuedCode);
  const paid = (await detail(issued, issuedCode)).totalAmount;

  const pending = await otherPage(browser);
  const pendingEmail = await register(pending);
  const body = [await journey(pending, q)];
  const pendingCode = await holdCode(pending, pendingEmail, body, "WELCOME10");

  // A-06: huỷ hai bước.
  await page.goto(`/admin/flights/${flight.id}`);
  await page.getByRole("button", { name: "Huỷ chuyến" }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Xác nhận huỷ chuyến" })
    .click();
  await expect(page.getByText("Đã huỷ", { exact: true }).first()).toBeVisible();

  // (b) booking chờ thanh toán bị huỷ, lượt WELCOME10 được trả (báo giá lại với mã này được).
  expect((await detail(pending, pendingCode)).status).toBe("CANCELLED");
  const again = await pending.request.post("/api/bookings/quote", {
    headers: await csrfHeaders(pending),
    data: {
      journeys: [
        await journey(pending, {
          from: "HAN",
          to: "SGN",
          date: day(5),
          flight: "VJ123",
          fare: "Eco",
        }),
      ],
      passengers: [adult],
      baggage: [],
      contact: {
        name: "Phạm Thu Hà",
        email: pendingEmail,
        phone: "0912345678",
      },
      voucherCode: "WELCOME10",
    },
  });
  expect(again.status(), await again.text()).toBe(200);

  // (a) booking đã xuất vé có yêu cầu hoàn FLIGHT_CANCELLED, phí 0, hoàn đủ số đã trả; email báo huỷ chuyến.
  const refunds = (await detail(issued, issuedCode)).refundRequests;
  expect(refunds).toHaveLength(1);
  expect(refunds[0]).toMatchObject({
    source: "FLIGHT_CANCELLED",
    status: "PENDING",
    feeAmount: 0,
    refundAmount: paid,
  });
  await expect
    .poll(async () => (await mailSubjects(issued, issuedEmail, 2)).join(" | "))
    .toMatch(/huỷ/i);

  // Chuyến không còn trong kết quả tìm kiếm; huỷ lần hai báo lỗi.
  const search = (await (
    await page.request.get(
      `/api/flights/search?from=HAN&to=DAD&date=${date}&adults=1&children=0&infants=0&cabin=ECONOMY`,
    )
  ).json()) as Schemas["SearchResult"];
  expect(
    search.itineraries.flatMap((i) => i.segments.map((s) => s.flightNumber)),
  ).not.toContain(flight.flightNumber);
  const twice = await page.request.post(
    `/api/admin/flights/${flight.id}/cancel`,
    { headers: await csrfHeaders(page) },
  );
  expect(twice.status()).toBe(409);
});

test("D9: đổi booking.hold_minutes chỉ áp dụng cho booking tạo sau đó", async ({
  page,
  browser,
}) => {
  await login(page, "admin@demo.local", "/admin/flights");
  const settings = (await (
    await page.request.get("/api/admin/settings")
  ).json()) as Schemas["Setting"][];
  const original = settings.find(
    (s) => s.key === "booking.hold_minutes",
  )!.value;
  const changed = original === 20 ? 25 : 20;
  const put = (value: number) =>
    csrfHeaders(page).then((headers) =>
      page.request.put("/api/admin/settings/booking.hold_minutes", {
        headers,
        data: { value },
      }),
    );

  const customer = await otherPage(browser);
  const email = await register(customer);
  const body = [
    await journey(customer, {
      from: "HAN",
      to: "SGN",
      date: day(5),
      flight: "VJ123",
      fare: "Eco",
    }),
  ];
  const minutes = async (code: string) => {
    const b = await detail(customer, code);
    return Math.round(
      (Date.parse(b.holdExpiresAt) - Date.parse(b.createdAt)) / 60_000,
    );
  };

  const before = await holdCode(customer, email, body);
  try {
    expect((await put(changed)).ok()).toBe(true);
    const after = await holdCode(customer, email, body);
    expect(await minutes(after)).toBe(changed);
    expect(await minutes(before)).toBe(original);
  } finally {
    expect((await put(original)).ok()).toBe(true);
  }
});

// Văn bản chính sách đọc tham số lúc có request (GET /api/settings/public), nên đổi tham số thì trang đổi ngay.
// Đặt ở đây vì cũng đổi booking.hold_minutes dùng chung như D9.
test("Chính sách: đổi booking.hold_minutes thì trang hoàn đổi hiện số mới", async ({
  page,
}) => {
  await login(page, "admin@demo.local", "/admin/flights");
  const settings = (await (
    await page.request.get("/api/settings/public")
  ).json()) as Record<string, number>;
  const original = settings["booking.hold_minutes"];
  const changed = original === 20 ? 25 : 20;
  const put = (value: number) =>
    csrfHeaders(page).then((headers) =>
      page.request.put("/api/admin/settings/booking.hold_minutes", {
        headers,
        data: { value },
      }),
    );

  expect((await put(changed)).ok()).toBe(true);
  try {
    await page.goto("/chinh-sach/hoan-doi-hanh-ly");
    const policy = page.locator("article.policy");
    await expect(policy).toContainText(`${changed} phút`);
    await expect(policy).not.toContainText("{{");
  } finally {
    expect((await put(original)).ok()).toBe(true);
  }
});

test("D10: báo cáo dòng tiền và doanh số tăng đúng theo booking vừa thanh toán", async ({
  page,
  browser,
}) => {
  await login(page, "admin@demo.local", "/admin/flights");
  const period = `from=${day(0)}&to=${day(0)}`;
  const report = async () => {
    const cash = (await (
      await page.request.get(
        `/api/admin/reports/cash-flow?${period}&groupBy=DAY`,
      )
    ).json()) as Schemas["CashFlowReport"];
    const sales = (await (
      await page.request.get(
        `/api/admin/reports/sales?${period}&groupBy=AIRLINE`,
      )
    ).json()) as Schemas["SalesReport"];
    const vj = sales.items.find((i) => i.groupKey === "VJ");
    return {
      charged: cash.totalCharged,
      net: cash.netRevenue,
      tickets: vj?.ticketCount ?? 0,
      revenue: vj?.revenue ?? 0,
    };
  };
  const before = await report();

  const customer = await otherPage(browser);
  const email = await register(customer);
  const code = await holdCode(customer, email, [
    await journey(customer, {
      from: "HAN",
      to: "SGN",
      date: day(6),
      flight: "VJ123",
      fare: "Eco",
    }),
  ]);
  await pay(customer, code);
  const b = await detail(customer, code);

  expect(await report()).toEqual({
    charged: before.charged + b.totalAmount,
    net: before.net + b.totalAmount,
    tickets: before.tickets + 1,
    revenue: before.revenue + b.fareTotal,
  });
});

test("D11: Customer gọi API admin nhận 403, mở booking của người khác nhận 404", async ({
  page,
  browser,
  playwright,
}) => {
  const ownerPage = await otherPage(browser);
  const ownerEmail = await register(ownerPage);
  const code = await holdCode(ownerPage, ownerEmail, [
    await journey(ownerPage, {
      from: "HAN",
      to: "SGN",
      date: day(5),
      flight: "VJ123",
      fare: "Eco",
    }),
  ]);

  await register(page);
  const admin = await page.request.get("/api/admin/airports");
  expect(admin.status()).toBe(403);
  expect(((await admin.json()) as { code: string }).code).toBe("FORBIDDEN");

  const other = await page.request.get(`/api/bookings/${code}`);
  expect(other.status()).toBe(404);
  expect(((await other.json()) as { code: string }).code).toBe(
    "RESOURCE_NOT_FOUND",
  );
  await page.goto(`/bookings/${code}`);
  await expect(page.getByText("Không tìm thấy đặt chỗ.")).toBeVisible();

  // Chưa đăng nhập, gửi POST không có CSRF: 403.
  const anonymous = await playwright.request.newContext({
    baseURL: test.info().project.use.baseURL,
  });
  expect((await anonymous.post("/api/bookings", { data: {} })).status()).toBe(
    403,
  );
  await anonymous.dispose();

  // Staff vào được /api/staff/**, không vào được /api/admin/**; Admin kế thừa quyền Staff.
  await login(page, "staff@demo.local", "/staff/bookings");
  expect((await page.request.get("/api/staff/bookings")).status()).toBe(200);
  expect((await page.request.get("/api/admin/airports")).status()).toBe(403);
  await login(page, "admin@demo.local", "/admin/flights");
  expect((await page.request.get("/api/staff/bookings")).status()).toBe(200);
});
