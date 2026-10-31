import { readFileSync } from "node:fs";
import { expect, test } from "@playwright/test";
import {
  PLACEHOLDER,
  POLICIES,
  SETTING_KEYS,
  fillSettings,
  headingId,
  renderPolicy,
  sections,
  type PolicySlug,
} from "../src/lib/policies";

// Unit test cho văn bản chính sách: không mở trình duyệt, không cần backend.

/** Playwright chạy từ thư mục frontend (như ../.env trong helpers.ts). */
const md = (slug: PolicySlug) =>
  readFileSync(`content/chinh-sach/${slug}.md`, "utf8");

test("fillSettings thay mọi chỗ giữ chỗ", () => {
  expect(
    fillSettings(
      "Giữ {{booking.hold_minutes}} phút, trẻ em {{pricing.child_percent}}%.",
      { "booking.hold_minutes": 15, "pricing.child_percent": 90 },
    ),
  ).toBe("Giữ 15 phút, trẻ em 90%.");
});

test("fillSettings báo lỗi với khoá lạ", () => {
  expect(() =>
    fillSettings("{{booking.hold_minute}}", { "booking.hold_minutes": 15 }),
  ).toThrow("Thiếu tham số booking.hold_minute");
});

test("fillSettings báo lỗi khi backend thiếu giá trị", () => {
  expect(() => fillSettings("{{booking.hold_minutes}}", {})).toThrow(
    "Thiếu tham số booking.hold_minutes",
  );
});

test("headingId bỏ dấu tiếng Việt", () => {
  expect(headingId("Đổi chuyến")).toBe("doi-chuyen");
  expect(headingId("Cookie và lưu trữ trên trình duyệt")).toBe(
    "cookie-va-luu-tru-tren-trinh-duyet",
  );
  expect(headingId("Giá & thanh toán")).toBe("gia-thanh-toan");
});

test("sections lấy heading cấp 2 theo thứ tự", () => {
  expect(
    sections("Mở đầu\n\n## Hoàn vé\n\n### Phí\n\n## Đổi chuyến\n"),
  ).toEqual([
    { id: "hoan-ve", title: "Hoàn vé" },
    { id: "doi-chuyen", title: "Đổi chuyến" },
  ]);
});

test("renderPolicy gắn id cho heading và dựng bảng", () => {
  const html = renderPolicy(
    "## Đổi chuyến\n\n| Gói | Phí |\n|---|---|\n| Eco | 0 |\n",
  );
  expect(html).toContain('<h2 id="doi-chuyen">Đổi chuyến</h2>');
  expect(html).toContain("<table>");
});

for (const { slug } of POLICIES) {
  test(`${slug}: mọi chỗ giữ chỗ là khoá công khai`, () => {
    for (const [, key] of md(slug).matchAll(PLACEHOLDER))
      expect(SETTING_KEYS).toContain(key);
  });

  test(`${slug}: không có {{ lạc`, () => {
    expect(md(slug).replace(PLACEHOLDER, "")).not.toContain("{{");
  });

  test(`${slug}: có mục, không có heading cấp 1`, () => {
    expect(sections(md(slug)).length).toBeGreaterThanOrEqual(4);
    expect(md(slug)).not.toMatch(/^# /m);
  });

  test(`${slug}: heading không chứa chỗ giữ chỗ`, () => {
    for (const s of sections(md(slug))) expect(s.title).not.toContain("{{");
  });

  test(`${slug}: id heading không trùng trong mỗi văn bản`, () => {
    const ids = sections(md(slug)).map((s) => s.id);
    expect(new Set(ids).size).toBe(ids.length);
  });
}

test("cả ba văn bản có cùng mục Liên hệ", () => {
  const contact = (s: string) => s.slice(s.indexOf("## Liên hệ"));
  expect(md("dieu-khoan")).toContain("## Liên hệ");
  expect(contact(md("quyen-rieng-tu"))).toBe(contact(md("dieu-khoan")));
  expect(contact(md("hoan-doi-hanh-ly"))).toBe(contact(md("dieu-khoan")));
});

test("hoàn đổi nhắc thời hạn giữ chỗ và hạn chót hoàn đổi", () => {
  expect(md("hoan-doi-hanh-ly")).toContain("{{booking.hold_minutes}}");
  expect(md("hoan-doi-hanh-ly")).toContain(
    "{{aftersales.min_hours_before_departure}}",
  );
});
