import { defineConfig, devices } from "@playwright/test";

/**
 * E2E chạy trên backend thật (profile dev) và frontend Next.js, xem CLAUDE.md.
 * Mặc định tự bật `npm run dev` ở cổng 3000 (hoặc dùng lại server đang chạy).
 * Đặt E2E_BASE_URL để chạy trên một server khác, VD bản build: `npx next start -p 3100`.
 */
const baseURL = process.env.E2E_BASE_URL ?? "http://localhost:3000";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  // npm run dev dịch route theo yêu cầu: 8 luồng (mặc định trên máy 16 nhân) làm trang hydrate chậm, click rơi vào
  // form chưa hydrate. 4 luồng chạy ổn định.
  workers: 4,
  retries: process.env.CI ? 1 : 0,
  reporter: "list",
  use: { baseURL, trace: "retain-on-failure" },
  projects: [
    {
      name: "chromium",
      testIgnore: /demo\.spec\.ts/,
      use: { ...devices["Desktop Chrome"] },
    },
    // Kịch bản demo D1–D11: đổi tham số dùng chung và so số liệu báo cáo, nên chạy sau cùng và tuần tự.
    {
      name: "demo",
      testMatch: /demo\.spec\.ts/,
      dependencies: ["chromium"],
      fullyParallel: false,
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : { command: "npm run dev", url: baseURL, reuseExistingServer: true },
});
