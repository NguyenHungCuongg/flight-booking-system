"use client";

import { useGSAP } from "@gsap/react";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { useRef, type ReactNode } from "react";

gsap.registerPlugin(useGSAP, ScrollTrigger);

/**
 * Chuyển động theo cuộn của trang chủ (DESIGN.md §Motion): khối gắn `data-reveal` hiện dần khi cuộn tới,
 * máy bay `data-jet` ở hero bay lên khi cuộn qua. Hiệu ứng vào trang của hero là CSS (globals.css).
 * Nội dung chỉ bị ẩn từ JS, nên script lỗi thì trang vẫn hiện đủ.
 */
export default function LandingMotion({ children }: { children: ReactNode }) {
  const root = useRef<HTMLElement>(null);

  useGSAP(
    () => {
      const mm = gsap.matchMedia();
      mm.add(
        "(prefers-reduced-motion: no-preference)",
        () => {
          gsap.set("[data-reveal]", { opacity: 0, y: 28 });
          ScrollTrigger.batch("[data-reveal]", {
            start: "top 90%",
            once: true,
            onEnter: (els) =>
              gsap.to(els, {
                opacity: 1,
                y: 0,
                duration: 0.6,
                ease: "power2.out",
                stagger: 0.08,
                // Xoá style inline khi xong để khối trở về đúng CSS gốc.
                clearProps: "opacity,transform",
              }),
          });
          gsap.to("[data-jet]", {
            yPercent: -14,
            xPercent: 6,
            ease: "none",
            scrollTrigger: {
              trigger: "[aria-labelledby=hero-h]",
              start: "top top",
              end: "bottom top",
              scrub: true,
            },
          });
        },
        root,
      );
      return () => mm.revert();
    },
    { scope: root },
  );

  return <main ref={root}>{children}</main>;
}
