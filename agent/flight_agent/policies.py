"""Văn bản chính sách SkyLine cho trợ lý.

Cùng nguồn với trang /chinh-sach của frontend (frontend/content/chinh-sach/*.md). Trợ lý không nạp cả ba văn
bản vào prompt mà đọc theo mục: mục lục nằm trong system prompt, model gọi read_policy để đọc mục cần
(progressive disclosure, Buổi 7). Số Admin sửa được viết {{khoá}} và được điền từ /api/settings/public.
"""
import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path

# Cùng thứ tự và tên với POLICIES của frontend/src/lib/policies.ts.
DOCS = {
    "dieu-khoan": "Điều khoản & điều kiện",
    "quyen-rieng-tu": "Chính sách quyền riêng tư",
    "hoan-doi-hanh-ly": "Chính sách hoàn, đổi vé và hành lý",
}

POLICY_DIR = Path(__file__).resolve().parents[2] / "frontend" / "content" / "chinh-sach"

PLACEHOLDER = re.compile(r"\{\{([a-z_.]+)\}\}")


def heading_id(text: str) -> str:
    """Như headingId của frontend: "Đổi chuyến" → "doi-chuyen", để link trợ lý đưa ra nhảy đúng mục."""
    text = "".join(c for c in unicodedata.normalize("NFD", text.lower()) if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]+", "-", text.replace("đ", "d")).strip("-")


@dataclass(frozen=True)
class Section:
    key: str  # <slug>#<id>
    document: str
    title: str
    body: str

    @property
    def source(self) -> str:
        return "/chinh-sach/" + self.key


def load_sections(directory: Path = POLICY_DIR) -> dict[str, Section]:
    """Mỗi dòng "## " mở một mục tới dòng "## " kế tiếp; bỏ phần mở đầu. Đọc file mỗi lần gọi để sửa văn bản
    không cần khởi động lại service."""
    sections: dict[str, Section] = {}
    for slug, document in DOCS.items():
        parts = re.split(r"^## (.+)$", (directory / f"{slug}.md").read_text(encoding="utf-8"), flags=re.M)
        for title, body in zip(parts[1::2], parts[2::2]):
            key = f"{slug}#{heading_id(title.strip())}"
            sections[key] = Section(key, document, title.strip(), body.strip())
    return sections


def index(sections: dict[str, Section]) -> str:
    return "\n".join(f"- {s.key}: {s.title} ({s.document})" for s in sections.values())


def fill(text: str, settings: dict[str, int]) -> str:
    def value(m: re.Match) -> str:
        if m[1] not in settings:
            raise ValueError(f"Thiếu tham số {m[1]}")
        return str(settings[m[1]])

    return PLACEHOLDER.sub(value, text)
