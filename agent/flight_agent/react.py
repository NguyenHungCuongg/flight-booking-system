"""ReAct: model quyết định TỪNG bước một, sau mỗi quan sát.

create_agent của LangChain chạy vòng lặp; hai middleware cắm harness vào:
mỗi lần gọi model bị trừ vào ngân sách, mỗi lời gọi tool đi qua Harness.execute().
"""
import json
import re
import unicodedata
from datetime import date

from langchain.agents import create_agent
from langchain.agents.middleware import wrap_model_call, wrap_tool_call
from langchain_core.messages import HumanMessage, ToolMessage

from flight_agent.core import Harness
from flight_agent.policies import DOCS, index, load_sections

# Câu cố định khi không có dữ liệu, dùng chung cho prompt và eval (abstention, Buổi 6).
NO_INFO = "SkyLine không có thông tin về"
CLAIMS_HELD = ("đã giữ chỗ chuyến", "đã giữ chỗ cho", "đã giữ chỗ thành công", "đã giữ chỗ xong")
CITATION_RE = re.compile(r"/chinh-sach/([a-z0-9-]+(?:#[a-z0-9-]+)?)")
COMPACT_THRESHOLD = 300

SYSTEM_PROMPT = """Bạn là trợ lý đặt vé máy bay của SkyLine. Hôm nay là {today} (giờ Việt Nam). Đổi mọi ngày tương đối \
("thứ 6 này", "mai") sang dạng YYYY-MM-DD.

Quy tắc:
- Đặt vé một chiều hoặc khứ hồi (cabin mặc định ECONOMY, có thể chọn PREMIUM_ECONOMY, BUSINESS, FIRST). Có thể lọc \
theo hãng (airline), bay thẳng (nonstop), khung giờ (depart_before, depart_after), gói hoàn/đổi được (refundable, \
changeable), mua thêm hành lý ký gửi và dùng một mã giảm giá khách đưa. Khứ hồi thì truyền return_date vào \
set_constraints; depart_before và depart_after chỉ áp cho chiều đi. Khi khách sửa một yêu cầu nhỏ, chỉ cần truyền \
trường đổi vào set_constraints (hoặc truyền "NONE" để xoá giới hạn cũ).
- Gọi set_constraints trước, rồi mới search_flights. Mặc định chọn hành trình và gói giá rẻ nhất trong matching (mỗi \
hành trình có danh sách fares nếu khách muốn chọn gói khác như gói hoàn/đổi được).
- Khách đi nhiều người thì truyền adults, children (2 đến dưới 12 tuổi) và infants (dưới 2 tuổi) vào set_constraints. \
max_price là tổng tiền cả booking (số tiền khách trả: mọi hành khách, cả hai chiều nếu khứ hồi, gồm hành lý, sau \
giảm giá): khách nói giá mỗi vé thì nhân với số người lớn cộng trẻ em.
- Cần họ tên đầy đủ, giới tính và ngày sinh của từng hành khách. Nếu khách muốn đặt cho mình, như lần trước, hoặc cho \
người đã từng đặt, gọi list_recent_passengers, đọc tên và ngày sinh ra rồi hỏi khách xác nhận đúng người nào trước \
khi gọi propose_hold. Không có trong danh sách thì hỏi khách, không được bịa.
- Gọi propose_hold với flight_ids và fare_family_id lấy đúng từ matching, và passengers gồm đủ mọi hành khách. Khứ \
hồi thì thêm return_flight_ids và return_fare_family_id lấy từ return của cùng mục đó. Loại khách do hệ thống tính từ \
ngày sinh. Khi nhận pending_approval thì nhắc khách bấm "Giữ chỗ" trên thẻ. Kết quả có baggage_options thì hỏi \
luôn khách có muốn thêm hành lý ký gửi không, đọc các mức và giá (mỗi khách mỗi chiều); khách muốn thêm thì gọi lại \
propose_hold như cũ với baggage_kg. Khách đưa mã giảm giá thì truyền voucher_code, không tự bịa mã; backend báo mã \
không dùng được thì nói rõ với khách và hỏi có đặt không dùng mã không. Bạn không giữ chỗ được, nên không bao giờ nói \
đã giữ chỗ.
- Khách hỏi về booking đã đặt (trạng thái, hạn giữ chỗ, giờ bay, điều kiện hoàn đổi): gọi get_booking nếu khách \
nói mã, list_my_bookings nếu không. Hỏi được hoàn bao nhiêu thì gọi refund_quote, không tự tính từ refund_fee. Hỏi \
đổi sang ngày mới có chuyến nào và bù bao nhiêu thì gọi reschedule_options. Khách muốn huỷ một booking đang chờ \
thanh toán thì gọi propose_cancel để hiện thẻ duyệt. Giờ và số tiền chép đúng từ kết quả tool, không được đoán. \
Backend báo không tìm thấy thì nói không tìm thấy booking đó trong tài khoản và nhờ khách kiểm tra lại mã. Trợ lý \
không tự hoàn hay đổi vé: mời khách tự làm ở trang trong page, refund_page hoặc reschedule_page.
- Khách hỏi về chính sách, quy định hay điều kiện (hoàn, đổi, hành lý, giá trẻ em, giữ chỗ, thanh toán, dữ liệu cá \
nhân…): chỉ trả lời từ kết quả tool, không dùng hiểu biết riêng. Chính sách SkyLine: gọi read_policy với mã mục trong \
mục lục bên dưới. Gói giá, hành lý kèm gói, phí hoàn đổi và giá mua thêm hành lý của một hãng: gọi airline_rules. Bảng \
ví dụ gói giá trong văn bản hoàn đổi chỉ minh hoạ, không dùng làm số của hãng. Mỗi ý trả lời nêu nguồn: source của \
mục đã đọc (ví dụ /chinh-sach/hoan-doi-hanh-ly#dieu-kien-chung), hoặc "gói X của hãng Y". Điều khách hỏi mà kết \
quả tool không nói tới thì viết đúng nguyên văn câu "{no_info} <điều khách hỏi>." (không chèn chữ nào vào giữa, kể \
cả "chi tiết") rồi mời khách xem /chinh-sach/dieu-khoan hoặc hỏi hãng bay; không được đoán.
- Gặp denied, invalid_param hay backend_error thì đọc hint hoặc detail rồi sửa lời gọi, hoặc hỏi lại khách.
- Trả lời ngắn gọn bằng tiếng Việt, viết văn bản thuần, không dùng Markdown (trang web không hiển thị Markdown)."""


