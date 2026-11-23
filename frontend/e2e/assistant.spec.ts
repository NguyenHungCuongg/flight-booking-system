import { expect, test, type Page } from "@playwright/test";

// /assistant giữ cuộc trò chuyện khi F5 (lộ trình trợ lý, Bước 1). Service agent được giả lập bằng page.route,
// nên không cần backend hay model thật; cookie JSESSIONID giả chỉ để proxy.ts cho vào trang.
const proposal = (proposalId: string, flightNumber: string) => ({
  type: "proposal",
  proposalId,
  journeys: [
    {
      direction: "OUTBOUND",
      segments: [
        {
          flightNumber,
          from: "SGN",
          to: "DAD",
          depart: "2026-10-30T08:10:00+07:00",
          arrive: "2026-10-30T09:30:00+07:00",
        },
      ],
      fare: "Phổ thông",
      fares: [
        { type: "ADULT", count: 1, unitPrice: 1_850_000, total: 1_850_000 },
      ],
      baggage: null,
    },
  ],
  passengers: [
    {
      lastName: "NGUYEN",
      firstName: "HUNG CUONG",
      gender: "MALE",
      dateOfBirth: "2004-03-12",
      type: "ADULT",
    },
  ],
  contact: {
    name: "Nguyễn Hùng Cường",
    email: "a@demo.local",
    phone: "0900000000",
  },
  discount: null,
  totalAmount: 1_850_000,
});

// Body của endpoint gửi tin (text/event-stream, lộ trình trợ lý Bước 6).
const sse = (...events: unknown[]) =>
  events.map((e) => `data: ${JSON.stringify(e)}\n\n`).join("");

const STEP = { type: "step", tool: "search_flights", args: {}, status: "ok" };

const VIEW = {
  entries: [
    { from: "user", text: "Đặt vé SGN đi Đà Nẵng" },
    {
      from: "agent",
      turn: {
        reply: "Bấm Giữ chỗ nhé.",
        trace: [{ tool: "search_flights", args: {}, status: "ok" }],
        cards: [proposal("p1", "VN122")],
      },
    },
    { from: "user", text: "Đổi chuyến sớm hơn" },
    {
      from: "agent",
      turn: {
        reply: "Mình đổi sang QH104.",
        trace: [],
        cards: [proposal("p2", "QH104")],
      },
    },
  ],
  proposalId: "p2",
};

test.beforeEach(async ({ context, baseURL, page }) => {
  await context.addCookies([
    { name: "JSESSIONID", value: "e2e", url: baseURL! },
  ]);
  await page.route("**/agent/chats", (r) =>
    r.request().method() === "GET"
      ? r.fulfill({ json: { chats: [] } })
      : r.fallback(),
  );
});

// Script chạy trước mỗi lần tải trang: chỉ đặt chatId ở lần đầu, sau đó để trang tự quản sessionStorage.
function remember(id: string) {
  if (!sessionStorage.getItem("e2e-seen")) {
    sessionStorage.setItem("e2e-seen", "1");
    sessionStorage.setItem("assistant-chat", id);
  }
}

test("F5 dựng lại chat một lần, chỉ thẻ còn hiệu lực có nút", async ({
  page,
}) => {
  let reads = 0;
  await page.addInitScript(remember, "abc");
  await page.route("**/agent/chats/abc", (r) => {
    reads++;
    return r.fulfill({ json: VIEW });
  });
  await page.route("**/agent/chats/abc/proposals/p2/approve", (r) =>
    r.fulfill({
      json: {
        reply: "Đã giữ chỗ K7M2QX.",
        trace: [],
        cards: [
          {
            type: "held",
            code: "K7M2QX",
            holdExpiresAt: "2026-10-30T06:25:00+07:00",
            totalAmount: 1_850_000,
          },
        ],
      },
    }),
  );
  await page.goto("/assistant");
  await expect(page.getByText("Đổi chuyến sớm hơn")).toBeVisible();
  await expect(page.getByText("Tìm chuyến bay phù hợp")).toBeVisible();
  await page.getByText("Chi tiết kỹ thuật (1 bước)").click();
  await expect(page.getByText("search_flights({}) → ok")).toBeVisible();
  await expect(page.getByRole("button", { name: "Giữ chỗ" })).toHaveCount(1);
  expect(reads).toBe(1);
  await page.getByRole("button", { name: "Giữ chỗ" }).click();
  await expect(page.getByText("Đã giữ chỗ K7M2QX")).toBeVisible();
});

