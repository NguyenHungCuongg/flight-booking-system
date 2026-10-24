import { expect, test } from "@playwright/test";
import {
  cabinLabel,
  listQuery,
  pageHref,
  pageOf,
  refundActions,
  sizeOf,
} from "../src/lib/staff";

const url = (q: string) => new URLSearchParams(q);

test("listQuery: bỏ ô rỗng, giữ thứ tự khoá, trang URL đếm từ 1 sang API đếm từ 0", () => {
  expect(
    listQuery(url("from=&to=SGN&date=&airline=QH&page=3&x=1"), [
      "from",
      "to",
      "date",
      "airline",
      "status",
    ]),
  ).toBe("to=SGN&airline=QH&page=2");
  expect(listQuery(url("q=%20%20&status="), ["q", "status"])).toBe("page=0");
  expect(listQuery(url("q=abc123&page=0"), ["q"])).toBe("q=abc123&page=0");
  expect(listQuery(url("page=abc"), ["q"])).toBe("page=0");
});

test("sizeOf/listQuery: chỉ nhận 10, 15, 20; size chỉ gửi lên API khi có trên URL", () => {
  expect(sizeOf(url(""))).toBe(20);
  expect(sizeOf(url("size=10"))).toBe(10);
  expect(sizeOf(url("size=999"))).toBe(20);
  expect(listQuery(url("size=15&page=2"), ["q"])).toBe("page=1&size=15");
  expect(listQuery(url("size=7"), ["q"])).toBe("page=0&size=20");
});

test("pageOf: thiếu, sai hoặc nhỏ hơn 1 thì là trang 1", () => {
  expect(pageOf(url(""))).toBe(1);
  expect(pageOf(url("page=-2"))).toBe(1);
  expect(pageOf(url("page=2.7"))).toBe(2);
});

test("pageHref: giữ bộ lọc khác rỗng, trang 1 không ghi page", () => {
  const p = url("status=PENDING&source=&page=2");
  expect(pageHref("/staff/refunds", p, 3)).toBe(
    "/staff/refunds?status=PENDING&page=3",
  );
  expect(pageHref("/staff/refunds", p, 1)).toBe(
    "/staff/refunds?status=PENDING",
  );
  expect(pageHref("/staff/flights", url("from=&page=4"), 1)).toBe(
    "/staff/flights",
  );
});

test("refundActions: theo bảng S-04 thao tác (BR-63, BR-64, BR-66)", () => {
  expect(refundActions("PENDING", "CUSTOMER")).toEqual({
    approve: true,
    reject: true,
    settle: false,
  });
  expect(refundActions("PENDING", "FLIGHT_CANCELLED")).toEqual({
    approve: true,
    reject: false,
    settle: false,
  });
  expect(refundActions("PENDING", "INVALID_PAYMENT").reject).toBe(false);
  expect(refundActions("APPROVED", "CUSTOMER")).toEqual({
    approve: false,
    reject: false,
    settle: true,
  });
  const none = { approve: false, reject: false, settle: false };
  expect(refundActions("REJECTED", "CUSTOMER")).toEqual(none);
  expect(refundActions("COMPLETED", "INVALID_PAYMENT")).toEqual(none);
});

test("cabinLabel: nhãn tiếng Việt, mã lạ giữ nguyên", () => {
  expect(cabinLabel("ECONOMY")).toBe("Phổ thông");
  expect(cabinLabel("XYZ")).toBe("XYZ");
});
