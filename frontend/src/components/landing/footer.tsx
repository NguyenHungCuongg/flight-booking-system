import Link from "next/link";
import { Logo } from "./icons";

export default function Footer() {
  return (
    <footer className="foot on-dark">
      <div className="wrap">
        <p className="foot-wm" aria-hidden="true">
          {[..."SkyLine"].map((c, i) => (
            <span key={i} className="fch">
              {c}
            </span>
          ))}
        </p>
        <div className="foot-grid">
          <div className="foot-brand">
            <a className="logo" href="#top" aria-label="SkyLine, về đầu trang">
              <Logo />
            </a>
            <p className="body foot-blurb">
              Vé máy bay của nhiều hãng, tìm và đặt ở một nơi.
            </p>
          </div>
          <nav className="foot-col" aria-label="Đặt vé">
            <h3>Đặt vé</h3>
            <a href="#tim-chuyen">Tìm chuyến bay</a>
            <Link href="/bookings">Đặt chỗ của tôi</Link>
            <a href="#cach-dat-ve">Cách đặt vé</a>
          </nav>
          <nav className="foot-col" aria-label="Tài khoản">
            <h3>Tài khoản</h3>
            <Link href="/login">Đăng nhập</Link>
            <Link href="/register">Đăng ký</Link>
            <Link href="/forgot-password">Quên mật khẩu</Link>
          </nav>
          <nav className="foot-col" aria-label="Hỗ trợ">
            <h3>Hỗ trợ</h3>
            <a href="#ho-tro">Câu hỏi thường gặp</a>
            <a href="#doi-hoan">Đổi và hoàn vé</a>
          </nav>
        </div>
        <div className="foot-legal">
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
