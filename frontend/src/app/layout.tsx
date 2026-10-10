import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

const inter = Inter({
  subsets: ["latin", "vietnamese"],
  axes: ["opsz"],
  variable: "--font-sans",
});

export const metadata: Metadata = {
  title: "SkyLine, tìm chuyến bay",
  description:
    "Vé máy bay của nhiều hãng, tìm và đặt ở một nơi. Giữ chỗ trước, thanh toán sau qua VNPay.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    // suppressHydrationWarning: extension như Dark Reader chèn thuộc tính vào <html> trước khi React hydrate.
    <html lang="vi" className={inter.variable} suppressHydrationWarning>
      <body>{children}</body>
    </html>
  );
}
