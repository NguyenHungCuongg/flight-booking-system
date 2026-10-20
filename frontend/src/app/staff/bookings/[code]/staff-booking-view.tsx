"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { BookingBody, Section } from "@/app/bookings/[code]/booking-view";
import { FormSkeleton, Notice, pillLine, textLink } from "@/components/form";
import { ApiError, apiFetch, type Schemas } from "@/lib/api";
import { STATUS_LABEL, at, useNow } from "@/lib/booking";
import { useApi } from "@/lib/use-api";
import { StaffPage } from "../../staff-ui";

export default function StaffBookingView() {
  const { code } = useParams<{ code: string }>();
  const router = useRouter();
  const [resending, setResending] = useState(false);
  const [resendStatus, setResendStatus] = useState<string | null>(null);
  const [resendError, setResendError] = useState<string | null>(null);

  const res = useApi<Schemas["StaffBookingDetail"]>(
    `/staff/bookings/${encodeURIComponent(code)}`,
  );
  const now = useNow();

  async function handleResend(email: string) {
    setResending(true);
    setResendStatus(null);
    setResendError(null);
    try {
      await apiFetch(
        `/staff/bookings/${encodeURIComponent(code)}/resend-ticket-email`,
        { method: "POST" },
      );
      setResendStatus(`Đã gửi lại email vé tới ${email}.`);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        const next =
          typeof window !== "undefined"
            ? window.location.pathname + window.location.search
            : "/staff/bookings";
        router.replace(`/login?next=${encodeURIComponent(next)}`);
        return;
      }
      setResendError(
        err instanceof ApiError
          ? err.detail
          : "Gửi lại email vé chưa thành công",
      );
    } finally {
      setResending(false);
    }
  }

  const is404 = res && "error" in res && res.error.status === 404;

  return (
    <StaffPage>
      <div className="grid gap-13">
        <Link href="/staff/bookings" className={`${textLink} text-caption`}>
          Tra cứu đặt chỗ
        </Link>
        <h1 className="text-heading-lg font-bold break-words">
          {is404
            ? "Không tìm thấy đặt chỗ."
            : `Đặt chỗ ${code ? code.toUpperCase() : ""}.`}
        </h1>
        {res && "data" in res && (
          <p className="text-muted">
            {`Đặt lúc ${at(res.data.booking.createdAt)} (giờ Việt Nam)`}
          </p>
        )}
      </div>

      {res === undefined && <FormSkeleton rows={3} />}
      {res && "error" in res && !is404 && <Notice>{res.error.detail}</Notice>}

      {res && "data" in res && (
        <>
          {resendStatus && <Notice tone="ok">{resendStatus}</Notice>}
          {resendError && <Notice>{resendError}</Notice>}

          <Section title="Tài khoản đặt chỗ">
            <p className="text-body">
              {`${res.data.account.fullName}, ${res.data.account.email}`}
            </p>
          </Section>

          <section
            aria-label="Trạng thái"
            className="flex flex-wrap items-center justify-between gap-16 border border-line p-22"
          >
            <div className="grid gap-4">
              <p className="text-subheading font-bold">
                {STATUS_LABEL[res.data.booking.status] ??
                  res.data.booking.status}
              </p>
              {res.data.booking.status === "PENDING_PAYMENT" && (
                <p className="text-caption text-muted">
                  {`Giữ chỗ đến ${at(res.data.booking.holdExpiresAt)} (giờ Việt Nam)`}
                </p>
              )}
              {res.data.booking.status === "ISSUED" &&
                res.data.booking.issuedAt && (
                  <p className="text-caption text-muted">
                    {`Xuất vé lúc ${at(res.data.booking.issuedAt)} (giờ Việt Nam)`}
                  </p>
                )}
            </div>
            {res.data.booking.status === "ISSUED" && (
              <div className="flex flex-wrap gap-13">
                <button
                  type="button"
                  className={pillLine}
                  disabled={resending}
                  onClick={() => handleResend(res.data.booking.contact.email)}
                >
                  Gửi lại email vé
                </button>
              </div>
            )}
          </section>

          <BookingBody booking={res.data.booking} now={now} staff />
        </>
      )}
    </StaffPage>
  );
}

