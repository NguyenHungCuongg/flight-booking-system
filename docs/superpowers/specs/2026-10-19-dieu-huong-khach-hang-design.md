# Thiết kế: điều hướng khu khách hàng và trang Tìm chuyến bay riêng

Ngày: 2026-10-19. Phạm vi: chỉ frontend (`frontend/`), không đổi API.

## 1. Vì sao

Người dùng bị lạc khi chuyển giữa các trang vì mỗi trang có header riêng và các link không thống nhất:

| Trang                                        | Header hiện tại                                                                           |
| -------------------------------------------- | ----------------------------------------------------------------------------------------- |
| `/` (C-01)                                   | `components/landing/nav.tsx`: Cách đặt vé, Hỗ trợ, Đặt chỗ của tôi, Tài khoản/Đăng nhập   |
| `/flights` (C-02)                            | `Brand` + nút "Đổi tìm kiếm" quay về `/`                                                  |
| `/checkout`, `/bookings`, `/bookings/[code]` | `components/customer-header.tsx`: Tìm chuyến bay (trỏ về `/`), Đặt chỗ của tôi, Tài khoản |
| `/account` (C-12)                            | Header riêng trong `account-view.tsx`: Đặt chỗ của tôi, nút Đăng xuất                     |

Ô tìm chuyến (`components/landing/search-dock.tsx`) chỉ có trên trang chủ. Vì vậy "Tìm chuyến bay" và "Đổi tìm kiếm" đều đưa người dùng về landing, phải cuộn qua hero mới tìm được.

Mục tiêu là điều hướng giống một app đặt vé chuyên nghiệp, tham khảo Traveloka: header thống nhất, và một trang tìm chuyến riêng đi thẳng vào việc.

## 2. Mục tiêu, ngoài phạm vi, điều cần tránh

**Mục tiêu**

- Mọi trang khách hàng (trừ `/checkout`) dùng chung một header, có đánh dấu mục đang mở.
- `/flights` là trang "Tìm chuyến bay" độc lập: tìm và sửa tìm kiếm ngay tại trang, không phải quay về trang chủ.
- Trang chủ vẫn giữ ô tìm chuyến để tìm nhanh. Bấm Tìm thì sang `/flights` như hiện nay.

**Ngoài phạm vi**

- Dải giá theo các ngày lân cận trên `/flights`: cần API mới ở backend, để sau.
- Các trang Staff/Admin (S-xx, A-xx): chưa được xây.
- Cột menu bên trái cho khu "của tôi": người dùng đã chọn không có.

**Điều cần tránh**

- Không có link nào trong khu khách hàng đưa người dùng về landing để tìm chuyến; nếu có thì coi như chưa xong.
- Không làm hỏng ràng buộc của Cache Components (CLAUDE.md, Frontend notes): không đọc `new Date()` hay `Date.now()` lúc render.

## 3. Thiết kế đã chốt

### 3.1 Header chung (thay cho `CustomerHeader` và header riêng của `/account`, `/flights`)

- Trái: `Brand` (logo, bấm về `/`).
- Giữa/phải: "Tìm chuyến bay" (`/flights`), "Đặt chỗ của tôi" (`/bookings`). Mục ứng với trang hiện tại được đánh dấu (in đậm hoặc gạch chân, theo DESIGN.md) và có `aria-current="page"`. `/bookings/[code]` tính là mục "Đặt chỗ của tôi".
- Ngoài cùng bên phải:
  - Đã đăng nhập: nút menu tên (tên gọi, tức từ cuối của `fullName`, kèm ▾). Bấm mở menu thả xuống gồm họ tên và email (chỉ hiển thị), "Đặt chỗ của tôi", "Tài khoản" (`/account`), "Đăng xuất". Staff/Admin có thêm "Trang làm việc" (`homeOf(role)` trong `lib/api.ts`). Menu đóng khi bấm ra ngoài, nhấn Esc hoặc chọn một mục, và dùng được bằng bàn phím.
  - Chưa đăng nhập: "Đăng nhập" (pill đen) và "Đăng ký" (pill viền).
  - Trạng thái đăng nhập lấy từ `GET /api/me`, giống `nav.tsx` hiện tại. 401 nghĩa là chưa đăng nhập.
- Đăng xuất: `POST /auth/logout`, xong thì chuyển về `/`. Hiện `/account` đang chuyển về `/login`; đổi luôn để thống nhất.
- Mobile: các link và menu tên gom vào một nút menu (giống `nav.tsx`).
- `/account` bỏ header riêng và nút Đăng xuất to (đăng xuất giờ nằm trong menu tên).

### 3.2 Trang chủ

Giữ navbar landing (trong suốt, đặc dần khi cuộn), nhưng các mục bên phải giống header chung: thêm "Tìm chuyến bay" (`/flights`) và thay link "Tài khoản" bằng menu tên ở §3.1. "Cách đặt vé" và "Hỗ trợ" (neo trong trang) giữ nguyên.

### 3.3 `/flights`: trang Tìm chuyến bay (phương án B, kiểu Traveloka)

Bố cục từ trên xuống:

