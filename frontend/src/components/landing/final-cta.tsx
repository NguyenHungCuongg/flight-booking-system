"use client";

import Image from "next/image";
import Link from "next/link";
import { Icon } from "./icons";
import { useSearch } from "./search-context";

export default function FinalCta() {
  const { focusSearch } = useSearch();

  return (
    <section className="final" aria-labelledby="final-h">
      <div className="final-bg" aria-hidden="true">
        <Image
          src="/assets/images/red-sky-2.jpg"
          alt=""
          fill
          sizes="100vw"
          className="final-bg-img"
        />
        <div className="final-bg-overlay" />
      </div>
      <div className="wrap final-in">
        <div className="climb-wrap">
          <Image
            className="climb-img"
            src="/assets/images/skyline-climb-jet.webp"
            width={1600}
            height={505}
            sizes="(min-width: 768px) 1120px, 128vw"
            alt="Máy bay thân trắng đang lấy độ cao"
          />
        </div>
        <h3 id="final-h" className="final-h">
          <span>Săn vé dễ dàng,</span>
          <span>ngập tràn trải nghiệm.</span>
        </h3>
        <div className="final-cta">
          <button
            type="button"
            className="pill pill-dark"
            onClick={focusSearch}
          >
            Tìm chuyến bay
            <Icon name="arrow" />
          </button>
          <Link className="pill pill-line" href="/register">
            Đăng ký
          </Link>
        </div>
      </div>
    </section>
  );
}
