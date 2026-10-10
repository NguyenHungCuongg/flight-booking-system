"use client";

import { useState } from "react";
import { HOLD_MINUTES } from "./data";
import { Icon } from "./icons";

const ITEMS = [
  [
    "Giữ chỗ được bao lâu?",
    `${HOLD_MINUTES} phút kể từ lúc giữ chỗ. Quá hạn mà chưa thanh toán, đặt chỗ tự huỷ và ghế được trả lại.`,
  ],
  [
    "Trẻ em và em bé tính giá thế nào?",
    "Trẻ em từ 2 đến dưới 12 tuổi trả 90% giá người lớn. Em bé dưới 2 tuổi trả 10% và ngồi cùng người lớn.",
  ],
  [
    "Bay quốc tế cần khai những gì?",
    "Quốc tịch, số hộ chiếu và ngày hết hạn của từng hành khách. Hộ chiếu phải còn hạn ít nhất 6 tháng tính từ ngày bay chặng cuối.",
  ],
  [
    "Khi nào tôi được đổi hoặc hoàn vé?",
    "Khi gói giá cho phép và còn ít nhất 24 giờ trước giờ cất cánh. Việc đổi, hoàn áp dụng cho cả chiều và mọi hành khách trong chiều đó.",
  ],
  [
    "Hãng đổi giờ hoặc huỷ chuyến thì sao?",
    "Bạn nhận email ngay khi có thay đổi. Nếu chuyến bị huỷ, SkyLine tự tạo yêu cầu hoàn 100% cho chiều bị ảnh hưởng.",
  ],
  [
    "Một lần đặt được bao nhiêu người?",
    "Tối đa 9 khách chiếm ghế. Mỗi người lớn đi kèm tối đa một em bé, em bé không chiếm ghế.",
  ],
];

export default function Faq() {
  const [open, setOpen] = useState<Record<number, boolean>>({ 0: true });

  return (
    <div className="faq">
      {ITEMS.map(([q, a], i) => (
        <div key={q} className={`faq-item${open[i] ? " is-open" : ""}`}>
          <h4>
            <button
              type="button"
              className="faq-q"
              aria-expanded={!!open[i]}
              aria-controls={`faq-a${i}`}
              onClick={() => setOpen((o) => ({ ...o, [i]: !o[i] }))}
            >
              <span>{q}</span>
              <span className="faq-ic" aria-hidden="true">
                <Icon name="plus" />
              </span>
            </button>
          </h4>
          {open[i] && (
            <div className="faq-a" id={`faq-a${i}`}>
              <p>{a}</p>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