1. Header chung, "Tìm chuyến bay" được đánh dấu.
2. **Dòng tóm tắt** điều kiện tìm: "TP.HCM (SGN) → Hà Nội (HAN)", ngày đi (và ngày về nếu có), số khách, hạng ghế, cùng nút "Đổi tìm kiếm". Bấm nút thì **thanh tìm kiếm đầy đủ** mở ra ngay dưới dòng tóm tắt. Thanh này có cùng trường và hành vi với ô tìm ở trang chủ, và được điền sẵn từ query string. Bấm Tìm thì `router.push('/flights?...')` rồi đóng thanh lại.
3. Khi URL không có điều kiện tìm (`hasQuery = false`): không có dòng tóm tắt, thanh tìm kiếm mở sẵn. Thay câu "Chưa có điều kiện tìm kiếm. Về trang chủ…" bằng một lời nhắc ngắn, không dẫn về `/`.
4. Phần còn lại giữ như hiện tại: bước Chiều đi/Chiều về, các thông báo, ô chiều đi đã chọn, và `ResultList` (bộ lọc bên trái, sắp xếp, danh sách). Các link "Tìm lại từ trang chủ" (ví dụ khi `badReturn`) đổi thành mở thanh tìm kiếm.

Tên trang (`metadata.title`) đổi thành "Tìm chuyến bay | SkyLine".

### 3.4 `/checkout`: header luồng đặt vé

Không dùng header chung. Thay bằng: `Brand` bên trái, bên phải là các bước "1. Chọn chuyến › 2. Thông tin hành khách › 3. Thanh toán", trong đó bước 2 được đánh dấu là bước hiện tại. Bước 1 là link quay lại `/flights` với đúng điều kiện tìm đã dùng, nếu dựng lại được từ lựa chọn trong `sessionStorage` (`lib/selection.ts`); nếu không thì về `/flights`. Bước 3 chỉ hiển thị, không bấm được. Lý do: khách đang nhập dữ liệu và có hạn giữ chỗ, ít lối thoát thì ít bỏ dở.

### 3.5 `/bookings` và `/bookings/[code]`

Chỉ thay `CustomerHeader` bằng header chung. Không thêm cột menu bên trái.

## 4. Ràng buộc kỹ thuật

- Đọc `frontend/node_modules/next/dist/docs/` trước khi viết code Next.js (Next 16, xem `frontend/AGENTS.md`).
- Thiết kế theo `DESIGN.md`: nút chính `#000d10`, line-height tính bằng px, `--spacing: 1px`.
- `search-dock.tsx` (≈870 dòng) phụ thuộc `SearchProvider` (`search-context.tsx`) và CSS `landing.css` (scope `.sk`). Muốn dùng lại trên `/flights` thì:
  - `SearchProvider` phải nhận được giá trị ban đầu từ URL. Hiện nó cứng HAN→SGN, ngày đi = hôm nay + 14.
  - Phải bọc trong `.sk` và import `landing.css`.
- Route mới hoặc đổi tên phải cập nhật `e2e/smoke.spec.ts`. Không thêm route mới.
- Chữ hiển thị và comment bằng tiếng Việt.

## 5. Để người làm tự quyết

- Dùng lại `SearchDock` nguyên trạng (thêm prop như `initial` hoặc `compact`) hay tách phần form ra khỏi phần trang trí của landing. Chọn cách ít đụng code nhất mà vẫn đúng giao diện.
- Header chung đặt ở đâu (ví dụ `components/site-header.tsx`), và có xoá `customer-header.tsx` không (có thì xoá luôn chỗ không còn dùng).
- Cách đánh dấu mục đang mở (`usePathname`).
- Hiệu ứng mở/đóng thanh tìm kiếm.

## 6. Các điểm nhỏ do Claude tự chốt (người dùng chưa xem)

- Các trang đăng nhập/đăng ký/quên mật khẩu (`AuthShell`) giữ nguyên header hiện tại.
- Header chung không dính (không sticky), giống các trang hiện tại.
- Trong lúc chờ `GET /api/me`, ô menu tên/Đăng nhập để trống nhưng vẫn giữ chỗ, để không nháy "Đăng nhập" rồi đổi thành tên.
- Footer không đổi.
- Bước 1 ở `/checkout`: dựng lại query từ `Selection` (sân bay và ngày lấy từ các chặng, hạng ghế lấy từ `fare.cabinClass`, số khách lấy từ `adults`/`children`/`infants`). Không cần thêm trường mới vào `sessionStorage`.
- Header chung trên mobile: không làm nút menu riêng vì chỉ có 2 link. Các link xuống dòng dưới logo (flex-wrap, như `CustomerHeader` hiện tại); thay cho câu "gom vào một nút menu" ở §3.1.
- Menu tên dùng Popover API của trình duyệt (`popover` + `popoverTarget`), nên tự có đóng khi bấm ra ngoài hoặc nhấn Esc. Menu nằm cố định ở góc phải trên.
- Staff/Admin: menu tên hiện "Trang làm việc" thay cho "Đặt chỗ của tôi", giống link ở `/account` hiện tại.

## 7. Kiểm tra

- `npm run lint`, `npm run build` qua.
- `npm run e2e` (smoke) qua.
- Kiểm tra tay bằng trình duyệt:
  - Đi hết vòng "/ → /flights → Đổi tìm kiếm → Tìm → chọn chuyến → /checkout → /bookings/[code] → menu tên → Tài khoản → Tìm chuyến bay" mà không lần nào bị đưa về landing ngoài ý muốn.
  - Mở `/flights` trống thì thấy thanh tìm kiếm mở sẵn.
  - Đăng xuất từ menu tên thì về `/` và header hiện Đăng nhập.
