# App Flow — Màn hình, luồng và trạng thái

| | |
|---|---|
| Phiên bản | 1.0 |
| Ngày | 2026-10-09 |
| Tài liệu liên quan | [PRD](PRD.md) · [TDD](TDD.md) · [Backend Schema](BACKEND_SCHEMA.md) |

Tài liệu này mô tả **màn hình ở mức chức năng** (mục đích, dữ liệu, thao tác, API được gọi) và **luồng nghiệp vụ** giữa các màn hình. Tài liệu không quy định bố cục hay giao diện, phần đó sẽ thiết kế sau.

---

## 1. Sơ đồ điều hướng

```mermaid
flowchart LR
  subgraph PUB["Công khai"]
    C01["C-01 Trang chủ / tìm chuyến"] --> C02["C-02 Kết quả tìm kiếm"]
    C03["C-03 Đăng nhập"]
    C04["C-04 Đăng ký"]
    C05["C-05 Quên mật khẩu"] -.->|"link trong email"| C06["C-06 Đặt lại mật khẩu"]
  end
  subgraph CUS["Customer"]
    C07["C-07 Đặt vé"]
    C08["C-08 Booking của tôi"]
    C09["C-09 Chi tiết booking"]
    C10["C-10 Hoàn vé"]
    C11["C-11 Đổi chuyến"]
    C12["C-12 Tài khoản"]
  end
  subgraph STF["Staff và Admin"]
    S01["S-01 Tra cứu booking"] --> S02["S-02 Chi tiết booking"]
    S03["S-03 Yêu cầu hoàn"] --> S04["S-04 Chi tiết yêu cầu hoàn"]
    S05["S-05 Danh sách chuyến"] --> S06["S-06 Hành khách theo chuyến"]
  end
  subgraph ADM["Chỉ Admin"]
    A01["A-01 Sân bay"]
    A02["A-02 Hãng bay"] --> A03["A-03 Chi tiết hãng"]
    A04["A-04 Chuyến bay"] --> A05["A-05 Tạo chuyến"]
    A04 --> A06["A-06 Chi tiết chuyến"]
    A07["A-07 Voucher"]
    A08["A-08 Tài khoản người dùng"]
    A09["A-09 Tham số hệ thống"]
    A10["A-10 Báo cáo"]
  end
  C02 -->|"chọn gói giá"| C07
  C07 -->|"giữ chỗ"| C09
  C08 --> C09
  C09 --> C10
  C09 --> C11
  C09 -->|"thanh toán"| VNP["VNPay"]
  C11 -->|"thanh toán đổi chuyến"| VNP
  VNP -->|"quay về"| C09
  A06 --> S06
```

- C-12 (Tài khoản) dùng được cho mọi vai trò đã đăng nhập.
- Sau khi đăng nhập, mỗi vai trò có trang mặc định riêng: Customer vào `/bookings`, Staff vào `/staff/bookings`, Admin vào `/admin/flights`. Nếu URL có tham số `next` thì quay về `next`.

---

## 2. Danh sách màn hình

Cột "API" ghi đường dẫn bỏ tiền tố `/api`.

### 2.1 Công khai và Customer

