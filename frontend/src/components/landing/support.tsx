import Link from "next/link";
import { Icon } from "./icons";
import Faq from "./faq";

export default function Support() {
  return (
    <section className="support on-dark" id="ho-tro" aria-labelledby="sup-h">
      <div className="wrap sup sec">
        <div className="sup-l">
          <h2 id="sup-h" className="h-sec rv">
            Hỗ trợ<span className="dot">.</span>
          </h2>
          <p className="body sup-p rv rv-2">
            Không nhận được email vé? Nhân viên SkyLine tra theo mã đặt chỗ,
            email hoặc số điện thoại rồi gửi lại cho bạn.
          </p>
          <Link className="pill pill-ghost rv rv-3" href="/bookings">
            Đặt chỗ của tôi
            <Icon name="upright" />
          </Link>
        </div>
        <div className="sup-r">
          <h3 className="faq-h">Câu hỏi thường gặp</h3>
          <Faq />
        </div>
      </div>
    </section>
  );
}
