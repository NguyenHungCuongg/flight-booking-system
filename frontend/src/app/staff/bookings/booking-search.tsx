"use client";

import Link from "next/link";
import Form from "next/form";
import { useSearchParams } from "next/navigation";
import { Field, FormSkeleton, Notice, SelectField, pillDark, textLink } from "@/components/form";
import type { Schemas } from "@/lib/api";
import { DIRECTION_LABEL, STATUS_LABEL, at, clock, dmy } from "@/lib/booking";
import { listQuery } from "@/lib/staff";
import { useApi } from "@/lib/use-api";
import { vnd } from "@/app/flights/itineraries";
import { Pager, StaffPage, tableBox, td, th } from "../staff-ui";

const STATUS_OPTIONS = [
  { value: "", label: "Tất cả" },
  ...Object.entries(STATUS_LABEL).map(([value, label]) => ({ value, label })),
];

export default function BookingSearch() {
  const params = useSearchParams();
  const q = params.get("q") ?? "";
  const status = params.get("status") ?? "";

  const query = listQuery(params, ["q", "status"]);
  const res = useApi<Schemas["PagedModelBookingSummary"]>(
    `/staff/bookings?${query}`,
  );

  return (
    <StaffPage>
      <div className="grid gap-13">
        <h1 className="text-heading-lg font-bold break-words">
          Tra cứu đặt chỗ.
        </h1>
      </div>

      <Form
        action="/staff/bookings"
        key={params.toString()}
        className="grid gap-16 sm:grid-cols-[minmax(0,1fr)_220px_auto] sm:items-end"
      >
        <Field
          label="Mã đặt chỗ, email hoặc số điện thoại"
          name="q"
          type="search"
          defaultValue={q}
        />
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
        <button type="submit" className={pillDark}>
          Tìm
        </button>
      </Form>

      {res === undefined && <FormSkeleton rows={2} />}
      {res && "error" in res && <Notice>{res.error.detail}</Notice>}
      {res && "data" in res && (
        <>
          {res.data.content.length === 0 ? (
            <p className="text-muted">
              {q
                ? `Không có đặt chỗ nào khớp "${q}".`
                : "Chưa có đặt chỗ nào."}
            </p>
          ) : (
            <div className={tableBox}>
              <table
                aria-label="Kết quả tra cứu"
                className="w-full border-collapse"
              >
                <thead>
                  <tr>
                    <th className={th}>Mã đặt chỗ</th>
                    <th className={th}>Trạng thái</th>
                    <th className={th}>Liên hệ</th>
                    <th className={th}>Hành trình</th>
                    <th className={th}>Tổng tiền</th>
                    <th className={th}>Đặt lúc</th>
                  </tr>
                </thead>
                <tbody>
                  {res.data.content.map((b) => (
                    <tr key={b.code}>
                      <td className={td}>
                        <Link
                          href={`/staff/bookings/${b.code}`}
                          className={textLink}
                        >
                          {b.code}
                        </Link>
                      </td>
                      <td className={td}>
                        {STATUS_LABEL[b.status] ?? b.status}
                      </td>
                      <td className={td}>
                        <p className="font-bold">{b.contactName}</p>
                        <p className="text-caption text-muted">
                          {b.contactEmail}, {b.contactPhone}
                        </p>
                      </td>
                      <td className={td}>
                        {b.routes.map((r) => (
                          <p key={r.direction} className="text-caption">
                            {`${DIRECTION_LABEL[r.direction]}: ${r.departureAirport} → ${r.arrivalAirport}, ${clock(r.departureTime)} ${dmy(r.departureTime)}`}
                          </p>
                        ))}
                      </td>
                      <td className={td}>
                        <span className="font-bold">{vnd(b.totalAmount)}</span>
                      </td>
                      <td className={td}>{at(b.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <Pager
            path="/staff/bookings"
            params={params}
            totalPages={res.data.page.totalPages}
          />
        </>
      )}
    </StaffPage>
  );
}

