"use client";

import Link from "next/link";
import StatusBadge from "@/components/status-badge";
import Form from "next/form";
import { useSearchParams } from "next/navigation";
import { Field, FormSkeleton, Notice, SelectField, pillPrimary, textLink } from "@/components/form";
import type { Schemas } from "@/lib/api";
import { useAirports } from "@/lib/airports";
import { clock, dmy } from "@/lib/booking";
import { FLIGHT_STATUS, cabinLabel, listQuery } from "@/lib/staff";
import { useApi } from "@/lib/use-api";
import { Pager, StaffPage, tableBox, td, th } from "../staff-ui";

const STATUS_OPTIONS = [
  { value: "", label: "Tất cả" },
  ...Object.entries(FLIGHT_STATUS).map(([value, label]) => ({ value, label })),
];

export default function FlightList() {
  const params = useSearchParams();
  const from = params.get("from") ?? "";
  const to = params.get("to") ?? "";
  const date = params.get("date") ?? "";
  const airline = params.get("airline") ?? "";
  const status = params.get("status") ?? "";

  const { airports, failed: airportsFailed } = useAirports();
  const airlinesRes = useApi<Schemas["AirlineResponse"][]>("/airlines");
  const airlinesReady = airlinesRes !== undefined;
  const airportsReady = airports !== null || airportsFailed;

  const query = listQuery(params, ["from", "to", "date", "airline", "status"]);
  const flightsRes = useApi<Schemas["PagedModelFlightView"]>(
    `/staff/flights?${query}`,
  );

  // Giá trị đang lọc trên URL luôn có trong ô chọn, kể cả khi sân bay/hãng đã tắt hoặc danh sách tải lỗi.
  const withCurrent = (
    options: { value: string; label: string }[],
    current: string,
  ) =>
    current && !options.some((o) => o.value === current)
      ? [...options, { value: current, label: current }]
      : options;

  const airportOptions = [
    { value: "", label: "Tất cả" },
    ...(airports ?? []).map((a) => ({
      value: a.code,
      label: `${a.code}, ${a.city}`,
    })),
  ];

  const airlineOptions = [
    { value: "", label: "Tất cả" },
    ...(airlinesRes && "data" in airlinesRes ? airlinesRes.data : []).map(
      (a) => ({
        value: a.code,
        label: `${a.code}, ${a.name}`,
      }),
    ),
  ];

  return (
    <StaffPage>
      <div className="grid gap-13">
        <h1 className="text-heading-lg font-bold break-words">Chuyến bay.</h1>
      </div>

      {!airportsReady || !airlinesReady ? (
        <FormSkeleton rows={1} />
      ) : (
        <Form
          action="/staff/flights"
          key={params.toString()}
          className="grid gap-16 sm:grid-cols-[repeat(auto-fit,minmax(180px,1fr))] sm:items-end"
        >
          <SelectField
            label="Điểm đi"
            name="from"
            defaultValue={from}
          >
            {withCurrent(airportOptions, from).map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </SelectField>
          <SelectField
            label="Điểm đến"
            name="to"
            defaultValue={to}
          >
            {withCurrent(airportOptions, to).map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </SelectField>
          <Field
            label="Ngày bay"
            name="date"
            type="date"
            defaultValue={date}
          />
          <SelectField
            label="Hãng bay"
            name="airline"
            defaultValue={airline}
          >
            {withCurrent(airlineOptions, airline).map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </SelectField>
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
          <button type="submit" className={pillPrimary}>
            Lọc
          </button>
        </Form>
      )}

      {flightsRes === undefined && <FormSkeleton rows={2} />}
      {flightsRes && "error" in flightsRes && (
        <Notice>{flightsRes.error.detail}</Notice>
      )}
      {flightsRes && "data" in flightsRes && (
        <>
          {flightsRes.data.content.length === 0 ? (
            <p className="text-muted">Không có chuyến nào khớp bộ lọc.</p>
          ) : (
            <div className="grid gap-16">
              <div className={tableBox}>
                <table
                  aria-label="Danh sách chuyến"
                  className="w-full border-collapse"
                >
                  <thead>
                    <tr>
                      <th className={th}>Chuyến</th>
                      <th className={th}>Tuyến</th>
                      <th className={th}>Cất cánh</th>
                      <th className={th}>Hạ cánh</th>
                      <th className={th}>Máy bay</th>
                      <th className={th}>Trạng thái</th>
                      <th className={th}>Ghế còn</th>
                      <th className={th}>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {flightsRes.data.content.map((f) => (
                      <tr key={f.id}>
                        <td className={td}>
                          <span className="font-bold">{f.flightNumber}</span>
                        </td>
                        <td className={td}>
                          {`${f.departureAirport} → ${f.arrivalAirport}`}
                        </td>
                        <td className={td}>
                          {`${clock(f.departureTime)} ${dmy(f.departureTime)}`}
                        </td>
                        <td className={td}>
                          {`${clock(f.arrivalTime)} ${dmy(f.arrivalTime)}`}
                        </td>
                        <td className={td}>{f.aircraftModel}</td>
                        <td className={td}>
                          <StatusBadge status={f.status} label={FLIGHT_STATUS[f.status] ?? f.status} />
                        </td>
                        <td className={td}>
                          {f.cabins.map((c) => (
                            <p key={c.cabinClass} className="whitespace-nowrap">
                              {`${cabinLabel(c.cabinClass)} ${c.availableSeats}/${c.totalSeats}`}
                            </p>
                          ))}
                        </td>
                        <td className={td}>
                          <Link
                            href={`/staff/flights/${f.id}/passengers`}
                            className={textLink}
                          >
                            Hành khách
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="text-caption text-muted">
                Giờ theo sân bay đi và đến.
              </p>
            </div>
          )}
          <Pager
            path="/staff/flights"
            params={params}
            totalPages={flightsRes.data.page.totalPages}
          />
        </>
      )}
    </StaffPage>
  );
}

