"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { FormSkeleton, Notice, textLink } from "@/components/form";
import type { Schemas } from "@/lib/api";
import { PASSENGER_LABEL, clock, dmy } from "@/lib/booking";
import { FLIGHT_STATUS, cabinLabel } from "@/lib/staff";
import { useApi } from "@/lib/use-api";
import { StaffPage, tableBox, td, th } from "../../../staff-ui";

export default function ManifestView() {
  const { id } = useParams<{ id: string }>();
  const res = useApi<Schemas["Manifest"]>(
    `/staff/flights/${encodeURIComponent(id)}/passengers`,
  );

  const is404 = res && "error" in res && res.error.status === 404;

  return (
    <StaffPage>
      <div className="grid gap-13">
        <Link href="/staff/flights" className={`${textLink} text-caption`}>
          Danh sách chuyến
        </Link>
        <h1 className="text-heading-lg font-bold break-words">
          {is404
            ? "Không tìm thấy chuyến bay."
            : res && "data" in res
              ? `Hành khách ${res.data.flight.flightNumber}.`
              : "Hành khách theo chuyến."}
        </h1>
        {res && "data" in res && (
          <div className="grid gap-4">
            <p className="text-body">
              {`${res.data.flight.departureAirport} → ${res.data.flight.arrivalAirport}, cất cánh ${clock(res.data.flight.departureTime)} ${dmy(res.data.flight.departureTime)} (giờ ${res.data.flight.departureAirport}), ${FLIGHT_STATUS[res.data.flight.status] ?? res.data.flight.status}`}
            </p>
            <p className="text-caption text-muted">
              {`${res.data.passengers.length} hành khách có vé hiệu lực.`}
            </p>
          </div>
        )}
      </div>

      {res === undefined && <FormSkeleton rows={3} />}
      {res && "error" in res && !is404 && <Notice>{res.error.detail}</Notice>}

      {res && "data" in res && (
        <>
          {res.data.passengers.length === 0 ? (
            <p className="text-muted">
              Chuyến này chưa có hành khách nào có vé.
            </p>
          ) : (
            <div className={tableBox}>
              <table aria-label="Hành khách" className="w-full border-collapse">
                <thead>
                  <tr>
                    <th className={th}>Họ tên</th>
                    <th className={th}>Loại khách</th>
                    <th className={th}>Hạng ghế</th>
                    <th className={th}>Số vé</th>
                    <th className={th}>Mã đặt chỗ</th>
                    <th className={th}>Liên hệ</th>
                  </tr>
                </thead>
                <tbody>
                  {res.data.passengers.map((p, i) => (
                    <tr key={p.ticketNumber || i}>
                      <td className={td}>
                        <span className="font-bold">{`${p.lastName} ${p.firstName}`}</span>
                      </td>
                      <td className={td}>
                        {PASSENGER_LABEL[
                          p.type as keyof typeof PASSENGER_LABEL
                        ] ?? p.type}
                      </td>
                      <td className={td}>{cabinLabel(p.cabinClass)}</td>
                      <td className={td}>{p.ticketNumber}</td>
                      <td className={td}>
                        <Link
                          href={`/staff/bookings/${p.bookingCode}`}
                          className={textLink}
                        >
                          {p.bookingCode}
                        </Link>
                      </td>
                      <td className={td}>
                        <p>{p.contactPhone}</p>
                        <p className="text-caption text-muted">
                          {p.contactEmail}
                        </p>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </StaffPage>
  );
}

