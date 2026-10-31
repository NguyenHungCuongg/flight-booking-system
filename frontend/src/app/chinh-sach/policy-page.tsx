import { readFileSync } from "node:fs";
import path from "node:path";
import Link from "next/link";
import { connection } from "next/server";
import { Suspense } from "react";
import SiteHeader from "@/components/site-header";
import {
  POLICIES,
  fillSettings,
  renderPolicy,
  sections,
  type PolicySlug,
  type PublicSettings,
} from "@/lib/policies";

// Server Component gọi thẳng backend: rewrite /api (next.config.ts) chỉ áp dụng cho request từ trình duyệt.
const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8080";

/** Thân văn bản: thay {{khoá}} bằng tham số đang áp dụng. Backend lỗi thì ném lỗi để app/error.tsx hiện trang lỗi. */
async function PolicyBody({ md }: { md: string }) {
  await connection(); // Gọi backend lúc có request, không gọi lúc build (next build chạy được khi backend tắt).
  const res = await fetch(`${BACKEND}/api/settings/public`);
  if (!res.ok)
    throw new Error(`Không đọc được tham số hệ thống (HTTP ${res.status})`);
  const settings = (await res.json()) as PublicSettings;
  return (
    <article
      className="policy"
      // HTML sinh từ file Markdown trong repo, không phải dữ liệu người dùng nhập, nên chèn thẳng là an toàn.
      dangerouslySetInnerHTML={{
        __html: renderPolicy(fillSettings(md, settings)),
      }}
    />
  );
}

/** Khung chung của ba trang chính sách: danh sách văn bản và mục lục nằm trong static shell, thân văn bản stream sau. */
export default function PolicyPage({ slug }: { slug: PolicySlug }) {
  const md = readFileSync(
    path.join(process.cwd(), "content/chinh-sach", `${slug}.md`),
    "utf8",
  );
  const title = POLICIES.find((p) => p.slug === slug)!.title;

  return (
    <div className="min-h-dvh">
      <SiteHeader />
      <main className="mx-auto grid max-w-page gap-28 px-16 pt-16 pb-80 sm:px-38 lg:grid-cols-[260px_minmax(0,1fr)] lg:gap-48 lg:pt-36">
        <aside className="grid min-w-0 content-start gap-28 lg:sticky lg:top-88 lg:self-start">
          <nav aria-label="Văn bản chính sách">
            <ul className="flex flex-wrap gap-8 text-caption lg:grid">
              {POLICIES.map((p) => (
                <li key={p.slug} className="shrink-0">
                  <Link
                    href={`/chinh-sach/${p.slug}`}
                    aria-current={p.slug === slug ? "page" : undefined}
                    className={
                      p.slug === slug
                        ? "block rounded-inner border border-accent bg-accent-soft px-11 py-6 font-semibold text-accent"
                        : "block rounded-inner border border-line bg-surface px-11 py-6 text-muted transition-colors hover:border-line-strong hover:text-ink"
                    }
                  >
                    {p.title}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
          <nav aria-label="Mục lục" className="hidden lg:block">
            <p className="mb-8 text-badge font-bold tracking-wide text-muted uppercase">
              Mục lục
            </p>
            <ol className="grid gap-6 text-caption">
              {sections(md).map((s) => (
                <li key={s.id}>
                  <a
                    href={`#${s.id}`}
                    className="text-muted transition-colors hover:text-ink"
                  >
                    {s.title}
                  </a>
                </li>
              ))}
            </ol>
          </nav>
        </aside>
        <div className="grid min-w-0 content-start gap-24">
          <h1 className="text-heading-lg font-bold">{title}</h1>
          <Suspense
            fallback={
              <p role="status" className="text-body text-muted">
                Đang tải văn bản…
              </p>
            }
          >
            <PolicyBody md={md} />
          </Suspense>
        </div>
      </main>
    </div>
  );
}
