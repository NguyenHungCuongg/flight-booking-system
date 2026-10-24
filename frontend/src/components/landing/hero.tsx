import Image from "next/image";
import { HOLD_MINUTES } from "./data";
import { Icon } from "./icons";
import SearchDock from "./search-dock";

const HIGHLIGHTS = [
  "Giá trọn gói đã gồm thuế phí",
  `Giữ chỗ miễn phí ${HOLD_MINUTES} phút`,
  "Đổi chuyến, hoàn vé ngay trên web",
] as const;

export default function Hero() {
  return (
    <section
      aria-labelledby="hero-h"
      className="relative flex min-h-[calc(100dvh-65px)] flex-col justify-center overflow-hidden border-b border-line pt-28 pb-48 sm:pt-44 sm:pb-68"
    >
      {/* 65px = chiều cao thanh điều hướng dính (nav.tsx), để hero cộng nav vừa đúng một màn hình. */}
      <div className="mx-auto w-full max-w-page px-16 sm:px-38">
        {/* Top Row: Headline & Editorial Copy */}
        <div className="relative z-10 grid gap-24 lg:grid-cols-12 lg:items-end lg:gap-32">
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

          <div className="grid gap-16 lg:col-span-5 lg:pb-44">
            <p className="max-w-[44ch] text-subheading leading-relaxed font-medium text-ink-2">
              So sánh chuyến bay thẳng và nối chuyến của nhiều hãng trong một
              lần tìm. Giữ chỗ trước, kiểm tra kỹ thông tin rồi mới thanh toán
              qua VNPay.
            </p>
            <ul className="flex flex-wrap gap-x-20 gap-y-8">
              {HIGHLIGHTS.map((item) => (
                <li
                  key={item}
                  className="inline-flex items-center gap-6 text-caption font-semibold text-ink-2"
                >
                  <Icon name="check" className="size-14 shrink-0 text-ok" />
                  {item}
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* Center Stage: 3D Climbing Jet */}
        <div className="pointer-events-none relative z-10 -my-6 flex justify-center select-none sm:-my-20 lg:-my-28">
          <Image
            src="/assets/images/skyline-climb-jet.webp"
            width={1600}
            height={505}
            priority
            sizes="(min-width: 1200px) 1080px, 96vw"
            alt="Máy bay chở khách 3D thân trắng đang vươn cao trên bầu trời"
            className="relative w-full max-w-1060 object-contain drop-shadow-[0_24px_36px_rgba(22,51,0,0.18)]"
          />
        </div>

        {/* Bottom Stage: Search Dock */}
        <div className="relative z-20 rounded-[20px] border border-line bg-tint/75 p-8 backdrop-blur-xs sm:p-14">
          <SearchDock />
        </div>
      </div>
    </section>
  );
}
