/**
 * Văn bản chính sách (spec 2026-10-31-chinh-sach-skyline-design): Markdown trong content/chinh-sach/, số Admin sửa
 * được viết dạng {{khoá}} và được thay bằng GET /api/settings/public. Không import node:fs để test chạy thuần.
 */

import { Marked } from "marked";

export type PolicySlug = "dieu-khoan" | "quyen-rieng-tu" | "hoan-doi-hanh-ly";

export const POLICIES: readonly { slug: PolicySlug; title: string }[] = [
  { slug: "dieu-khoan", title: "Điều khoản & điều kiện" },
  { slug: "quyen-rieng-tu", title: "Chính sách quyền riêng tư" },
  { slug: "hoan-doi-hanh-ly", title: "Chính sách hoàn, đổi vé và hành lý" },
];

/** Cùng danh sách với PublicSettingsController của backend. */
export const SETTING_KEYS = [
  "booking.hold_minutes",
  "booking.min_hours_before_departure",
  "booking.max_seated_passengers",
  "pricing.child_percent",
  "pricing.infant_percent",
  "aftersales.min_hours_before_departure",
  "search.min_connection_minutes",
  "search.max_connection_minutes",
] as const;

export type PublicSettings = Record<string, number>;

export const PLACEHOLDER = /\{\{([a-z_.]+)\}\}/g;

/** Khoá lạ hoặc backend không trả giá trị thì báo lỗi, không để khách thấy {{...}}. */
export function fillSettings(md: string, settings: PublicSettings): string {
  return md.replace(PLACEHOLDER, (_, key: string) => {
    const value = settings[key];
    if (
      !(SETTING_KEYS as readonly string[]).includes(key) ||
      value === undefined
    )
      throw new Error(`Thiếu tham số ${key}`);
    return String(value);
  });
}

/** "Đổi chuyến" → "doi-chuyen": id cho link neo tới từng mục. */
export function headingId(text: string): string {
  return text
    .toLowerCase()
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .replace(/đ/g, "d")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}

/** Các mục cấp 2 (## ) theo thứ tự, dùng làm mục lục. */
export function sections(md: string): { id: string; title: string }[] {
  return [...md.matchAll(/^## (.+)$/gm)].map(([, title]) => ({
    id: headingId(title.trim()),
    title: title.trim(),
  }));
}

const marked = new Marked({
  renderer: {
    heading({ tokens, depth, text }) {
      return `<h${depth} id="${headingId(text)}">${this.parser.parseInline(tokens)}</h${depth}>\n`;
    },
  },
});

export function renderPolicy(md: string): string {
  return marked.parse(md, { async: false });
}
