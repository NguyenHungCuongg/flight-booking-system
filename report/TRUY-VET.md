# Bảng truy vết yêu cầu (dùng nội bộ, không đưa vào báo cáo)

Báo cáo chỉ dùng mã của môn học (`YCNV-xx`, `BMx`, `QĐx.y`). File này giữ ánh xạ từ các mã đó sang mã trong tài liệu của dự án (`FR-xx`, `BR-xx`, `NFR-xx` trong `docs/PRD.md`) để agent và nhóm đối chiếu khi viết các chương sau hoặc kiểm tra với source code. Khi thêm hoặc sửa quy định ở chương 2, cập nhật file này.

## 1. Yêu cầu nghiệp vụ → yêu cầu chức năng

| Mã YCNV | Tên yêu cầu nghiệp vụ | Mã FR |
| --- | --- | --- |
| YCNV-01 | Tiếp nhận đăng ký tài khoản khách hàng | FR-01 |
| YCNV-02 | Tiếp nhận thông tin sân bay | FR-80 |
| YCNV-03 | Tiếp nhận thông tin hãng hàng không, gói giá và bảng giá hành lý | FR-81, FR-82, FR-83 |
| YCNV-04 | Lập lịch chuyến bay | FR-90 |
| YCNV-05 | Tiếp nhận mã giảm giá (voucher) | FR-100 |
| YCNV-06 | Tra cứu chuyến bay | FR-11, FR-12 |
| YCNV-07 | Tiếp nhận đặt vé và giữ chỗ | FR-20, FR-21, FR-22, FR-23, FR-24 |
| YCNV-08 | Thanh toán và xuất vé điện tử | FR-30, FR-31, FR-32 |
| YCNV-09 | Tiếp nhận và xử lý yêu cầu hoàn vé | FR-50, FR-51, FR-71 |
| YCNV-10 | Đổi lịch trình chuyến bay | FR-60, FR-61, FR-62 |
| YCNV-11 | Lập báo cáo doanh thu và dòng tiền | FR-120, FR-121, FR-122 |
| YCNV-12 | Tra cứu booking | FR-40, FR-41, FR-70 |
| YCNV-13 | Hủy chuyến bay | FR-93 |

## 2. Quy định → quy tắc nghiệp vụ

| Quy định | Mã gốc |
| --- | --- |
| QĐ1.1 | BR-100 |
| QĐ1.2 | BR-101 |
| QĐ2.1 | BR-90 |
| QĐ2.2 | BR-91 |
| QĐ3.1 | BR-90 |
| QĐ3.2 | BR-92 |
| QĐ3.3 | BR-24, BR-91 |
| QĐ4.1 | BR-80, BR-81 |
| QĐ4.2 | BR-81 |
| QĐ4.3 | BR-85 |
| QĐ4.4 | BR-91 |
| QĐ5.1 | FR-100, BR-30 |
| QĐ5.2 | BR-31 |
| QĐ5.3 | BR-30, BR-32 |
| QĐ6.1 | BR-01 |
| QĐ6.2 | BR-02, BR-03 |
| QĐ6.3 | BR-04 |
| QĐ6.4 | BR-05, BR-06 |
| QĐ6.5 | BR-11, BR-21 |
| QĐ7.1 | BR-10, BR-11 |
| QĐ7.2 | BR-12, BR-13, BR-14 |
| QĐ7.3 | BR-20, BR-21, BR-22, BR-23 |
| QĐ7.4 | BR-24 |
| QĐ7.5 | BR-40, BR-46 |
| QĐ7.6 | BR-30, BR-31, BR-32, BR-33 |
| QĐ8.1 | BR-41 |
| QĐ8.2 | BR-42 |
| QĐ8.3 | BR-43 |
| QĐ8.4 | BR-44 |
| QĐ9.1 | BR-50, BR-51, BR-60 |
| QĐ9.2 | BR-52, BR-53, BR-61 |
| QĐ9.3 | BR-62, BR-65 |
| QĐ9.4 | BR-63 |
| QĐ9.5 | BR-64 |
| QĐ9.6 | BR-66 |
| QĐ10.1 | BR-50, BR-51, BR-70 |
| QĐ10.2 | BR-71 |
| QĐ10.3 | BR-53, BR-72 |
| QĐ10.4 | BR-73 |
| QĐ10.5 | BR-74, BR-75 |
| QĐ11.1 | BR-110 |
| QĐ11.2 | BR-111 |
| QĐ11.3 | BR-112 |
| QĐ12.1 | BR-104 |
| QĐ12.2 | FR-40, FR-41, FR-70 |
| QĐ13.1 | BR-87, FR-93 |
| QĐ13.2 | BR-84 |

