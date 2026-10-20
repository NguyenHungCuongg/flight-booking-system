"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { Section } from "@/app/bookings/[code]/booking-view";
import PriceTable from "@/components/price-table";
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
import { ApiError, apiFetch, type Schemas } from "@/lib/api";
import {
  DIRECTION_LABEL,
  PAYMENT_STATUS,
  REFUND_SOURCE,
  REFUND_STATUS,
  STATUS_LABEL,
  at,
} from "@/lib/booking";
import { REFUND_PROVIDER, refundActions } from "@/lib/staff";
import { useApi } from "@/lib/use-api";
import { vnd } from "@/app/flights/itineraries";
import { StaffPage } from "../../staff-ui";

export default function RefundDetailView() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [version, setVersion] = useState(0);
  const [done, setDone] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const busyRef = useRef(false);
  const [busy, setBusy] = useState(false);

  const { pending, errors, submit } = useSubmit();

  const res = useApi<Schemas["RefundDetail"]>(
    `/staff/refund-requests/${encodeURIComponent(id)}`,
    version,
  );

  async function act(
    path: string,
    body?: unknown,
    ok?: string,
    form?: HTMLFormElement,
  ) {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setDone(null);
    setActionError(null);
    try {
      await apiFetch(path, {
        method: "POST",
        body,
      });
      if (ok) setDone(ok);
      if (form) form.reset();
      setVersion((v) => v + 1);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          const next =
            typeof window !== "undefined"
              ? window.location.pathname + window.location.search
              : "/staff/refunds";
          router.replace(`/login?next=${encodeURIComponent(next)}`);
          return;
        }
        if (err.errors.length > 0) {
          throw err;
        }
        setActionError(err.detail);
        setVersion((v) => v + 1);
        return;
      }
      setActionError("Thao tác thất bại");
      setVersion((v) => v + 1);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }

  const is404 = res && "error" in res && res.error.status === 404;
  const refund = res && "data" in res ? res.data : null;
  const acts = refund ? refundActions(refund.status, refund.source) : null;
  const isWorking = busy || pending;

  const handleApprove = async () => {
    if (!refund) return;
    const confirmed = window.confirm(
      `Duyệt yêu cầu và hoàn ${vnd(refund.refundAmount)} cho khách qua VNPay?`,
    );
    if (!confirmed) return;
    await act(
      `/staff/refund-requests/${encodeURIComponent(id)}/approve`,
      undefined,
      "Đã duyệt và hoàn tiền xong.",
    );
  };

  const handleRetry = async () => {
    const confirmed = window.confirm("Gọi lại VNPay để hoàn phần còn thiếu?");
    if (!confirmed) return;
    await act(
      `/staff/refund-requests/${encodeURIComponent(id)}/retry`,
      undefined,
      "Đã hoàn tiền xong.",
    );
  };

  const handleReject = async (data: FormData, form: HTMLFormElement) => {
    await act(
      `/staff/refund-requests/${encodeURIComponent(id)}/reject`,
      { reason: text(data, "reason") },
      "Đã từ chối yêu cầu. Khách sẽ nhận email kèm lý do.",
      form,
    );
  };

  const handleManual = async (data: FormData, form: HTMLFormElement) => {
    await act(
      `/staff/refund-requests/${encodeURIComponent(id)}/mark-manual`,
      { note: text(data, "note") },
      "Đã xác nhận hoàn thủ công.",
      form,
    );
  };

  return (
    <StaffPage>
      <div className="grid gap-13">
        <Link href="/staff/refunds" className={`${textLink} text-caption`}>
          Hàng đợi yêu cầu hoàn
        </Link>
        <h1 className="text-heading-lg font-bold break-words">
          {is404 ? "Không tìm thấy yêu cầu hoàn." : `Yêu cầu hoàn #${id}.`}
        </h1>
      </div>

      {done && <Notice tone="ok">{done}</Notice>}
      {actionError && <Notice>{actionError}</Notice>}
      {res && "error" in res && !is404 && <Notice>{res.error.detail}</Notice>}
      {res === undefined && <FormSkeleton rows={3} />}

      {refund && acts && (
        <>
          <section
            aria-label="Trạng thái"
            className="flex flex-wrap items-center justify-between gap-16 border border-line p-22"
          >
            <div className="grid gap-4">
              <p className="text-subheading font-bold">
                {REFUND_STATUS[refund.status] ?? refund.status}
              </p>
              <p className="text-caption text-muted">
                {`${refund.direction && refund.direction in DIRECTION_LABEL ? DIRECTION_LABEL[refund.direction as keyof typeof DIRECTION_LABEL] : refund.direction || "Khoản thanh toán thừa"}, ${REFUND_SOURCE[refund.source] ?? refund.source}`}
              </p>
              <p className="text-caption text-muted">
                {`Gửi lúc ${at(refund.createdAt)} (giờ Việt Nam)`}
              </p>
              {refund.completedAt && (
                <p className="text-caption text-muted">
                  {`Hoàn tất lúc ${at(refund.completedAt)} (giờ Việt Nam)`}
                </p>
              )}
            </div>
          </section>

          <div className="grid gap-38 lg:grid-cols-[minmax(0,1fr)_400px] lg:items-start">
            <div className="grid gap-38">
              <Section title="Xử lý">
                {refund.reason && (
                  <p className="text-body">{`Lý do của khách: ${refund.reason}`}</p>
                )}
                {refund.processedBy && (
                  <p className="text-body">
                    {`Người xử lý: ${refund.processedBy}${refund.processedAt ? `, lúc ${at(refund.processedAt)} (giờ Việt Nam)` : ""}`}
                  </p>
                )}
                {refund.staffNote && (
                  <p className="text-body">{`Ghi chú: ${refund.staffNote}`}</p>
                )}
                {refund.status === "APPROVED" && refund.lastError && (
                  <p className="text-body text-ink font-bold">{`Lỗi gần nhất: ${refund.lastError}`}</p>
                )}
                {!refund.reason &&
                  !refund.processedBy &&
                  !refund.staffNote &&
                  !(refund.status === "APPROVED" && refund.lastError) && (
                    <p className="text-muted">Chưa có ai xử lý.</p>
                  )}
              </Section>

              <Section title="Các lượt hoàn">
                {refund.attempts.length === 0 ? (
                  <p className="text-muted">Chưa có lượt hoàn nào.</p>
                ) : (
                  <ul className="grid gap-8">
                    {refund.attempts.map((a, i) => (
                      <li key={i} className="text-caption">
                        {`${REFUND_PROVIDER[a.provider] ?? a.provider}, ${PAYMENT_STATUS[a.status] ?? a.status}, ${vnd(a.amount)}, ${at(a.createdAt)}${a.responseCode ? `, mã ${a.responseCode}` : ""}`}
                      </li>
                    ))}
                  </ul>
                )}
              </Section>

              {(acts.approve || acts.reject || acts.settle) && (
                <Section title="Thao tác">
                  <div className="grid gap-22">
                    {acts.approve && (
                      <div>
                        <button
                          type="button"
                          className={pillDark}
                          disabled={isWorking}
                          onClick={handleApprove}
                        >
                          Duyệt
                        </button>
                      </div>
                    )}

                    {acts.reject && (
                      <form
                        className="grid gap-16"
                        onSubmit={async (e) => {
                          await submit(handleReject)(e);
                        }}
                      >
                        <Field
                          label="Lý do từ chối"
                          name="reason"
                          required
                          maxLength={500}
                          error={errors.reason}
                        />
                        <button
                          type="submit"
                          className={`${pillLine} justify-self-start`}
                          disabled={isWorking}
                        >
                          Từ chối
                        </button>
                      </form>
                    )}

                    {acts.settle && (
                      <div className="grid gap-22">
                        <div>
                          <button
                            type="button"
                            className={pillDark}
                            disabled={isWorking}
                            onClick={handleRetry}
                          >
                            Thử lại
                          </button>
                        </div>

                        <form
                          className="grid gap-16"
                          onSubmit={async (e) => {
                            await submit(handleManual)(e);
                          }}
                        >
                          <Field
                            label="Ghi chú hoàn thủ công"
                            name="note"
                            required
                            maxLength={500}
                            hint="Dùng khi đã chuyển tiền cho khách ngoài hệ thống."
                            error={errors.note}
                          />
                          <button
                            type="submit"
                            className={`${pillLine} justify-self-start`}
                            disabled={isWorking}
                          >
                            Xác nhận hoàn thủ công
                          </button>
                        </form>
                      </div>
                    )}
                  </div>
                </Section>
              )}
            </div>

            <div className="grid gap-38">
              <Section title="Số tiền hoàn">
                <PriceTable
                  rows={[
                    [
                      refund.source === "INVALID_PAYMENT"
                        ? "Số tiền đã thu"
                        : "Giá trị chiều",
                      refund.baseAmount,
                    ],
                    ["Phí hoàn", -refund.feeAmount],
                  ]}
                  total={refund.refundAmount}
                  totalLabel="Số tiền được hoàn"
                />
                <p className="text-caption text-muted">
                  {`Đã hoàn ${vnd(refund.refundedAmount)}`}
                </p>
              </Section>

              <Section title="Đặt chỗ">
                <div className="grid gap-4">
                  <Link
                    href={`/staff/bookings/${refund.bookingCode}`}
                    className={`${textLink} font-bold`}
                  >
                    {`Đặt chỗ ${refund.bookingCode}`}
                  </Link>
                  <p className="text-caption">
                    {STATUS_LABEL[
                      refund.bookingStatus as keyof typeof STATUS_LABEL
                    ] ?? refund.bookingStatus}
                  </p>
                  <p className="text-body">{refund.contactName}</p>
                  <p className="text-caption text-muted">
                    {`${refund.contactEmail}, ${refund.contactPhone}`}
                  </p>
                </div>
              </Section>
            </div>
          </div>
        </>
      )}
    </StaffPage>
  );
}

