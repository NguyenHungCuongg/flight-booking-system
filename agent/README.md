# Trợ lý đặt vé (agent)

Service Python thử nghiệm: khách gõ yêu cầu bằng lời trên `/assistant`, agent tìm chuyến thật, hỏi thông tin hành khách và đưa ra thẻ duyệt; khi khách bấm "Giữ chỗ" thì code giữ chỗ và đọc lại booking. Thiết kế: [docs/superpowers/specs/2026-10-26-tro-ly-dat-ve-agent-design.md](../docs/superpowers/specs/2026-10-26-tro-ly-dat-ve-agent-design.md).

## Cài và test

```bash
cd agent
uv sync
uv run pytest
```

Test chạy offline: model giả phát lại kịch bản, backend Spring giả bằng `httpx.MockTransport`.

## Eval

```bash
uv run python -m evals.run              # mọi case, mỗi case 3 lần
uv run python -m evals.run --k 1 --case noi-gia
```

Gọi model thật (cần `.env` thật, tốn tiền LLM), backend vẫn là backend giả nên dữ liệu ổn định. Không chạy trong `uv run pytest`. Mỗi case chấm bằng code: ràng buộc tách ra, trạng thái cuối (thẻ duyệt đúng chuyến và hành khách, câu trả lời, hay bàn giao đúng lý do) và vài chuỗi phải có hoặc không được có trong câu trả lời. Case đạt khi đạt cả k lần. Lượt dừng vì lỗi API model (429 của free tier Gemini, 15 request/phút) được chạy lại sau 60 giây; vẫn lỗi thì không tính vào tỉ lệ và in ở dòng "Lỗi hạ tầng". Trace của lượt trượt nằm ở `evals/results/<thời điểm>/`. Thêm case vào `evals/cases.py`; số liệu mỗi lần chạy ghi ở §3 của [lộ trình](../docs/superpowers/plans/2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md).

## Chạy

```bash
cp .env.example .env   # rồi điền key thật
uv run uvicorn flight_agent.app:create_app --factory --port 8000
```

Cần backend chạy profile `dev` trên cổng 8080 và frontend (`npm run dev`), frontend chuyển `/agent/*` tới `AGENT_URL` (mặc định `http://localhost:8000`).

Endpoint gửi tin (`POST /agent/chats/{id}/messages`) trả `text/event-stream`: mỗi bước tool xong là một sự kiện `{"type": "step", "tool", "args", "status"}`, cuối cùng là `{"type": "turn", "turn": {reply, trace, cards}}`. Lượt hỏng vì lỗi không phải lỗi hạ tầng thì stream đóng mà không có `turn`, service ghi traceback. Response có `Cache-Control: no-transform` để lớp gzip của Next.js không gom các sự kiện lại.

Chat được lưu vào SQLite (`AGENT_DB_PATH`, mặc định `:memory:`, kèm TTL 24 giờ cho chat và 15 phút cho thẻ duyệt). Trang giữ `chatId` trong `sessionStorage` và đọc lại bằng `GET /agent/chats/{id}` khi tải lại trang, có nút "Cuộc trò chuyện mới" và nút "Dừng" khi đang xử lý.

Một booking có thể gồm nhiều người lớn, trẻ em và em bé, một chiều hoặc khứ hồi, chọn được hạng ghế (`ECONOMY`, `PREMIUM_ECONOMY`, `BUSINESS`, `FIRST`), gói giá hoàn/đổi được (`refundable`, `changeable`), lọc hãng (`airline`), bay thẳng (`nonstop`), khung giờ (`depart_before`, `depart_after`) và cập nhật lẻ từng ràng buộc. Hành lý ký gửi có thể tách riêng theo chiều (`baggage_kg`, `return_baggage_kg`).

Khách hỏi về booking đã đặt thì các tool chỉ đọc trả lời: `list_my_bookings`, `get_booking(code)`, `refund_quote(code, direction)` và `reschedule_options(code, direction, date)`. Khách muốn huỷ đặt chỗ đang chờ thanh toán (`PENDING_PAYMENT`) thì `propose_cancel(code)` tạo thẻ duyệt, khi bấm "Huỷ giữ chỗ" code gọi `POST /api/bookings/{code}/cancel` và đọc lại xác nhận trạng thái `CANCELLED`.

Khách hỏi về chính sách thì `read_policy(sections)` đọc tối đa 4 mục của ba văn bản `frontend/content/chinh-sach/*.md` (cùng nguồn với trang `/chinh-sach`). Mục lục (mã mục `<slug>#<id>`, tên mục, tên văn bản) nằm sẵn trong system prompt, model chỉ đọc mục cần thay vì nạp cả ba văn bản; các số Admin sửa được (`{{khoá}}`) điền từ `GET /api/settings/public`, và mỗi mục trả về kèm nguồn là link `/chinh-sach/<slug>#<id>` để trích dẫn. `airline_rules(airline)` trả các gói giá đang bán (hành lý kèm gói, phí hoàn, phí đổi) và giá mua thêm hành lý ký gửi của một hãng. Không có thông tin trong kết quả tool thì trợ lý nói đúng câu "SkyLine không có thông tin về …" rồi mời khách xem `/chinh-sach` hoặc hỏi hãng bay; eval chấm câu này và chấm mọi link trích dẫn phải là mục đã đọc trong lượt đó.

Biến trong `.env`:

| Biến              | Ý nghĩa                                                       |
| ----------------- | ------------------------------------------------------------- |
| `OPENAI_API_KEY`  | API key của nhà cung cấp tương thích OpenAI                   |
| `OPENAI_BASE_URL` | Base URL của endpoint, gồm cả phần version (Gemini cũng được) |
| `OPENAI_MODEL`    | Mã model                                                      |
| `BACKEND_URL`     | Backend Spring, mặc định `http://localhost:8080`              |
