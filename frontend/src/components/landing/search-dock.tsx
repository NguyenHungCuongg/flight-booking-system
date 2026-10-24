"use client";

import { useRouter } from "next/navigation";
import { useCallback, useState, useTransition } from "react";
import { pillLine, pillPrimary } from "@/components/form";
import { countryName, useAirports, type Airport } from "@/lib/airports";
import { CABINS } from "./data";
import { Icon, Jet } from "./icons";
import {
  addDays,
  ddmm,
  iso,
  parseIso,
  useSearch,
  weekday,
} from "./search-context";

type Panel = "from" | "to" | "dep" | "ret" | "pax" | null;
type Field = "from" | "to" | "dep" | "ret";

interface Local {
  panel: Panel;
  popUp: boolean;
  query: string;
  hi: number;
  calMonth: number | null;
  swapTurns: number;
  paxKey: string;
  paxNote: string;
  errors: Partial<Record<Field, string>>;
  status: string;
}

const monthIdx = (d: Date) => d.getFullYear() * 12 + d.getMonth();
const norm = (s: string) =>
  s
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .trim();

function filterAirports(
  airports: Airport[],
  query: string,
  other: string,
): Airport[] {
  const q = norm(query);
  let list = airports.filter((a) => a.code !== other);
  if (q) {
    const Q = q.toUpperCase();
    list = list
      .map((a) => {
        const city = norm(a.city);
        const name = norm(a.name);
        let score = -1;
        if (a.code === Q) score = 0;
        else if (a.code.startsWith(Q)) score = 1;
        else if (city.startsWith(q)) score = 2;
        else if (city.includes(q) || name.includes(q)) score = 3;
        else if (norm(countryName(a.countryCode)).includes(q)) score = 4;
        return { a, score };
      })
      .filter((x) => x.score > -1)
      .sort((x, y) => x.score - y.score)
      .map((x) => x.a);
  }
  return list.slice(0, 8);
}

const focusOnMount = (el: HTMLInputElement | null) =>
  el?.focus({ preventScroll: true });

const iconCircleBtn =
  "inline-grid size-40 shrink-0 place-items-center rounded-inner border border-line-strong bg-surface text-ink transition hover:border-ink hover:bg-accent-soft active:scale-95 disabled:cursor-not-allowed disabled:opacity-35";

