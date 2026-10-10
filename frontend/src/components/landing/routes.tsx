"use client";

import { useRef } from "react";
import { ROUTES, airportByCode } from "./data";
import { Icon, Jet } from "./icons";
import { useSearch } from "./search-context";

export default function Routes() {
  const { pickRoute } = useSearch();
  const track = useRef<HTMLDivElement>(null);

  const scroll = (dir: number) => {
    const t = track.current;
    if (!t) return;
    const w = t.querySelector(".route")?.getBoundingClientRect().width ?? 300;
    const reduced = window.matchMedia(
      "(prefers-reduced-motion: reduce)",
    ).matches;
    t.scrollBy({ left: dir * w, behavior: reduced ? "auto" : "smooth" });
  };

  return (
    <section className="routes sec" aria-labelledby="routes-h">
      <div className="wrap routes-head">
        <div className="rv">
          <h2 id="routes-h" className="h-sec">
            Chọn nhanh một đường bay.
          </h2>
          <p className="body muted lead">
            Bấm vào một tuyến để điền sẵn điểm đi, điểm đến, rồi chọn ngày bay.
          </p>
        </div>
        <div className="routes-nav">
          <button
            type="button"
            className="icon-btn"
            aria-label="Xem tuyến trước"
            onClick={() => scroll(-1)}
          >
            <Icon name="left" />
          </button>
          <button
            type="button"
            className="icon-btn"
            aria-label="Xem tuyến tiếp theo"
            onClick={() => scroll(1)}
          >
            <Icon name="right" />
          </button>
        </div>
      </div>
      <div className="track" ref={track}>
        {ROUTES.map((r, i) => {
          const from = airportByCode(r.from)!;
          const to = airportByCode(r.to)!;
          return (
            <button
              key={i}
              type="button"
              className="route"
              onClick={() => pickRoute(i)}
            >
              <span className="r-code">{from.code}</span>
              <span className="r-line" aria-hidden="true">
                <span className="r-run">
                  <Jet />
                </span>
              </span>
              <span className="r-code r-to">{to.code}</span>
              <span className="r-city">
                {from.city} đến {to.city}
              </span>
              <span className="r-foot">
                <span className="r-act">
                  Điền vào ô tìm
                  <Icon name="arrow" />
                </span>
                {from.cc !== to.cc && <span className="r-tag">Quốc tế</span>}
              </span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
