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
] as const;

export default function Faq() {
  const [open, setOpen] = useState<Record<number, boolean>>({ 0: true });

  return (
    <div className="divide-y divide-line rounded-card border border-line bg-surface">
      {ITEMS.map(([q, a], i) => {
        const isOpen = !!open[i];
        return (
          <div key={q} className="px-20 py-4 sm:px-24">
            <h4>
              <button
                type="button"
                aria-expanded={isOpen}
                aria-controls={`faq-a${i}`}
                onClick={() => setOpen((o) => ({ ...o, [i]: !o[i] }))}
                className="flex w-full items-center justify-between gap-20 py-16 text-left text-nav font-bold text-ink transition-colors hover:text-accent"
              >
                <span>{q}</span>
                <span
                  aria-hidden="true"
                  className={`grid size-36 shrink-0 place-items-center rounded-inner border transition-all duration-200 ${
                    isOpen
                      ? "rotate-45 border-cta bg-cta text-cta-fg"
                      : "border-line-strong bg-paper text-ink"
                  }`}
                >
                  <Icon name="plus" className="size-18 shrink-0" />
                </span>
              </button>
            </h4>
            {isOpen && (
              <div
                id={`faq-a${i}`}
                className="pb-18 pr-40 text-body text-ink-2"
              >
                <p>{a}</p>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