export default function SearchDock() {
  const router = useRouter();
  const [pending, startNav] = useTransition();
  const { form, today: todayIso, update, flash } = useSearch();
  const { airports, failed } = useAirports();
  const [s, setS] = useState<Local>({
    panel: null,
    popUp: false,
    query: "",
    hi: 0,
    calMonth: null,
    swapTurns: 0,
    paxKey: "",
    paxNote: "",
    errors: {},
    status: "",
  });
  const patch = useCallback(
    (p: Partial<Local>) => setS((prev) => ({ ...prev, ...p })),
    [],
  );

  const today = todayIso ? parseIso(todayIso) : null;
  const byCode = (code: string) =>
    airports?.find((a) => a.code === code) ?? null;
  const F = byCode(form.from);
  const T = byCode(form.to);
  const depD = parseIso(form.dep);
  const retD = parseIso(form.ret);
  const oneway = form.trip === "oneway";
  const err = s.errors;
  const isAp = s.panel === "from" || s.panel === "to";
  const isCal = s.panel === "dep" || s.panel === "ret";
  const list =
    isAp && airports
      ? filterAirports(
          airports,
          s.query,
          s.panel === "from" ? form.to : form.from,
        )
      : [];
  const empty = failed
    ? "Không tải được danh sách sân bay. Hãy tải lại trang."
    : !airports
      ? "Đang tải danh sách sân bay..."
      : "Không tìm thấy sân bay phù hợp. Thử gõ tên thành phố hoặc mã 3 chữ cái, ví dụ DAD.";
  const hi = Math.min(s.hi, Math.max(0, list.length - 1));
  const cabin = CABINS.find((c) => c.id === form.cabin) ?? CABINS[0];
  const seated = form.adults + form.children;
  const total = seated + form.infants;
  const clearErr = (...keys: Field[]) =>
    Object.fromEntries(
      Object.entries(s.errors).filter(([k]) => !keys.includes(k as Field)),
    );

  /* ---------- panels ---------- */
  const open = (panel: Exclude<Panel, null>) => {
    const dock = document.getElementById("tim-chuyen");
    const popUp =
      !!dock &&
      window.innerWidth >= 640 &&
      dock.getBoundingClientRect().top > window.innerHeight * 0.52;
    const next = s.panel === panel ? null : panel;
    let calMonth = s.calMonth;
    if (next === "dep") calMonth = depD ? monthIdx(depD) : null;
    if (next === "ret")
      calMonth = retD || depD ? monthIdx((retD ?? depD)!) : null;
    patch({ panel: next, popUp, query: "", hi: 0, calMonth });
  };
  const closePanel = () => patch({ panel: null });
  const onPopKey = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") {
      e.stopPropagation();
      closePanel();
    }
  };

  /* ---------- route & dates ---------- */
  const setTrip = (t: "oneway" | "round") => {
    update({ trip: t, ret: t === "oneway" ? null : form.ret });
    patch({ panel: null, errors: clearErr("ret") });
  };
  const openRet = () => {
    if (oneway) update({ trip: "round" });
    open("ret");
  };
  const onQueryKey = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      patch({ hi: Math.min(Math.max(list.length - 1, 0), s.hi + 1) });
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      patch({ hi: Math.max(0, s.hi - 1) });
    } else if (e.key === "Enter") {
      e.preventDefault();
      const a = list[hi];
      if (a) pickAirport(a.code);
    } else if (e.key === "Escape") {
      e.preventDefault();
      e.stopPropagation();
      closePanel();
    }
  };
  const pickAirport = (code: string) => {
    const side = s.panel;
    if (side !== "from" && side !== "to") return;
    update((f) =>
      side === "from"
        ? { from: code, fromPar: f.fromPar + 1 }
        : { to: code, toPar: f.toPar + 1 },
    );
    const nextPanel: Panel =
      side === "from" && !form.to
        ? "to"
        : side === "to" && !form.dep
          ? "dep"
          : null;
    patch({
      panel: nextPanel,
      query: "",
      hi: 0,
      errors: clearErr("from", "to"),
    });
  };
  const swap = () => {
    update((f) => ({
      from: f.to,
      to: f.from,
      fromPar: f.fromPar + 1,
      toPar: f.toPar + 1,
    }));
    patch({ swapTurns: s.swapTurns + 1, errors: clearErr("from", "to") });
  };
  const shiftMonth = (n: number) => {
    if (!today) return;
    const first = monthIdx(today);
    patch({
      calMonth: Math.min(
        first + 12,
        Math.max(first, (s.calMonth ?? first) + n),
      ),
    });
  };
  const pickDate = (day: string) => {
    if (s.panel === "dep") {
      const keep = !!(form.ret && form.ret >= day);
      update({ dep: day, ret: keep ? form.ret : null });
      patch({
        panel: !oneway && !keep ? "ret" : null,
        errors: clearErr("dep"),
      });
    } else if (s.panel === "ret") {
      update({ ret: day });
      patch({ panel: null, errors: clearErr("ret") });
    }
  };

  /* ---------- hành khách ---------- */
  const pax = (kind: "adults" | "children" | "infants", delta: number) => {
    let { adults, children, infants } = form;
    let note = "";
    if (kind === "adults")
      adults = Math.min(9 - children, Math.max(1, adults + delta));
    if (kind === "children")
      children = Math.min(9 - adults, Math.max(0, children + delta));
    if (kind === "infants")
      infants = Math.min(adults, Math.max(0, infants + delta));
    if (infants > adults) {
      infants = adults;
      note = "Đã bớt số em bé vì mỗi người lớn chỉ đi kèm một em bé.";
    }
    update({ adults, children, infants });
    patch({ paxNote: note, paxKey: kind });
  };
  let paxNote = s.paxNote;
  if (!paxNote && seated >= 9)
    paxNote =
      "Tối đa 9 khách chiếm ghế trong một lần đặt. Em bé không chiếm ghế.";
  else if (!paxNote && form.infants > 0 && form.infants >= form.adults)
    paxNote = "Mỗi người lớn đi kèm tối đa một em bé.";

  /* ---------- gửi ---------- */
  const submit = () => {
    if (pending) return;
    const errors: Local["errors"] = {};
    if (!form.from) errors.from = "Chọn sân bay đi.";
    if (!form.to) errors.to = "Chọn sân bay đến.";
    else if (form.from === form.to) errors.to = "Điểm đến phải khác điểm đi.";
    if (!form.dep) errors.dep = "Chọn ngày đi.";
    if (!oneway && !form.ret) errors.ret = "Chọn ngày về.";
    if (Object.keys(errors).length) {
      patch({
        errors,
        panel: null,
        status: "Kiểm tra lại các ô được đánh dấu.",
      });
      return;
    }
    const q = new URLSearchParams({
      from: form.from,
      to: form.to,
      date: form.dep!,
      adults: String(form.adults),
      children: String(form.children),
      infants: String(form.infants),
      cabin: form.cabin,
    });
    if (!oneway) q.set("returnDate", form.ret!);
    patch({
      errors: {},
      panel: null,
      status: `Đang tìm chuyến ${form.from} đến ${form.to}.`,
    });
    startNav(() => router.push(`/flights?${q}`));
  };

  /* ---------- lịch ---------- */
  const calendar = () => {
    if (!today) return { cells: [], title: "", prevDis: true, nextDis: true };
    const first = monthIdx(today);
    const cm = s.calMonth ?? first;
    const y = Math.floor(cm / 12);
    const m = cm % 12;
    const offset = (new Date(y, m, 1).getDay() + 6) % 7;
    const days = new Date(y, m + 1, 0).getDate();
    const todayStr = iso(today);
    const maxStr = iso(addDays(today, 365));
    const minStr = s.panel === "ret" && form.dep ? form.dep : todayStr;
    const cells = Array.from({ length: 42 }, (_, i) => {
      const n = i - offset + 1;
      if (n < 1 || n > days) return null;
      const d = new Date(y, m, n);
      const day = iso(d);
      const sel = day === form.dep || day === form.ret;
      const inRange = !!(form.dep && form.ret && day > form.dep && day < form.ret);
      const isToday = day === todayStr;
      return {
        n,
        day,
        sel,
        inRange,
        isToday,
        disabled: day < minStr || day > maxStr,
        label: `${weekday(d)}, ${n} tháng ${m + 1} năm ${y}`,
      };
    });
    return {
      cells,
      title: `Tháng ${m + 1}, ${y}`,
      prevDis: cm <= first,
      nextDis: cm >= first + 12,
    };
  };
  const cal = isCal ? calendar() : null;

  const popPos = (alignRight = false) =>
    `max-sm:mt-10 sm:absolute sm:z-30 ${
      s.popUp ? "sm:bottom-[calc(100%+10px)]" : "sm:top-[calc(100%+10px)]"
    } ${alignRight ? "sm:right-0" : "sm:left-0"}`;

  return (
    <div
      id="tim-chuyen"
      role="search"
      aria-label="Tìm chuyến bay"
      className={`card relative grid gap-14 p-16 sm:p-20 transition-shadow duration-300 ${
        flash ? "ring-2 ring-accent" : ""
      }`}
    >
      {s.panel && (
        <button
          type="button"
          className="fixed inset-0 z-20 cursor-default bg-transparent max-sm:hidden"
          tabIndex={-1}
          aria-hidden="true"
          onClick={closePanel}
        />
      )}

      {/* Top controls: Segmented trip switch + Passenger/Cabin picker + International notice */}
      <div className="relative z-25 flex flex-wrap items-center gap-10">
        <div
          className="inline-grid grid-cols-2 rounded-inner border border-line bg-tint p-4"
          role="group"
          aria-label="Loại hành trình"
        >
          <button
            type="button"
            aria-pressed={oneway}
            onClick={() => setTrip("oneway")}
            className={`min-h-36 rounded-inner px-16 py-7 text-caption font-bold transition-colors ${
              oneway
                ? "bg-cta text-cta-fg shadow-xs"
                : "text-ink-2 hover:text-ink"
            }`}
          >
            Một chiều
          </button>
          <button
            type="button"
            aria-pressed={!oneway}
            onClick={() => setTrip("round")}
            className={`min-h-36 rounded-inner px-16 py-7 text-caption font-bold transition-colors ${
              !oneway
                ? "bg-cta text-cta-fg shadow-xs"
                : "text-ink-2 hover:text-ink"
            }`}
          >
            Khứ hồi
          </button>
        </div>

        <div className="relative">
          <button
            type="button"
            className="inline-flex min-h-42 items-center gap-10 rounded-inner border border-line-strong bg-surface px-16 text-caption font-bold text-ink transition hover:border-ink hover:bg-accent-soft"
            aria-expanded={s.panel === "pax"}
            aria-controls="pax-pop"
            onClick={() => open("pax")}
          >
            <Icon name="users" className="size-18 shrink-0" />
            <span>
              {total} khách, {cabin.label}
            </span>
            <Icon
              name="chev"
              className={`size-16 shrink-0 transition-transform duration-200 ${
                s.panel === "pax" ? "rotate-180" : ""
              }`}
            />
          </button>
          {s.panel === "pax" && (
            <div
              id="pax-pop"
              role="dialog"
              aria-label="Hành khách và hạng ghế"
              onKeyDown={onPopKey}
              className={`${popPos(false)} w-full rounded-card border border-line-strong bg-surface p-18 shadow-card-hover sm:w-420`}
            >
              <div className="divide-y divide-line">
                <PaxRow
                  title="Người lớn"
                  desc="Từ 12 tuổi"
                  noun="người lớn"
                  value={form.adults}
                  decDis={form.adults <= 1}
                  incDis={seated >= 9}
                  onDec={() => pax("adults", -1)}
                  onInc={() => pax("adults", 1)}
                />
                <PaxRow
                  title="Trẻ em"
                  desc="Từ 2 đến dưới 12 tuổi"
                  noun="trẻ em"
                  value={form.children}
                  decDis={form.children <= 0}
                  incDis={seated >= 9}
                  onDec={() => pax("children", -1)}
                  onInc={() => pax("children", 1)}
                />
                <PaxRow
                  title="Em bé"
                  desc="Dưới 2 tuổi, ngồi cùng người lớn"
                  noun="em bé"
                  value={form.infants}
                  decDis={form.infants <= 0}
                  incDis={form.infants >= form.adults}
                  onDec={() => pax("infants", -1)}
                  onInc={() => pax("infants", 1)}
                />
              </div>
              {paxNote && (
                <p
                  className="mt-12 flex items-start gap-8 rounded-inner bg-accent-soft/60 px-12 py-8 text-badge text-ink"
                  role="status"
                >
                  <Icon name="info" className="mt-1 size-16 shrink-0 text-accent" />
                  <span>{paxNote}</span>
                </p>
              )}
              <fieldset className="mt-16 border-0 p-0">
                <legend className="mb-10 text-caption font-bold text-ink">
                  Hạng ghế
                </legend>
                <div className="flex flex-wrap gap-8">
                  {CABINS.map((c) => {
                    const active = c.id === form.cabin;
                    return (
                      <button
                        key={c.id}
                        type="button"
                        aria-pressed={active}
                        onClick={() => update({ cabin: c.id })}
                        className={`min-h-36 rounded-inner border px-14 py-7 text-caption font-bold transition ${
                          active
                            ? "border-cta bg-cta text-cta-fg"
                            : "border-line-strong bg-surface text-ink hover:border-ink"
                        }`}
                      >
                        {c.label}
                      </button>
                    );
                  })}
                </div>
              </fieldset>
              <div className="mt-16 flex justify-end">
                <button
                  type="button"
                  className={pillPrimary}
                  onClick={closePanel}
                >
                  Xong
                </button>
              </div>
            </div>
          )}
        </div>

        {F && T && F.countryCode !== T.countryCode && (
          <p className="flex max-w-520 items-center gap-8 rounded-inner bg-accent-soft/65 px-12 py-8 text-badge text-ink-2 lg:ml-auto">
            <Icon name="info" className="size-18 shrink-0 text-accent" />
            <span>
              Hành trình quốc tế: hộ chiếu của mọi hành khách phải còn hạn ít
              nhất 6 tháng tính từ ngày bay chặng cuối.
            </span>
          </p>
        )}
      </div>

      {/* Main search fields grid */}
      <div className="relative z-25 grid rounded-inner border border-line bg-paper/45 lg:grid-cols-[minmax(0,2.4fr)_minmax(0,1.9fr)_auto]">
        {/* Route: From + Swap + To */}
        <div className="relative grid border-b border-line sm:grid-cols-[minmax(0,1fr)_52px_minmax(0,1fr)] lg:border-b-0">
          <div className="relative min-w-0">
            <FieldBtn
              label="Từ"
              open={s.panel === "from"}
              onClick={() => open("from")}
              error={err.from}
              main={form.from || "Chọn sân bay"}
              sub={F?.city ?? (form.from ? "\u00a0" : "Điểm đi")}
              isEmpty={!form.from}
            />
            {s.panel === "from" && (
              <AirportPop
                title="Bay từ"
                aria="Chọn sân bay đi"
                posClass={popPos(false)}
                query={s.query}
                list={list}
                empty={empty}
                hi={hi}
                onQuery={(q) => patch({ query: q, hi: 0 })}
                onKey={onQueryKey}
                onPopKey={onPopKey}
                onPick={pickAirport}
                onClose={closePanel}
              />
            )}
          </div>

          <div className="flex items-center justify-center max-sm:py-4">
            <button
              type="button"
              className="grid size-38 place-items-center rounded-inner border border-line-strong bg-surface text-ink transition hover:border-ink hover:bg-accent-soft active:scale-95"
              aria-label="Đảo điểm đi và điểm đến"
              onClick={swap}
              style={{ transform: `rotate(${s.swapTurns * 180}deg)` }}
            >
              <Icon name="swap" className="size-18 shrink-0 max-sm:rotate-90" />
            </button>
          </div>

          <div className="relative min-w-0 max-sm:border-t max-sm:border-line">
            <FieldBtn
              label="Đến"
              open={s.panel === "to"}
              onClick={() => open("to")}
              error={err.to}
              main={form.to || "Chọn sân bay"}
              sub={T?.city ?? (form.to ? "\u00a0" : "Điểm đến")}
              isEmpty={!form.to}
            />
            {s.panel === "to" && (
              <AirportPop
                title="Bay đến"
                aria="Chọn sân bay đến"
                posClass={popPos(true)}
                query={s.query}
                list={list}
                empty={empty}
                hi={hi}
                onQuery={(q) => patch({ query: q, hi: 0 })}
                onKey={onQueryKey}
                onPopKey={onPopKey}
                onPick={pickAirport}
                onClose={closePanel}
              />
            )}
          </div>
        </div>

        {/* Dates: Departure + Return */}
        <div className="grid grid-cols-2 border-b border-line lg:border-b-0 lg:border-l">
          <div className="relative min-w-0">
            <FieldBtn
              label="Ngày đi"
              open={s.panel === "dep"}
              onClick={() => open("dep")}
              error={err.dep}
              main={depD ? ddmm(depD) : "Chọn ngày"}
              sub={depD ? weekday(depD) : "Chưa chọn"}
              isEmpty={!depD}
            />
            {s.panel === "dep" && cal && (
              <CalendarPop
                aria="Chọn ngày đi"
                hint="Chọn ngày đi"
                posClass={popPos(false)}
                cal={cal}
                onShift={shiftMonth}
                onPick={pickDate}
                onKey={onPopKey}
                onClose={closePanel}
              />
            )}
          </div>

          <div className="relative min-w-0 border-l border-line">
            <FieldBtn
              label="Ngày về"
              open={s.panel === "ret"}
              onClick={openRet}
              error={err.ret}
              main={oneway ? "Thêm ngày về" : retD ? ddmm(retD) : "Chọn ngày"}
              sub={
                oneway ? "Không bắt buộc" : retD ? weekday(retD) : "Chưa chọn"
              }
              isEmpty={oneway || !retD}
            />
            {s.panel === "ret" && cal && (
              <CalendarPop
                aria="Chọn ngày về"
                hint="Chọn ngày về"
                posClass={popPos(true)}
                cal={cal}
                onShift={shiftMonth}
                onPick={pickDate}
                onKey={onPopKey}
                onClose={closePanel}
              />
            )}
          </div>
        </div>

        {/* Submit CTA */}
        <div className="flex items-center p-10 lg:border-l lg:border-line">
          <button
            type="button"
            className={`${pillPrimary} min-h-52 w-full px-24 text-body font-bold lg:w-auto`}
            aria-busy={pending}
            onClick={submit}
          >
            <span>{pending ? "Đang tìm" : "Tìm chuyến bay"}</span>
            {pending ? (
              <Jet className="size-20 shrink-0 animate-pulse" />
            ) : (
              <Icon name="arrow" className="size-20 shrink-0" />
            )}
          </button>
        </div>
      </div>

      <p className="sr-only" role="status">
        {s.status}
      </p>
    </div>
  );
}

