# Kịch bản kiểm thử End-to-End

|                    |                                                                               |
| ------------------ | ----------------------------------------------------------------------------- |
| Phiên bản          | 1.0                                                                           |
| Ngày               | 2026-10-24                                                                    |
| Tài liệu liên quan | [PRD](PRD.md) · [App Flow](APP_FLOW.md) · [Backend Schema](BACKEND_SCHEMA.md) |

Tài liệu này liệt kê các kịch bản E2E chạy qua giao diện thật (trình duyệt → Next.js → Spring Boot → PostgreSQL, VNPay sandbox, Mailpit). Mỗi kịch bản có mã `E2E-xx`, tham chiếu tới `FR`/`BR`/`D#` trong PRD và cột **Tự động** cho biết đã có spec Playwright trong `frontend/e2e/` hay chưa. Cột này được lập từ tên các test hiện có, nên cần đối chiếu lại với mã spec khi bổ sung.

---

## 1. Môi trường và dữ liệu

| Hạng mục    | Cách chuẩn bị                                                                                                                                                         |
| ----------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Hạ tầng     | `docker compose up -d postgres mailpit`, backend profile `dev` (cổng 8080), frontend `npm run dev` (cổng 3000)                                                        |
| Dữ liệu mẫu | Seed `V100`–`V106` (profile `dev`). Chuyến bay chỉ sinh cho 60 ngày kể từ lúc seed. Nếu seed quá ~58 ngày thì `docker compose down -v` rồi chạy lại                   |
| Tài khoản   | `admin@demo.local`, `staff@demo.local`, `customer@demo.local`, mật khẩu `Demo@1234`. Kịch bản đặt vé nên đăng ký tài khoản mới để voucher `WELCOME10` chưa bị dùng    |
| Email       | Mailpit http://localhost:8025                                                                                                                                         |
| Thanh toán  | VNPay sandbox, khoá `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` trong `.env`. Thẻ test NCB: `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456` |
| Trình duyệt | Chromium desktop (1280×800) là chính, thêm một lượt 390×844 (điện thoại) cho C-01, C-02, C-07, C-09                                                                   |

**Lưu ý khi chạy tự động:** dev server có thể sập worker sau nhiều lần HMR, nên chạy E2E trên `next start -p 3100`. Các test cần chữ ký VNPay phải chạy trên cổng 3000.

### Số liệu dùng để tính kết quả mong đợi

Chuyến `VN213` HAN→SGN, giá gốc 1.290.000, làm tròn 10.000 sau khi nhân hệ số gói:

| Gói VN          |                            Giá người lớn |  Hoàn: phí | Đổi: phí |
| --------------- | ---------------------------------------: | ---------: | -------: |
| Economy Lite    |                                1.290.000 | không hoàn |  600.000 |
| Economy Classic | 1.870.000 (1.290.000 × 1,45 = 1.870.500) |    600.000 |  300.000 |
| Economy Flex    | 2.450.000 (1.290.000 × 1,90 = 2.451.000) |    300.000 |        0 |

**Ví dụ chuẩn "Q1"** (dùng nhiều lần bên dưới): một chiều VN213, Economy Classic, 2 người lớn + 1 trẻ em + 1 em bé, mua thêm 20 kg (350.000) cho một người lớn.

| Dòng                | Công thức                   |       Số tiền |
| ------------------- | --------------------------- | ------------: |
| Người lớn ×2        | 2 × 1.870.000               |     3.740.000 |
| Trẻ em              | 1.870.000 × 90%             |     1.683.000 |
| Em bé               | 1.870.000 × 10%             |       187.000 |
| Hành lý 20 kg       |                             |       350.000 |
| Tạm tính            |                             |     5.960.000 |
| `WELCOME10`         | 10% = 596.000, trần 200.000 |      −200.000 |
| **Tổng thanh toán** |                             | **5.760.000** |

---

## 2. Quy ước

- **Vai trò:** KH = Customer, NV = Staff, QT = Admin, Khách = chưa đăng nhập.
- **Ưu tiên:** P0 bắt buộc pass trước khi demo, P1 nên pass, P2 chạy khi còn thời gian.
- **Mọi kịch bản** kiểm thêm hai điều ngầm: không có lỗi đỏ trong console trình duyệt, và mọi thông báo lỗi hiển thị bằng tiếng Việt.
- **Tự động:** ✅ đã có spec, 🟡 có spec nhưng chưa phủ hết ý chính, ❌ chưa có.

---

## 3. Nhóm A: Tài khoản và phân quyền

### E2E-A01 Đăng ký, hồ sơ, đổi mật khẩu, đăng xuất · P0 · FR-01, FR-02, FR-04 · ✅ `account.spec.ts`

1. Khách vào `/register`, nhập email mới, mật khẩu `Abc12345`, họ tên, số điện thoại → bấm Đăng ký.
2. Vào `/account`, sửa họ tên và số điện thoại, lưu. Thử sửa email.
3. Đổi mật khẩu: nhập sai mật khẩu hiện tại, rồi nhập đúng.
4. Đăng xuất, đăng nhập lại bằng mật khẩu mới.

**Mong đợi:** (1) vào thẳng `/bookings` đã đăng nhập; (2) họ tên, số điện thoại đổi, email không sửa được; (3) sai mật khẩu hiện tại báo lỗi, đúng thì thành công; (4) đăng nhập lại được, mật khẩu cũ không còn dùng được.

### E2E-A02 Đăng ký và đăng nhập sai · P0 · FR-01, FR-02, BR-100 · 🟡

1. Đăng ký bằng email đã tồn tại (khác hoa thường: `Customer@Demo.local`).
2. Đăng ký với mật khẩu `abcdefgh` (không có số) và `Ab1` (quá ngắn).
3. Đăng nhập với email đúng, mật khẩu sai. Rồi đăng nhập với email không tồn tại.

**Mong đợi:** (1) `EMAIL_ALREADY_USED`; (2) báo lỗi mật khẩu, không tạo tài khoản; (3) hai trường hợp cho cùng một thông báo `INVALID_CREDENTIALS`, không lộ email có tồn tại hay không.

