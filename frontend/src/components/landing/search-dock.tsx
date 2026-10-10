"use client";

import { useRouter } from "next/navigation";
import { useCallback, useState, useTransition } from "react";
import { AIRPORTS, CABINS, airportByCode, type Airport } from "./data";
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
  depPar: number;
  retPar: number;
  paxPar: number;
  paxKey: string;
  paxNote: string;
  errors: Partial<Record<Field, string>>;
  shake: number;
  status: string;
}

const monthIdx = (d: Date) => d.getFullYear() * 12 + d.getMonth();
const tick = (n: number) => (n > 0 ? (n % 2 ? "tick-a" : "tick-b") : "");
const norm = (s: string) =>
  s
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d")
    .trim();

function filterAirports(query: string, other: string): Airport[] {
  const q = norm(query);
  let list = AIRPORTS.filter((a) => a.code !== other);
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
        else if (norm(a.country).includes(q)) score = 4;
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

export default function SearchDock() {
  const router = useRouter();
  const [pending, startNav] = useTransition();
  const { form, today: todayIso, update, flash } = useSearch();
  const [s, setS] = useState<Local>({
    panel: null,
    popUp: false,
    query: "",
    hi: 0,
    calMonth: null,
    swapTurns: 0,
    depPar: 0,
    retPar: 0,
    paxPar: 0,
    paxKey: "",
    paxNote: "",
    errors: {},
    shake: 0,
    status: "",
  });
  const patch = useCallback(
    (p: Partial<Local>) => setS((prev) => ({ ...prev, ...p })),
    [],
  );

  const today = todayIso ? parseIso(todayIso) : null;
  const F = airportByCode(form.from);
  const T = airportByCode(form.to);
  const depD = parseIso(form.dep);
  const retD = parseIso(form.ret);
  const oneway = form.trip === "oneway";
  const err = s.errors;
  const isAp = s.panel === "from" || s.panel === "to";
  const isCal = s.panel === "dep" || s.panel === "ret";
  const list = isAp
    ? filterAirports(s.query, s.panel === "from" ? form.to : form.from)
    : [];
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
        depPar: s.depPar + 1,
        panel: !oneway && !keep ? "ret" : null,
        errors: clearErr("dep"),
      });
    } else if (s.panel === "ret") {
      update({ ret: day });
      patch({ retPar: s.retPar + 1, panel: null, errors: clearErr("ret") });
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
    patch({ paxNote: note, paxPar: s.paxPar + 1, paxKey: kind });
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
        shake: s.shake + 1,
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
      let cls = "day";
      if (form.dep && form.ret && day > form.dep && day < form.ret)
        cls += " is-range";
      if (day === todayStr) cls += " is-today";
      if (sel) cls += " is-sel";
      return {
        n,
        day,
        cls,
        sel,
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

  const dockCls = [
    "dock",
    s.popUp && "pop-up",
    flash && (flash % 2 ? "flash-a" : "flash-b"),
    s.shake && (s.shake % 2 ? "shake-a" : "shake-b"),
  ]
    .filter(Boolean)
    .join(" ");
  const fieldCls = (k: Field) =>
    `field field-${k}${s.panel === k ? " is-open" : ""}${err[k] ? " has-err" : ""}`;

  return (
    <div
      className={dockCls}
      id="tim-chuyen"
      role="search"
      aria-label="Tìm chuyến bay"
    >
      {s.panel && (
        <button
          type="button"
          className="scrim"
          tabIndex={-1}
          aria-hidden="true"
          onClick={closePanel}
        />
      )}

      <div className="dock-bar">
        <div
          className={`seg${oneway ? "" : " is-round"}`}
          role="group"
          aria-label="Loại hành trình"
        >
          <span className="seg-ind" aria-hidden="true" />
          <button
            type="button"
            aria-pressed={oneway}
            onClick={() => setTrip("oneway")}
          >
            Một chiều
          </button>
          <button
            type="button"
            aria-pressed={!oneway}
            onClick={() => setTrip("round")}
          >
            Khứ hồi
          </button>
        </div>

        <div className="pax-wrap">
          <button
            type="button"
            className="chip"
            aria-expanded={s.panel === "pax"}
            aria-controls="pax-pop"
            onClick={() => open("pax")}
          >
            <Icon name="users" />
            <span>
              {total} khách, {cabin.label}
            </span>
            <Icon name="chev" className="chev" />
          </button>
          {s.panel === "pax" && (
            <div
              className="pop pop-pax"
              id="pax-pop"
              role="dialog"
              aria-label="Hành khách và hạng ghế"
              onKeyDown={onPopKey}
            >
              <PaxRow
                title="Người lớn"
                desc="Từ 12 tuổi"
                noun="người lớn"
                value={form.adults}
                tick={s.paxKey === "adults" ? tick(s.paxPar) : ""}
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
                tick={s.paxKey === "children" ? tick(s.paxPar) : ""}
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
                tick={s.paxKey === "infants" ? tick(s.paxPar) : ""}
                decDis={form.infants <= 0}
                incDis={form.infants >= form.adults}
                onDec={() => pax("infants", -1)}
                onInc={() => pax("infants", 1)}
              />
              {paxNote && (
                <p className="pax-note" role="status">
                  <Icon name="info" />
                  <span>{paxNote}</span>
                </p>
              )}
              <fieldset className="cabin">
                <legend>Hạng ghế</legend>
                <div className="cabin-opts">
                  {CABINS.map((c) => (
                    <button
                      key={c.id}
                      type="button"
                      className={`opt-pill${c.id === form.cabin ? " is-on" : ""}`}
                      aria-pressed={c.id === form.cabin}
                      onClick={() => update({ cabin: c.id })}
                    >
                      {c.label}
                    </button>
                  ))}
                </div>
              </fieldset>
              <div className="pop-foot">
                <button
                  type="button"
                  className="pill pill-dark sm"
                  onClick={closePanel}
                >
                  Xong
                </button>
              </div>
            </div>
          )}
        </div>

        {F && T && F.cc !== T.cc && (
          <p className="intl">
            <Icon name="info" />
            <span>
              Hành trình quốc tế: hộ chiếu của mọi hành khách phải còn hạn ít
              nhất 6 tháng tính từ ngày bay chặng cuối.
            </span>
          </p>
        )}
      </div>

      <div className="fields">
        <div className="f-route">
          <div className={fieldCls("from")}>
            <FieldBtn
              label="Từ"
              open={s.panel === "from"}
              onClick={() => open("from")}
              error={err.from}
              main={F ? F.code : "Chọn sân bay"}
              sub={F ? F.city : "Điểm đi"}
              mainCls={F ? tick(form.fromPar) : "is-empty"}
            />
            {s.panel === "from" && (
              <AirportPop
                title="Bay từ"
                aria="Chọn sân bay đi"
                query={s.query}
                list={list}
                hi={hi}
                onQuery={(q) => patch({ query: q, hi: 0 })}
                onKey={onQueryKey}
                onPopKey={onPopKey}
                onPick={pickAirport}
                onClose={closePanel}
              />
            )}
          </div>

          <div className="swap-cell">
            <button
              type="button"
              className="swap"
              aria-label="Đảo điểm đi và điểm đến"
              onClick={swap}
              style={{ transform: `rotate(${s.swapTurns * 180}deg)` }}
            >
              <Icon name="swap" />
            </button>
          </div>

          <div className={fieldCls("to")}>
            <FieldBtn
              label="Đến"
              open={s.panel === "to"}
              onClick={() => open("to")}
              error={err.to}
              main={T ? T.code : "Chọn sân bay"}
              sub={T ? T.city : "Điểm đến"}
              mainCls={T ? tick(form.toPar) : "is-empty"}
            />
            {s.panel === "to" && (
              <AirportPop
                title="Bay đến"
                aria="Chọn sân bay đến"
                query={s.query}
                list={list}
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

        <div className="f-when">
          <div className={fieldCls("dep")}>
            <FieldBtn
              label="Ngày đi"
              open={s.panel === "dep"}
              onClick={() => open("dep")}
              error={err.dep}
              main={depD ? ddmm(depD) : "Chọn ngày"}
              sub={depD ? weekday(depD) : "Chưa chọn"}
              mainCls={depD ? tick(s.depPar) : "is-empty"}
            />
            {s.panel === "dep" && cal && (
              <CalendarPop
                aria="Chọn ngày đi"
                hint="Chọn ngày đi"
                cal={cal}
                onShift={shiftMonth}
                onPick={pickDate}
                onKey={onPopKey}
                onClose={closePanel}
              />
            )}
          </div>

          <div className={fieldCls("ret")}>
            <FieldBtn
              label="Ngày về"
              open={s.panel === "ret"}
              onClick={openRet}
              error={err.ret}
              main={oneway ? "Thêm ngày về" : retD ? ddmm(retD) : "Chọn ngày"}
              sub={
                oneway ? "Không bắt buộc" : retD ? weekday(retD) : "Chưa chọn"
              }
              mainCls={!oneway && retD ? tick(s.retPar) : "is-empty"}
            />
            {s.panel === "ret" && cal && (
              <CalendarPop
                aria="Chọn ngày về"
                hint="Chọn ngày về"
                cal={cal}
                onShift={shiftMonth}
                onPick={pickDate}
                onKey={onPopKey}
                onClose={closePanel}
              />
            )}
          </div>
        </div>

        <div className="go-cell">
          <button
            type="button"
            className={`pill pill-dark go${pending ? " is-loading" : ""}`}
            aria-busy={pending}
            onClick={submit}
          >
            <span>{pending ? "Đang tìm" : "Tìm chuyến bay"}</span>
            <span className="go-icon" aria-hidden="true">
              <Icon name="arrow" />
              <Jet />
            </span>
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
  mainCls: string;
  error?: string;
}) {
  return (
    <button
      type="button"
      className="field-btn"
      aria-expanded={p.open}
      onClick={p.onClick}
    >
      <span className="f-label">{p.label}</span>
      <span className={`f-main ${p.mainCls}`.trim()}>{p.main}</span>
      <span className="f-sub">{p.sub}</span>
      {p.error && (
        <span className="f-err">
          <Icon name="alert" />
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
  tick: string;
  decDis: boolean;
  incDis: boolean;
  onDec: () => void;
  onInc: () => void;
}) {
  return (
    <div className="pax-row">
      <div>
        <p className="pax-t">{p.title}</p>
        <p className="pax-d">{p.desc}</p>
      </div>
      <div className="stepper">
        <button
          type="button"
          className="icon-btn"
          aria-label={`Bớt một ${p.noun}`}
          disabled={p.decDis}
          onClick={p.onDec}
        >
          <Icon name="minus" />
        </button>
        <span className={`num ${p.tick}`.trim()}>{p.value}</span>
        <button
          type="button"
          className="icon-btn"
          aria-label={`Thêm một ${p.noun}`}
          disabled={p.incDis}
          onClick={p.onInc}
        >
          <Icon name="plus" />
        </button>
      </div>
    </div>
  );
}

function AirportPop(p: {
  title: string;
  aria: string;
  query: string;
  list: Airport[];
  hi: number;
  onQuery: (q: string) => void;
  onKey: (e: React.KeyboardEvent) => void;
  onPopKey: (e: React.KeyboardEvent) => void;
  onPick: (code: string) => void;
  onClose: () => void;
}) {
  return (
    <div
      className="pop pop-ap"
      role="dialog"
      aria-label={p.aria}
      onKeyDown={p.onPopKey}
    >
      <div className="pop-head">
        <label className="pop-title" htmlFor="ap-q">
          {p.title}
        </label>
        <button
          type="button"
          className="icon-btn"
          aria-label="Đóng"
          onClick={p.onClose}
        >
          <Icon name="close" />
        </button>
      </div>
      <div className="q-wrap">
        <Icon name="search" />
        <input
          id="ap-q"
          className="q"
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
      <div className="ap-list">
        {p.list.map((a, i) => (
          <button
            key={a.code}
            type="button"
            className={`opt${i === p.hi ? " is-hi" : ""}`}
            onClick={() => p.onPick(a.code)}
          >
            <span className="ap-code">{a.code}</span>
            <span className="ap-txt">
              <span className="ap-city">{a.city}</span>
              <span className="ap-name">{a.name}</span>
            </span>
            <span className="ap-tag">{a.cc === "VN" ? "" : a.country}</span>
          </button>
        ))}
        {p.list.length === 0 && (
          <p className="ap-empty">
            Không tìm thấy sân bay phù hợp. Thử gõ tên thành phố hoặc mã 3 chữ
            cái, ví dụ DAD.
          </p>
        )}
      </div>
    </div>
  );
}

function CalendarPop(p: {
  aria: string;
  hint: string;
  cal: {
    cells: ({
      n: number;
      day: string;
      cls: string;
      sel: boolean;
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
      className="pop pop-cal"
      role="dialog"
      aria-label={p.aria}
      onKeyDown={p.onKey}
    >
      <div className="cal-head">
        <button
          type="button"
          className="icon-btn"
          aria-label="Tháng trước"
          disabled={p.cal.prevDis}
          onClick={() => p.onShift(-1)}
        >
          <Icon name="left" />
        </button>
        <p className="cal-title" aria-live="polite">
          {p.cal.title}
        </p>
        <button
          type="button"
          className="icon-btn"
          aria-label="Tháng sau"
          disabled={p.cal.nextDis}
          onClick={() => p.onShift(1)}
        >
          <Icon name="right" />
        </button>
      </div>
      <p className="cal-hint">{p.hint}</p>
      <div className="cal-grid cal-wd" aria-hidden="true">
        {["T2", "T3", "T4", "T5", "T6", "T7", "CN"].map((d) => (
          <span key={d}>{d}</span>
        ))}
      </div>
      <div className="cal-grid">
        {p.cal.cells.map((c, i) =>
          c ? (
            <button
              key={c.day}
              type="button"
              className={c.cls}
              disabled={c.disabled}
              aria-pressed={c.sel}
              aria-label={c.label}
              onClick={() => p.onPick(c.day)}
            >
              {c.n}
            </button>
          ) : (
            <span key={`b${i}`} className="day-blank" />
          ),
        )}
      </div>
      <div className="pop-foot">
        <button type="button" className="pill pill-line sm" onClick={p.onClose}>
          Xong
        </button>
      </div>
    </div>
  );
}