function FieldBtn(p: {
  label: string;
  open: boolean;
  onClick: () => void;
  main: string;
  sub: string;
  isEmpty: boolean;
  error?: string;
}) {
  return (
    <button
      type="button"
      aria-expanded={p.open}
      onClick={p.onClick}
      className={`grid h-full w-full content-start gap-2 rounded-inner px-16 py-12 text-left transition-colors ${
        p.open ? "bg-accent-soft/75" : "hover:bg-tint/70"
      } ${p.error ? "ring-2 ring-inset ring-danger" : ""}`}
    >
      <span
        className={`text-badge ${
          p.error ? "font-bold text-danger" : "font-medium text-muted"
        }`}
      >
        {p.label}
      </span>
      <span
        className={
          p.isEmpty
            ? "pt-4 text-body font-semibold text-muted"
            : "truncate text-heading-sm font-black tracking-tight text-ink"
        }
      >
        {p.main}
      </span>
      <span className="truncate text-badge text-muted">{p.sub}</span>
      {p.error && (
        <span className="mt-4 flex items-center gap-6 text-badge font-bold text-danger">
          <Icon name="alert" className="size-16 shrink-0" />
          {p.error}
        </span>
      )}
    </button>
  );
}

function PaxRow(p: {
  title: string;
  desc: string;
  noun: string;
  value: number;
  decDis: boolean;
  incDis: boolean;
  onDec: () => void;
  onInc: () => void;
}) {
  return (
    <div className="flex items-center justify-between gap-16 py-12 first:pt-0">
      <div>
        <p className="text-body font-bold text-ink">{p.title}</p>
        <p className="text-badge text-muted">{p.desc}</p>
      </div>
      <div className="flex items-center gap-8">
        <button
          type="button"
          className={iconCircleBtn}
          aria-label={`Bớt một ${p.noun}`}
          disabled={p.decDis}
          onClick={p.onDec}
        >
          <Icon name="minus" className="size-18 shrink-0" />
        </button>
        <span className="min-w-32 text-center text-subheading font-black text-ink tabular-nums">
          {p.value}
        </span>
        <button
          type="button"
          className={iconCircleBtn}
          aria-label={`Thêm một ${p.noun}`}
          disabled={p.incDis}
          onClick={p.onInc}
        >
          <Icon name="plus" className="size-18 shrink-0" />
        </button>
      </div>
    </div>
  );
}