### E2E-A03 Quên và đặt lại mật khẩu · P1 · FR-03, BR-103, UF-06 · ❌

1. Ở `/forgot-password` nhập `customer@demo.local`, rồi nhập một email không tồn tại.
2. Mở Mailpit, lấy link `/reset-password?token=...` từ email.
3. Đặt mật khẩu mới `Newpass123`, rồi đăng nhập.
4. Mở lại chính link đó lần hai.
5. Mở link có token bịa.

**Mong đợi:** (1) cả hai email đều hiện "đã gửi email nếu tài khoản tồn tại", Mailpit chỉ có một email; (3) đặt lại xong chuyển sang `/login`, đăng nhập bằng mật khẩu mới được; (4)(5) báo `INVALID_TOKEN` kèm gợi ý làm lại từ đầu. Sau kịch bản, đặt lại `customer@demo.local` về `Demo@1234`.

### E2E-A04 Chuyển hướng sau đăng nhập · P0 · APP_FLOW §1 · ✅ `account.spec.ts`, `smoke.spec.ts`

1. Khách mở `/bookings`, `/staff/bookings`, `/admin/flights` lần lượt.
2. Đăng nhập bằng từng vai trò khi URL có `?next=`, và khi không có.
3. Đăng nhập với `next=https://evil.example`.

**Mong đợi:** (1) chuyển về `/login?next=...`; (2) có `next` thì về `next`, không có thì KH → `/bookings`, NV → `/staff/bookings`, QT → `/admin/flights`; (3) bỏ qua `next` ngoài site, về trang mặc định.

### E2E-A05 Ma trận phân quyền · P0 · PRD §2.1, BR-104, D11 · 🟡 `admin.spec.ts` (chỉ `/admin/**`)

| Hành động                                                                     | Mong đợi                                          |
| ----------------------------------------------------------------------------- | ------------------------------------------------- |
| KH mở `/staff/**` và `/admin/**`                                              | Bị chặn (chuyển hướng hoặc 403), không lộ dữ liệu |
| NV mở `/admin/**`                                                             | Bị chặn                                           |
| NV mở `/staff/**`                                                             | Vào được                                          |
| QT mở `/staff/**`                                                             | Vào được (QT kế thừa quyền NV)                    |
| NV hoặc QT mở `/checkout`, đặt vé                                             | Không có chức năng đặt vé cho tài khoản vận hành  |
| KH gọi `GET /api/admin/airports` (qua `fetch` trong trình duyệt đã đăng nhập) | 403                                               |
| KH A mở `/bookings/{mã của KH B}`                                             | 404 `RESOURCE_NOT_FOUND`, không phải 403          |
| Chưa đăng nhập gọi `POST` mà thiếu CSRF                                       | 403                                               |

### E2E-A06 Khoá tài khoản có hiệu lực ngay · P1 · BR-102, BR-105, FR-110 · 🟡 `admin.spec.ts` (A-08)

1. Trình duyệt 1: KH đăng nhập, mở `/bookings`.
2. Trình duyệt 2: QT vào A-08, khoá KH đó.
3. Trình duyệt 1: tải lại trang hoặc bấm sang trang khác.
4. Thử đăng nhập lại bằng KH bị khoá. Sau đó QT mở khoá.
5. QT thử khoá chính mình.

**Mong đợi:** (3) phiên mất hiệu lực ngay từ request kế tiếp; (4) `ACCOUNT_LOCKED`, mở khoá xong đăng nhập được; (5) bị từ chối (BR-105).

### E2E-A07 Hết phiên sau 30 phút · P2 · FR-02 · ❌ (thủ công)

Đăng nhập, để yên quá 30 phút (hoặc xoá cookie phiên), bấm một liên kết cần đăng nhập. **Mong đợi:** về `/login?next=` kèm thông báo, không vỡ trang. Đăng xuất xong thì nút Back không cho xem lại trang đã đăng nhập.

---

## 4. Nhóm B: Tìm chuyến (C-01, C-02)

### E2E-B01 Gợi ý sân bay và tìm một chiều · P0 · FR-10, FR-11 · ✅ `flight-search.spec.ts`

1. Ở `/`, gõ `han`, `sai`, `phu quoc` vào ô sân bay.
2. Chọn HAN → SGN, ngày đi = hôm nay + 7, 1 người lớn, Phổ thông, bấm Tìm.

**Mong đợi:** gợi ý khớp theo mã, tên và thành phố, chỉ sân bay đang hoạt động; chuyển sang `/flights?from=HAN&to=SGN&date=...`; có `VN213`, `VJ123`, `QH201`, mỗi hành trình có giờ theo giờ địa phương, thời lượng, các gói giá kèm tổng cho đúng số khách.

### E2E-B02 Kiểm tra form tìm · P1 · FR-11, FR-12, BR-11 · 🟡

| Dữ liệu nhập                 | Mong đợi                                |
| ---------------------------- | --------------------------------------- |
| Sân bay đi trùng sân bay đến | Không cho tìm, có thông báo             |
| Ngày về trước ngày đi        | Không cho tìm                           |
| 2 em bé + 1 người lớn        | Báo lỗi: em bé không vượt quá người lớn |
| 10 người lớn                 | Báo lỗi vượt số khách tối đa (9)        |
| Chỉ có em bé                 | Báo lỗi: cần ít nhất 1 người lớn        |

### E2E-B03 Khứ hồi hai bước, lọc và sắp xếp · P0 · FR-12, BR-04 · ✅ `flight-search.spec.ts`

1. Tìm HAN ⇄ SGN, đi hôm nay + 7, về hôm nay + 7 (cùng ngày).
2. Chọn chiều đi `VN213` (đến SGN lúc 08:10).
3. Ở bước chiều về, quan sát danh sách.
4. Lọc theo hãng, số điểm dừng, khung giờ, khoảng giá; sắp xếp theo giá, giờ đi, thời lượng.

