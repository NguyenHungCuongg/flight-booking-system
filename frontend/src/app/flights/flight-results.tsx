"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { Notice, pillLine, textLink } from "@/components/form";
import { CABINS } from "@/components/landing/data";
import { ddmm, parseIso, weekday } from "@/components/landing/search-context";
import SiteHeader from "@/components/site-header";
import { ApiError, apiFetch, type Schemas } from "@/lib/api";
import { formFromParams } from "@/lib/search-query";
import { saveSelection, type Journey } from "@/lib/selection";
import {
  departsAfter,
  first,
  hhmm,
  itineraryKey,
  last,
  vnd,
  type FareOption,
  type Itinerary,
} from "./itineraries";
import ResultList from "./result-list";
import SearchBar from "./search-bar";

type Load =
  | { status: "loading" }
  | { status: "ok"; itineraries: Itinerary[] }
  | { status: "error"; error: ApiError };

/** Gọi GET /flights/search khi chuỗi tham số đổi; qs = null thì không gọi. */
function useFlightSearch(qs: string | null) {
  const [result, setResult] = useState<{ qs: string; load: Load } | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!qs) return;
    let live = true;
    apiFetch<Schemas["SearchResult"]>(`/flights/search?${qs}`).then(
      (r) =>
        live &&
        setResult({ qs, load: { status: "ok", itineraries: r.itineraries } }),
      (e: ApiError) =>
        live && setResult({ qs, load: { status: "error", error: e } }),
    );
    return () => {
      live = false;
    };
  }, [qs, attempt]);
  const load: Load = result?.qs === qs ? result.load : { status: "loading" };
  return {
    load,
    retry: () => {
      setResult(null);
      setAttempt((n) => n + 1);
    },
  };
}

function dateLabel(iso: string) {
  const d = parseIso(iso);
  return d ? `${weekday(d)}, ${ddmm(d)}` : iso;
}

function paxLabel(adults: number, children: number, infants: number) {
  return [
    `${adults} người lớn`,
    children && `${children} trẻ em`,
    infants && `${infants} em bé`,
  ]
    .filter(Boolean)
    .join(", ");
}

function Skeleton() {
  return (
    <div aria-hidden="true" className="grid gap-16 motion-safe:animate-pulse">
      {[0, 1, 2].map((i) => (
        <div key={i} className="h-140 border border-line bg-tint" />
      ))}
    </div>
  );
}

function LoadError({ error, retry }: { error: ApiError; retry: () => void }) {
  return (
    <Notice>
      <p className="font-bold">
        {error.errors.length ? "Điều kiện tìm kiếm chưa hợp lệ." : error.detail}
      </p>
      {error.errors.map((e) => (
        <p key={e.field}>{e.message}</p>
      ))}
      <p className="mt-8">
        {error.status === 0 ? (
          <button type="button" className={textLink} onClick={retry}>
            Thử lại
          </button>
        ) : (
          "Hãy sửa điều kiện bằng nút Đổi tìm kiếm phía trên."
        )}
      </p>
    </Notice>
  );
}

/**
 * C-02 (APP_FLOW §2.1, UF-01). Một chiều: chọn gói giá là xong. Khứ hồi (FR-12): bước 1 chọn chiều đi, URL thêm
 * `out` (id các chặng) và `outFare`; bước 2 chọn chiều về, chỉ hiện hành trình thoả BR-04. Bước nằm trong URL nên
 * Back, Forward và tải lại trang giữ đúng bước. Chọn xong thì lưu lựa chọn vào sessionStorage rồi mở /checkout.
 */
