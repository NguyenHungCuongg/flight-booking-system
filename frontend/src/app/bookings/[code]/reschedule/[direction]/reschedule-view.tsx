"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import SiteHeader from "@/components/site-header";
import {
  Field,
  FormSkeleton,
  Notice,
  pillPrimary,
  pillLine,
  textLink,
} from "@/components/form";
import PriceTable from "@/components/price-table";
import { ApiError, apiFetch, type Schemas } from "@/lib/api";
import { DIRECTION_LABEL, clock, directionParam, dmy } from "@/lib/booking";
import {
  duration,
  fareRules,
  first,
  hhmm,
  itineraryKey,
  last,
  vnd,
} from "../../../../flights/itineraries";
import { Section } from "../../booking-view";

type Detail = Schemas["BookingDetail"];
type Option = Schemas["RescheduleOption"];
type Quote = Schemas["RescheduleQuote"];

function OptionCard({
  option,
  selected,
  onChoose,
}: {
  option: Option;
  selected: boolean;
  onChoose: () => void;
}) {
  const it = option.itinerary;
  const a = first(it);
  const z = last(it);
  const numbers = it.segments.map((s) => s.flightNumber).join(" + ");

  return (
    <article
      aria-label={`${a.airline.name} ${numbers}`}
      className="grid gap-16 card p-16 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
    >
      <div className="grid gap-4">
        <p className="text-caption text-muted">
          <span className="font-bold text-ink">{a.airline.name}</span>
          {" · "}
          {numbers}
        </p>
        <p className="font-bold">
          {`${hhmm(a.departure)} ${a.departure.airport} → ${hhmm(z.arrival)} ${z.arrival.airport}, ${dmy(a.departure.time)}`}
        </p>
        <p className="text-caption text-muted">
          {`${duration(it.totalDurationMinutes)} · ${
            it.stops === 0 ? "Bay thẳng" : `1 điểm dừng, ${a.arrival.airport}`
          }`}
        </p>
      </div>
      <div className="flex items-center justify-between gap-16 sm:grid sm:justify-items-end">
        <p className="font-bold">{`Phải trả ${vnd(option.quote.amountDue)}`}</p>
        <button
          type="button"
          className={selected ? pillLine : pillPrimary}
          onClick={onChoose}
        >
          {selected ? "Đã chọn" : "Chọn"}
        </button>
      </div>
    </article>
  );
}

/**
 * C-11 (APP_FLOW §2.1, UF-03, FR-60–FR-62): chọn ngày mới, xem các chuyến thay thế, xem báo giá đổi chuyến (BR-72) rồi
 * xác nhận đổi (BR-73–BR-75). Phải trả > 0 đ thì chuyển ngay sang VNPay; 0 đ thì hoàn tất ngay.
 */
