"""Ràng buộc, chuẩn hoá và harness của trợ lý đặt vé.

    Agent = Model + Harness

MODEL đề xuất lời gọi tool. HARNESS (file này) quyết định lời gọi có được chạy không, ghi lại,
dừng lượt chat, và dùng CODE để quyết định việc đã xong hay chưa.
"""
import re
import unicodedata
from dataclasses import dataclass

KEYS = ("depart_before", "max_price")


# =====================================================================
# 1. RÀNG BUỘC LÀ DỮ LIỆU
# =====================================================================
def _vnd(n: int) -> str:
    return f"{n:,}".replace(",", ".")


@dataclass(frozen=True)
class Constraints:
    origin: str
    destination: str
    date: str                           # ngày cất cánh theo giờ sân bay đi, YYYY-MM-DD
    depart_before: str | None = None    # HH:MM có số 0 ở đầu; None = không giới hạn
    max_price: int | None = None        # VND; None = không giới hạn

    def to_prompt(self) -> str:
        before = self.depart_before or "không giới hạn"
        price = f"{_vnd(self.max_price)} đ" if self.max_price is not None else "không giới hạn"
        return (f"Ràng buộc hiện tại: {self.origin} → {self.destination} ngày {self.date}, "
                f"cất cánh trước {before}, giá tối đa {price}.")

    def broken(self, origin: str, destination: str, departure: str, price: int) -> list[str]:
        """Tên các ràng buộc bị vi phạm. `departure` là giờ địa phương ISO có offset, vd 2026-10-30T08:10:00+07:00."""
        checks = [("origin", origin == self.origin),
                  ("destination", destination == self.destination),
                  ("date", departure[:10] == self.date),
                  ("depart_before", self.depart_before is None or departure[11:16] < self.depart_before),
                  ("max_price", self.max_price is None or price <= self.max_price)]
        return [name for name, ok in checks if not ok]

    def is_ok(self, origin: str, destination: str, departure: str, price: int) -> bool:
        """Quy tắc duy nhất, dùng chung cho kiểm quyền và tiêu chí hoàn thành."""
        return not self.broken(origin, destination, departure, price)


def normalise(key: str, value):
    """Trả (giá trị đã chuẩn hoá, None), hoặc (None, hint) nếu không đọc được."""
    if key not in KEYS:
        return None, f"Không có ràng buộc {key!r}. Chỉ dùng {', '.join(KEYS)}."
    raw = str(value).strip()
    if key == "depart_before":
        m = re.fullmatch(r"(\d{1,2}):(\d{2})", raw)
        if not m or int(m[1]) > 23 or int(m[2]) > 59:
            return None, "depart_before phải là giờ dạng HH:MM, ví dụ 09:00."
        return f"{int(m[1]):02d}:{m[2]}", None      # có số 0 ở đầu: Constraints.broken so sánh chuỗi
    if isinstance(value, float) and value.is_integer():
        raw = str(int(value))
    # Chữ số, có thể ngăn hàng nghìn ("2,000,000", "2.000.000"). Phần thập phân bị từ chối:
    # bỏ dấu "." của "2000000.0" sẽ thành 20.000.000.
    if not re.fullmatch(r"\d{1,3}([.,_]\d{3})+|\d+", raw) or (price := int(re.sub(r"\D", "", raw))) <= 0:
        return None, "max_price phải là số tiền VND dương, ví dụ 2000000."
    return price, None


def split_name(full_name: str) -> tuple[str, str] | None:
    """BR-12: bỏ dấu, in hoa, gộp khoảng trắng; từ đầu là họ, phần còn lại là tên đệm và tên."""
    text = full_name.replace("đ", "d").replace("Đ", "D")
    text = "".join(c for c in unicodedata.normalize("NFD", text) if unicodedata.category(c) != "Mn")
    words = re.sub(r"[^A-Z ]", "", text.upper()).split()
    return (words[0], " ".join(words[1:])) if len(words) >= 2 else None
