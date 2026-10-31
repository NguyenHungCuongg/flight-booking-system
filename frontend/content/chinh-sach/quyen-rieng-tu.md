Có hiệu lực từ ngày 01/11/2026.

Đây là văn bản mô phỏng của đồ án môn học, không có giá trị pháp lý.

Chính sách này giải thích Công ty TNHH SkyLine (pháp nhân mô phỏng) thu thập, sử dụng và bảo vệ dữ liệu cá nhân của bạn như thế nào khi bạn dùng trang SkyLine. Chính sách được xây dựng theo Nghị định 13/2023/NĐ-CP về bảo vệ dữ liệu cá nhân.

## Dữ liệu chúng tôi thu thập

**Khi bạn tạo tài khoản:** email, họ tên, số điện thoại và mật khẩu. Mật khẩu chỉ được lưu ở dạng băm (BCrypt), không ai đọc được mật khẩu gốc, kể cả nhân viên SkyLine.

**Khi bạn đặt vé:**

- Thông tin liên hệ của đặt chỗ: họ tên, email, số điện thoại.
- Thông tin từng hành khách: họ tên, giới tính, ngày sinh. Với hành trình có chặng quốc tế, thêm quốc tịch, số hộ chiếu và ngày hết hạn hộ chiếu.
- Chuyến bay, gói giá, hành lý mua thêm, mã giảm giá đã dùng.
- Lịch sử thanh toán, hoàn tiền và đổi chuyến của đặt chỗ, kèm lý do bạn nhập khi gửi yêu cầu hoàn.

**Khi bạn thanh toán:** địa chỉ IP của bạn, vì cổng VNPay yêu cầu thông tin này cho mỗi giao dịch.

SkyLine **không** thu thập số thẻ, ngày hết hạn thẻ hay mã bảo mật thẻ. Bạn nhập các thông tin này trực tiếp trên trang của VNPay.

## Mục đích sử dụng

- Giữ chỗ, xuất vé và gửi vé điện tử cho bạn.
- Gửi email giao dịch: vé điện tử, kết quả hoàn tiền, đổi chuyến, thông báo chuyến bay đổi giờ hoặc bị huỷ, đặt lại mật khẩu.
- Xử lý thanh toán, hoàn tiền và đổi chuyến.
- Kiểm tra điều kiện của hành khách: tính loại hành khách theo ngày sinh, kiểm tra hộ chiếu cho chặng quốc tế.
- Hỗ trợ bạn khi cần: nhân viên tra cứu đặt chỗ theo mã đặt chỗ, email hoặc số điện thoại liên hệ, và gửi lại email vé.
- Lập báo cáo doanh thu và tình hình bán vé nội bộ.

SkyLine không dùng dữ liệu của bạn cho quảng cáo và không bán dữ liệu cho bất kỳ ai.

## Chia sẻ dữ liệu với bên thứ ba

- **VNPay** (cổng thanh toán) nhận mã giao dịch, số tiền, nội dung thanh toán có mã đặt chỗ của bạn, và địa chỉ IP. VNPay không nhận họ tên, email hay thông tin hành khách từ SkyLine.
- **Nhà cung cấp dịch vụ email** chuyển email giao dịch tới địa chỉ email liên hệ của bạn.
- **Cơ quan nhà nước có thẩm quyền**, khi pháp luật yêu cầu.

Trong phiên bản mô phỏng này, SkyLine không kết nối với hệ thống của hãng bay, nên dữ liệu hành khách không được gửi tới hãng.

## Ai trong SkyLine xem được dữ liệu của bạn

- **Bạn** chỉ xem được tài khoản và đặt chỗ của chính mình.
- **Nhân viên hỗ trợ** xem được chi tiết đặt chỗ và tài khoản đã đặt khi tra cứu để hỗ trợ bạn, xử lý yêu cầu hoàn, hoặc lập danh sách hành khách của một chuyến.
- **Quản trị viên** quản lý tài khoản (tìm theo email hoặc họ tên, khoá và mở khoá) và xem báo cáo tổng hợp.

