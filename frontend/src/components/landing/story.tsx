"use client";

import { useEffect, useRef, useState } from "react";
import { HOLD_MINUTES, airportByCode } from "./data";
import { Icon } from "./icons";
import { ddmm, pad, parseIso, useSearch, weekday } from "./search-context";

const STEPS = [
  [
    "Tìm và chọn gói giá",
    "Chọn chuyến đi, chuyến về và gói giá hợp với hành lý, điều kiện đổi, hoàn bạn cần.",
  ],
  [
    "Điền thông tin hành khách",
    "Họ tên viết in hoa không dấu như trên giấy tờ. Mua thêm hành lý ký gửi, nhập mã giảm giá nếu có.",
  ],
  [
    `Giữ chỗ trong ${HOLD_MINUTES} phút`,
    "Ghế được giữ ngay khi bạn bấm Giữ chỗ, kèm mã đặt chỗ 6 ký tự để tra cứu về sau.",
  ],
  [
    "Thanh toán qua VNPay",
    "Lỡ đóng trang thanh toán? Mở lại đặt chỗ và thanh toán tiếp, miễn là còn trong hạn giữ chỗ.",
  ],
  [
    "Nhận vé điện tử",
    "Vé được xuất ngay khi thanh toán thành công và gửi tới email liên hệ của đặt chỗ.",
  ],
];
const STATUS = [
  "Đang chọn chuyến",
  "Đang điền thông tin",
  "Chờ thanh toán",
  "Đang thanh toán",
  "Đã xuất vé",
];

