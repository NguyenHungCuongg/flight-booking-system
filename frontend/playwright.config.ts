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
  retries: process.env.CI ? 1 : 0,
  reporter: "list",
  use: { baseURL, trace: "retain-on-failure" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : { command: "npm run dev", url: baseURL, reuseExistingServer: true },
});