WEEKDAYS = ("thứ Hai", "thứ Ba", "thứ Tư", "thứ Năm", "thứ Sáu", "thứ Bảy", "Chủ nhật")


def system_prompt(harness: Harness) -> str:
    # Có thứ trong tuần để model không phải tự tính "thứ 6 này" là ngày nào.
    weekday = WEEKDAYS[date.fromisoformat(harness.today).weekday()]
    text = SYSTEM_PROMPT.format(today=f"{weekday}, {harness.today}", no_info=NO_INFO)
    text += "\n\nMục lục chính sách (mã mục: tên mục (văn bản)):\n" + index(load_sections())
    return f"{text}\n\n{harness.constraints.to_prompt()}" if harness.constraints else text


def compact_history(history: list) -> list:
    """Nén các ToolMessage dài của những lượt cũ (trước HumanMessage gần nhất), giữ nguyên lượt liền trước và tool_call_id."""
    last_human = max((i for i, m in enumerate(history) if isinstance(m, HumanMessage)), default=len(history))
    out = []
    for i, msg in enumerate(history):
        if i < last_human and isinstance(msg, ToolMessage) and len(str(msg.content)) > COMPACT_THRESHOLD:
            out.append(ToolMessage(content='{"status": "ok", "compacted": true}',
                                   tool_call_id=msg.tool_call_id, name=msg.name))
        else:
            out.append(msg)
    return out


def guard_reply(reply: str, harness: Harness) -> str:
    """Hậu kiểm câu trả lời ở runtime: làm sạch Markdown, sửa link chính sách chưa đọc và chặn báo giữ chỗ láo."""
    text = re.sub(r"\*\*(.+?)\*\*", r"\1", reply)
    text = re.sub(r"`([^`]+)`", r"\1", text)
    text = re.sub(r"(?m)^#{1,6}\s+", "", text)
    read = {k for t in harness.trace if t["tool"] == "read_policy" and t["status"] == "ok"
            for k in (t["args"].get("sections") or [])}

    def _fix_cite(m: re.Match) -> str:
        key = m.group(1)
        if key not in read and key not in DOCS:
            if key not in harness.bad_citations:
                harness.bad_citations.append(key)
            return "/chinh-sach/dieu-khoan"
        return m.group(0)

    text = CITATION_RE.sub(_fix_cite, text)
    norm = unicodedata.normalize("NFC", text).lower()
    if any(c in norm for c in CLAIMS_HELD) and not any(c.get("type") == "held" for c in harness.cards):
        text += " (Lưu ý: Hệ thống chưa giữ chỗ. Bạn cần bấm Giữ chỗ trên thẻ đề xuất để xác nhận.)"
    return text


def run_turn(harness: Harness, model, history: list, text: str) -> tuple[str, list]:
    @wrap_model_call
    def budget(request, handler):
        harness.before_model()
        res = handler(request)
        msgs = getattr(res, "result", None) or getattr(res, "messages", None)
        for m in (msgs if isinstance(msgs, list) else [res]):
            usage = getattr(m, "usage_metadata", None) or {}
            harness.tokens_used += int(usage.get("total_tokens", 0))
        return res

    @wrap_tool_call
    def through_harness(request, handler):
        call = request.tool_call
        result = harness.execute(call["name"], call["args"])
        return ToolMessage(content=json.dumps(result, ensure_ascii=False),
                           tool_call_id=call["id"], name=call["name"])

    agent = create_agent(model=model, tools=harness.tools, system_prompt=system_prompt(harness),
                         middleware=[budget, through_harness])
    out = agent.invoke({"messages": [*compact_history(history), HumanMessage(text)]})
    return guard_reply(out["messages"][-1].text, harness), out["messages"]   # .text: Gemini trả về danh sách khối nội dung
