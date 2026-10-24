"use client";

import { useRef } from "react";
import { ROUTES, airportByCode } from "./data";
import { Icon, Jet } from "./icons";
import { useSearch } from "./search-context";

const navCircleBtn =
  "inline-grid size-42 place-items-center rounded-inner border border-line-strong bg-surface text-ink transition hover:border-ink hover:bg-accent-soft active:scale-95";

export default function Routes() {
  const { pickRoute } = useSearch();
  const track = useRef<HTMLDivElement>(null);

  const scroll = (dir: number) => {
    const t = track.current;
    if (!t) return;
    const card = t.querySelector("button");
    const w = card ? card.getBoundingClientRect().width + 16 : 300;
    const reduced = window.matchMedia(
      "(prefers-reduced-motion: reduce)",
    ).matches;
    t.scrollBy({ left: dir * w, behavior: reduced ? "auto" : "smooth" });
  };

  return (
    <section aria-labelledby="routes-h" className="py-56 sm:py-80">
      <div className="mx-auto max-w-page px-16 sm:px-38">
        <div className="flex flex-wrap items-end justify-between gap-20">
          <div className="grid gap-10">
            <h2
              id="routes-h"
              className="text-[clamp(28px,3.2vw,42px)] leading-[1.05] font-black tracking-[-0.028em] text-ink"
            >
              Chọn nhanh một đường bay<span className="text-accent">.</span>
            </h2>
            <p className="max-w-[48ch] text-body text-muted">
              Bấm vào một tuyến để điền sẵn điểm đi, điểm đến vào ô tìm kiếm,
              rồi chọn ngày khởi hành.
            </p>
          </div>

          <div className="hidden items-center gap-10 sm:flex">
            <button
              type="button"
              className={navCircleBtn}
              aria-label="Xem tuyến trước"
              onClick={() => scroll(-1)}
            >
              <Icon name="left" className="size-20 shrink-0" />
            </button>
            <button
              type="button"
              className={navCircleBtn}
              aria-label="Xem tuyến tiếp theo"
              onClick={() => scroll(1)}
            >
              <Icon name="right" className="size-20 shrink-0" />
            </button>
          </div>
        </div>

        <div
          ref={track}
          className="mt-32 flex snap-x snap-mandatory gap-16 overflow-x-auto pb-8 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        >
          {ROUTES.map((r, i) => {
            const from = airportByCode(r.from)!;
            const to = airportByCode(r.to)!;
            const isIntl = from.cc !== to.cc;
            return (
              <button
                key={`${r.from}-${r.to}`}
                type="button"
                onClick={() => pickRoute(i)}
                className="card group flex min-w-268 shrink-0 snap-start flex-col justify-between gap-24 p-22 text-left transition duration-200 hover:-translate-y-2 hover:shadow-card-hover sm:min-w-288"
              >
                <div className="grid gap-14">
                  <span
                    className={`justify-self-start rounded-badge px-10 py-3 text-badge font-bold ${
                      isIntl
                        ? "bg-info-soft text-info"
                        : "bg-accent-soft text-accent"
                    }`}
                  >
                    {isIntl ? "Quốc tế" : "Nội địa"}
                  </span>

                  <div className="mt-4 flex items-center justify-between gap-12">
                    <span className="text-heading-lg font-black tracking-tight text-ink">
                      {from.code}
                    </span>
                    <div
                      aria-hidden="true"
                      className="flex flex-1 items-center gap-6 px-4 text-accent"
                    >
                      <span className="size-6 rounded-inner bg-line-strong" />
                      <span className="flex-1 border-t border-dashed border-line-strong" />
                      <Jet className="size-18 shrink-0 transition-transform duration-300 group-hover:translate-x-3" />
                    </div>
                    <span className="text-heading-lg font-black tracking-tight text-accent">
                      {to.code}
                    </span>
                  </div>

                  <p className="text-caption font-semibold text-ink-2">
                    {from.city} đến {to.city}
                  </p>
                </div>

                <div className="mt-auto flex items-center justify-between border-t border-line pt-14 text-caption font-bold text-accent">
                  <span>Điền vào ô tìm</span>
                  <span className="inline-grid size-28 place-items-center rounded-full bg-cta text-cta-fg transition-transform duration-200 group-hover:translate-x-2">
                    <Icon name="arrow" className="size-16 shrink-0" />
                  </span>
                </div>
              </button>
            );
          })}
        </div>
      </div>
    </section>
  );
}
