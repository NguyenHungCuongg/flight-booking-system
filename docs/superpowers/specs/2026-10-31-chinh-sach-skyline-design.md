# Bộ chính sách SkyLine — thiết kế

|           |                                                                                                                                      |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| Ngày      | 2026-10-31                                                                                                                           |
| Nhánh     | `feature/chinh-sach`, tách từ `develop`                                                                                              |
| Liên quan | Bước 7 của [lộ trình mở rộng trợ lý](../plans/2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md) (trên nhánh `feature/flight-agent`) |

## 1. Vì sao làm

SkyLine mô phỏng một đại lý vé như Traveloka nhưng chưa có giấy tờ mà một trang bán vé thật luôn có. Chân trang chỉ trỏ tới các mục trên trang chủ (`#doi-hoan`, `#ho-tro`), và quy tắc hoàn, đổi đầy đủ chỉ nằm trong PRD (tài liệu nội bộ).

Giáo viên không chấm phần này. Chủ dự án làm vì muốn SkyLine đầy đủ như một trang thật, nên chuẩn "đủ" do chủ dự án đặt, lấy Traveloka làm mẫu.

Nhánh này bổ sung tài liệu để tra cứu thông tin về SkyLine cho **hai đối tượng đọc**:

- **Khách** đọc trên web.
- **Agent đặt vé** (Bước 7, làm sau trên `feature/flight-agent`) đọc chính các file đó, theo từng mục, để trả lời khách. Một nguồn duy nhất thì web và agent không bao giờ nói khác nhau.

## 2. Mục tiêu, ngoài phạm vi, điều cấm

**Mục tiêu.** Ba văn bản, mỗi văn bản một trang:

1. Chính sách quyền riêng tư
2. Điều khoản & điều kiện
3. Chính sách hoàn, đổi vé và hành lý

Thêm vào đó: một endpoint công khai trả về các tham số hệ thống mà khách cần biết, link tới ba trang từ chân trang và trang đăng ký.

**Ngoài phạm vi.**

- Mọi thay đổi ở `agent/`. Cách agent đọc file và tool tra cứu chính sách thuộc Bước 7.
- Sửa FAQ trên trang chủ để đọc tham số động. FAQ vẫn ghi cứng như hiện tại.
- Ô đánh dấu "Tôi đồng ý" có lưu xuống DB, và lưu phiên bản điều khoản khách đã đồng ý.
- Trang chính sách cookie, thanh toán, liên hệ hay khiếu nại riêng. Nội dung tương ứng nằm thành mục trong hai văn bản quyền riêng tư và điều khoản.
- Quy định hành lý của hãng (kích thước, vật phẩm cấm). SkyLine không có dữ liệu này.

**Điều cấm** (làm ra mà vướng một điều dưới đây là hỏng, dù trang vẫn chạy):

- **Văn bản nói sai về hệ thống.** Mọi câu phải đúng với code đang chạy trên `develop`: dữ liệu nào thật sự được thu, quy tắc nào thật sự được áp. Không chép mẫu chung chung, không hứa tính năng không có. Ví dụ: không có chức năng xoá tài khoản thì không viết "bạn có thể tự xoá tài khoản".
- **Tự đặt thêm quy tắc mới.** Phần hoàn và đổi viết lại từ BR trong [PRD §6](../../PRD.md), không thêm điều kiện hay mức phí nào PRD không có.
- **Mượn danh tính có thật.** Pháp nhân, địa chỉ, mã số thuế, hotline, email đều hư cấu và ghi rõ là mô phỏng. Không dùng tên, logo hay thông tin của công ty có thật. Email dùng tên miền `.example`.
- **Ghi cứng số mà Admin sửa được** (xem §4).

## 3. Nội dung ba văn bản