test("Sửa báo service bỏ đề xuất", async ({ page }) => {
  await page.addInitScript(remember, "abc");
  await page.route("**/agent/chats/abc", (r) => r.fulfill({ json: VIEW }));
  const dismissed = page.waitForRequest(
    (r) =>
      r.method() === "POST" &&
      r.url().endsWith("/agent/chats/abc/proposals/p2/dismiss"),
  );
  await page.route("**/agent/chats/abc/proposals/p2/dismiss", (r) =>
    r.fulfill({ json: null }),
  );
  await page.goto("/assistant");
  await page.getByRole("button", { name: "Sửa" }).click();
  await dismissed;
  await expect(page.getByRole("button", { name: "Giữ chỗ" })).toHaveCount(0);
});

test("404 khi đọc lại thì mở chat trống, không báo lỗi", async ({ page }) => {
  await page.addInitScript(remember, "gone");
  await page.route("**/agent/chats/gone", (r) =>
    r.fulfill({
      status: 404,
      json: { detail: "Không tìm thấy cuộc trò chuyện." },
    }),
  );
  // Khung chat trống có sẵn trong HTML prerender: chờ trang đọc lại xong rồi mới kiểm.
  const read = page.waitForResponse("**/agent/chats/gone");
  await page.goto("/assistant");
  await read;
  await expect
    .poll(() => page.evaluate(() => sessionStorage.getItem("assistant-chat")))
    .toBeNull();
  await expect(page.getByText("Gõ yêu cầu bằng lời")).toBeVisible();
  await expect(page.getByRole("textbox")).toBeEnabled();
  await expect(page.getByText("Không tìm thấy")).toHaveCount(0);
});

async function sendWith(page: Page, body: string) {
  await page.route("**/agent/chats", (r) =>
    r.request().method() === "GET"
      ? r.fulfill({ json: { chats: [] } })
      : r.fulfill({ status: 201, json: { chatId: "new" } }),
  );
  await page.route("**/agent/chats/new/messages", (r) =>
    r.fulfill({ contentType: "text/event-stream", body }),
  );
  await page.goto("/assistant");
  await page.getByRole("textbox").fill("Đặt vé SGN đi Đà Nẵng");
  await page.getByRole("button", { name: "Gửi" }).click();
}

test("Gửi tin: hiện các bước rồi câu trả lời", async ({ page }) => {
  await sendWith(
    page,
    sse(STEP, {
      type: "turn",
      turn: {
        reply: "Bấm Giữ chỗ nhé.",
        trace: [{ tool: "search_flights", args: {}, status: "ok" }],
        cards: [proposal("p1", "VN122")],
      },
    }),
  );
  await expect(page.getByText("Bấm Giữ chỗ nhé.")).toBeVisible();
  await expect(page.getByText("Tìm chuyến bay phù hợp")).toHaveCount(1);
  await expect(page.getByRole("button", { name: "Giữ chỗ" })).toHaveCount(1);
});

test("Stream đóng mà không có câu trả lời thì báo lỗi", async ({ page }) => {
  await sendWith(page, sse(STEP));
  await expect(
    page.getByText("Trợ lý đang không hoạt động. Hãy thử lại sau."),
  ).toBeVisible();
  await expect(page.getByText("search_flights({}) → ok")).toHaveCount(0);
  await expect(page.getByRole("textbox")).toBeEnabled();
  await expect(page.getByText("Đặt vé SGN đi Đà Nẵng")).toBeVisible();
});

test("Đường dẫn trong câu trả lời bấm được", async ({ page }) => {
  const link = "/chinh-sach/hoan-doi-hanh-ly#dieu-kien-chung";
  await page.addInitScript(remember, "abc");
  await page.route("**/agent/chats/abc", (r) =>
    r.fulfill({
      json: {
        entries: [
          { from: "user", text: "Đổi vé trước bao lâu?" },
          {
            from: "agent",
            turn: { reply: `Xem ${link} nhé.`, trace: [], cards: [] },
          },
        ],
        proposalId: null,
      },
    }),
  );
  await page.goto("/assistant");
  await expect(page.getByRole("link", { name: link })).toHaveAttribute(
    "href",
    link,
  );
});