export default function FlightResults() {
  const router = useRouter();
  const params = useSearchParams();
  const from = params.get("from") ?? "";
  const to = params.get("to") ?? "";
  const date = params.get("date") ?? "";
  const returnDate = params.get("returnDate");
  const adults = params.get("adults") ?? "1";
  const children = params.get("children") ?? "0";
  const infants = params.get("infants") ?? "0";
  const cabin = params.get("cabin") ?? "ECONOMY";
  const hasQuery = Boolean(from && to && date);
  const badReturn = Boolean(returnDate && returnDate < date);

  const query = (f: string, t: string, d: string) =>
    new URLSearchParams({
      from: f,
      to: t,
      date: d,
      adults,
      children,
      infants,
      cabin,
    }).toString();
  // Tải cả hai chiều cùng lúc để bước 2 hiện ngay; tham số không đổi giữa hai bước nên không tải lại.
  const outbound = useFlightSearch(hasQuery ? query(from, to, date) : null);
  const inbound = useFlightSearch(
    hasQuery && returnDate && !badReturn ? query(to, from, returnDate) : null,
  );

  // Chiều đi đã chọn (bước 2) tìm lại trong kết quả chiều đi theo `out` và `outFare` của URL.
  const outKey = params.get("out");
  const outFare = Number(params.get("outFare"));
  const picked = (() => {
    if (!returnDate || !outKey || outbound.load.status !== "ok") return null;
    const itinerary = outbound.load.itineraries.find(
      (it) => itineraryKey(it) === outKey,
    );
    const fare = itinerary?.fareOptions.find((f) => f.fareFamilyId === outFare);
    return itinerary && fare ? { itinerary, fare } : null;
  })();
  const step2 = picked !== null;
  // Chuyến đã hết chỗ hoặc URL sửa tay: quay về bước 1 và báo cho khách.
  const lost =
    Boolean(returnDate && outKey) && outbound.load.status === "ok" && !step2;

  const counts = {
    adults: Number(adults),
    children: Number(children),
    infants: Number(infants),
  };
  const passengers = counts.adults + counts.children + counts.infants;
  const cabinLabel = CABINS.find((c) => c.id === cabin)?.label ?? cabin;
  const step1Url = (() => {
    const p = new URLSearchParams(params);
    p.delete("out");
    p.delete("outFare");
    return `/flights?${p}`;
  })();

  const journey = (
    direction: Journey["direction"],
    itinerary: Itinerary,
    fare: FareOption,
  ): Journey => ({
    direction,
    flightIds: itinerary.segments.map((s) => s.flightId),
    fareFamilyId: fare.fareFamilyId,
    itinerary,
    fare,
  });

  const choose = (itinerary: Itinerary, fare: FareOption) => {
    if (returnDate && !picked) {
      const p = new URLSearchParams(params);
      p.delete("unavailable");
      p.set("out", itineraryKey(itinerary));
      p.set("outFare", String(fare.fareFamilyId));
      router.push(`/flights?${p}`);
      return;
    }
    const journeys = picked
      ? [
          journey("OUTBOUND", picked.itinerary, picked.fare),
          journey("RETURN", itinerary, fare),
        ]
      : [journey("OUTBOUND", itinerary, fare)];
    saveSelection({ ...counts, journeys });
    // /checkout cần đăng nhập: proxy.ts chuyển về /login?next=%2Fcheckout, đăng nhập xong quay lại (UF-01).
    router.push("/checkout");
  };

  const title = !hasQuery
    ? "Tìm chuyến bay."
    : !returnDate
      ? "Chọn chuyến bay."
      : step2
        ? "Chọn chiều về."
        : "Chọn chiều đi.";
  const [legFrom, legTo, legDate] = picked
    ? [to, from, returnDate ?? date]
    : [from, to, date];
  const current = picked ? inbound : outbound;

  let body;
  if (!hasQuery) {
    body = (
      <p className="text-muted">
        Chọn điểm đi, điểm đến và ngày bay để xem các chuyến còn chỗ.
      </p>
    );
  } else if (badReturn) {
    body = (
      <Notice>
        <p className="font-bold">Ngày về không được trước ngày đi.</p>
        <p className="mt-8">Hãy chọn lại ngày ở ô tìm phía trên.</p>
      </Notice>
    );
  } else if (current.load.status === "loading") {
    body = <Skeleton />;
  } else if (current.load.status === "error") {
    body = <LoadError error={current.load.error} retry={current.retry} />;
  } else if (picked) {
    const all = current.load.itineraries;
    const allowed = all.filter(departsAfter(picked.itinerary));
    const hidden = all.length - allowed.length;
    body = (
      <ResultList
        key="return"
        itineraries={allowed}
        passengers={passengers}
        note={
          hidden
            ? `Đã ẩn ${hidden} hành trình cất cánh trước giờ hạ cánh của chiều đi.`
            : undefined
        }
        onChoose={choose}
      />
    );
  } else {
    body = (
      <ResultList
        key="outbound"
        itineraries={current.load.itineraries}
        passengers={passengers}
        onChoose={choose}
      />
    );
  }

  return (
    <div className="min-h-dvh bg-paper">
      <SiteHeader />

      <main className="mx-auto grid max-w-page gap-38 px-16 pt-22 pb-119 sm:px-38">
        <div className="grid gap-13">
          {returnDate && hasQuery && (
            <ol className="flex gap-22 text-caption">
              {["Chiều đi", "Chiều về"].map((label, i) => {
                const active = (i === 1) === step2;
                return (
                  <li
                    key={label}
                    aria-current={active ? "step" : undefined}
                    className={active ? "font-bold" : "text-muted"}
                  >
                    {i + 1}. {label}
                  </li>
                );
              })}
            </ol>
          )}
          <h1 className="text-heading-lg font-bold">{title}</h1>
        </div>

        <SearchBar
          key={params.toString()}
          initial={formFromParams(params)}
          summary={
            hasQuery && !badReturn ? (
              <p className="text-muted">
                {legFrom} → {legTo}, {dateLabel(legDate)},{" "}
                {paxLabel(counts.adults, counts.children, counts.infants)},{" "}
                {cabinLabel}
              </p>
            ) : null
          }
        />

        {lost && (
          <Notice>
            Chiều đi đã chọn không còn bán hoặc đã hết chỗ. Hãy chọn lại chiều
            đi.
          </Notice>
        )}

        {params.get("unavailable") && (
          <Notice>
            Chuyến bay bạn chọn không còn đủ chỗ hoặc đã ngừng bán. Hãy chọn
            chuyến khác.
          </Notice>
        )}

        {picked && (
          <section
            aria-label="Chiều đi đã chọn"
            className="grid gap-16 border border-ink p-22 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
          >
            <div className="grid gap-4">
              <p className="text-caption text-muted">Chiều đi đã chọn</p>
              <p className="font-bold">
                {first(picked.itinerary).airline.name}{" "}
                {picked.itinerary.segments
                  .map((s) => s.flightNumber)
                  .join(" + ")}
                {": "}
                {hhmm(first(picked.itinerary).departure)} {from} →{" "}
                {hhmm(last(picked.itinerary).arrival)} {to}, {dateLabel(date)}
              </p>
              <p className="text-caption text-muted">
                {picked.fare.name}, {vnd(picked.fare.totalPrice)}
              </p>
            </div>
            <Link href={step1Url} className={pillLine}>
              Đổi chiều đi
            </Link>
          </section>
        )}

        {body}
      </main>
    </div>
  );
}
