# Trợ lý đặt vé (agent)

Service Python thử nghiệm: khách gõ yêu cầu bằng lời trên `/assistant`, agent tìm chuyến thật, hỏi thông tin hành khách và đưa ra thẻ duyệt; khi khách bấm "Giữ chỗ" thì code giữ chỗ và đọc lại booking. Thiết kế: [docs/superpowers/specs/2026-10-26-tro-ly-dat-ve-agent-design.md](../docs/superpowers/specs/2026-10-26-tro-ly-dat-ve-agent-design.md).

## Cài và test

```bash
cd agent
uv sync
uv run pytest
```

Test chạy offline: model giả phát lại kịch bản, backend Spring giả bằng `httpx.MockTransport`.

## Chạy

```bash
cp .env.example .env   # rồi điền key thật
uv run uvicorn flight_agent.app:create_app --factory --port 8000
```

Cần backend chạy profile `dev` trên cổng 8080 và frontend (`npm run dev`), frontend chuyển `/agent/*` tới `AGENT_URL` (mặc định `http://localhost:8000`).

Biến trong `.env`:

| Biến              | Ý nghĩa                                                       |
| ----------------- | ------------------------------------------------------------- |
| `OPENAI_API_KEY`  | API key của nhà cung cấp tương thích OpenAI                   |
| `OPENAI_BASE_URL` | Base URL của endpoint, gồm cả phần version (Gemini cũng được) |
| `OPENAI_MODEL`    | Mã model                                                      |
| `BACKEND_URL`     | Backend Spring, mặc định `http://localhost:8080`              |
