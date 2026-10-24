"use client";

import { useEffect, useRef, useState } from "react";
import { HOLD_MINUTES } from "./data";
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
] as const;

const STATUS = [
  "Đang chọn chuyến",
  "Đang điền thông tin",
  "Chờ thanh toán",
  "Đang thanh toán",
  "Đã xuất vé",
] as const;

export default function Story() {
  const { form } = useSearch();
  const [step, setStep] = useState(2);
  const items = useRef<(HTMLLIElement | null)[]>([]);
  const timerEl = useRef<HTMLSpanElement>(null);
  const lineEl = useRef<HTMLSpanElement>(null);
  const t0 = useRef(0);

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
  const route = `${form.from || "HAN"} → ${form.to || "SGN"}`;
  const meta = `${depD ? `${weekday(depD)}, ${ddmm(depD)}, ` : ""}${paxText}${form.trip === "oneway" ? "" : ", khứ hồi"}`;

  return (
    <section
      id="cach-dat-ve"
      aria-labelledby="story-h"
      className="border-t border-line py-56 sm:py-88"
    >
      <div className="mx-auto max-w-page px-16 sm:px-38">
        <div className="grid gap-36 lg:grid-cols-12 lg:items-start lg:gap-48">
          {/* Left: Interactive 5-step timeline */}
          <div className="grid gap-32 lg:col-span-7">
            <div className="grid gap-12">
              <h2
                id="story-h"
                className="text-[clamp(28px,3.2vw,42px)] leading-[1.05] font-black tracking-[-0.028em] text-ink"
              >
                Giữ chỗ trước, thanh toán sau
                <span className="text-accent">.</span>
              </h2>
              <p className="max-w-[48ch] text-body text-muted">
                Từ lúc tìm chuyến tới khi nhận vé điện tử, mọi bước đều hiển thị
                rõ trạng thái và hạn giữ chỗ trên SkyLine.
              </p>
            </div>

            <ol className="grid">
              {STEPS.map(([title, desc], i) => {
                const isActive = i === step;
                const isDone = i < step;
                return (
                  <li
                    key={title}
                    ref={(el) => {
                      items.current[i] = el;
                    }}
                    className="relative"
                  >
                    {i < STEPS.length - 1 && (
                      <span
                        aria-hidden="true"
                        className={`absolute top-36 bottom-6 left-15 w-2 transition-colors duration-300 ${
                          isDone
                            ? "bg-accent"
                            : "border-l border-dashed border-line-strong"
                        }`}
                      />
                    )}
                    <button
                      type="button"
                      aria-pressed={isActive}
                      aria-controls="bk-card"
                      onClick={() => setStep(i)}
                      className="group grid w-full grid-cols-[32px_minmax(0,1fr)] gap-x-20 gap-y-6 pb-32 text-left"
                    >
                      <span
                        aria-hidden="true"
                        className={`grid size-32 place-items-center rounded-inner border text-caption font-bold transition-colors ${
                          isDone || isActive
                            ? "border-cta bg-cta text-cta-fg"
                            : "border-line-strong bg-surface text-muted group-hover:border-ink"
                        }`}
                      >
                        {isDone ? (
                          <Icon name="check" className="size-16 shrink-0" />
                        ) : (
                          <span>{i + 1}</span>
                        )}
                      </span>
                      <div className="grid gap-6">
                        <span
                          className={`text-subheading font-bold transition-colors ${
                            isActive || isDone
                              ? "text-ink"
                              : "text-muted group-hover:text-ink"
                          }`}
                        >
                          {title}
                        </span>
                        <span
                          className={`max-w-[46ch] text-body transition-colors ${
                            isActive ? "text-ink-2" : "text-muted"
                          }`}
                        >
                          {desc}
                        </span>
                      </div>
                    </button>
                  </li>
                );
              })}
            </ol>
          </div>

          {/* Right: Sticky Live Booking Preview Card */}
          <div className="lg:sticky lg:top-96 lg:col-span-5">
            <div className=" border border-line bg-tint p-20 sm:p-28">
              <div
                id="bk-card"
                aria-live="polite"
                className="card-square grid min-h-360 content-start gap-16 p-22"
              >
                <div className="flex items-start justify-between gap-16">
                  <div>
                    <p className="text-badge text-muted">Mã đặt chỗ</p>
                    <p className="text-subheading font-black tracking-wider text-ink">
                      {step >= 2 ? "K7Q2XM" : "Chưa có"}
                    </p>
                  </div>
                  <span
                    className={`inline-flex min-h-32 items-center rounded-badge px-12 py-4 text-badge font-bold ${
                      step === 4
                        ? "bg-cta text-cta-fg"
                        : "border border-line-strong bg-accent-soft text-accent"
                    }`}
                  >
                    {STATUS[step]}
                  </span>
                </div>

                <div className="border-t border-line pt-14">
                  <p className="text-heading font-black tracking-tight text-ink">
                    {route}
                  </p>
                  <p className="mt-4 text-badge text-muted">{meta}</p>
                </div>

                {step === 0 && (
                  <div className="grid gap-10 border-t border-line pt-14">
                    <p className="text-badge font-semibold text-muted">
                      Gói giá
                    </p>
                    <div className="flex items-center justify-between rounded-inner border border-line bg-paper/50 px-14 py-10">
                      <span className="grid">
                        <b className="text-body font-bold text-ink">
                          Tiết kiệm
                        </b>
                        <small className="text-badge text-muted">
                          Không hoàn, đổi có phí
                        </small>
                      </span>
                    </div>
                    <div className="flex items-center justify-between rounded-inner border-2 border-accent bg-accent-soft/70 px-14 py-10 text-accent">
                      <span className="grid">
                        <b className="text-body font-bold">Linh hoạt</b>
                        <small className="text-badge text-ink-2">
                          Được hoàn, được đổi
                        </small>
                      </span>
                      <Icon name="check" className="size-18 shrink-0" />
                    </div>
                  </div>
                )}

                {step === 1 && (
                  <div className="grid gap-10 border-t border-line pt-14">
                    <p className="text-badge font-semibold text-muted">
                      Hành khách
                    </p>
                    <p className="text-subheading font-black tracking-wide text-ink">
                      NGUYEN VAN AN
                    </p>
                    <div className="flex justify-between text-caption">
                      <span className="text-muted">Hành lý ký gửi thêm</span>
                      <b className="font-bold text-ink">20 kg</b>
                    </div>
                    <div className="flex justify-between text-caption">
                      <span className="text-muted">Mã giảm giá</span>
                      <b className="rounded-badge bg-accent-soft px-8 py-2 text-badge font-bold text-accent">
                        WELCOME10
                      </b>
                    </div>
                  </div>
                )}

                {step === 2 && (
                  <div className="grid gap-12 border-t border-line pt-14">
                    <p className="text-badge font-semibold text-muted">
                      Hạn giữ chỗ còn
                    </p>
                    <p className="text-display font-black tracking-tight text-accent tabular-nums">
                      <span ref={timerEl}>{pad(HOLD_MINUTES)}:00</span>
                    </p>
                    <div className="h-4 overflow-hidden rounded-inner bg-tint">
                      <span
                        ref={lineEl}
                        aria-hidden="true"
                        className="block h-full origin-left rounded-inner bg-cta transition-transform duration-1000 ease-linear"
                      />
                    </div>
                    <p className="text-badge leading-relaxed text-muted">
                      Ghế đang được giữ cho bạn. Quá hạn mà chưa thanh toán, đặt
                      chỗ tự huỷ và ghế được trả lại.
                    </p>
                  </div>
                )}

                {step === 3 && (
                  <div className="grid gap-12 border-t border-line pt-14">
                    <p className="text-badge font-semibold text-muted">
                      Thanh toán
                    </p>
                    <p className="text-subheading font-bold text-ink">
                      Đang chuyển tới VNPay
                    </p>
                    <div
                      aria-hidden="true"
                      className="h-8 overflow-hidden rounded-inner bg-tint"
                    >
                      <div className="h-full w-1/2 animate-pulse rounded-inner bg-cta" />
                    </div>
                    <p className="text-badge leading-relaxed text-muted">
                      Thanh toán lỗi hoặc bị huỷ giữa chừng, bạn vẫn thử lại
                      được trong hạn giữ chỗ.
                    </p>
                  </div>
                )}

                {step === 4 && (
                  <div className="grid gap-12 border-t border-line pt-14">
                    <p className="text-badge font-semibold text-muted">
                      Vé điện tử
                    </p>
                    <p className="flex items-center gap-10 text-subheading font-bold text-ok">
                      <Icon name="mail" className="size-22 shrink-0" />
                      Đã gửi tới email liên hệ
                    </p>
                    <p className="text-badge leading-relaxed text-muted">
                      Mỗi hành khách có một số vé 13 chữ số cho từng chặng bay.
                    </p>
                  </div>
                )}
              </div>

              <p className="mt-14 text-badge text-muted">
                Đặt chỗ minh hoạ, đổi theo bước bạn đang xem.
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
