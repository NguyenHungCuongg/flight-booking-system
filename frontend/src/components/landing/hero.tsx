import Image from "next/image";
import { Icon } from "./icons";
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
        <div className="relative z-10 grid gap-24 lg:grid-cols-12 lg:items-center lg:gap-32">
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

        {/* Center Stage: 3D Climbing Jet. Cao tối đa 28dvh để ô tìm chuyến vẫn nằm trọn trong màn đầu ở 1366×768. */}
        <div className="pointer-events-none relative z-10 -mt-6 mb-12 flex justify-center select-none sm:-mt-20 lg:mt-8">
          <Image
            src="/assets/images/skyline-climb-jet.webp"
            width={1600}
            height={505}
            priority
            sizes="(min-width: 1200px) 1080px, 96vw"
            alt="Máy bay chở khách 3D thân trắng đang vươn cao trên bầu trời"
            className="relative w-full max-w-1060 object-contain max-sm:max-h-72 lg:max-h-[28dvh] drop-shadow-[0_24px_36px_rgba(22,51,0,0.18)]"
          />
        </div>

        {/* Bottom Stage: Search Dock */}
        <div className="relative z-20">
          <SearchDock />
        </div>
      </div>
    </section>
  );
}
