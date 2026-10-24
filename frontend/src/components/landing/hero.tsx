import { HOLD_MINUTES } from "./data";
import { Icon, Jet } from "./icons";
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
      className="relative overflow-hidden border-b border-line pt-28 pb-48 sm:pt-48 sm:pb-68"
    >
      <div className="mx-auto max-w-page px-16 sm:px-38">
        <div className="grid gap-24 lg:grid-cols-12 lg:items-end lg:gap-32">
          <div className="grid gap-18 lg:col-span-7">
            <div className="inline-flex justify-self-start items-center gap-8 rounded-pill border border-line bg-accent-soft px-14 py-6 text-badge font-bold text-accent">
              <Jet className="size-16 shrink-0" />
              <span>Vé máy bay nội địa và quốc tế · Nhiều hãng một nơi</span>
            </div>

            <h1
              id="hero-h"
              className="text-[clamp(40px,6.2vw,76px)] leading-[0.95] font-black tracking-[-0.035em] text-ink"
            >
              <span className="sr-only">SkyLine. </span>
              <span className="block">Tìm một giây.</span>
              <span className="mt-4 inline-block rounded-pill bg-cta px-18 py-4 text-cta-fg">
                Bay vạn dặm.
              </span>
            </h1>
          </div>

          <div className="grid gap-16 lg:col-span-5 lg:pb-6">
            <p className="max-w-[44ch] text-subheading leading-relaxed font-medium text-ink-2">
              So sánh chuyến bay thẳng và nối chuyến của nhiều hãng trong một lần
              tìm. Giữ chỗ trước, kiểm tra kỹ thông tin rồi mới thanh toán qua
              VNPay.
            </p>
            <ul className="flex flex-wrap gap-8">
              {HIGHLIGHTS.map((item) => (
                <li
                  key={item}
                  className="inline-flex items-center gap-6 rounded-pill border border-line bg-surface px-12 py-5 text-badge font-semibold text-ink-2"
                >
                  <Icon name="check" className="size-14 shrink-0 text-ok" />
                  {item}
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className="mt-32 rounded-[20px] border border-line bg-tint/65 p-8 sm:mt-40 sm:p-14">
          <SearchDock />
        </div>
      </div>
    </section>
  );
}
