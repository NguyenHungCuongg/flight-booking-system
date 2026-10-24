import type { Metadata } from "next";
import { Be_Vietnam_Pro } from "next/font/google";
import "./globals.css";

// Be Vietnam Pro vẽ dấu tiếng Việt ngay từ đầu (không chồng dấu lên dòng trên ở cỡ lớn). Không phải font biến nên khai
// đủ các độ đậm đang dùng, bao gồm 900 cho tiêu đề lớn kiểu Wise (NEW_DESIGN.md).
const sans = Be_Vietnam_Pro({
  subsets: ["latin", "vietnamese"],
  weight: ["400", "500", "600", "700", "800", "900"],
  variable: "--font-sans",
});

export const metadata: Metadata = {
  title: "SkyLine, tìm chuyến bay",
  description:
    "Vé máy bay của nhiều hãng, tìm và đặt ở một nơi. Giữ chỗ trước, thanh toán sau qua VNPay.",
};

const themeInitScript = `
(() => {
  try {
    const stored = localStorage.getItem("skyline-theme");
    if (stored === "dark" || stored === "light") {
      document.documentElement.setAttribute("data-theme", stored);
      document.documentElement.style.colorScheme = stored;
    }
  } catch {}
})();
`;

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    // suppressHydrationWarning: script khởi tạo theme hoặc extension như Dark Reader chèn thuộc tính vào <html> trước khi React hydrate.
    <html lang="vi" className={sans.variable} suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeInitScript }} />
      </head>
      <body>{children}</body>
    </html>
  );
}