**Mong đợi:** luôn có tóm tắt chiều đi đã chọn; chiều về chỉ hiện chuyến cất cánh sau giờ hạ cánh của chiều đi (`VN220` 17:00, `VJ122` 19:00 có, chuyến trước giờ hạ cánh thì không); lọc và sắp xếp đúng, không gọi lại API.

### E2E-B04 Hành trình nối chuyến · P0 · BR-02, BR-03, D2 · ❌

1. Tìm HAN → PQC, ngày đi hôm nay + 7, 1 người lớn.

**Mong đợi:** có hành trình bay thẳng `VN1233` và hành trình 2 chặng qua SGN (`VN213` + `VN1825`...) nếu thời gian nối nằm trong 60–720 phút; hai chặng cùng hãng; hiển thị số điểm dừng và thời gian nối; giá chiều bằng tổng giá các chặng. Không có hành trình nối chuyến khác hãng.

### E2E-B05 Chuyến quốc tế hiển thị đúng múi giờ · P1 · NFR-05, D3 · ✅ `flight-search.spec.ts`

Tìm SGN → SIN. **Mong đợi:** `VN651` cất cánh 09:30 (+07:00) và hạ cánh 12:30 (+08:00) (120 phút bay + chênh 1 giờ); giờ không đổi khi đổi múi giờ trình duyệt.

### E2E-B06 Biên của kết quả · P1 · BR-01, BR-06, FR-11 · 🟡

| Tình huống                                                                      | Mong đợi                                              |
| ------------------------------------------------------------------------------- | ----------------------------------------------------- |
| Ngày đi là hôm nay                                                              | Chuyến cất cánh trong vòng 3 giờ tới không xuất hiện  |
| Tuyến không có chuyến (VD DAD → HAN thứ không bay) hoặc ngày ngoài 60 ngày seed | Danh sách rỗng và thông báo, không phải lỗi           |
| 9 người lớn, hạng Thương gia hãng VJ (10 ghế) sau khi đã có đơn giữ 2 ghế       | Gói giá không còn đủ ghế không hiện hoặc đánh dấu hết |
| URL `/flights?from=ZZZ&...` sai                                                 | Báo rõ, trang không vỡ                                |
| `/flights` không tham số                                                        | Vẫn hiện ô tìm                                        |

### E2E-B07 Chọn gói giá khi chưa đăng nhập · P0 · UF-01 · ✅ `flight-search.spec.ts`

Khách chọn gói giá → bị chuyển `/login` → đăng nhập → **quay lại `/checkout` với đúng hành trình đã chọn** (lưu ở `sessionStorage`). Mở `/checkout` trực tiếp khi chưa chọn gì thì quay về `/flights`.

---

## 5. Nhóm C: Đặt vé và thanh toán (C-07, C-09)

### E2E-C01 Đặt vé một chiều trọn vẹn, có voucher và hành lý · P0 · FR-20→FR-31, D1 · 🟡 `booking.spec.ts`

Dùng ví dụ **Q1**.

1. KH mới đăng nhập, tìm HAN → SGN, 2 người lớn + 1 trẻ em + 1 em bé, chọn `VN213` Economy Classic.
2. Ở C-07 khai 4 hành khách: họ và tên in hoa không dấu, ngày sinh khớp loại khách, giới tính. Kiểm tra thông tin liên hệ được điền sẵn từ tài khoản.
3. Chọn 20 kg cho người lớn thứ nhất. Nhập `WELCOME10`.
4. Xem bảng giá, bấm Giữ chỗ.
5. Ở C-09 bấm Thanh toán, trả bằng thẻ test VNPay.
6. Quay về C-09. Mở Mailpit.

**Mong đợi:**

- (3) bảng giá sau mỗi thay đổi khớp bảng Q1: tạm tính 5.960.000, giảm 200.000, tổng **5.760.000**.
- (4) mở C-09 với mã đặt chỗ 6 ký tự (không có `0 O 1 I`), trạng thái `PENDING_PAYMENT`, đếm ngược từ ~15:00, tổng tiền khớp báo giá.
- (5)(6) URL `?payment=success`, booking `ISSUED`, **4 số vé 13 chữ số** (4 khách × 1 chặng, em bé cũng có vé), mỗi số bắt đầu bằng `738` (mã số vé của VN). Email vé trong Mailpit tới đúng email liên hệ, số tiền khớp.

> Số vé = số hành khách × số chặng. Với Q1 một chiều bay thẳng là 4 vé. Kịch bản D1 gốc trong PRD (khứ hồi) cho 8 vé.

### E2E-C02 Khứ hồi 4 khách, voucher, đủ 8 vé · P0 · D1 · ❌

Như C01 nhưng khứ hồi HAN ⇄ SGN (`VN213` đi, `VN220` về), mua hành lý chỉ ở chiều đi. **Mong đợi:** giảm giá của voucher được chia cho hai chiều theo tỉ lệ tạm tính (BR-33), booking `ISSUED` với **8 số vé** (4 khách × 2 chặng), số vé duy nhất, tổng khớp báo giá, mỗi chiều có gói giá và quy định riêng.

### E2E-C03 Đặt nối chuyến · P0 · D2 · ❌

Đặt hành trình HAN → PQC nối qua SGN (từ B04), thanh toán. **Mong đợi:** mỗi khách có 2 số vé (một cho mỗi chặng), cả hai đầu số `738`, C-09 hiển thị 2 chặng của chiều.

### E2E-C04 Kiểm tra dữ liệu hành khách · P0 · BR-10→BR-14, D3 · 🟡 `booking.spec.ts`, `booking-request.spec.ts` (phần logic)

| Tình huống                                                                 | Mong đợi                                                                     |
| -------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| Họ có dấu `Nguyễn` hoặc chữ thường                                         | Tự bỏ dấu và viết hoa, hoặc báo lỗi đúng ô (BR-12)                           |
| Ngày sinh khiến tuổi lệch loại khai báo (người lớn 5 tuổi, trẻ em 13 tuổi) | Báo lỗi đúng ô                                                               |
| Ngày sinh sau ngày bay                                                     | Báo lỗi                                                                      |
| Số điện thoại `12345` hoặc có chữ                                          | Báo lỗi (9–15 chữ số)                                                        |
| Chuyến quốc tế (SGN → SIN), bỏ trống hộ chiếu                              | Báo lỗi `PASSENGER_RULES_VIOLATED`, đánh dấu từng ô bị thiếu, **ở lại C-07** |
| Hộ chiếu hết hạn trong vòng 6 tháng kể từ ngày bay                         | Báo lỗi                                                                      |
| Chuyến nội địa                                                             | Không hiện các ô hộ chiếu                                                    |

