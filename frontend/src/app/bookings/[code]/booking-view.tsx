"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import SiteHeader from "@/components/site-header";
import {
  FormSkeleton,
  Notice,
  pillDark,
  pillLine,
  textLink,
} from "@/components/form";
import { CABINS } from "@/components/landing/data";
import PriceTable from "@/components/price-table";
import { ApiError, apiFetch, type Schemas } from "@/lib/api";
import {
  DIRECTION_LABEL,
  PASSENGER_LABEL,
  STATUS_LABEL,
  clock,
  countdown,
  dmy,
  useNow,
} from "@/lib/booking";
import { fareRules, vnd } from "../../flights/itineraries";

type Detail = Schemas["BookingDetail"];

const PAYMENT_KIND: Record<string, string> = {
  CHARGE: "Thanh toán",
  REFUND: "Hoàn tiền",
};
const PAYMENT_STATUS: Record<string, string> = {
  PENDING: "đang xử lý",
  SUCCESS: "thành công",
  FAILED: "thất bại",
};

/** "08:00 16/10/2026", theo giờ trong chuỗi. */
const at = (iso: string) => `${clock(iso)} ${dmy(iso)}`;

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section
      aria-label={title}
      className="grid content-start gap-16 border-t border-line pt-22"
    >
      <h2 className="text-subheading font-bold">{title}</h2>
      {children}
    </section>
  );
}

/** Phần chỉ đọc của FR-41: hành trình, hành khách và vé, bảng giá, liên hệ, lượt thu. */
function BookingBody({ booking }: { booking: Detail }) {
  const rows: [string, number][] = [["Giá vé", booking.fareTotal]];
  if (booking.baggageTotal)
    rows.push(["Hành lý mua thêm", booking.baggageTotal]);
  if (booking.discountTotal) {
    rows.push([
      `Giảm giá ${booking.voucherCode ?? ""}`,
      -booking.discountTotal,
    ]);
  }

  return (
    <div className="grid gap-38 lg:grid-cols-[minmax(0,1fr)_400px] lg:items-start">
      <div className="grid gap-38">
        <Section title="Hành trình">
          {booking.journeys.map((j) => {
            const cabin =
              CABINS.find((c) => c.id === j.cabinClass)?.label ?? j.cabinClass;
            return (
              <div
                key={j.direction}
                className="grid gap-8 border border-line p-22"
              >
                <p className="font-bold">
                  {DIRECTION_LABEL[j.direction]}
                  {j.status === "REFUNDED" && ", đã hoàn"}
                </p>
                <ol className="grid gap-4">
                  {j.segments.map((s) => (
                    <li key={s.flightId}>
                      {`${s.flightNumber}: ${clock(s.departureTime)} ${s.departureAirport} → ${clock(s.arrivalTime)} ${s.arrivalAirport}, ${dmy(s.departureTime)}`}
                      {s.status === "CANCELLED" && " (đã huỷ)"}
                    </li>
                  ))}
                </ol>
                <p className="text-caption text-muted">
                  {`${j.fareFamilyName}, ${cabin}. ${fareRules(j).join(", ")}.`}
                </p>
              </div>
            );
          })}
        </Section>

        <Section title="Hành khách">
          <ul className="grid gap-16">
            {booking.passengers.map((p, i) => (
              <li key={i} className="grid gap-4 border border-line p-22">
                <p className="font-bold">{`${p.lastName} ${p.firstName}`}</p>
                <p className="text-caption text-muted">
                  {`${PASSENGER_LABEL[p.type]}, ${p.gender === "MALE" ? "nam" : "nữ"}, sinh ngày ${dmy(p.dateOfBirth)}`}
                  {p.passportNumber &&
                    p.passportExpiry &&
                    `, hộ chiếu ${p.passportNumber} (${p.nationality}, hết hạn ${dmy(p.passportExpiry)})`}
                </p>
                {p.tickets.map((t) => (
                  <p
                    key={`${t.direction}-${t.flightNumber}`}
                    className="text-caption"
                  >
                    {`Vé ${t.flightNumber}: ${t.ticketNumber ?? "chưa xuất vé"}`}
                  </p>
                ))}
                {booking.journeys.flatMap((j) =>
                  j.baggage
                    .filter((b) => b.passengerIndex === i)
                    .map((b) => (
                      <p key={j.direction} className="text-caption">
                        {`Hành lý mua thêm ${DIRECTION_LABEL[j.direction].toLowerCase()}: ${b.weightKg} kg`}
                      </p>
                    )),
                )}
              </li>
            ))}
          </ul>
        </Section>
      </div>

      <div className="grid gap-38">
        <Section title="Bảng giá">
          <PriceTable rows={rows} total={booking.totalAmount} />
        </Section>
        <Section title="Liên hệ">
          <p>{booking.contact.name}</p>
          <p className="text-caption text-muted">
            {`${booking.contact.email}, ${booking.contact.phone}`}
          </p>
        </Section>
        {booking.payments.length > 0 && (
          <Section title="Lịch sử thanh toán">
            <ul className="grid gap-4 text-caption">
              {booking.payments.map((p, i) => (
                <li key={i}>
                  {`${PAYMENT_KIND[p.kind] ?? p.kind}, ${PAYMENT_STATUS[p.status] ?? p.status}, ${vnd(p.amount)}, ${at(p.createdAt)}`}
                </li>
              ))}
            </ul>
          </Section>
        )}
      </div>
    </div>
  );
}

