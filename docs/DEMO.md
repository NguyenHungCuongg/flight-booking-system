# Kịch bản demo SkyLine

|                    |                                                                                                  |
| ------------------ | ------------------------------------------------------------------------------------------------ |
| Ngày               | 2026-10-26                                                                                       |
| Tài liệu liên quan | [PRD §7](PRD.md) (D1–D11) · [E2E_TEST_SCENARIOS](E2E_TEST_SCENARIOS.md) · [README](../README.md) |

Tài liệu này là kịch bản thuyết trình. Phần 3 đi qua 11 kịch bản D1–D11 của PRD theo thứ tự diễn, mỗi bước ghi thao tác và điều cần chỉ cho người xem. Mọi kịch bản đã có E2E tự động (phần 4), nên nếu một bước trên sân khấu trục trặc, có thể chạy lại test để chứng minh.

---

## 1. Chuẩn bị (trước buổi demo một ngày)

1. Docker Desktop đang chạy. Trong `.env` có `SPRING_PROFILES_ACTIVE=dev`, `VNPAY_TMN_CODE` và `VNPAY_HASH_SECRET` của tài khoản VNPay sandbox.
2. Dựng lại dữ liệu sạch, vì seed `V106` chỉ sinh chuyến bay cho 60 ngày kể từ lúc chạy migration:

   ```bash
   docker compose down -v
   docker compose up -d --build
   docker compose ps          # đủ 4 service "healthy": postgres, mailpit, backend, frontend
   ```

3. Mở sẵn các tab:

   | Tab                     | Địa chỉ                                        |
   | ----------------------- | ---------------------------------------------- |
   | Khách hàng              | http://localhost:3000                          |
   | Khách thứ hai (ẩn danh) | http://localhost:3000 trong cửa sổ ẩn danh     |
   | Staff/Admin             | http://localhost:3000/login (trình duyệt khác) |
   | Mailpit                 | http://localhost:8025                          |
   | Swagger                 | http://localhost:8080/swagger-ui.html          |

4. Chạy thử toàn bộ E2E một lần trên stack vừa dựng (mục 4). Chạy xong thì `docker compose down -v && docker compose up -d` lại để số liệu báo cáo chỉ có giao dịch của buổi demo.

**Thanh toán:** dùng thẻ test NCB `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456`. VNPay gọi IPN tới URL công khai nên không tới được `localhost`. Booking vẫn xuất vé nhờ Return URL khi trình duyệt quay về (TDD AD-07). Muốn demo IPN thì mở tunnel (VD `cloudflared tunnel --url http://localhost:3000`) và khai URL đó trên cổng VNPay.

**Tài khoản:** `admin@demo.local`, `staff@demo.local`, `customer@demo.local`, mật khẩu `Demo@1234`. Kịch bản D1 đăng ký tài khoản mới vì mỗi tài khoản chỉ dùng `WELCOME10` được một lần.

---

## 2. Thứ tự diễn

D5 cần chờ hết hạn giữ chỗ, nên tạo booking của D5 ngay đầu buổi rồi quay lại ở cuối. D9 đổi thời gian giữ chỗ, nên diễn sau D5. D10 đọc lại số liệu của D1–D8, nên diễn gần cuối.

| #   | Kịch bản                                        | Vai trò         | Thời lượng |
| --- | ----------------------------------------------- | --------------- | ---------- |
| 0   | Mở đầu: kiến trúc, `docker compose ps`, Swagger |                 | 2 phút     |
| 1   | D5 (bước 1): giữ chỗ, không trả                 | Khách           | 1 phút     |
| 2   | D1 khứ hồi 4 khách                              | Khách mới       | 5 phút     |
| 3   | D2 nối chuyến                                   | Khách           | 2 phút     |
| 4   | D3 quốc tế, thiếu hộ chiếu                      | Khách           | 2 phút     |
| 5   | D4 tranh ghế cuối                               | Admin + 2 khách | 3 phút     |
| 6   | D6 đổi chuyến                                   | Khách           | 3 phút     |
| 7   | D7 hoàn vé, Staff duyệt                         | Khách + Staff   | 3 phút     |
| 8   | D8 huỷ chuyến                                   | Admin           | 3 phút     |
| 9   | D5 (bước 2): booking đã hết hạn                 | Khách           | 1 phút     |
| 10  | D9 đổi tham số giữ chỗ                          | Admin + Khách   | 2 phút     |
| 11  | D10 báo cáo                                     | Admin           | 2 phút     |
| 12  | D11 phân quyền                                  | Khách           | 2 phút     |

---

## 3. Từng kịch bản

Ngày bay dùng "hôm nay + 7" trừ khi ghi khác. Giờ trên màn hình luôn là giờ của sân bay, kể cả khi máy đặt múi giờ khác.