## Cookie và lưu trữ trên trình duyệt

| Tên              | Loại            | Mục đích                                                         | Thời hạn                            |
| ---------------- | --------------- | ---------------------------------------------------------------- | ----------------------------------- |
| `JSESSIONID`     | Cookie          | Giữ phiên đăng nhập. Không đọc được bằng JavaScript (`HttpOnly`) | Hết hạn sau 30 phút không hoạt động |
| `XSRF-TOKEN`     | Cookie          | Chống giả mạo yêu cầu (CSRF)                                     | Theo phiên trình duyệt              |
| `skyline-theme`  | Local storage   | Nhớ giao diện sáng hoặc tối bạn chọn                             | Tới khi bạn xoá dữ liệu trình duyệt |
| Chuyến đang chọn | Session storage | Mang chuyến bay bạn chọn ở trang tìm chuyến sang trang đặt vé    | Tới khi đóng tab                    |

SkyLine không dùng cookie quảng cáo, cookie theo dõi hay công cụ phân tích của bên thứ ba.

## Bảo mật dữ liệu

- Mật khẩu được băm bằng BCrypt.
- Cookie phiên có `HttpOnly` và `SameSite=Lax`, thêm `Secure` khi chạy HTTPS.
- Mọi yêu cầu thay đổi dữ liệu đều được kiểm tra chống giả mạo (CSRF).
- Hệ thống kiểm tra vai trò và quyền sở hữu đặt chỗ ở mọi yêu cầu.
- Mọi phản hồi từ VNPay đều được kiểm tra chữ ký trước khi xử lý.
- Mật khẩu cơ sở dữ liệu, khoá VNPay và tài khoản email nằm trong biến môi trường, không nằm trong mã nguồn, và không bao giờ được ghi vào log.

## Thời gian lưu trữ

Dữ liệu tài khoản và đặt chỗ được lưu trong suốt thời gian tài khoản tồn tại. Dữ liệu đặt chỗ, thanh toán và hoàn tiền cần cho việc đối soát và báo cáo, nên không bị xoá khi đặt chỗ kết thúc.

## Quyền của bạn

- **Xem và sửa:** bạn xem thông tin tài khoản, sửa họ tên và số điện thoại, đổi mật khẩu trong trang Tài khoản. Email không đổi được.
- **Xem đặt chỗ:** bạn xem chi tiết mọi đặt chỗ của mình trong trang Đặt chỗ của tôi.
- **Yêu cầu khác:** để nhận bản sao dữ liệu, yêu cầu xoá tài khoản hoặc rút lại sự đồng ý, bạn gửi email tới địa chỉ hỗ trợ ở mục Liên hệ. Trang SkyLine chưa có chức năng tự làm các việc này.

Thông tin hành khách không sửa được sau khi giữ chỗ (xem [Điều khoản & điều kiện](/chinh-sach/dieu-khoan)).

## Dữ liệu của trẻ em

Thông tin trẻ em và em bé do người lớn đặt vé khai thay, và chỉ dùng cho các mục đích ở trên. Người đặt vé xác nhận mình có quyền cung cấp thông tin của các hành khách trong đặt chỗ.

## Thay đổi chính sách

SkyLine có thể cập nhật chính sách này. Ngày hiệu lực ở đầu trang cho biết phiên bản đang áp dụng.

## Liên hệ

Công ty TNHH SkyLine (pháp nhân mô phỏng)

- Địa chỉ: Số 01 đường Mô Phỏng, Khu đô thị Đồ Án, TP. Hồ Chí Minh (địa chỉ mô phỏng)
- Mã số thuế: 0000000000 (mô phỏng)
- Hotline: 028 0000 0000 (số mô phỏng)
- Email hỗ trợ: hotro@skyline.example
