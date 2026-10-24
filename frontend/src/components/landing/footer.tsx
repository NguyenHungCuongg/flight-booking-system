import Link from "next/link";
import { Logo } from "./icons";

export default function Footer() {
  return (
    <footer className="bg-forest-ink pt-68 pb-40 text-pure-white">
      <div className="mx-auto max-w-page px-16 sm:px-38">
        <p
          aria-hidden="true"
          className="mb-48 text-[clamp(48px,8vw,96px)] leading-[0.85] font-black tracking-[-0.035em] text-pure-white select-none sm:mb-68"
        >
          SkyLine<span className="text-lime-voltage">.</span>
        </p>

        <div className="grid gap-36 sm:grid-cols-2 lg:grid-cols-[minmax(0,1.6fr)_repeat(3,minmax(0,1fr))]">
          <div className="grid content-start gap-16 sm:col-span-2 lg:col-span-1">
            <a
              href="#top"
              aria-label="SkyLine, về đầu trang"
              className="inline-flex items-center gap-10 text-subheading font-black tracking-tight text-pure-white"
            >
              <span className="size-36 rounded-inner ring-1 ring-lime-voltage/30 [&>svg]:size-full">
                <Logo />
              </span>
              SkyLine
            </a>
            <p className="max-w-[30ch] text-body text-linen-mist/80">
              Vé máy bay của nhiều hãng, tìm và đặt ở một nơi. Giữ chỗ trước,
              thanh toán sau qua VNPay.
            </p>
          </div>

          <nav aria-label="Đặt vé" className="grid content-start gap-8">
            <h3 className="mb-4 text-caption font-bold text-lime-voltage">
              Đặt vé
            </h3>
            <a
              href="#tim-chuyen"
              className="text-body text-linen-mist/75 transition-colors hover:text-lime-voltage"
            >
              Tìm chuyến bay
            </a>
            <Link
              href="/bookings"
              className="text-body text-linen-mist/75 transition-colors hover:text-lime-voltage"
            >
              Đặt chỗ của tôi
            </Link>
            <a
              href="#cach-dat-ve"
              className="text-body text-linen-mist/75 transition-colors hover:text-lime-voltage"
            >
              Cách đặt vé
            </a>
          </nav>

          <nav aria-label="Tài khoản" className="grid content-start gap-8">
            <h3 className="mb-4 text-caption font-bold text-lime-voltage">
              Tài khoản
            </h3>
            <Link
              href="/login"
              className="text-body text-linen-mist/75 transition-colors hover:text-lime-voltage"
            >
              Đăng nhập
            </Link>
            <Link
              href="/register"
              className="text-body text-linen-mist/75 transition-colors hover:text-lime-voltage"
            >
              Đăng ký
            </Link>
            <Link
              href="/forgot-password"
              className="text-body text-linen-mist/75 transition-colors hover:text-lime-voltage"
            >
              Quên mật khẩu
            </Link>
          </nav>

          <nav aria-label="Hỗ trợ" className="grid content-start gap-8">
            <h3 className="mb-4 text-caption font-bold text-lime-voltage">
              Hỗ trợ
            </h3>
            <a
              href="#ho-tro"
              className="text-body text-linen-mist/75 transition-colors hover:text-lime-voltage"
            >
              Câu hỏi thường gặp
            </a>
            <a
              href="#doi-hoan"
              className="text-body text-linen-mist/75 transition-colors hover:text-lime-voltage"
            >
              Đổi và hoàn vé
            </a>
          </nav>
        </div>

        <div className="mt-56 flex flex-wrap items-center justify-between gap-16 border-t border-linen-mist/15 pt-24 text-badge text-linen-mist/65">
          <p>© 2026 SkyLine</p>
          <p>
            Đồ án môn học. Lịch bay và giá vé là dữ liệu mô phỏng, không phải
            chính sách của hãng bay.
          </p>
        </div>
      </div>
    </footer>
  );
}
