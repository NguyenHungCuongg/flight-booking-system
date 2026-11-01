import { expect, test } from "@playwright/test";
import { replyParts } from "../src/app/assistant/agent-api";

test("replyParts tách đường dẫn nội bộ, bỏ dấu câu ở cuối", () => {
  expect(
    replyParts(
      "Xem /chinh-sach/hoan-doi-hanh-ly#doi-chuyen. Hoặc tự đặt trên /flights, nhé",
    ),
  ).toEqual([
    "Xem ",
    { href: "/chinh-sach/hoan-doi-hanh-ly#doi-chuyen" },
    ". Hoặc tự đặt trên ",
    { href: "/flights" },
    ", nhé",
  ]);
  expect(replyParts("Không có link")).toEqual(["Không có link"]);
  expect(replyParts("/bookings/K7M2QX/refund/outbound")).toEqual([
    { href: "/bookings/K7M2QX/refund/outbound" },
  ]);
});