export default function Story() {
  const { form } = useSearch();
  const [step, setStep] = useState(2);
  const items = useRef<(HTMLLIElement | null)[]>([]);
  const timerEl = useRef<HTMLSpanElement>(null);
  const lineEl = useRef<HTMLSpanElement>(null);
  const t0 = useRef(0);

  // Bước nào đi qua giữa khung nhìn thì thành bước đang xem.
  useEffect(() => {
    const io = new IntersectionObserver(
      (entries) =>
        entries.forEach((e) => {
          const i = items.current.indexOf(e.target as HTMLLIElement);
          if (e.isIntersecting && i > -1) setStep(i);
        }),
      { rootMargin: "-45% 0px -45% 0px" },
    );
    items.current.forEach((el) => el && io.observe(el));
    return () => io.disconnect();
  }, []);

  // Đếm ngược hạn giữ chỗ ghi thẳng vào DOM, không render lại mỗi giây.
  useEffect(() => {
    if (!t0.current) t0.current = Date.now();
    const paint = () => {
      const total = HOLD_MINUTES * 60;
      const left =
        total - (Math.floor((Date.now() - t0.current) / 1000) % total);
      if (timerEl.current)
        timerEl.current.textContent = `${pad(Math.floor(left / 60))}:${pad(left % 60)}`;
      if (lineEl.current)
        lineEl.current.style.transform = `scaleX(${(left / total).toFixed(4)})`;
    };
    paint();
    const id = setInterval(paint, 1000);
    return () => clearInterval(id);
  }, [step]);

  const depD = parseIso(form.dep);
  const paxText = `${form.adults} người lớn${form.children ? `, ${form.children} trẻ em` : ""}${form.infants ? `, ${form.infants} em bé` : ""}`;
  const route = `${airportByCode(form.from)?.code ?? "HAN"} → ${airportByCode(form.to)?.code ?? "SGN"}`;
  const meta = `${depD ? `${weekday(depD)}, ${ddmm(depD)}, ` : ""}${paxText}${form.trip === "oneway" ? "" : ", khứ hồi"}`;

  return (
    <section
      className="story-sec sec"
      id="cach-dat-ve"
      aria-labelledby="story-h"
    >
      <div className="wrap story">
        <div className="story-head rv">
          <h2 id="story-h" className="h-sec">
            Giữ chỗ trước, thanh toán sau.
          </h2>
          <p className="body muted lead">
            Từ lúc tìm chuyến tới khi nhận vé điện tử, mọi bước đều diễn ra trên
            SkyLine.
          </p>
        </div>

        <ol className="steps">
          {STEPS.map(([title, desc], i) => (
            <li
              key={title}
              ref={(el) => {
                items.current[i] = el;
              }}
              className={`st${i === step ? " is-active" : ""}${i < step ? " is-done" : ""}`}
            >
              <span className="link" aria-hidden="true" />
              <button
                type="button"
                className="step"
                aria-pressed={i === step}
                aria-controls="bk-card"
                onClick={() => setStep(i)}
              >
                <span className="step-mark" aria-hidden="true">
                  <Icon name="check" />
                </span>
                <span className="step-t">{title}</span>
                <span className="step-d">{desc}</span>
              </button>
            </li>
          ))}
        </ol>

        <div className="story-right">
          <div className="stage">
            <div className="bk" id="bk-card" aria-live="polite">
              <div className="bk-top">
                <div>
                  <p className="bk-k">Mã đặt chỗ</p>
                  <p className="bk-code">{step >= 2 ? "K7Q2XM" : "Chưa có"}</p>
                </div>
                <span className={`chip-st${step === 4 ? " is-done" : ""}`}>
                  {STATUS[step]}
                </span>
              </div>
              <p className="bk-route">{route}</p>
              <p className="bk-meta">{meta}</p>

              {step === 0 && (
                <div className="bk-pane">
                  <p className="bk-k">Gói giá</p>
                  <div className="fare">
                    <span>
                      <b>Tiết kiệm</b>
                      <small>Không hoàn, đổi có phí</small>
                    </span>
                  </div>
                  <div className="fare is-on">
                    <span>
                      <b>Linh hoạt</b>
                      <small>Được hoàn, được đổi</small>
                    </span>
                    <Icon name="check" />
                  </div>
                </div>
              )}
              {step === 1 && (
                <div className="bk-pane">
                  <p className="bk-k">Hành khách</p>
                  <p className="bk-pax">NGUYEN VAN AN</p>
                  <p className="bk-row">
                    <span>Hành lý ký gửi thêm</span>
                    <b>20 kg</b>
                  </p>
                  <p className="bk-row">
                    <span>Mã giảm giá</span>
                    <b>WELCOME10</b>
                  </p>
                </div>
              )}
              {step === 2 && (
                <div className="bk-pane">
                  <p className="bk-k">Hạn giữ chỗ còn</p>
                  <p className="bk-timer">
                    <span ref={timerEl}>{pad(HOLD_MINUTES)}:00</span>
                  </p>
                  <span className="hold-line" ref={lineEl} aria-hidden="true" />
                  <p className="bk-note">
                    Ghế đang được giữ cho bạn. Quá hạn mà chưa thanh toán, đặt
                    chỗ tự huỷ và ghế được trả lại.
                  </p>
                </div>
              )}
              {step === 3 && (
                <div className="bk-pane">
                  <p className="bk-k">Thanh toán</p>
                  <p className="bk-big">Đang chuyển tới VNPay</p>
                  <span className="shimmer" aria-hidden="true" />
                  <p className="bk-note">
                    Thanh toán lỗi hoặc bị huỷ giữa chừng, bạn vẫn thử lại được
                    trong hạn giữ chỗ.
                  </p>
                </div>
              )}
              {step === 4 && (
                <div className="bk-pane">
                  <p className="bk-k">Vé điện tử</p>
                  <p className="bk-big">
                    <Icon name="mail" />
                    Đã gửi tới email liên hệ
                  </p>
                  <p className="bk-note">
                    Mỗi hành khách có một số vé 13 chữ số cho từng chặng bay.
                  </p>
                </div>
              )}
            </div>
            <p className="stage-cap">
              Đặt chỗ minh hoạ, đổi theo bước bạn đang xem.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
