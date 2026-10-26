/** Chỗ giữ khi một trang đang tải: dùng cho loading.tsx và làm fallback của các Suspense bọc trang. */
export default function PageLoading() {
  return (
    <div role="status" className="flex min-h-dvh items-center justify-center">
      <span
        aria-hidden="true"
        className="size-36 rounded-full border-4 border-line border-t-accent motion-safe:animate-spin"
      />
      <span className="sr-only">Đang tải…</span>
    </div>
  );
}
