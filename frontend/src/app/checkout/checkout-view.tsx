"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { FormSkeleton, Notice, textLink } from "@/components/form";
import { useAirports } from "@/lib/airports";
import { ApiError, apiFetch, type User } from "@/lib/api";
import { flightsUrl } from "@/lib/search-query";
import { useSelection } from "@/lib/selection";
import { first } from "../flights/itineraries";
import { isInternational } from "./booking-request";
import CheckoutForm, { type BaggageOption } from "./checkout-form";
import CheckoutHeader from "./checkout-header";

/**
 * C-07: đọc lựa chọn ở sessionStorage (Plan 08), tài khoản để điền sẵn liên hệ (FR-21), danh mục sân bay để biết chặng
 * quốc tế (BR-13) và bảng giá hành lý của từng hãng (FR-22), rồi mới mở form.
 */
export default function CheckoutView() {
  const router = useRouter();
  const selection = useSelection();
  const { airports, failed } = useAirports();
  const [user, setUser] = useState<User>();
  const [loadError, setLoadError] = useState<string>();
  // undefined: đang tải; null: tải lỗi (form vẫn mở, chỉ không mua thêm hành lý được).
  const [bags, setBags] = useState<Record<string, BaggageOption[]> | null>();

  useEffect(() => {
    apiFetch<User>("/me").then(setUser, (e: ApiError) => {
      if (e.status === 401) router.replace("/login?next=%2Fcheckout");
      else setLoadError(e.detail);
    });
  }, [router]);

  // Mã các hãng khai thác, nối thành chuỗi để effect chỉ chạy lại khi đổi hãng.
  const airlines = selection
    ? [
        ...new Set(
          selection.journeys.map((j) => first(j.itinerary).airline.code),
        ),
      ].join(",")
    : "";
  useEffect(() => {
    if (!airlines) return;
    let live = true;
    Promise.all(
      airlines
        .split(",")
        .map((code) =>
          apiFetch<BaggageOption[]>(`/airlines/${code}/baggage-options`).then(
            (list) => [code, list] as const,
          ),
        ),
    ).then(
      (pairs) => live && setBags(Object.fromEntries(pairs)),
      () => live && setBags(null),
    );
    return () => {
      live = false;
    };
  }, [airlines]);

  return (
    <div className="min-h-dvh">
      <CheckoutHeader
        backUrl={selection ? flightsUrl(selection) : "/flights"}
      />
      <main className="mx-auto grid max-w-page gap-28 px-16 pt-16 pb-80 sm:px-38">
        {selection === null ? (
          <div className="grid gap-13">
            <h1 className="text-heading-lg font-bold break-words">
              Chưa có chuyến bay nào được chọn.
            </h1>
            <p className="text-muted">
              Hãy tìm và chọn chuyến bay trước khi đặt vé.{" "}
              <Link href="/flights" className={textLink}>
                Tìm chuyến bay
              </Link>
            </p>
          </div>
        ) : (
          <>
            <h1 className="text-heading-lg font-bold break-words">
              Hoàn tất đặt vé.
            </h1>
            {loadError || failed ? (
              <Notice>
                {loadError ??
                  "Không tải được danh sách sân bay. Hãy tải lại trang."}
              </Notice>
            ) : selection && user && airports && bags !== undefined ? (
              <CheckoutForm
                selection={selection}
                user={user}
                international={isInternational(selection, airports)}
                baggage={bags}
              />
            ) : (
              <FormSkeleton rows={4} />
            )}
          </>
        )}
      </main>
    </div>
  );
}
