"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import SiteHeader from "@/components/site-header";
import {
  Field,
  FormSkeleton,
  Notice,
  pillDark,
  pillLine,
  text,
  textLink,
  useSubmit,
} from "@/components/form";
import PriceTable from "@/components/price-table";
import { ApiError, apiFetch, type Schemas } from "@/lib/api";
import { DIRECTION_LABEL, directionParam } from "@/lib/booking";
import { Section } from "../../booking-view";

type Quote = Schemas["RefundQuote"];

/**
 * C-10 (APP_FLOW §2.1, UF-02, FR-50, FR-51): xem số tiền hoàn (BR-52, BR-53, BR-61) rồi gửi yêu cầu hoàn một chiều.
 * Quá hạn hoặc không đủ điều kiện thì hiện câu báo của backend, không hiện form.
 */
export default function RefundView() {
  const { code, direction } = useParams<{ code: string; direction: string }>();
  const dir = directionParam(direction);
  const router = useRouter();
  // undefined: đang tải; null: không tìm thấy.
  const [quote, setQuote] = useState<Quote | null>();
  const [blocked, setBlocked] = useState<string>();
  const { pending, errors, formError, submit } = useSubmit();
  const sent = useRef(false);

  useEffect(() => {
    if (dir === null) return;
    let live = true;
    apiFetch<Quote>(
      `/bookings/${encodeURIComponent(code)}/journeys/${dir}/refund-quote`,
    ).then(
      (q) => live && setQuote(q),
      (e: ApiError) => {
        if (!live) return;
        if (e.status === 401) {
          router.replace(
            `/login?next=${encodeURIComponent(location.pathname + location.search)}`,
          );
        } else if (e.status === 404) {
          setQuote(null);
        } else {
          setBlocked(e.detail);
        }
      },
    );
    return () => {
      live = false;
    };
  }, [code, dir, router]);

  const notFound = dir === null || quote === null;
  const bookingHref = `/bookings/${encodeURIComponent(code)}`;

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (sent.current || !dir) return;
    sent.current = true;
    await submit(
      async (data) => {
        try {
          await apiFetch<Schemas["RefundCreatedResponse"]>(
            `/bookings/${encodeURIComponent(code)}/journeys/${dir}/refund-requests`,
            { method: "POST", body: { reason: text(data, "reason") } },
          );
          router.push(`${bookingHref}?refund=sent`);
        } catch (err) {
          sent.current = false;
          throw err;
        }
      },
      (err) => {
        if (err.status === 401) {
          router.replace(
            `/login?next=${encodeURIComponent(location.pathname + location.search)}`,
          );
          return true;
        }
        return false;
      },
    )(e);
  }

  return (
    <div className="min-h-dvh bg-paper">
      <SiteHeader />
      <main className="mx-auto grid max-w-page gap-38 px-16 pt-22 pb-119 sm:px-38">
        <div className="grid gap-13">
          <Link href={bookingHref} className={`${textLink} text-caption`}>
            {`Đặt chỗ ${code.toUpperCase()}`}
          </Link>
          <h1 className="text-heading-lg font-bold break-words">
            {notFound
              ? "Không tìm thấy đặt chỗ."
              : `Hoàn ${DIRECTION_LABEL[dir].toLowerCase()}.`}
          </h1>
          {notFound && (
            <p className="text-muted">
              {`Mã ${code.toUpperCase()} không có trong các đặt chỗ của bạn.`}
            </p>
          )}
        </div>

        {blocked && <Notice>{blocked}</Notice>}
        {!notFound && quote === undefined && !blocked && (
          <FormSkeleton rows={2} />
        )}

        {quote && (
          <div className="grid max-w-[560px] gap-38">
            <Section title="Số tiền hoàn">
              <PriceTable
                rows={[
                  ["Giá trị chiều", quote.journeyValue],
                  ["Phí hoàn", -quote.fee],
                ]}
                total={quote.refundAmount}
                totalLabel="Số tiền được hoàn"
              />
              <p className="text-caption text-muted">
                Số tiền được chốt lúc gửi yêu cầu. Mã giảm giá đã dùng không
                được trả lại.
              </p>
            </Section>

            <form onSubmit={onSubmit} className="grid gap-22">
              <Field
                label="Lý do hoàn vé"
                name="reason"
                required
                maxLength={500}
                error={errors.reason}
              />
              {formError && <Notice>{formError}</Notice>}
              <div className="flex flex-wrap gap-13">
                <button type="submit" className={pillDark} disabled={pending}>
                  Gửi yêu cầu hoàn
                </button>
                <Link href={bookingHref} className={pillLine}>
                  Quay lại
                </Link>
              </div>
            </form>
          </div>
        )}
      </main>
    </div>
  );
}