## 3. Yêu cầu chất lượng và yêu cầu hệ thống (mục 2.1.2, 2.1.3)

| Mục | Yêu cầu | Mã gốc |
| --- | --- | --- |
| 1. Tính tiến hóa | Nguyên tắc áp dụng quy định mới | BR-25 |
| 1. Tính tiến hóa | Bảo trì mã nguồn | NFR-10 |
| 1. Tính tiến hóa | Quản lý thay đổi schema | NFR-04 |
| 2. Tính tiện dụng | Gợi ý sân bay | FR-10 |
| 2. Tính tiện dụng | Tìm khứ hồi theo 2 bước | FR-12 |
| 2. Tính tiện dụng | Lọc và sắp xếp kết quả | FR-11 |
| 2. Tính tiện dụng | Báo giá trực tiếp | FR-20 |
| 2. Tính tiện dụng | Chỉ hiện thao tác hợp lệ | FR-41 |
| 2. Tính tiện dụng | Ngôn ngữ | NFR-09 |
| 3. Tính hiệu quả | Thời gian đáp ứng | NFR-01 |
| 3. Tính hiệu quả | Gửi email bất đồng bộ | FR-130 |
| 4. Tính tương thích | Thời gian | NFR-05 |
| 4. Tính tương thích | Tiền | NFR-06 |
| 4. Tính tương thích | Triển khai | NFR-08 |
| 1. Tính an toàn | Đúng đắn khi đồng thời | NFR-02 |
| 1. Tính an toàn | Ràng buộc trong cơ sở dữ liệu | NFR-04 |
| 1. Tính an toàn | Tự hủy giữ chỗ quá hạn | BR-45, BR-75 |
| 1. Tính an toàn | Khả năng kiểm thử | NFR-07 |
| 2. Tính bảo mật | Mật khẩu | NFR-03, BR-100 |
| 2. Tính bảo mật | Phiên đăng nhập | NFR-03, FR-02 |
| 2. Tính bảo mật | Chống CSRF (Cross-Site Request Forgery, giả mạo request từ trang web khác) | NFR-03 |
| 2. Tính bảo mật | Phân quyền | NFR-03, BR-104 |
| 2. Tính bảo mật | Khóa tài khoản | BR-102, BR-105 |
| 2. Tính bảo mật | Đặt lại mật khẩu | BR-103 |
| 2. Tính bảo mật | Callback VNPay | NFR-03, BR-42 |
| 2. Tính bảo mật | Bí mật hệ thống | NFR-03 |

## 4. Chỗ trong báo cáo đã đổi mã BR sang QĐ

- Mục 2.1.2, Tính tương thích, ý "Tiền": "Làm tròn theo `BR-21`, `BR-31`, `BR-33`" đổi thành `QĐ6.5`, `QĐ5.2`, `QĐ7.6`.
- Mục 2.1.2, ý "Nguyên tắc áp dụng quy định mới": `BR-25`; câu "đã snapshot (`BR-24`)" đổi thành `QĐ7.4`.
- Mục 2.1.3, Tính an toàn: "xử lý một lần (`BR-42`)" đổi thành `QĐ8.2`; "phần chưa hoàn của nó (`BR-62`)" đổi thành `QĐ9.3`.
- Mục 2.2, thay đổi quy định: câu "áp dụng cho giao dịch tạo sau thời điểm thay đổi" là `BR-25`.