### D5 (bước 1): giữ chỗ rồi không thanh toán

1. Đăng nhập `customer@demo.local`. Tìm HAN → SGN một chiều, chọn `VJ123` gói Eco.
2. Khai một hành khách, bấm **Giữ chỗ**.
3. **Chỉ ra:** C-09 có trạng thái "Chờ thanh toán" và đồng hồ đếm ngược 15 phút (`booking.hold_minutes`). Ghi lại mã booking.

### D1: khứ hồi 4 khách, hành lý, voucher, thanh toán

1. Đăng ký tài khoản mới ở `/register`.
2. Tìm khứ hồi HAN ⇄ SGN cho 2 người lớn, 1 trẻ em, 1 em bé.
3. Bước 1 chọn `VN213` gói Economy Classic, bước 2 chọn `VN220` gói Economy Classic. **Chỉ ra:** bước 2 chỉ hiện chuyến cất cánh sau giờ hạ cánh của chiều đi (BR-04).
4. Ở C-07 khai 4 hành khách. Họ tên có dấu được tự bỏ dấu và viết hoa (BR-12). Mua 20 kg hành lý cho người lớn 1 ở chiều đi. Nhập `WELCOME10`.
5. **Chỉ ra bảng giá:** mỗi chiều 5.610.000 đ (trẻ em 90%, em bé 10%), hành lý 350.000 đ, voucher giảm 10% chạm trần 200.000 đ, tổng **11.370.000 đ**.
6. Giữ chỗ, bấm Thanh toán, trả bằng thẻ test trên VNPay.
7. **Chỉ ra:** C-09 "Đã xuất vé" với **8 số vé** (4 khách × 2 chặng), mỗi số 13 chữ số, đầu `738`. Mailpit có email vé tới đúng email liên hệ.

### D2: nối chuyến HAN → SGN → PQC

1. Tìm HAN → PQC, 1 người lớn.
2. **Chỉ ra:** ngoài chuyến bay thẳng có hành trình `VN213 + VN1825` "1 điểm dừng, SGN", hai chặng cùng hãng, thời gian nối hiện trong chi tiết (60–720 phút, BR-03).
3. Chọn Economy Lite, đặt và thanh toán. **Chỉ ra:** C-09 có 2 chặng trong một chiều, hành khách có 2 số vé.

### D3: chuyến quốc tế, thiếu hộ chiếu

1. Tìm SGN → SIN, chọn `VN651`.
2. **Chỉ ra:** giờ hạ cánh theo giờ Singapore (+08:00). C-07 hỏi quốc tịch, số hộ chiếu, hạn hộ chiếu.
3. Để trống hộ chiếu hoặc nhập hạn chỉ còn 30 ngày, bấm Giữ chỗ. **Chỉ ra:** báo lỗi `PASSENGER_RULES_VIOLATED` ngay dưới đúng ô (BR-13).

### D4: hai khách tranh ghế cuối

1. Admin vào A-05 `/admin/flights/new`, tạo chuyến lẻ HAN → DAD hôm nay + 25, hạng phổ thông **1 ghế**.
2. Hai cửa sổ (thường và ẩn danh), hai khách khác nhau cùng chọn chuyến đó, cùng khai xong C-07.
3. Bấm **Giữ chỗ** ở hai cửa sổ gần như cùng lúc.
4. **Chỉ ra:** một bên vào C-09. Bên kia quay về C-02 với thông báo hết chỗ (`SEATS_UNAVAILABLE`). A-06 hiện ghế còn 0, không âm. Giữ ghế là một câu `UPDATE` nguyên tử (Q-01), có integration test 20 luồng đặt cùng lúc.

### D6: đổi chiều về sang ngày khác

1. Mở booking khứ hồi của D1, ở chiều về bấm **Đổi chiều này**.
2. Chọn ngày khác và chuyến mới. **Chỉ ra:** số tiền phải trả = phí đổi của gói + chênh lệch giá (BR-72).
3. Xác nhận, thanh toán qua VNPay.
4. **Chỉ ra:** chặng cũ `REPLACED`, chặng mới `ACTIVE` với số vé mới, Mailpit có email đổi chuyến.

### D7: hoàn chiều đi, Staff duyệt

1. Khách mở booking của D1, ở chiều đi bấm **Hoàn chiều này**. **Chỉ ra:** số tiền hoàn = giá trị chiều − phí hoàn của gói (BR-61). Gửi yêu cầu.
2. Đăng nhập `staff@demo.local`, vào S-03 `/staff/refunds`, mở yêu cầu, bấm **Duyệt**.
3. **Chỉ ra:** backend gọi VNPay Refund API, yêu cầu chuyển `COMPLETED`, ghế được trả, Mailpit có email hoàn tiền.
4. Nếu sandbox từ chối hoàn: yêu cầu ở trạng thái lỗi, có nút **Thử lại** (chỉ hoàn phần còn thiếu) và **Xác nhận hoàn thủ công** (FR-71).