### E2E-C05 Voucher · P1 · FR-23, BR-30→BR-33 · ❌

| Dữ liệu                                                                                       | Mong đợi                                                            |
| --------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| Mã sai `ABC`                                                                                  | `VOUCHER_INVALID`, lý do không tồn tại                              |
| `SALE100K` với tạm tính 990.000                                                               | Lý do chưa đạt đơn tối thiểu (1.000.000)                            |
| `SALE100K` với tạm tính 1.870.000                                                             | Giảm cố định 100.000                                                |
| `WELCOME10` với tạm tính 1.000.000                                                            | 10% = 100.000, chưa chạm trần                                       |
| `WELCOME10` với tạm tính 5.960.000                                                            | Giảm 200.000 (trần)                                                 |
| Dùng `WELCOME10` lần hai bằng cùng tài khoản khi booking trước còn `PENDING_PAYMENT`/`ISSUED` | Lý do tài khoản đã dùng                                             |
| Dùng lại sau khi booking trước `CANCELLED`                                                    | Dùng được (BR-32)                                                   |
| Voucher đã tạo trong A-07 nhưng đặt ngày kết thúc là hôm qua, hoặc ngừng kích hoạt            | Không dùng được                                                     |
| Voucher giảm hết toàn bộ (tạo `FIXED` ≥ tạm tính)                                             | Tổng 0, booking **`ISSUED` ngay**, không có bước thanh toán (BR-40) |

### E2E-C06 Giữ chỗ lỗi · P0 · FR-24 · 🟡 `booking.spec.ts`

- Chuyến không còn bán (QT huỷ chuyến, hoặc đã sát giờ bay) khi KH đang ở C-07 → bấm Giữ chỗ → quay lại C-02 kèm thông báo `FLIGHT_NOT_BOOKABLE`.
- Hết ghế (xem C09) → quay lại C-02 kèm `SEATS_UNAVAILABLE`, không có booking nào được tạo.

### E2E-C07 Thanh toán thất bại và thử lại · P0 · FR-30, BR-41 · 🟡

1. Tạo booking `PENDING_PAYMENT`, bấm Thanh toán.
2. Trên VNPay bấm **Hủy giao dịch**, rồi quay về.
3. Bấm Thanh toán lại, lần này nhập thẻ đúng.

**Mong đợi:** (2) C-09 báo thanh toán thất bại, booking **vẫn `PENDING_PAYMENT`**, đếm ngược vẫn chạy, nút Thanh toán còn; (3) thành công, `ISSUED`, chỉ **một** bộ vé và **một** email vé. Bảng lượt thu có 1 `FAILED` và 1 `SUCCESS`.

### E2E-C08 Đóng trình duyệt giữa chừng, IPN vẫn xuất vé · P1 · UF-01, FR-31 · 🟡 `aftersales.spec.ts` (ký Return URL)

1. Giữ chỗ, bấm Thanh toán, **đóng tab** ở trang VNPay.
2. Mở lại `/bookings`, vào C-09 thanh toán tiếp khi còn hạn giữ chỗ.
3. Biến thể: gửi IPN thành công thẳng tới `/api/payments/vnpay/ipn` (ký bằng `VNPAY_HASH_SECRET`), không qua Return URL.

**Mong đợi:** (2) thanh toán tiếp được; (3) booking `ISSUED` và có email vé dù khách không quay về. Gọi IPN **hai lần** với cùng dữ liệu: chỉ một bộ vé (FR-31), phản hồi `RspCode` hợp lệ cho cả hai.

### E2E-C09 Hai khách tranh ghế cuối · P0 · D4, NFR-02 · ❌ (có integration test backend)

1. QT tạo chuyến thử còn đúng **1 ghế** hạng phổ thông (A-05).
2. Hai KH (hai trình duyệt riêng) cùng chọn chuyến đó, cùng điền xong form C-07.
3. Hai bên bấm Giữ chỗ gần như đồng thời.

**Mong đợi:** đúng một bên vào C-09; bên kia quay về C-02 với `SEATS_UNAVAILABLE`. Ở A-06 chuyến đó hiện ghế còn = 0 (không âm).

### E2E-C10 Huỷ giữ chỗ thủ công · P0 · FR-33, BR-45 · ✅ `booking.spec.ts`

Tạo booking có voucher → bấm Huỷ giữ chỗ → xác nhận. **Mong đợi:** `CANCELLED`, ghế của chuyến tăng lại đúng số khách chiếm ghế (xem A-06), lượt voucher được trả (dùng lại `WELCOME10` được), nút Thanh toán biến mất.

### E2E-C11 Hết hạn giữ chỗ tự động · P0 · BR-40, BR-45, D5 · ✅ `booking.spec.ts`

1. QT vào A-09 đổi `booking.hold_minutes` về 5 để rút ngắn thời gian chờ (hoàn lại sau kịch bản).
2. KH giữ chỗ có voucher, không thanh toán.
3. Chờ hạn giữ chỗ + 5 phút + tối đa 1 phút của job.

**Mong đợi:** đếm ngược về `00:00`, nút Thanh toán bị vô hiệu hoặc báo `HOLD_EXPIRED` khi bấm; sau đó booking `EXPIRED`, ghế và lượt voucher được trả.

### E2E-C12 Thanh toán trễ → hoàn tự động · P1 · FR-32, BR-44 · ❌

1. Giữ chỗ, bấm Thanh toán để có lượt thu `PENDING`.
2. Chờ booking sang `EXPIRED` (như C11) hoặc huỷ giữ chỗ.
3. Gửi IPN thành công ký hợp lệ cho lượt thu đó.

