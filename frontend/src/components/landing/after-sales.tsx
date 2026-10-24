"use client";

import Image from "next/image";
import { useState } from "react";
import { Icon } from "./icons";

const PANES = {
  doi: {
    tab: "Đổi chuyến",
    badge: "Đổi ngày bay trực tuyến",
    formula: "Phí đổi + chênh lệch giá vé",
    facts: [
      "Đổi trước giờ bay ít nhất 24 giờ, cùng hãng, cùng gói giá.",
      "Chuyến mới rẻ hơn thì phần chênh lệch không được hoàn.",
    ],
  },
  hoan: {
    tab: "Hoàn vé",
    badge: "Hoàn theo từng chiều bay",
    formula: "Giá trị chiều − phí hoàn",
    facts: [
      "Số tiền hoàn được chốt khi gửi yêu cầu, nhân viên duyệt rồi hoàn qua VNPay.",
      "Hãng huỷ chuyến thì bạn được hoàn 100%, không mất phí.",
    ],
  },
} as const;

export default function AfterSales() {
  const [tab, setTab] = useState<keyof typeof PANES>("doi");
  const pane = PANES[tab];

  return (
    <section
      id="doi-hoan"
      aria-labelledby="after-h"
      className="isolate overflow-hidden bg-forest-ink text-pure-white"
    >
      <div className="relative mx-auto max-w-page px-16 py-48 sm:px-38 sm:py-68 lg:py-96">
        {/* Nền: mạng đường bay render sẵn bằng Three.js, chuyến cũ nét đứt và chuyến mới kèm máy bay; bám theo khung nội dung để máy bay luôn nằm trên thẻ bên phải, hai mép mờ dần vào nền */}
        <Image
          src="/assets/images/after-sales-flight.webp"
          alt=""
          fill
          sizes="(min-width: 1400px) 1320px, 100vw"
          className="pointer-events-none -z-10 hidden object-cover object-bottom mask-x-from-85% lg:block"
        />
        <div className="grid gap-32 lg:grid-cols-12 lg:items-center lg:gap-48">
          {/* Left: Headline + Segmented Switch */}
          <div className="grid content-start gap-20 lg:col-span-6">
            <h2
              id="after-h"
              className="text-[clamp(28px,3.2vw,44px)] leading-[1.04] font-black tracking-[-0.028em] text-lime-voltage"
            >
              Đổi chuyến, hoàn vé. Tự làm trên web.
            </h2>
            <p className="max-w-[46ch] text-body text-linen-mist/90">
              Gói giá cho phép thì bạn tự đổi ngày bay hoặc gửi yêu cầu hoàn cho
              từng chiều, thấy trước phí và số tiền ngay trên màn hình đặt chỗ.
            </p>

            <div
              className="mt-4 flex flex-wrap gap-10"
              role="group"
              aria-label="Xem quy định"
            >
              {(Object.keys(PANES) as (keyof typeof PANES)[]).map((k) => {
                const active = tab === k;
                return (
                  <button
                    key={k}
                    type="button"
                    aria-pressed={active}
                    onClick={() => setTab(k)}
                    className={`min-h-42 rounded-inner border px-20 py-9 text-caption font-bold transition ${
                      active
                        ? "border-lime-voltage bg-lime-voltage text-forest-ink"
                        : "border-linen-mist/35 bg-transparent text-pure-white hover:border-lime-voltage"
                    }`}
                  >
                    {PANES[k].tab}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Right: Formula & Rule Card */}
          <div
            aria-live="polite"
            className="rounded-card border border-lime-voltage/25 bg-spruce/70 p-24 sm:p-32 lg:col-span-6"
          >
            <div key={tab} className="grid gap-18">
              <div className="grid gap-6 border-b border-linen-mist/15 pb-16">
                <span className="text-badge font-semibold text-linen-mist/80">
                  {pane.badge} · Công thức tính
                </span>
                <p className="text-[clamp(22px,2.2vw,30px)] leading-tight font-black tracking-tight text-lime-voltage">
                  {pane.formula}
                </p>
              </div>

              <ul className="grid gap-12">
                {pane.facts.map((f) => (
                  <li
                    key={f}
                    className="flex items-start gap-12 text-body text-pure-white/95"
                  >
                    <span className="mt-2 grid size-22 shrink-0 place-items-center rounded-inner bg-lime-voltage text-forest-ink">
                      <Icon name="check" className="size-14 shrink-0" />
                    </span>
                    <span>{f}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
