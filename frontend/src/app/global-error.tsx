"use client";

import "./globals.css";

/** Lỗi ở chính layout gốc: thay cả <html>, nên chỉ giữ khung tối thiểu (không dùng component phụ thuộc layout). */
export default function GlobalError({ retry }: { retry: () => void }) {
  return (
    <html lang="vi">
      <body>
        <main className="mx-auto flex min-h-dvh max-w-520 flex-col items-center justify-center gap-18 px-16 text-center">
          <h1 className="text-heading font-black tracking-tight text-ink">
            Có lỗi xảy ra
          </h1>
          <p className="text-muted">
            SkyLine không tải được. Hãy thử lại, nếu vẫn lỗi thì quay lại sau ít
            phút.
          </p>
          <button
            type="button"
            onClick={() => retry()}
            className="inline-flex min-h-40 items-center rounded-inner bg-cta px-18 py-10 text-caption font-semibold text-cta-fg hover:bg-cta-hover"
          >
            Thử lại
          </button>
        </main>
      </body>
    </html>
  );
}