**Mong đợi:** booking giữ nguyên `EXPIRED`/`CANCELLED`, **không** xuất vé; ở S-03 xuất hiện yêu cầu hoàn nguồn `INVALID_PAYMENT`, số tiền 100% lượt thu, không có nút Từ chối; C-09 của khách hiện yêu cầu hoàn này.

### E2E-C13 Chữ ký và số tiền sai · P1 · BR-42, NFR-03 · ❌

Gửi IPN với (a) chữ ký sai, (b) số tiền lệch, (c) `vnp_ResponseCode≠00`. **Mong đợi:** (a) `RspCode` chữ ký sai, (b) số tiền không khớp, (c) lượt thu `FAILED`; cả ba trường hợp booking vẫn `PENDING_PAYMENT`, không có vé.

### E2E-C14 Danh sách và chi tiết booking · P0 · FR-40, FR-41 · 🟡

KH có ít nhất 3 booking (ISSUED, CANCELLED, PENDING_PAYMENT). **Mong đợi:** `/bookings` mới nhất trước; lọc `?status=` đúng; phân trang nếu >1 trang; C-09 chỉ hiện thao tác hợp lệ (xem bảng dưới); ẩn nút khi chưa hydrate.

| Trạng thái                                 | Thanh toán | Huỷ giữ chỗ | Hoàn |        Đổi        |
| ------------------------------------------ | :--------: | :---------: | :--: | :---------------: |
| `PENDING_PAYMENT`, còn hạn                 |     ✓      |      ✓      |      |                   |
| `PENDING_PAYMENT`, quá hạn                 |            |      ✓      |      |                   |
| `ISSUED`, gói cho hoàn/đổi, còn ≥ 24 giờ   |            |             |  ✓   |         ✓         |
| `ISSUED`, gói không hoàn (VD Economy Lite) |            |             |      | ✓ nếu gói cho đổi |
| `ISSUED`, còn < 24 giờ                     |            |             |      |                   |
| Chiều đã `REFUNDED`                        |            |             |      |                   |
| `EXPIRED`, `CANCELLED`, `REFUNDED`         |            |             |      |                   |

---

## 6. Nhóm D: Hậu mãi của khách (C-10, C-11)

### E2E-D01 Hoàn một chiều, Staff duyệt, hoàn tiền xong · P0 · FR-50→FR-52, FR-71, BR-60→BR-62, D7 · ✅ `aftersales.spec.ts`, 🟡 `staff.spec.ts`

Dùng booking `ISSUED` Economy Classic, 1 người lớn, giá chiều 1.870.000.

1. KH mở C-10 chiều đi. **Mong đợi:** giá trị chiều 1.870.000, phí 600.000, hoàn **1.270.000**.
2. Bỏ trống lý do rồi gửi → báo lỗi. Nhập lý do rồi gửi.
3. Quay về C-09: chiều có yêu cầu `PENDING`, ẩn nút Hoàn/Đổi của chiều đó.
4. NV vào S-03, mở yêu cầu, bấm Duyệt.
5. Xem lại ở C-09 và Mailpit.

**Mong đợi:** (4) chiều `REFUNDED`, ghế trả ngay; booking `REFUNDED` nếu mọi chiều đều hoàn (BR-65); VNPay Refund API chạy. Sandbox thường **không hoàn được cho lượt thu tự ký**, nên yêu cầu sẽ ở `APPROVED` kèm `last_error` → NV xác nhận hoàn thủ công kèm ghi chú → `COMPLETED`. (5) email "đã hoàn tiền" gửi đúng một lần khi `COMPLETED`.

### E2E-D02 Hoàn bị từ chối · P1 · BR-63 · ✅ `staff.spec.ts`

NV bấm Từ chối: lý do bắt buộc. **Mong đợi:** `REJECTED`, booking giữ nguyên `ISSUED`, KH thấy lý do và nhận email "từ chối hoàn"; KH gửi được yêu cầu mới.

### E2E-D03 Hoàn không đủ điều kiện · P0 · BR-51, BR-60, FR-50 · 🟡 `booking-request.spec.ts` (logic ẩn nút)

| Tình huống                         | Mong đợi                                                       |
| ---------------------------------- | -------------------------------------------------------------- |
| Gói không hoàn (Economy Lite)      | Không có nút Hoàn; mở thẳng URL C-10 → `FARE_RULE_NOT_ALLOWED` |
| Chuyến cất cánh sau < 24 giờ       | `DEADLINE_PASSED`                                              |
| Chiều đã có yêu cầu `PENDING`      | `REQUEST_IN_PROGRESS`                                          |
| Booking chưa `ISSUED`              | `INVALID_STATE`                                                |
| Phí hoàn lớn hơn giá trị chiều     | Số tiền hoàn hiển thị 0, không âm (BR-61)                      |
| Booking của người khác             | 404                                                            |
| `/refund/xyz` (hướng không hợp lệ) | 404                                                            |

### E2E-D04 Hoàn theo từng chiều, khứ hồi · P1 · BR-50, BR-65 · ❌

Booking khứ hồi `ISSUED`. Hoàn chiều đi và được duyệt. **Mong đợi:** booking **vẫn `ISSUED`**, chiều về còn `ACTIVE` và vẫn hoàn/đổi được; hoàn nốt chiều về thì booking sang `REFUNDED`. Voucher **không** được trả lại (BR-32).

### E2E-D05 Đổi chuyến có trả tiền · P0 · FR-60→FR-62, BR-72→BR-74, D6 · ✅ `aftersales.spec.ts`

Booking khứ hồi Economy Classic, đổi chiều về sang ngày khác.

1. Ở C-11 chọn ngày mới. **Mong đợi:** danh sách hành trình cùng tuyến, cùng hãng, cùng gói giá, mỗi lựa chọn kèm số tiền phải trả = phí đổi 300.000 + max(0, giá mới − giá cũ) (BR-72).
2. Chọn một hành trình, xem báo giá, xác nhận.
3. C-09 hiện yêu cầu đổi `PENDING_PAYMENT` và đếm ngược; bấm Thanh toán, trả qua VNPay.

