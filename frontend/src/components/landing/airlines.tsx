import { Icon, Jet } from "./icons";

const FEATURES = [
  [
    "Bay thẳng hoặc nối chuyến",
    "Kết quả hiển thị cả chuyến thẳng lẫn chuyến nối một điểm dừng cùng hãng, thời gian chờ nối từ 1 đến 12 tiếng.",
  ],
  [
    "Giá trọn gói, đúng số khách",
    "Giá hiển thị đã gồm thuế phí, tính sẵn theo đúng số người lớn, trẻ em và em bé bạn đã chọn.",
  ],
  [
    "Gói giá minh bạch",
    "Xem rõ hành lý xách tay, ký gửi và điều kiện đổi hoặc hoàn của từng gói vé ngay trước khi đặt.",
  ],
  [
    "Lọc và sắp xếp tức thì",
    "Lọc theo hãng bay, số điểm dừng, khung giờ cất cánh, giá tối đa. Sắp xếp theo giá, giờ bay hoặc tổng thời lượng.",
  ],
] as const;

export default function Airlines() {
  return (
    <section
      aria-labelledby="air-h"
      className="border-t border-line py-56 sm:py-88"
    >
      <div className="mx-auto max-w-page px-16 sm:px-38">
        <div className="grid gap-36 lg:grid-cols-12 lg:items-center lg:gap-48">
          {/* Left: Visual Fare Transparency Preview */}
          <div className="border border-line bg-tint p-20 sm:p-28 lg:col-span-5">
            <div className="card-square grid gap-16 p-20">
              <div className="flex items-center justify-between gap-12 border-b border-line pb-14">
                <span className="text-caption font-bold text-ink">
                  Vietnam Airlines · VN213
                </span>
                <span className="rounded-badge bg-tint px-10 py-3 text-badge font-semibold text-ink-2">
                  Bay thẳng
                </span>
              </div>

              <div className="flex items-center justify-between gap-12">
                <div>
                  <p className="text-heading-sm font-black text-ink">08:00</p>
                  <p className="text-badge text-muted">HAN · Hà Nội</p>
                </div>
                <div className="grid flex-1 justify-items-center gap-4 px-8 text-badge text-muted">
                  <span>2 giờ 10 phút</span>
                  <div className="flex w-full items-center gap-4 text-accent">
                    <span className="size-6 rounded-inner border border-line-strong" />
                    <span className="flex-1 border-t border-dashed border-line-strong" />
                    <Jet className="size-16 shrink-0" />
                  </div>
                </div>
                <div className="text-right">
                  <p className="text-heading-sm font-black text-ink">10:10</p>
                  <p className="text-badge text-muted">SGN · TP. HCM</p>
                </div>
              </div>

              <div className="grid gap-10 border-t border-line pt-14">
                <div className="rounded-inner border border-line bg-paper/60 p-12">
                  <div className="flex items-center justify-between gap-8">
                    <span className="text-caption font-bold text-ink">
                      Economy Saver
                    </span>
                    <span className="text-caption font-black text-ink">
                      1.490.000 ₫
                    </span>
                  </div>
                  <p className="mt-4 text-badge text-muted">
                    Xách tay 12 kg · Đổi có phí · Không hoàn
                  </p>
                </div>

                <div className="rounded-inner border-2 border-accent bg-accent-soft/55 p-12">
                  <div className="flex items-center justify-between gap-8">
                    <span className="inline-flex items-center gap-6 text-caption font-bold text-accent">
                      <Icon name="check" className="size-16 shrink-0" />
                      Economy Flex
                    </span>
                    <span className="text-caption font-black text-accent">
                      1.890.000 ₫
                    </span>
                  </div>
                  <p className="mt-4 text-badge text-ink-2">
                    Xách tay 12 kg + Ký gửi 23 kg · Được đổi · Được hoàn
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Right: Feature Breakdown */}
          <div className="grid gap-32 lg:col-span-7">
            <div className="grid gap-12">
              <h2
                id="air-h"
                className="text-[clamp(28px,3.2vw,42px)] leading-[1.05] font-black tracking-[-0.028em] text-ink"
              >
                Đủ thông tin để chọn đúng vé
                <span className="text-accent">.</span>
              </h2>
              <p className="max-w-[52ch] text-body text-muted">
                Không phí ẩn ở bước cuối. Mọi điều kiện hành lý, đổi ngày bay và
                hoàn vé đều được đặt cạnh nhau trước khi bạn giữ chỗ.
              </p>
            </div>

            <div className="grid gap-24 sm:grid-cols-2 sm:gap-28">
              {FEATURES.map(([title, desc]) => (
                <div
                  key={title}
                  className="grid content-start gap-10 border-t border-line pt-20"
                >
                  <h3 className="text-subheading font-bold text-ink">
                    {title}
                  </h3>
                  <p className="text-body text-muted">{desc}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
