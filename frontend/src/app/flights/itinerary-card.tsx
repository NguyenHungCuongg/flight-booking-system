"use client";

import { pillPrimary, pillLine } from "@/components/form";
import { Icon, Jet } from "@/components/landing/icons";
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

function TimeBlock({
  t,
  shift = 0,
  align = "left",
}: {
  t: AirportTime;
  shift?: number;
  align?: "left" | "right";
}) {
  return (
    <div className={align === "right" ? "text-right" : "text-left"}>
      <div className="flex items-baseline gap-4">
        <span className="text-heading-sm font-black text-ink">{hhmm(t)}</span>
        {shift > 0 && (
          <span className="text-badge font-bold text-danger">+{shift}d</span>
        )}
      </div>
      <p className="text-caption font-bold text-ink-2">{t.airport}</p>
      <p className="text-badge text-muted">{t.city}</p>
    </div>
  );
}

/** Một hành trình bay chuẩn OTA: phân cấp thời gian rõ ràng, đường bay thanh lịch, các gói vé so sánh phẳng */
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
  const isDirect = itinerary.stops === 0;

  return (
    <article
      aria-label={`${a.airline.name} ${numbers}`}
      className="card transition-colors duration-150 hover:border-ink-2/60"
    >
      <div className="grid gap-20 p-20 md:grid-cols-[minmax(0,1fr)_auto] md:items-center md:gap-32">
        {/* Left: Flight Info & Timetable */}
        <div className="grid gap-16">
          <div className="flex flex-wrap items-center gap-x-12 gap-y-6">
            <span className="rounded-badge bg-tint px-10 py-3 text-badge font-bold text-ink">
              {a.airline.name}
            </span>
            <span className="text-caption font-semibold text-muted">
              {numbers}
            </span>
          </div>

          <div className="grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-16 sm:gap-28">
            <TimeBlock t={a.departure} align="left" />

            <div className="flex flex-col items-center gap-4 px-4 text-center">
              <span className="text-badge font-medium text-muted">
                {duration(itinerary.totalDurationMinutes)}
              </span>
              <div
                aria-hidden="true"
                className="flex w-full items-center gap-6 text-accent"
              >
                <span className="size-6 rounded-inner bg-line-strong" />
                <span className="flex-1 border-t border-dashed border-line-strong" />
                <Jet className="size-16 shrink-0" />
              </div>
              <span
                className={`text-badge font-semibold ${
                  isDirect ? "text-accent" : "text-muted"
                }`}
              >
                {isDirect ? "Bay thẳng" : `1 điểm dừng tại ${a.arrival.airport}`}
              </span>
            </div>

            <TimeBlock
              t={z.arrival}
              shift={dayShift(a.departure, z.arrival)}
              align="right"
            />
          </div>
        </div>

        {/* Right: Fare Price & Action */}
        <div className="flex items-center justify-between gap-20 border-t border-line pt-14 md:flex-col md:items-end md:justify-center md:border-t-0 md:border-l md:pt-0 md:pl-28">
          <div className="text-left md:text-right">
            <span className="block text-badge text-muted">Giá từ</span>
            <span className="text-heading-sm font-black text-accent">
              {vnd(cheapest(itinerary))}
            </span>
            <span className="block text-badge text-muted">
              cho {passengers} khách
            </span>
          </div>
          <button
            type="button"
            className={open ? pillLine : pillPrimary}
            aria-expanded={open}
            aria-controls={panelId}
            onClick={onToggle}
          >
            {open ? "Thu gọn" : "Xem gói vé"}
          </button>
        </div>
      </div>

      {/* Expanded Details & Fare Families */}
      {open && (
        <div id={panelId} className="border-t border-line bg-tint/30 p-20 sm:p-24">
          {/* Segment Details */}
          <div className="mb-20 grid gap-12 rounded-inner border border-line bg-surface p-16">
            <h4 className="text-caption font-bold text-ink">Chi tiết chặng bay</h4>
            <ol className="grid gap-12 divide-y divide-line">
              {segs.map((s, i) => (
                <li key={s.flightId} className="grid gap-4 pt-8 first:pt-0">
                  <div className="flex flex-wrap items-center justify-between gap-8 text-caption font-bold text-ink">
                    <span>
                      {hhmm(s.departure)} {s.departure.city} ({s.departure.airport}) →{" "}
                      {hhmm(s.arrival)} {s.arrival.city} ({s.arrival.airport})
                    </span>
                    <span className="text-badge font-semibold text-muted">
                      {duration(s.durationMinutes)}
                    </span>
                  </div>
                  <p className="text-badge text-muted">
                    {s.airline.name} {s.flightNumber} · Tàu bay {s.aircraftModel}
                  </p>
                  {i < segs.length - 1 && (
                    <p className="rounded-badge bg-warn-soft px-10 py-4 text-badge font-bold text-warn">
                      Nối chuyến tại {s.arrival.city} ({s.arrival.airport}), thời gian chờ{" "}
                      {duration(s.layoverMinutesAfter)}
                    </p>
                  )}
                </li>
              ))}
            </ol>
          </div>

          {/* Fare Family Comparison Grid */}
          <h4 className="mb-12 text-caption font-bold text-ink">
            Chọn gói giá ({itinerary.fareOptions.length} lựa chọn)
          </h4>
          <div className="grid gap-14 sm:grid-cols-2 lg:grid-cols-3">
            {itinerary.fareOptions.map((f) => (
              <div
                key={f.fareFamilyId}
                className="flex flex-col justify-between rounded-inner border border-line bg-surface p-18 transition-colors duration-150 hover:border-accent"
              >
                <div>
                  <div className="flex items-center justify-between gap-8 pb-8 border-b border-line">
                    <span className="text-body font-black text-ink">{f.name}</span>
                    {f.seatsLeft <= 5 && (
                      <span className="rounded-badge bg-danger-soft px-8 py-2 text-badge font-bold text-danger">
                        Còn {f.seatsLeft} ghế
                      </span>
                    )}
                  </div>

                  <ul className="grid gap-8 py-14 text-caption text-ink-2">
                    {fareRules(f).map((r) => (
                      <li key={r} className="flex items-start gap-8">
                        <Icon name="check" className="mt-2 size-14 shrink-0 text-accent" />
                        <span>{r}</span>
                      </li>
                    ))}
                  </ul>
                </div>

                <div className="border-t border-line pt-14">
                  <div className="mb-12">
                    <span className="block text-badge text-muted">
                      Tổng cho {passengers} khách
                    </span>
                    <span className="text-heading-sm font-black text-accent">
                      {vnd(f.totalPrice)}
                    </span>
                    <span className="block text-badge text-muted">
                      Người lớn {vnd(f.adultPrice)}
                    </span>
                  </div>
                  <button
                    type="button"
                    className={`${pillPrimary} w-full`}
                    aria-label={`Chọn gói ${f.name} giá ${vnd(f.totalPrice)}`}
                    onClick={() => onChoose(f)}
                  >
                    Chọn gói này
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </article>
  );
}