### D8: Admin huỷ chuyến có booking

1. Chuẩn bị: hai khách đặt cùng một chuyến do Admin tạo (như D4 nhưng 50 ghế). Khách thứ nhất thanh toán, khách thứ hai chỉ giữ chỗ với `WELCOME10`.
2. Admin mở A-06 của chuyến, bấm **Huỷ chuyến**, đọc cảnh báo, bấm **Xác nhận huỷ chuyến**.
3. **Chỉ ra:**
   - booking chờ thanh toán chuyển "Đã huỷ", lượt voucher được trả;
   - booking đã xuất vé tự có yêu cầu hoàn nguồn `FLIGHT_CANCELLED`, phí 0, hoàn 100%, hiện ở S-03;
   - Mailpit có email "chuyến bị huỷ";
   - chuyến không còn trong kết quả tìm kiếm.

### D5 (bước 2): booking hết hạn

1. Mở lại booking của bước 1 (đã quá 15 phút giữ chỗ + 5 phút ân hạn).
2. **Chỉ ra:** trạng thái "Hết hạn giữ chỗ". Job chạy mỗi 60 giây đã trả ghế và lượt voucher (BR-45).

### D9: đổi thời gian giữ chỗ

1. Admin vào A-09 `/admin/settings`, sửa "Thời gian giữ chỗ" từ 15 lên 20. Thử nhập 70 trước: bị từ chối vì ngoài khoảng 5–60.
2. Khách tạo một booking mới. **Chỉ ra:** đếm ngược 20 phút. Booking tạo trước đó vẫn giữ hạn cũ (BR-25: tham số đọc lúc tạo giao dịch).
3. Đặt lại giá trị 15.

### D10: báo cáo

1. Admin vào A-10 `/admin/reports`, kỳ là tháng này.
2. **Chỉ ra:**
   - **Dòng tiền:** tổng thu bằng tổng số đã trả ở D1, D2, D6, D8; tổng hoàn bằng số đã hoàn ở D7; doanh thu ròng = thu − hoàn;
   - **Doanh số theo hãng:** chỉ đếm vé của chặng đang `ACTIVE`, nên vé đã đổi hoặc đã hoàn không còn tính;
   - **Tỉ lệ lấp đầy:** chuyến vừa đặt có tỉ lệ tăng.

### D11: phân quyền

1. Đăng nhập khách, mở DevTools và gõ `await fetch("/api/admin/airports").then(r => r.status)`. **Chỉ ra:** `403`.
2. Lấy mã booking của khách khác (VD booking của D1), mở `/bookings/{mã}` bằng tài khoản `customer@demo.local`. **Chỉ ra:** "Không tìm thấy đặt chỗ." (API trả 404, không phải 403, để không lộ việc mã đó có tồn tại).
3. Staff mở `/admin/users`: báo không có quyền. Admin mở `/staff/bookings`: vào được (Admin kế thừa quyền Staff).

---

## 4. Bằng chứng tự động

Chạy trên stack Docker ở trên (frontend ở cổng 3000, cần `VNPAY_HASH_SECRET` trong `.env` để ký Return URL):

```bash
cd frontend
npx playwright install chromium     # lần đầu
E2E_BASE_URL=http://localhost:3000 npm run e2e
node scripts/perf.mjs               # NFR-01
```

| Kịch bản                     | Spec                                                          |
| ---------------------------- | ------------------------------------------------------------- |
| D1, D2, D4, D8, D9, D10, D11 | `e2e/demo.spec.ts` (project `demo`, chạy sau cùng và tuần tự) |
| D3                           | `e2e/booking.spec.ts`, `e2e/flight-search.spec.ts`            |
| D5                           | `e2e/booking.spec.ts`                                         |
| D6                           | `e2e/aftersales.spec.ts`                                      |
| D7                           | `e2e/aftersales.spec.ts`, `e2e/staff.spec.ts`                 |

NFR-01 đo ngày 2026-10-26 trên máy dev, stack Docker, 200 lượt mỗi API: tìm kiếm có p95 khoảng 11–16 ms (ngưỡng 1.000 ms), các API khác có p95 dưới 13 ms (ngưỡng 500 ms).

NFR-08: `docker compose up -d --build` dựng cả 4 service, mỗi service có health check (`/actuator/health` cho backend, trang chủ cho frontend). OpenAPI ở http://localhost:8080/v3/api-docs, Swagger UI ở http://localhost:8080/swagger-ui.html (profile `dev`).