export default function RescheduleView() {
  const { code, direction } = useParams<{ code: string; direction: string }>();
  const dir = directionParam(direction);
  const router = useRouter();
  // undefined: đang tải; null: không tìm thấy.
  const [booking, setBooking] = useState<Detail | null>();
  const [loadError, setLoadError] = useState<string>();
  const [date, setDate] = useState<string>();
  const [version, setVersion] = useState(0);
  const [list, setList] = useState<{
    date: string;
    version: number;
    options?: Option[];
    error?: string;
  }>();
  const [chosen, setChosen] = useState<number[]>();
  const [quoteState, setQuoteState] = useState<{
    key: string;
    quote: Quote;
  }>();
  const [actionError, setActionError] = useState<string>();
  const [busy, setBusy] = useState(false);
  const confirming = useRef(false);

  useEffect(() => {
    if (dir === null) return;
    let live = true;
    apiFetch<Detail>(`/bookings/${encodeURIComponent(code)}`).then(
      (b) => live && setBooking(b),
      (e: ApiError) => {
        if (!live) return;
        if (e.status === 401) {
          router.replace(
            `/login?next=${encodeURIComponent(location.pathname + location.search)}`,
          );
        } else if (e.status === 404) {
          setBooking(null);
        } else {
          setLoadError(e.detail);
        }
      },
    );
    return () => {
      live = false;
    };
  }, [code, dir, router]);

  const journey =
    booking && dir
      ? (booking.journeys.find((j) => j.direction === dir) ?? null)
      : undefined;
  const selectedDate =
    date ?? (journey ? journey.segments[0].departureTime.slice(0, 10) : "");

  useEffect(() => {
    if (!journey || !dir || !selectedDate) return;
    let live = true;
    apiFetch<Schemas["RescheduleOptions"]>(
      `/bookings/${encodeURIComponent(code)}/journeys/${dir}/reschedule-options?date=${encodeURIComponent(selectedDate)}`,
    ).then(
      ({ options }) =>
        live && setList({ date: selectedDate, version, options }),
      (e: ApiError) => {
        if (!live) return;
        if (e.status === 401) {
          router.replace(
            `/login?next=${encodeURIComponent(location.pathname + location.search)}`,
          );
        } else {
          setList({ date: selectedDate, version, error: e.detail });
        }
      },
    );
    return () => {
      live = false;
    };
  }, [code, dir, journey, selectedDate, version, router]);

  const chosenKey = chosen?.join(",");
  useEffect(() => {
    if (!chosenKey || !dir) return;
    let live = true;
    const flightIds = chosenKey.split(",").map(Number);
    apiFetch<Quote>(
      `/bookings/${encodeURIComponent(code)}/journeys/${dir}/reschedule-quote`,
      { method: "POST", body: { flightIds } },
    ).then(
      (quote) => live && setQuoteState({ key: chosenKey, quote }),
      (e: ApiError) => {
        if (!live) return;
        if (e.status === 401) {
          router.replace(
            `/login?next=${encodeURIComponent(location.pathname + location.search)}`,
          );
        } else {
          setActionError(e.detail);
          setChosen(undefined);
          setVersion((v) => v + 1);
        }
      },
    );
    return () => {
      live = false;
    };
  }, [code, dir, chosenKey, router]);

  const notFound = dir === null || booking === null || journey === null;
  const bookingHref = `/bookings/${encodeURIComponent(code)}`;
  const current =
    list?.date === selectedDate && list.version === version ? list : undefined;
  const quote =
    chosenKey && quoteState?.key === chosenKey ? quoteState.quote : undefined;

  async function confirmReschedule() {
    if (confirming.current || !chosen || !dir) return;
    confirming.current = true;
    setBusy(true);
    setActionError(undefined);
    try {
      const created = await apiFetch<Schemas["RescheduleCreatedResponse"]>(
        `/bookings/${encodeURIComponent(code)}/journeys/${dir}/reschedules`,
        { method: "POST", body: { flightIds: chosen } },
      );
      if (created.status === "COMPLETED") {
        router.push(`${bookingHref}?reschedule=done`);
        return;
      }
      try {
        const { paymentUrl } = await apiFetch<
          Schemas["ReschedulePaymentResponse"]
        >(`/reschedules/${created.id}/payments`, { method: "POST" });
        window.location.assign(paymentUrl);
      } catch {
        router.push(bookingHref);
      }
    } catch (e) {
      confirming.current = false;
      setBusy(false);
      if (!(e instanceof ApiError)) throw e;
      if (e.status === 401) {
        router.replace(
          `/login?next=${encodeURIComponent(location.pathname + location.search)}`,
        );
      } else {
        setActionError(e.detail);
        setChosen(undefined);
        setVersion((v) => v + 1);
      }
    }
  }

  return (
    <div className="min-h-dvh">
      <SiteHeader />
      <main className="mx-auto grid max-w-page gap-28 px-16 pt-16 pb-80 sm:px-38">
        <div className="grid gap-13">
          <Link href={bookingHref} className={`${textLink} text-caption`}>
            {`Đặt chỗ ${code.toUpperCase()}`}
          </Link>
          <h1 className="text-heading-lg font-bold break-words">
            {notFound
              ? "Không tìm thấy đặt chỗ."
              : `Đổi ${DIRECTION_LABEL[dir].toLowerCase()}.`}
          </h1>
          {notFound && (
            <p className="text-muted">
              {`Mã ${code.toUpperCase()} không có trong các đặt chỗ của bạn.`}
            </p>
          )}
        </div>

        {loadError && <Notice>{loadError}</Notice>}
        {!notFound && !journey && !loadError && <FormSkeleton rows={3} />}

        {journey && (
          <>
            <Section title="Chuyến hiện tại">
              <ol className="grid gap-4">
                {journey.segments.map((s) => (
                  <li key={s.flightId} className="font-bold">
                    {`${s.flightNumber}: ${clock(s.departureTime)} ${s.departureAirport} → ${clock(s.arrivalTime)} ${s.arrivalAirport}, ${dmy(s.departureTime)}`}
                  </li>
                ))}
              </ol>
              <p className="text-caption text-muted">
                {`${journey.fareFamilyName}. ${fareRules(journey).join(", ")}.`}
              </p>
            </Section>

            {actionError && <Notice>{actionError}</Notice>}

            <div className="grid gap-28 lg:grid-cols-[minmax(0,1fr)_360px] lg:items-start">
              <div className="grid gap-16">
                <div className="max-w-[260px]">
                  <Field
                    label="Ngày bay mới"
                    name="date"
                    type="date"
                    value={selectedDate}
                    onChange={(e) => {
                      setDate(e.target.value);
                      setChosen(undefined);
                    }}
                  />
                </div>

                {!selectedDate && (
                  <p className="text-muted">Hãy chọn ngày bay mới.</p>
                )}
                {selectedDate && !current && <FormSkeleton rows={2} />}
                {current?.error && <Notice>{current.error}</Notice>}
                {current?.options && current.options.length === 0 && (
                  <p className="text-muted">
                    Không có chuyến thay thế trong ngày này.
                  </p>
                )}
                {current?.options && current.options.length > 0 && (
                  <ul aria-label="Chuyến thay thế" className="grid gap-16">
                    {current.options.map((opt) => {
                      const key = itineraryKey(opt.itinerary);
                      return (
                        <li key={key}>
                          <OptionCard
                            option={opt}
                            selected={chosenKey === key}
                            onChoose={() => {
                              setActionError(undefined);
                              setChosen(
                                opt.itinerary.segments.map((s) => s.flightId),
                              );
                            }}
                          />
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>

              <Section title="Báo giá đổi chuyến">
                {!chosen && (
                  <p className="text-caption text-muted">
                    Chọn một chuyến để xem báo giá.
                  </p>
                )}
                {chosen && !quote && <FormSkeleton rows={2} />}
                {chosen && quote && (
                  <div className="grid gap-16">
                    <PriceTable
                      rows={[
                        ["Phí đổi chuyến", quote.changeFeeTotal],
                        [
                          "Chênh lệch giá vé",
                          Math.max(0, quote.newFareTotal - quote.oldFareTotal),
                        ],
                      ]}
                      total={quote.amountDue}
                      totalLabel="Phải trả"
                    />
                    <p className="text-caption text-muted">
                      {`Giá vé hiện tại ${vnd(quote.oldFareTotal)}, giá vé mới ${vnd(quote.newFareTotal)}. Chuyến mới rẻ hơn thì phần chênh lệch không được hoàn.`}
                    </p>
                    <p className="text-caption text-muted">
                      {quote.amountDue > 0
                        ? "Bạn sẽ được chuyển sang VNPay để thanh toán."
                        : "Đổi xong ngay, không cần thanh toán."}
                    </p>
                    <button
                      type="button"
                      className={pillPrimary}
                      disabled={busy}
                      onClick={confirmReschedule}
                    >
                      Xác nhận đổi
                    </button>
                  </div>
                )}
              </Section>
            </div>
          </>
        )}
      </main>
    </div>
  );
}
