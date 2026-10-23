"use client";

import { pillPrimary, pillLine } from "@/components/form";
import {
  cheapest,
  dayShift,
  duration,
  fareRules,
  first,
  hhmm,
  itineraryKey,
  last,
  vnd,
  type AirportTime,
  type FareOption,
  type Itinerary,
} from "./itineraries";

function Time({ t, shift = 0 }: { t: AirportTime; shift?: number }) {
  return (
    <p className="grid">
      <span className="text-subheading font-bold">
        {hhmm(t)}
        {shift > 0 && (
          <span className="ml-4 align-top text-caption">+{shift} ngày</span>
        )}
      </span>
      <span className="text-caption text-muted">{t.airport}</span>
    </p>
  );
}

/** Một hành trình (FR-11): tóm tắt trên một dòng; bấm "Chọn" mở chi tiết chặng và các gói giá. */
export default function ItineraryCard({
  itinerary,
  passengers,
  open,
  onToggle,
  onChoose,
}: {
  itinerary: Itinerary;
  passengers: number;
  open: boolean;
  onToggle: () => void;
  onChoose: (fare: FareOption) => void;
}) {
  const segs = itinerary.segments;
  const a = first(itinerary);
  const z = last(itinerary);
  const numbers = segs.map((s) => s.flightNumber).join(" + ");
  const panelId = `fares-${itineraryKey(itinerary)}`;

  return (
    <article
      aria-label={`${a.airline.name} ${numbers}`}
      className="card transition-shadow duration-200 hover:shadow-card-hover"
    >
      <div className="grid gap-16 p-16 md:grid-cols-[minmax(0,1fr)_auto] md:items-center md:gap-28">
        <div className="grid gap-13">
          <p className="flex flex-wrap items-center gap-x-8 text-caption text-muted">
            <span
              aria-hidden="true"
              className="inline-grid h-22 min-w-28 place-items-center rounded-badge bg-accent-soft px-5 text-badge font-bold text-accent"
            >
              {a.airline.code}
            </span>
            <span className="font-semibold text-ink">{a.airline.name}</span>
            {" · "}
            {numbers}
          </p>
          <div className="flex items-center gap-16">
            <Time t={a.departure} />
            <div className="grid flex-1 justify-items-center gap-4 text-caption text-muted">
              <span>{duration(itinerary.totalDurationMinutes)}</span>
              {/* Đường bay: chấm rỗng ở nơi đi, nét đứt, chấm đất nung ở nơi đến. */}
              <span
                aria-hidden="true"
                className="flex w-full items-center gap-4"
              >
                <span className="size-8 shrink-0 rounded-pill border-2 border-line-strong" />
                <span className="flex-1 border-t-2 border-dashed border-line-strong" />
                <span className="size-8 shrink-0 rounded-pill bg-accent" />
              </span>
              <span>
                {itinerary.stops === 0
                  ? "Bay thẳng"
                  : `1 điểm dừng, ${a.arrival.airport}`}
              </span>
            </div>
            <Time t={z.arrival} shift={dayShift(a.departure, z.arrival)} />
          </div>
        </div>
        <div className="flex items-center justify-between gap-16 md:grid md:justify-items-end">
          <p className="grid md:justify-items-end">
            <span className="text-caption text-muted">Từ</span>
            <span className="text-subheading font-bold text-accent">
              {vnd(cheapest(itinerary))}
            </span>
            <span className="text-caption text-muted">
              cho {passengers} khách
            </span>
          </p>
          <button
            type="button"
            className={open ? pillLine : pillPrimary}
            aria-expanded={open}
            aria-controls={panelId}
            onClick={onToggle}
          >
            {open ? "Thu gọn" : "Chọn"}
          </button>
        </div>
      </div>

      {open && (
        <div id={panelId} className="grid gap-16 border-t border-line p-16">
          <ol className="grid gap-16">
            {segs.map((s, i) => (
              <li key={s.flightId} className="grid gap-4">
                <p className="font-bold">
                  {hhmm(s.departure)} {s.departure.city} ({s.departure.airport})
                  {" → "}
                  {hhmm(s.arrival)} {s.arrival.city} ({s.arrival.airport})
                </p>
                <p className="text-caption text-muted">
                  {s.airline.name} {s.flightNumber} · {s.aircraftModel} ·{" "}
                  {duration(s.durationMinutes)}
                </p>
                {i < segs.length - 1 && (
                  <p className="text-caption">
                    Nối chuyến tại {s.arrival.city}, chờ{" "}
                    {duration(s.layoverMinutesAfter)}
                  </p>
                )}
              </li>
            ))}
          </ol>

          <ul className="grid gap-16 sm:grid-cols-2 lg:grid-cols-3">
            {itinerary.fareOptions.map((f) => (
              <li
                key={f.fareFamilyId}
                className="flex flex-col gap-13 rounded-inner border border-line bg-paper/60 p-16 transition-colors duration-200 hover:border-accent/60"
              >
                <p className="text-subheading font-bold">{f.name}</p>
                <ul className="grid gap-4 text-caption">
                  {fareRules(f).map((r) => (
                    <li key={r}>{r}</li>
                  ))}
                </ul>
                <p className="mt-auto grid">
                  <span className="text-caption text-muted">
                    Tổng cho {passengers} khách
                  </span>
                  <span className="text-subheading font-bold text-accent">
                    {vnd(f.totalPrice)}
                  </span>
                  <span className="text-caption text-muted">
                    Người lớn {vnd(f.adultPrice)}, còn {f.seatsLeft} ghế
                  </span>
                </p>
                <button
                  type="button"
                  className={pillPrimary}
                  aria-label={`Chọn gói ${f.name}`}
                  onClick={() => onChoose(f)}
                >
                  Chọn gói này
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </article>
  );
}
