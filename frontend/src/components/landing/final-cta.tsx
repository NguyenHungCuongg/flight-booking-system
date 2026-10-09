"use client";

import Image from "next/image";
import Link from "next/link";
import { Icon } from "./icons";
import { useSearch } from "./search-context";

export default function FinalCta() {
  const { focusSearch } = useSearch();

  return (
    <section className="final" aria-labelledby="final-h">
      <div className="wrap final-in">
        <div className="climb-wrap">
          <Image className="climb-img" src="/assets/images/skyline-climb-jet.webp" width={1600} height={505}
            sizes="(min-width: 768px) 1120px, 128vw" alt="Máy bay thân trắng đang lấy độ cao" />
        </div>
        <h2 id="final-h" className="final-h">Cất cánh.</h2>
        <div className="final-cta">
          <button type="button" className="pill pill-dark" onClick={focusSearch}>Tìm chuyến bay<Icon name="arrow" /></button>
          <Link className="pill pill-line" href="/register">Đăng ký</Link>
        </div>
      </div>
    </section>
  );
}