**Mong đợi:** chặng cũ `REPLACED` và trả ghế, chặng mới `ACTIVE` với số vé mới, yêu cầu `COMPLETED`, email vé mới. Hành lý và mức giảm của chiều giữ nguyên. Chiều đi không đổi.

### E2E-D06 Đổi miễn phí hoàn tất ngay · P1 · BR-73 · ✅ `aftersales.spec.ts`

Gói Economy Flex (phí đổi 0) đổi sang chuyến rẻ hơn hoặc bằng. **Mong đợi:** số tiền phải trả 0 → `COMPLETED` ngay, không qua VNPay, số vé mới được cấp, có email. Ngày mặc định ở bộ chọn ngày theo giờ sân bay (không lệch một ngày).

### E2E-D07 Đổi sang chuyến rẻ hơn không được hoàn chênh lệch · P1 · BR-72 · ❌

Chiều có giá 2.450.000 (Flex) đổi sang chuyến rẻ hơn. **Mong đợi:** tiền phải trả = phí đổi, không có dòng hoàn chênh lệch.

### E2E-D08 Yêu cầu đổi hết hạn · P1 · BR-75 · ❌

Xác nhận đổi có trả phí, không thanh toán, chờ quá hạn + 5 phút. **Mong đợi:** yêu cầu `EXPIRED`, chặng mới `CANCELLED` và trả ghế chuyến mới; chiều về như trước khi đổi, vẫn đổi lại được.

### E2E-D09 Đổi không đủ điều kiện · P1 · BR-70, BR-71 · ❌

Gói không cho đổi, chuyến còn < 24 giờ, đang có yêu cầu hoàn mở, đổi khi chiều còn lại sẽ vi phạm BR-04. **Mong đợi:** không có nút Đổi hoặc báo lỗi đúng mã; ngày mới chọn bất kỳ vẫn hợp lệ (BR-71).

---

## 7. Nhóm E: Nhân viên (S-01→S-06)

### E2E-E01 Tra cứu và gửi lại email vé · P0 · FR-70, FR-73, UF-07 · ✅ `staff.spec.ts`

NV vào S-01, tìm theo mã đặt chỗ, theo email liên hệ và theo số điện thoại, lọc `status=ISSUED`. Mở S-02. **Mong đợi:** kết quả khớp; S-02 giống C-09 (chỉ đọc) cộng tài khoản đã đặt và lịch sử thu, hoàn, đổi; "Gửi lại email vé" tạo thêm một email trong Mailpit; nút này **không hiện** cho booking chưa `ISSUED`.

### E2E-E02 Hàng đợi yêu cầu hoàn · P1 · FR-71 · 🟡 `staff.spec.ts`

Có yêu cầu nguồn `CUSTOMER`, `FLIGHT_CANCELLED`, `INVALID_PAYMENT`. **Mong đợi:** S-03 xếp cũ nhất trước, lọc `?status=&source=` đúng; S-04 với yêu cầu nguồn hệ thống **không có nút Từ chối** (BR-66); ghi nhận người xử lý và thời điểm xử lý.

### E2E-E03 Duyệt, thử lại, hoàn thủ công · P1 · BR-64 · ✅ `staff.spec.ts`

Duyệt khi VNPay lỗi → yêu cầu `APPROVED` + `last_error` → bấm Thử lại (chỉ hoàn phần còn thiếu) → bấm Hoàn thủ công, bắt buộc ghi chú (để trống bị chặn). **Mong đợi:** `COMPLETED`, dòng hoàn thủ công xuất hiện trong lịch sử, tính vào "Tổng hoàn" của báo cáo.

### E2E-E04 Danh sách chuyến và manifest · P2 · FR-72, FR-91 · ❌

1. S-05: lọc theo tuyến, ngày, hãng, trạng thái. Kiểm tra tổng ghế và ghế còn.
2. Mở S-06 của chuyến có booking đã xuất vé và booking chờ thanh toán.

**Mong đợi:** manifest chỉ liệt kê vé đang hiệu lực của booking `ISSUED` (không có `PENDING_PAYMENT`, chặng `REPLACED` hoặc `CANCELLED`) với mã đặt chỗ, họ tên, loại khách, số vé, hạng ghế, liên hệ.

---

## 8. Nhóm F: Quản trị (A-01→A-10)

### E2E-F01 Chuẩn bị dữ liệu rồi mở bán · P0 · UF-04, FR-80→FR-90 · ✅ `admin.spec.ts` (A-01→A-05)

Theo thứ tự: A-01 thêm sân bay → A-02 thêm hãng (mã số vé 3 chữ số) → A-03 thêm gói giá và mức hành lý → A-05 tạo chuyến → **KH tìm thấy chuyến đó** trong C-02 với đúng giá. **Mong đợi:** cuối chuỗi có chuyến đặt được. Tạo lại đúng khoảng ngày đó: kết quả báo "bỏ qua N ngày vì trùng".

### E2E-F02 Danh mục: ràng buộc xoá và kiểm tra dữ liệu · P0 · BR-90→BR-92 · ✅ `admin.spec.ts`

| Hành động                                                  | Mong đợi                                                          |
| ---------------------------------------------------------- | ----------------------------------------------------------------- |
| Thêm sân bay mã 2 chữ cái, múi giờ sai, quốc gia 3 chữ cái | Báo lỗi đúng ô                                                    |
| Thêm sân bay/hãng trùng mã                                 | Báo lỗi                                                           |
| Xoá sân bay hoặc hãng đã có chuyến                         | Không xoá được, chỉ ngừng kích hoạt                               |
| Ngừng kích hoạt hãng                                       | Không chọn được khi tạo chuyến mới, chuyến cũ vẫn tìm và đặt được |
| Tên gói giá hoặc mức kg trùng trong một hãng               | Báo lỗi (BR-92)                                                   |
| Ngừng bán một gói giá                                      | Gói đó biến khỏi kết quả tìm kiếm, booking cũ không đổi           |