function AirportPop(p: {
  title: string;
  aria: string;
  posClass: string;
  query: string;
  list: Airport[];
  empty: string;
  hi: number;
  onQuery: (q: string) => void;
  onKey: (e: React.KeyboardEvent) => void;
  onPopKey: (e: React.KeyboardEvent) => void;
  onPick: (code: string) => void;
  onClose: () => void;
}) {
  return (
    <div
      role="dialog"
      aria-label={p.aria}
      onKeyDown={p.onPopKey}
      className={`${p.posClass} w-full rounded-card border border-line-strong bg-surface p-18 shadow-card-hover sm:w-400`}
    >
      <div className="mb-12 flex items-center justify-between gap-12">
        <label className="text-body font-bold text-ink" htmlFor="ap-q">
          {p.title}
        </label>
        <button
          type="button"
          className={iconCircleBtn}
          aria-label="Đóng"
          onClick={p.onClose}
        >
          <Icon name="close" className="size-18 shrink-0" />
        </button>
      </div>
      <div className="flex min-h-46 items-center gap-10 rounded-inner border border-line-strong bg-paper/50 px-14 focus-within:border-accent focus-within:ring-2 focus-within:ring-accent-soft">
        <Icon name="search" className="size-18 shrink-0 text-muted" />
        <input
          id="ap-q"
          className="min-w-0 flex-1 bg-transparent py-10 text-body text-ink outline-none placeholder:text-muted"
          type="text"
          value={p.query}
          ref={focusOnMount}
          placeholder="Thành phố, sân bay hoặc mã"
          autoComplete="off"
          spellCheck={false}
          aria-describedby="ap-count"
          onChange={(e) => p.onQuery(e.target.value)}
          onKeyDown={p.onKey}
        />
      </div>
      <p className="sr-only" id="ap-count" aria-live="polite">
        {p.list.length
          ? `${p.list.length} sân bay phù hợp`
          : "Không có sân bay phù hợp"}
      </p>
      <div className="mt-10 grid max-h-320 overflow-y-auto overscroll-contain">
        {p.list.map((a, i) => (
          <button
            key={a.code}
            type="button"
            onClick={() => p.onPick(a.code)}
            className={`grid min-h-56 w-full grid-cols-[56px_minmax(0,1fr)_auto] items-center gap-12 rounded-inner px-10 py-8 text-left transition-colors ${
              i === p.hi
                ? "bg-accent-soft/80 shadow-[inset_3px_0_0_var(--color-accent)]"
                : "hover:bg-tint"
            }`}
          >
            <span className="text-subheading font-black tracking-tight text-accent">
              {a.code}
            </span>
            <span className="grid min-w-0">
              <span className="text-body font-bold text-ink">{a.city}</span>
              <span className="truncate text-badge text-muted">{a.name}</span>
            </span>
            <span className="text-badge font-medium text-muted">
              {a.countryCode === "VN" ? "" : countryName(a.countryCode)}
            </span>
          </button>
        ))}
        {p.list.length === 0 && (
          <p className="px-6 py-14 text-caption text-muted">{p.empty}</p>
        )}
      </div>
    </div>
  );
}

