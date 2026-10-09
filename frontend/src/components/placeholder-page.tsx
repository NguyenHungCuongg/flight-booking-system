import Link from "next/link";

/** Trang tạm cho màn hình chưa thiết kế. Thay bằng UI thật khi tới plan của màn đó. */
export default function PlaceholderPage({ code, title }: { code: string; title: string }) {
  return (
    <main className="mx-auto flex min-h-screen max-w-xl flex-col justify-center gap-4 px-6 py-16">
      <p className="text-sm text-neutral-500">{code}</p>
      <h1 className="text-4xl font-bold tracking-tight">{title}</h1>
      <p className="text-neutral-600">Màn hình này chưa được thiết kế.</p>
      <Link href="/" className="font-bold underline underline-offset-4">Về trang chủ</Link>
    </main>
  );
}
