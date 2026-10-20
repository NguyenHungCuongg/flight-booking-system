"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError, apiFetch } from "./api";

export type ApiResult<T> = { data: T } | { error: ApiError };

/**
 * Hook tải dữ liệu API theo đường dẫn `path`.
 * Dùng cho các màn chỉ đọc theo đường dẫn (Staff S-01–S-06). C-08, C-09 giữ cách tải riêng của chúng.
 * - Trả về `undefined` khi chưa có kết quả cho `path` hiện tại.
 * - Khi đổi `version`, dữ liệu được tải lại nhưng vẫn giữ kết quả cũ của cùng `path` trong lúc chờ.
 * - Nếu API trả 401, tự động chuyển hướng về `/login?next=...` (đường dẫn và query hiện tại).
 * - Bỏ qua phản hồi nếu `path` đã thay đổi trước khi API trả về (cờ `live`).
 */
export function useApi<T>(
  path: string,
  version: number = 0,
): ApiResult<T> | undefined {
  const router = useRouter();
  const [state, setState] = useState<{ path: string; result: ApiResult<T> } | null>(null);

  useEffect(() => {
    let live = true;
    apiFetch<T>(path)
      .then((data) => {
        if (!live) return;
        setState({ path, result: { data } });
      })
      .catch((err) => {
        if (!live) return;
        if (err instanceof ApiError && err.status === 401) {
          const next =
            typeof window !== "undefined"
              ? window.location.pathname + window.location.search
              : "/";
          router.replace(`/login?next=${encodeURIComponent(next)}`);
          return;
        }
        const error =
          err instanceof ApiError
            ? err
            : new ApiError(500, "NETWORK_ERROR", "Không thể kết nối tới máy chủ");
        setState({ path, result: { error } });
      });

    return () => {
      live = false;
    };
  }, [path, version, router]);

  return state?.path === path ? state.result : undefined;
}

