import Link from "next/link";
import { pillLine } from "@/components/form";
import Faq from "./faq";
import { Icon } from "./icons";

export default function Support() {
  return (
    <section
      id="ho-tro"
      aria-labelledby="sup-h"
      className="border-t border-line py-56 sm:py-88"
    >
      <div className="mx-auto max-w-page px-16 sm:px-38">
        <div className="grid gap-36 lg:grid-cols-12 lg:items-start lg:gap-48">
          <div className="grid content-start justify-items-start gap-20 lg:sticky lg:top-96 lg:col-span-5">
            <h2
              id="sup-h"
              className="text-[clamp(28px,3.2vw,42px)] leading-[1.05] font-black tracking-[-0.028em] text-ink"
            >
              Hỗ trợ<span className="text-accent">.</span>
            </h2>
            <p className="max-w-[42ch] text-body text-muted">
              Không nhận được email vé? Nhân viên SkyLine tra cứu theo mã đặt
              chỗ, email hoặc số điện thoại rồi gửi lại vé điện tử cho bạn.
            </p>
            <Link href="/bookings" className={pillLine}>
              <span>Đặt chỗ của tôi</span>
              <Icon name="upright" className="size-18 shrink-0" />
            </Link>
          </div>

          <div className="grid gap-16 lg:col-span-7">
            <h3 className="text-subheading font-bold text-ink">
              Câu hỏi thường gặp
            </h3>
            <Faq />
          </div>
        </div>
      </div>
    </section>
  );
}