Viết bằng tiếng Việt, giọng văn bản pháp lý của một trang bán vé, nhưng câu ngắn, dễ đọc. Đầu mỗi văn bản có ngày hiệu lực và một câu ghi rõ đây là văn bản mô phỏng của đồ án môn học. Pháp nhân hư cấu: "Công ty TNHH SkyLine (pháp nhân mô phỏng)", kèm địa chỉ, mã số thuế, email hỗ trợ và hotline hư cấu. Thông tin pháp nhân xuất hiện giống nhau trong cả ba văn bản.

Mỗi mục bắt đầu bằng một heading `##`. Agent sẽ nạp văn bản theo từng mục, nên mỗi mục phải tự đứng được (đọc riêng vẫn hiểu) và tên mục nói rõ nội dung. Danh sách mục bên dưới là khung tối thiểu; người viết được thêm, tách hay đổi tên mục.

### 3.1 Chính sách quyền riêng tư

Phải khớp với dữ liệu thật trong `V1__init.sql` và frontend:

- **Dữ liệu thu thập.** Tài khoản: email, họ tên, số điện thoại, mật khẩu (chỉ lưu dạng băm BCrypt). Mỗi booking: họ tên, email, số điện thoại liên hệ. Mỗi hành khách: họ tên, giới tính, ngày sinh; chặng quốc tế có thêm quốc tịch, số hộ chiếu, ngày hết hạn. Lịch sử giao dịch thanh toán, hoàn tiền.
- **Mục đích sử dụng.** Giữ chỗ, xuất vé, gửi email giao dịch, hoàn và đổi, nhân viên hỗ trợ tra cứu (FR-70), báo cáo nội bộ.
- **Chia sẻ với bên thứ ba.** VNPay nhận thông tin cần cho giao dịch thanh toán và hoàn tiền: mã giao dịch, số tiền, địa chỉ IP (kiểm trong `Vnpay.java` xem gửi đúng những trường nào). SkyLine không nhận và không lưu số thẻ. Email được gửi qua nhà cung cấp SMTP.
- **Cookie và lưu trữ trên trình duyệt.** Cookie phiên đăng nhập (hết hạn sau 30 phút không hoạt động), cookie `XSRF-TOKEN` để chống giả mạo yêu cầu, `localStorage` lưu giao diện sáng/tối, `sessionStorage` lưu chuyến bay đang chọn. Không có cookie quảng cáo hay theo dõi. Kiểm lại danh sách bằng cách grep `localStorage`/`sessionStorage` trong `frontend/src`.
- **Quyền của khách.** Xem và sửa hồ sơ, đổi mật khẩu (FR-04). Các yêu cầu khác (trích xuất hay xoá dữ liệu) gửi qua email hỗ trợ, vì hệ thống chưa có chức năng tự làm. Được dẫn chiếu Nghị định 13/2023/NĐ-CP về bảo vệ dữ liệu cá nhân.
- **Bảo mật và lưu trữ**, **thay đổi chính sách**, **liên hệ**.

Trợ lý đặt vé (gửi nội dung chat tới Google Gemini) **không** được nhắc tới, vì `develop` không có trợ lý. Bước 7 thêm mục này trên `feature/flight-agent`.

### 3.2 Điều khoản & điều kiện

- **Vai trò của SkyLine.** Đại lý bán vé của nhiều hãng, không phải hãng vận chuyển. Lịch bay và giá do hãng cung cấp (trong đồ án là Admin nhập).
- **Tài khoản.** Cần tài khoản để đặt vé, quy tắc mật khẩu (BR-100), tài khoản bị khoá (BR-102).
- **Chuyến bay được bán.** Không bán chuyến cất cánh trong vòng `{{booking.min_hours_before_departure}}` giờ tới (BR-01); bay thẳng hoặc nối chuyến tối đa một điểm dừng, cùng hãng, thời gian nối trong khoảng tối thiểu và tối đa (BR-03).
- **Đặt chỗ và hành khách.** Loại khách theo tuổi, số khách tối đa, em bé đi kèm người lớn, quy cách họ tên, giấy tờ bay quốc tế (BR-10–14).
- **Giá và thanh toán.** Giá đã gồm thuế phí, giá trẻ em và em bé theo %, giá chốt lúc giữ chỗ, thời hạn giữ chỗ, thanh toán qua VNPay, thanh toán trễ hoặc trùng thì được hoàn tự động (BR-20–24, BR-40–46, BR-66).
- **Voucher** (BR-30–33).
- **Hoàn và đổi vé.** Tóm tắt một đoạn và dẫn link sang văn bản 3.3.
- **Hãng đổi giờ hoặc huỷ chuyến** (BR-83, BR-84).
- **Trách nhiệm và giới hạn trách nhiệm**, **luật áp dụng** (pháp luật Việt Nam), **liên hệ**.