| ID | Màn hình | Route | Quyền | Nội dung | Thao tác → API |
|---|---|---|---|---|---|
| C-01 | Trang chủ / tìm chuyến | `/` | Công khai | Form tìm: sân bay đi và đến (có gợi ý), ngày đi, ngày về (tuỳ chọn), số người lớn, trẻ em, em bé, hạng ghế | Gõ tên sân bay → `GET /airports?q=`. Bấm Tìm → mở C-02 |
| C-02 | Kết quả tìm kiếm | `/flights?from=&to=&date=&returnDate=&adults=&children=&infants=&cabin=` | Công khai | Danh sách hành trình theo FR-11, có lọc và sắp xếp ở client. Khứ hồi đi theo 2 bước: chọn chiều đi, sau đó chọn chiều về (luôn hiện tóm tắt chiều đi đã chọn) | Tải kết quả → `GET /flights/search`. Chọn gói giá → lưu lựa chọn vào `sessionStorage` rồi mở C-07 (chưa đăng nhập thì qua C-03 trước, đăng nhập xong quay lại C-07) |
| C-03 | Đăng nhập | `/login?next=` | Công khai | Email, mật khẩu | `POST /auth/login`, sau đó `GET /auth/csrf`, rồi chuyển về `next` hoặc trang mặc định của vai trò |
| C-04 | Đăng ký | `/register` | Công khai | Email, mật khẩu, họ tên, số điện thoại | `POST /auth/register`, sau đó xử lý như đăng nhập |
| C-05 | Quên mật khẩu | `/forgot-password` | Công khai | Email | `POST /auth/forgot-password`, rồi luôn hiện thông báo "đã gửi email nếu tài khoản tồn tại" |
| C-06 | Đặt lại mật khẩu | `/reset-password?token=` | Công khai | Mật khẩu mới, nhập lại mật khẩu mới | `POST /auth/reset-password`, xong thì mở C-03 |
| C-07 | Đặt vé | `/checkout` | Customer | Xem mục [C-07 chi tiết](#c-07-đặt-vé) bên dưới | Mỗi lần dữ liệu thay đổi → `POST /bookings/quote`. Tải bảng giá hành lý → `GET /airlines/{code}/baggage-options`. Bấm "Giữ chỗ" → `POST /bookings`, thành công thì mở C-09 |
| C-08 | Booking của tôi | `/bookings?status=` | Customer | Danh sách: mã đặt chỗ, tuyến, ngày bay, trạng thái, tổng tiền | `GET /bookings` |
| C-09 | Chi tiết booking | `/bookings/[code]?payment=` | Chủ booking | Toàn bộ thông tin theo FR-41. Đếm ngược hạn giữ chỗ nếu đang chờ thanh toán. Thông báo kết quả thanh toán nếu URL có `payment=` | Xem mục [C-09 chi tiết](#c-09-chi-tiết-booking) bên dưới |
| C-10 | Hoàn vé | `/bookings/[code]/refund/[direction]` | Chủ booking | Giá trị chiều, phí hoàn, số tiền được hoàn, ô nhập lý do | Mở trang → `GET /bookings/{code}/journeys/{direction}/refund-quote`. Bấm Gửi → `POST .../refund-requests`, xong quay về C-09 |
| C-11 | Đổi chuyến | `/bookings/[code]/reschedule/[direction]` | Chủ booking | Chọn ngày mới, xem danh sách hành trình thay thế kèm số tiền phải trả, xem báo giá, xác nhận | `GET .../reschedule-options?date=` → `POST .../reschedule-quote` → `POST .../reschedules`. Nếu phải trả tiền → `POST /reschedules/{id}/payments` rồi sang VNPay, xong quay về C-09 |
| C-12 | Tài khoản | `/account` | Đã đăng nhập | Hồ sơ, đổi mật khẩu, đăng xuất | `GET /me`, `PUT /me`, `PUT /me/password`. Đăng xuất → `POST /auth/logout` |

#### C-07 Đặt vé

Nội dung:
- Tóm tắt hành trình đã chọn.
- Form hành khách, đúng theo số lượng đã nhập lúc tìm kiếm (BR-10 đến BR-13). Các trường hộ chiếu chỉ hiện khi hành trình có chặng quốc tế.
- Thông tin liên hệ, điền sẵn từ tài khoản.
- Chọn hành lý mua thêm cho từng hành khách ở từng chiều.
- Ô nhập voucher.
- Bảng giá.

Khi bấm "Giữ chỗ":
- Gặp `SEATS_UNAVAILABLE` hoặc `FLIGHT_NOT_BOOKABLE` thì quay lại C-02 kèm thông báo.
- Gặp lỗi dữ liệu thì ở lại C-07 và đánh dấu từng trường bị lỗi.

#### C-09 Chi tiết booking

| Thao tác | Điều kiện hiện | API |
|---|---|---|
| Thanh toán | Booking `PENDING_PAYMENT` và chưa quá hạn | `POST /bookings/{code}/payments`, rồi chuyển sang VNPay |
| Huỷ giữ chỗ | Booking `PENDING_PAYMENT` | `POST /bookings/{code}/cancel` |
| Thanh toán đổi chuyến | Đang có yêu cầu đổi chờ thanh toán | `POST /reschedules/{id}/payments` |
| Hoàn chiều này | Chiều thoả BR-51 và BR-60 | Mở C-10 |
| Đổi chiều này | Chiều thoả BR-51 và BR-70 | Mở C-11 |

### 2.2 Staff (Admin cũng dùng được)

| ID | Màn hình | Route | Nội dung | Thao tác → API |
|---|---|---|---|---|
| S-01 | Tra cứu booking | `/staff/bookings?q=&status=` | Ô tìm theo mã đặt chỗ, email hoặc số điện thoại; lọc theo trạng thái; bảng kết quả | `GET /staff/bookings` |
| S-02 | Chi tiết booking | `/staff/bookings/[code]` | Như C-09, thêm tài khoản đã đặt và toàn bộ lịch sử thu, hoàn, đổi | `GET /staff/bookings/{code}`. Gửi lại email vé → `POST /staff/bookings/{code}/resend-ticket-email` |
| S-03 | Yêu cầu hoàn | `/staff/refunds?status=&source=` | Hàng đợi, cũ nhất trước: mã booking, chiều, nguồn, số tiền, trạng thái, thời điểm gửi | `GET /staff/refund-requests` |
| S-04 | Chi tiết yêu cầu hoàn | `/staff/refunds/[id]` | Thông tin yêu cầu, tóm tắt booking, các lượt hoàn đã chạy, lỗi gần nhất | Xem bảng [S-04 thao tác](#s-04-thao-tác) bên dưới |
| S-05 | Danh sách chuyến | `/staff/flights?from=&to=&date=&airline=&status=` | Lọc theo tuyến, ngày, hãng, trạng thái. Hiện tổng ghế và ghế còn | `GET /staff/flights` |
| S-06 | Hành khách theo chuyến | `/staff/flights/[id]/passengers` | Manifest theo FR-72 | `GET /staff/flights/{id}/passengers` |

#### S-04 thao tác

| Thao tác | Điều kiện hiện | API |
|---|---|---|
| Duyệt | Yêu cầu `PENDING` | `POST /staff/refund-requests/{id}/approve` |
| Từ chối (bắt buộc nhập lý do) | Yêu cầu `PENDING` và nguồn `CUSTOMER` | `POST /staff/refund-requests/{id}/reject` |
| Thử lại | Yêu cầu `APPROVED` | `POST /staff/refund-requests/{id}/retry` |
| Xác nhận hoàn thủ công (bắt buộc ghi chú) | Yêu cầu `APPROVED` | `POST /staff/refund-requests/{id}/mark-manual` |

### 2.3 Admin

| ID | Màn hình | Route | Nội dung | Thao tác → API |
|---|---|---|---|---|
| A-01 | Sân bay | `/admin/airports` | Bảng sân bay, kể cả sân bay ngừng kích hoạt. Form thêm và sửa | `GET`, `POST /admin/airports`; `PUT`, `DELETE /admin/airports/{code}` |
| A-02 | Hãng bay | `/admin/airlines` | Bảng hãng bay. Form thêm và sửa, gồm cả mã số vé | `GET`, `POST /admin/airlines`; `PUT`, `DELETE /admin/airlines/{code}` |
| A-03 | Chi tiết hãng | `/admin/airlines/[code]` | Tab "Gói giá" và tab "Bảng giá hành lý" | `.../fare-families`, `.../baggage-options` (TDD §8.7) |
| A-04 | Chuyến bay | `/admin/flights` | Danh sách chuyến (dùng chung API với S-05), nút "Tạo chuyến" | `GET /staff/flights` |
| A-05 | Tạo chuyến | `/admin/flights/new` | Form theo FR-90. Khoảng ngày chỉ gồm 1 ngày nghĩa là tạo chuyến lẻ. Sau khi chọn hãng, hiện các gói giá của hãng đó để nhập giá bán | `POST /admin/flights`, rồi hiện số chuyến được tạo và số ngày bị bỏ qua |
| A-06 | Chi tiết chuyến | `/admin/flights/[id]` | Thông tin chuyến, ghế theo hạng (tổng và còn), giá theo gói, số booking. Có nút sửa, huỷ, xoá và link sang S-06 | Xem bảng [A-06 thao tác](#a-06-thao-tác) bên dưới |
| A-07 | Voucher | `/admin/vouchers` | Bảng voucher (gồm cả số lượt đã dùng), form tạo và sửa | `GET`, `POST /admin/vouchers`; `PUT /admin/vouchers/{id}` |
| A-08 | Tài khoản người dùng | `/admin/users?q=&role=` | Tìm tài khoản, tạo tài khoản Staff hoặc Admin, khoá và mở khoá | `GET`, `POST /admin/users`; `PATCH /admin/users/{id}/status` |
| A-09 | Tham số hệ thống | `/admin/settings` | 8 tham số kèm khoảng hợp lệ, người sửa gần nhất và thời điểm sửa | `GET /admin/settings`; `PUT /admin/settings/{key}` |
| A-10 | Báo cáo | `/admin/reports` | 3 tab: dòng tiền, doanh số vé, tỉ lệ lấp đầy. Mỗi tab có chọn khoảng ngày và cách nhóm | `GET /admin/reports/cash-flow`, `/sales`, `/load-factor` |

#### A-06 thao tác

| Thao tác | Điều kiện | API |
|---|---|---|
| Sửa | Theo BR-82 và BR-87 | `PUT /admin/flights/{id}` |
| Huỷ chuyến | Admin xác nhận hai bước. Chuyến chưa cất cánh (BR-87) | `POST /admin/flights/{id}/cancel` |
| Xoá | Chuyến chưa từng có booking | `DELETE /admin/flights/{id}` |

---

## 3. Luồng người dùng

### UF-01 Tìm và đặt vé

```mermaid
flowchart TD
  A["C-01 Nhập điều kiện tìm"] --> B["C-02 Kết quả chiều đi"]
  B -->|"chọn hành trình và gói giá"| C{"Khứ hồi?"}
  C -->|"có"| D["C-02 Kết quả chiều về"]
  D -->|"chọn hành trình và gói giá"| E{"Đã đăng nhập?"}
  C -->|"không"| E
  E -->|"chưa"| F["C-03 Đăng nhập hoặc C-04 Đăng ký"]
  F --> G
  E -->|"rồi"| G["C-07 Hành khách, liên hệ, hành lý, voucher"]
  G -->|"dữ liệu thay đổi: gọi quote"| G
  G -->|"bấm Giữ chỗ"| H{"Kết quả"}
  H -->|"SEATS_UNAVAILABLE hoặc FLIGHT_NOT_BOOKABLE"| B
  H -->|"lỗi dữ liệu hoặc voucher"| G
  H -->|"thành công"| I["C-09 Booking PENDING_PAYMENT, đếm ngược"]
  I -->|"bấm Thanh toán"| J["VNPay"]
  J -->|"thành công"| K["C-09 Booking ISSUED, email vé"]
  J -->|"thất bại hoặc khách huỷ"| I
  I -->|"bấm Huỷ giữ chỗ"| L["Booking CANCELLED"]
  I -->|"hết hạn giữ chỗ"| M["Booking EXPIRED"]
```

Các tình huống khác:
- **Khách đóng trình duyệt khi đang ở trang VNPay.** Booking vẫn chờ thanh toán. Khách vào C-08, mở C-09 và thanh toán tiếp, miễn là còn trong thời hạn giữ chỗ.
- **Khách đã trả tiền nhưng không quay về trang của hệ thống.** IPN vẫn cập nhật booking sang `ISSUED` và email vé vẫn được gửi.
- **Khách trả tiền khi booking vừa hết hạn.** Hệ thống tự tạo yêu cầu hoàn 100% (BR-44). Khách thấy yêu cầu hoàn này trong C-09.

### UF-02 Hoàn vé

```mermaid
flowchart TD
  A["C-09 Chi tiết booking"] -->|"Hoàn chiều này"| B["C-10 Xem số tiền hoàn"]
  B -->|"không thoả điều kiện"| X["Báo lỗi: gói không hoàn, quá hạn, đang có yêu cầu"]
  B -->|"nhập lý do, gửi"| C["Yêu cầu PENDING"]
  C --> D["S-03 Hàng đợi của Staff"]
  D --> E["S-04 Xem yêu cầu"]
  E -->|"Từ chối, nhập lý do"| F["REJECTED, email cho khách"]
  E -->|"Duyệt"| G["APPROVED: chiều REFUNDED, trả ghế"]
  G --> H{"VNPay Refund API"}
  H -->|"thành công"| I["COMPLETED, email cho khách"]
  H -->|"lỗi"| J["APPROVED, ghi lỗi"]
  J -->|"Thử lại"| H
  J -->|"Hoàn thủ công, ghi chú"| I
```

### UF-03 Đổi chuyến

```mermaid
flowchart TD
  A["C-09 Chi tiết booking"] -->|"Đổi chiều này"| B["C-11 Chọn ngày mới"]
  B --> C["Danh sách hành trình thay thế kèm số tiền phải trả"]
  C -->|"chọn một hành trình"| D["Báo giá: phí đổi + chênh lệch"]
  D -->|"Xác nhận"| E{"Số tiền phải trả"}
  E -->|"bằng 0"| F["Hoàn tất: chặng mới, số vé mới, email"]
  E -->|"lớn hơn 0"| G["Yêu cầu đổi PENDING_PAYMENT, giữ ghế chuyến mới"]
  G -->|"Thanh toán"| H["VNPay"]
  H -->|"thành công"| F
  H -->|"thất bại"| G
  G -->|"quá hạn"| I["EXPIRED: trả ghế chuyến mới, chiều cũ giữ nguyên"]
```

### UF-04 Admin chuẩn bị dữ liệu và mở bán

```mermaid
flowchart LR
  A["A-01 Sân bay"] --> B["A-02 Hãng bay"]
  B --> C["A-03 Gói giá của hãng"]
  C --> D["A-03 Bảng giá hành lý"]
  D --> E["A-05 Tạo chuyến lẻ hoặc hàng loạt"]
  E --> F["Chuyến xuất hiện trong kết quả tìm kiếm"]
  G["A-07 Voucher"] -.->|"không bắt buộc"| F
```

Phải làm theo đúng thứ tự trên vì dữ liệu sau tham chiếu dữ liệu trước:
- Chuyến bay cần sân bay và hãng.
- Giá bán của chuyến cần gói giá của hãng.
- Hành lý mua thêm cần bảng giá hành lý của hãng.

### UF-05 Sự cố chuyến bay

```mermaid
flowchart TD
  A["A-06 Chi tiết chuyến"] -->|"Sửa giờ"| B["Lưu giờ mới"]
  B --> C["Email cho khách có vé"]
  A -->|"Huỷ chuyến, xác nhận 2 bước"| D["Chuyến CANCELLED"]
  D --> E["Booking chờ thanh toán: CANCELLED, trả ghế và voucher"]
  D --> F["Yêu cầu đổi đang dở: CANCELLED, trả ghế chuyến mới"]
  D --> G["Mỗi chiều đã xuất vé: yêu cầu hoàn 100% FLIGHT_CANCELLED"]
  G --> H["Email cho khách"]
  G --> I["S-03 Staff xử lý như UF-02"]
```

### UF-06 Quên mật khẩu

1. Ở C-05, người dùng nhập email. Hệ thống luôn báo "đã gửi email nếu tài khoản tồn tại".
2. Nếu email tồn tại, hệ thống gửi link `/reset-password?token=...`, sống 30 phút và chỉ dùng được một lần.
3. Ở C-06, người dùng nhập mật khẩu mới. Thành công thì mở C-03. Token không hợp lệ thì báo `INVALID_TOKEN` và gợi ý làm lại từ bước 1.

### UF-07 Staff hỗ trợ khách

1. Ở S-01, Staff tìm booking theo mã đặt chỗ, email hoặc số điện thoại mà khách cung cấp.
2. Ở S-02, Staff xem trạng thái, vé, các lượt thu và hoàn, các yêu cầu hoàn và đổi.
3. Nếu khách không nhận được vé, Staff bấm "Gửi lại email vé".
4. Nếu chuyến bị đổi giờ hoặc huỷ, Staff mở S-06 của chuyến đó để có danh sách khách cần liên hệ.

---

## 4. Vòng đời trạng thái

### 4.1 Booking

```mermaid
stateDiagram-v2
  [*] --> PENDING_PAYMENT: giữ chỗ
  [*] --> ISSUED: giữ chỗ với tổng tiền bằng 0
  PENDING_PAYMENT --> ISSUED: thanh toán thành công
  PENDING_PAYMENT --> EXPIRED: quá hạn giữ chỗ thêm 5 phút
  PENDING_PAYMENT --> CANCELLED: khách huỷ hoặc chuyến bị huỷ
  ISSUED --> REFUNDED: mọi chiều đều REFUNDED
  EXPIRED --> [*]
  CANCELLED --> [*]
  REFUNDED --> [*]
```

Booking khứ hồi mới hoàn một chiều thì vẫn ở `ISSUED`. Trạng thái "đã bay" không được lưu, mà suy ra từ giờ cất cánh.

### 4.2 Chiều (journey)

```mermaid
stateDiagram-v2
  [*] --> ACTIVE: giữ chỗ
  ACTIVE --> REFUNDED: Staff duyệt yêu cầu hoàn
  REFUNDED --> [*]
```

Đổi chuyến không đổi trạng thái của chiều, chỉ thay các chặng bên trong.

### 4.3 Chặng (segment) và số ghế

```mermaid
stateDiagram-v2
  [*] --> ACTIVE: giữ chỗ, trừ ghế
  [*] --> PENDING_CHANGE: xác nhận đổi chuyến, trừ ghế chuyến mới
  PENDING_CHANGE --> ACTIVE: thanh toán đổi chuyến thành công
  PENDING_CHANGE --> CANCELLED: đổi chuyến quá hạn hoặc bị huỷ, trả ghế
  ACTIVE --> REPLACED: đổi chuyến hoàn tất, trả ghế
  ACTIVE --> CANCELLED: booking hết hạn hoặc bị huỷ, hoặc chiều được duyệt hoàn, trả ghế
  REPLACED --> [*]
  CANCELLED --> [*]
```

**Bất biến về ghế:** chặng ở `ACTIVE` hoặc `PENDING_CHANGE` đang giữ (số người lớn + số trẻ em) ghế trong hạng ghế của chiều. Mỗi khi chặng rời hai trạng thái này, số ghế đó phải được cộng trả lại đúng một lần.

### 4.4 Lượt thu (payment, `kind = CHARGE`)

```mermaid
stateDiagram-v2
  [*] --> PENDING: tạo lượt thu
  PENDING --> SUCCESS: callback thành công
  PENDING --> FAILED: callback thất bại
  SUCCESS --> [*]
  FAILED --> [*]
```

- Lượt thu ở `PENDING` mà không bao giờ có callback (khách bỏ ngang) thì cứ giữ nguyên, không gây hại gì: link VNPay đã hết hạn, và báo cáo chỉ tính các lượt `SUCCESS`.
- Lượt hoàn (`kind = REFUND`) được ghi thẳng ở trạng thái `SUCCESS` hoặc `FAILED` sau mỗi lần gọi Refund API.

### 4.5 Yêu cầu hoàn

```mermaid
stateDiagram-v2
  [*] --> PENDING: khách gửi hoặc hệ thống tạo
  PENDING --> APPROVED: Staff duyệt
  PENDING --> REJECTED: Staff từ chối, chỉ với nguồn CUSTOMER
  APPROVED --> APPROVED: Refund API lỗi, ghi last_error
  APPROVED --> COMPLETED: hoàn đủ qua VNPay hoặc thủ công
  REJECTED --> [*]
  COMPLETED --> [*]
```

| Nguồn | Ai tạo | Phí | Chiều liên quan |
|---|---|---|---|
| `CUSTOMER` | Khách (FR-51) | Theo gói giá | Có |
| `FLIGHT_CANCELLED` | Hệ thống khi huỷ chuyến (BR-84) | 0 | Có |
| `INVALID_PAYMENT` | Hệ thống khi thanh toán trễ hoặc trùng (BR-44) | 0 | Không. Gắn với lượt thu |

### 4.6 Yêu cầu đổi chuyến

```mermaid
stateDiagram-v2
  [*] --> PENDING_PAYMENT: xác nhận, số tiền phải trả lớn hơn 0
  [*] --> COMPLETED: xác nhận, số tiền phải trả bằng 0
  PENDING_PAYMENT --> COMPLETED: thanh toán thành công
  PENDING_PAYMENT --> EXPIRED: quá hạn thêm 5 phút
  PENDING_PAYMENT --> CANCELLED: chuyến liên quan bị huỷ
  COMPLETED --> [*]
  EXPIRED --> [*]
  CANCELLED --> [*]
```

### 4.7 Chuyến bay và tài khoản

```mermaid
stateDiagram-v2
  [*] --> SCHEDULED: Admin tạo
  SCHEDULED --> CANCELLED: Admin huỷ, trước giờ cất cánh
  CANCELLED --> [*]
```

```mermaid
stateDiagram-v2
  [*] --> ACTIVE: đăng ký hoặc Admin tạo
  ACTIVE --> LOCKED: Admin khoá
  LOCKED --> ACTIVE: Admin mở khoá
```

---

## 5. Sequence diagram

IPN và Return URL đều đi vào domain công khai của Next.js rồi được rewrite sang backend. Để sơ đồ gọn, các bước rewrite được lược bỏ.

### SD-01 Giữ chỗ và thanh toán

```mermaid
sequenceDiagram
  autonumber
  actor KH as Khách
  participant FE as Next.js
  participant BE as Backend
  participant DB as PostgreSQL
  participant VNP as VNPay
  participant MAIL as SMTP
  KH->>FE: Bấm Giữ chỗ
  FE->>BE: POST /api/bookings
  BE->>DB: Khoá chia sẻ chuyến, trừ ghế, giữ lượt voucher, ghi booking
  BE-->>FE: 201 kèm mã đặt chỗ và hạn giữ chỗ
  KH->>FE: Bấm Thanh toán
  FE->>BE: POST /api/bookings/K7Q2XM/payments
  BE->>DB: Ghi payment PENDING
  BE-->>FE: paymentUrl
  FE->>VNP: Chuyển hướng tới trang thanh toán
  KH->>VNP: Nhập thẻ và OTP
  par IPN
    VNP->>BE: GET /api/payments/vnpay/ipn
    BE->>DB: Kiểm chữ ký, khoá payment, SUCCESS, xuất vé
    BE-->>VNP: RspCode 00
  and Return URL
    VNP->>FE: Chuyển hướng trình duyệt về Return URL
    FE->>BE: GET /api/payments/vnpay/return
    BE->>DB: Kiểm chữ ký, payment đã xử lý thì bỏ qua
    BE-->>FE: 302 về /bookings/K7Q2XM?payment=success
  end
  BE--)MAIL: Email vé điện tử, gửi sau commit
```

### SD-02 Hết hạn giữ chỗ

```mermaid
sequenceDiagram
  participant JOB as Job mỗi phút
  participant BK as Booking service
  participant DB as PostgreSQL
  JOB->>DB: Lấy booking PENDING_PAYMENT quá hạn hơn 5 phút
  loop Mỗi booking trong một transaction riêng
    JOB->>BK: expire bookingId
    BK->>DB: SELECT FOR UPDATE, kiểm tra vẫn PENDING_PAYMENT
    BK->>DB: Chặng ACTIVE thành CANCELLED, cộng trả ghế
    BK->>DB: Trả lượt voucher, booking thành EXPIRED
  end
  Note over JOB,DB: Yêu cầu đổi chuyến quá hạn được xử lý tương tự
```

### SD-03 Hoàn vé

```mermaid
sequenceDiagram
  autonumber
  actor KH as Khách
  actor NV as Staff
  participant BE as Backend
  participant DB as PostgreSQL
  participant VNP as VNPay
  KH->>BE: GET refund-quote
  BE-->>KH: Giá trị chiều, phí, số tiền hoàn
  KH->>BE: POST refund-requests kèm lý do
  BE->>DB: Ghi yêu cầu PENDING
  NV->>BE: POST approve
  BE->>DB: Transaction 1, chiều REFUNDED, chặng CANCELLED, trả ghế, yêu cầu APPROVED
  BE->>DB: Transaction 2, khoá yêu cầu, lấy các lượt thu còn hoàn được
  loop Mỗi lượt thu, mới nhất trước
    BE->>VNP: Refund API
    VNP-->>BE: vnp_ResponseCode
    BE->>DB: Ghi dòng REFUND
  end
  alt Hoàn đủ
    BE->>DB: Yêu cầu COMPLETED
    BE--)KH: Email đã hoàn tiền
  else Có lỗi
    BE->>DB: Giữ APPROVED, ghi last_error
    BE-->>NV: PAYMENT_GATEWAY_ERROR
  end
```

### SD-04 Đổi chuyến

```mermaid
sequenceDiagram
  autonumber
  actor KH as Khách
  participant BE as Backend
  participant DB as PostgreSQL
  participant VNP as VNPay
  KH->>BE: GET reschedule-options theo ngày mới
  BE-->>KH: Hành trình thay thế kèm amountDue
  KH->>BE: POST reschedules kèm flightIds
  BE->>DB: Khoá chia sẻ chuyến mới, khoá booking, trừ ghế chuyến mới
  BE->>DB: Ghi chặng PENDING_CHANGE, vé mới, reschedule PENDING_PAYMENT
  BE-->>KH: rescheduleId và amountDue
  KH->>BE: POST /api/reschedules/ID/payments
  BE-->>KH: paymentUrl
  KH->>VNP: Thanh toán
  VNP->>BE: IPN thành công
  BE->>DB: Chặng cũ REPLACED và trả ghế, chặng mới ACTIVE và cấp số vé, reschedule COMPLETED
  BE--)KH: Email vé mới
```

### SD-05 Huỷ chuyến bay

```mermaid
sequenceDiagram
  autonumber
  actor AD as Admin
  participant FL as flight
  participant BK as booking
  participant AS as aftersales
  participant DB as PostgreSQL
  participant NT as notification
  AD->>FL: POST /api/admin/flights/ID/cancel
  FL->>DB: Khoá chuyến FOR UPDATE, chuyển CANCELLED
  FL->>BK: FlightCancelled, đồng bộ, thứ tự 1
  BK->>DB: Khoá mọi booking bị ảnh hưởng theo id tăng dần
  BK->>DB: Booking PENDING_PAYMENT thành CANCELLED, trả ghế, trả voucher
  FL->>AS: FlightCancelled, đồng bộ, thứ tự 2
  AS->>DB: Huỷ yêu cầu đổi đang dở, trả ghế chuyến mới
  AS->>DB: Tạo hoặc chuyển yêu cầu hoàn sang FLIGHT_CANCELLED, phí 0
  FL-->>AD: 200
  FL--)NT: FlightCancelled, sau commit
  NT->>NT: Gửi email cho các booking ISSUED bị ảnh hưởng
```