/**
 * C-09 (APP_FLOW §2.1, FR-41): chỉ chủ booking (của người khác backend trả 404, BR-104). Đang chờ thanh toán thì đếm
 * ngược hạn giữ chỗ, cho thanh toán (FR-30) và huỷ (FR-33). `?payment=` là kết quả VNPay do Return URL gắn vào.
 */
export default function BookingView() {
  const { code } = useParams<{ code: string }>();
  const params = useSearchParams();
  const payment = params.get("payment");
  const router = useRouter();
  const now = useNow();
  // undefined: đang tải; null: không tìm thấy.
  const [booking, setBooking] = useState<Detail | null>();
  const [loadError, setLoadError] = useState<string>();
  const [version, setVersion] = useState(0);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string>();

  useEffect(() => {
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
  }, [code, version, router]);
  const reload = () => setVersion((v) => v + 1);

  async function pay() {
    setBusy(true);
    setActionError(undefined);
    try {
      const { paymentUrl } = await apiFetch<Schemas["PaymentUrlResponse"]>(
        `/bookings/${encodeURIComponent(code)}/payments`,
        { method: "POST" },
      );
      // Giữ busy: trang đang chuyển sang VNPay.
      window.location.assign(paymentUrl);
    } catch (e) {
      setBusy(false);
      if (!(e instanceof ApiError)) throw e;
      // HOLD_EXPIRED, INVALID_STATE: trạng thái trên trang đã cũ.
      setActionError(e.detail);
      reload();
    }
  }

  async function cancel() {
    if (
      !confirm(
        `Huỷ giữ chỗ ${code.toUpperCase()}? Ghế đã giữ sẽ được trả lại, không khôi phục được.`,
      )
    ) {
      return;
    }
    setBusy(true);
    setActionError(undefined);
    try {
      await apiFetch(`/bookings/${encodeURIComponent(code)}/cancel`, {
        method: "POST",
      });
    } catch (e) {
      if (!(e instanceof ApiError)) throw e;
      setActionError(e.detail);
    } finally {
      setBusy(false);
      reload();
    }
  }

  const pending = booking?.status === "PENDING_PAYMENT";
  // Thời gian giữ chỗ còn lại (ms); null khi không chờ thanh toán hoặc chưa hydrate.
  const left =
    booking && booking.status === "PENDING_PAYMENT" && now !== null
      ? Date.parse(booking.holdExpiresAt) - now
      : null;
  const canPay = left !== null && left > 0;

  return (
    <div className="min-h-dvh bg-paper">
      <SiteHeader />
      <main className="mx-auto grid max-w-page gap-38 px-16 pt-22 pb-119 sm:px-38">
        <div className="grid gap-13">
          <Link href="/bookings" className={`${textLink} text-caption`}>
            Tất cả đặt chỗ
          </Link>
          <h1 className="text-heading-lg font-bold break-words">
            {booking === null
              ? "Không tìm thấy đặt chỗ."
              : `Đặt chỗ ${code.toUpperCase()}.`}
          </h1>
          {booking === null && (
            <p className="text-muted">
              {`Mã ${code.toUpperCase()} không có trong các đặt chỗ của bạn.`}
            </p>
          )}
          {booking && (
            <p className="text-muted">{`Đặt lúc ${at(booking.createdAt)} (giờ Việt Nam)`}</p>
          )}
        </div>

        {loadError && <Notice>{loadError}</Notice>}
        {booking === undefined && !loadError && <FormSkeleton rows={3} />}

        {booking && payment === "success" && (
          <Notice tone="ok">
            {booking.status === "ISSUED"
              ? `Thanh toán thành công. Vé điện tử đã được gửi tới ${booking.contact.email}.`
              : "Đã nhận kết quả từ VNPay, hệ thống đang xác nhận thanh toán. Hãy tải lại trang sau ít phút."}
          </Notice>
        )}
        {booking && payment === "failed" && (
          <Notice>
            Thanh toán chưa thành công.
            {canPay && " Bạn có thể thanh toán lại trước khi hết hạn giữ chỗ."}
          </Notice>
        )}
        {actionError && <Notice>{actionError}</Notice>}

        {booking && (
          <section
            aria-label="Trạng thái"
            className="grid gap-16 border border-ink p-22 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center"
          >
            <div className="grid gap-4">
              <p className="text-subheading font-bold">
                {STATUS_LABEL[booking.status]}
              </p>
              {canPay && <p>{`Còn ${countdown(left ?? 0)} để thanh toán`}</p>}
              {left !== null && !canPay && <p>Đã hết hạn giữ chỗ.</p>}
              {pending && (
                <p className="text-caption text-muted">
                  {`Giữ chỗ đến ${at(booking.holdExpiresAt)} (giờ Việt Nam)`}
                </p>
              )}
              {booking.status === "ISSUED" && booking.issuedAt && (
                <p className="text-caption text-muted">
                  {`Xuất vé lúc ${at(booking.issuedAt)} (giờ Việt Nam)`}
                </p>
              )}
            </div>
            {pending && (
              <div className="flex flex-wrap gap-13">
                {canPay && (
                  <button
                    type="button"
                    className={pillDark}
                    disabled={busy}
                    onClick={pay}
                  >
                    {`Thanh toán ${vnd(booking.totalAmount)}`}
                  </button>
                )}
                <button
                  type="button"
                  className={pillLine}
                  disabled={busy}
                  onClick={cancel}
                >
                  Huỷ giữ chỗ
                </button>
              </div>
            )}
          </section>
        )}

        {booking && <BookingBody booking={booking} />}
      </main>
    </div>
  );
}
