"use client";

import { useState, type ReactNode } from "react";
import { pillLine, textLink } from "@/components/form";
import ItineraryCard from "./itinerary-card";
import {
  DAY_PARTS,
  NO_FILTERS,
  SORTS,
  applyFilters,
  cheapest,
  first,
  itineraryKey,
  sortItineraries,
  vnd,
  type FareOption,
  type Filters,
  type Itinerary,
  type SortKey,
} from "./itineraries";

const box =
  "size-16 shrink-0 accent-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent";

function toggle<T>(list: T[], value: T) {
  return list.includes(value)
    ? list.filter((v) => v !== value)
    : [...list, value];
}

function Group({ legend, children }: { legend: string; children: ReactNode }) {
  return (
    <fieldset className="grid gap-11 border-t border-line pt-16 first:border-t-0 first:pt-0">
      <legend className="float-left mb-11 text-caption font-semibold text-ink-2">
        {legend}
      </legend>
      {children}
    </fieldset>
  );
}

function Check({
  label,
  hint,
  checked,
  onChange,
}: {
  label: string;
  hint?: string;
  checked: boolean;
  onChange: () => void;
}) {
  return (
    <label className="flex cursor-pointer items-start gap-11 text-caption">
      <input
        type="checkbox"
        className={`${box} mt-2`}
        checked={checked}
        onChange={onChange}
      />
      <span className="grid">
        {label}
        {hint && <span className="text-muted">{hint}</span>}
      </span>
    </label>
  );
}

/**
 * Danh sách hành trình của một chiều, lọc và sắp xếp ở client (FR-11). Bộ lọc theo hãng, số điểm dừng, khung giờ
 * cất cánh, giá tối đa; sắp xếp theo giá, giờ cất cánh, tổng thời lượng.
 */
export default function ResultList({
  itineraries,
  passengers,
  note,
  onChoose,
}: {
  itineraries: Itinerary[];
  passengers: number;
  /** Dòng ghi chú trên danh sách, VD số chuyến chiều về bị ẩn theo BR-04. */
  note?: string;
  onChoose: (itinerary: Itinerary, fare: FareOption) => void;
}) {
  const [filters, setFilters] = useState<Filters>(NO_FILTERS);
  const [sort, setSort] = useState<SortKey>("price");
  const [open, setOpen] = useState<string | null>(null);
  const [showFilters, setShowFilters] = useState(false);

  const shown = sortItineraries(applyFilters(itineraries, filters), sort);
  const airlines = [
    ...new Map(
      itineraries.map((it) => [first(it).airline.code, first(it).airline]),
    ).values(),
  ].sort((x, y) => x.name.localeCompare(y.name, "vi"));
  const stops = [...new Set(itineraries.map((it) => it.stops))].sort(
    (x, y) => x - y,
  );
  const prices = itineraries.map(cheapest);
  const minPrice = Math.min(...prices);
  const maxPrice = Math.max(...prices);
  const set = (patch: Partial<Filters>) =>
    setFilters((f) => ({ ...f, ...patch }));
  const filtered =
    filters.airlines.length > 0 ||
    filters.stops.length > 0 ||
    filters.dayParts.length > 0 ||
    filters.maxPrice !== null;

  if (itineraries.length === 0) {
    return (
      <div className="grid gap-13 card p-16">
        <p className="text-subheading font-bold">
          Không có chuyến bay phù hợp.
        </p>
        <p className="text-muted">
          {note ?? "Thử chọn ngày khác, hạng ghế khác hoặc ít khách hơn."}
        </p>
      </div>
    );
  }

  return (
    <div className="grid gap-16 lg:grid-cols-[248px_minmax(0,1fr)] lg:items-start lg:gap-28">
      <button
        type="button"
        className={`${pillLine} justify-self-start lg:hidden`}
        aria-expanded={showFilters}
        aria-controls="bo-loc"
        onClick={() => setShowFilters((v) => !v)}
      >
        {showFilters ? "Ẩn bộ lọc" : "Bộ lọc"}
      </button>

      <aside
        id="bo-loc"
        aria-label="Bộ lọc"
        className={`${showFilters ? "grid" : "hidden"} card gap-16 p-16 lg:sticky lg:top-80 lg:grid`}
      >
        <Group legend="Hãng bay">
          {airlines.map((a) => (
            <Check
              key={a.code}
              label={a.name}
              checked={filters.airlines.includes(a.code)}
              onChange={() =>
                set({ airlines: toggle(filters.airlines, a.code) })
              }
            />
          ))}
        </Group>
        <Group legend="Số điểm dừng">
          {stops.map((n) => (
            <Check
              key={n}
              label={n === 0 ? "Bay thẳng" : `${n} điểm dừng`}
              checked={filters.stops.includes(n)}
              onChange={() => set({ stops: toggle(filters.stops, n) })}
            />
          ))}
        </Group>
        <Group legend="Giờ cất cánh">
          {DAY_PARTS.map((p) => (
            <Check
              key={p.id}
              label={p.label}
              hint={p.range}
              checked={filters.dayParts.includes(p.id)}
              onChange={() => set({ dayParts: toggle(filters.dayParts, p.id) })}
            />
          ))}
        </Group>
        {minPrice < maxPrice && (
          <Group legend="Giá tối đa">
            <label className="grid gap-8 text-caption">
              <span>{vnd(filters.maxPrice ?? maxPrice)}</span>
              <input
                type="range"
                className="accent-accent"
                min={minPrice}
                max={maxPrice}
                step="any"
                value={filters.maxPrice ?? maxPrice}
                aria-label="Giá tối đa"
                aria-valuetext={vnd(filters.maxPrice ?? maxPrice)}
                onChange={(e) => {
                  const v = Number(e.target.value);
                  set({ maxPrice: v >= maxPrice ? null : Math.round(v) });
                }}
              />
            </label>
          </Group>
        )}
        {filtered && (
          <button
            type="button"
            className={`${textLink} justify-self-start text-caption`}
            onClick={() => setFilters(NO_FILTERS)}
          >
            Xoá bộ lọc
          </button>
        )}
      </aside>

      <section aria-label="Kết quả" className="grid gap-16">
        <div className="flex flex-wrap items-center justify-between gap-16">
          <p className="text-caption text-muted" aria-live="polite">
            {shown.length} trên {itineraries.length} hành trình
          </p>
          <label className="flex items-center gap-11 text-caption font-bold">
            Sắp xếp
            <select
              className="cursor-pointer rounded-inner border border-line-strong bg-surface px-13 py-8 font-normal text-ink"
              value={sort}
              onChange={(e) => setSort(e.target.value as SortKey)}
            >
              {SORTS.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.label}
                </option>
              ))}
            </select>
          </label>
        </div>
        {note && <p className="text-caption text-muted">{note}</p>}
        {shown.map((it) => {
          const key = itineraryKey(it);
          return (
            <ItineraryCard
              key={key}
              itinerary={it}
              passengers={passengers}
              open={open === key}
              onToggle={() => setOpen(open === key ? null : key)}
              onChoose={(fare) => onChoose(it, fare)}
            />
          );
        })}
        {shown.length === 0 && (
          <div className="grid gap-13 card p-16">
            <p className="font-bold">Không có hành trình khớp bộ lọc.</p>
            <button
              type="button"
              className={`${textLink} justify-self-start text-caption`}
              onClick={() => setFilters(NO_FILTERS)}
            >
              Xoá bộ lọc
            </button>
          </div>
        )}
      </section>
    </div>
  );
}
