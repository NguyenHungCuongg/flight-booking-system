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

Chat nằm trong RAM của service. Trang giữ `chatId` trong `sessionStorage` và đọc lại bằng `GET /agent/chats/{id}` khi tải lại trang; restart service thì chat mất và trang mở chat trống.

Một booking có thể gồm nhiều người lớn, trẻ em và em bé (một chiều, hạng phổ thông). Số khách nằm trong ràng buộc và được gửi khi tìm chuyến, nên `max_price` là tổng tiền cả booking. Loại khách do code tính từ ngày sinh tại ngày bay (BR-10); số khách theo loại phải khớp ràng buộc thì mới báo giá. Thẻ duyệt hiện từng hành khách và giá từng loại khách, và `is_done` so từng hành khách của booking đọc lại với thẻ đã duyệt.

Khách muốn "đặt như lần trước" thì tool `list_recent_passengers` đọc hành khách (kể cả trẻ em) từ 3 booking gần nhất (chỉ GET, không trả số giấy tờ). Không có kho memory riêng: dữ liệu luôn lấy từ backend.

Khách hỏi về booking đã đặt thì ba tool chỉ đọc trả lời: `list_my_bookings` (10 booking gần nhất), `get_booking(code)` (trạng thái, hạn giữ chỗ, chặng, điều kiện hoàn đổi) và `refund_quote(code, direction)` (số tiền hoàn thật của một chiều). Mỗi tool gọi đúng một `GET`; mã booking được kiểm theo bảng chữ của backend trước khi ghép vào đường dẫn. Giờ và tiền trả về đã định dạng sẵn để model chép nguyên văn. Booking của người khác backend trả 404. Agent không huỷ, hoàn hay đổi vé, chỉ đưa link C-09, C-10, C-11 để khách tự làm.

Biến trong `.env`:

| Biến              | Ý nghĩa                                                       |
| ----------------- | ------------------------------------------------------------- |
| `OPENAI_API_KEY`  | API key của nhà cung cấp tương thích OpenAI                   |
| `OPENAI_BASE_URL` | Base URL của endpoint, gồm cả phần version (Gemini cũng được) |
| `OPENAI_MODEL`    | Mã model                                                      |
| `BACKEND_URL`     | Backend Spring, mặc định `http://localhost:8080`              |
