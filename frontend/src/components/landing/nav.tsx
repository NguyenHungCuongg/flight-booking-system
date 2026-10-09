"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Icon, Logo } from "./icons";

export default function Nav() {
  const [solid, setSolid] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  // Nền thanh điều hướng đặc dần khi cuộn qua phần đầu hero.
  useEffect(() => {
    const onScroll = () => setSolid(window.scrollY > 140);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const close = () => setMenuOpen(false);

  return (
    <header className={`nav${solid ? " is-solid" : ""}`}>
      <div className="nav-bg" aria-hidden="true" />
      <div className="wrap nav-in">
        <a className="logo" href="#top" aria-label="SkyLine, về đầu trang"><Logo /></a>
        <nav className="nav-links" aria-label="Điều hướng chính">
          <a className="nav-link" href="#cach-dat-ve">Cách đặt vé</a>
          <a className="nav-link" href="#ho-tro">Hỗ trợ</a>
          <Link className="nav-link" href="/bookings">Đặt chỗ của tôi</Link>
          <Link className="pill pill-dark" href="/login">Đăng nhập</Link>
        </nav>
        <button type="button" className="icon-btn solid menu-btn" aria-expanded={menuOpen} aria-controls="menu"
          aria-label={menuOpen ? "Đóng menu" : "Mở menu"} onClick={() => setMenuOpen((o) => !o)}>
          <Icon name={menuOpen ? "close" : "menu"} />
        </button>
      </div>
      <div className={`menu${menuOpen ? " is-open" : ""}`} id="menu" onKeyDown={(e) => e.key === "Escape" && close()}>
        <nav className="menu-links" aria-label="Menu">
          <a href="#cach-dat-ve" onClick={close}>Cách đặt vé</a>
          <a href="#ho-tro" onClick={close}>Hỗ trợ</a>
          <Link href="/bookings" onClick={close}>Đặt chỗ của tôi</Link>
        </nav>
        <div className="menu-cta">
          <Link className="pill pill-dark" href="/login" onClick={close}>Đăng nhập</Link>
          <Link className="pill pill-line" href="/register" onClick={close}>Đăng ký</Link>
        </div>
      </div>
    </header>
  );
}