### E2E-F03 Tạo chuyến hàng loạt · P1 · FR-90, BR-80, BR-81, BR-85 · 🟡 `admin.spec.ts`

| Dữ liệu                                                     | Mong đợi                                                              |
| ----------------------------------------------------------- | --------------------------------------------------------------------- |
| Khoảng 14 ngày, chọn Thứ 2/4/6                              | Số chuyến tạo ra đúng số ngày rơi vào các thứ đó                      |
| Khoảng > 180 ngày                                           | Báo lỗi                                                               |
| Số hiệu không đúng mẫu hãng + 1–4 chữ số (`VN12345`, `XX1`) | Báo lỗi                                                               |
| Sân bay đi trùng sân bay đến                                | Báo lỗi                                                               |
| Không chọn hạng ghế nào                                     | Báo lỗi                                                               |
| Giá bán cho gói giá của hãng khác                           | Không thể nhập                                                        |
| Giờ hạ cánh                                                 | = giờ cất cánh + thời lượng, hiển thị đúng giờ địa phương sân bay đến |

### E2E-F04 Sửa chuyến và các giới hạn khi đã có booking · P1 · FR-92, BR-82, BR-86, BR-87 · 🟡

Chuyến đã có booking: **không** đổi được hãng, số hiệu, sân bay; **không** giảm ghế dưới số đã bán hoặc đang giữ; **không** bỏ hạng đang có ghế bán; **không** xoá. Chuyến đã cất cánh: không còn nút Sửa/Huỷ (✅ có test). Đổi giá bán chỉ ảnh hưởng booking mới (booking cũ giữ giá, BR-24).

### E2E-F05 Đổi giờ chuyến gửi email · P1 · BR-83 · ❌

QT đổi giờ cất cánh của chuyến có booking `ISSUED`. **Mong đợi:** mỗi booking `ISSUED` có chặng `ACTIVE` trên chuyến nhận một email "đổi giờ" ở email liên hệ; booking `PENDING_PAYMENT` và `EXPIRED` thì không; giờ trong email đúng giờ địa phương.

### E2E-F06 Huỷ chuyến có đủ loại booking · P0 · FR-93, BR-84, UF-05, D8 · 🟡 `admin.spec.ts` (huỷ chuyến chưa có booking)

Chuẩn bị chuyến X có: (a) booking `ISSUED`, (b) booking `PENDING_PAYMENT` có voucher, (c) booking có yêu cầu hoàn `PENDING` của khách, (d) booking có yêu cầu đổi `PENDING_PAYMENT` mà chặng mới nằm trên X.

1. QT vào A-06, bấm Huỷ chuyến, **xác nhận hai bước** (huỷ ở bước 2 thì không có gì xảy ra).
2. Kiểm tra từng booking.

**Mong đợi:**

- (b) `CANCELLED`, ghế và lượt voucher được trả.
- (d) yêu cầu đổi `CANCELLED`, ghế chuyến mới được trả, chiều cũ nguyên vẹn.
- (a) tự có yêu cầu hoàn **100%** nguồn `FLIGHT_CANCELLED`, phí 0, hiện ở S-03 và C-09.
- (c) yêu cầu hoàn đổi nguồn sang `FLIGHT_CANCELLED`, phí 0, số tiền tính lại (có thể lớn hơn trước).
- Mỗi booking (a)(c) nhận một email "chuyến bị huỷ".
- Chuyến không còn trong kết quả tìm kiếm. Huỷ lần hai báo lỗi. Chuyến đã cất cánh không huỷ được.
- NV xử lý các yêu cầu này như D01, không có nút Từ chối.

### E2E-F07 Voucher · P1 · FR-100 · ✅ `admin.spec.ts` (A-07)

Tạo, sửa, ngừng kích hoạt voucher; mã trùng bị từ chối; mã không sửa được sau khi tạo; cột "đã dùng" tăng sau khi KH đặt vé (đối chiếu C05). Thay đổi chỉ áp dụng cho booking mới.

### E2E-F08 Tham số hệ thống ảnh hưởng đúng giao dịch mới · P1 · FR-111, BR-25, D9 · 🟡 `admin.spec.ts` (A-09 chỉ kiểm sửa)

1. A-09 nhập giá trị ngoài khoảng (`booking.hold_minutes = 4` hoặc `61`) → báo lỗi.
2. Đặt `booking.hold_minutes` từ 15 lên 20.
3. KH tạo booking mới, so sánh với booking tạo trước đó.

**Mong đợi:** (3) booking mới hạn giữ 20 phút, booking cũ vẫn 15; A-09 hiện người sửa và thời điểm sửa. Ngoài ra thử `pricing.child_percent=80` (báo giá trẻ em mới 80%), `booking.max_seated_passengers=2` (chặn nhập 3 khách), `search.min_connection_minutes` lớn hơn `max` (bị từ chối). **Hoàn lại giá trị mặc định sau kịch bản.**

### E2E-F09 Báo cáo khớp giao dịch · P1 · FR-120→FR-122, BR-110→BR-112, D10 · 🟡 `admin.spec.ts` (A-10 chỉ kiểm hiển thị)

Sau khi chạy C01, C02, D01, D05 trên dữ liệu sạch (hoặc ghi lại số trước và sau). **Mong đợi:**

- **Dòng tiền:** tổng thu tăng đúng số tiền VNPay đã thu; tổng hoàn tăng đúng số đã hoàn (kể cả hoàn thủ công); doanh thu ròng = thu − hoàn. Nhóm theo ngày/tháng/năm cộng lại cùng một tổng.
- **Doanh số vé:** số vé và tổng giá vé theo hãng/tuyến chỉ tính vé thuộc chặng `ACTIVE` (chặng `REPLACED` hoặc `REFUNDED` không tính).
- **Tỉ lệ lấp đầy:** chuyến vừa đặt hoặc giữ chỗ tăng tỉ lệ đúng `(tổng − còn) / tổng`; lọc theo hãng đúng.

---

## 9. Nhóm G: Điều hướng, giao diện và phi chức năng

