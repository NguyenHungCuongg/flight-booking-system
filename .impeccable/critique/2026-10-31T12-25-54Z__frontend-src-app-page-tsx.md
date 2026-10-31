---
target: landing /
total_score: 27
max_score: 36
na_heuristics: 7
p0_count: 1
p1_count: 3
target_identity: "file:F:\\UIT\\Flight Booking System\\frontend\\src\\app\\page.tsx"
target_fingerprint: "sha256:bdaa3fb4bc33c73bb57a304539ea8ad57df4d90bd8af6633a40b006f65e38515"
target_path: "F:\\UIT\\Flight Booking System\\frontend\\src\\app\\page.tsx"
timestamp: 2026-10-31T12-25-54Z
slug: frontend-src-app-page-tsx
---
Method: dual-agent (A: design review · B: detector + browser)

## Design Health Score (27/36, Good; #7 n/a: trang Persuade)
| # | Heuristic | Điểm | Vấn đề chính |
|---|---|---|---|
| 1 | Visibility of System Status | 3 | Focus rơi về <body> sau khi chọn sân bay |
| 2 | Match System / Real World | 3 | Tagline hero là khẩu hiệu, không "nói thẳng con số" |
| 3 | User Control and Freedom | 3 | Đóng popover không trả focus về trigger |
| 4 | Consistency and Standards | 2 | 5 kiểu khung bọc card; Nav riêng thay vì site-header |
| 5 | Error Prevention | 3 | Tốt (em bé ≤ người lớn, ngày quá khứ khoá) |
| 6 | Recognition Rather Than Recall | 3 | Accessible name field bị dính ("TừHANHà Nội") |
| 7 | Flexibility and Efficiency | n/a | Persuade |
| 8 | Aesthetic and Minimalist Design | 3 | Jet chiếm ~35% fold, bị dock cắt động cơ |
| 9 | Error Recovery | 3 | Lỗi cụ thể tại field |
| 10 | Help and Documentation | 4 | FAQ + link chính sách |

## Design Specificity
Thân trang (gói giá, story đếm ngược, công thức hoàn/đổi) là positioning thật; fold (jet 3D + "Tìm một giây. Bay vạn dặm.") và final CTA chung chung; bảng màu Wise đọc như fintech.
Detector (URL scan): nested-cards x3 (search-dock.tsx:507 trong hero.tsx:69; airlines.tsx:62/76), edge-flush-cards (routes.tsx:65, lộ lỗi thật: hover lift bị cắt), straggler x2-3. Tương phản 0 lỗi light mode; 27 tap target <44px ở 390.

## Priority Issues
- [P0] Tham số Admin ghi cứng: HOLD_MINUTES=15 (data.ts:41), 24 giờ, 90%/10%, 9 khách, 1-12 tiếng → fetch GET /api/settings/public trong page.tsx. /impeccable harden
- [P1] aria-live trên #bk-card (story.tsx:178) đọc timer mỗi giây → aria-live chỉ trên badge, timer aria-hidden. /impeccable harden
- [P1] Ô chọn sân bay mất focus, thiếu combobox/listbox, lịch ~30 tab stop. /impeccable harden
- [P1] Nav sticky mobile 193px (nav.tsx:60-107) → menu Popover dưới sm hoặc bỏ sticky. /impeccable adapt
- [P2] Fold bán khẩu hiệu + render; 1366×768 nút tìm sát mép; CTA cuối chung chung; gộp khung bọc card. /impeccable layout + clarify

## Persona Red Flags
Jordan: nav trộn anchor/route; "Đặt chỗ của tôi" x3 cần đăng nhập; Support không có kênh liên hệ (support.tsx:22-29); "Điền vào ô tìm" x6.
Riley: "15 phút" lệch khi Admin đổi; mất focus; sân bay ZQY từ E2E.
Casey: nav 193px; dock y≈645; ẩn prev/next card tuyến; preview Story ngoài màn hình.
Giảng viên: palette clone Wise; DESIGN.md tiêu đề "Wise — Style Reference" có mục lặp; ZQY trong demo.

## Minor
Dark mode mất nhịp dải forest, outline focus forest có thể chìm; ô gói giá thiếu nhãn "minh hoạ"; alt jet nên rỗng; "DAD Đà Nẵng / Đà Nẵng"; headline lệch ~40px; hover card tuyến bị cắt; link footer cao 24px; CLAUDE.md còn nói landing.css.

## Questions
Đưa con số sống lên fold? Gộp Story + AfterSales? Một chi tiết riêng SkyLine trong hướng Wise?
