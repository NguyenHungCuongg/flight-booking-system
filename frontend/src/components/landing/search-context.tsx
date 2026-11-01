"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
} from "react";
import { apiFetch } from "@/lib/api";
import type { PublicSettings } from "@/lib/policies";
import { ROUTES, type CabinId } from "./data";

export type Trip = "oneway" | "round";

/** Phần biểu mẫu tìm chuyến mà nhiều khối trên trang cùng đọc hoặc ghi (ô tìm, tuyến gợi ý, thẻ đặt chỗ minh hoạ). */
export interface SearchForm {
  trip: Trip;
  from: string;
  to: string;
  dep: string | null;
  ret: string | null;
  adults: number;
  children: number;
  infants: number;
  cabin: CabinId;
  /** Bộ đếm để chạy lại animation "tick" của ô khi giá trị đổi. */
  fromPar: number;
  toPar: number;
}

export const pad = (n: number) => (n < 10 ? "0" : "") + n;
export const iso = (d: Date) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
export const parseIso = (s: string | null) => {
  if (!s) return null;
  const [y, m, d] = s.split("-").map(Number);
  return new Date(y, m - 1, d);
};
export const addDays = (d: Date, n: number) =>
  new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);

const subscribeNone = () => () => {};
const WD = [
  "Chủ Nhật",
  "Thứ Hai",
  "Thứ Ba",
  "Thứ Tư",
  "Thứ Năm",
  "Thứ Sáu",
  "Thứ Bảy",
];
export const weekday = (d: Date) => WD[d.getDay()];
export const ddmm = (d: Date) => `${pad(d.getDate())}/${pad(d.getMonth() + 1)}`;

const reducedMotion = () =>
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

interface SearchCtx {
  form: SearchForm;
  /** Ngày hôm nay (yyyy-mm-dd), null khi render ở server để không đọc giờ lúc prerender. */
  today: string | null;
  update: (
    patch: Partial<SearchForm> | ((f: SearchForm) => Partial<SearchForm>),
  ) => void;
  /** Tăng mỗi lần cần nháy viền ô tìm. */
  flash: number;
  /** Cuộn tới ô tìm, nháy viền và đặt focus vào ô `field` (mặc định ô Từ). */
  focusSearch: (field?: string) => void;
  pickRoute: (i: number) => void;
  /** Tham số Admin đổi được (GET /api/settings/public), undefined khi đang tải hoặc backend lỗi. */
  settings: PublicSettings | undefined;
}

const Ctx = createContext<SearchCtx | null>(null);

export function useSearch() {
  const c = useContext(Ctx);
  if (!c) throw new Error("useSearch phải nằm trong SearchProvider");
  return c;
}

/** `initial` ghi đè giá trị mặc định ban đầu của form, VD điều kiện tìm đọc từ URL ở C-02. */
export function SearchProvider({
  children,
  initial,
}: {
  children: React.ReactNode;
  initial?: Partial<SearchForm>;
}) {
  // getServerSnapshot trả null nên prerender không gọi new Date() (Cache Components cấm).
  const today = useSyncExternalStore(
    subscribeNone,
    () => iso(new Date()),
    () => null,
  );
  const [raw, setRaw] = useState<SearchForm>({
    trip: "oneway",
    from: "HAN",
    to: "SGN",
    dep: null,
    ret: null,
    adults: 1,
    children: 0,
    infants: 0,
    cabin: "ECONOMY",
    fromPar: 0,
    toPar: 0,
    ...initial,
  });
  const [flash, setFlash] = useState(0);
  const [settings, setSettings] = useState<PublicSettings>();
  useEffect(() => {
    let live = true;
    let timer: ReturnType<typeof setTimeout>;
    // Lỗi mạng thoáng qua thì thử lại vài lần, để các con số không kẹt ở "…".
    const load = (tries: number) =>
      apiFetch<PublicSettings>("/settings/public").then(
        (s) => live && setSettings(s),
        () => {
          if (live && tries > 1)
            timer = setTimeout(() => load(tries - 1), 3000);
        },
      );
    load(3);
    return () => {
      live = false;
      clearTimeout(timer);
    };
  }, []);

  // Ngày đi mặc định: 14 ngày sau hôm nay, cho tới khi người dùng chọn.
  const form = useMemo(
    () => ({
      ...raw,
      dep: raw.dep ?? (today ? iso(addDays(parseIso(today)!, 14)) : null),
    }),
    [raw, today],
  );

  const update = useCallback<SearchCtx["update"]>(
    (p) => setRaw((f) => ({ ...f, ...(typeof p === "function" ? p(f) : p) })),
    [],
  );

  const focusSearch = useCallback((field = "fld-from") => {
    setFlash((n) => n + 1);
    document.getElementById("tim-chuyen")?.scrollIntoView({
      behavior: reducedMotion() ? "auto" : "smooth",
      block: "center",
    });
    document.getElementById(field)?.focus({ preventScroll: true });
  }, []);

  const pickRoute = useCallback(
    (i: number) => {
      const r = ROUTES[i];
      update((f) => ({
        from: r.from,
        to: r.to,
        fromPar: f.fromPar + 1,
        toPar: f.toPar + 1,
      }));
      focusSearch("fld-dep");
    },
    [update, focusSearch],
  );

  const value = useMemo(
    () => ({ form, today, update, flash, focusSearch, pickRoute, settings }),
    [form, today, update, flash, focusSearch, pickRoute, settings],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}
