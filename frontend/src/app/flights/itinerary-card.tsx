"use client";

import { pillDark, pillLine } from "@/components/form";
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
      className="border border-line bg-paper"
    >
      <div className="grid gap-16 p-22 md:grid-cols-[minmax(0,1fr)_auto] md:items-center md:gap-38">
        <div className="grid gap-13">
          <p className="text-caption text-muted">
            <span className="font-bold text-ink">{a.airline.name}</span>
            {" · "}
            {numbers}
          </p>
          <div className="flex items-center gap-16">
            <Time t={a.departure} />
            <div className="grid flex-1 justify-items-center gap-4 text-caption text-muted">
              <span>{duration(itinerary.totalDurationMinutes)}</span>
              <span aria-hidden="true" className="h-1 w-full bg-line-strong" />
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
            <span className="text-subheading font-bold">
              {vnd(cheapest(itinerary))}
            </span>
            <span className="text-caption text-muted">
              cho {passengers} khách
            </span>
          </p>
          <button
            type="button"
            className={open ? pillLine : pillDark}
            aria-expanded={open}
            aria-controls={panelId}
            onClick={onToggle}
          >
            {open ? "Thu gọn" : "Chọn"}
          </button>
        </div>
      </div>

      {open && (
        <div id={panelId} className="grid gap-22 border-t border-line p-22">
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
                className="grid content-start gap-13 border border-line p-22"
              >
                <p className="text-subheading font-bold">{f.name}</p>
                <ul className="grid gap-4 text-caption">
                  {fareRules(f).map((r) => (
                    <li key={r}>{r}</li>
                  ))}
                </ul>
                <p className="grid">
                  <span className="text-caption text-muted">
                    Tổng cho {passengers} khách
                  </span>
                  <span className="text-subheading font-bold">
                    {vnd(f.totalPrice)}
                  </span>
                  <span className="text-caption text-muted">
                    Người lớn {vnd(f.adultPrice)}, còn {f.seatsLeft} ghế
                  </span>
                </p>
                <button
                  type="button"
                  className={pillDark}
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
