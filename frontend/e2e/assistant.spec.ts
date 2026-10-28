import { expect, test } from "@playwright/test";

// /assistant giữ cuộc trò chuyện khi F5 (lộ trình trợ lý, Bước 1). Service agent được giả lập bằng page.route,
// nên không cần backend hay model thật; cookie JSESSIONID giả chỉ để proxy.ts cho vào trang.
const proposal = (proposalId: string, flightNumber: string) => ({
  type: "proposal",
  proposalId,
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
  passenger: {
    lastName: "NGUYEN",
    firstName: "HUNG CUONG",
    gender: "MALE",
    dateOfBirth: "2004-03-12",
  },
  contact: {
    name: "Nguyễn Hùng Cường",
    email: "a@demo.local",
    phone: "0900000000",
  },
  totalAmount: 1_850_000,
});

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

test.beforeEach(async ({ context, baseURL }) => {
  await context.addCookies([
    { name: "JSESSIONID", value: "e2e", url: baseURL! },
  ]);
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
