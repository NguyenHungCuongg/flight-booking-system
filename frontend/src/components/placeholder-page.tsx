import Link from "next/link";

/** Trang tạm cho màn hình chưa thiết kế. Thay bằng UI thật khi tới plan của màn đó. */
export default function PlaceholderPage({
  code,
  title,
}: {
  code: string;
  title: string;
}) {
  return (
    <main className="mx-auto flex min-h-dvh max-w-576 flex-col justify-center gap-16 px-22 py-68">
      <p className="text-caption text-muted">{code}</p>
      <h1 className="text-heading font-bold">{title}</h1>
      <p className="text-muted">Màn hình này chưa được thiết kế.</p>
      <Link href="/" className="font-bold underline underline-offset-4">
        Về trang chủ
      </Link>
    </main>
  );
}
