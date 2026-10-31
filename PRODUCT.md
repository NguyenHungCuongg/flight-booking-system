# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Khách hàng (`CUSTOMER`)**: người Việt tự tìm, đặt và thanh toán vé máy bay nội địa và quốc tế, rồi tự quản lý booking (thanh toán lại trong hạn giữ chỗ, huỷ giữ chỗ, hoàn vé, đổi chuyến). Khách chưa đăng nhập chỉ tìm chuyến; đặt vé bắt buộc có tài khoản.
- **Nhân viên vận hành (`STAFF`)**: tra cứu mọi booking, xử lý hàng đợi yêu cầu hoàn tiền, xem manifest, gửi lại email vé. Tài khoản vận hành không dùng để đặt vé.
- **Quản trị viên (`ADMIN`)**: mọi quyền của Staff, cộng quản lý danh mục (sân bay, hãng, gói giá, hành lý), chuyến bay (kể cả tạo hàng loạt, đổi giờ, huỷ), voucher, tài khoản, tham số hệ thống và báo cáo.
- **Hội đồng chấm đồ án UIT**: xem kịch bản demo (`docs/DEMO.md`). Đây là đối tượng ngang hàng với người dùng thật: màn khách hàng phải trung thực như một OTA thật, màn Staff/Admin phải để người xem demo hiểu ngay đang diễn ra nghiệp vụ gì.

## Product Purpose

SkyLine là đại lý bán vé máy bay trực tuyến (OTA) đa hãng theo mô hình Traveloka, làm trong đồ án môn học tại UIT. Khách tự tìm chuyến (bay thẳng, nối chuyến 1 điểm dừng cùng hãng, khứ hồi), giữ chỗ có thời hạn, thanh toán qua VNPay và nhận vé điện tử qua email; đại lý vận hành việc hoàn tiền; Admin quản lý nguồn vé và theo dõi doanh thu.

Thành công: chạy trọn kịch bản demo trên Docker Compose với VNPay sandbox, và hệ thống luôn đúng khi nhiều người thao tác cùng lúc.

## Positioning

- **Minh bạch chính sách**: điều kiện và phí hoàn, đổi, hành lý được nêu trước mỗi thao tác bằng báo giá (quote) trước khi giữ chỗ, hoàn hay đổi. Văn bản chính sách (`/chinh-sach/*`) được điền từ tham số hệ thống đang có hiệu lực, nên con số khách đọc luôn khớp con số hệ thống tính.
- **Đặt vé luôn đúng**: không bán vượt số ghế, không xuất vé hai lần, không hoàn vượt số tiền đã thu, có integration test chứng minh khi đặt đồng thời. Giữ chỗ có đồng hồ đếm ngược thật.

Trợ lý đặt vé AI (`agent/`) tồn tại trên nhánh thử nghiệm nhưng chưa phải điểm định vị.

## Operating Context

- Luồng khách: trang chủ tìm chuyến → kết quả (lọc, sắp xếp, khứ hồi hai bước) → `/checkout` (hành khách, hành lý, voucher, báo giá trực tiếp, giữ chỗ) → chi tiết booking (đếm ngược giữ chỗ, thanh toán VNPay, huỷ) → hoàn vé / đổi chuyến.
- Thanh toán qua cổng VNPay sandbox (chuyển trang ra ngoài rồi quay về Return URL); vé và mọi thông báo gửi qua email (không SMS, không push).
- Staff/Admin làm việc trên desktop, trong khu `/staff` và `/admin`.
- Demo trình chiếu theo `docs/DEMO.md` (D1–D11).

## Capabilities and Constraints

- Tiền một đơn vị là VND (số nguyên); giờ hiển thị theo múi giờ sân bay.
- Bốn hạng ghế (Phổ thông, Phổ thông đặc biệt, Thương gia, Hạng nhất); gói giá do từng hãng định nghĩa; ba loại hành khách (người lớn, trẻ em, em bé).
- Hoàn và đổi theo cả chiều, không lẻ từng hành khách hay từng chặng; không sửa thông tin hành khách sau khi đặt.
- Ngoài phạm vi: chọn chỗ ngồi, suất ăn, bảo hiểm, multi-city, nối chuyến khác hãng, đặt vé không tài khoản, đa tiền tệ, điểm thưởng, check-in, đăng nhập mạng xã hội.
- Nguồn vé mô phỏng: không kết nối GDS/NDC; Admin nhập lịch bay, số ghế và giá.
- Mọi chữ trên giao diện, thông báo lỗi và tài liệu bằng tiếng Việt.
- Không hard-code tham số Admin đổi được (thời hạn giữ chỗ, phí, mốc hoàn...) vào nội dung; đọc từ `GET /api/settings/public`.
- Thuật ngữ chuẩn: xem PRD §4 (booking, chiều, chặng, gói giá, giữ chỗ, xuất vé, giá trị chiều...).

## Brand Commitments

- Tên sản phẩm: **SkyLine** (khu quản trị: "SkyLine Admin").
- Giọng văn: tiếng Việt, rõ ràng, nói thẳng con số và điều kiện.

## Evidence on Hand

- Dữ liệu seed (profile `dev`): sân bay, hãng (Vietnam Airlines, Vietjet Air, Bamboo Airways, Singapore Airlines, Thai Airways...), gói giá, hành lý, voucher, 1.200 chuyến bay, tài khoản demo (`V100`–`V106`).
- Ảnh: `frontend/public/assets/images/` (máy bay SkyLine, cánh, đuôi, quả cầu chấm, cờ Việt Nam, ảnh hậu mãi).
- Văn bản chính sách thật: `frontend/content/chinh-sach/`.
- Không có: khách hàng thật, đánh giá, số liệu doanh thu thật, hợp tác chính thức với hãng bay, báo chí. Không bịa những thứ này; tên hãng trong seed chỉ là dữ liệu mô phỏng, không phải đối tác.

## Product Principles

1. **Con số trước, cam kết sau**: khách thấy giá, phí và điều kiện đầy đủ trước khi bấm giữ chỗ, hoàn hay đổi.
2. **Trạng thái luôn thật**: giữ chỗ, thanh toán, hoàn tiền hiển thị đúng trạng thái hệ thống, kể cả lỗi và thời hạn.
3. **Một nguồn sự thật**: chính sách, tham số và giá lấy từ hệ thống, không chép tay.
4. **Vận hành để đọc nhanh**: màn Staff/Admin ưu tiên tra cứu, quét danh sách và thao tác chính xác hơn trang trí.

## Accessibility & Inclusion

WCAG 2.2 AA là mức sàn (tương phản, bàn phím, focus). Desktop-first: demo và màn Staff/Admin chạy trên desktop; màn khách hàng trên điện thoại ở mức cố gắng tốt nhất. Hỗ trợ cả giao diện sáng và tối.