### 3.3 Chính sách hoàn, đổi vé và hành lý

- **Điều kiện chung.** Hoàn và đổi theo cả chiều, cho mọi hành khách của chiều; 4 điều kiện của BR-51.
- **Hoàn vé.** Cách tính giá trị chiều, phí hoàn, số tiền được hoàn, quy trình nhân viên duyệt, hoàn qua VNPay, các trạng thái của yêu cầu (BR-52, BR-53, BR-60–66). Chuyến bị huỷ thì hoàn 100%.
- **Đổi chuyến.** Điều kiện chuyến mới, số tiền phải trả, chênh lệch không được hoàn, hạn thanh toán và trường hợp quá hạn (BR-70–75).
- **Điều kiện theo gói giá.** Phí hoàn, phí đổi, hành lý xách tay và ký gửi đi theo từng gói giá của từng hãng, và hiển thị khi chọn chuyến. Có một bảng _ví dụ minh hoạ_, ghi rõ là ví dụ, lấy vài dòng từ seed `V102`. Văn bản không cam kết số cụ thể của gói nào.
- **Hành lý mua thêm.** Mua theo từng hành khách, từng chiều, tối đa một mức; em bé không được mua (BR-22). Bảng giá theo hãng hiển thị lúc đặt vé.

## 4. Tham số động

PRD §7 có 8 tham số Admin sửa được, và cả 8 đều là quy tắc khách gặp phải: `booking.hold_minutes`, `booking.min_hours_before_departure`, `booking.max_seated_passengers`, `pricing.child_percent`, `pricing.infant_percent`, `aftersales.min_hours_before_departure`, `search.min_connection_minutes`, `search.max_connection_minutes`. Văn bản không ghi số mà ghi chỗ giữ chỗ, ví dụ `{{booking.hold_minutes}}`. Khi hiển thị, chỗ giữ chỗ được thay bằng giá trị lấy từ endpoint mới.

**Endpoint** `GET /api/settings/public` (cho phép gọi không đăng nhập):

- Nằm trong module `common`, đọc qua `SettingsApi`. Chỉ đọc.
- Trả 8 khoá trên, dạng `{ "booking.hold_minutes": 15, ... }`. Danh sách khoá công khai là danh sách cố định trong code, không trả cả bảng `system_settings`: sau này thêm tham số nội bộ thì nó không tự lộ ra ngoài.
- Thêm vào `permitAll` của `SecurityConfig`, thêm vào danh sách API của TDD và bảng hợp đồng (§6) của roadmap 21 plan, rồi chạy `npm run gen:api`.

Các hằng số không cấu hình được (5 phút chờ ở BR-45, hộ chiếu còn hạn 6 tháng, phiên hết hạn sau 30 phút) thì ghi thẳng vào văn bản.

Chỗ giữ chỗ trỏ tới khoá không có trong kết quả endpoint thì phải là lỗi phát hiện được, không được lặng lẽ hiện `{{...}}` cho khách.

## 5. Giao diện và cách hiển thị

**File nguồn.** `frontend/content/chinh-sach/{quyen-rieng-tu,dieu-khoan,hoan-doi-hanh-ly}.md`. Dùng Markdown thuần, có bảng; không dùng MDX vì agent cần đọc được file thô.

**Route.** `/chinh-sach/quyen-rieng-tu`, `/chinh-sach/dieu-khoan`, `/chinh-sach/hoan-doi-hanh-ly`, dùng chung một layout. `/chinh-sach` chuyển về văn bản đầu tiên. Slug không có trong danh sách thì báo 404.

