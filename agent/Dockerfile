FROM ghcr.io/astral-sh/uv:python3.12-bookworm-slim
# Đặt mã nguồn ở /app/agent để policies.py (parents[2]/frontend/content/chinh-sach) đọc đúng thư mục chính sách được mount.
WORKDIR /app/agent
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project
COPY flight_agent flight_agent
EXPOSE 8000
CMD ["uv", "run", "--no-dev", "--no-sync", "uvicorn", "flight_agent.app:create_app", "--factory", "--host", "0.0.0.0", "--port", "8000"]