### E2E-G01 Điều hướng khách · P1 · spec điều hướng khách hàng · ✅ `user-menu.spec.ts`, `smoke.spec.ts`

Header chung, menu tên mở đúng vị trí (màn rộng và điện thoại), chọn mục đang mở thì đóng, Đăng xuất hoạt động; `/checkout` có thanh bước; `/flights` có ô tìm điền sẵn.

### E2E-G02 Chế độ tối và điện thoại · P2 · DESIGN.md · 🟡

Bật dark mode: không có chữ mất tương phản trên C-01, C-02, C-07, C-09, S-04, A-06. Ở 390×844: không cuộn ngang, nút bấm đủ lớn, đặt vé được trọn vẹn C01 trên điện thoại.

### E2E-G03 Truy cập bằng bàn phím (a11y cơ bản) · P2 · ✅ một phần (`admin.spec.ts` hộp thoại)

Điều hướng bằng bàn phím qua form C-07; hộp thoại Admin giữ focus, Escape đóng và trả focus; mọi ô nhập có nhãn; thông báo lỗi gắn với ô tương ứng.

### E2E-G04 Hiệu năng cảm nhận · P2 · NFR-01 · ❌

Với 1.200 chuyến seed: tìm kiếm trả kết quả dưới ~1 giây, các trang danh sách (S-01, S-03, A-04) dưới ~500 ms; không có request lặp thừa khi gõ ô sân bay (có debounce).

### E2E-G05 Smoke mọi route · P0 · ✅ `smoke.spec.ts`

Mọi route công khai hiển thị được; route cần đăng nhập chuyển về `/login?next=`; sau khi đăng nhập hiển thị được. Route mới thêm phải vào danh sách `PUBLIC`/`PROTECTED`.

---

## 10. Độ phủ so với kịch bản demo (PRD §9)

| Demo | Nội dung                                                   | Kịch bản E2E       | Trạng thái                     |
| ---- | ---------------------------------------------------------- | ------------------ | ------------------------------ |
| D1   | Đăng ký, khứ hồi 4 khách, hành lý, `WELCOME10`, thanh toán | A01, B03, C01, C02 | 🟡 thiếu khứ hồi 8 vé trọn vẹn |
| D2   | Nối chuyến HAN→SGN→PQC                                     | B04, C03           | ❌                             |
| D3   | Quốc tế, thiếu hộ chiếu, giờ Singapore                     | B05, C04           | ✅                             |
| D4   | Hai khách tranh ghế cuối                                   | C09                | ❌                             |
| D5   | Không thanh toán, tự hết hạn                               | C11                | ✅                             |
| D6   | Đổi chuyến, trả phí qua VNPay                              | D05                | ✅                             |
| D7   | Hoàn vé, Staff duyệt                                       | D01, E03           | ✅                             |
| D8   | Huỷ chuyến có cả `ISSUED` và `PENDING_PAYMENT`             | F06                | 🟡                             |
| D9   | Đổi `booking.hold_minutes`                                 | F08                | 🟡                             |
| D10  | Báo cáo khớp D1–D8                                         | F09                | 🟡                             |
| D11  | Phân quyền 403 và 404                                      | A05                | 🟡                             |

### Khoảng trống cần ưu tiên viết tự động

1. **C09** hai khách tranh ghế (D4) và **C12/C13** IPN trễ, trùng, sai chữ ký: các luồng dễ sai nhất, hiện chỉ có integration test backend.
2. **F06** huỷ chuyến có đủ 4 loại booking (D8) và **F05** email đổi giờ.
3. **B04/C03** nối chuyến (D2) và **C02** khứ hồi 8 vé (D1).
4. **A03** quên mật khẩu, **C05** các lý do voucher không dùng được.
5. **F09** báo cáo khớp số liệu (D10), kiểm số cụ thể thay vì chỉ kiểm hiển thị.

---

## 11. Thứ tự chạy đề xuất

Các kịch bản dùng chung dữ liệu nên chạy theo thứ tự này để mỗi bước dựng dữ liệu cho bước sau, và hoàn lại tham số về mặc định ở cuối.

| Lượt                 | Kịch bản          | Mục đích                                                  |
| -------------------- | ----------------- | --------------------------------------------------------- |
| 1. Smoke             | G05, A04, A05     | Xác nhận môi trường và phân quyền trước khi tốn thời gian |
| 2. Tài khoản, tìm    | A01–A03, B01–B07  | Không tạo dữ liệu nghiệp vụ                               |
| 3. Dựng dữ liệu      | F01, F02, F03     | Chuyến thử riêng cho các kịch bản tranh ghế, huỷ chuyến   |
| 4. Đặt và thanh toán | C01–C08, C10, C14 | Tạo booking `ISSUED` cho các lượt sau                     |
| 5. Đồng thời, trễ    | C09, C12, C13     | Dùng chuyến thử của lượt 3                                |
| 6. Hậu mãi           | D01–D09, E01–E04  | Dùng booking của lượt 4                                   |
| 7. Sự cố chuyến bay  | F04, F05, F06     | Dùng chuyến có đủ loại booking                            |
| 8. Tham số, hết hạn  | F08, C11, D08     | Chạy cuối vì phải chờ 5–10 phút, rồi hoàn lại tham số     |
| 9. Báo cáo           | F09               | Đối chiếu sau khi mọi giao dịch đã phát sinh              |

## 12. Tiêu chí đạt

- Mọi kịch bản **P0** pass trên Docker Compose với VNPay sandbox.
- Không có lỗi `5xx` hoặc trang trắng ở bất kỳ bước nào.
- Số tiền trên giao diện, trong email và ở báo cáo khớp nhau và khớp phép tính trong tài liệu này.
- Bất biến ghế (APP_FLOW §4.3): sau toàn bộ lượt chạy, với mỗi chuyến, `ghế còn + ghế đang giữ bởi chặng ACTIVE/PENDING_CHANGE = tổng ghế`.
- Lỗi tìm được ghi kèm: mã kịch bản, bước, ảnh chụp, mã đặt chỗ, và log backend cùng thời điểm.
