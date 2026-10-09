# PRD — Hệ thống bán vé máy bay trực tuyến

| | |
|---|---|
| Phiên bản | 1.0 |
| Ngày | 2026-10-09 |
| Trạng thái | Đã chốt nghiệp vụ. Chưa thiết kế UI/UX |
| Tài liệu liên quan | [TDD](TDD.md) · [App Flow](APP_FLOW.md) · [Backend Schema](BACKEND_SCHEMA.md) |

Tài liệu này định nghĩa **cái gì** hệ thống phải làm. Mỗi quy tắc nghiệp vụ có mã `BR-xx`, mỗi yêu cầu chức năng có mã `FR-xx`; các tài liệu khác tham chiếu theo mã này.

---

## 1. Tổng quan

### 1.1 Bối cảnh

Đồ án môn học tại UIT, nhóm từ 5 người, thời gian từ 12 tuần. Hệ thống mô phỏng một **đại lý bán vé máy bay trực tuyến (OTA) đa hãng** theo mô hình của [Traveloka](https://www.traveloka.com): khách tự tìm, đặt và thanh toán vé của nhiều hãng bay; đại lý vận hành việc hoàn tiền; quản trị viên quản lý nguồn vé và theo dõi doanh thu.

Hệ thống không kết nối được với hệ thống đặt chỗ thật của các hãng (GDS/NDC), nên **nguồn vé được mô phỏng**: Admin nhập lịch bay, số ghế, gói giá và giá bán của từng hãng.

### 1.2 Mục tiêu

1. Khách hàng tự tìm chuyến (bay thẳng, nối chuyến, khứ hồi), đặt vé, thanh toán qua VNPay và nhận vé điện tử.
2. Khách hàng tự quản lý booking: thanh toán lại trong thời hạn giữ chỗ, huỷ giữ chỗ, yêu cầu hoàn vé, đổi chuyến.
3. Nhân viên vận hành xử lý yêu cầu hoàn tiền và hỗ trợ khách.
4. Quản trị viên quản lý danh mục, chuyến bay, giá, voucher, tham số nghiệp vụ và xem báo cáo.
5. Hệ thống luôn đúng khi nhiều người thao tác cùng lúc: không bán vượt số ghế, không xuất vé hai lần, không hoàn vượt số tiền đã thu.

### 1.3 Tiêu chí thành công

- Chạy trọn các kịch bản ở [mục 9](#9-kịch-bản-demo-và-tiêu-chí-nghiệm-thu) trên Docker Compose với VNPay sandbox.
- Có integration test chứng minh không bán vượt ghế khi nhiều người đặt đồng thời.
- Lập trình viên cài đặt và viết test được cho mọi quy tắc `BR-xx` mà không phải hỏi lại.

---

## 2. Người dùng và vai trò

| Vai trò | Mô tả | Cách có tài khoản |
|---|---|---|
| Khách chưa đăng nhập | Tra cứu chuyến bay | Không cần |
| Khách hàng (`CUSTOMER`) | Đặt vé, thanh toán, quản lý booking của mình | Tự đăng ký |
| Nhân viên vận hành (`STAFF`) | Tra cứu mọi booking, xử lý yêu cầu hoàn tiền, hỗ trợ khách | Admin tạo |
| Quản trị viên (`ADMIN`) | Toàn bộ quyền của Staff, cộng quản lý dữ liệu, cấu hình, báo cáo | Admin tạo; tài khoản admin đầu tiên có sẵn trong dữ liệu khởi tạo |

Mỗi tài khoản có đúng một vai trò. Tài khoản Staff và Admin là tài khoản vận hành, không dùng để đặt vé.

### 2.1 Ma trận quyền

| Chức năng | Chưa đăng nhập | Customer | Staff | Admin |
|---|:-:|:-:|:-:|:-:|
| Tìm chuyến, tra cứu sân bay và hãng | ✓ | ✓ | ✓ | ✓ |
| Đặt vé, thanh toán, huỷ giữ chỗ | | ✓ | | |
| Xem booking của mình, hoàn vé, đổi chuyến | | ✓ | | |
| Tra cứu mọi booking, gửi lại email vé | | | ✓ | ✓ |
| Xử lý yêu cầu hoàn tiền | | | ✓ | ✓ |
| Xem danh sách chuyến bay và hành khách theo chuyến | | | ✓ | ✓ |
| Quản lý sân bay, hãng, gói giá, bảng giá hành lý | | | | ✓ |
| Quản lý chuyến bay (tạo, sửa, huỷ, xoá) | | | | ✓ |
| Quản lý voucher, tài khoản, tham số hệ thống | | | | ✓ |
| Xem báo cáo | | | | ✓ |

---

## 3. Phạm vi

### 3.1 Trong phạm vi

- Đường bay nội địa và quốc tế, một đơn vị tiền tệ là VND.
- Hành trình một chiều hoặc khứ hồi. Mỗi chiều bay thẳng hoặc nối chuyến tối đa 1 điểm dừng, hai chuyến nối phải cùng hãng.
- Bốn hạng ghế (Phổ thông, Phổ thông đặc biệt, Thương gia, Hạng nhất) và các gói giá do từng hãng định nghĩa.
- Ba loại hành khách: người lớn, trẻ em, em bé.
- Mua thêm hành lý ký gửi, mã giảm giá (voucher).
- Giữ chỗ có thời hạn, thanh toán qua VNPay sandbox, xuất vé điện tử và gửi email.
- Hoàn vé (khách yêu cầu, Staff duyệt, hoàn tiền qua VNPay) và đổi chuyến (khách tự làm, trả phí và chênh lệch).
- Xử lý khi hãng đổi giờ hoặc huỷ chuyến.
- Quản trị danh mục, chuyến bay (có tạo hàng loạt), voucher, tài khoản, tham số và báo cáo.

### 3.2 Ngoài phạm vi

| Hạng mục | Ghi chú |
|---|---|
| Thiết kế UI/UX | Làm sau. [App Flow](APP_FLOW.md) chỉ định nghĩa màn hình ở mức chức năng |
| Kết nối GDS/NDC hoặc hệ thống thật của hãng | Nguồn vé do Admin nhập |
| Chọn chỗ ngồi, suất ăn, bảo hiểm | |
| Hành trình nhiều chặng (multi-city), nối chuyến khác hãng, nhiều hơn 1 điểm dừng | |
| Đặt vé không cần tài khoản | |
| Cổng riêng cho đối tác hãng bay | |
| Giá động, đa tiền tệ, điểm thưởng | |
| Hoàn hoặc đổi lẻ từng hành khách, từng chặng | Hoàn và đổi theo cả chiều |
| Sửa thông tin hành khách sau khi đặt | |
| SMS, push notification | Chỉ gửi email |
| Đăng nhập bằng mạng xã hội, xác thực email khi đăng ký | |
| Check-in, thẻ lên máy bay | |

---

## 4. Thuật ngữ

| Thuật ngữ | Ý nghĩa |
|---|---|
| Booking (đặt chỗ) | Một lần đặt vé, gồm 1–2 chiều và nhiều hành khách. Có **mã đặt chỗ** 6 ký tự |
| Chiều (journey) | Chiều đi (`OUTBOUND`) hoặc chiều về (`RETURN`). Gồm 1 chặng (bay thẳng) hoặc 2 chặng (nối chuyến) |
| Chặng (segment) | Một chuyến bay cụ thể nằm trong một chiều |
| Chuyến bay (flight) | Một lần bay cụ thể: hãng, số hiệu, sân bay đi và đến, giờ cất cánh và hạ cánh |
| Hạng ghế (cabin class) | `ECONOMY`, `PREMIUM_ECONOMY`, `BUSINESS`, `FIRST`. Số ghế được quản lý theo từng hạng |
| Gói giá (fare family) | Gói bán vé của một hãng trong một hạng ghế (VD "Economy Lite"). Quy định hành lý, có được hoàn/đổi không và mức phí |
| Khách chiếm ghế | Người lớn và trẻ em. Em bé ngồi cùng người lớn, không chiếm ghế |
| Vé điện tử (e-ticket) | Vé của 1 hành khách trên 1 chặng, có số vé 13 chữ số |
| Giữ chỗ (hold) | Trừ ghế tạm thời trong lúc chờ khách thanh toán |
| Xuất vé (issue) | Cấp số vé sau khi thanh toán thành công |
| Giá trị chiều | Số tiền khách đang có trong một chiều, dùng để tính tiền hoàn (BR-52) |
| Lượt thu, lượt hoàn | Một giao dịch thu tiền hoặc hoàn tiền qua VNPay (hoặc hoàn thủ công) |
| Snapshot | Bản sao giá và quy định tại thời điểm mua, lưu trong booking |
| IPN | Lời gọi server-to-server từ VNPay báo kết quả thanh toán |
| Manifest | Danh sách hành khách của một chuyến bay |
| Tỉ lệ lấp đầy (load factor) | Tỉ lệ ghế đã bán hoặc đang giữ trên tổng số ghế |

---

## 5. Yêu cầu chức năng

Mức ưu tiên: **P0** là bắt buộc cho luồng demo cốt lõi. **P1** là cần có. **P2** là có thì tốt, làm nếu còn thời gian.

### E1. Tài khoản

#### FR-01 Đăng ký · P0

**Là** khách, **tôi muốn** tạo tài khoản bằng email và mật khẩu **để** đặt vé và quản lý booking.

- Nhập email, mật khẩu, họ tên, số điện thoại. Tài khoản tạo ra có vai trò `CUSTOMER`, trạng thái `ACTIVE` (BR-100, BR-101).
- Email đã tồn tại thì báo lỗi `EMAIL_ALREADY_USED`.
- Đăng ký thành công thì được đăng nhập luôn.

#### FR-02 Đăng nhập, đăng xuất · P0

**Là** người dùng, **tôi muốn** đăng nhập **để** dùng các chức năng theo vai trò của mình.

- Sai email hoặc mật khẩu thì báo `INVALID_CREDENTIALS` bằng một thông báo chung, không tiết lộ email có tồn tại hay không.
- Tài khoản `LOCKED` thì báo `ACCOUNT_LOCKED` (BR-102).
- Phiên hết hạn sau 30 phút không hoạt động. Đăng xuất thì phiên bị huỷ ngay.

#### FR-03 Quên và đặt lại mật khẩu · P1

**Là** người dùng quên mật khẩu, **tôi muốn** nhận link đặt lại qua email **để** lấy lại tài khoản.

- Sau khi nhập email, hệ thống luôn trả lời "đã gửi email nếu tài khoản tồn tại".
- Link chứa token dùng một lần, hết hạn sau 30 phút (BR-103). Token sai, hết hạn hoặc đã dùng thì báo `INVALID_TOKEN`.

#### FR-04 Hồ sơ cá nhân · P1

- Xem và sửa họ tên, số điện thoại. Email không đổi được.
- Đổi mật khẩu phải nhập đúng mật khẩu hiện tại. Mật khẩu mới theo BR-100.

### E2. Tìm kiếm

#### FR-10 Tra cứu sân bay · P0

- Gợi ý sân bay theo mã IATA, tên sân bay hoặc thành phố. Chỉ hiện sân bay đang hoạt động.

#### FR-11 Tìm chuyến một chiều · P0

**Là** khách, **tôi muốn** tìm các chuyến từ A đến B vào một ngày **để** so sánh và chọn vé phù hợp.

- **Đầu vào:** sân bay đi, sân bay đến (khác nhau), ngày đi theo giờ địa phương của sân bay đi, số người lớn, trẻ em, em bé (thoả BR-11), hạng ghế. Không cần đăng nhập.
- **Kết quả:** danh sách hành trình bay thẳng và nối chuyến thoả BR-01 đến BR-06. Mỗi hành trình gồm:
  - các chặng: số hiệu, hãng, sân bay, giờ cất cánh và hạ cánh theo giờ địa phương, thời lượng bay, loại máy bay;
  - số điểm dừng, thời gian nối chuyến, tổng thời lượng;
  - danh sách gói giá. Mỗi gói hiển thị tên, hành lý xách tay và ký gửi, có được hoàn/đổi không và mức phí, giá người lớn của chiều, **tổng giá cho đúng số khách đã nhập** (BR-21), số ghế còn (nhỏ nhất qua các chặng).
- Không có kết quả thì trả danh sách rỗng, không phải lỗi.
- Kết quả có đủ dữ liệu để lọc theo hãng, số điểm dừng, khung giờ cất cánh, khoảng giá và sắp xếp theo giá, giờ cất cánh, tổng thời lượng. Việc lọc và sắp xếp làm ở client.

#### FR-12 Tìm khứ hồi · P0

- Nhập thêm ngày về (không trước ngày đi).
- Khách chọn chiều đi trước, sau đó hệ thống tìm chiều về theo tuyến ngược lại.
- Chỉ cho chọn chiều về thoả BR-04 so với chiều đi đã chọn.

### E3. Đặt vé

#### FR-20 Báo giá trước khi giữ chỗ · P0

- Gửi hành trình đã chọn, hành khách, hành lý và voucher thì nhận lại bảng giá chi tiết: giá vé theo loại khách, hành lý, giảm giá, tổng tiền (BR-20 đến BR-23, BR-30, BR-31).
- Báo giá không giữ ghế và không ghi dữ liệu.
- Báo giá kiểm tra giống hệt bước giữ chỗ (FR-24), nên lỗi nào xảy ra khi giữ chỗ cũng sẽ hiện ra ở đây.

#### FR-21 Nhập thông tin hành khách và liên hệ · P0

- Mỗi hành khách khai: loại khách, họ, tên đệm và tên, giới tính, ngày sinh. Nếu hành trình có chặng quốc tế thì khai thêm quốc tịch, số hộ chiếu, ngày hết hạn hộ chiếu (BR-10 đến BR-13).
- Thông tin liên hệ mặc định lấy từ tài khoản, khách sửa được (BR-14).

#### FR-22 Mua thêm hành lý · P1

- Theo BR-22. Hiển thị bảng giá hành lý của hãng khai thác từng chiều.

#### FR-23 Áp voucher · P1

- Theo BR-30 đến BR-33.
- Voucher không dùng được thì báo `VOUCHER_INVALID` kèm lý do: không tồn tại, chưa đến hạn, hết hạn, hết lượt, chưa đạt giá trị đơn tối thiểu, hoặc tài khoản đã dùng.

#### FR-24 Giữ chỗ · P0

**Là** khách, **tôi muốn** giữ chỗ cho hành trình đã chọn **để** có thời gian thanh toán mà không mất ghế.

- Tạo booking `PENDING_PAYMENT` có mã đặt chỗ (BR-46), trừ ghế và giữ chỗ trong `booking.hold_minutes` phút (BR-40). Giá được chốt tại thời điểm này (BR-24).
- Bất kỳ chặng nào thiếu ghế thì báo `SEATS_UNAVAILABLE` và không giữ gì cả.
- Chuyến không còn bán được (đã huỷ, quá sát giờ bay, gói giá ngừng bán) thì báo `FLIGHT_NOT_BOOKABLE`.
- Nhiều khách cùng đặt những ghế cuối cùng thì tổng số ghế bán ra không vượt quá số ghế còn.

### E4. Thanh toán và xuất vé

#### FR-30 Thanh toán qua VNPay · P0

- Từ booking `PENDING_PAYMENT` chưa quá hạn, khách bấm thanh toán và được chuyển sang trang VNPay (BR-41). Booking đã quá hạn thì báo `HOLD_EXPIRED`.
- Sau khi thanh toán, khách quay về trang chi tiết booking và thấy thông báo thành công hoặc thất bại.
- Thanh toán thất bại hoặc khách huỷ trên VNPay thì booking vẫn chờ thanh toán. Khách thử lại được trong thời hạn giữ chỗ.

#### FR-31 Xuất vé · P0

- Thanh toán thành công (BR-42) thì booking chuyển `ISSUED`, cấp số vé (BR-43) và gửi email vé điện tử tới email liên hệ.
- Cùng một kết quả thanh toán có thể đến nhiều lần (VNPay gửi lại IPN, cộng với Return URL). Hệ thống chỉ xử lý một lần.

#### FR-32 Thanh toán trễ hoặc trùng · P1

- Theo BR-44. Yêu cầu hoàn tự tạo xuất hiện trong hàng đợi của Staff.

#### FR-33 Huỷ giữ chỗ · P0

- Hệ thống tự huỷ booking quá hạn giữ chỗ và chuyển sang `EXPIRED` (BR-45).
- Khách chủ động huỷ booking chưa thanh toán thì booking chuyển `CANCELLED`.
- Cả hai trường hợp đều trả ghế và trả lượt voucher.

### E5. Quản lý booking

#### FR-40 Danh sách booking của tôi · P0

- Hiển thị mới nhất trước, lọc được theo trạng thái, có phân trang.

#### FR-41 Chi tiết booking · P0

- **Thông tin chung:** mã đặt chỗ, trạng thái, thời hạn giữ chỗ còn lại (nếu đang chờ thanh toán), thông tin liên hệ.
- **Từng chiều:** gói giá và quy định (snapshot), trạng thái chiều, các chặng hiện hành.
- **Chi tiết khác:** hành khách, số vé của từng hành khách trên từng chặng, hành lý mua thêm, bảng giá, các lượt thu và hoàn, yêu cầu hoàn và đổi chuyến cùng trạng thái của chúng.
- **Thao tác:** chỉ hiện những thao tác hợp lệ với trạng thái hiện tại: thanh toán, huỷ giữ chỗ, hoàn một chiều, đổi một chiều.
- Khách chỉ xem được booking của mình. Mã đặt chỗ của người khác thì báo `RESOURCE_NOT_FOUND` (BR-104).

### E6. Hoàn vé

#### FR-50 Xem trước số tiền hoàn · P1

- Với một chiều thoả BR-51 và BR-60, hiển thị giá trị chiều, phí hoàn và số tiền sẽ được hoàn (BR-52, BR-53, BR-61).
- Không thoả điều kiện thì báo lỗi tương ứng: `FARE_RULE_NOT_ALLOWED`, `DEADLINE_PASSED`, `REQUEST_IN_PROGRESS` hoặc `INVALID_STATE`.

#### FR-51 Gửi yêu cầu hoàn · P1

**Là** khách, **tôi muốn** huỷ một chiều đã mua và nhận lại tiền **để** không mất toàn bộ tiền khi đổi kế hoạch.

- Bắt buộc nhập lý do. Hệ thống tạo yêu cầu `PENDING`, số tiền được chốt tại thời điểm gửi.
- Mỗi chiều chỉ có một yêu cầu hoàn đang mở.

#### FR-52 Theo dõi yêu cầu hoàn · P1

- Khách xem được trạng thái yêu cầu, ghi chú và lý do từ chối (nếu có).
- Khách nhận email khi yêu cầu bị từ chối hoặc khi đã hoàn tiền xong.

### E7. Đổi chuyến

#### FR-60 Tìm chuyến thay thế · P1

- Khách chọn ngày mới, hệ thống trả danh sách hành trình thoả BR-71. Mỗi lựa chọn kèm số tiền phải trả (BR-72).

#### FR-61 Xác nhận đổi · P1

- Theo BR-73. Hệ thống tạo yêu cầu đổi chờ thanh toán. Nếu tổng phải trả bằng 0 thì hoàn tất ngay.

#### FR-62 Thanh toán và hoàn tất đổi · P1

- Thanh toán qua VNPay giống FR-30.
- Thành công thì áp dụng BR-74 và gửi email vé mới. Quá hạn thì áp dụng BR-75.

### E8. Vận hành (Staff)

#### FR-70 Tra cứu booking · P0

- Tìm theo mã đặt chỗ, email hoặc số điện thoại liên hệ.
- Xem chi tiết đầy đủ như FR-41, kèm thông tin tài khoản đã đặt.

#### FR-71 Xử lý yêu cầu hoàn · P1

**Là** Staff, **tôi muốn** duyệt hoặc từ chối yêu cầu hoàn **để** khách nhận lại tiền đúng chính sách.

- Hàng đợi lọc được theo trạng thái và nguồn, yêu cầu cũ nhất hiển thị trước.
- Các thao tác: duyệt (BR-62), từ chối (BR-63), thử lại hoặc xác nhận đã hoàn thủ công (BR-64).
- Yêu cầu do hệ thống tạo thì không từ chối được (BR-66).
- Hệ thống ghi nhận người xử lý và thời điểm xử lý.

#### FR-72 Danh sách hành khách theo chuyến · P2

- Liệt kê các vé đang hiệu lực của booking `ISSUED` trên một chuyến: mã đặt chỗ, họ tên, loại khách, số vé, hạng ghế, thông tin liên hệ.

#### FR-73 Gửi lại email vé · P2

- Chỉ áp dụng cho booking `ISSUED`.

### E9. Danh mục (Admin)

#### FR-80 Sân bay · P0

- Thêm, sửa, ngừng kích hoạt, xoá nếu chưa được dùng (BR-90, BR-91).

#### FR-81 Hãng bay · P0

- Như FR-80, có thêm mã số vé 3 chữ số (duy nhất).

#### FR-82 Gói giá · P0

- Mỗi gói thuộc một hãng và một hạng ghế, quy định hành lý xách tay, hành lý ký gửi, có được hoàn/đổi không và mức phí (BR-92).
- Sửa gói không ảnh hưởng vé đã bán (BR-24).

#### FR-83 Bảng giá hành lý · P1

- Mỗi hãng có các mức kg, mỗi mức một giá. Trong một hãng không có hai mức kg trùng nhau (BR-92).

### E10. Chuyến bay (Admin)

#### FR-90 Tạo chuyến · P0

**Là** Admin, **tôi muốn** tạo một chuyến hoặc cả lịch bay định kỳ trong một lần **để** mở bán nhanh.

- **Thông tin nhập:** hãng, số hiệu, sân bay đi và đến, giờ cất cánh theo giờ địa phương, thời lượng bay, khoảng ngày, các thứ trong tuần, loại máy bay, số ghế từng hạng, giá bán từng gói giá (BR-80, BR-81, BR-85).
- Tạo một chuyến lẻ thì chọn khoảng ngày chỉ gồm một ngày.
- **Kết quả:** số chuyến được tạo và số ngày bị bỏ qua vì trùng.

#### FR-91 Danh sách và chi tiết chuyến · P0

- Lọc theo tuyến, ngày, hãng, trạng thái. Staff cũng xem được danh sách này.
- Trang chi tiết có số ghế tổng và ghế còn theo hạng, giá theo gói, số booking.

#### FR-92 Sửa chuyến · P1

- Theo BR-82, BR-83, BR-86, BR-87.

#### FR-93 Huỷ chuyến · P1

- Admin phải xác nhận hai bước. Hệ quả theo BR-84.

#### FR-94 Xoá chuyến · P2

- Chỉ xoá được chuyến chưa từng có booking (BR-82).

### E11. Voucher (Admin)

#### FR-100 Quản lý voucher · P1

- Tạo, sửa, ngừng kích hoạt voucher. Mã voucher duy nhất và không sửa được sau khi tạo.
- Xem được số lượt đã dùng.
- Thay đổi chỉ áp dụng cho booking mới.

### E12. Tài khoản và tham số (Admin)

#### FR-110 Quản lý tài khoản · P1

- Tìm theo email hoặc họ tên, lọc theo vai trò.
- Tạo tài khoản `STAFF` hoặc `ADMIN` với mật khẩu ban đầu.
- Khoá và mở khoá tài khoản (BR-102, BR-105).

#### FR-111 Tham số hệ thống · P1

- Xem và sửa các tham số ở [mục 7](#7-tham-số-hệ-thống), chỉ trong khoảng hợp lệ.
- Ghi nhận người sửa và thời điểm sửa. Giá trị mới chỉ áp dụng cho giao dịch mới (BR-25).

### E13. Báo cáo (Admin)

#### FR-120 Dòng tiền · P1

- Theo BR-110, nhóm theo ngày, tháng hoặc năm.

#### FR-121 Doanh số vé · P1

- Theo BR-111, nhóm theo hãng hoặc theo tuyến.

#### FR-122 Tỉ lệ lấp đầy · P2

- Theo BR-112, lọc được theo hãng.

### E14. Thông báo

#### FR-130 Email giao dịch · P1

- Hệ thống gửi email bất đồng bộ. Gửi email lỗi thì chỉ ghi log, không làm hỏng giao dịch.

| Email | Khi nào | Người nhận |
|---|---|---|
| Vé điện tử | Xuất vé (FR-31) hoặc Staff gửi lại (FR-73) | Email liên hệ của booking |
| Đổi chuyến thành công | BR-74 | Email liên hệ của booking |
| Đã hoàn tiền | Yêu cầu hoàn chuyển `COMPLETED` | Email liên hệ của booking |
| Từ chối hoàn | Yêu cầu hoàn chuyển `REJECTED` | Email liên hệ của booking |
| Chuyến bay đổi giờ | BR-83 | Email liên hệ của các booking bị ảnh hưởng |
| Chuyến bay bị huỷ | BR-84 | Email liên hệ của các booking `ISSUED` bị ảnh hưởng |
| Đặt lại mật khẩu | FR-03 | Email của tài khoản |

---

## 6. Quy tắc nghiệp vụ

Các con số in nghiêng kèm tên tham số (VD *3 giờ* `booking.min_hours_before_departure`) là giá trị mặc định, Admin đổi được (mục 7).

### 6.1 Tìm kiếm và hành trình

| Mã | Quy tắc |
|---|---|
| BR-01 | Chỉ bán chuyến ở trạng thái `SCHEDULED` và cất cánh sau thời điểm hiện tại ít nhất *3 giờ* (`booking.min_hours_before_departure`). Áp dụng cho chặng đầu tiên của mỗi chiều. |
| BR-02 | Một chiều gồm 1 chặng (bay thẳng) hoặc 2 chặng (nối chuyến, đúng 1 điểm dừng). |
| BR-03 | Nối chuyến: hai chuyến cùng hãng; sân bay đến của chặng 1 là sân bay đi của chặng 2; thời gian nối (giờ cất cánh chặng 2 trừ giờ hạ cánh chặng 1) nằm trong khoảng *60–720 phút* (`search.min_connection_minutes`, `search.max_connection_minutes`). |
| BR-04 | Khứ hồi: chiều về đi từ sân bay đến của chiều đi, về sân bay đi của chiều đi. Chặng đầu chiều về phải cất cánh sau giờ hạ cánh của chặng cuối chiều đi. Hai chiều độc lập về hãng và gói giá. |
| BR-05 | Mỗi chiều dùng đúng một gói giá. Gói giá phải đang kích hoạt, thuộc hãng khai thác chiều đó và được bán trên mọi chặng của chiều. Hạng ghế của chiều là hạng ghế của gói giá. |
| BR-06 | Mỗi chặng phải còn ít nhất (số người lớn + số trẻ em) ghế trong hạng ghế của chiều. |

### 6.2 Hành khách

| Mã | Quy tắc |
|---|---|
| BR-10 | Loại khách xác định theo tuổi tròn năm tại ngày cất cánh (giờ địa phương) của chặng đầu tiên trong chiều đi: `ADULT` từ 12 tuổi, `CHILD` từ 2 đến dưới 12 tuổi, `INFANT` dưới 2 tuổi. Ngày sinh không được sau ngày cất cánh đó. Loại khách khai báo phải khớp với tuổi. |
| BR-11 | Mỗi booking có ít nhất 1 người lớn. Số em bé không vượt quá số người lớn. Số khách chiếm ghế (người lớn + trẻ em) không vượt quá *9* (`booking.max_seated_passengers`). Em bé không chiếm ghế. |
| BR-12 | Họ, và tên đệm + tên, viết in hoa không dấu (chỉ A–Z), các từ cách nhau đúng một khoảng trắng, mỗi phần dài 1–50 ký tự. |
| BR-13 | Chặng quốc tế là chặng có sân bay đi và sân bay đến thuộc hai quốc gia khác nhau. Nếu booking có chặng quốc tế: mọi hành khách bắt buộc khai quốc tịch, số hộ chiếu (6–20 ký tự chữ in hoa và số) và ngày hết hạn; hộ chiếu phải còn hạn ít nhất 6 tháng tính từ ngày cất cánh của chặng cuối cùng trong booking. |
| BR-14 | Thông tin liên hệ bắt buộc gồm họ tên, email, số điện thoại (9–15 chữ số, có thể có dấu `+` ở đầu). Mọi email về booking gửi tới email liên hệ. |

### 6.3 Giá

| Mã | Quy tắc |
|---|---|
| BR-20 | Giá đã gồm thuế và phí. Giá người lớn của một chặng là giá bán của gói giá đã chọn trên chuyến đó. Giá người lớn của một chiều là tổng giá người lớn các chặng. |
| BR-21 | Giá vé của một hành khách trên một chặng = giá người lớn của chặng × tỉ lệ theo loại khách, làm tròn half-up đến đồng. Tỉ lệ: `ADULT` 100%, `CHILD` *90%* (`pricing.child_percent`), `INFANT` *10%* (`pricing.infant_percent`). |
| BR-22 | Hành lý mua thêm tính theo từng hành khách trên từng chiều. Mỗi hành khách chọn tối đa một mức (kg) cho mỗi chiều, trong bảng giá hành lý đang kích hoạt của hãng khai thác chiều đó. Chỉ người lớn và trẻ em được mua. |
| BR-23 | Tạm tính = tổng giá vé + tổng tiền hành lý. Tổng thanh toán = tạm tính − mức giảm của voucher. |
| BR-24 | Khi giữ chỗ, hệ thống chốt (snapshot) giá người lớn từng chặng, giá từng vé, giá hành lý và quy định gói giá. Admin thay đổi giá hay gói giá sau đó không ảnh hưởng booking đã tạo. |
| BR-25 | Thay đổi tham số hệ thống chỉ áp dụng cho giao dịch tạo sau thời điểm thay đổi. |

### 6.4 Voucher

| Mã | Quy tắc |
|---|---|
| BR-30 | Voucher dùng được khi thoả cả 5 điều kiện: đang kích hoạt; thời điểm hiện tại nằm trong [thời điểm bắt đầu, thời điểm kết thúc); còn lượt (nếu có giới hạn lượt); tạm tính ≥ giá trị đơn tối thiểu; tài khoản chưa có booking nào dùng voucher này ở trạng thái `PENDING_PAYMENT`, `ISSUED` hoặc `REFUNDED`. |
| BR-31 | Mức giảm: loại `PERCENT` = tạm tính × phần trăm, làm tròn xuống đến đồng, không vượt mức giảm tối đa (nếu có); loại `FIXED` = số tiền cố định. Mức giảm không vượt quá tạm tính. |
| BR-32 | Mỗi booking dùng tối đa một voucher. Lượt dùng bị trừ khi giữ chỗ. Lượt được trả lại khi booking chuyển `EXPIRED` hoặc `CANCELLED`, không được trả lại khi hoàn vé. |
| BR-33 | Mức giảm được chia cho từng chiều theo tỉ lệ tạm tính của chiều, làm tròn xuống. Chiều cuối cùng nhận phần còn lại. |

### 6.5 Giữ chỗ, thanh toán và xuất vé

| Mã | Quy tắc |
|---|---|
| BR-40 | Giữ chỗ trừ ghế ngay. Booking ở trạng thái `PENDING_PAYMENT`, hạn giữ chỗ = thời điểm tạo + *15 phút* (`booking.hold_minutes`). Nếu tổng thanh toán bằng 0 (voucher giảm hết), booking được xuất vé ngay (BR-43) mà không qua thanh toán. |
| BR-41 | Chỉ tạo được lượt thu khi booking đang `PENDING_PAYMENT` và chưa quá hạn giữ chỗ. Link thanh toán hết hạn đúng lúc hết hạn giữ chỗ. Khách được tạo nhiều lượt thu (thử lại) trong thời hạn. |
| BR-42 | Thanh toán thành công khi thoả cả 4 điều kiện: chữ ký VNPay hợp lệ, số tiền khớp, `vnp_ResponseCode` = `00`, `vnp_TransactionStatus` = `00`. Kết quả của mỗi lượt thu chỉ được xử lý một lần. |
| BR-43 | Thanh toán thành công cho booking đang `PENDING_PAYMENT` thì booking chuyển `ISSUED`. Mỗi hành khách trên mỗi chặng nhận một số vé 13 chữ số, gồm mã số vé 3 chữ số của hãng và 10 chữ số tăng dần trên toàn hệ thống. |
| BR-44 | Thanh toán thành công nhưng booking (hoặc yêu cầu đổi chuyến) đã không còn chờ thanh toán: hệ thống tự tạo yêu cầu hoàn 100% số tiền của lượt thu đó, nguồn `INVALID_PAYMENT`. |
| BR-45 | Booking chưa thanh toán bị huỷ khi quá hạn giữ chỗ (chuyển `EXPIRED`) hoặc khi khách chủ động huỷ (chuyển `CANCELLED`). Khi huỷ, hệ thống trả ghế và trả lượt voucher. Với trường hợp quá hạn, hệ thống chờ thêm 5 phút sau hạn giữ chỗ rồi mới huỷ, để kịp nhận kết quả thanh toán đến trễ. |
| BR-46 | Mã đặt chỗ gồm 6 ký tự lấy từ chữ in hoa và chữ số, bỏ các ký tự dễ nhầm `0`, `O`, `1`, `I`. Mã duy nhất trên toàn hệ thống và được cấp ngay khi giữ chỗ. |

### 6.6 Hậu mãi: quy tắc chung

| Mã | Quy tắc |
|---|---|
| BR-50 | Hoàn và đổi thực hiện theo đơn vị chiều, áp dụng cho toàn bộ hành khách của chiều đó. |
| BR-51 | Khách chỉ yêu cầu hoàn hoặc đổi một chiều khi thoả cả 4 điều kiện: booking `ISSUED`; chiều đang `ACTIVE`; chặng đầu của chiều cất cánh sau thời điểm hiện tại ít nhất *24 giờ* (`aftersales.min_hours_before_departure`); chiều không có yêu cầu hoàn đang mở (`PENDING`, `APPROVED`) và không có yêu cầu đổi đang chờ thanh toán. |
| BR-52 | Giá trị chiều = tổng giá vé trên các chặng `ACTIVE` của chiều + tổng tiền hành lý mua thêm của chiều − mức giảm đã phân bổ cho chiều. Nếu kết quả âm thì tính là 0. Phí đổi chuyến đã trả không tính vào giá trị chiều. |
| BR-53 | Phí hoàn và phí đổi = mức phí của gói giá (theo snapshot) × số khách chiếm ghế. Em bé được miễn phí. |

### 6.7 Hoàn vé

| Mã | Quy tắc |
|---|---|
| BR-60 | Khách chỉ gửi được yêu cầu hoàn khi gói giá của chiều cho phép hoàn. |
| BR-61 | Số tiền hoàn = max(0, giá trị chiều − phí hoàn), chốt tại thời điểm gửi yêu cầu. Voucher không được trả lại. |
| BR-62 | Khi Staff duyệt: chiều chuyển `REFUNDED`; các chặng của chiều chuyển `CANCELLED` và trả ghế ngay; sau đó hệ thống hoàn tiền qua VNPay. Số tiền hoàn được chia vào các lượt thu thành công của booking: lượt mới nhất trước, mỗi lượt hoàn tối đa phần chưa hoàn của nó. Hoàn đủ thì yêu cầu chuyển `COMPLETED`. |
| BR-63 | Staff từ chối thì phải ghi lý do. Yêu cầu chuyển `REJECTED`, booking giữ nguyên. Khách có thể gửi yêu cầu mới nếu vẫn thoả BR-51. |
| BR-64 | Nếu cổng thanh toán lỗi khi hoàn, yêu cầu giữ ở `APPROVED` kèm thông báo lỗi. Staff có thể thử lại (chỉ hoàn phần còn thiếu), hoặc xác nhận đã hoàn thủ công (bắt buộc ghi chú). |
| BR-65 | Khi tất cả các chiều của booking đều `REFUNDED` thì booking chuyển `REFUNDED`. |
| BR-66 | Yêu cầu hoàn do hệ thống tạo (nguồn `FLIGHT_CANCELLED`, `INVALID_PAYMENT`) có phí bằng 0, không bị ràng buộc bởi BR-51 và BR-60, và không thể bị từ chối. Với nguồn `INVALID_PAYMENT`: số tiền hoàn là phần chưa hoàn của chính lượt thu đó, và booking không đổi trạng thái. |

### 6.8 Đổi chuyến

| Mã | Quy tắc |
|---|---|
| BR-70 | Khách chỉ đổi được khi gói giá của chiều cho phép đổi. |
| BR-71 | Hành trình mới phải: cùng sân bay đi và sân bay đến với chiều hiện tại; do cùng hãng khai thác; bán cùng gói giá trên mọi chặng; thoả BR-01, BR-02, BR-03, BR-06; giữ đúng thứ tự BR-04 với chiều còn lại nếu chiều đó đang `ACTIVE`. Ngày mới có thể là bất kỳ ngày nào. |
| BR-72 | Số tiền phải trả = phí đổi (BR-53) + max(0, tổng giá vé mới − tổng giá vé hiện tại của chiều). Giá vé mới tính theo BR-21 với giá bán và tham số hiện hành. Nếu chuyến mới rẻ hơn thì phần chênh lệch không được hoàn. |
| BR-73 | Khi khách xác nhận đổi: hệ thống giữ ghế trên các chuyến mới, các chặng mới ở trạng thái `PENDING_CHANGE`, hạn thanh toán = thời điểm xác nhận + `booking.hold_minutes` phút. Nếu số tiền phải trả bằng 0 thì hoàn tất ngay, không cần thanh toán. |
| BR-74 | Thanh toán đổi chuyến thành công: chặng cũ chuyển `REPLACED` và trả ghế; chặng mới chuyển `ACTIVE`; hệ thống cấp số vé mới cho các chặng mới. Hành lý mua thêm và mức giảm đã phân bổ của chiều giữ nguyên. |
| BR-75 | Quá hạn thanh toán đổi chuyến (có 5 phút chờ như BR-45): yêu cầu đổi chuyển `EXPIRED`, các chặng mới chuyển `CANCELLED` và trả ghế. Chiều giữ nguyên như trước khi đổi. |

### 6.9 Chuyến bay

| Mã | Quy tắc |
|---|---|
| BR-80 | Số hiệu chuyến = mã hãng + 1–4 chữ số (VD `VN213`). Không có hai chuyến cùng hãng, cùng số hiệu, cùng giờ cất cánh. |
| BR-81 | Giờ cất cánh và hạ cánh nhập theo giờ địa phương của sân bay tương ứng, lưu dưới dạng thời điểm tuyệt đối. Giờ hạ cánh phải sau giờ cất cánh; sân bay đi khác sân bay đến. Mỗi chuyến có ít nhất một hạng ghế. Mỗi giá bán phải thuộc một gói giá của đúng hãng đó và gói giá phải thuộc một hạng ghế có trên chuyến. |
| BR-82 | Chuyến đã từng có booking thì: không được xoá; không được đổi hãng, số hiệu, sân bay đi, sân bay đến; không được giảm tổng ghế của một hạng xuống dưới số ghế đã bán hoặc đang giữ; không được bỏ hạng ghế đang có ghế bán ra hoặc đang giữ. |
| BR-83 | Đổi giờ chuyến bay thì gửi email cho các booking `ISSUED` có chặng `ACTIVE` trên chuyến. Hệ thống không tự kiểm tra lại thời gian nối chuyến của các booking đó. |
| BR-84 | Huỷ chuyến (không đảo ngược được) kéo theo 4 việc: **(a)** booking `PENDING_PAYMENT` có chặng trên chuyến chuyển `CANCELLED`, trả ghế và trả lượt voucher; **(b)** yêu cầu đổi đang chờ thanh toán có chặng cũ hoặc chặng mới trên chuyến chuyển `CANCELLED`, trả ghế các chặng mới; **(c)** mỗi chiều `ACTIVE` của booking `ISSUED` có chặng trên chuyến được tạo một yêu cầu hoàn nguồn `FLIGHT_CANCELLED`; nếu chiều đó đã có yêu cầu hoàn `PENDING` của khách thì yêu cầu đó được chuyển sang nguồn `FLIGHT_CANCELLED`, phí 0, và tính lại số tiền; **(d)** gửi email cho các booking ở (c). |
| BR-85 | Tạo chuyến hàng loạt: trong khoảng ngày (tối đa 180 ngày), mỗi ngày rơi vào các thứ đã chọn sẽ tạo một chuyến. Ngày đã có chuyến trùng theo BR-80 thì bỏ qua. Giờ hạ cánh = giờ cất cánh + thời lượng bay. |
| BR-86 | Thay đổi giá bán hoặc danh sách gói giá của chuyến chỉ ảnh hưởng booking mới. |
| BR-87 | Chỉ được sửa hoặc huỷ chuyến chưa cất cánh. |

### 6.10 Danh mục

| Mã | Quy tắc |
|---|---|
| BR-90 | Sân bay: mã IATA 3 chữ cái (duy nhất), quốc gia theo mã ISO 3166-1 alpha-2, múi giờ IANA (VD `Asia/Ho_Chi_Minh`). Hãng bay: mã IATA 2 ký tự (duy nhất), mã số vé 3 chữ số (duy nhất). |
| BR-91 | Dữ liệu danh mục đã được tham chiếu thì chỉ ngừng kích hoạt được, không xoá được. Sân bay hoặc hãng ngừng kích hoạt thì không chọn được khi tạo chuyến mới, nhưng chuyến đã có vẫn hoạt động bình thường. Gói giá hoặc mức hành lý ngừng kích hoạt thì không bán nữa. |
| BR-92 | Trong một hãng, tên gói giá không trùng nhau và mức kg hành lý không trùng nhau. |

### 6.11 Tài khoản

| Mã | Quy tắc |
|---|---|
| BR-100 | Email là định danh đăng nhập, duy nhất, không phân biệt hoa thường. Mật khẩu dài ít nhất 8 ký tự, có cả chữ và số. |
| BR-101 | Tự đăng ký luôn tạo tài khoản `CUSTOMER`. Tài khoản `STAFF` và `ADMIN` do Admin tạo. |
| BR-102 | Tài khoản `LOCKED` không đăng nhập được. Phiên đang mở của tài khoản đó mất hiệu lực ngay từ request kế tiếp. |
| BR-103 | Link đặt lại mật khẩu dùng một lần và hết hạn sau 30 phút. |
| BR-104 | Customer chỉ xem và thao tác được trên booking của chính mình. |
| BR-105 | Admin không thể tự khoá tài khoản của chính mình. |

### 6.12 Báo cáo

| Mã | Quy tắc |
|---|---|
| BR-110 | Dòng tiền tính theo thời điểm hoàn tất giao dịch, giờ Việt Nam. Tổng thu = tổng các lượt thu thành công. Tổng hoàn = tổng các lượt hoàn thành công (kể cả hoàn thủ công). Doanh thu ròng = tổng thu − tổng hoàn. |
| BR-111 | Doanh số vé gồm số vé và tổng giá vé của các vé đã xuất trong kỳ và thuộc chặng đang `ACTIVE`. Nhóm theo hãng khai thác, hoặc theo tuyến (sân bay đi – sân bay đến của chặng). |
| BR-112 | Tỉ lệ lấp đầy của một chuyến = (tổng ghế − ghế còn) / tổng ghế, tính cho các chuyến `SCHEDULED` cất cánh trong kỳ. Ghế đang giữ chỗ cũng được tính là đã lấp. |

---

## 7. Tham số hệ thống

Admin đổi được tham số qua FR-111. Giá trị mới áp dụng cho giao dịch tạo sau đó (BR-25).

| Khoá | Mặc định | Khoảng hợp lệ | Ý nghĩa | Dùng ở |
|---|---|---|---|---|
| `booking.hold_minutes` | 15 | 5–60 | Thời gian giữ chỗ, đồng thời là hạn thanh toán đổi chuyến (phút) | BR-40, BR-41, BR-73 |
| `booking.min_hours_before_departure` | 3 | 0–72 | Không bán chuyến cất cánh trong vòng N giờ tới | BR-01 |
| `booking.max_seated_passengers` | 9 | 1–9 | Số khách chiếm ghế tối đa trong một booking | BR-11 |
| `pricing.child_percent` | 90 | 0–100 | Giá vé trẻ em, tính theo % giá người lớn | BR-21 |
| `pricing.infant_percent` | 10 | 0–100 | Giá vé em bé, tính theo % giá người lớn | BR-21 |
| `aftersales.min_hours_before_departure` | 24 | 0–168 | Hạn chót để hoàn hoặc đổi: trước giờ cất cánh N giờ | BR-51 |
| `search.min_connection_minutes` | 60 | 30–600 | Thời gian nối chuyến tối thiểu (phút) | BR-03 |
| `search.max_connection_minutes` | 720 | 60–1440 | Thời gian nối chuyến tối đa (phút). Phải lớn hơn giá trị tối thiểu | BR-03 |

Các giá trị sau là hằng số, không cấu hình được: 5 phút chờ trước khi huỷ booking quá hạn (BR-45), hộ chiếu còn hạn 6 tháng (BR-13), link đặt lại mật khẩu sống 30 phút (BR-103), phiên đăng nhập hết hạn sau 30 phút không hoạt động, tạo chuyến hàng loạt tối đa 180 ngày (BR-85).

---

## 8. Yêu cầu phi chức năng

| Mã | Nhóm | Yêu cầu |
|---|---|---|
| NFR-01 | Hiệu năng | Chạy với dữ liệu mẫu (khoảng 30 sân bay, 1.200 chuyến) trên máy dev: tìm kiếm có p95 dưới 1 giây; các API khác có p95 dưới 500 ms. |
| NFR-02 | Đúng đắn khi đồng thời | Không bán vượt ghế, không xuất vé hai lần, không hoàn vượt số tiền đã thu, kể cả khi nhiều request chạy cùng lúc. Mỗi bất biến có integration test riêng. |
| NFR-03 | Bảo mật | Mật khẩu băm bằng BCrypt. Cookie phiên `HttpOnly`, `SameSite=Lax`, thêm `Secure` khi chạy HTTPS. Có chống CSRF. Backend kiểm tra vai trò ở mọi API và kiểm tra quyền sở hữu booking. Mọi callback VNPay đều được kiểm tra chữ ký. Các bí mật (mật khẩu DB, khoá VNPay, tài khoản SMTP) lấy từ biến môi trường, không commit vào repo. Không ghi mật khẩu, khoá, token vào log. |
| NFR-04 | Toàn vẹn dữ liệu | Các bất biến chính được ràng buộc ngay trong DB (khoá ngoại, `CHECK`, `UNIQUE`, unique có điều kiện). Schema được quản lý bằng migration có phiên bản. |
| NFR-05 | Thời gian | Lưu thời điểm tuyệt đối (UTC). Hiển thị giờ địa phương của sân bay tương ứng. Báo cáo tính theo giờ Việt Nam. |
| NFR-06 | Tiền | Đơn vị VND, lưu số nguyên đồng. Làm tròn theo BR-21, BR-31, BR-33. |
| NFR-07 | Khả năng kiểm thử | Logic nghiệp vụ có unit test. Các luồng chính có integration test chạy trên PostgreSQL thật. CI chạy toàn bộ test ở mỗi pull request. |
| NFR-08 | Vận hành | Toàn hệ thống chạy được bằng một lệnh `docker compose up`. Có health check. Tài liệu API được sinh tự động (OpenAPI). |
| NFR-09 | Ngôn ngữ | Giao diện và email bằng tiếng Việt. |
| NFR-10 | Bảo trì | Backend chia module, ranh giới giữa các module được kiểm tra tự động (xem [TDD §3](TDD.md#3-cấu-trúc-mã-nguồn)). |

---

## 9. Kịch bản demo và tiêu chí nghiệm thu

Các kịch bản dùng dữ liệu mẫu trong [Backend Schema §8](BACKEND_SCHEMA.md#8-dữ-liệu-khởi-tạo).

| # | Kịch bản | Kết quả mong đợi |
|---|---|---|
| D1 | Khách đăng ký. Tìm vé khứ hồi HAN–SGN cho 2 người lớn, 1 trẻ em, 1 em bé. Chọn gói giá, mua 20 kg hành lý cho một người lớn ở chiều đi, áp voucher `WELCOME10`, giữ chỗ, thanh toán bằng thẻ test VNPay. | Booking `ISSUED` với 8 số vé (4 khách × 2 chặng). Email vé xuất hiện trong Mailpit. Tổng tiền khớp với báo giá. |
| D2 | Tìm HAN→PQC, chọn hành trình nối chuyến qua SGN, đặt và thanh toán. | Hành trình 2 chặng cùng hãng, thời gian nối nằm trong 60–720 phút, mỗi khách có 2 số vé. |
| D3 | Tìm SGN→SIN (chuyến quốc tế), đặt vé nhưng không khai hộ chiếu. | Báo lỗi `PASSENGER_RULES_VIOLATED`. Giờ hạ cánh hiển thị theo giờ Singapore (+08:00). |
| D4 | Hai khách cùng đặt khi chuyến chỉ còn 1 ghế. | Một booking thành công, booking kia nhận `SEATS_UNAVAILABLE`. Số ghế còn bằng 0. |
| D5 | Giữ chỗ rồi không thanh toán. | Sau hạn giữ chỗ cộng 5 phút, booking chuyển `EXPIRED`. Ghế và lượt voucher được trả lại. |
| D6 | Khách đổi chiều về sang ngày khác. | Khách trả phí đổi và chênh lệch qua VNPay. Chặng cũ `REPLACED`, chặng mới `ACTIVE`, có số vé mới, email gửi đi. |
| D7 | Khách yêu cầu hoàn chiều đi (gói giá cho phép hoàn), Staff duyệt. | Ghế được trả. VNPay hoàn đúng số tiền. Yêu cầu chuyển `COMPLETED`, email gửi đi. |
| D8 | Admin huỷ một chuyến đang có cả booking đã xuất vé lẫn booking chờ thanh toán. | Booking chờ thanh toán chuyển `CANCELLED`. Booking đã xuất vé được tự tạo yêu cầu hoàn 100%. Email báo huỷ được gửi. |
| D9 | Admin đổi `booking.hold_minutes` từ 15 lên 20. | Booking tạo sau đó có hạn giữ chỗ 20 phút. Booking tạo trước đó không đổi. |
| D10 | Admin xem báo cáo dòng tiền tháng này và báo cáo doanh số theo hãng. | Số liệu khớp với các giao dịch từ D1 đến D8. |
| D11 | Customer gọi API của admin. Customer A mở booking của Customer B. | Lần lượt nhận 403 và 404. |

---

## 10. Giả định và ràng buộc

- Nguồn vé là mô phỏng. Giá và quy định gói giá trong dữ liệu mẫu chỉ để minh hoạ, không phải chính sách thật của các hãng.
- VNPay chạy ở môi trường sandbox. IPN cần một URL công khai, khi dev thì dùng tunnel (xem [TDD §6.3](TDD.md#63-thanh-toán-vnpay)).
- Backend chỉ chạy một instance. Job định kỳ và cache tham số dựa vào giả định này.
- Email gửi qua SMTP. Môi trường dev dùng Mailpit để xem email.
- Phân quyền theo vai trò cố định, không có phân quyền chi tiết theo từng chức năng.

## 11. Giới hạn đã biết

| Giới hạn | Ảnh hưởng |
|---|---|
| Đổi giờ chuyến có thể làm lỡ chặng nối của khách | Hệ thống chỉ gửi email (BR-83). Staff hỗ trợ khách thủ công |
| Hãng huỷ chặng 2 của một chiều nối chuyến khi chặng 1 đã bay | Khách được hoàn 100% giá trị cả chiều (đơn giản hoá) |
| Email gửi lỗi không tự gửi lại | Staff có thể gửi lại email vé (FR-73). Các email khác bị mất |
| Đăng nhập không giới hạn số lần thử sai | Chấp nhận trong phạm vi đồ án |
