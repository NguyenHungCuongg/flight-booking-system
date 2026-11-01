import Image from "next/image";
import { Icon, Jet } from "./icons";
import SearchDock from "./search-dock";
import { Setting } from "./setting";

const HIGHLIGHTS = [
  "Giá trọn gói đã gồm thuế phí",
  <>
    Giữ chỗ miễn phí <Setting name="booking.hold_minutes" /> phút
  </>,
  "Đổi chuyến, hoàn vé ngay trên web",
];

export default function Hero() {
  return (
    <section
      aria-labelledby="hero-h"
      className="relative flex min-h-[calc(100dvh-65px)] flex-col justify-center overflow-hidden border-b border-line pt-28 pb-48 sm:pt-44 sm:pb-68"
    >
      {/* 65px = chiều cao thanh điều hướng dính (nav.tsx), để hero cộng nav vừa đúng một màn hình. */}
      <div className="mx-auto w-full max-w-page px-16 sm:px-38">
        {/* Top Row: Headline & Editorial Copy */}
        <div className="relative z-20 grid gap-24 lg:grid-cols-12 lg:items-center lg:gap-32">
          <div className="grid gap-18 lg:col-span-7">
            <h1
              id="hero-h"
              className="text-[clamp(40px,6.2vw,76px)] leading-[0.95] font-black tracking-[-0.035em] text-ink"
            >
              <span className="sr-only">SkyLine. </span>
              <span className="block">Tìm một giây.</span>
              <span className="mt-4 inline-block bg-cta px-18 py-4 text-cta-fg">
                Bay vạn dặm.
              </span>
            </h1>
          </div>

          <div className="grid gap-16 lg:col-span-5">
            <p className="max-w-[44ch] text-subheading leading-relaxed font-medium text-ink-2">
              So sánh chuyến bay thẳng và nối chuyến của nhiều hãng trong một
              lần tìm. Giữ chỗ trước, kiểm tra kỹ thông tin rồi mới thanh toán
              qua VNPay.
            </p>
            <ul className="flex flex-wrap gap-x-20 gap-y-8">
              {HIGHLIGHTS.map((item, i) => (
                <li
                  key={i}
                  className="inline-flex items-center gap-6 text-caption font-semibold text-ink-2"
                >
                  <Icon name="check" className="size-14 shrink-0 text-ok" />
                  {item}
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* Center Stage: Full-width 3D Earth Globe & Clouds + Climbing Jet + Pill Telemetry Cards. Cao tối đa 28dvh để ô tìm chuyến vẫn nằm trọn trong màn đầu ở 1366×768. */}
        <div className="pointer-events-none relative z-10 -mt-6 mb-12 flex items-center justify-center select-none sm:-mt-20 lg:mt-8">
          {/* Lớp nền Full-Width (100vw): Quả địa cầu 3D, mây volumetric (render từ Three.js) và đường bay quỹ đạo */}
          <div
            aria-hidden="true"
            className="
              absolute top-1/2 left-1/2 z-0 h-[132%] w-screen min-w-1060 -translate-x-1/2 -translate-y-[44%]
            "
          >
            <Image
              src="/assets/images/hero-backdrop.webp"
              width={2400}
              height={640}
              priority
              unoptimized
              sizes="100vw"
              alt=""
              className="h-full w-full object-cover object-center [[data-theme=dark]_&]:opacity-25 [[data-theme=dark]_&]:contrast-125"
            />
          </div>

          {/* Lớp SVG Quỹ đạo bay & đường neo thẻ thông số */}
          <svg
            aria-hidden="true"
            viewBox="0 0 1120 215"
            fill="none"
            className="
              absolute inset-0 z-5 h-full w-full overflow-visible
            "
          >
            {/* Vòng quỹ đạo mảnh bao quanh địa cầu */}
            <path
              d="M 145 158 C 110 112, 340 52, 695 72"
              stroke="var(--color-line-strong)"
              strokeWidth="1"
              opacity="0.55"
            />
            <path
              d="M 130 158 C 315 122, 585 90, 845 78"
              stroke="var(--color-accent)"
              strokeWidth="1.4"
              strokeDasharray="4 6"
              opacity="0.65"
            />

            {/* Đường bay phụ nối sang cánh phải */}
            <path
              d="M 248 92 C 455 120, 680 152, 855 162"
              stroke="var(--color-line-strong)"
              strokeWidth="1.2"
              strokeDasharray="3 6"
            />

            {/* Đường kẻ dọc neo từ đáy thẻ HAN -> SGN xuống điểm nút tọa độ o */}
            <line
              x1="130"
              y1="96"
              x2="130"
              y2="153"
              stroke="var(--color-accent)"
              strokeWidth="1.25"
              opacity="0.55"
            />
            <circle
              cx="130"
              cy="158"
              r="4.5"
              fill="var(--color-surface)"
              stroke="var(--color-accent)"
              strokeWidth="2"
            />

            {/* Quỹ đạo sáng màu xanh Lime Voltage vươn vòng qua mũi máy bay như ảnh mẫu */}
            <path
              d="M 305 174 C 465 148, 635 108, 785 92"
              stroke="var(--color-cta)"
              strokeWidth="3"
              strokeLinecap="round"
            />
            <path
              d="M 845 78 C 918 84, 898 124, 735 158"
              stroke="var(--color-cta)"
              strokeWidth="2"
              strokeLinecap="round"
            />

            {/* Điểm nút tọa độ phát sáng phía trước mũi máy bay */}
            <circle
              cx="845"
              cy="78"
              r="6"
              fill="var(--color-cta)"
              opacity="0.4"
            />
            <circle cx="845" cy="78" r="3.5" fill="var(--color-accent)" />
          </svg>

          {/* Thẻ tọa độ tuyến bay bên trái — bo tròn 2 cạnh (stadium pill) */}
          <div
            aria-hidden="true"
            className="absolute top-18 left-0 z-20 hidden min-w-236 flex-col gap-4 rounded-full border border-line bg-surface/95 px-26 py-12 backdrop-blur-xs lg:flex xl:left-12"
          >
            <div className="flex items-center justify-between gap-12">
              <span className="inline-flex items-center gap-5 rounded-full bg-cta px-10 py-2 text-badge font-bold text-cta-fg">
                <Jet className="size-12 shrink-0 -rotate-45" />
                Bay thẳng
              </span>
              <span className="text-badge font-semibold text-ink-2">
                02g 10p
              </span>
            </div>
            <div className="flex items-center justify-between gap-12">
              <span className="text-heading-sm font-black tracking-tight text-ink">
                HAN
              </span>
              <Icon name="arrow" className="size-16 shrink-0 text-ok" />
              <span className="text-heading-sm font-black tracking-tight text-ink">
                SGN
              </span>
            </div>
            <span className="text-badge font-medium text-ink-2">
              Hà Nội đến TP. Hồ Chí Minh
            </span>
          </div>

          {/* Hình máy bay 3D trung tâm */}
          <Image
            src="/assets/images/skyline-climb-jet.webp"
            width={1600}
            height={505}
            priority
            sizes="(min-width: 1200px) 1080px, 96vw"
            alt="Máy bay chở khách 3D thân trắng đang vươn cao trên bầu trời"
            className="relative z-10 w-full max-w-1060 object-contain max-sm:max-h-72 lg:max-h-[28dvh] drop-shadow-[0_24px_36px_rgba(22,51,0,0.18)]"
          />

          {/* Thẻ thông số hành trình bên phải — bo tròn 2 cạnh (stadium pill) */}
          <div
            aria-hidden="true"
            className="absolute right-0 bottom-6 z-20 hidden min-w-256 flex-col gap-4 rounded-full border border-line bg-surface/95 px-28 py-12 backdrop-blur-xs lg:flex xl:right-8"
          >
            <div className="flex items-center justify-between gap-10">
              <span className="inline-flex items-center gap-6 text-badge font-bold text-ink">
                <Jet className="size-13 shrink-0 -rotate-45 text-ok" />
                FL350 · 890 km/h
              </span>
              <span className="rounded-full bg-ok-soft px-10 py-2 text-badge font-bold text-ok">
                Đúng giờ
              </span>
            </div>
            <div className="flex items-center gap-6 text-caption font-black tracking-tight text-ink">
              <span className="inline-flex items-center gap-4">
                SGN
                <Icon name="arrow" className="size-13 text-ink-2" />
                DAD
              </span>
              <span className="text-line-strong">·</span>
              <span className="inline-flex items-center gap-4">
                HAN
                <Icon name="arrow" className="size-13 text-ink-2" />
                PQC
              </span>
            </div>
            <span className="text-badge font-medium text-ink-2">
              So sánh 4 hãng trong 1 lần tìm
            </span>
          </div>
        </div>

        {/* Bottom Stage: Search Dock */}
        <div className="relative z-20">
          <SearchDock />
        </div>
      </div>
    </section>
  );
}
