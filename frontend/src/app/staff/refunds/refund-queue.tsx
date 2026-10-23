"use client";

import Link from "next/link";
import StatusBadge from "@/components/status-badge";
import Form from "next/form";
import { useSearchParams } from "next/navigation";
import { FormSkeleton, Notice, SelectField, pillPrimary, textLink } from "@/components/form";
import type { Schemas } from "@/lib/api";
import { DIRECTION_LABEL, REFUND_SOURCE, REFUND_STATUS, at } from "@/lib/booking";
import { listQuery } from "@/lib/staff";
import { useApi } from "@/lib/use-api";
import { vnd } from "@/app/flights/itineraries";
import { Pager, StaffPage, tableBox, td, th } from "../staff-ui";

const STATUS_OPTIONS = [
  { value: "", label: "Tất cả" },
  ...Object.entries(REFUND_STATUS).map(([value, label]) => ({ value, label })),
];

const SOURCE_OPTIONS = [
  { value: "", label: "Tất cả" },
  ...Object.entries(REFUND_SOURCE).map(([value, label]) => ({ value, label })),
];

export default function RefundQueue() {
  const params = useSearchParams();
  const status = params.get("status") ?? "";
  const source = params.get("source") ?? "";

  const query = listQuery(params, ["status", "source"]);
  const res = useApi<Schemas["PagedModelRefundSummary"]>(
    `/staff/refund-requests?${query}`,
  );

  return (
    <StaffPage>
      <div className="grid gap-13">
        <h1 className="text-heading-lg font-bold break-words">
          Yêu cầu hoàn.
        </h1>
        <p className="text-muted">Cũ nhất trước.</p>
      </div>

      <Form
        action="/staff/refunds"
        key={params.toString()}
        className="grid gap-16 sm:grid-cols-[220px_220px_auto] sm:items-end sm:justify-start"
      >
        <SelectField
          label="Trạng thái"
          name="status"
          defaultValue={status}
        >
          {STATUS_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </SelectField>
        <SelectField
          label="Nguồn"
          name="source"
          defaultValue={source}
        >
          {SOURCE_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </SelectField>
        <button type="submit" className={pillPrimary}>
          Lọc
        </button>
      </Form>

      {res === undefined && <FormSkeleton rows={2} />}
      {res && "error" in res && <Notice>{res.error.detail}</Notice>}
      {res && "data" in res && (
        <>
          {res.data.content.length === 0 ? (
            <p className="text-muted">Không có yêu cầu nào khớp bộ lọc.</p>
          ) : (
            <div className="grid gap-16">
              <div className={tableBox}>
                <table
                  aria-label="Hàng đợi yêu cầu hoàn"
                  className="w-full border-collapse"
                >
                  <thead>
                    <tr>
                      <th className={th}>Yêu cầu</th>
                      <th className={th}>Mã đặt chỗ</th>
                      <th className={th}>Chiều</th>
                      <th className={th}>Nguồn</th>
                      <th className={th}>Số tiền</th>
                      <th className={th}>Trạng thái</th>
                      <th className={th}>Gửi lúc</th>
                    </tr>
                  </thead>
                  <tbody>
                    {res.data.content.map((r) => (
                      <tr key={r.id}>
                        <td className={td}>
                          <Link
                            href={`/staff/refunds/${r.id}`}
                            className={textLink}
                          >
                            #{r.id}
                          </Link>
                        </td>
                        <td className={td}>
                          <Link
                            href={`/staff/bookings/${r.bookingCode}`}
                            className={textLink}
                          >
                            {r.bookingCode}
                          </Link>
                        </td>
                        <td className={td}>
                          {r.direction &&
                          r.direction in DIRECTION_LABEL
                            ? DIRECTION_LABEL[
                                r.direction as keyof typeof DIRECTION_LABEL
                              ]
                            : r.direction || "Khoản thanh toán thừa"}
                        </td>
                        <td className={td}>
                          {REFUND_SOURCE[r.source] ?? r.source}
                        </td>
                        <td className={td}>
                          <span className="font-bold">
                            {vnd(r.refundAmount)}
                          </span>
                        </td>
                        <td className={td}>
                          <StatusBadge status={r.status} label={REFUND_STATUS[r.status] ?? r.status} />
                        </td>
                        <td className={td}>{at(r.createdAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="text-caption text-muted">Giờ Việt Nam.</p>
            </div>
          )}
          <Pager
            path="/staff/refunds"
            params={params}
            totalPages={res.data.page.totalPages}
          />
        </>
      )}
    </StaffPage>
  );
}

