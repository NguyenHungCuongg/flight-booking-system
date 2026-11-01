# Thiết kế: trợ lý đặt vé (agent) trên SkyLine

Ngày: 2026-10-26. Nhánh: `feature/flight-agent` (tách từ `develop`). Phạm vi: service Python mới trong `agent/` và trang `/assistant` ở frontend. Backend Java không đổi.

## 1. Vì sao

Ở môn Agentic (SE373), chủ dự án đã làm một agent đặt vé có harness: [mini-flight-booking-agent](https://github.com/NguyenHungCuongg/mini-flight-booking-agent), bản local ở `F:\UIT\Kì 7\Agentic\BTVN\demo-flight-agent`. Agent đó chỉ chạy trên một hãng bay giả (`World` trong `core.py`). Mục đích của phần này là đưa harness đó lên hệ thống thật để xem nó vận hành ra sao khi gọi API thật, với tiền và ghế thật.

Đây là thử nghiệm cá nhân, nằm ngoài phạm vi môn học và không được trình bày khi báo cáo đồ án. Vì vậy phần này nằm trên nhánh riêng, không sửa roadmap (`docs/superpowers/plans/2026-10-09-00-roadmap.md`) và không sửa các tài liệu PRD, TDD, APP_FLOW.

## 2. Mục tiêu, ngoài phạm vi, điều cần tránh

**Mục tiêu (MVP)**

- Khách đã đăng nhập gõ một yêu cầu bằng lời trên `/assistant`, ví dụ "Đặt giúp mình 1 vé SGN đi Đà Nẵng thứ 6 này, bay trước 12h, dưới 2 triệu". Agent tìm chuyến, lọc theo yêu cầu, hỏi phần thông tin hành khách còn thiếu, rồi đưa ra một thẻ duyệt. Khi khách bấm "Giữ chỗ", hệ thống mới giữ chỗ. Code đọc lại booking để xác nhận, sau đó đưa khách sang C-09 `/bookings/{code}` để tự thanh toán VNPay.
- Chỉ đặt **vé một chiều cho đúng một người lớn**, hạng phổ thông (`ECONOMY`), bay thẳng hoặc nối chuyến (theo kết quả của API tìm chuyến), không mua thêm hành lý, không dùng voucher.
- Bốn ý của harness cũ vẫn giữ trên API thật: ràng buộc là dữ liệu, kiểm quyền trước khi ghi, "xong" do code quyết định, và mọi lần dừng bất thường đều kết thúc bằng bàn giao.

**Ngoài phạm vi**

- Khứ hồi, nhiều hành khách, trẻ em và em bé, hạng ghế khác `ECONOMY`, hành lý, voucher.
- Chặng quốc tế: hệ thống bắt khai hộ chiếu (BR-13). Nếu báo giá trả lỗi về hộ chiếu thì agent bàn giao và mời khách đặt trên `/flights`.
- Thanh toán, huỷ, hoàn vé, đổi chuyến. Agent không có tool nào cho các việc này.
- Memory giữa các session (Challenge A), Plan-then-Execute, Hybrid, `evaluate.py` và 8 kịch bản mock.
- Stream câu trả lời theo từng token: mỗi lượt chat trả về một lần.
- Dùng cho Staff hoặc Admin. API booking chỉ cho `CUSTOMER`, nên tài khoản khác nhận 403 và agent bàn giao "Trợ lý chỉ dùng cho tài khoản khách hàng".
- Đưa vào CI hay `docker-compose.yml`: service chạy tay bằng `uv`.

**Điều cần tránh** (nếu xảy ra thì coi như hỏng, dù các phần khác chạy được)

- Có booking được tạo khi khách chưa bấm duyệt, hoặc booking tạo ra khác với thẻ khách đã duyệt (chuyến, gói giá, hành khách, giá).
- Agent báo "đã giữ chỗ" trong khi booking đọc lại từ hệ thống không ở trạng thái `PENDING_PAYMENT` hoặc không khớp ràng buộc.
- Làm mất tác dụng của CSRF. Service agent không bao giờ tự đọc cookie `XSRF-TOKEN` rồi đặt vào header `X-XSRF-TOKEN`, vì làm thế thì một trang lạ cũng giữ chỗ được thay khách (xem §5.3).
- App hiện tại bị ảnh hưởng khi service agent không chạy. Mọi trang khác vẫn chạy bình thường, chỉ `/assistant` báo "Trợ lý đang không hoạt động".
- Một lượt chat gọi model không giới hạn: mỗi lượt có ngân sách cứng (§4.5).

## 3. Flow đã chốt

Chủ dự án đã xem mockup và chọn flow "Agent giữ chỗ hộ, bạn tự thanh toán". Mockup nằm trong `.superpowers/brainstorm/` (bị gitignore, chỉ có trên máy).

1. **Khách gõ yêu cầu** trên `/assistant`. Trang này cần đăng nhập.
2. **Agent tách yêu cầu thành ràng buộc** bằng tool `set_constraints`, và trang hiện một thẻ "Ràng buộc": tuyến, ngày, giờ cất cánh tối đa, giá tối đa. Muốn sửa thì khách nhắn tiếp ("tăng giá lên 2,5 triệu"), agent gọi lại `set_constraints`. Thẻ không sửa trực tiếp được.
3. **Agent tìm chuyến** bằng tool `search_flights`, gọi API thật. Trang hiện danh sách các bước tool đã chạy dưới dạng chữ đơn cách, mỗi dòng gồm tên tool, tham số rút gọn và trạng thái.
4. **Agent hỏi thông tin còn thiếu:** họ tên, giới tính, ngày sinh của hành khách. Thông tin liên hệ thì code lấy từ `GET /api/me`, không hỏi khách.
5. **Thẻ duyệt.** Agent gọi tool `propose_hold`. Code kiểm quyền rồi gọi báo giá thật. Nếu qua được cả hai bước, trang hiện thẻ gồm chuyến, giờ, gói giá, hành khách đã chuẩn hoá tên, liên hệ và tổng tiền, kèm hai nút "Giữ chỗ" và "Sửa". Bấm "Sửa" chỉ đóng thẻ, khách nhắn tiếp để sửa.
6. **Giữ chỗ và xác nhận.** Khi khách bấm "Giữ chỗ", code (không phải model) gửi đúng body đã duyệt tới `POST /api/bookings`, rồi đọc lại `GET /api/bookings/{code}`. Nếu đọc lại đạt tiêu chí hoàn thành (§4.4), trang hiện thẻ "Đã giữ chỗ {code}, hết hạn lúc {giờ}" với nút "Thanh toán VNPay" dẫn tới `/bookings/{code}`.
7. **Bàn giao** khi harness dừng lượt chat (§4.6): đã làm tới đâu, đã thử gì, và đúng một câu hỏi. Mockup có các nút trả lời nhanh, nhưng MVP chỉ hiện câu hỏi, khách gõ câu trả lời (lựa chọn của người viết spec).

## 4. Agent (Python, `agent/`)

### 4.1 Lấy gì từ repo cũ

Copy repo mini agent vào `agent/` trong repo này. Không dùng submodule.

| Repo cũ                                                                                        | Trong `agent/`                                                                                                                                       |
| ---------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `core.py`: `Constraints`, `Harness`                                                            | Giữ khung: `before_model`/`after_model` (ngân sách), `is_looping`, `execute`, `check_permission`, `is_done`, `handoff`. Đổi nội dung theo §4.3–§4.6. |
| `core.py`: `World`, `SCENARIOS`, `RuleApprover`, `HumanApprover`, `Plan`, `Step`               | Bỏ. `World` được thay bằng client gọi API thật (`httpx`). Người duyệt bây giờ là nút "Giữ chỗ" trên web.                                             |
| `react.py`                                                                                     | Giữ cách dùng `create_agent` cùng middleware `wrap_model_call`/`wrap_tool_call`. Prompt hệ thống viết lại bằng tiếng Việt.                           |
| `model.py`                                                                                     | Giữ nguyên, kể cả nhánh Gemini.                                                                                                                      |
| `run.py` (`INFRA_ERRORS`)                                                                      | Giữ cách xếp 429, timeout và 5xx của LLM thành lỗi hạ tầng (bàn giao "Thử lại sau"). Hàm `run()` được thay bằng hàm xử lý một lượt chat.             |
| `plan_execute.py`, `hybrid.py`, `evaluate.py`, `demo.py`, `memory*.py`, `results/`, các REPORT | Bỏ.                                                                                                                                                  |
| `tests/test_run.py` (`ScriptedModel`)                                                          | Giữ cách test: model giả phát lại kịch bản có sẵn, test chỉ kiểm kết quả. Thêm backend giả bằng `httpx.MockTransport`.                               |
| `pyproject.toml`, `uv.lock`                                                                    | Giữ, thêm `fastapi`, `uvicorn` và đưa `httpx` lên dependency chính. Bỏ phụ thuộc không còn dùng.                                                     |

### 4.2 Ràng buộc

`Constraints` gồm `origin`, `destination` (mã IATA), `date` (ngày cất cánh theo giờ sân bay đi), `depart_before` (`HH:MM`, có thể để trống) và `max_price` (VND, có thể để trống). Trống nghĩa là không giới hạn. Số khách luôn là 1 người lớn và không phải là ràng buộc.

- Model đặt ràng buộc qua tool `set_constraints`. Code kiểm: mã sân bay phải có trong `GET /api/airports`, nếu sai thì trả `invalid_param` kèm gợi ý. Ngày không được ở quá khứ. Mỗi lần đặt thành công thì trang hiện lại thẻ "Ràng buộc".
- Prompt hệ thống phải có ngày hôm nay theo giờ `Asia/Ho_Chi_Minh`, do code chèn vào, để model hiểu được "thứ 6 này".
- `is_ok(itinerary, fare)`: sân bay đi của chặng đầu là `origin`, sân bay đến của chặng cuối là `destination`, ngày và giờ cất cánh của chặng đầu (giờ địa phương trong `departure.time`) khớp `date` và sớm hơn `depart_before`, giá ≤ `max_price`. Đây là quy tắc duy nhất, dùng chung cho kiểm quyền và tiêu chí hoàn thành, giống `Constraints.is_ok()` cũ.

### 4.3 Tool của model

Model chỉ có ba tool. Không có tool nào giữ chỗ, thanh toán hay huỷ.

| Tool                                                                         | Làm gì                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `set_constraints(origin, destination, date, depart_before?, max_price?)`     | Đặt hoặc thay ràng buộc (§4.2).                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `search_flights()`                                                           | Gọi `GET /api/flights/search?from&to&date&adults=1&cabin=ECONOMY` theo ràng buộc hiện tại. Kết quả trả cho model là bản rút gọn: mỗi hành trình gồm `flightIds`, số hiệu, giờ đi và đến, số điểm dừng, và gói giá rẻ nhất (`fareFamilyId`, tên, `totalPrice`, `seatsLeft`). Kết quả được chia hai phần: `matching` là các hành trình thoả `is_ok`, xếp theo giá tăng dần; `nearest` là tối đa 2 hành trình rẻ nhất chỉ vi phạm một ràng buộc. Harness giữ lại kết quả tìm gần nhất để kiểm quyền. |
| `propose_hold(flight_ids, fare_family_id, full_name, gender, date_of_birth)` | Tạo thẻ duyệt (§4.4). Model không biết và không cần biết lúc duyệt xảy ra chuyện gì, kết quả chỉ trả về `pending_approval`.                                                                                                                                                                                                                                                                                                                                                                       |

Mọi kết quả tool đều có `status` (`ok`, `invalid_param`, `denied`, `not_found`, `backend_error`) và kèm `hint` khi lỗi, giống quy ước quan sát của repo cũ. Lỗi `ProblemDetail` của backend chuyển thành `backend_error`, với `code` và `detail` (tiếng Việt) lấy nguyên từ backend.

### 4.4 Kiểm quyền, duyệt và tiêu chí hoàn thành

**Kiểm quyền (`check_permission`), chạy trước mỗi tool:**

- `search_flights` và `propose_hold` bị chặn nếu chưa có ràng buộc.
- `propose_hold` bị chặn nếu cặp `flight_ids` và `fare_family_id` không có trong kết quả tìm gần nhất, hoặc không thoả `is_ok`.

**`propose_hold` khi đã qua kiểm quyền:**

1. Code chuẩn hoá tên theo BR-12: bỏ dấu (NFD, bỏ dấu kết hợp, `đ`/`Đ` thành `D`), viết in hoa, gộp khoảng trắng. Từ đầu là họ (`lastName`), phần còn lại là tên đệm và tên (`firstName`). Nếu chỉ có một từ thì trả `invalid_param` và yêu cầu nhập đủ họ tên. Ví dụ: "Nguyễn Hùng Cường" thành `NGUYEN` / `HUNG CUONG`.
2. Code lấy `contact` (`fullName`, `email`, `phone`) từ `GET /api/me`.
3. Code dựng `BookingRequest`: một `JourneyRequest` với `OUTBOUND`, `flight_ids`, `fare_family_id`; một `PassengerRequest` loại `ADULT`; `baggage` rỗng; không có `voucherCode`. Sau đó gọi `POST /api/bookings/quote`. Các quy tắc như tuổi (BR-10) hay chuyến sắp bay (BR-01) để backend kiểm; lỗi trả về được chuyển cho model ở dạng `backend_error`.
4. Nếu `totalAmount` của báo giá lớn hơn `max_price` thì `denied`.
5. Nếu qua hết, code lưu một **đề xuất** (`proposalId`, body đã dựng, kết quả báo giá) vào chat, trả `pending_approval` cho model và gắn thẻ duyệt vào câu trả lời của lượt này. Mỗi lúc chỉ có một đề xuất còn hiệu lực; đề xuất mới thay đề xuất cũ.

**Duyệt:** khi khách bấm "Giữ chỗ", frontend gọi endpoint duyệt (§5.1). Code gửi **đúng body đã lưu** tới `POST /api/bookings`, không gọi model. Mỗi đề xuất chỉ duyệt được một lần.

**Tiêu chí hoàn thành (`is_done`):** code đọc lại `GET /api/bookings/{code}`. Booking được coi là xong khi đủ các điều kiện sau:

- `status = PENDING_PAYMENT`;
- có đúng một hành trình `OUTBOUND`, với các `flightId` trùng với đề xuất;
- có đúng một hành khách;
- `totalAmount` trùng với báo giá;
- hành trình thoả `is_ok`.

Chỉ khi đó trang mới hiện thẻ "Đã giữ chỗ". Câu trả lời của model không bao giờ được tính.

**Giữ chỗ thất bại sau khi duyệt** (ví dụ hết ghế giữa lúc báo giá và lúc giữ): lỗi được ghi vào lịch sử chat như một quan sát, rồi agent được chạy thêm một lượt để đề xuất chuyến khác. Đề xuất mới lại phải được duyệt. Đây là kịch bản `sold_out` của repo cũ.

### 4.5 Ngân sách và phát hiện lặp

- Ngân sách: tối đa 10 lần gọi model **mỗi lượt chat**, giữ `BUDGET` cũ.
- Phát hiện lặp: cùng một cặp (tool, args) xuất hiện 3 lần trong 6 lời gọi gần nhất thì dừng, giữ nguyên như cũ.

### 4.6 Bàn giao

Lượt chat bình thường kết thúc bằng câu trả lời của model, có thể là một câu hỏi như "Cho mình ngày sinh nhé". Đó không phải bàn giao. Bàn giao là thẻ do **code** tạo khi harness dừng lượt chat:

| Lý do dừng                                      | Câu hỏi (một câu)                                                |
| ----------------------------------------------- | ---------------------------------------------------------------- |
| `search_flights` không có hành trình `matching` | Nêu hành trình gần nhất trong `nearest` và hỏi nới ràng buộc nào |
| Hết ngân sách / lặp                             | Hỏi thử lại hay tự đặt trên `/flights`                           |
| Lỗi hạ tầng của LLM                             | Hỏi thử lại sau                                                  |
| Backend trả 401                                 | Mời đăng nhập lại                                                |
| Backend trả 403                                 | Báo trợ lý chỉ dành cho tài khoản khách hàng                     |
| Báo giá báo cần hộ chiếu                        | Mời đặt chặng quốc tế trên `/flights`                            |

Mỗi thẻ gồm `done_so_far`, `tried` và `question`, giống `Harness.handoff()` cũ. Chữ hiển thị là tiếng Việt.

## 5. Ghép vào hệ thống

### 5.1 Đường đi của request

```
Trình duyệt ──/agent/*──▶ Next.js (rewrite) ──▶ agent (FastAPI, AGENT_URL) ──/api/*──▶ Spring (BACKEND_URL)
          └──/api/*───▶ Next.js (rewrite) ──▶ Spring   (như hiện nay)
```

Endpoint của service (do người làm tự chọn tên, nhưng phải đủ ba việc):

- tạo chat;
- gửi một tin nhắn, nhận về câu trả lời, danh sách bước tool và thẻ nếu có (ràng buộc, duyệt, đã giữ chỗ, bàn giao);
- duyệt một đề xuất, nhận về thẻ "Đã giữ chỗ" hoặc câu trả lời của lượt chạy thêm (§4.4).

Lịch sử chat và đề xuất nằm **trong bộ nhớ** của service, mất khi khởi động lại; với một thử nghiệm thì chấp nhận được. Mỗi chat gắn với giá trị cookie `JSESSIONID` lúc tạo. Request có cookie khác thì trả 404.

### 5.2 Phiên đăng nhập

Service gọi backend thay mặt khách: chuyển tiếp header `Cookie` mà trình duyệt gửi qua rewrite, gồm `JSESSIONID` và `XSRF-TOKEN`. Service không có tài khoản riêng và không lưu mật khẩu.

### 5.3 CSRF

Backend dùng `csrf.spa()`: request ghi phải có header `X-XSRF-TOKEN` trùng với cookie `XSRF-TOKEN`. Với các request ghi tới `/agent/*`, frontend phải tự gửi `X-XSRF-TOKEN`, dùng lại cách lấy token trong `frontend/src/lib/api.ts`. Service **chuyển tiếp nguyên header đó** khi gọi các API ghi của backend (báo giá, giữ chỗ). Thiếu header thì backend trả 403, và đó là hành vi đúng.

### 5.4 Frontend

- Thêm rewrite trong `frontend/next.config.ts`: `/agent/:path*` tới `${AGENT_URL ?? "http://localhost:8000"}/agent/:path*`, đặt cạnh rewrite `/api` hiện có.
- Trang `frontend/src/app/assistant/` là Client Component dạng chat: khung tin nhắn, danh sách bước tool và bốn loại thẻ ở §3. Giao diện theo `DESIGN.md`: token màu theo vai trò, `card`/`card-hi` (thẻ duyệt là `card-hi`), nút `pillPrimary`/`pillLine` từ `components/form.tsx`.
- Thêm `/assistant/:path*` vào `matcher` của `frontend/src/proxy.ts`, thêm `/assistant` vào danh sách `PROTECTED` của `e2e/smoke.spec.ts`, và thêm link "Trợ lý đặt vé" vào nav khách hàng trong `components/site-header.tsx`.
- Kiểu dữ liệu của API agent viết tay trong thư mục trang. API này không có trong OpenAPI của Spring nên không nằm trong `api-types.ts`.
- Nếu service không chạy (rewrite trả lỗi 5xx hoặc không kết nối được) thì trang hiện "Trợ lý đang không hoạt động", không crash.
- Ràng buộc của Cache Components vẫn áp dụng: không gọi `new Date()` hay `Date.now()` lúc render. Giờ hết hạn giữ chỗ lấy từ `holdExpiresAt` của backend.

### 5.5 Chạy

- `agent/.env` (bị gitignore) và `agent/.env.example`: `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL` như repo cũ, thêm `BACKEND_URL` (mặc định `http://localhost:8080`).
- Lệnh chạy: `cd agent && uv sync && uv run uvicorn …` trên cổng 8000. `agent/README.md` ngắn: cách chạy, cách test.
- Trên nhánh này, thêm một dòng về `agent/` vào phần Commands của `CLAUDE.md`. Roadmap giữ nguyên.

## 6. Kiểm chứng

**Test offline** (`cd agent && uv run pytest`): model giả kiểu `ScriptedModel` cộng với backend giả bằng `httpx.MockTransport`, không gọi mạng. Mỗi ý dưới đây có ít nhất một test:

1. `propose_hold` cho chuyến vi phạm ràng buộc, hoặc chuyến không có trong kết quả tìm, bị `denied` và không có thẻ duyệt.
2. `propose_hold` trước `set_constraints` bị `denied`.
3. Model không có tool nào gọi `POST /api/bookings`. Chỉ endpoint duyệt mới gọi, và body gửi đi giống hệt body đã lưu.
4. Duyệt cùng một đề xuất hai lần chỉ tạo một booking.
5. Đọc lại booking không khớp (sai trạng thái, sai chuyến, sai tổng tiền) thì không hiện "Đã giữ chỗ" mà hiện bàn giao.
6. Model trả lời "đã giữ chỗ xong" khi chưa có duyệt thì không có thẻ "Đã giữ chỗ".
7. Hết ngân sách, lặp, hoặc không có hành trình `matching` thì có thẻ bàn giao với đúng một câu hỏi.
8. Chuẩn hoá tên: "Nguyễn Hùng Cường" thành `NGUYEN` / `HUNG CUONG`, "Đặng Thị Ánh" thành `DANG` / `THI ANH`, một từ thì `invalid_param`.
9. Service chuyển tiếp nguyên `X-XSRF-TOKEN` của request đến và không bao giờ tự lấy từ cookie. Request không có header thì backend giả nhận request không có header.
10. Chat được tạo bởi một `JSESSIONID` thì trả 404 cho `JSESSIONID` khác.
11. Hết ghế khi duyệt thì agent được chạy thêm một lượt và có thể đề xuất chuyến khác (kịch bản `sold_out`).

**Frontend:** `npm run lint` và `npm run build` xanh. Smoke test có `/assistant`.

**Chạy thật** (một lần, bằng tay, với key LLM thật, backend profile `dev` và seed `V106` còn mới): đăng nhập `customer@demo.local`, gõ câu ví dụ ở §2 với một tuyến và ngày có trong seed. Agent phải đi hết §3 tới thẻ "Đã giữ chỗ", và `/bookings/{code}` hiện booking `PENDING_PAYMENT` có đồng hồ đếm ngược. Sau đó gõ một yêu cầu không có chuyến nào thoả để thấy thẻ bàn giao.

## 7. Để người làm tự quyết

- Bố cục file trong `agent/`, tên endpoint, dạng JSON của tin nhắn và thẻ.
- Lời prompt hệ thống, miễn là bằng tiếng Việt và bám các quy tắc ở §4.
- Chi tiết giao diện trong khuôn khổ `DESIGN.md`.
- Cách rút gọn bước tool khi hiển thị.
- Cách lưu lịch sử tin nhắn LangChain theo từng chat, và cách đưa lỗi giữ chỗ sau duyệt vào lịch sử để lượt chạy thêm đọc được (§4.4).