**Bố cục** (tôi chọn, chủ dự án giao lại phần này): `SiteHeader` ở trên cùng. Màn rộng: cột trái liệt kê ba văn bản (văn bản đang mở được đánh dấu) và mục lục các heading `##` của văn bản đang mở; cột phải là nội dung, độ rộng dòng dễ đọc. Màn hẹp: danh sách văn bản thành hàng tab ngang trên đầu, mục lục ẩn. Mỗi heading có `id` để liên kết thẳng tới mục (ví dụ `/chinh-sach/hoan-doi-hanh-ly#doi-chuyen`). Kiểu chữ và màu theo `DESIGN.md`: role token, line-height viết bằng px, chạy đúng ở cả sáng lẫn tối.

**Hiển thị Markdown.** Dùng thư viện `marked`, gọi phía server nên không thêm gì vào bundle trình duyệt. Nội dung là file trong repo, không phải dữ liệu người dùng nhập, nên chèn HTML đã sinh vào trang là an toàn.

**Lấy tham số.** Server Component gọi thẳng backend qua `BACKEND_URL`, cùng giá trị mặc định với `next.config.ts`, vì rewrite `/api` chỉ áp dụng cho request từ trình duyệt. `next build` (kể cả trong Docker) phải chạy được khi backend tắt, nên lời gọi này chạy lúc có request, không chạy lúc build. Theo quy tắc Cache Components, phần này cần bọc trong `Suspense` hoặc dùng cách tương đương; đọc `frontend/node_modules/next/dist/docs/` trước khi viết. Backend không trả lời được thì trang hiện thông báo lỗi chung của app, không hiện văn bản thiếu số _(tôi chọn)_.

**Link vào trang.**

- Chân trang (`components/landing/footer.tsx`): thêm nhóm "Chính sách" gồm ba link. Link "Đổi và hoàn vé" ở nhóm Hỗ trợ đổi sang trỏ tới trang hoàn và đổi.
- Trang đăng ký: thêm dòng "Bằng việc đăng ký, bạn đồng ý với Điều khoản & điều kiện và Chính sách quyền riêng tư", có link tới hai trang.

**Docker.** `frontend/Dockerfile` đang `COPY . .` nên thư mục `content/` đã được đưa vào image. Kiểm lại `.dockerignore` để chắc thư mục này không bị loại.

## 6. Kiểm chứng

- **Backend.** Integration test (`@IntegrationTest`): gọi không đăng nhập được 200 và đúng 8 khoá, không thừa không thiếu; Admin sửa một tham số thì lần gọi sau trả giá trị mới.
- **Frontend.** Thêm ba route vào danh sách `PUBLIC` của `e2e/smoke.spec.ts`. Một test E2E: đổi `booking.hold_minutes` qua API admin thì trang hoàn và đổi hiện số mới, rồi trả lại giá trị cũ. Test này chạy cùng nhóm với các test khác cũng sửa tham số này (project `demo`) để không tranh nhau. Thêm một kiểm tra không cần trình duyệt: mọi `{{khoá}}` trong ba file đều nằm trong danh sách 8 khoá, và mỗi file có ít nhất một heading `##`.
- **Bằng tay.** Đọc hết ba văn bản trên web ở cả giao diện sáng và tối, trên màn rộng và màn hẹp. Đối chiếu từng câu ở mục 3.1 và 3.3 với code và BR.
- `./mvnw verify`, `npm run lint`, `npm run build` đều xanh.
- Cập nhật mục Status trong `CLAUDE.md`.

## 7. Phần để người làm tự quyết

Tên và cấu trúc hàm đọc và thay chỗ giữ chỗ; cách sinh `id` cho heading; chi tiết giao diện trong khuôn khổ §5 và `DESIGN.md`; câu chữ cụ thể của từng văn bản, miễn đúng §2 và §3; thông tin pháp nhân hư cấu cụ thể.
