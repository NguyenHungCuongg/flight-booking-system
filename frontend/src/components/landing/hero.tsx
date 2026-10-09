"use client";

import Image from "next/image";
import { useRef } from "react";
import SearchDock from "./search-dock";

const reduced = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;

export default function Hero() {
  const heroRef = useRef<HTMLElement>(null);
  const raf = useRef(0);
  const pos = useRef({ x: 0, y: 0 });

  // Parallax theo con trỏ: chỉ ghi biến CSS --mx/--my, không đi qua state của React.
  const onMove = (e: React.MouseEvent) => {
    const el = heroRef.current;
    if (!el || reduced()) return;
    const r = el.getBoundingClientRect();
    if (!r.width || !r.height) return;
    pos.current = {
      x: Math.max(-0.5, Math.min(0.5, (e.clientX - r.left) / r.width - 0.5)),
      y: Math.max(-0.5, Math.min(0.5, (e.clientY - r.top) / r.height - 0.5)),
    };
    if (raf.current) return;
    raf.current = requestAnimationFrame(() => {
      raf.current = 0;
      el.style.setProperty("--mx", pos.current.x.toFixed(3));
      el.style.setProperty("--my", pos.current.y.toFixed(3));
    });
  };
  const onLeave = () => {
    heroRef.current?.style.setProperty("--mx", "0");
    heroRef.current?.style.setProperty("--my", "0");
  };

  return (
    <section className="hero" ref={heroRef} onMouseMove={onMove} onMouseLeave={onLeave} aria-labelledby="hero-h">
      <div className="wrap hero-in">
        <div className="wm" aria-hidden="true">
          <div className="wm-exit">
            <div className="wm-par">
              <p className="wm-text">
                {[..."SkyLine"].map((c, i) => <span key={i} className="wm-ch">{c}</span>)}
              </p>
            </div>
          </div>
        </div>

        <div className="hero-copy">
          <h1 id="hero-h" className="h-hero">
            <span className="sr-only">SkyLine. </span>
            <span className="hl-line">Mọi hãng bay.</span>
            <span className="hl-line">Một lần tìm.</span>
          </h1>
          <p className="hero-sub">Bay thẳng hoặc nối chuyến, giá đã gồm thuế phí. Giữ chỗ trước, thanh toán sau qua VNPay.</p>
        </div>

        <div className="jet-stage">
          <div className="jet-exit">
            <div className="jet-par">
              <div className="jet-float">
                <Image className="jet-img" src="/assets/images/skyline-hero-jet.webp" width={2000} height={643} priority
                  sizes="(min-width: 1200px) 1160px, 96vw"
                  alt="Máy bay chở khách thân trắng, không mang logo hãng, đang nghiêng cánh trên bầu trời" />
              </div>
            </div>
          </div>
        </div>

        <div className="dock-row">
          <div className="dock-in">
            <SearchDock />
          </div>
        </div>
      </div>
    </section>
  );
}
