import { expect, test } from "@playwright/test";
import {
  validateSettingValue,
  WEEKDAY_OPTIONS,
  VOUCHER_TYPE_LABEL,
} from "../src/lib/admin";

test.describe("admin.ts pure helpers", () => {
  test("WEEKDAY_OPTIONS contains 7 days from Monday (1) to Sunday (7)", () => {
    expect(WEEKDAY_OPTIONS).toHaveLength(7);
    expect(WEEKDAY_OPTIONS[0]).toEqual({ day: 1, label: "Thứ 2" });
    expect(WEEKDAY_OPTIONS[6]).toEqual({ day: 7, label: "Chủ nhật" });
  });

  test("VOUCHER_TYPE_LABEL maps percent and fixed", () => {
    expect(VOUCHER_TYPE_LABEL.PERCENT).toBe("Phần trăm (%)");
    expect(VOUCHER_TYPE_LABEL.FIXED).toBe("Số tiền cố định");
  });

  test("validateSettingValue verifies bounds and connection rules", () => {
    expect(validateSettingValue("booking.hold_minutes", 15)).toBeNull();
    expect(validateSettingValue("booking.hold_minutes", 3)).toBe(
      "Giá trị phải từ 5 đến 60",
    );
    expect(validateSettingValue("booking.hold_minutes", 70)).toBe(
      "Giá trị phải từ 5 đến 60",
    );
  });
});
