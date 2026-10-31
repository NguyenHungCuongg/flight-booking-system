import { expect, test } from "@playwright/test";
import {
  fillSettings,
  headingId,
  renderPolicy,
  sections,
} from "../src/lib/policies";

// Unit test cho văn bản chính sách: không mở trình duyệt, không cần backend.

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
