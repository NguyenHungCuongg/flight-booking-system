"use client";

import { useState, useSyncExternalStore } from "react";
import { AdminPage, AdminTabs, tableBox, td, th } from "../admin-ui";
import { Field, FormSkeleton, Notice, SelectField } from "@/components/form";
import { vnd } from "@/app/flights/itineraries";
import { type Schemas } from "@/lib/api";
import { clock, dmy } from "@/lib/booking";
import { useApi } from "@/lib/use-api";

type CashFlow = Schemas["CashFlowReport"];
type Sales = Schemas["SalesReport"];
type LoadFactor = Schemas["LoadFactorReport"];
type Tab = "cash-flow" | "sales" | "load-factor";
type CashGroupBy = "DAY" | "MONTH" | "YEAR";
type SalesGroupBy = "AIRLINE" | "ROUTE";

/** Ngày theo giờ Việt Nam, n ngày sau thời điểm ms (yyyy-mm-dd). */
const vnDate = (ms: number, n = 0) =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh" }).format(
    ms + n * 86_400_000,
  );

const noSubscribe = () => () => {};

/** Kỳ của báo cáo dòng tiền (ngày đầu kỳ) theo cách nhóm. */
const periodLabel = (period: string, groupBy: CashGroupBy) =>
  groupBy === "DAY"
    ? dmy(period)
    : groupBy === "MONTH"
      ? `${period.slice(5, 7)}/${period.slice(0, 4)}`
      : period.slice(0, 4);

export default function ReportsView() {
  const [tab, setTab] = useState<Tab>("cash-flow");

  // Mặc định 30 ngày gần nhất theo giờ Việt Nam; chỉ đọc giờ sau khi hydrate (server: null).
  const today = useSyncExternalStore(
    noSubscribe,
    () => vnDate(Date.now()),
    () => null,
  );
  const [fromInput, setFrom] = useState<string>();
  const [toInput, setTo] = useState<string>();
  const from = fromInput ?? (today ? vnDate(Date.parse(today), -30) : "");
  const to = toInput ?? today ?? "";

  const [cashGroupBy, setCashGroupBy] = useState<CashGroupBy>("DAY");
  const [salesGroupBy, setSalesGroupBy] = useState<SalesGroupBy>("AIRLINE");
  const [airlineFilter, setAirlineFilter] = useState<string>("");

  const airlinesRes = useApi<Schemas["AirlineResponse"][]>("/airlines");

  const range = `from=${from}&to=${to}`;
  const path =
    tab === "cash-flow"
      ? `/admin/reports/cash-flow?${range}&groupBy=${cashGroupBy}`
      : tab === "sales"
        ? `/admin/reports/sales?${range}&groupBy=${salesGroupBy}`
        : `/admin/reports/load-factor?${range}${airlineFilter ? `&airline=${airlineFilter}` : ""}`;

  return (
    <AdminPage>
      <div>
        <h1 className="text-heading-lg font-bold">Báo cáo & Thống kê.</h1>
        <p className="text-caption text-muted mt-4">
          Thống kê dòng tiền, doanh số vé và tỉ lệ lấp đầy chuyến bay theo múi
          giờ Việt Nam (A-10, FR-120–122).
        </p>
      </div>

      <AdminTabs
        activeKey={tab}
        onChange={(k) => setTab(k as Tab)}
        tabs={[
          { key: "cash-flow", label: "Dòng tiền (Cash Flow)" },
          { key: "sales", label: "Doanh số vé (Sales)" },
          { key: "load-factor", label: "Tỉ lệ lấp đầy (Load Factor)" },
        ]}
      />

      {/* Bộ lọc khoảng ngày và nhóm */}
      <div className="grid gap-16 sm:grid-cols-[200px_200px_1fr] sm:items-end border-b border-line pb-16">
        <Field
          label="Từ ngày"
          name="from"
          type="date"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
        />
        <Field
          label="Đến ngày"
          name="to"
          type="date"
          value={to}
          onChange={(e) => setTo(e.target.value)}
        />

        {tab === "cash-flow" && (
          <SelectField
            label="Nhóm theo"
            name="cashGroupBy"
            value={cashGroupBy}
            onChange={(e) => setCashGroupBy(e.target.value as CashGroupBy)}
          >
            <option value="DAY">Theo ngày</option>
            <option value="MONTH">Theo tháng</option>
            <option value="YEAR">Theo năm</option>
          </SelectField>
        )}

        {tab === "sales" && (
          <SelectField
            label="Nhóm theo"
            name="salesGroupBy"
            value={salesGroupBy}
            onChange={(e) => setSalesGroupBy(e.target.value as SalesGroupBy)}
          >
            <option value="AIRLINE">Theo hãng khai thác</option>
            <option value="ROUTE">Theo tuyến bay (Đi - Đến)</option>
          </SelectField>
        )}

        {tab === "load-factor" && (
          <SelectField
            label="Lọc theo hãng bay"
            name="airlineFilter"
            value={airlineFilter}
            onChange={(e) => setAirlineFilter(e.target.value)}
          >
            <option value="">Tất cả các hãng</option>
            {airlinesRes &&
              "data" in airlinesRes &&
              airlinesRes.data.map((al) => (
                <option key={al.code} value={al.code}>
                  {al.code} - {al.name}
                </option>
              ))}
          </SelectField>
        )}
      </div>

      {/* Chỉ tải khi đã biết khoảng ngày; báo cáo được tải lại khi đổi tab hoặc bộ lọc (đổi path). */}
      {from && to ? (
        <ReportBody
          tab={tab}
          path={path}
          cashGroupBy={cashGroupBy}
          salesGroupBy={salesGroupBy}
        />
      ) : (
        <FormSkeleton rows={3} />
      )}
    </AdminPage>
  );
}

