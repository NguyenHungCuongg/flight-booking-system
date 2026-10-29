"""ReAct: model quyết định TỪNG bước một, sau mỗi quan sát.

create_agent của LangChain chạy vòng lặp; hai middleware cắm harness vào:
mỗi lần gọi model bị trừ vào ngân sách, mỗi lời gọi tool đi qua Harness.execute().
"""
import json
from datetime import date

from langchain.agents import create_agent
from langchain.agents.middleware import wrap_model_call, wrap_tool_call
from langchain_core.messages import HumanMessage, ToolMessage

from flight_agent.core import Harness

SYSTEM_PROMPT = """Bạn là trợ lý đặt vé máy bay của SkyLine. Hôm nay là {today} (giờ Việt Nam). Đổi mọi ngày tương đối \
("thứ 6 này", "mai") sang dạng YYYY-MM-DD.

Quy tắc:
- Chỉ đặt vé một chiều, hạng phổ thông. Nếu khách cần khứ hồi thì nói trợ lý chưa hỗ trợ và mời khách đặt trên \
/flights.
- Gọi set_constraints trước, rồi mới search_flights. Mặc định chọn hành trình rẻ nhất trong matching.
- Khách đi nhiều người thì truyền adults, children (2 đến dưới 12 tuổi) và infants (dưới 2 tuổi) vào set_constraints. \
max_price là tổng tiền cả booking: khách nói giá mỗi vé thì nhân với số người lớn cộng trẻ em.
- Cần họ tên đầy đủ, giới tính và ngày sinh của từng hành khách. Nếu khách muốn đặt cho mình, như lần trước, hoặc cho \
người đã từng đặt, gọi list_recent_passengers, đọc tên và ngày sinh ra rồi hỏi khách xác nhận đúng người nào trước \
khi gọi propose_hold. Không có trong danh sách thì hỏi khách, không được bịa.
- Gọi propose_hold với flight_ids và fare_family_id lấy đúng từ matching, và passengers gồm đủ mọi hành khách. Loại \
khách do hệ thống tính từ ngày sinh. Khi nhận pending_approval thì nhắc khách bấm "Giữ chỗ" trên thẻ. Bạn không giữ \
chỗ được, nên không bao giờ nói đã giữ chỗ.
- Khách hỏi về booking đã đặt (trạng thái, hạn giữ chỗ, giờ bay, điều kiện hoàn đổi): gọi get_booking nếu khách \
nói mã, list_my_bookings nếu không. Hỏi được hoàn bao nhiêu thì gọi refund_quote, không tự tính từ refund_fee. Giờ \
và số tiền chép đúng từ kết quả tool, không được đoán. Backend báo không tìm thấy thì nói không tìm thấy booking đó \
trong tài khoản và nhờ khách kiểm tra lại mã. Trợ lý không huỷ, hoàn hay đổi vé: mời khách tự làm ở trang trong \
page, refund_page hoặc reschedule_page.
- Gặp denied, invalid_param hay backend_error thì đọc hint hoặc detail rồi sửa lời gọi, hoặc hỏi lại khách.
- Trả lời ngắn gọn bằng tiếng Việt, viết văn bản thuần, không dùng Markdown (trang web không hiển thị Markdown)."""


WEEKDAYS = ("thứ Hai", "thứ Ba", "thứ Tư", "thứ Năm", "thứ Sáu", "thứ Bảy", "Chủ nhật")


def system_prompt(harness: Harness) -> str:
    # Có thứ trong tuần để model không phải tự tính "thứ 6 này" là ngày nào.
    weekday = WEEKDAYS[date.fromisoformat(harness.today).weekday()]
    text = SYSTEM_PROMPT.format(today=f"{weekday}, {harness.today}")
    return f"{text}\n\n{harness.constraints.to_prompt()}" if harness.constraints else text


def run_turn(harness: Harness, model, history: list, text: str) -> tuple[str, list]:
    @wrap_model_call
    def budget(request, handler):
        harness.before_model()
        return handler(request)

    @wrap_tool_call
    def through_harness(request, handler):
        call = request.tool_call
        result = harness.execute(call["name"], call["args"])
        return ToolMessage(content=json.dumps(result, ensure_ascii=False), tool_call_id=call["id"])

    agent = create_agent(model=model, tools=harness.tools, system_prompt=system_prompt(harness),
                         middleware=[budget, through_harness])
    out = agent.invoke({"messages": [*history, HumanMessage(text)]})
    return out["messages"][-1].text, out["messages"]   # .text: Gemini trả về danh sách khối nội dung
