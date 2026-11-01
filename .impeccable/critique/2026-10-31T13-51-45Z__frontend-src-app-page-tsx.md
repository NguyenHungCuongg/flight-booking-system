---
target: landing /
total_score: 26
max_score: 36
na_heuristics: 7
p0_count: 0
p1_count: 2
target_identity: "file:F:\\UIT\\Flight Booking System\\frontend\\src\\app\\page.tsx"
target_fingerprint: "sha256:bdaa3fb4bc33c73bb57a304539ea8ad57df4d90bd8af6633a40b006f65e38515"
target_path: "F:\\UIT\\Flight Booking System\\frontend\\src\\app\\page.tsx"
timestamp: 2026-10-31T13-51-45Z
slug: frontend-src-app-page-tsx
---
Method: dual-agent (A: design review · B: detector + browser)

## Design Health Score (26/36, Good 72%; #7 n/a)
| # | Heuristic | Điểm | Vấn đề chính |
|---|---|---|---|
| 1 | Visibility | 3 | Viền flash không tắt (search-dock.tsx:358); lỗi tham số hiện "…" |
| 2 | Real world | 3 | Tên gói lệch: Story "Tiết kiệm/Linh hoạt" vs Airlines "Economy Saver/Flex" |
| 3 | Control | 3 | Tab ra khỏi lịch không đóng lịch |
| 4 | Consistency | 2 | Lime cho tagline, Một chiều, Đăng nhập, CTA; thẻ Airlines thiếu nhãn minh hoạ |
| 5 | Error prevention | 3 | Gõ "HAN" ở ô Đến ra SIN (cHANgi) |
| 6 | Recognition | 3 | Tốt |
| 7 | Flexibility | n/a | Persuade |
| 8 | Aesthetic | 3 | Lime dùng quá nhiều; dấu "." accent lặp 6 heading |
| 9 | Error recovery | 3 | Không focus tới ô lỗi đầu |
| 10 | Help | 3 | FAQ không link văn bản chính sách |

## So với lần 1 (27/36)
5 vấn đề cũ đã sửa. Lần này tìm thêm lỗi có từ trước: lịch/ô sân bay bị bóp ở 390px. Không so cùng điều kiện.

## Detector
URL scan 4 (trước 6): nested-cards x3 (search-dock.tsx:523, airlines.tsx:68/82), edge-flush (routes.tsx:65) — false positive/cố ý. Straggler hết. Tương phản 0 lỗi light+dark; header mobile 65px; focus ring đủ; aria-live không đổi theo giây.

## Priority Issues
- [P1] Lịch + ô sân bay bị bóp ở 390 (CalendarPop in-flow trong ô 2 cột); nút tìm dưới fold ~200px → bottom sheet max-sm:fixed. /impeccable adapt
- [P1 demo] ZQY test airport; thẻ Airlines VN213 08:00/1.490.000 lệch seed (06:00/1.290.000), thiếu nhãn minh hoạ → reset DB, thêm nhãn. /impeccable harden
- [P2] Nhiều primary lime: nav Đăng nhập pillPrimary, segment Một chiều → pillLine, ink. /impeccable quieter
- [P2] Chọn tuyến chỉ scroll không focus; flash ring không tắt → focus #fld-dep, ring tự tắt. /impeccable polish
- [P2] Lỗi tham số hiện "…"/"--:00" → phân biệt loading vs lỗi. /impeccable harden

## Persona
Jordan: Đăng nhập nổi ngang CTA; không báo cần tài khoản; "Điền vào ô tìm". Riley: HAN→SIN; lịch không đóng khi Tab; ring kẹt; "…"; ZQY. Casey: lịch bóp; picker ngoài màn; CTA dưới fold; Story card ngoài màn. Giảng viên: ZQY; VN213 lệch; "đặt vé luôn đúng" vô hình; palette Wise.

## Minor
Ảnh vòng cung sau badge thẻ công thức; dark mode dải after-sales/footer chìm; FAQ aria-controls trỏ id không tồn tại; popup hành khách không chuyển focus; jet mobile ~120px; tap target <44 (swap 38, toggle, pax, Đổi/Hoàn 42).