function ReportBody({
  tab,
  path,
  cashGroupBy,
  salesGroupBy,
}: {
  tab: Tab;
  path: string;
  cashGroupBy: CashGroupBy;
  salesGroupBy: SalesGroupBy;
}) {
  const res = useApi<CashFlow | Sales | LoadFactor>(path);
  if (res === undefined) return <FormSkeleton rows={3} />;
  if ("error" in res) return <Notice>{res.error.detail}</Notice>;

  // useApi trả kết quả theo đúng path hiện tại, nên kiểu dữ liệu khớp với tab đang mở.
  if (tab === "cash-flow") {
    const data = res.data as CashFlow;
    return (
      <div className="grid gap-16">
        <div className="grid gap-16 sm:grid-cols-3">
          <div className="card p-16">
            <span className="text-caption text-muted">Tổng thu (Charged)</span>
            <p className="text-heading font-bold text-ink mt-4">
              {vnd(data.totalCharged)}
            </p>
          </div>
          <div className="card p-16">
            <span className="text-caption text-muted">
              Tổng hoàn (Refunded)
            </span>
            <p className="text-heading font-bold text-danger mt-4">
              {vnd(data.totalRefunded)}
            </p>
          </div>
          <div className="card p-16">
            <span className="text-caption text-muted">
              Doanh thu ròng (Net)
            </span>
            <p className="text-heading font-bold text-ink mt-4">
              {vnd(data.netRevenue)}
            </p>
          </div>
        </div>

        <div className={tableBox}>
          <table
            aria-label="Báo cáo dòng tiền"
            className="w-full border-collapse"
          >
            <thead>
              <tr>
                <th className={th}>Thời kỳ</th>
                <th className={th}>Tiền thu</th>
                <th className={th}>Tiền hoàn</th>
                <th className={th}>Doanh thu ròng</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((item) => (
                <tr key={item.period}>
                  <td className={`${td} font-bold font-mono`}>
                    {periodLabel(item.period, cashGroupBy)}
                  </td>
                  <td className={td}>{vnd(item.charged)}</td>
                  <td className={`${td} text-danger`}>
                    {vnd(item.refunded)}
                  </td>
                  <td className={`${td} font-bold`}>{vnd(item.net)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    );
  }

  if (tab === "sales") {
    const data = res.data as Sales;
    return (
      <div className="grid gap-16">
        <div className="grid gap-16 sm:grid-cols-2">
          <div className="card p-16">
            <span className="text-caption text-muted">
              Tổng số vé xuất kỳ này
            </span>
            <p className="text-heading font-bold text-ink mt-4">
              {data.totalTickets.toLocaleString("vi-VN")} vé
            </p>
          </div>
          <div className="card p-16">
            <span className="text-caption text-muted">Tổng doanh thu vé</span>
            <p className="text-heading font-bold text-ink mt-4">
              {vnd(data.totalRevenue)}
            </p>
          </div>
        </div>

        <div className={tableBox}>
          <table
            aria-label="Báo cáo doanh số vé"
            className="w-full border-collapse"
          >
            <thead>
              <tr>
                <th className={th}>
                  {salesGroupBy === "AIRLINE" ? "Hãng hàng không" : "Tuyến bay"}
                </th>
                <th className={th}>Số lượng vé</th>
                <th className={th}>Doanh thu vé</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((item) => (
                <tr key={item.groupKey}>
                  <td className={`${td} font-bold`}>
                    {item.groupKey}{" "}
                    {item.groupName ? `- ${item.groupName}` : ""}
                  </td>
                  <td className={td}>{item.ticketCount} vé</td>
                  <td className={`${td} font-bold`}>{vnd(item.revenue)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    );
  }

  const data = res.data as LoadFactor;
  return (
    <div className="grid gap-16">
      <div className="card p-16">
        <span className="text-caption text-muted">
          Tỉ lệ lấp đầy bình quân toàn bộ chuyến
        </span>
        <p className="text-heading font-bold text-ink mt-4">
          {data.averageLoadFactorPercent.toFixed(1)}%
        </p>
      </div>

      <div className={tableBox}>
        <table
          aria-label="Báo cáo tỉ lệ lấp đầy"
          className="w-full border-collapse"
        >
          <thead>
            <tr>
              <th className={th}>Chuyến</th>
              <th className={th}>Tuyến bay</th>
              <th className={th}>Ngày cất cánh</th>
              <th className={th}>Tổng ghế</th>
              <th className={th}>Ghế đã lấp</th>
              <th className={th}>Tỉ lệ lấp đầy</th>
            </tr>
          </thead>
          <tbody>
            {data.items.map((item) => (
              <tr key={item.flightId}>
                <td className={`${td} font-bold`}>{item.flightNumber}</td>
                <td
                  className={td}
                >{`${item.departureAirport} → ${item.arrivalAirport}`}</td>
                <td className={td}>
                  {dmy(item.departureTime)} {clock(item.departureTime)}
                </td>
                <td className={td}>{item.totalSeats}</td>
                <td className={td}>{item.occupiedSeats}</td>
                <td className={`${td} font-bold`}>
                  {item.loadFactorPercent.toFixed(1)}%
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
