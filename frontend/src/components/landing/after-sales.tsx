"use client";

import Image from "next/image";
import { useState } from "react";
import { Icon } from "./icons";

const PANES = {
  doi: {
    tab: "Đổi chuyến",
    formula: "Phí đổi + chênh lệch giá vé",
    facts: [
      "Đổi trước giờ bay ít nhất 24 giờ, cùng hãng, cùng gói giá.",
      "Chuyến mới rẻ hơn thì phần chênh lệch không được hoàn.",
    ],
  },
  hoan: {
    tab: "Hoàn vé",
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
    <section className="after sec" id="doi-hoan" aria-labelledby="after-h">
      <div className="clay">
        <div className="clay-copy">
          <h2 id="after-h" className="clay-h rv">Đổi chuyến, hoàn vé. Tự làm trên web.</h2>
          <p className="clay-p rv rv-2">Gói giá cho phép thì bạn tự đổi ngày bay hoặc gửi yêu cầu hoàn cho từng chiều, thấy trước phí và số tiền.</p>
          <div className="clay-seg" role="group" aria-label="Xem quy định">
            {(Object.keys(PANES) as (keyof typeof PANES)[]).map((k) => (
              <button key={k} type="button" className={`clay-tab${tab === k ? " is-on" : ""}`} aria-pressed={tab === k} onClick={() => setTab(k)}>
                {PANES[k].tab}
              </button>
            ))}
          </div>
        </div>
        <div className="clay-right">
          <div className="eng-wrap">
            <Image className="eng-img" src="/assets/images/skyline-engine-wing.webp" width={1400} height={452}
              sizes="(min-width: 900px) 64vw, 116vw" alt="Cận cảnh động cơ và cánh máy bay sơn trắng" />
          </div>
          <div className="clay-panel" aria-live="polite">
            <div className="clay-pane" key={tab}>
              <p className="clay-formula">{pane.formula}</p>
              <ul className="clay-facts">
                {pane.facts.map((f) => (
                  <li key={f}><Icon name="check" />{f}</li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