test("Cuộc trò chuyện mới xoá khung chat và vẫn gửi được tin mới", async ({
  page,
}) => {
  await page.addInitScript(remember, "abc");
  await page.route("**/agent/chats/abc", (r) => r.fulfill({ json: VIEW }));
  await page.route("**/agent/chats", (r) =>
    r.request().method() === "GET"
      ? r.fulfill({ json: { chats: [] } })
      : r.fulfill({ status: 201, json: { chatId: "fresh" } }),
  );
  await page.route("**/agent/chats/fresh/messages", (r) =>
    r.fulfill({
      contentType: "text/event-stream",
      body: sse({
        type: "turn",
        turn: { reply: "Chào bạn, mình hỗ trợ gì được?", trace: [], cards: [] },
      }),
    }),
  );
  await page.goto("/assistant");
  await expect(page.getByText("Đổi chuyến sớm hơn")).toBeVisible();
  await page.getByRole("button", { name: "Cuộc trò chuyện mới" }).click();
  await expect(page.getByText("Đổi chuyến sớm hơn")).toHaveCount(0);
  const input = page.getByRole("textbox");
  const send = page.getByRole("button", { name: "Gửi" });
  await expect(input).toBeEnabled();
  await expect(send).toBeDisabled();
  await input.fill("Xin chào");
  await expect(send).toBeEnabled();
  await send.click();
  await expect(page.getByText("Chào bạn, mình hỗ trợ gì được?")).toBeVisible();
});

test("Hiển thị thẻ nguồn hành khách từ đặt chỗ gần đây và thanh ngữ cảnh phiên", async ({
  page,
}) => {
  await page.route("**/agent/chats", (r) =>
    r.request().method() === "GET"
      ? r.fulfill({ json: { chats: [] } })
      : r.fulfill({ status: 201, json: { chatId: "new" } }),
  );
  await page.route("**/agent/chats/new/messages", (r) =>
    r.fulfill({
      contentType: "text/event-stream",
      body: sse({
        type: "turn",
        turn: {
          reply: "Mình đã lấy thông tin hành khách từ đơn trước.",
          trace: [{ tool: "list_recent_passengers", args: {}, status: "ok" }],
          cards: [
            {
              type: "memory_used",
              source: "recent_bookings",
              passengers: [
                {
                  fullName: "NGUYEN VAN AN",
                  gender: "MALE",
                  dateOfBirth: "1998-05-15",
                  bookingCode: "K7M2QX",
                },
              ],
            },
          ],
        },
      }),
    }),
  );
  await page.goto("/assistant");
  await page.getByRole("textbox").fill("Đặt cho khách lần trước");
  await page.getByRole("button", { name: "Gửi" }).click();
  await expect(
    page.getByText("Thông tin hành khách từ đặt chỗ gần đây"),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "K7M2QX" })).toHaveAttribute(
    "href",
    "/bookings/K7M2QX",
  );
  await expect(
    page.getByText("Tra cứu hành khách từ các đặt chỗ gần đây"),
  ).toBeVisible();
  await expect(
    page.getByLabel("Ngữ cảnh trợ lý đang nắm"),
  ).toContainText("NGUYEN VAN AN");
});

test("Lịch sử trò chuyện: mở lại chat cũ và xoá chat đang mở", async ({
  page,
}) => {
  const chats = [
    {
      chatId: "old-1",
      title: "Tìm vé SGN đi Đà Nẵng",
      updatedAt: "2026-11-23T18:00:00+07:00",
    },
  ];
  await page.route("**/agent/chats", (r) => r.fulfill({ json: { chats } }));
  await page.route("**/agent/chats/old-1", (r) =>
    r.request().method() === "DELETE"
      ? r.fulfill({ status: 204 })
      : r.fulfill({ json: VIEW }),
  );
  await page.goto("/assistant");
  await page.getByRole("button", { name: /^Tìm vé SGN đi Đà Nẵng/ }).click();
  await expect(page.getByText("Đổi chuyến sớm hơn")).toBeVisible();
  await page
    .getByRole("button", { name: "Xoá cuộc trò chuyện Tìm vé SGN đi Đà Nẵng" })
    .click();
  await expect(page.getByText("Đổi chuyến sớm hơn")).toHaveCount(0);
  await expect(page.getByText("Gõ yêu cầu bằng lời")).toBeVisible();
  await expect(
    page.getByRole("button", { name: /^Tìm vé SGN đi Đà Nẵng/ }),
  ).toHaveCount(0);
});

test("Phiên hết hạn (401 từ /agent/chats) chuyển hướng về /login", async ({
  page,
}) => {
  await page.route("**/agent/chats", (r) =>
    r.fulfill({ status: 401, json: { detail: "Phiên đăng nhập đã hết hạn." } }),
  );
  await page.goto("/assistant");
  await expect(page).toHaveURL(/\/login\?next=%2Fassistant/);
});

test("Service agent tắt (503 khi nạp lịch sử) vẫn mở trang trống không crash", async ({
  page,
}) => {
  await page.route("**/agent/chats", (r) =>
    r.fulfill({ status: 503, json: { detail: "Service unavailable" } }),
  );
  await page.goto("/assistant");
  await expect(page.getByText("Gõ yêu cầu bằng lời")).toBeVisible();
  await expect(page.getByRole("textbox")).toBeEnabled();
});
