"use client";

import Link from "next/link";
import { pillLine, pillPrimary } from "@/components/form";
import { Icon } from "./icons";
import { useSearch } from "./search-context";

export default function FinalCta() {
  const { focusSearch } = useSearch();

  return (
    <section
      aria-labelledby="final-h"
      className="relative isolate overflow-hidden pb-56 sm:pb-88"
    >
      {/* Quả địa cầu chấm (render sẵn bằng Three.js) dùng làm mask để đổi màu theo theme; chỉ lộ nửa trên. */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute bottom-0 left-1/2 -z-10 aspect-square w-[min(1040px,140vw)] -translate-x-1/2 translate-y-[68%] bg-accent opacity-[0.16] mask-[url(/assets/images/globe-dots.webp)] mask-contain mask-no-repeat"
      />
      <div className="mx-auto max-w-page px-16 sm:px-38">
        <div className="grid justify-items-center gap-24 rounded-[28px] px-24 py-48 text-center sm:px-48 sm:py-68">
          <h2
            id="final-h"
            className="max-w-[20ch] text-[clamp(30px,4.2vw,52px)] leading-[1.04] font-black tracking-[-0.03em] text-ink"
          >
            <span className="block">Săn vé dễ dàng,</span>
            <span className="text-accent">ngập tràn trải nghiệm.</span>
          </h2>
          <p className="max-w-[46ch] text-body text-ink-2">
            Chọn hành trình phù hợp, giữ chỗ ngay trong vài bước và thanh toán
            an toàn qua VNPay.
          </p>
          <div className="mt-4 flex flex-wrap justify-center gap-12">
            <button
              type="button"
              className={`${pillPrimary} min-h-46 px-24 text-body`}
              onClick={focusSearch}
            >
              <span>Tìm chuyến bay</span>
              <Icon name="arrow" className="size-20 shrink-0" />
            </button>
            <Link
              href="/register"
              className={`${pillLine} min-h-46 px-24 text-body`}
            >
              Đăng ký tài khoản
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}