function CalendarPop(p: {
  aria: string;
  hint: string;
  posClass: string;
  cal: {
    cells: ({
      n: number;
      day: string;
      sel: boolean;
      inRange: boolean;
      isToday: boolean;
      disabled: boolean;
      label: string;
    } | null)[];
    title: string;
    prevDis: boolean;
    nextDis: boolean;
  };
  onShift: (n: number) => void;
  onPick: (day: string) => void;
  onKey: (e: React.KeyboardEvent) => void;
  onClose: () => void;
}) {
  return (
    <div
      role="dialog"
      aria-label={p.aria}
      onKeyDown={p.onKey}
      className={`${p.posClass} w-full rounded-card border border-line-strong bg-surface p-18 shadow-card-hover sm:w-356`}
    >
      <div className="flex items-center justify-between gap-10">
        <button
          type="button"
          className={iconCircleBtn}
          aria-label="Tháng trước"
          disabled={p.cal.prevDis}
          onClick={() => p.onShift(-1)}
        >
          <Icon name="left" className="size-18 shrink-0" />
        </button>
        <p className="text-body font-bold text-ink" aria-live="polite">
          {p.cal.title}
        </p>
        <button
          type="button"
          className={iconCircleBtn}
          aria-label="Tháng sau"
          disabled={p.cal.nextDis}
          onClick={() => p.onShift(1)}
        >
          <Icon name="right" className="size-18 shrink-0" />
        </button>
      </div>
      <p className="mt-4 mb-12 text-center text-badge text-muted">{p.hint}</p>
      <div
        className="grid grid-cols-7 text-center text-badge font-semibold text-muted"
        aria-hidden="true"
      >
        {["T2", "T3", "T4", "T5", "T6", "T7", "CN"].map((d) => (
          <span key={d} className="grid h-32 place-items-center">
            {d}
          </span>
        ))}
      </div>
      <div className="grid grid-cols-7 gap-y-2">
        {p.cal.cells.map((c, i) =>
          c ? (
            <button
              key={c.day}
              type="button"
              disabled={c.disabled}
              aria-pressed={c.sel}
              aria-label={c.label}
              onClick={() => p.onPick(c.day)}
              className={`grid h-42 w-full place-items-center text-caption font-bold tabular-nums transition ${
                c.sel
                  ? "rounded-inner bg-cta text-cta-fg"
                  : c.inRange
                    ? "bg-accent-soft text-ink"
                    : "rounded-inner text-ink hover:bg-tint"
              } ${
                c.isToday && !c.sel
                  ? "ring-1 ring-inset ring-line-strong"
                  : ""
              } disabled:cursor-not-allowed disabled:font-normal disabled:text-muted disabled:opacity-40 disabled:hover:bg-transparent`}
            >
              {c.n}
            </button>
          ) : (
            <span key={`b${i}`} className="h-42 w-full" />
          ),
        )}
      </div>
      <div className="mt-14 flex justify-end">
        <button type="button" className={pillLine} onClick={p.onClose}>
          Xong
        </button>
      </div>
    </div>
  );
}
